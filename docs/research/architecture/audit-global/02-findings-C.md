# Audit global HyColony : 02, axe C, couplage, cycles, direction des dépendances

```
ÉTAT : phase 2, axe C écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (cmd 3, 4, 13, arêtes réelles vs
matrice), FeatureDependenciesTest, ArchitectureTest, rapport « ports ». Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Les frontières sont vérifiées par le build (`ArchitectureTest`, `FeatureDependenciesTest`, `checkModApis`) ; **chaque arête autorisée par la matrice est utilisée** (aucune ligne morte) ; le seul cycle de premier niveau est l'agrégat `Colony`, comme `IColony` chez MC (décision écrite, `FeatureDependenciesTest.java:14-18`) ; aucun service locator (`getInstance`/`INSTANCE` : 0 hit) ; fan-out > 20 seulement dans les racines de composition et les adaptateurs. Reste des cycles de sous-paquets hors garde.

## 1. Constats

### C-1 — BAS — Cycles entre sous-paquets hors de toute règle ArchUnit
`core/src/main/java/dev/hycolony/core/app` ↔ `app/action` et `app/view` ; `building` ↔ `building/module` ; `farming/job` ↔ `farming/hut` ; `request` ↔ `request/resolver` (phase 0, cmd 13 : cycles directs par `import`)
```java
// ArchitectureTest.java : beFreeOfCycles() sur "dev.hycolony.core.crafting.(*).." et "…construction.(*).." seulement
```
Mécanisme : `ArchitectureTest` n'impose `slices().beFreeOfCycles()` qu'à `construction` et `crafting`. `app` ↔ `app.action` (`ColonyManager` possède les actions, les actions appellent `ColonyManager`) et `building` ↔ `building.module` sont intrinsèques ; `farming/job` ↔ `farming/hut` (`FarmWork` ↔ `FarmerFieldsModule`/`FieldWalk`) et `request` ↔ `request/resolver` (`RequestSerializer` → résolveurs, résolveurs → racine) ne le sont pas et ne sont pas gardés : le prochain import inverse passe le build.
Impact : la structure « point d'entrée public, reste package-private » (§ 1) ne tient que par discipline pour ces paquets.
Règle : § 1. Remède : étendre `beFreeOfCycles()` à `logistics` (vrai aujourd'hui : courier → pickup/warehouse), `farming` et `request` après avoir cassé les deux cycles évitables ; documenter `app` ↔ `app.action` comme voulu.
Sévérité BAS · effort S · confiance HAUTE (cycles) / MOYENNE (coût de les casser) · DÉJÀ CONNU partiel (`docs/research/architecture/best-practices.md` § 1 recommande `beFreeOfCycles()` sur tout le cœur).

### C-2 — BAS — Racine du plugin en cycle avec huit sous-paquets
`plugin/src/main/java/dev/hycolony/plugin/` ↔ `adapter`, `block`, `command`, `goggles`, `npc`, `prefab`, `subplugin`, `ui/wand` ; `adapter` ↔ `block`, `ui`, `ui/citizen` ; `ui` ↔ `ui/hut`, `ui/logistics`
Mécanisme : `WorldRuntime`/`WorldRuntimes`/`IdMap` (racine) sont importés par les sous-paquets qu'ils construisent ; `adapter` importe `block` (`HutBlockSystems.byBlockId`) et `ui` (`HytaleUiPort` ouvre les pages). Aucune règle ne fige le plugin (les cœurs seuls ont ArchUnit, les plugins n'ont pas de tests).
Impact : faible tant que le plugin reste un ensemble d'adaptateurs ; aucun mécanisme n'empêche `adapter` de dépendre de `ui/hut` demain.
Règle : § 1 (sous-paquets par sous-domaine). Remède : au prochain découpage du plugin (B-1), sortir `WorldRuntime`/`WorldRuntimes` dans un paquet `runtime` que tout le monde importe et qui n'importe que des interfaces ; pas de règle ArchUnit possible sans tests de plugin.
Sévérité BAS · effort M · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- Cycle `colony` ↔ {building, citizen, construction, crafting, farming, job} : décision écrite (`FeatureDependenciesTest.java:14-18`, audit A § 5, « on fige les frontières, on ne les supprime pas ») ; la matrice ne peut que rétrécir et aucune arête n'est morte.
- `colony → crafting` (3 imports), `colony → farming` (2) : `ColonyRegistries` (recettes, champs) et `Colony` les possèdent, comme `IColony.getRecipeManager` chez MC.
- Fan-out > 20 : `HytaleCitizenBodies` 43, `HytaleUiPort` 41, `WorldRuntime` 40, `HyColonyCommand` 39 (plugin, dettes B-1), `CutterPage` 29, `ColonySerializer` 29, `HyColonyPlugin` 28 : adaptateurs et racines, imports d'API Hytale ou de types de modèle.
- Loi de Déméter : `manager.context().ports()` ×10 dans `wand`/`goggles` : DÉJÀ CONNU (audit A § 6).
- Maps statiques `Map<UUID, X>` : `Highlights.ACTIVE` seule, traitée en D.
- Visibilité « tout public » de `app/ui` (12/12), `citizen` (11/11), `building` (8/8), `kernel/port` (15/15), `request/model` (11/11), `farming/field` (6/6) : records de vue, ports et modèles consommés par d'autres paquets ; imposé par l'usage inter-paquets (relecteurs). Bien encapsulés : `app/view` 1/11, `app/wand` 1/8, `construction/builder` 4/15, `logistics/courier` 3/13.
- `job → logistics` (1 import) : autorisé par la matrice, `TaskQueues` (audit A l'a accepté).
- Deux interfaces de capacité hors de `building` (`job.HiringListener`, `logistics.pickup.KeepsItems`) : conséquence voulue de `buildingDependsOnNeitherConstructionJobLogisticsNorCrafting`.

## 3. Ce qui est bien fait

- `core/src/test/java/dev/hycolony/core/FeatureDependenciesTest.java:20-73` : la matrice figée des dépendances de premier niveau, avec `ensureAllClassesAreContainedInArchitecture()` qui refuse un nouveau paquet non déclaré (commit dfbb5298) ; `app` et `root` inaccessibles, `kernel` isolé, `request` → `kernel` seul.
- `build-logic/src/main/kotlin/hy.java-checks.gradle.kts` (`checkModApis`) : un mod n'atteint un autre que par ses `api`, `compileOnly`, jamais embarqué (`bundled` = son cœur seul).
- `core/src/main/java/dev/hycolony/core/request/` : `request` ne connaît ni bâtiments ni construction (`ArchitectureTest.requestDoesNotDependOn…`), les fonctionnalités se branchent par `Requester`/`Resolver` ; `crafting` nomme sa recette par id.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| BAS | `core/.../farming/job` ↔ `farming/hut`, `request` ↔ `request/resolver` (+ `app`, `building` intrinsèques) | C-1 cycles de sous-paquets non gardés |
| BAS | `plugin/src/main/java/dev/hycolony/plugin/` ↔ 8 sous-paquets | C-2 racine du plugin cyclique |
