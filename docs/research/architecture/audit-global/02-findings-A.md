# Audit global HyColony : 02, axe A, structure et principes (SOLID, GRASP)

```
ÉTAT : phase 2, axe A écrit. Code audité : commit 3e2e70ca. Sources : relecteurs cœur (app/colony/building/citizen/
kernel ; construction/job), plugin, mods frères, rapport « ports ». Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Ports et adaptateurs tenus (16 ports, un adaptateur `Hytale*` et un `Fake*` chacun, aucun import Hytale dans un cœur), composition plutôt qu'héritage (une classe abstraite, `Job`, profondeur 1), types de bâtiments et métiers par modules et registres (ouvert/fermé respecté : un métier s'ajoute dans `CoreFeatures` sans toucher `CitizenAI`, `ColonySerializer` ni `Job`). Les constats sont des classes d'action qui cumulent des responsabilités et quelques règles de jeu restées dans les plugins.

## 1. Constats

### A-1 — MOYEN — `HutActions` cumule quatre blocs de responsabilités
`core/src/main/java/dev/hycolony/core/app/action/HutActions.java:24-27, 38-115, 118-159, 167-220, 223-258`
```java
 * What players do to huts: place and remove them (MC AbstractBlockHut), pick a deconstructed one up, and staff it
 * (hire, fire, hiring mode, builder mode). A managing action needs MANAGE_HUTS and re-shows the hut's window.
```
Mécanisme : 279 lignes, 12 méthodes publiques : règles de placement (`checkPlacement`, `checkHutRules`, `checkOutsideColonies`, `spawnDistanceRefusal`, ~80 l. de lecture pure, réutilisées par `WandPlacement` et `WandPaste`), cycle de vie du bloc (`place`, `onRemoved`, `breakBy`, `remove`, `pickUp`), embauche (`hire`, `fire`, `setHiring`) et réglages du constructeur (`setBuilderMode`, `setFillBlock`), qui font importer `construction.shared.BuilderSettingsModule` dans l'action générique des huttes.
Impact : chaque métier à réglages (bûcheron…) rallonge cette classe (audit A § 7 point 5) ; la Javadoc a déjà deux « and ».
Règle : § 2 (« une seule responsabilité… sans “et” » ; extraire des collaborateurs). Remède : `HutPlacementRules` (les quatre règles) et `BuilderSettingsActions` (les deux réglages, avec `ManagedHut`) ; `HutActions` garde bloc + embauche.
Sévérité MOYEN · effort M · confiance HAUTE · DÉJÀ CONNU partiel (triplet hire/fire dans l'audit duplication ; couplage `BuilderSettingsModule` dans l'audit A ; la scission non).

### A-2 — MOYEN — `WorkManager` porte les règles « qui peut bâtir quoi » et la diffusion aux membres
`core/src/main/java/dev/hycolony/core/construction/workorder/WorkManager.java:268-289, 320-323, 32-33, 89-105`
```java
public static boolean canBuildIgnoringDistance(BlockPos builderHut, int builderLevel, WorkOrder o) {
```
Mécanisme : `isAllowed`, `canBuild`, `canBuildIgnoringDistance`, `isEmployedBuilder`, `ORDER` sont statiques, ne touchent pas l'état du gestionnaire et sont appelés depuis `WorkOrderValidation`, `WorkOrderAssignment`, `ManualSelection`, `hut/WorkOrderListModule` (MC `WorkOrderBuilding.canBuild`) ; `announceCreated` duplique la boucle membres/`RECEIVE_MESSAGES` de `BuildCompletion.apply:39-43`. 324 lignes, 48 méthodes, 51 branches : la deuxième plus grosse classe du cœur.
Impact : responsabilité « possède les ordres **et** dit qui peut les prendre » ; `workorder` importe `job.WorkerModule` et `shared.BuilderHut` pour ces seules règles.
Règle : § 2. Remède : `WorkOrderRules` package-private (≈ 45 l.) et `Colony.broadcast(Msg)` pour les deux boucles ; `workorder` a 10 fichiers, de la place.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU partiel (diffusion : audit duplication § 1).

### A-3 — MOYEN — Un crochet de test vit dans le chemin chaud de production
`core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java:30, 66-70, 216-219`
```java
public void tickData() {
    if (failNextTick) { failNextTick = false; throw new IllegalStateException("test failure"); }
```
Mécanisme : `failNextTickForTest()` est public et n'a qu'un appelant, `core/src/test/.../colony/ColonyTest.java:90`. `tickData` (toutes les 60 ticks) porte un test de drapeau et un `throw` de production.
Impact : n'importe quel appelant du cœur ou du plugin peut faire suspendre une colonie 5 minutes ; responsabilité étrangère à la classe.
Règle : § 2 (visibilité minimale, une responsabilité), § 3 (code fragile). Remède : supprimer le crochet ; dans `ColonyTest`, faire lever l'exception par un faux (`FakeBodies.position` qui lève une fois, appelée par `tickData` l. 77).
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non. (`CitizenManager` est en cours de modification par une autre session : à faire après.)

### A-4 — MOYEN (DÉJÀ CONNU) — Les règles de la table de découpe vivent dans `domum/plugin`, sans test
`domum/plugin/src/main/java/dev/hydomum/plugin/cutter/CutterCrafting.java:104-118` (+ `CutterCraftClicks.java:47-51`, `CutterSlots.java:61-70`, `CutterCraftQueue.java:94-119`, `CutterPage.java:41`, `CutterDrawing.java:129`)
```java
if (!(now instanceof CutterCraft.Ready ready) || !ready.key().blockTypeKey().equals(asked.key().blockTypeKey())) {
    say(player, "hydomum.ornament.cutter.changed"); return false; }
int crafts = Math.min(request.crafts(), CutterCraft.maxCrafts(ready, slots));
```
Mécanisme : plafond `min(crafts, maxCrafts)`, revalidation « les matériaux ont changé », tout-ou-rien de `take`, une fabrication par `craftMillis`, arrêt au premier échec ; la quantité ×10 vit deux fois dans le plugin (`BATCH = 10`, `max < 10`) et nulle part dans le cœur à côté de `CutterCraft.MAX_BATCH`.
Impact : l'invariant de la spec DO-2 (l. 66, « à tout échec, rien n'est consommé ni donné ») n'a aucun test ; une régression passe le build.
Règle : § 1, § 8. Remède : un `CutterCraftPlan` dans `domum/core/cutter` qui rend « combien fabriquer et quoi retirer » à partir des contenus avant/après, `take` ne faisant qu'exécuter.
Sévérité MOYEN · effort M · confiance HAUTE · DÉJÀ CONNU oui (`docs/BACKLOG.md`, sauf le `10` dupliqué).

### A-5 — BAS — `WandActions` fabrique sa vue au lieu d'un `WandViews`
`core/src/main/java/dev/hycolony/core/app/wand/WandActions.java:210-224, 240-269`
```java
private void show(UUID player, WandSession s) {
    List<String> huts = offered(player, s.style()).stream().map(BuildingType::id).toList();
    WandView view = new WandView(styles(), huts, maxLevel(s), ...
```
Mécanisme : 271 lignes, 13 méthodes publiques ; `show`, `offered`, `hasPlan`, `carries`, `maxLevel`, `styles` (~60 l.) construisent le `WandView`, alors que hutte, hôtel de ville, citoyen et presse-papiers ont leur `app/view/*Views`.
Règle : § 2, § 7. Remède : `WandViews` package-private dans `app/wand`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### A-6 — BAS — Règles de jeu portées par le plugin seul
`plugin/src/main/java/dev/hycolony/plugin/npc/CitizenFireImmunitySystems.java:28-31, 110-117` ; `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java:52, 330-336`
```java
 * Deviation from MC: citizens are immune to fire (user request), independent of invulnerability.
```
```java
private static final double TELEPORT_Y_RANGE = 10;
```
Mécanisme : « le feu et les braises ne blessent pas un citoyen, les pièges si » n'existe ni dans le cœur ni dans une spec ; la constante MC de `completeStuckAction` (10 blocs) vit dans l'adaptateur et n'est appliquée qu'en vertical (`MotionControllerWalk.translateToAccessiblePosition`), là où MC cherche aussi à l'horizontale (`findAround(world, desired, 10, 10, …)`) : cible dans un mur → « no free spot » → `StuckHandler` abandonne là où MC pose le citoyen à côté.
Règle : § 1 (tempéré : l'immunité n'a d'expression que par les effets Hytale), § 6. Remède : une règle documentée dans le cœur que le plugin applique ; `Deviation from MC:` sur la téléportation, ou balayage horizontal, ou portée passée par le port.
Sévérité BAS · effort S · confiance HAUTE (feu), MOYENNE (téléportation : source MC citée de mémoire) · DÉJÀ CONNU non.

### A-7 — BAS — `Rank.add/remove` publics sur des rangs exposés en lecture
`core/src/main/java/dev/hycolony/core/colony/permission/Rank.java:23-29` ; `Permissions.java:184-186`
```java
public void add(Action action) { permissions |= action.mask(); }
```
Mécanisme : `Permissions.ranks()` renvoie une `unmodifiableMap` de `Rank` mutables ; les seuls appelants sont dans le paquet. Toute classe peut changer les droits d'un rang sans `EDIT_PERMISSIONS` ni `markDirty`.
Règle : § 2 (visibilité minimale). Remède : package-private, comme `setColonyManager`/`setHostile`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### A-8 — BAS — `LivingModule.capacity` : échafaudage « pour SP4 » sans appelant
`core/src/main/java/dev/hycolony/core/construction/hut/LivingModule.java:6-10`
```java
/** Residence capacity: as many citizens as the building's level. Used from SP4 on. */
public int capacity(Building b) {
```
Règle : § 2 (pas de code « pour plus tard »). Remède : supprimer la méthode (le module vide reste le marqueur), ou la brancher avec son test quand SP4 arrive.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- `Building.module(Class)` ne rend qu'un module ; 36 classes appellent `context()` ; job inconnu : DÉJÀ CONNU (audit A).
- `RequestManager` façade à 25 méthodes : audit B § 1.1, légitime.
- `WorldRuntime` 41 imports : racine de composition (audit B).
- `ColonyContext` record de 13 composants : pas un service locator (audit A § 6).
- `WorkerModule` (173 l.) : port direct de `WorkerBuildingModule`, une responsabilité.
- `SubPlugins` (237 l.) : une responsabilité, cycle de vie des packs.
- `HytaleGameClock` seuils 0.25/0.75 : échelle interne de Hytale (`WorldTimeResource.java:278-285`), pas une règle de jeu.
- `HytaleItemCatalog` replis de dureté/pile : traductions d'API, `ToolScale` du cœur tient les formules.
- `BlockUseProtectionSystem.java:83` (« hors colonie, libre ») : duplique une réponse que le cœur donnerait pareil.
- `Job` porte-tout, `sealed` sur `Requestable`, un module Gradle par fonctionnalité : faux problèmes déjà écartés (audit A).
- `ColonyFoundation.confirm` accepte un `HutPlacement.Allowed` comme `FoundNewColony` : cas improbable (colonie sans hôtel de ville étendue entre `begin` et `confirm`) ; un `instanceof` serait gratuit.

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/CoreFeatures.java:23-32` : un métier ou un type de bâtiment s'ajoute par une ligne d'enregistrement ; `CitizenSerializer`, `ColonySerializer`, `CitizenAI`, `Job` n'ont aucun `switch` par type (vérifié par l'audit A, toujours vrai).
- `core/src/main/java/dev/hycolony/core/job/work/` (`WorkerMachine`, `WorkerStock`, `SyncRequests`, `ToolRequests`, `WorkerHands`, `WorkDelay`) : le socle de `AbstractEntityAIBasic` extrait en collaborateurs composés, partagés par les quatre métiers sans classe mère.
- `core/src/main/java/dev/hycolony/core/construction/builder/BuilderContext.java:46-58` : résolution des ports une fois au bord, collaborateurs à dépendances étroites (modèle cité par l'audit A, toujours suivi par `CourierContext`, `CraftingWorkContext`, `FarmWorkContext`).
- `vanilla/core/src/main/java/dev/hyvanilla/core/FlowerPot.java` : la règle « comme Minecraft FlowerPotBlock » dans le cœur avec 8 tests, le plugin n'ordonne que les effets.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `core/.../app/action/HutActions.java:24-27, 38-258` | A-1 quatre blocs de responsabilités |
| MOYEN | `core/.../construction/workorder/WorkManager.java:268-289, 89-105` | A-2 règles d'éligibilité et diffusion à extraire |
| MOYEN | `core/.../citizen/CitizenManager.java:30, 66-70, 216-219` | A-3 crochet de test en production |
| MOYEN (connu) | `domum/plugin/.../cutter/CutterCrafting.java:104-118` | A-4 règles du cutter dans le plugin |
| BAS | `core/.../app/wand/WandActions.java:210-269` | A-5 vue construite dans l'action |
| BAS | `plugin/.../npc/CitizenFireImmunitySystems.java:28-31` ; `adapter/HytaleCitizenBodies.java:52` | A-6 règles de jeu dans le plugin |
| BAS | `core/.../colony/permission/Rank.java:23-29` | A-7 `add/remove` publics |
| BAS | `core/.../construction/hut/LivingModule.java:6-10` | A-8 code « pour SP4 » |
