# Audit global HyColony : 02, axe E, threads, tick et robustesse

```
ÉTAT : phase 2, axe E écrit. Code audité : commit 3e2e70ca. Sources : rapport « threads » (sources décompilées
citées), rapport « ECS », relecteurs plugin, mods frères, cœur (requêtes/logistique/crafting/farming). Chemins
relatifs à la racine du dépôt.
```

**Note de l'axe : 3/5.** Tout l'état de jeu tourne sur le thread du monde ; les sorties (préchargement, HyDomum) reviennent par `world.execute` et sont journalisées ; les systèmes d'événements attrapent, annulent et journalisent SEVERE ; les attentes du cœur sont bornées (sauf celles gardées par le joueur, comme MC). Mais un port lève dans le tick de toutes les colonies, deux systèmes chauds ne sont pas gardés, un état de fermier ne sort plus, et un `join()` n'a pas de délai.

## 1. Constats

### E-1 — HAUT — `HytalePlayerDirectory.position` lève pendant un transfert de monde et fait sauter le tick de toutes les colonies
`plugin/src/main/java/dev/hycolony/plugin/adapter/HytalePlayerDirectory.java:45-53`
```java
TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
Vector3d p = t.getPosition();
```
Mécanisme : `Store.getComponent` appelle `assertThread()` qui lève `IllegalStateException` si le store n'est pas celui du thread courant (`component/Store.java:1184-1186`, `:2310-2314`, un vrai `throw`). `refIn` (:114-120) filtre `world.getPlayerRefs()` mais `PlayerRef.getReference()` renvoie la ref du store où le joueur est *maintenant* : pendant un transfert, le joueur figure encore dans l'ancien monde avec une ref dans le nouveau store. Le projet garde ce cas ailleurs (`HytaleUiPort.playerHere:229-233`, `LiveWindows.openPage:63-65`), pas ici ; `t` peut aussi être `null`. `isCreative`/`facing`/`isOperator` ont leur `try/catch`, `position` non.
Impact : `ColonyState.of` (`core/.../colony/ColonyState.java:20-21`) appelle `position` pour chaque joueur en ligne toutes les 100 ticks ; l'exception remonte dans `manager.tick()` → `WorldRuntime.tickCore:141` journalise SEVERE et **le tick entier du monde (toutes ses colonies) est perdu** à chaque évaluation tant que dure la fenêtre.
Règle : § 4 (« un port ne lève jamais »). Remède : même `try/catch` que `facing`, et dans `refIn` refuser une ref dont `ref.getStore().getExternalData().getWorld().isInThread()` est faux.
Sévérité HAUT · effort S · confiance HAUTE (mécanisme), MOYENNE (fréquence) · DÉJÀ CONNU non.

### E-2 — HAUT — Le fermier ne redemande jamais graines ni engrais : sa requête de hutte n'est jamais `RECEIVED`
`core/src/main/java/dev/hycolony/core/farming/job/FarmWork.java:220-229`
```java
if (r.requestable() instanceof StackRequest s && s.item().equals(item) && r.state().isBefore(RequestState.RECEIVED)) { return; }
...
ctx.colony().requests().createAndAssign(ctx.hut(), new StackRequest(item, count, 1, true), Request.NO_CITIZEN);
```
Mécanisme : la requête est déposée au niveau de la hutte (`NO_CITIZEN`). Une fois COMPLETED, personne ne la passe RECEIVED : `Building.onRequestComplete` (`building/Building.java:197-200`) ne reçoit que les requêtes de hutte **non** livrables, `SyncRequests.pickUp` (`job/work/SyncRequests.java:59,105`) ne traite que les requêtes portant l'id du citoyen, et `BuilderRequests.receiveCompletedBuildingRequests` (:80-85) n'est appelé que par le bâtisseur ; ce sont les trois seuls émetteurs de `RECEIVED` du cœur. La requête COMPLETED reste dans `byRequester(hut)` et `askOnce` sort avant d'en créer une autre.
Impact : 64 graines livrées (ou « Fournir » par le joueur) sont plantées en une passe (un champ de rayon 5 fait 121 cases) ; à la passe suivante `canGoPlanting` trouve la hutte vide, `askOnce` se tait, `field.nextStage()` saute la plantation, définitivement ; idem l'engrais une fois usé. MC reçoit ses requêtes async dans NEEDS_ITEM (`lookForRequests`/`markRequestAsAccepted`). Tests : `FarmWorkPrepareTest.noSeedAnywhereAsksOnceAndSkipsPlanting` (l. 100) et `fertilizerIsRequestedOnceWhenNoneAnywhere` ne vérifient que la première demande.
Règle : § 4 (état qui ne sort plus), § 6, § 8. Remède : recevoir les requêtes de hutte COMPLETED du fermier quand il est à la hutte (déplacer `receiveCompletedBuildingRequests` vers `job/work` et l'appeler dans `prepare()`), ou déposer sous l'id du citoyen ; test d'abord `hutAsksForSeedsAgainOnceTheDeliveredOnesAreUsedUp`.
Sévérité HAUT · effort S/M · confiance HAUTE · DÉJÀ CONNU non.

### E-3 — HAUT — Deux gestionnaires chauds sans garde : une exception tue le thread du monde ou celui de la carte
`plugin/src/main/java/dev/hycolony/plugin/goggles/GogglesSystems.java:145-161` ; `plugin/src/main/java/dev/hycolony/plugin/ui/highlight/HighlightMarkers.java:24-33`
```java
EntityTrackerSystems.EntityViewer viewer = chunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
if (viewer != null && player != null) { viewer.hiddenCount += rt.previews().hideFromOthers(player.getUuid(), viewer.visible); }
```
```java
Highlights.active(player.getPlayerRef().getUuid()).filter(a -> a.world().equals(world.getWorldConfig().getUuid()))
```
Mécanisme : `Visibility.tick` est un `EntityTickingSystem` (par joueur, chaque tick) sans `try/catch` : une `RuntimeException` remonte à `TickingThread.java:88-97` et **arrête le thread du monde** ; `HighlightMarkers.update` tourne sur le thread de la carte (`WorldMapManager.java:66, 174-181`) que Hytale ne garde pas (`MapMarkerTracker.java:79-81`). Tous les autres systèmes du plugin sont gardés (14/16).
Impact : un `Ref` invalide dans `visible`, un `Player.getPlayerRef()` (déprécié pour retrait) qui change de contrat : un monde entier ou sa carte s'arrête.
Règle : § 4 (les gestionnaires attrapent `RuntimeException` et journalisent SEVERE). Remède : `try { … } catch (RuntimeException e) { LOG SEVERE }` dans les deux, comme `GuardedBodies`.
Sévérité HAUT · effort S · confiance HAUTE (mécanisme), BASSE (probabilité d'une exception aujourd'hui) · DÉJÀ CONNU non.

### E-4 — MOYEN — `WorldRuntimes.remove` bloque le thread appelant sans délai
`plugin/src/main/java/dev/hycolony/plugin/WorldRuntimes.java:53-58`
```java
if (world.isInThread() || !world.isAlive()) {
    rt.saveAll(); // on the world thread, or its thread is gone: nothing else touches the colonies
} else {
    CompletableFuture.runAsync(rt::saveAll, world).join();
```
Mécanisme : `RemoveWorldEvent` est dispatché en synchrone sur le thread appelant (`Universe.java:1302-1305, 1349-1352`). Depuis `World.onShutdown` (le monde lui-même) et les commandes (pool commun), c'est sûr et c'est le miroir de `Universe.removeWorld` (`runAsync(...).join()` :1298-1305). Mais `CloseWorldWhenBreakingDeviceSystems.java:27` (portails) retire le monde-fragment F **depuis le thread du monde d'origine W**, dans un rappel de `ChunkStore` et sous `ASSET_LOCK.readLock` : W attend la file de F, que F ne vide qu'après son propre `readLock` (`World.java:380-396`) ; un écrivain en attente (HyDomum `loadAssets`, rechargement d'assets ; verrou non équitable, `AssetRegistry.java:19`) bloque F tout en attendant le `readLock` de W : cycle W → F → écrivain → W. Si la tâche est offerte entre le dernier drain (`World.java:439`) et `acceptingTasks = false` (:448), `join()` ne rend jamais la main. Hytale fait déjà une attente W → F sur ce chemin (`World.java:322-324`) ; la nôtre s'ajoute **sans délai**. Le relecteur plugin juge le blocage « pas au-delà de celui de Hytale » : désaccord à trancher en phase 4.
Impact : blocage du thread d'un monde (donc de ses colonies) dans une fenêtre étroite (fermeture d'un portail pendant un lot HyDomum).
Règle : § 4 (chaque attente a une sortie). Remède : `.orTimeout(10, SECONDS)` sur le `join()` ; sur délai, SEVERE et laisser le runtime au filet de sécurité `ShutdownEvent`.
Sévérité MOYEN · effort S · confiance MOYENNE · DÉJÀ CONNU non.

### E-5 — MOYEN — `openCitizenInventory` sans garde : une fenêtre fantôme si `build` échoue
`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleUiPort.java:180-182` → `plugin/src/main/java/dev/hycolony/plugin/ui/citizen/CitizenInventoryWindows.java:66-69`
```java
if (shown.pages().openCustomPageWithWindows(ref, store, shown.page().withInventory(pr, panel), window)) {
    open.add(new Open(ref, colonyId, window));
    window.registerCloseEvent(e -> forget(window));
```
Mécanisme : `PageManager.openCustomPageWithWindows` (`PageManager.java:208-226`) ouvre d'abord les fenêtres puis `openCustomPage` → `page.build(...)` sans `try` (:129-138). Toute `RuntimeException` de `CitizenPage.build` (grilles HyBlockUI, panneaux, `SkillRowRenderer`) traverse le port : contrairement à `open`/`close` (:201, :219-223), aucun `guarded`.
Impact : fenêtre enregistrée dans le `WindowManager`, aucun paquet `OpenWindow`, `open.add` jamais exécuté → `closeAll` à la suppression de la colonie ne la ferme pas ; le joueur garde une fenêtre fantôme. L'appel unique vient de `CitizenPage.select:167` sous `PageEvents.guard`, donc pas de plantage.
Règle : § 4. Remède : `guarded("openCitizenInventory", () -> citizenInventories.open(...))`.
Sévérité MOYEN · effort S · confiance HAUTE (mécanisme), BASSE (probabilité) · DÉJÀ CONNU non.

### E-6 — MOYEN — `ColonyManager.tick` rafraîchit les fenêtres hors du `try` : une vue qui lève supprime lunettes, baguette et autosave
`core/src/main/java/dev/hycolony/core/app/ColonyManager.java:134-139` ; `plugin/src/main/java/dev/hycolony/plugin/WorldRuntime.java:133-143`
```java
for (Colony colony : colonies.values()) { colony.tick(); }
windows.tick();
```
Mécanisme : `Colony.tick` attrape ses exceptions et suspend la colonie ; `windows.tick()` (`OpenWindows.tick`, toutes les 20 ticks) n'est gardé que par `tickCore`, qui journalise et abandonne le reste du pas (`goggles.tick()`, `wand.tick()`, `saveDirty`). `OpenWindows.java:55-62` retire la fenêtre fautive, mais une vue qui lève à chaque reconstruction (état incohérent) ferait manquer le tick d'autosave (`% autosaveTicks == 0`, un seul tick) à chaque période.
Impact : une colonie qui n'est plus sauvegardée qu'à l'arrêt tant que la vue lève ; lunettes et baguette figées.
Règle : § 4. Remède : `windows.tick()` dans son propre `try/catch` (ou dans `tickCore`, un `safely` par étape) ; l'autosave comme dernier pas gardé séparément.
Sévérité MOYEN · effort S · confiance HAUTE (mécanisme), BASSE (probabilité) · DÉJÀ CONNU non.

### E-7 — BAS — `world.execute` hors garde dans deux gestionnaires d'événements et le filet `ShutdownEvent`
`plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java:98, 123-139` ; `plugin/src/main/java/dev/hycolony/plugin/goggles/GogglesSystems.java:55-60`
```java
worlds.all().forEach(rt -> rt.world().execute(() -> { safely("goggles", ...); safely("wand", ...); }));
```
Mécanisme : `World.execute` lève `SkipSentryException` quand `acceptingTasks` est faux (`World.java:1120-1126`), dès le début de l'arrêt d'un monde (:448) ; `safely` n'entoure que l'intérieur de la tâche ; `onPlayerReady` a `e.getPlayer().getWorld()` et `runtimes.of` hors du `try` ; le `ShutdownEvent` → `saveAll` de chaque monde n'est pas gardé (l'exception d'un monde saute les suivants, Hytale attrape `Throwable` à `SyncEventBusRegistry.java:143-146`).
Impact : une déconnexion pendant l'arrêt d'un monde interrompt le nettoyage des autres mondes (lunettes, baguette, fondation) ; à l'arrêt, une colonie non sauvegardée après une qui a levé.
Règle : § 4. Remède : `execute` dans `safely` (ou `world.isAlive()` avant) ; un `try` par monde dans le filet.
Sévérité BAS · effort S · confiance HAUTE (mécanisme), BASSE (fréquence) · DÉJÀ CONNU non.

### E-8 — BAS — Le thread du tick est hors `processing`, deux commentaires disent le contraire
`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java:309` ; `plugin/src/main/java/dev/hycolony/plugin/adapter/HytalePreviewPort.java:36-37`
```java
 * added. Deferred to world.execute: the colony ticks inside a store system, where structural changes throw.
```
Mécanisme : `Store.tickInternal` (:1990-2013) ne prend pas `processing` pour un `TickingSystem` plain ; `spawn` (:105-120) et `addComponent(MoveTarget)` (:156) sont d'ailleurs synchrones depuis le tick. Le report reste nécessaire pour `RefSystem` (`despawn` depuis `onBodyLoaded`), les systèmes d'événements et les interactions (lunettes, baguette).
Impact : la règle « quand différer » n'est pas dite correctement ; un futur adaptateur pourrait différer inutilement ou, pire, appeler `spawn` depuis un événement.
Règle : § 3 (commentaire = pourquoi exact), § 1 (API vérifiée dans les sources). Remède : corriger les deux commentaires (tick hors `processing` ; report pour événement/`RefSystem`/interaction) et le noter dans `plugin-b-api.md`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### E-9 — BAS — Sorties du thread et attentes de HyDomum : deux hops non commentés, `scheduleAfter` réimplémenté, horloge murale, `busy()` sans sortie
`domum/plugin/src/main/java/dev/hydomum/plugin/cutter/CutterCraftQueue.java:79, 84-91, 96-123` ; `CutterPreviewVariants.java:62-63` ; `persistence/VariantStore.java:39, 65` ; `blockui/.../InventoryMoves.java:22`
```java
startedAt = System.currentTimeMillis();
```
Mécanisme : `delayedExecutor` → pool commun → `world.execute` sans commentaire sur le saut, alors que `World.scheduleAfter(Runnable, long, TimeUnit)` (`World.java:1142-1148`) fait exactement cela sans pool ni `try/catch` (`BodySelfTest` l'utilise) ; `VariantStore` `synchronized` avec I/O sans commentaire ; `currentTimeMillis` : un recalage NTP en arrière rend `done` négatif et la fabrication attend que l'horloge repasse ; si `craftOne` lève en synchrone dans `tick`, `remaining > 0` sans tick planifié et `busy()` reste vrai jusqu'à la fermeture de la fenêtre. `PageEvents.guard` journalise SEVERE la première fois puis FINE (:22-24), le § 4 dit SEVERE.
Règle : § 4 (sorties documentées, chaque attente a une sortie), § 2 (pas de code pour ce que la plateforme fournit). Remède : `World.scheduleAfter`, `System.nanoTime()`, un `catch` autour de `craftOne` qui replanifie ou annule, commentaires sur `VariantStore` et `InventoryMoves.WARNED`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### E-10 — BAS — `Guard` (immunité au feu) laisse passer en cas d'échec ; `Grant` et `Guard` mettent en cache des assets pour la vie du plugin
`plugin/src/main/java/dev/hycolony/plugin/npc/CitizenFireImmunitySystems.java:140-146`
Mécanisme : tous les autres systèmes annulent sur exception ; celui-ci journalise et laisse le dégât passer (les citoyens sont `Invulnerable` par leur rôle, donc sans effet aujourd'hui).
Règle : § 4 (« annulent l'événement si c'est possible »). Remède : `event.setCancelled(true)` dans le `catch`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### E-11 — BAS — Divers : préchargement non durable, cache concurrent inutile, `KnownRecipes` sans garde de monde, cycle d'artisanat inféré
`plugin/src/main/java/dev/hycolony/plugin/prefab/HytaleBlueprintSource.java:73-74, 111` ; `plugin/src/main/java/dev/hycolony/plugin/crafting/KnownRecipes.java:20-25` ; `core/src/main/java/dev/hycolony/core/crafting/job/CraftingWork.java:165-202`
Mécanisme : `prewarm` remplit le `CACHE` de `PrefabBufferUtil` à `WeakReference` (:41, :62-63) et jette l'accesseur : l'entrée est collectable au prochain GC ; `cache`/`warned` sont des `ConcurrentHashMap` pour une instance par monde lue sur son seul thread ; `KnownRecipes.knows` sur un joueur d'un autre monde lève `assertThread` (attrapé à `HytaleRecipeCatalog.java:106-110`, une fois WARNING) ; `checkForItems ↔ gather` : si la hutte a l'ingrédient mais `stock.take` prend 0 (inventaire plein de piles liées à la recette), aucun compteur ne casse la boucle (inféré, non reproduit).
Règle : § 4. Remède : garder une référence forte le temps du préchargement, `HashMap`, garde `isInThread`, un compteur d'essais sur le cycle d'artisanat.
Sévérité BAS · effort S · confiance HAUTE / BASSE (cycle) · DÉJÀ CONNU non.

## 2. Non retenus

- Ports « sans try/catch » de la phase 1 vérifiés par le relecteur plugin dans les sources : `HytaleGameClock.isDaytime` (`getResource` sur le store du monde, sur son thread), `HytaleNotifier.send` et `HytaleUiPort.notifyNeedsPlayer` (`Universe.getPlayer` = `ConcurrentHashMap.get`, `sendMessage` = `writeNoCache`), `HytaleWorldQuery.isLoaded` (`ChunkGrid.getChunkReference`, lecture optimiste), `HytalePlayerDirectory.isOnline/onlineIn` (lectures de cartes), `HytaleContainerAccess.insert:80` (`DefaultAssetMap.getAsset`), `HytalePreviewPort.show/hide/hideAll/remove` (`getIndex` lecture ; `world.execute` ne lève que si le monde s'arrête, impossible depuis son propre tick), `HytaleBlueprintSource.defaultFillBlock` (clé `blueprint.fillBlock` présente, première définition gagnante dans `JsonFragments`), `fillBlockChoices`, `styles`, `load:141`, `HytaleRecipeCatalog.*`, `HytaleFarming.seeds/fertilizerItem` : ne lèvent pas. Les 11 méthodes de `HytaleCitizenBodies` sont couvertes par `GuardedBodies` (accès direct seulement depuis `BodySelfTest`).
- `ShutdownEvent` sur `ShutdownThread` après `shutdownAllWorlds` (-32) : threads des mondes joints, chaque monde déjà sauvegardé par `RemoveWorldEvent(EXCEPTIONAL)` ; filet correct (E-7 pour la garde).
- `HytaleCitizenBodies.spawn`/`addComponent` synchrones : légaux, seule chaîne d'appel = tick hors `processing` ; à documenter (E-8).
- `HytaleBlocks.addEntities` (drops) depuis `HytaleUiPort.java:78` : page ouverte sur le thread du monde, hors `processing`.
- `HytaleWorldBlocks.java:173` `ensureAndGetComponent` sur le `ChunkStore` : pendant que celui-ci ne traite pas (`World.java:385` avant :391).
- `BodySelfTest.future.get()` : thread du pool commun, borné (500 ms × 15 s) ; `Textures.getBlob().join()` : threads de création/boot, borné par `orTimeout` 30 s.
- Attentes gardées par le joueur (`NEEDS_ITEM`, `PlayerResolver`, entrepôt plein avec message toutes les 6 000 ticks) : comme MC, documentées.
- `OrnamentVariantRegistry.request` : chaque échec (clé, lot, timeout) complète le futur en erreur et l'évince (:76-99).
- `ASSET_LOCK.writeLock` de `BlockTypeSynchronizer` (:74, :105) qui bloque tous les mondes le temps d'un lot : documenté (:21-22), lots groupés (`OrnamentVariantRegistry.java:26-27`).
- `WorldRuntime.enabled` écrit d'un autre thread sans `volatile` (`WorldRuntimes.java:86`) : valeur identique pour tous, bénin.
- `CitizenPage.onDismiss`, `CutterPage.onDismiss` « Never throws » sans `try` propre : leurs appelés sont gardés.
- `Colony.tick` fait tourner `citizens.tickAi()` en INACTIVE/UNLOADED : fidèle à MC (l'IA d'un citoyen chargé tourne quel que soit l'état).

## 3. Ce qui est bien fait

- `plugin/src/main/java/dev/hycolony/plugin/npc/GuardedBodies.java:94-115` : le port des corps rendu infaillible par un décorateur (« première WARNING, puis FINE »), et `core/.../kernel/nav/DetouringBodies.java` par-dessus, sans que le cœur voie Hytale.
- `core/src/main/java/dev/hycolony/core/kernel/nav/StuckHandler.java:380-426` : re-chemin → téléportation → abandon, délai global `max(2400, 200 × max(10, manhattan))`, `MIN_TARGET_DIST`, `teleportAllowed=false` ; 8 tests dont `circlingAfterTeleportGivesUp`.
- `plugin/src/main/java/dev/hycolony/plugin/ColonyTickSystem.java:14-44` : le cœur découplé du TPS serveur, rattrapage borné à 10 pas puis abandon du retard (« MineColonies loses ticks too ») ; `WorldRuntime.tickCore:133-143` attrape et journalise SEVERE sans jamais arrêter le monde.
- `domum/plugin/.../registry/OrnamentVariantRegistry.java:76-99` et `blockui/.../PageRedraw.java`, `HeldWindows.java` : chaque retour au thread du monde passe par `world.execute`, chaque `SkipSentryException` à l'arrêt est attrapée.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| HAUT | `plugin/.../adapter/HytalePlayerDirectory.java:45-53` | E-1 `position` lève et fait sauter le tick des colonies |
| HAUT | `core/.../farming/job/FarmWork.java:220-229` | E-2 le fermier ne redemande jamais graines ni engrais |
| HAUT | `plugin/.../goggles/GogglesSystems.java:145-161` ; `ui/highlight/HighlightMarkers.java:24-33` | E-3 systèmes chauds sans garde, thread tué |
| MOYEN | `plugin/.../WorldRuntimes.java:57` | E-4 `join()` sans délai (désaccord entre agents) |
| MOYEN | `plugin/.../adapter/HytaleUiPort.java:180-182` | E-5 `openCitizenInventory` sans garde |
| MOYEN | `core/.../app/ColonyManager.java:138` | E-6 `windows.tick()` hors garde, autosave sautée |
| BAS | `plugin/.../HyColonyPlugin.java:98, 123-139` ; `goggles/GogglesSystems.java:55-60` | E-7 `execute` et filet d'arrêt hors garde |
| BAS | `plugin/.../adapter/HytaleCitizenBodies.java:309` ; `HytalePreviewPort.java:36-37` | E-8 commentaires faux sur `processing` |
| BAS | `domum/plugin/.../cutter/CutterCraftQueue.java:79, 84-91, 96-123` ; `CutterPreviewVariants.java:62` ; `VariantStore.java:39,65` | E-9 hops non commentés, horloge murale, `busy()` |
| BAS | `plugin/.../npc/CitizenFireImmunitySystems.java:140-146` | E-10 `Guard` laisse passer |
| BAS | `plugin/.../prefab/HytaleBlueprintSource.java:73-74, 111` ; `crafting/KnownRecipes.java:20-25` ; `core/.../crafting/job/CraftingWork.java:165-202` | E-11 divers |
