# Audit global HyColony : 02, axe M, fidélité à MineColonies

```
ÉTAT : phase 2, axe M écrit. Code audité : commit 3e2e70ca (identique à c24bf475 hors docs). Six contrôles
`mc-fidelity-checker` (fondations citoyen/métier, requêtes, construction, logistique, artisanat, fermier + colonie),
sources MC `version/main` relues par les agents, plus les points de fidélité remontés par les relecteurs de périmètre.
Chemins relatifs à `core/src/main/java/dev/hycolony/core/` sauf mention. Vérification en phase 4.
```

**Note de l'axe** : 6/6 des systèmes portés ont été comparés à leur source MC. La grande majorité des constantes, formules, états et transitions concordent (listes « comparé et conforme » des agents, reprises en annexe § 3). Les 169 `Deviation from MC:` du code sont cohérents avec MC et aucun n'est contesté. Ce qui suit est ce qui **manque** de marqueur, ou diverge sans le dire. Note de l'axe : **3/5** (fidélité solide sur les formules et cadences, mais un bug de cas limite et plusieurs écarts de jeu non documentés sur les règles de fondation, de permission et de requête).

## 1. Constats

### M-1 — HAUT — Le constructeur « termine » un chantier dont une partie est dans un chunk non chargé
`construction/builder/StructureScan.java:53-55` ; `construction/builder/BuilderBlockWork.java:198-207` ; `construction/builder/BuilderAI.java:116-122, 212-223`
```java
BlockState world = blocks.get(pos).orElse(null);
return switch (stage) {
    case CLEAR -> world != null && clears(site, pos, world) && notAHut(pos);
```
```java
if (!ctx.blocks().place(pos, e.state(), e.hasContainer())) {
    LOG.log(System.Logger.Level.WARNING, "Builder {0}: failed to place {1} at {2}; skipped", …);
    ctx.site().progress(stage, i + 1);
```
Mécanisme : aucun appel à `WorldBlocks.isLoaded` dans `construction/`. Une position dont `blocks.get(pos)` est vide (section non chargée) « n'a pas besoin de travail » en CLEAR ; en SOLID/DECORATE `place` renvoie `false` (le plugin refuse une section non chargée, `HytaleWorldBlocks.java:120-122, 152-165`) et l'index avance avec un WARNING. `orderLost` ne regarde que l'existence et la réclamation de l'ordre. MC `AbstractEntityAIStructureWithWorkOrder.checkIfCanceled` (:479-496) renvoie l'IA en IDLE tant que `!WorldUtil.isBlockLoaded(world, wo.getLocation())` (événement STATE_BLOCKING de cadence 1, `AbstractEntityAIStructure.java:158`).
Impact : corps chargé en bordure de zone chargée, positions du plan dans le chunk voisin : CLEAR sauté en silence, SOLID/DECORATE sautés, `COMPLETE_BUILD` : niveau monté, claims, XP, message « terminé », bâtiment troué. La vérification finale (`BuilderAI.java:212-223`) ne repasse que SOLID/DECORATE.
Règle : § 6 (cas limite « unloaded » que MC gère, sans `Deviation from MC:`), § 4. Remède : dans `structureStep`/`work`, attendre (`return null`) quand `!ctx.blocks().isLoaded(pos)`, ou porter le test de `checkIfCanceled` sur la position du bâtiment ; test avec `FakeWorldBlocks` déchargé sur une moitié du plan.
Sévérité HAUT · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-2 — HAUT — Le contournement opérateur/créatif n'existe que dans `ColonyProtection`, pas dans `hasPermission`
`colony/permission/Permissions.java:161-163` ; `app/ColonyProtection.java:35-39` ; appelants directs : `app/action/HutActions.java:40`, `app/action/ManagedHut.java:15`, `app/action/FieldActions.java:133,160`, `app/action/CitizenInventoryActions.java:41`, `app/wand/WandPlacement.java:72`, `construction/workorder/WorkManager.java:56`, `app/view/BuildingViews.java:38`, `app/view/WorkOrderViews.java:26`, `farming/hut/FieldsTab.java:37`, `app/view/ColonyWindows.java:140,145`, `app/action/RequestActions.java:46,113`
```java
public boolean hasPermission(UUID player, Action action) {
    return rankOf(player).has(action);
}
```
Mécanisme : MC `Permissions.hasPermission(Player, Action)` (:694-706) ajoute `player.hasPermissions(permissionEventMinBypassPermLevel) && isCreative()` → `OP_RANK` (ACCESS_HUTS, PLACE_HUTS, BREAK_HUTS, MANAGE_HUTS…), et c'est cette méthode qu'utilisent `EventHandler.onBlockHutPlaced` (:743), `onPlayerInteract` (:601, 642) et tous les messages de bâtiment. HyColony n'applique le contournement (`bypassesPermissions` + `operatorRankHas`) que dans `ColonyProtection.isAllowed`, donc aux blocs.
Impact : un opérateur en créatif peut casser et poser des blocs dans une colonie étrangère mais ne peut ni y poser une hutte, ni ouvrir ou gérer ses huttes ; la spec SP0 § 3.2 (l. 175) affirme la parité.
Règle : § 6. Remède : un seul point d'entrée qui applique le contournement (faire passer les 12 appels par `ColonyProtection.isAllowed`, ou donner à `Permissions.hasPermission` le prédicat « opérateur créatif ») ; test `creativeOperatorManagesAForeignHut`.
Sévérité HAUT · effort S/M · confiance HAUTE · DÉJÀ CONNU non.

### M-3 — HAUT (à trancher : le plan SP1-2 l'a choisi) — `RetryingResolver` réessaie 11 fois plus vite que MC
`request/resolver/RetryingResolver.java:28, 105`
```java
e.setValue(e.getValue() - RequestManager.TICK_INTERVAL);
```
Mécanisme : MC `StandardRetryingRequestResolver.update()` (:151-153) fait `delays.put(t, --current)` (**−1 par appel**), appelé par `StandardRequestManager.tick()` toutes les `UPDATE_RS_INTERVAL = 11` ticks (`ColonyConstants.java:60`) : `RETRY_DELAY = 1200` vaut 13 200 ticks (11 min) par essai, 33 min avant le joueur. Ici 1 200 ticks (1 min), 3 min. `docs/research/minecolonies-analysis.md:78` le dit (« 1200 × 11 ticks »). Le plan SP1-2 (`docs/superpowers/plans/2026-09-25-hycolony-sp12-core.md`, tâche 4) choisit « décrémente chaque délai de 11 » ; ni la classe (aucun `Deviation from MC:`), ni la spec (`…sp12…design.md:97` « délai de 1200 ticks ») ne le documentent ; `ResolversTest.retryingRetriesThreeTimesEvery1200TicksThenPlayer` et `docs/TESTING.md` point 29 (« environ 3 minutes ») figent la version rapide.
Impact : les requêtes tombent au joueur 11 fois plus tôt ; une requête qu'un entrepôt ou un artisan aurait servie dans les minutes suivantes est arrachée au joueur.
Règle : § 6. Remède : soit `- 1` par appel (le compteur sauvegardé reste valide) avec tests et docs alignés sur « 1200 mises à jour de 11 ticks », soit `Deviation from MC:` + ligne dans la spec si les 3 minutes sont voulues (elles ont été testées en jeu).
Sévérité HAUT · effort S · confiance HAUTE · DÉJÀ CONNU non (décision de plan non reportée).

### M-4 — HAUT — Réassignation du parent après l'échec d'un enfant : liste noire héritée, blocage possible
`request/RequestTransitions.java:116-119`
```java
// onChildRequestCancelled; the parent keeps the blacklist it was assigned with.
parent.setDeliveries(List.of());
canceller.cancelChildren(parent);
assigner.reassign(parent, parent.blacklist());
```
Mécanisme : MC `RequestHandler.onChildRequestCancelled` (:461-468) fait `reassignRequest(parent, ImmutableList.of())` (**liste vide**). Ici un parent déjà réassigné une fois depuis Player (`PlayerResolver.onColonyUpdate` → `assign(req, {player})`, `RequestAssigner.java:37`) garde `{player}` : si un enfant échoue (livraison FAILED, ingrédient) et qu'aucun autre résolveur ne le prend, il finit `REPORTED` sans résolveur et plus personne ne le re-propose.
Impact : blocage définitif d'une requête (§ 4), là où MC retombe sur le joueur.
Règle : § 6, § 4. Remède : `assigner.reassign(parent, Set.of())` ; test `parentFallsBackToThePlayerWhenItsChildFailsTwice`.
Sévérité HAUT · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-5 — HAUT — `onColonyUpdate` ne remonte pas aux parents (Player et Retrying)
`request/resolver/PlayerResolver.java:115-121` ; `request/resolver/RetryingResolver.java:122-129`
```java
for (Request r : new ArrayList<>(open.values())) {
    if (which.test(r) && r.children().isEmpty()) {
        m.reassign(r.token(), Set.of(ID));
```
Mécanisme : MC `StandardPlayerRequestResolver.onColonyUpdate` (:198-220) et `StandardRetryingRequestResolver` (:283-305) remontent les parents quand le prédicat ne matche pas la requête détenue ; le premier ancêtre qui matche voit ses enfants annulés puis est réassigné avec le résolveur en liste noire. Conséquence : `CraftingModule.handleRecipeUpdate` (`crafting/module/CraftingModule.java:150-159`, prédicat « deliverable qui matche la sortie de la recette ») ne débloque jamais un arbre dont la racine (planches) attend un ingrédient (bûches) coincé chez le joueur ; idem `RequestActions.onContainerChanged` (:147) et `SyncRequests.claimOpenFromHut` (`job/work/SyncRequests.java:74`), dont le prédicat exige `r.requester().equals(b.requesterId())` : un enfant (requester `resolver:…`) ne matche jamais.
Impact : une requête reste chez le joueur alors qu'un artisan nouvellement capable, ou un dépôt dans la hutte, aurait dû la reprendre ; comportement MC absent, sans commentaire.
Règle : § 6. Remède : porter la remontée des parents (ancêtre matchant → enfants annulés → réassignation avec liste noire) ; test avec un arbre planches ← bûches chez le joueur puis apprentissage de la recette.
Sévérité HAUT · effort M · confiance HAUTE · DÉJÀ CONNU non.

### M-6 — MOYEN — Règle de distance à la fondation différente de MC
`colony/territory/TerritoryIndex.java:49-60` ; appelants `app/action/HutActions.java:82-88`, `app/wand/WandPlacement.java:93-96`
```java
/** No claimed cell within (initialSize + minDistance) cells of the would-be centre. */
public boolean isFreeForNewColony(BlockPos center, int initialSize, int minDistance) {
    int r = initialSize + minDistance;
```
Mécanisme : refus si **une cellule revendiquée** existe dans le carré de rayon 12 cellules (25×25). MC `ColonyManager.isFarEnoughFromColonies` (:311-324) : distance **euclidienne 3D** entre la position et le **centre** de la colonie la plus proche ≥ `max(minColonyDistance, initialColonySize) << 4` = 128 blocs, **et** `ChunkDataHelper.canClaimChunksInRange(pos, initialColonySize)` (:174-207) : seuls les chunks du 9×9 doivent être libres.
Impact : HyColony refuse une fondation dont le 25×25 touche une revendication de bâtiment d'une autre colonie (jusqu'à 20 cellules de son centre) ; MC l'accepte dès que le 9×9 est libre et les centres à 128 blocs. La spec SP0 § 3.2 l. 152 présente la règle HyColony comme celle de MC.
Règle : § 6. Remède : porter les deux tests MC ; ou `Deviation from MC:` + spec.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-7 — MOYEN — Un bloc différent en place est miné (délai, XP, usure) au lieu d'être retiré sans délai
`construction/builder/BuilderBlockWork.java:49-54`
```java
if (stage == Stage.CLEAR || stage == Stage.REMOVE || stage == Stage.CLEAR_LEFTOVERS
        || ctx.scan().mustMineFirst(pos)) {
    return startMining(pos);
}
```
Mécanisme : `mustMineFirst` → `MINE_BLOCK` → délai de casse, usure 1, `award(0.05)`, action, drops sauf minerais, **avant** le test `lacking(cost)` (:55-60). Structurize `StructurePlacer.java:233-239` ne fait `BREAK_BLOCK` que si `!handler.allowReplace()`, vrai pour toute étape sauf CLEAR (`BuildingStructureHandler.java:315-318`) ; sinon `MISSING_ITEMS` d'abord (:339-342) puis `handleRemoval` (`IPlacementHandler.java:95-109`) : drops (minerais compris) dans l'inventaire, `removeBlock`, pose avec le seul délai de pose.
Impact : UPGRADE (sans CLEAR) : chaque case qui change coûte un délai de minage (jusqu'à 250 ticks à dureté 1, compétence 0) et une usure ; idem REPAIR après le CLEAR et la passe finale. Absent de la spec § 11.
Règle : § 6. Remède : retirer sans délai, drops gardés minerais compris, après le test des objets ; ou documenter.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-8 — MOYEN — Errance : ancre, distance, cadence et état WANDERING diffèrent de MC
`citizen/CitizenAI.java:29-31, 73, 99-119, 127-142` ; `citizen/CitizenState.java:5-10`
```java
private static final int WANDER_RADIUS = 10;
private static final int IDLE_MIN_TICKS = 200, IDLE_MAX_TICKS = 400;
private static final int WANDER_TIMEOUT_TICKS = 600;
```
Mécanisme : repos 200–400 ticks puis un point dans un carré ±10 **autour de l'hôtel de ville** ; MC `EntityAICitizenWander` (:82, 259-297) : transition toutes les 100 ticks, dès que la navigation finit, `walkToRandomPos(citizen, 10, speed)` → `BlockPosUtil.getRandomPosAround(start, 10)` : un point **à 10 blocs de la position courante**. `WANDERING` n'existe pas dans `CitizenAIState` (MC reste IDLE) ; la Javadoc « SP0 subset of MineColonies' CitizenAIState » est inexacte. La spec SP0 § 3.4 décrit ce comportement comme « la version réduite du CitizenAI », pas comme un écart ; seul le tirage sans recherche de chemin porte une déviation.
Impact : le citoyen HyColony reste attaché à l'hôtel de ville et se repose 10–20 s ; celui de MC se promène librement de proche en proche toutes les 5 s (comportement visible).
Règle : § 6. Remède : `Deviation from MC:` sur l'ancre, le repos et l'état, ou aligner (ancre = position courante, sans repos).
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-9 — MOYEN — Exception d'IA : `reset()` + pause fixe de 100 ticks, MC garde l'état et double le délai
`job/work/WorkerMachine.java:25-26, 82-87` ; `citizen/CitizenAI.java:94-97`
```java
private void onException(RuntimeException e) {
    LOG.log(…); lastError = e;
    machine.reset();
    pause.accept(EXCEPTION_DELAY);
```
Mécanisme : MC `AbstractEntityAIBasic.onException` (:353-363) : `timeout = EXCEPTION_TIMEOUT (100) * exceptionTimer ; setDelay ; exceptionTimer *= 2`, **sans reset** (`BasicStateMachine.checkTransition` appelle le handler et renvoie false). Côté citoyen, le handler MC ne fait que journaliser (`AbstractEntityCitizen.java:135-138`). La spec § 6 fixe le reset comme choix, mais § 3.1 présente le moteur comme « portage fidèle ».
Impact : une IA qui plante à chaque tick réessaie toutes les 100 ticks pour toujours au lieu de s'espacer ; le reset perd l'état de travail.
Règle : § 6. Remède : `Deviation from MC:` sur les deux `onException`, ou porter le backoff exponentiel sans reset.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### M-10 — MOYEN — Respawn : une seule position candidate, MC en essaie quatre
`citizen/CitizenManager.java:152-166` (`updateBodyIfNecessary`)
Mécanisme : `respawnPosition ?: lastPosition ?: hôtel de ville ?: centre`, et si cette position n'est pas chargée, abandon jusqu'au contrôle suivant (5 min). MC `CitizenData.updateEntityIfNecessary` (:922-975) construit `[nextRespawnPos?, lastPosition, workBuilding?, homeBuilding?]` et `spawnOrCreateCivilian` (:231-267) fait apparaître à la **première position chargée** de la liste.
Impact : un citoyen dont la dernière position est déchargée mais dont la hutte de travail est chargée ne réapparaît pas avant 5 minutes ; MC le fait apparaître à la hutte.
Règle : § 6. Remède : porter la liste de candidats.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Constats secondaires (une ligne chacun, comptés)

| # | Sév. | `chemin:ligne` | Constat |
|---|---|---|---|
| M-11 | MOYEN | `colony/territory/TerritoryIndex.java:30-42` ; `colony/Colony.java:137-145` | Revendication des bâtiments bornée par un **carré** (`max(|dx|,|dz|) > maxColonySize`), MC par un **disque** (`chunkDistanceSquared > maxColonySize²`, `ChunkDataHelper.java:239-255`) et revendique aussi les chunks que la **boîte du bâtiment** intersecte (:152-162, 279+) ; non documenté. |
| M-12 | MOYEN | `colony/ColonyState.java:24-31` | ACTIVE/UNLOADED sur `isMember` (≥ Friend, filtré au monde) ; MC sur `getRank(player).isColonyManager()` (Owner/Officer, `EventHandler.java:467-480`) sans filtre de monde ; « centre chargé » remplace « > 40 chunks » sans `Deviation from MC:` ; spec SP0 § 3.2 l. 134-135 dit « rang ≥ Friend ». |
| M-13 | MOYEN | `app/NeedsPlayerAnnouncer.java:18-21, 65-75` | Cite `StandardPlayerRequestResolver` « needs message », qui **n'envoie aucun message** (:104-136) ; le signal MC est la `RequestBasedInteraction` du citoyen (`AbstractBuilding.java:1376-1386`, marqueur « ! ») ; la ligne de chat aux `rankId <= OFFICER` est un ajout HyColony sans `Deviation from MC:` ni spec (et exclut un rang personnalisé « manager »). |
| M-14 | MOYEN | `building/BuildingManager.java:93-101` | Tick de tous les modules de tous les bâtiments ; MC `RegisteredStructureManager.java:292-301` ne tick que les bâtiments dont la position est chargée : une hutte de fermier dans un chunk déchargé revendique quand même un champ. |
| M-15 | MOYEN | `building/Building.java:161-168` (+ `logistics/warehouse/WarehouseStorage.java:54-74`, `WarehouseStockResolver.java:176-186`, `logistics/courier/DeliveryDrop.java:82`, `ForcedInsert.java:31-40`) | `containers()` = hutte **puis** étagères ; MC `AbstractBuildingContainer.getContainers` (:140-145) et le handler combiné (`TileEntityColonyBuilding.java:635-655`) = étagères **puis** hutte : l'entrepôt neuf se remplit d'abord dans le bloc de hutte, les `Delivery` et le rangement du livreur suivent cet ordre ; seul le ramassage est documenté (spec SP3a l. 73). |
| M-16 | MOYEN | `construction/builder/BuilderAI.java:178` ; `job/work/WorkerStock.java:156-168` | Le vidage du constructeur garde le premier outil non usé de **chaque** type et seulement le seau courant (`toolsAnd`), au lieu des règles que sa hutte déclare déjà (`KeepToolsModule(PICKAXE, AXE, SHOVEL)`, `BuildingResourcesModule.keepRules` = MC `BuildingBuilder` keepX + `getRequiredItemsAndAmount`) via `dumpKeepingHutRules`, que seul `CraftingWork.java:305` appelle : outil hors liste gardé à vie, matériaux des seaux suivants renvoyés puis re-cherchés ; règle du bâtisseur dans le socle partagé (§ 2). |
| M-17 | MOYEN | `citizen/Skills.java:68-76` ; `job/JobXp.java:49-52` ; `job/WorkerModule.java:106-110` | Plafond dans la boucle et XP remise à 0 au niveau 99 (MC `CitizenSkillHandler.java:195-210` : pas de plafond, reste conservé) ; le booléen de montée de niveau est ignoré, `Job.onLevelUp` (MC :243-259, aussi à l'embauche `WorkerBuildingModule.onAssignment`) n'existe pas (`DeliverymanAI.java:139` recalcule sa vitesse lui-même). |
| M-18 | BAS | `job/work/SyncRequests.java:85-106` | `pickUp` re-demande seulement le manquant ; MC `lookForRequests` (:606-636) recrée la requête entière si les objets livrés ne sont plus tous là. |
| M-19 | BAS | `logistics/courier/DeliveryPreparation.java:113-119`, `DeliveryDrop.java:77` | Chargement et livraison comparés par objet sans l'usure ; MC `gatherIfInTileEntity` (:569-579) et `:368-378` comparent avec `ItemStorage` (usure, NBT) ; `PickupRound.java:121` le fait bien. |
| M-20 | BAS | `construction/resources/Buckets.java:22-40` ; `BuildingResourcesModule.java:63-80` | Seaux de 18 piles, MC ≤ 17 (`max = slots − 9`, nouveau seau dès `total + stacks >= max`, `BuildingResourcesModule.java:218-244`) ; décrément du premier seau contenant l'objet, MC du seau **courant** (:255-282). |
| M-21 | BAS | `construction/builder/BuilderAI.java:235-236` ; `BuilderGathering.java:105-118` | `cancelAllFrom(hut)` à la fin du chantier (MC `executeSpecificCompleteActions` :372-460 ne fait que `resetNeededResources`) ; une seule requête pour la somme des deux seaux (MC une par seau, `checkOrRequestBucket`) ; commentés mais sans `Deviation from MC:`. |
| M-22 | BAS | `building/BuildingResolver.java:67-108` | Cinq petits écarts vs `BuildingRequestResolver` : pas d'exclusion de l'entrepôt (:67-70, sans effet sans stock minimum), livraisons depuis tous les conteneurs (MC : bloc de hutte seul, :136-160), pas d'enfant « reste » sous `minCount` (:103-133), prédicat parent absent (:77-91), `min(left, dispo)` au lieu de piles entières (:144-155). |
| M-23 | BAS | `request/resolver/PlayerResolver.java:88-93` ; `kernel/config/ColonyConfig.java` | Option MC `requestSystem.creativeResolve` (défaut false, `ServerConfiguration.java:201-203`) absente de `config.json` (§ 3) ; connue de `docs/research/config-inventory.md:103,168`. |
| M-24 | BAS | `crafting/job/CraftingWork.java:263-269, 132-141` ; `crafting/job/RecipeCounts.java:124-131` ; `crafting/job/CraftedOutputs.java:86-91` | Artisanat : `FAILED` compte 1 action au lieu de la récompense (64 pour le fermier, `EntityAIWorkFarmer.java:168-171`) ; `inHut` exclut les outils usés (MC compte tout) ; recette oubliée sans préfixe `Deviation from MC:` (écart 26 de la spec) ; filtre `isLoaded` sur l'entrepôt, à documenter. |
| M-25 | BAS | `app/ColonyManager.java:31` ; `app/action/ColonyAdministration.java:65-73` | Nom de colonie : 32 caractères refusés ; MC `TownHallRenameMessage` : 25, tronqué à 24 sans message. |
| M-26 | BAS | `app/action/HutActions.java:59-70` | Pas d'exception créatif à la pose à la main (MC `EventHandler.onBlockHutPlaced` :720-751 : `return player.isCreative()` hors colonie, second hôtel de ville permis) ; ordre des refus de fondation différent de `CreateColonyMessage.java:146-199`. |
| M-27 | BAS | `colony/permission/Permissions.java:161-163, 176-182` | Cas spéciaux MC absents (`hasPermission(Rank, Action)` :639-648 : Neutral jamais EDIT_PERMISSIONS/TELEPORT ; `fullyAbandoned`) ; `setRank` refuse OWNER et le propriétaire (MC accepte tout rang). |
| M-28 | BAS | `colony/Colony.java:86-105` | La suspension de 5 min arrête aussi `citizens.tickAi()` ; MC ne retarde que la machine de la colonie (`Colony.java:386-390`). |
| M-29 | BAS | `farming/hut/FarmerFieldsModule.java:81-88` ; `app/action/FieldActions.java:84-106` | Spirale remise à zéro quand le champ change (MC continue à l'ancien index, correction d'une bizarrerie) ; Assigner/Libérer vérifiés côté serveur en manuel (plus strict que MC) : à documenter comme l'écart « Sélection manuelle ». |
| M-30 | BAS | `job/work/WorkerStock.java:37-38, 102-104, 140` ; `citizen/CitizenManager.java:66-83` ; `job/WorkerModule.java:95-101` ; `citizen/CitizenNames.java:39-47` ; `citizen/CitizenManager.java:105` | `DUMP_RETRY_ACTIONS = 32` et règle `MIN_OPEN_SLOTS` absente, seulement un `ponytail:` ; `tickInactivity` compte sans corps vivant (MC `update` :1642-1648 sort avant) ; `hire` refuse tout employé (MC `assignTo` déplace un même métier) ; noms non uniques (MC `generateName` :626-680) ; réglage `MOVE_IN` de l'hôtel de ville non porté. |
| M-31 | BAS | `job/work/WorkerHands.java:32` ; `kernel/ai/TickRateStateMachine.java:105` ; `request/RequestTransitions.java:94-108` ; `request/RequestCanceller.java:46-58` | Citation « MC equipTool » introuvable ; `slownessFactor` non porté (sans effet à 20 TPS) ; `overrule` pose les livraisons **après** `onCancelling` (MC avant, :346-356) et sa Javadoc dit l'inverse ; `cancelAllFrom` annule toujours directement (MC `removeRequester` :584-593 passe par `onRequestCancelled`) : sans effet, tous les appelants passent des racines. |
| M-32 | BAS | `construction/workorder/WorkOrderType.java`, `WorkOrderRefusal.java`, `hut/LivingModule.java`, `blueprint/Blueprint.java`, `BlueprintEntry.java`, `BlueprintSource.java` ; `construction/shared/ClaimRadius.java:5` ; `colony/Colony.java:23-26` ; `citizen/CitizenManager.java:18-20` ; `citizen/CitizenAI.java:30-32` ; `colony/EventLog.java:9` | Classes et constantes portées **sans citation MC** (`WorkOrderType`, `LivingModule` = `LivingBuildingModule.getMaxInhabitants`, `Blueprint*` = Structurize `Blueprint`, `ClaimRadius` = `AbstractBuilding.getClaimRadius`/`BuildingTownHall`, `UPDATE_STATE_INTERVAL`/`CITIZEN_DATA_INTERVAL`/`DAYTIME_INTERVAL`/`SLOW_TICK`, `RESPAWN_CHECK_TICKS`…, `MAX_ENTRIES`). |

**PLANIFIÉ ou contrainte Hytale (non comptés)** : message de début de chantier (`BUILD_START`), suffixe « mode manuel » du message de fin, `killMobs` (hutte ≥ 4), refus « trop haut / trop bas » (`BUILDER_BUILDING_TOO_HIGH/LOW`), ruban de chantier, `walkAwayFrom` (`prePlacementLogic`), ordre en serpentin de `BlueprintIteratorDefault`, réglage « mode de construction » (recherche), niveau d'outil requis par bloc (`getCorrectHarvestLevelForBlock` : pas de paliers de récolte Hytale, à dire dans la Javadoc), ramassage au sol du livreur (`setCanPickUpLoot`), temps hors ligne (`processOfflineTime`, spec SP0 le prévoit, code non), stock minimum et améliorations d'entrepôt (BACKLOG), `MOVE_IN`.

**DÉJÀ CONNU (non comptés)** : écarts de déplacement du constructeur (BACKLOG « Déplacement du constructeur ») ; `CitizenAI` IDLE 20 ticks vs 10 (BACKLOG) ; `CourierTaskPicker` sans borne (BACKLOG).

## 3. Ce qui est bien fait sur cet axe

- **Machines à états** (`kernel/ai/TickRateStateMachine.java`, `TickingTransition.java:10-12, 29-30`) : ordre AI_BLOCKING → EVENT → STATE_BLOCKING → état courant, décalage `variant % tickRate`, bornes [1, 12 000], historique 20, `null` = pas de transition : conforme ligne à ligne à MC `BasicStateMachine`/`TickRateStateMachine`.
- **Système de requêtes** (`request/RequestAssigner.java`, `RequestTransitions.java`, `RequestCanceller.java`) : 13 états, priorités 200/150/125/100/50/0, attribution avec liste noire et métrique strictement meilleure, parent résolu après le dernier enfant, résolveurs qui n'assignent jamais leurs enfants, cascade d'annulation enfants d'abord : conformes à `RequestHandler`.
- **Logistique et artisanat** (`logistics/warehouse/WarehouseStockResolver.java:136-147`, `logistics/courier/CourierTaskPicker.java`, `crafting/module/CraftingModule.java`, `crafting/request/CraftingBatches.java:58-78`) : métrique d'aptitude, choix de la tâche, machine du livreur, `keepX`, `getMaxRecipes = 2^niveau × 5`, chance d'amélioration `min(5, 0,0625·count + 0,0625·niveau)`, lots et cycles : conformes ; `CraftingBatches` corrige même la boucle infinie de MC avec un écart documenté et testé.
- **Fermier** (`farming/job/FieldPass.java:220-223`, `farming/field/FarmField.java`) : `MAX_BLOCKS_MINED` 64, `getLevelDelay = max(1, 40 − Endurance/2)`, spirale `nextValidCell`, rayons et budget 20, `Stage.next` : conformes ; les 15 écarts de la spec SP3b-2 portent tous leur `Deviation from MC:`.
- **Colonie** : 27 `Action`, rangs et tables cumulatives, `OP_RANK`, `ClaimCell` 16, distance au spawn 2D, `EventLog` 100, `DenialNotices` 10 s, ordre des vérifications de `BlockUse` : conformes à `Permissions`, `ColonyPermissionEventHandler`, `CreateColonyMessage`.
- **Sources MC** : 249 fichiers de production citent `MC X`, 169 `Deviation from MC:` tous jugés cohérents par les six agents.

## 4. Tableau compact

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| HAUT | `construction/builder/StructureScan.java:53-55` ; `BuilderBlockWork.java:198-207` | M-1 chantier « terminé » dans un chunk non chargé |
| HAUT | `colony/permission/Permissions.java:161-163` | M-2 contournement opérateur/créatif absent de `hasPermission` |
| HAUT | `request/resolver/RetryingResolver.java:105` | M-3 réessais 11× plus rapides que MC (décision de plan non documentée) |
| HAUT | `request/RequestTransitions.java:116-119` | M-4 parent réassigné avec la liste noire héritée : blocage possible |
| HAUT | `request/resolver/PlayerResolver.java:115-121` | M-5 `onColonyUpdate` ne remonte pas aux parents |
| MOYEN | `colony/territory/TerritoryIndex.java:49-60` | M-6 règle de distance à la fondation |
| MOYEN | `construction/builder/BuilderBlockWork.java:49-54` | M-7 bloc à remplacer miné avec délai |
| MOYEN | `citizen/CitizenAI.java:29-31, 127-142` | M-8 errance ancrée à l'hôtel de ville |
| MOYEN | `job/work/WorkerMachine.java:82-87` | M-9 exception : reset + délai fixe |
| MOYEN | `citizen/CitizenManager.java:152-166` | M-10 respawn à une seule position candidate |
| MOYEN ×7, BAS ×15 | voir § 2 | M-11 à M-32 |
