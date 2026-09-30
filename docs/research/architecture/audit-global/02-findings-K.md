# Audit global HyColony : 02, axe K, performance par tick

```
ÉTAT : phase 2, axe K écrit. Code audité : commit 3e2e70ca. Sources : relecteur « performance » (chemin du tick
tracé de bout en bout, coûts estimés non mesurés), rapport « modèle de données » (balayages), relecteur plugin.
Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Les machines à états n'allouent pas ; chaque transition est décalée (`OFFSET_VARIANT`) ; les cadences 20/60/100/500 ne s'alignent pas entre entités ; les index sont O(1) (`RequestStore`, `BuildingManager.at/byRequester`, `TerritoryIndex.colonyAt`) ; les bornes existent (`SCAN_LIMIT` 10 000, `SEARCH_LIMIT` 4 096, budget de collage). Estimation pour 20 citoyens / 10 bâtiments / 1 colonie ACTIVE : ≈ 300-400 appels et 30-60 allocations par tick, dominés par les lectures monde des pas de métier via le plugin. Le pire tick est l'autosave.

## 1. Constats

### K-1 — MOYEN — Autosave synchrone de toutes les colonies sales sur un seul tick, et une colonie en chantier est toujours sale
`plugin/src/main/java/dev/hycolony/plugin/WorldRuntime.java:138-140` ; `core/src/main/java/dev/hycolony/core/app/ColonyPersistence.java:124-130, 141` ; `core/.../construction/builder/BuildSite.java:128-131` ; `core/.../kernel/persist/FileColonyStorage.java:139-151`
```java
if (clock.currentTick() % autosaveTicks == 0) { manager.persistence().saveDirty(); }
```
```java
void progress(Stage stage, int index) { resources.progress(stage, index); colony.markDirty(); }
```
Mécanisme : modulo global sans décalage par colonie ; chaque colonie sale est sérialisée en arbre Gson, convertie en chaîne, écrite puis renommée deux fois sur le thread du monde ; chaque pas du bâtisseur marque sale, et `CitizenManager.tickData` (:80-82) aussi dès qu'un corps est lié : une colonie active est réécrite intégralement à chaque autosave. Le tick d'autosave (multiple de 6 000) est aussi multiple de 20 : il coïncide avec `OpenWindows.tick` et `BuildGoggles.tick`.
Impact : par colonie de 20 citoyens / 10 bâtiments, JSON de l'ordre de 100 Ko → 5-15 ms (sérialisation + 3 opérations fichier NTFS) ; 5 colonies → 25-75 ms, soit 1 à 2 ticks de 50 ms perdus toutes les 5 minutes. MC sauve aussi avec le monde, synchrone : pas un écart de fidélité, un pic évitable.
Règle : § 4 (chemin chaud bloqué). Remède : une colonie sale par tick à partir du tick d'autosave (file vidée à raison d'une par tick), et un décalage par colonie.
Sévérité MOYEN · effort S · confiance HAUTE (mécanisme), MOYENNE (ordre de grandeur, non mesuré) · DÉJÀ CONNU non.

### K-2 — BAS — `GogglesView.remaining` parcourt tout le blueprint sans plafond, tous porteurs sur le même tick
`core/src/main/java/dev/hycolony/core/app/goggles/GogglesView.java:73-85` ; `core/.../app/goggles/BuildGoggles.java:62-67, 86-94`
```java
for (BlueprintEntry e : bp.get().entries()) { ... Optional<BlockState> current = world.get(o.buildingPos().offset(...));
    ... else if (!current.map(e.state()::equals).orElse(false)) { blocks.add(new PreviewPort.Block(e.offset(), e.state())); }
```
Mécanisme : par chantier visible et par porteur, toutes les 100 ticks, une lecture monde par entrée du plan (recherche de section + `Optional`), une référence de méthode liée par entrée, un record par bloc restant, puis `blocks.equals(before.blocks())` élément par élément ; `blueprints().load(...)` rappelé à chaque rafraîchissement ; tous les porteurs sur le même tick (`ticks % CHECK_INTERVAL_TICKS`). Aucun `SCAN_LIMIT`, contrairement au pas du bâtisseur.
Impact : plan de 5 000 entrées, 3 chantiers visibles, 2 porteurs → 30 000 lectures monde et ~40 000 allocations sur un tick toutes les 100 ticks.
Règle : § 4 (parcours bornés). Remède : réutiliser le `StructurePlan` et le progrès de l'ordre au lieu de relire tout le plan, ou plafonner par tick et étaler les porteurs.
Sévérité BAS (outil de joueur, peu de porteurs) · effort M · confiance HAUTE · DÉJÀ CONNU non.

### K-3 — BAS — Un pas de scan à la borne `SCAN_LIMIT` coûte 10 000 lectures monde sur un seul tick
`core/src/main/java/dev/hycolony/core/construction/builder/BuilderAI.java:195-202` ; `StructureScan.java:29-36, 52-53`
```java
int limit = (int) Math.min(size, (long) from + SCAN_LIMIT);
int i = ctx.scan().firstNeedingWork(site, stage, from, limit);
```
Mécanisme : la borne est par pas (5 ticks), pas par tick ; chaque position coûte une recherche de section (`HytaleWorldBlocks.get`), un `Optional<BlockState>` et un `Optional<Building>` ; atteinte à chaque re-parcours complet (vérification finale, CLEAR d'un prefab dont `clearList` est tout le volume, reprise après chargement). MC a la même constante (`getMaxBlocksCheckedPerCall`) sur un tick d'entité.
Impact : jusqu'à 10 000 lectures et ~20 000 allocations en un tick par bâtisseur, ≈ 5-10 ms ; deux bâtisseurs finissant le même tick, 10-20 ms ; une fois par étape rejouée.
Règle : § 4. Remède : borne par tick plus basse (1 000, le pas reprend déjà de `site.progress(stage, i)`), ou documenter le pic.
Sévérité BAS · effort S · confiance HAUTE (mécanisme), MOYENNE (coût) · DÉJÀ CONNU non (la borne est documentée, pas le pic).

### K-4 — BAS — Allocations à cadence 1 par citoyen : `Optional` de `work()`, `isAlive` à travers trois ports et une lambda
`core/src/main/java/dev/hycolony/core/citizen/CitizenAI.java:75, 160` ; `core/.../citizen/CitizenData.java:118-120` ; `core/.../citizen/CitizenManager.java:87-92` ; `plugin/.../npc/GuardedBodies.java:39-41`
```java
Job job = data.job().orElse(null);          // CitizenData: return Optional.ofNullable(job);
```
```java
if (body != null && ctx().bodies().isAlive(body)) { e.getValue().tick(); }
```
Mécanisme : seule transition à cadence 1 du cœur ; `Optional.ofNullable` sur un job non nul alloue ; `isAlive` = `ref(body) != null` côté plugin via `DetouringBodies` puis `GuardedBodies.guard("isAlive", () -> …, false)` (lambda capturante), alors que `onEntityRemove` → `untrack` → `onBodyUnloaded` retire déjà le corps de `ais` et chaque appel de port répond vide/faux sur un corps disparu (§ 4).
Impact : 1 + 1 allocation et 3 appels virtuels par citoyen et par tick (20 citoyens → 800 objets/s), pour une information déjà tenue par événement ; l'analyse d'échappement en élimine sans doute une partie.
Règle : § 4. Remède : accesseur `@Nullable Job` package-private (ou garder le job dans l'IA) ; retirer `isAlive` de `tickAi`, ne le garder que pour le respawn.
Sévérité BAS · effort S · confiance HAUTE (source), MOYENNE (coût réel) · DÉJÀ CONNU non.

### K-5 — BAS — Allocations dans le plugin à chaque tick serveur : boxing `Float` + map concurrente, itérateur par joueur
`plugin/src/main/java/dev/hycolony/plugin/ColonyTickSystem.java:20, 33, 43` ; `plugin/src/main/java/dev/hycolony/plugin/adapter/HytalePreviewPort.java:87-95`
```java
private final Map<String, Float> accumulators = new ConcurrentHashMap<>();
float acc = accumulators.getOrDefault(key, 0f) + dt; ... accumulators.put(key, acc);
```
Mécanisme : `Float.valueOf` (pas de cache) + `put` concurrent à chaque tick de monde ; `hideFromOthers` alloue un itérateur de `HashMap` par joueur et par tick même quand `owners` est vide. La carte concurrente n'existe que parce que l'accumulateur n'est pas dans `WorldRuntime`, déjà propre à un monde et à son thread.
Règle : § 4. Remède : champ `float` dans `WorldRuntime` ; `if (owners.isEmpty()) return 0;`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### K-6 — BAS — Petites allocations périodiques sans usage : historique de la machine, copie des résolveurs
`core/src/main/java/dev/hycolony/core/kernel/ai/TickRateStateMachine.java:139-142` ; `core/src/main/java/dev/hycolony/core/request/RequestManager.java:183-185` ; `ResolverRegistry.java:139-141`
```java
history.addLast(state + "->" + newState);
```
Mécanisme : `StringBuilder` + `String` par transition effective, historique lu par un seul test ; `RequestManager.tick` copie la liste des résolveurs toutes les 11 ticks pour n'appeler qu'un `tick` non vide (`RetryingResolver`).
Impact : ≈ 0,5 allocation/tick par travailleur actif, ≈ 0,4 par colonie ; négligeable.
Règle : § 4, § 3 (code mort). Remède : supprimer l'historique ou mémoriser une paire d'énumérés ; liste `ticking` remplie à l'enregistrement.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU partiel (copie : audit B § 1.4).

### K-7 — BAS (DÉJÀ CONNU) — `CourierTaskPicker` : file × bâtiments × résolveurs, trois fois par entrée, sans borne
`core/src/main/java/dev/hycolony/core/logistics/courier/CourierTaskPicker.java:64-76, 103-125, 132-172` ; `logistics/warehouse/RequesterLocation.java:20-32`
Mécanisme : à chaque décision (100 ticks par livreur), `score` → `source` + `target` (2 × `RequesterLocation.of`, linéaires sur les bâtiments et leurs résolveurs), puis `groupAndAge` rappelle `target` par entrée. `colony.buildings().byRequester(id)` est O(1) (`BuildingManager.java:63-65`).
Impact : Q = 50, B = 10 × ~2 résolveurs : ≈ 3 000 `equals` par décision, ~60 opérations/tick pour 2 livreurs.
Règle : § 4. Remède : `byRequester` avant la boucle des résolveurs.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU oui (`docs/BACKLOG.md`).

## 2. Non retenus

- `CitizenAI.rainStopsWork:202-214` (stream + 3 lambdas toutes les 10 ticks par travailleur) : écarté par l'audit A, ≈ 2 appels/tick.
- `OpenWindows.tick`, `CitizenNameplates.refresh`, `BuildGoggles.tick` (`new ArrayList<>(wearers.entrySet())` même sans porteur), `WorkManager.tick` + `WorkOrderAssignment.assign` (`ArrayList`, `HashSet`, tri, `module()` stream par bâtiment) : 20 ticks, bornés.
- `SyncRequests.claimOpenFromHut` → `PlayerResolver.onColonyUpdate` : 40 ticks, court-circuit sur `requester`/`citizenId`.
- `WorkerStock.hutCount` par item dans `missingForCurrentAndNext` (≤ 36 items × conteneurs × piles, `stacks()` réalloue côté plugin) : 20 ticks, borné par `BUCKET_STACKS`.
- `PickupRound.pickupFromBuilding` relit `hut.containers()` et `stacks(container)` à chaque slot : 5-10 ticks pendant une tournée, borné.
- `WorkerStock.toolInInventory` : 27 `Optional` par appel au pas MINE_BLOCK (10 ticks).
- `DetouringBodies.navStatus` : 2 `position()` et un `Long` boxé toutes les 5 ticks par marcheur.
- `BuilderGestures.hitTarget` : lambda + `Optional` toutes les 5 ticks pendant un minage ; `blockHit` à chaque tick d'IA comme MC.
- `ColonyState.of` : joueurs × colonies, 100 ticks, décalé.
- `Colony.slowTick` (500) : les 9 transitions ont des variantes consécutives, `checkDayTime`, `work.tick`, `nameplates` ne tombent jamais le même tick ; `WorkerModule.onColonyTick` (citoyens par bâtiment), `CourierAssignmentModule.onColonyTick` (`warehouseOf` par livreur), `FarmerFieldsModule.onColonyTick` (champs² par hutte) : bornés, tick lent.
- Colonie INACTIVE/UNLOADED : une transition (100 ticks) plus une boucle `tickAi` vide quand les corps sont déchargés : conforme à MC.
- `WorkerMachine.tick` sans décalage propre : les JobAI naissent depuis `CitizenAI.idle` (offset `3k % 20` par citoyen), donc étalés.
- `CitizenAI.startJob` recrée un JobAI complet à chaque cycle WORKING → IDLE : pas par tick.
- `StructurePlan` : fonction pure du blueprint, aucune donnée monde en cache, pas d'invalidation manquante ; `NeededResources` recalculé au plus une fois par index.
- `TerritoryIndex.colonyAt` O(1), `BuildingManager.at`/`byRequester` O(1) ; `owningContainer` linéaire mais sur événements ; `townHall()` stream à 200-500 ticks.
- `PasteQueue.tick` : lecture de config + `isEmpty()` par tick ; budget respecté ; la boucle de saut de `clearNext` n'est pas comptée mais bornée par la boîte, en créatif seulement.
- Requêtes relancées : `RetryingResolver` 1 200 ticks × 3, `ToolRequests`, `BuilderRequests`, `FarmWork.askOnce` dédupliquent sur les requêtes vivantes.
- `CraftingWork.getRecipe` → `RecipeChoice.firstFulfillable` (recettes × ingrédients × conteneurs) au pas GET_RECIPE (5 ticks) : une évaluation par recette choisie.
- `Building.module(Class)` stream : écarté par l'audit A, aucun appel à chaque tick trouvé.

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/kernel/ai/TickingTransition.java:29-30` : `OFFSET_VARIANT` décale chaque transition à sa création ; avec 3 transitions par `CitizenAI` et 9 par `Colony`, les cadences ne s'alignent pas entre entités.
- `core/src/main/java/dev/hycolony/core/construction/workorder/WorkManager.java:177-180, 194-214` : `holds` et `claimedBy` sans allocation, documentés pour le pas du bâtisseur ; `construction/blueprint/StructurePlan.java:197-205, 250-252` : positions monde précalculées et `satisfied` sans allocation ; `BuilderAI.java:24, 195-202` : `SCAN_LIMIT` avec reprise de l'index.
- `core/src/main/java/dev/hycolony/core/request/RequestStore.java:24-27` : index inverses O(1) ; `logistics/courier/DeliverymanJob.java:96-104` : tirage de la file d'entrepôt seulement en START_WORKING (100 ticks) ; `DeliveryPreparation.java:22-23` : map `covered` réutilisée.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `plugin/.../WorldRuntime.java:138` ; `core/.../app/ColonyPersistence.java:124-130` ; `construction/builder/BuildSite.java:130` | K-1 autosave synchrone de toutes les colonies sur un tick |
| BAS | `core/.../app/goggles/GogglesView.java:73-85` | K-2 blueprint entier sans plafond, porteurs alignés |
| BAS | `core/.../construction/builder/BuilderAI.java:195-198` | K-3 pas de scan à la borne : 10 000 lectures sur un tick |
| BAS | `core/.../citizen/CitizenAI.java:160` ; `CitizenManager.java:87-92` | K-4 `Optional` et `isAlive` à cadence 1 |
| BAS | `plugin/.../ColonyTickSystem.java:20,33,43` ; `adapter/HytalePreviewPort.java:87-95` | K-5 boxing et itérateur par tick serveur |
| BAS | `core/.../kernel/ai/TickRateStateMachine.java:142` ; `request/RequestManager.java:184` | K-6 historique et copie sans usage |
| BAS (connu) | `core/.../logistics/courier/CourierTaskPicker.java:132-172` | K-7 file × bâtiments × résolveurs |
