# SP3b-2 fermier : plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans. Étapes à cocher (`- [ ]`).

**Objectif :** une hutte de fermier cultive les champs posés par le joueur, comme dans MineColonies. Sa récolte (cultures et essence de vie) va à la colonie, et elle fabrique ses propres graines avec l'essence.

**Architecture :**
- **Cœur :** un nouveau paquet racine `dev.hycolony.core.farming`, avec :
  - `field` : champ, rayons, spirale, registre de la colonie ;
  - `hut` : type de hutte, module de champs, réglages ;
  - `job` : métier, IA, étapes agricoles ;
  - à la racine, le port `FarmingAccess`.
- **Composition sans héritage** (`ArchitectureTest`) : `FarmerJob extends Job implements Crafter` et `FarmerAI implements JobAI`, qui combine `CraftingWork` (SP3b-1) et un nouveau `FarmWork`.
- **Plugin :** le port, le bloc « Champ », la fenêtre du champ, l'onglet Champs, les assets et les plans de test.

**Pile technique :** Java 25, Gradle, JUnit 5, Gson, ArchUnit, palantir-java-format (Spotless), PMD, Error Prone et NullAway ; API serveur Hytale 0.6.8 (`build/vineflower/hytale-server`).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-sp3b-farmer-design.md`.
- **Lis-la en entier**, ainsi que `CLAUDE.md`, `docs/research/sp3b-mc-farmer.md` (« MC § ») et `docs/research/sp3b-hytale-farming.md` (« H § »).
- Source MC : `github.com/ldtteam/minecolonies`, `version/main`, commit `6b4e03e337fcb886b4ab8cbe21f9b6731818769d`. Relis `EntityAIWorkFarmer.java` avant de porter une méthode.

**Qui fait quoi :**
- **Tasks 1 à 9 (cœur, Java pur)** : réalisables sans le jar Hytale, donc dans une session cloud.
- **Tasks 10 à 14 (plugin)** : demandent les sources décompilées et le jar, donc se font **en local**.

Une session sans le jar s'arrête après la Task 9 et le dit dans son résumé.

## Contraintes globales

- `core/` ne touche jamais `com.hypixel`. Aucune classe n'hérite d'un sous-type de `Job` ou de `JobAI` **[ArchitectureTest]**. `farming` ne dépend ni de `construction`, ni de `colony.action`, `colony.view` ou `colony.persistence`.
- **Paquets déjà à 15 fichiers : `colony`, `building`, `kernel/port`.** Aucun fichier n'y est ajouté.
- Taille des fichiers : 400 lignes au plus (300 visées), 40 lignes par méthode, 5 paramètres **[checkFileSizes, PMD]**.
- Javadoc courte sur chaque classe et méthode non triviale, avec sa source MC. Un écart porte un commentaire `Deviation from MC: …`. Pas de commentaire séparateur. `./gradlew spotlessApply` avant chaque commit.
- **Constantes MC exactes :**
  - `MAX_BLOCKS_MINED = 64`, `DEFAULT_DELAY = 40`, `XP_PER_HARVEST = 0.5`, `XP_PER_BLOCK = 0.05`, `MAX_DEPTH = 5` ;
  - `MAX_RANGE = 20`, `DEFAULT_RANGE = 5`, 1681 cases ;
  - délais : `PREPARING` 20, états du champ 5 ; tick de colonie des huttes 500.
- **Persistance :** le schéma passe de 4 à 5 (`"fields": []` à la racine de la colonie), avec une fixture `colony-v4-*.json` qui se charge. Lecture tolérante.
- **Textes :** clés en en-US **et** fr-FR ; un paramètre traduit imbriqué va sur `.TextSpans`.
- **Processus :**
  - TDD ;
  - `./gradlew build` vert avant chaque commit ;
  - `git add <chemins>` explicites ;
  - commits en `type(module): description` ;
  - **ne jamais lancer le serveur Hytale**.
- Fin de message de commit : les lignes demandées par la session qui exécute.

## Points à surveiller en relecture

1. **Hutte retirée pendant qu'elle travaille un champ** : le champ est libéré, puis repris par une autre hutte au tick suivant. Rien ne reste bloqué (Task 4, `removingTheHutFreesItsFields`).
2. **Bloc Champ cassé en plein passage** : le fermier abandonne le champ sans exception, au prochain `workAtField` (Task 7, `fieldBrokenMidPassIsDropped`).
3. **Champ sans graine** : il n'est jamais assigné, ni en automatique ni en manuel (Task 4, `seedlessFieldIsNeverAssigned`).
4. **Graines introuvables** (ni dans l'inventaire, ni dans la hutte, ni fabricables) : une seule requête ouverte, l'étape passe à PLANTED sans planter, et le fermier ne boucle pas (Task 6, `noSeedAnywhereAsksOnceAndSkipsPlanting`).
5. **Rayons à la limite** : 20/0/0/0 est accepté, 6/5/5/5 est refusé, et la spirale ne sort jamais du champ (Task 2, `spiralNeverLeavesTheField`).

---

### Task 1 : port d'agriculture, houe, fake

**Fichiers :**
- Créer : `farming/FarmingAccess.java`, `farming/CropState.java` ; `core/src/test/java/dev/hycolony/core/testing/farming/FakeFarming.java`.
- Modifier : `kernel/item/ToolType.java` (+ `HOE`), `colony/ConstructionPorts.java` (+ `FarmingAccess farming`), `testing/TestContexts.java` (`public FakeFarming farming`), et **chaque** `new ConstructionPorts` du plugin avec `FarmingAccess.NONE` provisoire (`grep -rn "new ConstructionPorts" core plugin`).
- Tester : `testing/farming/FakeFarmingTest.java`, qui teste le fake lui-même, dont les règles serviront partout.

**Interfaces produites :**

```java
public enum CropState { NONE, GROWING, MATURE }
/** Port: Hytale soil and crops (H § 2-4). Never throws; unknown or unloaded = false / NONE / empty. */
public interface FarmingAccess {
    FarmingAccess NONE = /* anonymous: everything false / NONE / empty */;
    boolean isTillable(BlockPos pos);          // one of the 16 soils of H § 2.1
    boolean isTilled(BlockPos pos);            // Soil_Dirt_Tilled (any state)
    boolean till(BlockPos pos);                // H § 4, without bit 2
    boolean isFertilized(BlockPos pos);
    boolean fertilize(BlockPos pos);           // TilledSoilBlock.setFertilized + setTicking
    CropState crop(BlockPos pos);              // the block AT pos (a crop stands above its soil)
    boolean plant(BlockPos pos, ItemKey seed); // the seed's crop block at pos, without bit 2
    List<ItemAmount> harvest(BlockPos pos);    // harvest drops; normal -> air, eternal -> back to Stage1
    boolean isFieldBarrier(BlockPos pos);      // fence, gate, wall (MC isNoPartOfField)
    List<ItemKey> seeds();                     // crop seeds, sorted by id
}
```

`FakeFarming` garde des ensembles publics : `tillable`, `tilled`, `fertilized` et `barriers`, plus des cartes `crops` (position → `ItemKey` de la graine) et `cropState`.
- `plant` refuse une case qui a déjà une culture.
- `harvest` renvoie `[crop 1, Ingredient_Life_Essence 3]` pour une culture `MATURE`, puis vide la case, sauf si la graine finit par `_Eternal` : la culture revient alors à `GROWING`.
- `seeds()` renvoie `Plant_Seeds_Wheat` et `Plant_Seeds_Wheat_Eternal`.

- [ ] **Step 1 : tests qui échouent** (`FakeFarmingTest`) :
  - `tillThenPlantThenHarvestEmptiesANormalCrop` ;
  - `eternalCropRegrowsAfterHarvest` ;
  - `plantRefusesAnOccupiedCell`.
- [ ] **Step 2 :** lancer → ÉCHEC. **Step 3 :** implémenter. **Step 4 :** `./gradlew build` → vert (le plugin compile avec `FarmingAccess.NONE`).
- [ ] **Step 5 : commit** `feat(core): farming port and hoe tool type`.

---

### Task 2 : champ, rayons, spirale

**Fichiers :**
- Créer : `farming/field/FarmField.java`, `FieldStage.java`, `FieldRadii.java`, `FieldCells.java`.
- Tester : `farming/field/FieldRadiiTest.java`, `FieldCellsTest.java`, `FarmFieldTest.java`.

**Interfaces :**

```java
public enum FieldStage { EMPTY, HOED, PLANTED; public FieldStage next(); }   // PLANTED -> EMPTY
public record FieldRadii(int south, int west, int north, int east) {            // MC order S=0, W=1, N=2, E=3
    public static final int MAX_RANGE = 20, DEFAULT_RANGE = 5;
    public static FieldRadii defaults();
    public Optional<FieldRadii> resized(Direction dir, int size);   // MC FarmFieldPlotResizeMessage rules; empty = refused
    public FieldRadii cycled(Direction dir);                        // window button: (cur % min(cur + left, 20)) + 1
    public int get(Direction dir);
    public enum Direction { SOUTH, WEST, NORTH, EAST }
}
public final class FarmField {                                     // mutable state of one field (MC FarmField)
    public FarmField(BlockPos pos);
    public BlockPos pos(); public Optional<BlockPos> owner(); public void setOwner(Optional<BlockPos> hut);
    public Optional<ItemKey> seed(); public void setSeed(Optional<ItemKey> seed);
    public FieldRadii radii(); public void setRadii(FieldRadii r);
    public FieldStage stage(); public void nextStage();
    public boolean isTaken();
    public JsonObject write(); public static Optional<FarmField> read(JsonObject in);   // tolerant
}
public final class FieldCells {                                    // MC EntityAIWorkFarmer.nextValidCell
    public static final int LARGEST_CELL = 1681;
    /** Next cell index after {@code cell} whose offset is inside {@code radii}; empty when the pass is over. */
    public static OptionalInt next(int cell, FieldRadii radii);
    /** The (x, z) offset of cell index {@code cell} on the outward square spiral. */
    public static int[] offset(int cell);
}
```

- [ ] **Step 1 : tests qui échouent** :
  - `firstRingFollowsMcOrder` : les cases 0 à 7 donnent (1,1), (0,1), (−1,1), (−1,0), (−1,−1), (0,−1), (1,−1), (1,0) ;
  - `defaultFieldVisitsEveryCellOnceButTheCentre` : 5/5/5/5 donne 120 cases distinctes ;
  - `spiralNeverLeavesTheField` : 20/0/0/0 et 1/1/1/1 (Review Focus 5) ;
  - `growthBeyondTheBudgetIsRefused` : 6/5/5/5 est refusé, 20/0/0/0 accepté ;
  - `negativeRadiusIsRefused` ;
  - `buttonCyclesFromOneToCurrentPlusLeftover` : un côté à 5 et les autres à 5 → 1, puis 2, …, jusqu'à 5 + 4 ;
  - `stageWrapsAfterPlanted` ;
  - `fieldSurvivesSaveAndLoad` ;
  - `unknownStageReadsAsEmpty`.
- [ ] **Step 2 à 4.** Recopie la formule de MC § 3.4 (bloc `ring` / `ringCell` / `facing`) à l'identique, avec sa source en Javadoc.
- [ ] **Step 5 : commit** `feat(core): farm field, radii and spiral walk (MC FarmField, nextValidCell)`.

---

### Task 3 : registre des champs de la colonie et persistance

**Fichiers :**
- Créer : `farming/field/FieldRegistry.java`.
- Modifier :
  - `colony/Colony.java` : champ `private final FieldRegistry fields = new FieldRegistry();` et `fields()` ;
  - `colony/persistence/ColonySerializer.java` : `SCHEMA_VERSION = 5`, écrit et lit `"fields"` ;
  - `kernel/persist/MigrationChain.java` : `sp3b2()`, avec `v4ToV5` qui ajoute `"fields": []` ;
  - le tick lent de la colonie : il appelle `fields().cleanUp(colony)` toutes les 500 ticks, au même endroit que le tick des bâtiments.
- Tester : `farming/field/FieldRegistryTest.java`, `colony/persistence/MigrationV4ToV5Test.java`, fixture `colony-v4-fields.json` (copie d'une fixture v4 existante, ou d'une sortie du sérialiseur v4).

**Interfaces :**

```java
public final class FieldRegistry {                   // MC RegisteredStructureManager building extensions
    public boolean add(BlockPos pos);                // putIfAbsent; true if new
    public Optional<FarmField> get(BlockPos pos);
    public Optional<FarmField> remove(BlockPos pos);
    public List<FarmField> all();                     // registration order
    public List<FarmField> free();                    // !isTaken, registration order
    public List<FarmField> ownedBy(BlockPos hut);
    /** MC cleanUpBuildings: drops loaded fields outside the colony or whose block is no longer the field block. */
    public void cleanUp(Colony colony, Predicate<BlockPos> isFieldBlock);
    public JsonArray write(); public static FieldRegistry read(JsonArray in);
}
```

`isFieldBlock` vient d'une `BlockKey` de champ que le cœur reçoit. Ajoute à `FarmingAccess` la méthode `boolean isFieldBlock(BlockPos)`, dans le fake : un ensemble `fieldBlocks`. Mets-la à jour en Task 1 si tu fais les tâches dans l'ordre.

- [ ] **Step 1 : tests qui échouent** :
  - `addingTwiceKeepsOneField` ;
  - `freeListsOnlyUntakenFields` ;
  - `cleanUpDropsAFieldOutsideTheColony` ;
  - `cleanUpDropsAFieldWhoseBlockIsGone` ;
  - `cleanUpKeepsAnUnloadedField` ;
  - `fieldsSurviveSaveAndLoad` ;
  - `v4ColonyLoadsWithNoField` ;
  - `ownerOfAMissingHutIsFreedOnLoad` (dans `ColonySerializer.heal`).
- [ ] **Step 2 à 4.**
- [ ] **Step 5 : commit** `feat(core): colony field registry, saved (MC building extensions)`.

---

### Task 4 : hutte du fermier et module de champs

**Fichiers :**
- Créer : `farming/hut/FarmerHut.java`, `FarmerFieldsModule.java`, `FarmerSettingsModule.java`, `FieldChoice.java`.
- Modifier : `CoreFeatures.java` : `FarmerHut.register(buildings)` et `FarmerHut.register(jobs)`. Le métier de la Task 8 est enregistré à ce moment-là : écris d'abord ici `FarmerJob` minimal, avec un `createAI` qui renvoie une IA inactive, et la Task 8 le complète.
- Tester : `farming/hut/FarmerFieldsModuleTest.java`, `FieldChoiceTest.java`.

**Interfaces :**

```java
public final class FarmerHut {
    public static final String TYPE_ID = "hycolony:farmer";
    public static final int MAX_LEVEL = 5;
    public static final BuildingType TYPE;   // hutKey "hut.farmer"; modules in this order:
    // "worker"  -> new WorkerModule(FarmerJob.TYPE, Skill.Stamina, Skill.Athletics, 1, false)
    // "crafting" -> new CraftingModule(TYPE_ID, true)
    // "craftingResolvers" -> CraftingResolvers::new
    // "fields"  -> FarmerFieldsModule::new
    // "settings" -> FarmerSettingsModule::new
    public static void register(BuildingRegistry r); public static void register(JobRegistry r);
}
public final class FarmerFieldsModule implements PersistentModule, TickingModule, BuildingEventsModule, ProvidesTab {
    public boolean assignManually(); public void setAssignManually(boolean manual);
    public int maxFields(Building b);                                       // = level
    public boolean canAssign(Colony c, Building b, FarmField f);            // owned < max && seed present
    public boolean assign(Colony c, Building b, FarmField f);
    public void free(Colony c, Building b, FarmField f);
    public Optional<FarmField> fieldToWorkOn(Colony c, Building b);         // FieldChoice, MC getExtensionToWorkOn
    public Optional<FarmField> currentField(Colony c);
    public void resetCurrentField(Colony c);                                // checked[current] = day
    // onColonyTick: auto-claim one; onRemoved: free every owned field (deviation 3)
}
public final class FarmerSettingsModule implements PersistentModule {
    public boolean fertilize(); public void setFertilize(boolean on);     // default true
}
```

Le « à garder » de la hutte (1 houe, 64 graines par champ possédé, 1 `Tool_Fertilizer`) passe par le même mécanisme que les `keepX` existants. Cherche comment `WorkerStock.dump(Map<ItemKey,Integer> keep)` est alimenté par les autres métiers (`grep -rn "dump(" core/src/main/java`), et expose `static Map<ItemKey,Integer> keep(Colony, Building)` dans `FarmerFieldsModule` ou dans un petit `FarmerKeep`. La houe se garde par type d'outil : `WorkerStock` garde déjà 1 outil par type.

L'onglet (`ProvidesTab`) est rendu en Task 9. Ici, `tab(...)` renvoie une `FieldsView` minimale, créée en Task 9 : garde `ProvidesTab` pour la Task 9 si tu préfères.

- [ ] **Step 1 : tests qui échouent** :
  - `levelOneHutOwnsOneFieldAtMost` ;
  - `seedlessFieldIsNeverAssigned` (Review Focus 3) ;
  - `autoClaimTakesOneFreeFieldPerTick` ;
  - `manualModeClaimsNothing` ;
  - `removingTheHutFreesItsFields` (Review Focus 1, écart 3) ;
  - `levelZeroHutClaimsNothing`.
  - Dans `FieldChoiceTest`, qui suit MC § 1.3 :
    - `currentFieldIsKeptWhileItExists` ;
    - `neverCheckedFieldComesFirst` ;
    - `fieldDoneTodayIsNotPickedAgainUntilTomorrow` ;
    - `oldestCheckedFieldIsPickedNext` ;
    - `checkedDaysSurviveSaveAndLoad` (écart 4).
- [ ] **Step 2 à 4.**
- [ ] **Step 5 : commit** `feat(core): farmer hut and its fields module (MC BuildingFarmer, FarmerFieldsModule)`.

---

### Task 5 : lecture d'une case (`FieldScan`)

**Fichiers :**
- Créer : `farming/job/FieldScan.java`.
- Tester : `farming/job/FieldScanTest.java`.

**Interfaces :**

```java
final class FieldScan {                                   // MC getSurfacePos, find*Surface, isNoPartOfField
    FieldScan(WorldBlocks blocks, ItemCatalog items, FarmingAccess farming, BlockPos fieldPos);
    Optional<BlockPos> surface(BlockPos column);          // column = field Y - 1 at the cell; search +-MAX_DEPTH
    boolean isNoPartOfField(BlockPos surface);            // air, or barrier above
    Optional<BlockPos> hoeable(BlockPos column);          // tillable, not tilled, no crop / field block above
    Optional<BlockPos> plantable(BlockPos column);        // tilled, nothing growing above, not the field block
    Optional<BlockPos> harvestable(BlockPos column);      // MATURE crop above the surface
}
```

« Solide » = `items.kind(block) == BlockKind.SOLID` ou un fluide. Une culture (`farming.crop(pos) != NONE`) n'est jamais solide. **Aucun effet de bord** (écart 2).

- [ ] **Step 1 : tests qui échouent** :
  - `surfaceIsTheTopSolidBlockWithinFive` ;
  - `noSurfaceBeyondFiveBlocks` ;
  - `barrierAboveExcludesTheCell` ;
  - `tilledCellIsNotHoeable` ;
  - `untilledCellIsNotPlantable` ;
  - `growingCropIsNotHarvestable` ;
  - `matureCropIsHarvestable` ;
  - `scanDestroysNothing` (le monde du fake est inchangé après chaque test).
- [ ] **Step 2 à 5.** Commit `feat(core): farmer cell scan without side effects (MC find*Surface)`.

---

### Task 6 : préparation (`FarmWork.prepare`, `canGoPlanting`, engrais)

**Fichiers :**
- Créer : `farming/job/FarmerState.java`, `farming/job/FarmWork.java`, `farming/job/FarmWorkContext.java`.
- Tester : `farming/job/FarmWorkPrepareTest.java`.

**Interfaces :**

```java
public enum FarmerState implements IState {   // IDLE, START_WORKING, PREPARING, FARMER_HOE, FARMER_PLANT, FARMER_HARVEST,
    // and the crafting states mapped from CraftingStep (GET_RECIPE, QUERY_ITEMS, GATHERING_REQUIRED_MATERIALS, CRAFT,
    // INVENTORY_FULL, NEEDS_ITEM); isOkayToEat as MC (field states false while working a cell)
}
record FarmWorkContext(Colony colony, CitizenData citizen, Job job, Building hut, FarmerFieldsModule fields,
        FarmerSettingsModule settings, WorkerStock stock, ToolRequests tools, BodyWalker walker, FarmingAccess farming) {}
public final class FarmWork {
    public FarmerState prepare();          // MC prepareForFarming, spec § prepareForFarming 1-7
    FarmerState canGoPlanting(FarmField f);
    // persisted on the hut through the fields module: cell, workingOffset, prevPos
}
```

Le contexte a 10 composants : si PMD s'en plaint, regroupe `fields` et `settings` dans un `record FarmerModules`.

La requête d'engrais est une `StackRequest(new ItemKey("Tool_Fertilizer"), 1, 1, false)`. L'identifiant vient de l'id-map côté plugin. Côté cœur, il est reçu par `FarmingAccess.fertilizerItem()`. Ajoute cette méthode au port (fake : `Tool_Fertilizer`).

- [ ] **Step 1 : tests qui échouent** :
  - `levelZeroHutStaysPreparing` ;
  - `noFieldBlocksTheFarmer` ;
  - `missingHoeIsRequested` ;
  - `fertilizerIsRequestedOnceWhenNoneAnywhere` ;
  - `noFertilizerRequestWhenTheSettingIsOff` ;
  - `fertilizerInTheHutIsFetched` ;
  - `emptyStageWithWorkGoesHoeing` ;
  - `nothingToDoSkipsTheStageAndFourSkipsReleaseTheField` ;
  - `plantedStageWithAMatureCropGoesHarvesting` ;
  - `hoedStageWithSeedsGoesPlanting` ;
  - `noSeedAnywhereAsksOnceAndSkipsPlanting` (Review Focus 4) ;
  - `seedsInTheHutAreTakenBeforeAsking`.
- [ ] **Step 2 à 5.** Commit `feat(core): farmer preparation, seed and fertilizer requests (MC prepareForFarming)`.

---

### Task 7 : passage sur le champ (`FieldPass`)

**Fichiers :**
- Créer : `farming/job/FieldPass.java`.
- Modifier : `FarmWork` (`workAtField` délègue à `FieldPass`).
- Tester : `farming/job/FieldPassTest.java`.

Il porte MC `workAtField`, `hoeIfAble`, `tryToPlant`, `harvestIfAble` (MC § 3.4 et 3.5), plus l'engrais de l'écart 1 :
- **Labour** :
  - casser le bloc au-dessus s'il est remplaçable et non vide (+1 action, +0,05 XP, drops au sol comme MC) ;
  - `till`, 1 de durabilité à la houe ;
  - s'il n'y a pas de houe, la case est sautée.
- **Engrais** : après le labour, ou sur une case déjà labourée pendant la plantation. Si le réglage est activé, que la case n'est pas engraissée et qu'un `Tool_Fertilizer` non usé est dans l'inventaire : `fertilize`, puis 1 de durabilité (`Inventory.damage` avec `items.durability`).
- **Plantation** : `plant(surface.above(), graine)`, puis retirer 1 graine. S'il n'y a plus de graine → `PREPARING`.
- **Récolte** : `harvest`, puis `stock.storeDrops`, +1 action, +0,05 + 0,5 XP.
- `delay = max(1, 40 − Stamina / 2)` après chaque case.
- **Fin de passage** : `shouldDumpInventory`, `nextStage`, `resetCurrentField` si `didWork` ou au 4e saut, puis `IDLE`.

- [ ] **Step 1 : tests qui échouent** :
  - `hoePassTillsEveryTillableCellAndWearsTheHoe` ;
  - `missingHoeMidPassSkipsTheCell` ;
  - `plantPassPlantsTheFieldSeed` ;
  - `plantPassWithoutSeedGoesBackToPreparing` ;
  - `harvestPassPutsDropsInTheInventoryAndEmptiesNormalCrops` ;
  - `eternalCropsStayAfterHarvest` ;
  - `fertilizerIsUsedOncePerUnfertilizedCell` ;
  - `noFertilizerUsedWhenTheSettingIsOff` ;
  - `cellDelayFollowsStamina` ;
  - `finishedPassAdvancesTheStageAndAsksForADump` ;
  - `fieldBrokenMidPassIsDropped` (Review Focus 2).
- [ ] **Step 2 à 5.** Commit `feat(core): farmer field pass, hoe, plant, harvest and fertilize (MC workAtField)`.

---

### Task 8 : métier et IA, scénario complet

**Fichiers :**
- Créer ou compléter : `farming/job/FarmerJob.java`, `farming/job/FarmerAI.java`.
- Tester : `farming/job/FarmerAITest.java`, `farming/FarmerScenarioTest.java`.

**`FarmerJob extends Job implements Crafter`** :
- il possède un `CraftingTasks`, persisté sous `"crafting"` (comme `TestCrafterJob`) ;
- `onRemoval` appelle `cancelAll` ;
- `createAI` construit `FarmerAI` avec `CraftingWorkContext.of(...)` et `FarmWorkContext`.

**`FarmerAI implements JobAI`** : une `TickRateStateMachine<FarmerState>` construite comme `TestCrafterAI`.
- Les événements de vidage (100) et de besoin d'objet (20 et 40) sont repris.
- `IDLE → START_WORKING` toutes les 20 ticks, puisque `hasWorkToDo` est toujours vrai.
- `START_WORKING` (5) : `CraftingWork.decide()`. Un `CraftingStep.IDLE` se traduit par `PREPARING` (MC `decide` du fermier), et les autres `CraftingStep` par l'état de fabrication correspondant.
- `PREPARING` (20) → `FarmWork.prepare`.
- `FARMER_*` (5) → `FarmWork.workAtField`.
- `canGoIdle` : vrai seulement sans champ à travailler et sans tâche de fabrication (MC § 3.2).

- [ ] **Step 1 : tests qui échouent** :
  - `idleFarmerPreparesAfterWalkingToTheHut` ;
  - `craftingTaskIsDoneBeforeFarming` ;
  - `dumpAfterEveryPass` ;
  - `farmerStopsInTheRain`.
- [ ] **`FarmerScenarioTest.wheatFieldFeedsItsOwnSeeds`** :
  1. une hutte niveau 1 avec une table `Farmingbench` niveau 1 enregistrée ;
  2. la recette `2 Ingredient_Life_Essence → 1 Plant_Seeds_Wheat` donnée comme recette intégrée par un `crafting.json` de test ;
  3. un champ 1/1/1/1 de blé, du sol labourable, 8 graines et une houe dans la hutte, un livreur et un entrepôt (copie le montage de `CourierAITestBase`) ;
  4. on fait tourner la colonie. Le fermier laboure et plante. On passe les 8 cultures à `MATURE` dans le fake. Il récolte, puis n'a plus de graines ; sa requête est fabriquée par sa propre hutte avec l'essence récoltée, et il replante.

  On vérifie les 8 cases replantées, et de l'essence et du blé à l'entrepôt. Au plus 60 000 ticks.
- [ ] **Step 2 à 5.** Commit `feat(core): farmer job and AI on the crafting and farming components (MC EntityAIWorkFarmer)`.

---

### Task 9 : vues et actions (fenêtre du champ, onglet Champs)

**Fichiers :**
- Créer : `app/ui/FieldView.java`, `farming/hut/FieldsView.java`, `colony/action/FieldActions.java`.
- Modifier :
  - `app/ui/UiPort.java` (+ `void showField(UUID player, FieldView view)`) et `testing/FakeUi.java` ;
  - `building/module/ModuleTab.java` (+ `FieldsView`) ;
  - `FarmerFieldsModule` (`tab`).
- Tester : `colony/action/FieldActionsTest.java`.

**Interfaces :**

```java
public record FieldView(BlockPos pos, Optional<String> farmer, Optional<ItemKey> seed, FieldRadii radii,
        List<ItemKey> seeds, boolean canManage) {}
public record FieldsView(boolean manual, int owned, int max, List<Row> rows, boolean canManage) implements ModuleTab {
    public record Row(BlockPos field, Optional<ItemKey> seed, int distance, String direction, FieldStage stage,
            boolean owned, Optional<String> refusal) {}   // refusal key: hycolony.ui.fields.refused.limit|noseed
}
public final class FieldActions {                          // built by its caller, like CraftingActions
    public FieldActions(ColonyManager manager);
    public boolean placed(UUID player, BlockPos pos);      // block placed: register if inside a colony
    public void broken(BlockPos pos);                       // block broken: remove, free owner
    public boolean open(UUID player, BlockPos pos);        // use: register if missing, showField
    public boolean setSeed(UUID player, BlockPos pos, ItemKey seed);   // MANAGE_HUTS, must be in seeds()
    public boolean cycleRadius(UUID player, BlockPos pos, FieldRadii.Direction dir);
    public boolean toggleMode(UUID player, BlockPos hut);
    public boolean assign(UUID player, BlockPos hut, BlockPos field);  // manual mode only
    public boolean free(UUID player, BlockPos hut, BlockPos field);
    public boolean toggleFertilize(UUID player, BlockPos hut);
}
```

La distance et la direction courte suivent MC `FieldsComparator` et le texte « Distance : n m <dir> » (MC § 5.1). La direction est une clé `hycolony.ui.direction.<n|s|e|w|ne…>`.

- [ ] **Step 1 : tests qui échouent** :
  - `placingOutsideAColonyRegistersNothing` ;
  - `openingAnUnknownFieldRegistersIt` ;
  - `playerWithoutManageHutsCannotChangeTheSeed` ;
  - `seedMustBeACropSeed` ;
  - `radiusButtonCycles` ;
  - `assignRefusedInAutomaticMode` ;
  - `rowsListOwnedFieldsFirstThenByDistance` ;
  - `breakingAFieldFreesItsOwner`.
- [ ] **Step 2 à 4.**
- [ ] **Step 5 :** clés de langue en-US et fr-FR :
  - `hycolony.ui.field.*` : titre, fermier, personne, choisir la graine, info-bulles des 4 rayons ;
  - `hycolony.ui.fields.*` : onglet, compteur « {p0}/{p1} champs utilisés », mode, assigner, libérer, distance, étapes, les 2 refus ;
  - `hycolony.ui.farmer.fertilize` ;
  - `hycolony.farmer.noFields` (« Placez d'autres champs pour me faire travailler. »).

  Commit `feat(core): field window and fields tab views and actions`.

Une **session sans le jar Hytale s'arrête ici** : elle pousse la branche, puis résume ce qui est fait et ce qui reste (Tasks 10 à 14).

---

### Task 10 (local) : port Hytale et houe

- Skill `hytale-api`. Vérifie dans les sources décompilées, puis note au `plugin-b-api.md` § 27 :
  - `TilledSoilBlock` : get, set et `setTicking` ;
  - la pose de `Soil_Dirt_Tilled` et d'un bloc de culture sans le bit 2 ;
  - `BlockTypeToPlace` d'une graine (`Seed_Place`) ;
  - le stade final (`getBlockKeyForState` / `Gathering.isHarvestable`) ;
  - la récolte (`FarmingUtil.harvest` et ses drops sans entité, ou `BlockHarvestUtils.getDrops` plus la branche éternelle) ;
  - les clôtures, portillons et murets (tags ou familles).
- **Créer :**
  - `plugin/.../farming/HytaleFarming.java`, où chaque méthode attrape `RuntimeException` et journalise le premier échec ;
  - l'id-map : `farming.tillable` (les 16 sols), `farming.tilled` (`Soil_Dirt_Tilled`), `farming.fertilizer` (`Tool_Fertilizer`), `farming.fieldBarriers`, `block.field`, `hut.farmer`.
- **`HytaleItemCatalog.tool`** : les `Tool_Hoe_*` → `ToolType.HOE`, au niveau 0 pour Crude, 1 pour Copper, 2 pour Iron et 3 pour Thorium. Document the mapping in Javadoc.
- **Câbler** `HytaleFarming` à la place de `FarmingAccess.NONE`.
- `./gradlew build`. Commit `feat(plugin): Hytale farming port and hoes`.

### Task 11 (local) : assets, plans de test, fabrication du fermier

- **`HyColony_Hut_Farmer.json`** : copie de `HyColony_Hut_Courier.json`, avec son propre nom, un modèle choisi dans les assets (un coffre de ferme ou autre, à vérifier dans le zip) et une recette à l'établi.
- **`HyColony_Field.json`** : le `BlockType` de `Deco_Scarecrow` (modèle, texture, hitbox, échelle, icône), sa recette (Farmingbench, `Decorative`), et `Interactions.Use` vers une interaction HyColony, comme `HyColony_Hut_Use`.
- **`tools/prefabs/add_bench.py`** avec son test (`tools/prefabs/check.py`) :
  - il copie un prefab vanilla dans `plugin/src/main/resources/Server/Prefabs/HyColony/Farmer/<style>_L<n>.prefab.json` ;
  - il ajoute une case `Bench_Farming` avec `components.Components.BenchBlock.TierLevel = n` sur la première case de sol intérieure libre près du bloc de hutte (déterministe).
- **`styles.json`** des deux styles : `hycolony:farmer` niveaux 1 à 5 vers ces prefabs.
- **`crafting.json`** : l'entrée `hycolony:farmer` de la spec. Les **vrais** identifiants de recettes se relèvent avec le catalogue (au besoin, ajoute une ligne de debug au selftest qui liste les recettes de sortie `Plant_Seeds_*`).
- **Clés de langue :** noms et descriptions de la hutte et du champ.
- `./gradlew build`. Commit `feat(plugin): farmer hut, field block, test plans and farmer recipes`.

### Task 12 (local) : événements du bloc Champ

- **`block/FieldBlockSystems.java`** : pose, casse et utilisation du bloc `block.field` vers `FieldActions.placed`, `broken` et `open`.
  - Suis `HutBlockSystems` (pose et casse) et `FlowerPotSystem` (utilisation, ordre après la protection).
  - Attrape `RuntimeException` et journalise en SEVERE.
- `./gradlew build`. Commit `feat(plugin): field block registers, removes and opens its field`.

### Task 13 (local) : fenêtre du champ et onglet Champs

- **`ui/FieldPage.java`** et `Pages/HyColony/Field.ui`, avec le sélecteur de graines (`FieldSeedRow.ui`) :
  - la graine, le fermier ;
  - 4 boutons de rayon autour d'une icône centrale, sur le modèle de MC `windowfield.xml` ;
  - `HytaleUiPort.showField`.
- **`ui/hut/FieldsTab.java`**, `FieldsTab.ui` et `FieldRow.ui` ; `HutTabs` (`case FieldsView`).
- Valide les `.ui` avec l'éditeur UI de l'utilisateur, par le même script que pour SP3b-1 (`Workspace` + `expand`, aucun diagnostic).
- `./gradlew build`. Commit `feat(plugin): field window and fields tab`.

### Task 14 (local) : selftest, documentation, relectures

- **Selftest :**
  - `FarmingAccess.seeds()` n'est pas vide ;
  - `hut.farmer` et `block.field` sont dans l'id-map ;
  - le plan de niveau 1 du fermier a une table (`workstation`) `Farmingbench` de niveau 1 ;
  - les recettes intégrées du fermier existent dans le catalogue.
- **`docs/TESTING.md`**, section « Fermier » :
  - poser un champ, choisir la graine, régler les rayons ;
  - le mode automatique et le mode manuel ;
  - un passage complet (labour, plantation, récolte, engrais) ;
  - les graines refabriquées avec l'essence, et l'essence qui arrive à l'entrepôt ;
  - casser le champ, retirer la hutte ;
  - puis dérouler les points 159 à 172 de la fabrication avec la hutte du fermier.
- **Documentation :**
  - les écarts de la spec, reportés au § 11 de la spec SP1+2 ;
  - `docs/BACKLOG.md` : « les citoyens ne mangent ni les graines des champs ni le blé » (SP4), et « lanterne de l'épouvantail ».
- **Relectures :** `hycolony-reviewer` sur toute la plage, et `mc-fidelity-checker` sur `farming/**`. Corrige, puis fais relire les corrections.
- Ne lance pas le serveur. Donne le feu vert à l'utilisateur.
