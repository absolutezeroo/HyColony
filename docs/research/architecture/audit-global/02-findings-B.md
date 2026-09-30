# Audit global HyColony : 02, axe B, classes fourre-tout, brain methods, feature envy

```
ÉTAT : phase 2, axe B écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (métriques), rapports PMD du build,
relecteurs de périmètre. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Aucun fichier de production ne dépasse 366 lignes ; les listes de taille sont vides ; PMD (`GodClass`, `CyclomaticComplexity` 10/80, `CognitiveComplexity` 15, `NPathComplexity` 200, `TooManyMethods` 25, `CouplingBetweenObjects` 20…) est vert hors 16 exceptions, toutes dans le plugin sauf une. **Comparaison MC** : `Colony` 233 l. (MC ~2 000), `CitizenData` ~130 l. (MC ~2 200), `Building` 220 l. (MC `AbstractBuilding` ~2 150), `WorkerStock` 263 l. (MC `InventoryUtils` ~3 400), `TickRateStateMachine` + `WorkerMachine` (MC `AbstractAISkeleton` → `AbstractEntityAIBasic` ~1 900 → 5 niveaux) : **le port a évité chacune des piles de protocoles de MC**. Les candidats de la phase 0 ont été lus ; verdicts ci-dessous.

## 1. Constats

### B-1 — MOYEN (DÉJÀ CONNU) — Les six dettes PMD du plugin restent à découper, les découpages proposés tiennent
`config/pmd/known-violations.txt` (16 lignes) ; `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java` (342 l., GodClass WMC 73, TCC 1,5 %), `HytaleCitizenBodies.java` (354 l., GodClass), `HytaleUiPort.java` (246 l., CBO 28), `command/HyColonyCommand.java` (329 l.), `WorldRuntime.java`, `HytalePlayerInventory.java`
Mécanisme (mesures PMD du build, `plugin/build/reports/pmd/main.xml` : 18 violations tolérées) : `HytaleItemCatalog` a toujours deux groupes qui ne partagent rien (blocs : `computeBlock`, `toolType` ; objets : `computeItem`, `durability`, plus la branche houe `computeHoe` apparue depuis) ; `HytaleCitizenBodies` a désormais **trois** groupes (références/cycle de vie, navigation/téléportation, gestes : `lookAt`, `playAnimation`, `setHeldItem`, `setMovementSpeed`) ; `HyColonyCommand` a perdu six selftests mais garde `ConstructionSelfTest` (:230-319) et quatre sous-commandes ; `HytaleUiPort` garde le `Handler` anonyme (:85-108) et `open/playerHere/guarded` (:205-245).
Verdict par entrée : `HytaleItemCatalog` CONFIRMÉ (audit B § 2.2 : `HytaleBlockInfo` / `HytaleToolStats`) ; `HytaleCitizenBodies` CONFIRMÉ (audit B § 2.5 + gestes, comme le BACKLOG le note) ; `HyColonyCommand` CONFIRMÉ à moitié fait (§ 2.3) ; `HytaleUiPort` CANDIDAT (§ 2.4, extraire `FoundColonyWindow` et `PageOpener`) ; `WorldRuntime` ÉCARTÉ (racine de composition) ; `HytalePlayerInventory` ÉCARTÉ (`@SuppressWarnings` sur la seule méthode, § 2.1).
Règle : § 2, § 8 (listes qui ne font que rétrécir). Remède : les trois découpages, chacun retirant ses lignes de `known-violations.txt`.
Sévérité MOYEN · effort M par classe · confiance HAUTE · DÉJÀ CONNU oui (BACKLOG « Audit du code du 2026-09-29 », audit B § 2).

### B-2 — BAS — `CraftingWork` (366 l.) : une responsabilité, mais 66 lignes au-dessus de la cible et un pas dupliqué avec le bâtisseur
`core/src/main/java/dev/hycolony/core/crafting/job/CraftingWork.java:44-50, 291-365` ; `core/src/main/java/dev/hycolony/core/construction/builder/BuilderGathering.java:122-133`
```java
private @Nullable Request currentRequest; private @Nullable Chosen currentRecipe; private boolean dumped; private @Nullable Needed needed;
```
Mécanisme : 22 méthodes toutes ≤ 40 l., une seule source MC (`AbstractEntityAICrafting`), quatre collaborateurs déjà extraits ; les étapes génériques de `AbstractEntityAIBasic` (`inventoryNeedsDump`, `dump`, `needsItem`, `waitForRequests`, :291-365) sont réécrites à l'identique dans `BuilderGathering` (`pending()` → `receiveAtHut()`). Seul `FarmerAI` utilise la classe ; le prochain artisan (fournaise, statistiques MC) la fera passer les 400. Verdict phase 0 : CANDIDAT (LOC > 300, 50 méthodes estimées, 53 branches), ÉCARTÉ comme fourre-tout à la lecture (une responsabilité), retenu comme Large Class.
Règle : § 2 (300 visées). Remède : un pas `WorkerRequests` dans `job/work` (≈ 40 l. de moins ici et la copie du bâtisseur en moins).
Sévérité BAS · effort M · confiance HAUTE · DÉJÀ CONNU non.

### B-3 — BAS — `construction/builder` est à 15 fichiers : le prochain ajout casse `checkFileSizes`
`core/src/main/java/dev/hycolony/core/construction/builder/` (15 fichiers, limite § 1) ; idem `kernel/port`, `request`, `plugin/adapter` (15 chacun)
Mécanisme : tout nouveau collaborateur (`BuilderDump`, un état de plus) échoue au build ; découpe naturelle `builder/site` (`BuildSite`, `StructureScan`, `StructureLoader`, `WorkSpot`) ou `builder/motion` (`BuilderWalker`, `BuilderGestures`, `WorkSpot`, `BuilderTimings`), chacune obligeant à rendre publics des points d'entrée aujourd'hui package-private (`BuildSite`, `WorkSpot.Spot`, `BuilderContext`).
Règle : § 1. Remède : décider la découpe avant le prochain fichier, pas dans l'urgence d'un correctif.
Sévérité BAS · effort M · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus (candidats de la phase 0 lus et écartés)

- `WorkManager` (324 l., 48 méthodes) : traité en A-2 (responsabilité), pas un god class (ATFD faible, une seule collection possédée).
- `HutActions` (279 l., 40 méthodes) : traité en A-1.
- `CraftingModule` (272 l., 38 méthodes) : port direct de `AbstractCraftingBuildingModule`, une responsabilité (recettes apprises, améliorations, compatibilité), toutes les méthodes ≤ 40 l.
- `WandActions` (271 l.) : traité en A-5.
- `WorkerStock` (263 l., 35 méthodes) : une responsabilité (objets du travailleur) ; la règle propre au bâtisseur (`toolsAnd`) est traitée en M-16.
- `StructurePlan` (260 l.) : listes de travail précalculées, fonction pure du plan ; testée en performance (20 000 blocs).
- `CraftingProductionResolver` (259 l.), `WarehouseStockResolver` (215 l.) : ports directs de leurs résolveurs MC.
- `ColonySerializer` (246 l.), `RequestSerializer` (236 l.) : sérialiseurs par objet, déjà découpés (`RequestableJson`, `SavedRequests`, `BuildingSerializer`, `CitizenSerializer`).
- `CitizenAI` (240 l.), `RequestManager` (239 l., façade), `Colony` (233 l.), `CitizenManager` (220 l.), `Building` (220 l.) : agrégats fidèles à MC en bien plus petit.
- `FarmWork` (230 l., 43 branches), `FieldPass` (232 l.) : deux moitiés de `EntityAIWorkFarmer`, chacune une responsabilité.
- `CutterPage` (228 l., indentation 40) : une fenêtre ; `CutterDrawing` extrait.
- `SubPlugins` (237 l.) : cycle de vie des packs.
- Brain methods : aucune méthode > 40 lignes trouvée par les relecteurs dans le cœur ; dans le plugin, `HytaleWorldBlocks.breakBlock`/`place` (audit B § 2.5) ont été découpés depuis (`HytaleBlockBreaker`, `HytaleBlockStates`), et `HyColonyCommand.SelfTest` reste (B-1).
- Feature envy : `app/wand` et `app/goggles` enchaînent `manager.context().ports()` (audit A § 6, DÉJÀ CONNU, « plus tard, sous forme de règle »).
- Duplication : `HutActions.hire/fire/setHiring` triplet, `CourierResolver`/`WarehouseStockResolver`, `PlayerResolver`/`RetryingResolver` : DÉJÀ CONNU (audit duplication, CPD max 88 tokens).

## 3. Ce qui est bien fait

- `core/src/main/java/dev/hycolony/core/citizen/CitizenData.java` (~130 l.) contre `CitizenData` MC (~2 200 l., 115 méthodes) : identité, compétences (`Skills`), métier, positions ; bonheur, deuil, nourriture, maladie viendront en sous-objets (SP4) et non dans la classe.
- `core/src/main/java/dev/hycolony/core/request/RequestManager.java` (239 l.) contre `StandardRequestManager` (~750 l.) : même correspondance de handlers en `RequestAssigner` (183 l.), `RequestTransitions` (121 l.), `RequestCanceller` (76 l.) (audit B § 1.1).
- `gradle/file-size-allowlist.txt` et `gradle/package-size-allowlist.txt` vides, `config/pmd/known-violations.txt` passé de 22 à 16 lignes depuis l'audit B : les listes rétrécissent comme le § 8 l'exige.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN (connu) | `config/pmd/known-violations.txt` ; `plugin/.../adapter/HytaleItemCatalog.java`, `HytaleCitizenBodies.java`, `HytaleUiPort.java`, `command/HyColonyCommand.java` | B-1 dettes PMD du plugin : découpages toujours valables |
| BAS | `core/.../crafting/job/CraftingWork.java:291-365` | B-2 366 l., pas `AbstractEntityAIBasic` dupliqué avec `BuilderGathering` |
| BAS | `core/.../construction/builder/` (15), `kernel/port` (15), `request` (15), `plugin/adapter` (15) | B-3 paquets à la limite de 15 fichiers |
