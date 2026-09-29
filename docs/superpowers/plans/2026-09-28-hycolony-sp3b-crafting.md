# SP3b-1 fabrication : plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans. Étapes à cocher (`- [ ]`).

**Objectif :** les huttes apprennent des recettes Hytale et leurs artisans les fabriquent pour la colonie, par le système de requêtes, comme dans MineColonies.

**Architecture :**
- **Cœur :** un nouveau paquet racine `dev.hycolony.core.crafting`, avec quatre sous-paquets :
  - `recipe` : modèle, port, registre et règles ;
  - `module` : le module de hutte ;
  - `request` : les résolveurs ;
  - `job` : les composants d'artisan.
- **Pas d'héritage sous `Job` et `JobAI`** (règle de `ArchitectureTest`). Le socle MC `AbstractJobCrafter` / `AbstractEntityAICrafting` devient des composants (`CraftingTasks`, `CraftingWork`) qu'un métier concret possède.
- **Plugin :** il fournit le port `RecipeCatalog`, lit les tables des prefabs, pose les tables avec leur niveau et affiche l'onglet Recettes.

**Pile technique :** Java 25, Gradle, JUnit 5, Gson, ArchUnit, palantir-java-format (Spotless), PMD, Error Prone et NullAway ; API serveur Hytale 0.6.8 (sources décompilées dans `build/vineflower/hytale-server`).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-sp3b-crafting-design.md`. **Lis-la en entier avant de commencer**, ainsi que `CLAUDE.md` (règles du projet). Source MC : `github.com/ldtteam/minecolonies`, branche `version/main`, commit `6b4e03e337fcb886b4ab8cbe21f9b6731818769d`. Clone-la (`git clone -c core.longpaths=true`) dans un dossier temporaire pour relire une méthode citée.

## Contraintes globales

- `core/` ne touche jamais `com.hypixel` ; `kernel` ne dépend de rien d'autre **[ArchitectureTest]**.
- **Pas d'héritage profond :** aucune classe n'hérite d'un sous-type de `Job` ou de `JobAI` **[ArchitectureTest]**.
- **Dépendances interdites :**
  - `request` ne dépend ni de `colony`, ni de `building`, ni de `construction`, ni de `job`, ni de `citizen` ;
  - `building` ne dépend ni de `construction`, ni de `job`, ni de `logistics` **[ArchitectureTest]** ;
  - `crafting` ne dépend pas de `construction`, `colony.action`, `colony.view` ni `colony.persistence`.
- **Taille des fichiers :** au plus 400 lignes par fichier (300 visées), 40 lignes par méthode, 5 paramètres, 15 fichiers par paquet **[checkFileSizes]**.
  - `kernel/port` et `colony` sont **déjà à 15 fichiers** : n'y ajoute aucun fichier.
  - `building` est à 14 fichiers.
- **Style :**
  - Javadoc courte sur chaque classe et chaque méthode non triviale, en anglais, avec sa source MC (`MC AbstractCraftingBuildingModule.addRecipe`) ;
  - un écart porte un commentaire `Deviation from MC: …` ;
  - pas de commentaire séparateur ;
  - lance `./gradlew spotlessApply` avant chaque commit.
- **Constantes MC exactes :**
  - `CONST_CRAFTING_RESOLVER_PRIORITY = 125`, `MAX_CRAFTING_CYCLE_DEPTH = 20` ;
  - `STANDARD_DELAY = 5`, `HIT_DELAY = 10`, `TICKS_SECOND = 20` ;
  - `PROGRESS_MULTIPLIER = 10`, `MAX_LEVEL = 50`, `HITTING_TIME = 3` ;
  - `BASE_CHANCE = 0.0625`, `EXTRA_RECIPE_MULTIPLIER = 5` ;
  - `MAX_BUILDING_PRIORITY = 10`, `DEFAULT_DELIVERY_PRIORITY = 13` ;
  - inventaire 27 cases, `maxSlots = 27 − 27 % 8 = 24`.
- **Persistance :** JSON versionné. Le schéma passe de 3 à 4 par `MigrationChain`, avec une fixture `colony-v3-*.json` qui se charge. La lecture est tolérante : une clé absente prend sa valeur par défaut.
- **Textes :** tout texte vu par un joueur passe par une clé présente en en-US **et** fr-FR (`plugin/src/main/resources/Server/Languages/*/hycolony.lang`), paramètres `{p0}`…
- **API Hytale :** chaque API est vérifiée dans `build/vineflower/hytale-server` avant usage, et notée dans `docs/research/plugin-b-api.md`.
- **Processus :**
  - TDD : le test qui échoue d'abord ;
  - `./gradlew build` vert avant chaque commit ;
  - `git add <chemins>` explicites (jamais `-A`), et jamais `config.json` ;
  - messages de commit `type(module): description` en anglais ;
  - **ne lance jamais le serveur Hytale**.
- **Fin de message de commit :** les lignes de fin demandées par la session qui exécute le plan.

## Points à surveiller en relecture

1. **Une recette Hytale disparue** après une mise à jour du jeu, alors qu'une hutte l'a apprise : le chargement doit la retirer sans planter (Task 15, test `vanishedHytaleRecipeIsDroppedOnLoad`).
2. **Une table cassée par un joueur :** ses recettes deviennent incompatibles. Elles ne sont plus choisies (`getFirstRecipe` refuse une recette incompatible) mais restent listées, comme MC qui ne les retire qu'au rafraîchissement de la vue (Task 6, test `recipeOfABrokenBenchIsNoLongerChosen`).
3. **Un artisan renvoyé en pleine tâche :** ses tâches passent en FAILED et la requête parente est réassignée. Rien ne doit rester bloqué (Task 8, test `firingTheCrafterFailsItsTasks`).
4. **Une requête qui demande sa propre sortie** : A demande X, X se fabrique avec X. Elle est refusée par la détection de cycle, sans boucle infinie (Task 9, test `recipeNeedingItsOwnOutputIsACycle`).
5. **Une requête de 1 000 objets :** elle est découpée en lots qui tiennent dans 24 cases, jamais une seule tâche géante (Task 9, test `largeRequestIsSplitIntoBatchesThatFitTheInventory`).

---

## Structure des fichiers

**Cœur, créés** (`core/src/main/java/dev/hycolony/core/`) :

| Fichier | Rôle |
|---|---|
| `kernel/item/Workstation.java` | record `(String benchId, int tier)` : une table et son niveau |
| `crafting/recipe/RecipeId.java` | record `(String value)` : `hytale:<id>`, `custom:<id>`, `improved:<n>` |
| `crafting/recipe/Ingredient.java` | interface scellée : `OfItem`, `OfResourceType`, `OfTag`, avec `amount` |
| `crafting/recipe/BenchRequirement.java` | record `(String benchId, List<String> categories, int requiredTier)` |
| `crafting/recipe/RecipeSource.java` | interface scellée : `Hytale(String id)`, `Custom(String id)`, `Improved()` |
| `crafting/recipe/Recipe.java` | le record de recette, `cleanedInput()` et `sameContentAs` |
| `crafting/recipe/RecipeCatalog.java` | le port |
| `crafting/recipe/RecipeMatching.java` | un ingrédient accepte-t-il un objet ; quels objets l'acceptent |
| `crafting/recipe/RecipeRegistry.java` | le registre de la colonie (MC `StandardRecipeManager`) et son JSON |
| `crafting/recipe/CraftingRules.java` | lecture de `crafting.json`, filtre des métiers, recettes maison, réductibles |
| `crafting/recipe/CraftingSetup.java` | record `(RecipeCatalog catalog, CraftingRules rules)` |
| `crafting/module/CraftingModule.java` | le module de hutte : liste, maximum, apprendre, retirer, activer, ordre, JSON, onglet |
| `crafting/module/RecipeCompatibility.java` | table, niveau, filtre du métier, connaissance du joueur |
| `crafting/module/RecipeChoice.java` | `getFirstRecipe` et `getFirstFulfillableRecipe` |
| `crafting/module/CustomRecipes.java` | `checkForWorkerSpecificRecipes` |
| `crafting/module/RecipeImprovement.java` | `improveRecipe` |
| `crafting/module/RecipeReservations.java` | les réservations et le « à garder » |
| `crafting/request/CraftingRequestResolver.java` | Deliverable → tâches `Crafting` (public ou privé) |
| `crafting/request/CraftingProductionResolver.java` | `Crafting` → ingrédients, tâche confiée à l'artisan |
| `crafting/request/CraftingBatches.java` | découpage en lots (MC `createRequestsForRecipe`) |
| `crafting/request/CraftingCycles.java` | `createsCraftingCycle` |
| `crafting/job/Crafter.java` | interface : `CraftingTasks craftingTasks()` |
| `crafting/job/CraftingTasks.java` | l'état persisté de MC `AbstractJobCrafter` |
| `crafting/job/CraftingStep.java` | enum des étapes |
| `crafting/job/CraftingWork.java` | les étapes de MC `AbstractEntityAICrafting` |
| `crafting/job/CraftingWorkContext.java` | record des collaborateurs de `CraftingWork` |
| `crafting/job/CraftingProgress.java` | nombre de coups, `maxCraftingCount` |
| `crafting/job/RecipeExecution.java` | consommer les entrées, produire les sorties dans un `Inventory` |
| `request/model/StackList.java` | Deliverable « l'un de ces objets » (MC `StackList`) |
| `request/model/Crafting.java` | Requestable de tâche de fabrication |
| `crafting/module/RecipesView.java` | vue de l'onglet Recettes |
| `colony/action/CraftingActions.java` | apprendre, retirer, activer, déplacer |

**Cœur, modifiés :**
- `construction/blueprint/BlueprintEntry.java` (+ `workstation`) ;
- `building/Building.java` (+ tables) ;
- `colony/persistence/BuildingSerializer.java`, `colony/persistence/ColonySerializer.java` ;
- `kernel/persist/MigrationChain.java` ;
- `colony/Colony.java` (+ `recipes()`), `colony/ConstructionPorts.java` (+ `crafting`) ;
- `request/model/Requestable.java`, `request/model/Deliverable.java`, `request/Request.java`, `request/RequestableJson.java` ;
- `job/WorkerModule.java` (+ résolveurs privés, `CreatesResolvers`) ;
- `construction/resources/NeededResources.java`, `construction/builder/BuilderBlockWork.java`, `construction/wand/PasteQueue.java` ;
- `kernel/port/WorldBlocks.java` (+ `setBenchTier`) ;
- `building/module/ModuleTab.java` ;
- `colony/ColonyManager.java` (+ `crafting()`) ;
- `colony/persistence/ColonySerializer.java` (`heal`).

**Tests** (`core/src/test/java/dev/hycolony/core/`) :
- `testing/FakeRecipeCatalog.java` ;
- `testing/TestCrafters.java` : un métier et une IA d'artisan de test, construits sur les composants ;
- un test par classe dans `crafting/...` ;
- `crafting/CraftingScenarioTest.java` ;
- la fixture `resources/fixtures/colony-v3-crafting.json`.

**Plugin** (`plugin/src/main/java/dev/hycolony/plugin/`) :
- **créés :**
  - `crafting/HytaleRecipeCatalog.java` ;
  - `crafting/RecipeConversion.java` (asset Hytale → `Recipe`) ;
  - `ui/hut/RecipesTab.java` ;
  - `resources/Common/UI/Custom/Pages/HyColony/RecipesTab.ui` et `RecipeRow.ui` ;
  - `resources/hycolony/crafting.json` ;
- **modifiés :**
  - `prefab/PrefabCells.java`, `prefab/HytaleBlueprintSource.java` ;
  - `HytaleWorldBlocks` (+ `setBenchTier`) ;
  - `subplugin/SubPlugins.java` (fusion de `crafting.json`) ;
  - `WorldRuntime.java` / `HyColonyPlugin.java` (câblage de `CraftingSetup`) ;
  - `ui/hut/HutTabs.java` ;
  - les deux `hycolony.lang` ;
  - `command/` (selftest).

---

### Task 1 : tables dans le plan et dans la hutte

**Fichiers :**
- Créer : `core/src/main/java/dev/hycolony/core/kernel/item/Workstation.java`
- Modifier : `construction/blueprint/BlueprintEntry.java`, `building/Building.java`, `colony/persistence/BuildingSerializer.java`, `kernel/persist/MigrationChain.java`, `colony/persistence/ColonySerializer.java` (`SCHEMA_VERSION = 4`, `MigrationChain.sp3b()`)
- Tester : `core/src/test/java/dev/hycolony/core/building/BuildingWorkstationsTest.java`, `colony/persistence/MigrationV3ToV4Test.java`, fixture `core/src/test/resources/fixtures/colony-v3-crafting.json` (copie de `colony-v3-unknown-job.json`)

**Interfaces :**
- Produit :
  - `record Workstation(String benchId, int tier)` ;
  - `BlueprintEntry(BlockPos offset, BlockState state, boolean hasContainer, Optional<Workstation> workstation)` avec le constructeur secondaire `BlueprintEntry(BlockPos, BlockState, boolean)` qui passe `Optional.empty()` : les appels existants restent valides ;
  - `Building.addWorkstation(BlockPos, Workstation)`, `removeWorkstation(BlockPos)`, `Map<BlockPos, Workstation> workstations()` (non modifiable, ordre d'insertion) ;
  - JSON de la hutte : `"workstations": [{"pos":[x,y,z],"bench":"Farmingbench","tier":2}]`.

- [ ] **Step 1 : tests qui échouent**

```java
package dev.hycolony.core.building;

class BuildingWorkstationsTest {
    private static final BlockPos BENCH = new BlockPos(3, 64, 1);

    @Test
    void registeredWorkstationKeepsItsBenchAndTier() {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(0, 64, 0), 0);
        b.addWorkstation(BENCH, new Workstation("Farmingbench", 2));
        assertEquals(Map.of(BENCH, new Workstation("Farmingbench", 2)), b.workstations());
    }

    @Test
    void brokenWorkstationIsForgotten() {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(0, 64, 0), 0);
        b.addWorkstation(BENCH, new Workstation("Farmingbench", 1));
        b.removeWorkstation(BENCH);
        assertTrue(b.workstations().isEmpty());
    }

    @Test
    void workstationsSurviveSaveAndLoad() {
        // Use BuildingSerializer.write / read exactly like the existing containers round-trip test in
        // colony/persistence (find it with: grep -rn "containers" core/src/test/java/dev/hycolony/core/colony/persistence).
    }
}
```

Écris le troisième test en copiant l'aller-retour existant des `containers` dans `core/src/test/java/dev/hycolony/core/colony/persistence/` et en remplaçant `addContainer` par `addWorkstation(BENCH, new Workstation("Farmingbench", 3))`. Ajoute aussi `MigrationV3ToV4Test.v3ColonyLoadsWithNoWorkstationsAndNoRecipes` : il charge `fixtures/colony-v3-crafting.json` par `ColonySerializer` et vérifie que chaque bâtiment a `workstations()` vide.

- [ ] **Step 2 : lancer** `./gradlew :core:test --tests '*BuildingWorkstationsTest' --tests '*MigrationV3ToV4Test'` → ÉCHEC (compilation).
- [ ] **Step 3 : implémenter.**

```java
package dev.hycolony.core.kernel.item;

/** A crafting bench a hut owns through its plan, and the tier its plan gave it (Hytale BenchBlock.TierLevel). */
public record Workstation(String benchId, int tier) {
    public Workstation {
        Objects.requireNonNull(benchId, "benchId");
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be >= 1: " + tier);
        }
    }
}
```

Dans `Building`, ajoute `private final Map<BlockPos, Workstation> workstations = new LinkedHashMap<>();` et les trois méthodes. `addWorkstation` remplace une entrée à la même position. Javadoc : `/** MC AbstractBuilding.registerBlockPosition for a bench the builder placed from the plan; a player's bench never comes here. */`.

`BuildingSerializer` écrit `workstations` quand la liste n'est pas vide et lit avec tolérance : clé absente = aucune ; entrée sans `bench`, ou avec `tier < 1`, ignorée.

`MigrationChain` :
- nouvelle fabrique `sp3b()` : `new MigrationChain(4, List.of(new Migration(1, …v1ToV2), new Migration(2, …v2ToV3), new Migration(3, MigrationChain::v3ToV4)))` ;
- `v3ToV4` ajoute `"workstations": []` à chaque bâtiment et `"recipes": {}` au document ;
- `ColonySerializer` utilise `sp3b()` et `SCHEMA_VERSION = 4`.
- [ ] **Step 4 : lancer** `./gradlew :core:test` → tout vert.
- [ ] **Step 5 : commit**

```bash
./gradlew spotlessApply build
git add core/src/main/java/dev/hycolony/core/kernel/item/Workstation.java core/src/main/java/dev/hycolony/core/construction/blueprint/BlueprintEntry.java core/src/main/java/dev/hycolony/core/building/Building.java core/src/main/java/dev/hycolony/core/colony/persistence/BuildingSerializer.java core/src/main/java/dev/hycolony/core/colony/persistence/ColonySerializer.java core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java core/src/test/java/dev/hycolony/core/building/BuildingWorkstationsTest.java core/src/test/java/dev/hycolony/core/colony/persistence/MigrationV3ToV4Test.java core/src/test/resources/fixtures/colony-v3-crafting.json
git commit -m "feat(core): huts register the crafting benches of their plan, with their tier"
```

---

### Task 2 : modèle de recette, port et correspondance des ingrédients

**Fichiers :**
- Créer :
  - `crafting/recipe/RecipeId.java`, `Ingredient.java`, `BenchRequirement.java`, `RecipeSource.java`, `Recipe.java`, `RecipeCatalog.java`, `RecipeMatching.java` ;
  - `core/src/test/java/dev/hycolony/core/testing/FakeRecipeCatalog.java`.
- Tester : `core/src/test/java/dev/hycolony/core/crafting/recipe/RecipeTest.java`, `RecipeMatchingTest.java`.

**Interfaces :**
- Produit :

```java
public record RecipeId(String value) {}                       // "hytale:<id>", "custom:<id>", "improved:<n>"
public sealed interface Ingredient {
    int amount();
    record OfItem(ItemKey item, int amount) implements Ingredient {}
    record OfResourceType(String id, int amount) implements Ingredient {}
    record OfTag(String id, int amount) implements Ingredient {}
    Ingredient withAmount(int amount);
}
/** A recipe's bench; benchId "Fieldcraft" = crafted by hand (MC SMALL_CRAFTING, intermediate AIR). */
public record BenchRequirement(String benchId, List<String> categories, int requiredTier) {
    public static final String FIELDCRAFT = "Fieldcraft";
    public boolean isFieldcraft() { return FIELDCRAFT.equals(benchId); }
}
public sealed interface RecipeSource {
    record Hytale(String id) implements RecipeSource {}
    record Custom(String id) implements RecipeSource {}
    record Improved() implements RecipeSource {}
}
public record Recipe(
        List<Ingredient> inputs,
        ItemAmount primaryOutput,
        List<ItemAmount> secondaryOutputs,
        BenchRequirement bench,                                // always present; Fieldcraft for hand recipes
        Optional<ToolType> requiredTool,
        RecipeSource source,
        boolean knowledgeRequired) {
    List<Ingredient> cleanedInput();                           // MC getCleanedInput: equal ingredients merged, amounts summed, order kept
    boolean sameContentAs(Recipe other);                       // inputs (cleaned), outputs, bench, tool; ignores source
}
public interface RecipeCatalog {                               // port: never throws, unknown = empty
    List<Recipe> all();                                        // Crafting and Fieldcraft recipes only
    Optional<Recipe> byHytaleId(String id);
    List<ItemKey> itemsOf(Ingredient ingredient);              // OfItem -> that item; type/tag -> every matching item
    List<ItemAmount> benchUpgradeCost(String benchId, int fromTier, int toTier);  // sum of UpgradeRequirement, fromTier exclusive
    Optional<ItemKey> benchItem(String benchId);               // the item that places this bench block
    List<String> benchCategories(String benchId);              // the bench's own categories; empty = accepts every category
    boolean playerKnows(UUID player, String hytaleRecipeId);
    String itemName(ItemKey item);                             // display key for the recipes tab
}
public final class RecipeMatching {
    static boolean accepts(Ingredient in, ItemKey item, RecipeCatalog catalog);
}
```

- [ ] **Step 1 : tests qui échouent**

```java
class RecipeTest {
    static final ItemKey ESSENCE = new ItemKey("Ingredient_Life_Essence");
    static final ItemKey SEED = new ItemKey("Plant_Seeds_Wheat");

    @Test
    void cleanedInputMergesEqualIngredients() {
        Recipe r = recipe(List.of(new Ingredient.OfItem(ESSENCE, 1), new Ingredient.OfItem(ESSENCE, 1)));
        assertEquals(List.of(new Ingredient.OfItem(ESSENCE, 2)), r.cleanedInput());
    }

    @Test
    void sameContentIgnoresTheSource() {
        Recipe a = recipe(List.of(new Ingredient.OfItem(ESSENCE, 2)));
        Recipe b = new Recipe(a.inputs(), a.primaryOutput(), a.secondaryOutputs(), a.bench(), a.requiredTool(),
                new RecipeSource.Custom("x"), false);
        assertTrue(a.sameContentAs(b));
    }

    static Recipe recipe(List<Ingredient> in) {
        return new Recipe(in, new ItemAmount(SEED, 1), List.of(),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 1), Optional.empty(),
                new RecipeSource.Hytale("Plant_Seeds_Wheat"), false);
    }
}

class RecipeMatchingTest {
    @Test
    void resourceTypeAcceptsEveryItemOfThatType() {
        FakeRecipeCatalog c = new FakeRecipeCatalog();
        c.resourceType("Wood_Trunk", new ItemKey("Wood_Oak_Trunk"), new ItemKey("Wood_Birch_Trunk"));
        Ingredient in = new Ingredient.OfResourceType("Wood_Trunk", 4);
        assertTrue(RecipeMatching.accepts(in, new ItemKey("Wood_Birch_Trunk"), c));
        assertFalse(RecipeMatching.accepts(in, new ItemKey("Rock_Stone"), c));
    }
}
```

- [ ] **Step 2 : lancer** `./gradlew :core:test --tests '*RecipeTest' --tests '*RecipeMatchingTest'` → ÉCHEC.
- [ ] **Step 3 : implémenter** les records, le port et `RecipeMatching`.
  - Validation : `amount > 0` ; les listes sont copiées (`List.copyOf`) dans les constructeurs compacts.
  - `FakeRecipeCatalog` :
    - champs publics `recipes` (une liste modifiable), `resourceTypes`, `tags`, `upgradeCosts` (clé `bench + ":" + tier` → `List<ItemAmount>`), `benchItems` et `knownByPlayer` ;
    - méthodes d'aide `resourceType(String, ItemKey...)` et `add(Recipe)`.
  - Javadoc de `Recipe` : `/** MC RecipeStorage: what a hut can craft, as Hytale describes it (inputs, outputs, bench, tool). */`.
- [ ] **Step 4 : lancer** → vert.
- [ ] **Step 5 : commit** `feat(core): recipe model and RecipeCatalog port (MC RecipeStorage)`.

---

### Task 3 : `crafting.json` et le filtre des métiers

**Fichiers :**
- Créer : `crafting/recipe/CraftingRules.java`, `crafting/recipe/CraftingSetup.java`.
- Modifier : `colony/ConstructionPorts.java` (+ `CraftingSetup crafting`), `testing/TestContexts.java` (`public FakeRecipeCatalog recipes`, `public CraftingRules craftingRules = CraftingRules.EMPTY`), puis **chaque** construction de `ConstructionPorts` dans le plugin (trouve-les avec `grep -rn "new ConstructionPorts" plugin core`). Dans le plugin, passe provisoirement `new CraftingSetup(RecipeCatalog.NONE, CraftingRules.EMPTY)` ; la Task 16 branchera le vrai catalogue.
- Tester : `crafting/recipe/CraftingRulesTest.java`.

**Interfaces :**
- Produit :

```java
public record CraftingSetup(RecipeCatalog catalog, CraftingRules rules) {}
public final class CraftingRules {
    public static final CraftingRules EMPTY;
    public static CraftingRules parse(JsonObject json, Consumer<String> warn);  // tolerant; warn once per bad entry
    public boolean allows(String jobId, Recipe recipe);         // MC crafterProduct / crafterProductExclusions
    public List<CustomRecipe> custom(String jobId);
    public boolean isReduceable(ItemKey ingredient);
    public boolean isExcludedFromReduction(ItemKey product);
    public record CustomRecipe(String id, String hytaleRecipe, int minBuildingLevel, int maxBuildingLevel) {}
}
```

Ajoute aussi `RecipeCatalog.NONE`, un catalogue vide (constante de l'interface, classe anonyme).

- [ ] **Step 1 : tests qui échouent**

```java
class CraftingRulesTest {
    static final String JSON = """
        {"jobs":{"farmer":{
            "allow":[{"bench":"Farmingbench","categories":["*"]},{"bench":"Fieldcraft","categories":["Seeds"]}],
            "includeItems":["Food_Bread"],
            "excludeItems":["Plant_Sapling_Oak"],
            "custom":[{"id":"farmer_wheat_seeds","hytaleRecipe":"Plant_Seeds_Wheat","minBuildingLevel":1,"maxBuildingLevel":5}]}},
         "reduceable":{"ingredients":["Ingredient_Life_Essence"],"excludedProducts":["Plant_Seeds_Wheat"]}}
        """;
    final CraftingRules rules = CraftingRules.parse(JsonParser.parseString(JSON).getAsJsonObject(), w -> fail(w));

    @Test
    void jobMayLearnEveryCategoryOfAnAllowedBench() {
        assertTrue(rules.allows("farmer", RecipeFixtures.at("Farmingbench", "Anything", "Plant_Seeds_Corn")));
    }

    @Test
    void excludedOutputIsRefusedEvenOnAnAllowedBench() {
        assertFalse(rules.allows("farmer", RecipeFixtures.at("Farmingbench", "Saplings", "Plant_Sapling_Oak")));
    }

    @Test
    void includedOutputIsAllowedOnAnyBench() {
        assertTrue(rules.allows("farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
    }

    @Test
    void fieldcraftRecipeFollowsItsCategories() {
        assertTrue(rules.allows("farmer", RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat")));
        assertFalse(rules.allows("farmer", RecipeFixtures.fieldcraft("Tools", "Tool_Hoe_Crude")));
    }

    @Test
    void unknownJobMayLearnNothing() {
        assertFalse(rules.allows("miner", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
    }

    @Test
    void badEntryIsSkippedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        CraftingRules r = CraftingRules.parse(
                JsonParser.parseString("{\"jobs\":{\"farmer\":{\"allow\":[{\"categories\":[]}]}}}").getAsJsonObject(),
                warnings::add);
        assertEquals(1, warnings.size());
        assertFalse(r.allows("farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
    }
}
```

`RecipeFixtures` est créé dans `core/src/test/java/dev/hycolony/core/crafting/recipe/` :
- `at(bench, category, output)` construit une recette avec `BenchRequirement(bench, List.of(category), 1)` et une entrée `OfItem("Ingredient_Life_Essence", 2)` ;
- `fieldcraft(category, output)` construit la même recette avec `BenchRequirement("Fieldcraft", List.of(category), 0)`.

Une recette `Fieldcraft`, faite à la main, garde ainsi ses catégories. `RecipeCompatibility` (Task 6) la traite comme « sans table », et le résolveur privé (Task 9) comme l'équivalent de l'`intermediate == AIR` de MC. `BenchRequirement.FIELDCRAFT = "Fieldcraft"` et `boolean isFieldcraft()` sont définis à la Task 2.

- [ ] **Step 2 : lancer** → ÉCHEC.
- [ ] **Step 3 : implémenter.**
  - `allows` renvoie vrai si la sortie est dans `includeItems`. Sinon, il faut à la fois :
    - une entrée `allow` dont `bench` égale le `benchId` de la recette ;
    - des `categories` qui valent `["*"]` ou contiennent toutes les catégories de la recette ;
    - une sortie qui n'est pas dans `excludeItems`.
  - Méthodes ≤ 40 lignes : sépare `parseJob` et `parseAllow`.
- [ ] **Step 4 : lancer** `./gradlew :core:test` → vert (les tests existants compilent avec le nouveau `ConstructionPorts`).
- [ ] **Step 5 : commit** `feat(core): crafting.json rules per job (MC crafterProduct tags)`.

---

### Task 4 : registre des recettes de la colonie

**Fichiers :**
- Créer : `crafting/recipe/RecipeRegistry.java`.
- Modifier : `colony/Colony.java` (champ `private final RecipeRegistry recipes = new RecipeRegistry();`, accès `recipes()`), `colony/persistence/ColonySerializer.java` (écrit et lit `"recipes"`).
- Tester : `crafting/recipe/RecipeRegistryTest.java`.

**Interfaces :**
- Produit :

```java
public final class RecipeRegistry {                    // MC StandardRecipeManager
    public RecipeId checkOrAdd(Recipe recipe);         // same content -> existing id; Hytale -> "hytale:<id>"; custom -> "custom:<id>"; improved -> "improved:<n>"
    public Optional<Recipe> get(RecipeId id);
    public Optional<RecipeId> idOf(Recipe recipe);     // by sameContentAs
    public JsonObject write();                         // only custom and improved recipes are written in full; Hytale ones by id
    public static RecipeRegistry read(JsonObject in, RecipeCatalog catalog, Consumer<String> warn);  // Hytale ids re-read from the catalog; unknown dropped with a warning
}
```

- [ ] **Step 1 : tests qui échouent**

```java
class RecipeRegistryTest {
    @Test
    void sameRecipeGetsTheSameId() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe r = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");
        assertEquals(reg.checkOrAdd(r), reg.checkOrAdd(r));
        assertEquals(new RecipeId("hytale:Plant_Seeds_Wheat"), reg.checkOrAdd(r));
    }

    @Test
    void improvedRecipesGetIncreasingIds() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId a = reg.checkOrAdd(RecipeFixtures.improved("A"));
        RecipeId b = reg.checkOrAdd(RecipeFixtures.improved("B"));
        assertEquals(new RecipeId("improved:1"), a);
        assertEquals(new RecipeId("improved:2"), b);
    }

    @Test
    void improvedRecipeSurvivesSaveAndLoad() {
        RecipeRegistry reg = new RecipeRegistry();
        Recipe improved = RecipeFixtures.improved("A");
        RecipeId id = reg.checkOrAdd(improved);
        RecipeRegistry back = RecipeRegistry.read(reg.write(), new FakeRecipeCatalog(), w -> {});
        assertTrue(back.get(id).orElseThrow().sameContentAs(improved));
    }

    @Test
    void vanishedHytaleRecipeIsDroppedOnLoad() {
        RecipeRegistry reg = new RecipeRegistry();
        RecipeId id = reg.checkOrAdd(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Gone"));
        List<String> warnings = new ArrayList<>();
        RecipeRegistry back = RecipeRegistry.read(reg.write(), new FakeRecipeCatalog(), warnings::add);
        assertTrue(back.get(id).isEmpty());
        assertEquals(1, warnings.size());
    }
}
```

`RecipeFixtures.improved(output)` : une recette `RecipeSource.Improved()` sans table, qui produit `output`.

- [ ] **Step 2 : lancer** → ÉCHEC.
- [ ] **Step 3 : implémenter.**
  - Le JSON d'une recette complète : `inputs` (liste de `{"kind":"item|type|tag","id":..,"amount":..}`), `output` (`{"item":..,"count":..}`), `secondary`, `bench` (`{"id":..,"categories":[..],"tier":..}` ou absent), `tool` (nom de `ToolType` ou absent), `source` (`hytale:<id>`, `custom:<id>` ou `improved`) et `knowledge`.
  - Le compteur `improved` est persisté (`"nextImproved"`).
  - Sépare `RecipeJson` (lecture et écriture d'une recette) dans le même paquet si `RecipeRegistry` dépasse 200 lignes.
  - `ColonySerializer` lit le registre **avant** les bâtiments, pour que les modules puissent vérifier leurs identifiants à la Task 15.
- [ ] **Step 4 : lancer** `./gradlew :core:test` → vert.
- [ ] **Step 5 : commit** `feat(core): colony recipe registry, saved (MC StandardRecipeManager)`.

---

### Task 5 : requêtes `StackList` et `Crafting`

**Fichiers :**
- Créer : `request/model/StackList.java`, `request/model/Crafting.java`.
- Modifier :
  - `request/model/Deliverable.java` (`permits StackRequest, ToolRequest, StackList`) ;
  - `request/model/Requestable.java` (`permits Deliverable, Delivery, Pickup, Crafting`) ;
  - `request/Request.java` (`incrementPriorityDueToAging` : ajoute `case Crafting c -> c;`) ;
  - `request/RequestableJson.java` (`"type":"stackList"` et `"type":"crafting"`) ;
  - chaque `switch` sur `Requestable` que le compilateur signale (le compilateur les liste tous : corrige-les un par un, `Crafting` n'est ni livrable ni transportable).
- Tester : `request/model/StackListTest.java`, `request/RequestableJsonCraftingTest.java`.

**Interfaces :**
- Produit :

```java
/** MC StackList: any one of {@code accepted}; used for a recipe ingredient given by resource type or tag. */
public record StackList(List<ItemKey> accepted, String description, int count, int minCount) implements Deliverable {
    public boolean matches(ItemKey item, ItemCatalog catalog) { return accepted.contains(item); }
    public StackList withCount(int n) { return new StackList(accepted, description, n, minCount); }
    public boolean canBeResolvedByBuilding() { return true; }
    public String describe() { return count + " x " + description; }
}
/** MC PublicCrafting / PrivateCrafting: craft {@code count} executions of {@code recipeId} for {@code stack}. */
public record Crafting(ItemKey stack, int count, int minCount, String recipeId, boolean isPublic) implements Requestable {
    // equals/hashCode as MC AbstractCrafting: stack, count, minCount (not the recipe, not isPublic)
    public String describe() { return count + " x craft " + stack.id(); }
}
```

- [ ] **Step 1 : tests qui échouent**
  - `StackListTest` : `matchesAnyAcceptedItem`, `withCountKeepsTheAcceptedItems`.
  - `RequestableJsonCraftingTest` : aller-retour de `Crafting(new ItemKey("Plant_Seeds_Wheat"), 3, 1, "hytale:Plant_Seeds_Wheat", true)` et d'une `StackList` de deux objets.
  - Et `craftingEqualityIgnoresTheRecipe` : `new Crafting(X, 3, 1, "a", true).equals(new Crafting(X, 3, 1, "b", false))`.
- [ ] **Step 2 : lancer** → ÉCHEC.
- [ ] **Step 3 : implémenter.**
  - `equals` et `hashCode` redéfinis dans `Crafting`. PMD peut demander `@SuppressWarnings` : ne l'ajoute pas ; une redéfinition cohérente des deux passe PMD.
  - Lecture tolérante : une `StackList` sans `accepted` n'est pas lue (le `Request` passe par le chemin « requestable inconnu » existant, voir `colony-v3-unknown-request.json`).
- [ ] **Step 4 : lancer** `./gradlew :core:test` → vert.
- [ ] **Step 5 : commit** `feat(core): StackList and Crafting requestables (MC StackList, PublicCrafting)`.

---

### Task 6 : module de fabrication (liste, maximum, compatibilité, choix)

**Fichiers :**
- Créer : `crafting/module/CraftingModule.java`, `RecipeCompatibility.java`, `RecipeChoice.java`.
- Tester : `crafting/module/CraftingModuleTest.java`, `RecipeCompatibilityTest.java`.

**Interfaces :**
- Consomme :
  - `Colony.recipes()` ;
  - `ConstructionPorts.crafting()` ;
  - `Building.workstations()`, `level()` ;
  - `WorkerModule.job().id()`.
- Produit :

```java
public final class CraftingModule implements PersistentModule {   // MC AbstractCraftingBuildingModule
    public CraftingModule(String jobId, boolean canLearnManyRecipes);
    public String jobId();
    public List<RecipeId> recipes();                               // ordered, read-only
    public boolean isDisabled(RecipeId id);
    public boolean isCustom(Colony c, RecipeId id);               // source Custom: automatic, not counted, not removable
    public int maxRecipes(Building b);                             // (int)(2^level * (1 + 0) * (many ? 5 : 1))
    public int activeRecipes(Colony c);                            // active and taught by hand
    public Optional<LearnRefusal> canLearn(Colony c, Building b, RecipeId id, UUID player);
    public boolean learn(Colony c, Building b, RecipeId id, UUID player);   // then handleRecipeUpdate
    public void remove(Colony c, RecipeId id);
    public void toggle(Colony c, int index);
    public void switchOrder(int i, int j, boolean fullMove);
    void addRecipeToList(RecipeId id, boolean atTop);
    void replaceRecipe(RecipeId oldId, RecipeId newId);
    public enum LearnRefusal { FULL, INCOMPATIBLE, UNKNOWN_TO_PLAYER, ALREADY_KNOWN }
}
final class RecipeCompatibility {
    static boolean benchPresent(Building b, Recipe r);            // Fieldcraft or a workstation with same id, categories, tier >= required
    static boolean compatible(Colony c, Building b, String jobId, Recipe r);   // bench + rules.allows
}
public final class RecipeChoice {                                  // MC getFirstRecipe / getFirstFulfillableRecipe
    public static Optional<RecipeId> firstRecipe(Colony c, Building b, CraftingModule m, Predicate<ItemKey> output);
    public static Optional<RecipeId> firstFulfillable(Colony c, Building b, CraftingModule m, Predicate<ItemKey> output,
            FulfillQuery q);
    public record FulfillQuery(int count, Map<ItemKey, Integer> reserved, List<Inventory> workerInventories) {}
}
```

Pour les catégories, suis la règle vérifiée : la table accepte une recette si ses propres catégories (`RecipeCatalog.benchCategories`, liste vide = toutes) contiennent celles de la recette.

- [ ] **Step 1 : tests qui échouent** (dans `CraftingModuleTest`, avec une colonie de test comme `CourierAITestBase` et une hutte de test de type `TestCrafters.HUT`. `TestCrafters` est créé à la Task 12 ; pour l'instant, construis `Building.create(new BuildingType("test:crafter", "hut.test", 5, List.of(new ModuleProducer("crafting", () -> new CraftingModule("test:crafter", true)))), pos, 0)`).

```java
@Test void maxRecipesIsTwoToTheLevelTimesFive()          // level 1 -> 10, level 3 -> 40
@Test void simpleModuleLearnsTwoToTheLevel()             // canLearnManyRecipes=false, level 2 -> 4
@Test void learnRefusedWhenFull()                        // LearnRefusal.FULL
@Test void customRecipesDoNotCountTowardsTheMaximum()
@Test void benchOfTooLowTierMakesTheRecipeIncompatible() // workstation tier 1, requiredTier 2 -> INCOMPATIBLE
@Test void fieldcraftRecipeNeedsNoBench()
@Test void recipeReservedToKnowingPlayersNeedsAPlayerWhoKnowsIt()  // knowledgeRequired, FakeRecipeCatalog.knownByPlayer
@Test void toggleDisablesThenEnables()
@Test void switchOrderFullMoveSendsToTop()               // MC: i > j -> add(0, remove(i))
@Test void switchOrderFullMoveSendsToBottom()            // i < j -> add(remove(i))
@Test void firstRecipeFollowsTheListOrderAndSkipsDisabled()
@Test void recipeOfABrokenBenchIsNoLongerChosen()        // Review Focus 2: removeWorkstation -> firstRecipe empty
@Test void learningWakesRequestsForItsOutput()           // a player StackRequest for the output is re-offered (m.onColonyUpdate)
@Test void recipesSurviveSaveAndLoad()                   // write/read keeps order and disabled set
```

Écris chaque test en entier : construis la colonie, `colony.recipes().checkOrAdd(recipe)`, `module.learn(...)`, puis vérifie les appels et les valeurs attendues.

- [ ] **Step 2 : lancer** → ÉCHEC.
- [ ] **Step 3 : implémenter** d'après MC `AbstractCraftingBuildingModule` (lignes citées dans la spec).
  - `learn` réveille les requêtes : `c.requests().onColonyUpdate(r -> r.deliverable().map(d -> d.matches(out, catalog)).orElse(false))`.
  - `firstRecipe` ignore une recette désactivée, **ou incompatible** (Review Focus 2). C'est un écart léger : MC la retire lors de `serializeToView`. Mets-le en Javadoc (`Deviation from MC:`).
  - JSON : `{"recipes":["hytale:..", ..], "disabled":[..]}`.
- [ ] **Step 4 : lancer** → vert.
- [ ] **Step 5 : commit** `feat(core): hut crafting module, recipe list and compatibility (MC AbstractCraftingBuildingModule)`.

---

### Task 7 : recettes maison et amélioration

**Fichiers :**
- Créer : `crafting/module/CustomRecipes.java`, `crafting/module/RecipeImprovement.java`.
- Modifier : `CraftingModule` (implémente `TickingModule` : `onColonyTick` appelle `CustomRecipes.check`).
- Tester : `CustomRecipesTest.java`, `RecipeImprovementTest.java`.

**Interfaces :**
- Produit :
  - `static void check(Colony c, Building b, CraftingModule m)` (MC `checkForWorkerSpecificRecipes`) ;
  - `static void improve(Colony c, Building b, CraftingModule m, RecipeId id, int count, CitizenData citizen, Skill improvementSkill)` ;
  - constante `BASE_CHANCE = 0.0625`.
- Le tirage vient de `c.context().random()`.
- Le message : `Msg.of("hycolony.crafting.improved." + random.nextInt(3), jobName, output, ingredient, citizenName)`, envoyé aux joueurs de la colonie par `c.context().notifier()`. Suis ce que fait déjà l'envoi de « entrepôt plein » (`grep -rn "warehouse.full" core/src/main/java`).

- [ ] **Step 1 : tests qui échouent**

```java
@Test void customRecipeIsGrantedFromItsMinimumLevel()     // level 0 -> absent, level 1 -> present at the end of the list
@Test void customRecipeIsRemovedAboveItsMaximumLevel()
@Test void customRecipeReplacesAPlayerTaughtDuplicate()   // same output and inputs, taught by hand -> replaced in place
@Test void improvementChanceIsCappedAtFivePercent()       // count 1000, skill 99 -> chance 5.0
@Test void improvementRemovesOneOfEachReduceableIngredientAboveOne()  // random returns 0 -> essence 2 -> 1; ingredient at 1 unchanged
@Test void improvementNeverTouchesAnExcludedProduct()
@Test void improvedRecipeTakesThePlaceOfTheOriginal()
```

Pour le hasard, `TestContexts` utilise `new Random(1234)`. Soit tu passes au test un `RandomGenerator` qui renvoie `0.0` (une classe anonyme dans le test, par `RecipeImprovement.improve(..., RandomGenerator)` en surcharge package-private), soit tu trouves la graine. Choix : ajoute la surcharge avec `RandomGenerator` et appelle-la dans les tests.

- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter** (MC `checkForWorkerSpecificRecipes`, `improveRecipe`). **Step 4 : lancer** → vert.
- [ ] **Step 5 :** ajoute les clés `hycolony.crafting.improved.0..2` en en-US et fr-FR, avec `{p0}` = métier, `{p1}` = sortie, `{p2}` = ingrédient réduit, `{p3}` = citoyen. Exemple en-US : `The {p0} {p3} found a way to make {p1} with one {p2} less!`. Commit `feat(core): custom recipes by hut level and recipe improvement (MC CustomRecipe, improveRecipe)`.

---

### Task 8 : état de l'artisan (`CraftingTasks`)

**Fichiers :**
- Créer : `crafting/job/Crafter.java`, `crafting/job/CraftingTasks.java`.
- Tester : `crafting/job/CraftingTasksTest.java`.

**Interfaces :**
- Produit :

```java
/** A job that crafts for its hut's crafting module (MC AbstractJobCrafter); not a Job subtype, see ArchitectureTest. */
public interface Crafter {
    CraftingTasks craftingTasks();
}
public final class CraftingTasks {
    public List<RequestToken> taskQueue();                 // read-only
    public List<RequestToken> assignedTasks();             // read-only
    public Optional<Request> currentTask(RequestManager m); // pops dead head tokens first (MC getCurrentTask)
    public void finishRequest(RequestManager m, boolean ok); // head -> RESOLVED or FAILED
    public void onTaskBeingScheduled(RequestToken t);       // -> assigned
    public void onTaskBeingResolved(RequestToken t);        // assigned -> queue
    public void onTaskDeletion(RequestToken t);
    public void cancelAll(RequestManager m);                // MC onRemoval: queue and assigned -> FAILED
    public int load();                                      // queue + assigned sizes
    public int maxCraftingCount(); public void setMaxCraftingCount(int n);
    public int craftCounter();     public void setCraftCounter(int n);
    public int progress();         public void setProgress(int n);
    public Map<ItemKey, Integer> secondaryOutputs();        // mutable
    public JsonObject write(); public void read(JsonObject in);   // each key into its own field (MC bug fixed)
}
```

- [ ] **Step 1 : tests qui échouent** : `scheduledThenResolvedTaskMovesToTheQueue`, `deadHeadTokensArePopped`, `finishRequestResolvesTheHead`, `countersAreReadIntoTheirOwnFields` (écrit `maxCraftingCount = 4`, `craftCounter = 2`, `progress = 7` puis relit : les trois valeurs sont retrouvées), `firingTheCrafterFailsItsTasks` (Review Focus 3 : deux tâches, `cancelAll`, les deux requêtes sont FAILED et la requête parente redevient assignable, en vérifiant par `m.get(parent)`).
- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter.** **Step 4 : lancer** → vert.
- [ ] **Step 5 : commit** `feat(core): crafter task state (MC AbstractJobCrafter)`.

---

### Task 9 : résolveur de fabrication (lots et cycles)

**Fichiers :**
- Créer : `crafting/request/CraftingRequestResolver.java`, `CraftingBatches.java`, `CraftingCycles.java`.
- Modifier : `CraftingModule` implémente `CreatesResolvers` (résolveurs publics : celui-ci et celui de la Task 10).
- Tester : `CraftingRequestResolverTest.java`, `CraftingBatchesTest.java`.

**Interfaces :**
- Produit :

```java
public final class CraftingRequestResolver implements Resolver {
    public static final int PRIORITY = 125;                 // MC CONST_CRAFTING_RESOLVER_PRIORITY
    public CraftingRequestResolver(Colony colony, Building hut, String jobId, boolean isPublic);
    // resolverId(): "crafting:" + (isPublic ? "public:" : "private:") + hut.requesterId().value()
    // handles: Deliverable only
}
final class CraftingBatches {                               // MC AbstractCraftingRequestResolver.createRequestsForRecipe
    static final int INVENTORY_SLOTS = 27;
    static List<Crafting> split(Recipe r, String recipeId, int count, int minCount, boolean isPublic, ItemCatalog catalog);
}
final class CraftingCycles {                                // MC createsCraftingCycle, MAX_CRAFTING_CYCLE_DEPTH = 20
    static boolean createsCycle(RequestManager m, Request request, Requestable target, @Nullable Request targetRequest);
}
```

Le calcul de `split`, recopié de MC :

```java
int maxSlots = INVENTORY_SLOTS - INVENTORY_SLOTS % 8;          // 24; + CITIZEN_INV_SLOTS research (0)
int outPer = r.primaryOutput().count();
int executions = (int) Math.ceil((double) count / outPer);
int minExecutions = (int) Math.ceil((double) minCount / outPer);
int batch = executions;
int totalSlots = Integer.MAX_VALUE;
while (totalSlots > maxSlots) {
    int stacks = (int) Math.ceil((double) (outPer * batch) / catalog.maxStack(r.primaryOutput().item()));
    for (Ingredient in : r.cleanedInput()) {
        stacks += isToolOrSecondary(r, in) ? 1 : (int) Math.ceil((double) (in.amount() * batch) / maxStackOf(in));
    }
    if (stacks > maxSlots) {
        batch = (int) Math.floor((double) batch * ((double) maxSlots / stacks));
    }
    totalSlots = Math.min(totalSlots, stacks);
}
// then: while executions > 0 -> new Crafting(out, min(batch, executions), max(1, min(batch, minExecutions)), id, isPublic)
//       executions -= batch; minExecutions = minExecutions > batch ? minExecutions - batch : 0
```

`maxStackOf(in)` renvoie le `maxStack` du premier objet de `catalog.itemsOf(in)`, et 64 s'il n'y en a aucun.

- [ ] **Step 1 : tests qui échouent**

```java
@Test void largeRequestIsSplitIntoBatchesThatFitTheInventory()  // Review Focus 5: 1000 seeds (1 per run, 2 essence each, max stack 64) -> several Crafting children, each batch's stacks <= 24, counts sum to 1000
@Test void smallRequestIsOneBatch()                             // 10 -> one Crafting(count 10, minCount 10)
@Test void publicResolverServesAnotherHut()
@Test void privateResolverServesOnlyItsOwnHut()
@Test void privateResolverTakesOnlyRecipesWithoutBench()        // MC: intermediate AIR only
@Test void hutWithoutWorkerCannotResolve()
@Test void levelZeroHutCannotResolve()
@Test void recipeNeedingItsOwnOutputIsACycle()                  // Review Focus 4
@Test void suitabilityIsTheDistance()
```

- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter** d'après MC `AbstractCraftingRequestResolver` (spec, § Requêtes et résolveurs). `canResolve` cherche la recette par `RecipeChoice.firstRecipe`. **Step 4 : lancer** → vert.
- [ ] **Step 5 : commit** `feat(core): crafting request resolver, batches and cycle check (MC AbstractCraftingRequestResolver)`.

---

### Task 10 : résolveur de production, résolveurs privés et réservations

**Fichiers :**
- Créer : `crafting/request/CraftingProductionResolver.java`, `crafting/module/RecipeReservations.java`.
- Modifier :
  - `job/WorkerModule.java` : il implémente `CreatesResolvers`, avec les résolveurs privés `CraftingRequestResolver(isPublic=false)` et `CraftingProductionResolver(isPublic=false)`. **Attention :** `job` ne dépend pas de `crafting` aujourd'hui. Pour éviter le cycle `job → crafting → job`, les résolveurs privés ne sont **pas** créés dans `WorkerModule`. Ils sont créés par `CraftingModule.createResolvers`, qui renvoie **quatre** résolveurs : public et privé, pour la requête et pour la production.
    - Écart assumé : une hutte sans module de fabrication n'a pas de fabrication privée. Aucun métier sans module n'en a besoin dans MC, puisque les recettes sans table viennent d'un module appris.
    - Écris ce choix en Javadoc de `CraftingModule.createResolvers`.
  - La logique « à garder » du bâtiment : trouve où les `keepX` des métiers sont calculés (`grep -rn "amountToKeep\|toKeep\|keep(" core/src/main/java`) et ajoute les réservations de `RecipeReservations`.
- Tester : `CraftingProductionResolverTest.java`, `RecipeReservationsTest.java`.

**Interfaces :**
- Produit :

```java
public final class CraftingProductionResolver implements Resolver {
    public CraftingProductionResolver(Colony colony, Building hut, String jobId, boolean isPublic);
    // handles: Crafting with same isPublic; servesOnly(hut.requesterId())
    // attemptResolve: [] if already fulfillable; else one StackRequest/StackList per cleaned ingredient
    // onAssigned (public): least loaded crafter.onTaskBeingScheduled, none -> m.updateState(CANCELLED)
    // resolve (public): crafter.onTaskBeingResolved; stays IN_PROGRESS
    // resolve (private): RecipeExecution.craftInContainers x count, then RESOLVED
    // followups (public): parent from another hut -> one Delivery(hut, parent requester, stack, 13) per delivery
    // onCancelled / on completion: onTaskDeletion on the crafter holding it
}
public final class RecipeReservations {
    public static Map<ItemKey, Integer> reservedExcluding(Colony c, Building b, @Nullable Request excluded);
    public static Map<ItemKey, Integer> toKeep(Colony c, Building b);   // inputs x count + outputs x count
}
```

- [ ] **Step 1 : tests qui échouent** : `alreadyFulfillableTaskAsksForNothing`, `missingIngredientsAreRequestedTimesTheCount`, `resourceTypeIngredientIsRequestedAsAStackList`, `taskGoesToTheLeastLoadedCrafter`, `noCrafterCancelsTheTask`, `privateTaskCraftsAtOnceInTheHut`, `finishedTaskIsDeliveredToAnotherHut`, `finishedTaskForTheSameHutNeedsNoDelivery`, `ingredientsOfQueuedTasksAreKeptFromCouriers`.
- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter.** La fabrication immédiate du privé vient de `RecipeExecution.craftInContainers` : écris-la ici si la Task 11 n'est pas encore faite, avec son test. **Step 4 : lancer** → vert.
- [ ] **Step 5 : commit** `feat(core): crafting production resolvers and reserved ingredients (MC AbstractCraftingProductionResolver)`.

---

### Task 11 : exécution d'une recette et durée

**Fichiers :**
- Créer : `crafting/job/RecipeExecution.java`, `crafting/job/CraftingProgress.java`.
- Tester : `RecipeExecutionTest.java`, `CraftingProgressTest.java`.

**Interfaces :**
- Produit :

```java
public final class RecipeExecution {
    /** MC RecipeStorage.fullfillRecipeAndCopy: consumes one run's inputs from inv, adds the outputs; empty if an input or room is missing (nothing changed). */
    public static Optional<List<ItemAmount>> craftOnce(Recipe r, Inventory inv, RecipeCatalog recipes, ItemCatalog items);
    public static boolean craftInContainers(Recipe r, List<BlockPos> containers, ContainerAccess access, RecipeCatalog recipes, ItemCatalog items);
}
public final class CraftingProgress {
    public static final int PROGRESS_MULTIPLIER = 10, MAX_LEVEL = 50, HITTING_TIME = 3;
    /** MC getRequiredProgressForMakingRawMaterial, integer division kept. */
    public static int requiredHits(int speedSkillLevel) {
        return PROGRESS_MULTIPLIER / Math.min(speedSkillLevel / 2 + 1, MAX_LEVEL) * HITTING_TIME;
    }
}
```

- [ ] **Step 1 : tests qui échouent** :
  - `requiredHitsFollowsMcIntegerDivision` : skill 0 → 30, skill 2 → 15, skill 10 → 3, skill 30 → 0 ;
  - `craftOnceConsumesInputsAndAddsOutputs` ;
  - `craftOnceChangesNothingWhenAnInputIsMissing` ;
  - `craftOnceTakesAnyItemOfAResourceType` ;
  - `secondaryOutputIsReturnedToo`.
- [ ] **Step 2 à 4.** `craftOnce` vérifie d'abord la place avec une **copie** de l'inventaire (`inv.copy()`) : il ne touche l'original que si tout passe.
- [ ] **Step 5 : commit** `feat(core): recipe execution and crafting duration (MC fullfillRecipeAndCopy)`.

---

### Task 12 : `CraftingWork`, l'IA d'artisan en composants, et le scénario complet

**Fichiers :**
- Créer : `crafting/job/CraftingStep.java`, `CraftingWork.java`, `CraftingWorkContext.java` ; `core/src/test/java/dev/hycolony/core/testing/TestCrafters.java`.
- Tester : `crafting/job/CraftingWorkTest.java`, `crafting/CraftingScenarioTest.java`.

**Interfaces :**
- Consomme :
  - `WorkerStock` (`take`, `hutCount`, `dumpNow`, `toolInInventory`) ;
  - `ToolRequests.requestTool` ;
  - `BodyWalker.walkTo` ;
  - `WorldEffects.blockHit` ;
  - `Job.incrementActions` / `actionsDone` ;
  - `JobXp` / `Experience` pour l'expérience : suis `CourierContext.award`.
- Produit :

```java
public enum CraftingStep { IDLE, START_WORKING, GET_RECIPE, QUERY_ITEMS, GATHERING_REQUIRED_MATERIALS, CRAFT, INVENTORY_FULL }
public record CraftingWorkContext(Colony colony, CitizenData citizen, Job job, Crafter crafter, Building hut,
        WorkerStock stock, ToolRequests tools, BodyWalker walker, Skill speedSkill, Skill improvementSkill) {}
public final class CraftingWork {                     // MC AbstractEntityAICrafting
    public static final int STANDARD_DELAY = 5, HIT_DELAY = 10, TICKS_SECOND = 20;
    public CraftingWork(CraftingWorkContext ctx);
    public boolean hasWorkToDo();
    public CraftingStep decide();                     // START_WORKING target
    public CraftingStep getRecipe();
    public CraftingStep queryItems();
    public CraftingStep gather();                     // GATHERING_REQUIRED_MATERIALS
    public CraftingStep craft();                      // every HIT_DELAY ticks
    public CraftingStep afterDump();
    public void resetValues();
}
```

Le record de contexte a 10 champs. C'est un record, pas une méthode, donc la règle des 5 paramètres ne s'applique pas. Si PMD s'en plaint (`ExcessiveParameterList`), regroupe `speedSkill` et `improvementSkill` dans un `record CraftingSkills(Skill speed, Skill improvement)`.

`TestCrafters` (dans `testing`) :
- `HUT` : un `BuildingType` `"test:crafter"`, niveau max 5, avec deux modules :
  - `WorkerModule(TestCrafters.JOB, Skill.Dexterity, Skill.Knowledge, 1, false)` ;
  - `CraftingModule("test:crafter", true)`.
- `JOB` : un `JobType` `"test:crafter"` dont le métier `TestCrafterJob extends Job implements Crafter` possède un `CraftingTasks`, persisté dans `write`/`read`.
- Son IA `TestCrafterAI implements JobAI` utilise un `TickRateStateMachine<CraftingStep>`, construit comme `DeliverymanAI` :
  - `IDLE` → `START_WORKING` si `work.hasWorkToDo()`, toutes les 20 ticks ;
  - `START_WORKING` → `decide`, toutes les 5 ticks ;
  - `GET_RECIPE`, `QUERY_ITEMS` et `GATHERING_REQUIRED_MATERIALS` toutes les 5 ticks ;
  - `CRAFT` toutes les 10 ticks ;
  - `INVENTORY_FULL` : `stock.dumpNow()` puis `work.afterDump()`.

  Le fermier de SP3b-2 suivra exactement ce modèle.
- `TestContexts.jobs()` enregistre `TestCrafters.JOB` ; `TestContexts.extraBuildingTypes` reçoit `TestCrafters.HUT` dans les tests qui en ont besoin.

- [ ] **Step 1 : tests qui échouent** (`CraftingWorkTest`, colonie de test avec `t.bodies.instant = true`) :
  - `noTaskMeansIdle` ;
  - `missingModuleFailsTheTask` ;
  - `missingToolRequestsItAndFailsTheTask` ;
  - `ingredientInTheHutIsFetchedBeforeCrafting` ;
  - `ingredientNowhereForgetsTheRecipe` ;
  - `craftingTakesTheSkillBasedNumberOfHits` : skill 0 → 30 coups de 10 ticks ;
  - `lastRunImprovesTheRecipeThenDumps` ;
  - `secondaryOutputsGoToTheNearestWarehouse`.
- [ ] **`CraftingScenarioTest.playerRequestIsCraftedAndDelivered` :**
  1. une hutte de test niveau 1, avec une table `Farmingbench` niveau 1 enregistrée et un artisan embauché ;
  2. la recette `2 × Ingredient_Life_Essence → 1 × Plant_Seeds_Wheat` apprise ;
  3. 20 essences dans le coffre de la hutte ;
  4. une résidence demande 10 graines (`StackRequest`) ;
  5. on fait tourner la colonie jusqu'à ce que la requête soit terminée, au plus 20 000 ticks.

  On vérifie : 10 graines livrées à la résidence (le livreur vient de `CourierAITestBase` ; copie son montage) et aucune essence restante.
- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter** `CraftingWork` ligne à ligne d'après la spec, section Artisan (MC `AbstractEntityAICrafting` : `decide`, `getNextCraftingState`, `getRecipe`, `checkForItems`, `craft`, `executeCraftingAction`, `finalizeCraftingTask`, `afterDump`). Chaque méthode fait ≤ 40 lignes : sépare `GetRecipeCounts` (le calcul de `maxCraftingCount`) si besoin. **Step 4 : lancer** `./gradlew :core:test` → vert.
- [ ] **Step 5 : commit** `feat(core): crafter AI steps as a shared component (MC AbstractEntityAICrafting)`.

---

### Task 13 : coût et pose des tables par le constructeur

**Fichiers :**
- Modifier :
  - `construction/resources/NeededResources.java` : une case avec `workstation` coûte l'objet de la table, plus `benchUpgradeCost(bench, 1, tier)` ;
  - `construction/builder/BuilderBlockWork.java` : il vérifie **tous** les objets de la case, les consomme tous à la pose, appelle `setBenchTier`, puis enregistre la table ;
  - `construction/wand/PasteQueue.java` : il enregistre la table et règle son niveau, sans coût (collage créatif) ;
  - `kernel/port/WorldBlocks.java` : `boolean setBenchTier(BlockPos pos, int tier)`. Un port ne lève jamais d'exception : bloc absent ou pas une table = `false` ;
  - `testing/FakeWorldBlocks.java`.
- Tester : `construction/resources/NeededResourcesWorkstationTest.java`, `construction/builder/BuilderPlacesWorkstationTest.java`.

**Interfaces :**
- Produit : `List<ItemAmount> EntryCost.of(BlueprintEntry e, ItemCatalog items, RecipeCatalog recipes)` dans `construction/resources` (5 fichiers ensuite). C'est la liste des objets qu'une case consomme : un seul objet pour une case normale, la table plus ses montées de niveau pour une table. `NeededResources.collect` et `BuilderBlockWork.work` passent tous deux par elle.

- [ ] **Step 1 : tests qui échouent** :
  - `tierThreeBenchCostsTheBenchAndTwoUpgrades` : `FakeRecipeCatalog.upgradeCosts` « Farmingbench:2 » = 5 × A, « Farmingbench:3 » = 8 × B ; les ressources comptent 1 table, 5 A et 8 B ;
  - `tierOneBenchCostsOnlyTheBench` ;
  - `builderWaitsForEveryUpgradeItem` : il manque les B → état de rassemblement, pas de pose ;
  - `placedBenchGetsItsTierAndJoinsTheHut` : après pose, `FakeWorldBlocks` a le niveau 3 à la position, et `hut.workstations()` la contient ;
  - `pastedBenchJoinsTheHutForFree`.
- [ ] **Step 2 : lancer** → ÉCHEC. **Step 3 : implémenter.** Les demandes de matériaux (`Buckets`) sont découpées dans `sequence()`, qui garde un objet par entrée : ajoute un élément à la séquence par unité d'objet, pour que les paquets restent justes. **Step 4 : lancer** `./gradlew :core:test` → vert.
- [ ] **Step 5 : commit** `feat(core): builder pays and places plan benches at their tier (deviation: Hytale bench tiers)`.

---

### Task 14 : onglet Recettes (vue et actions du cœur)

**Fichiers :**
- Créer : `crafting/module/RecipesView.java`, `colony/action/CraftingActions.java`.
- Modifier :
  - `building/module/ModuleTab.java` (`permits ..., RecipesView`) ;
  - `CraftingModule` implémente `ProvidesTab` ;
  - `colony/ColonyManager.java` : `public CraftingActions crafting()`, construit comme `workOrders()`.
- Tester : `colony/action/CraftingActionsTest.java`, `crafting/module/RecipesViewTest.java`.

**Interfaces :**
- Produit :

```java
public record RecipesView(int active, int max, List<Line> learned, List<Line> learnable, boolean canManage) implements ModuleTab {
    public record Line(String recipeId, ItemAmount output, List<IngredientLine> inputs, Optional<String> bench,
            boolean disabled, boolean custom, Optional<String> refusal) {}
    public record IngredientLine(String label, int amount) {}   // label: item id, or "type:<id>" / "tag:<id>"
}
public final class CraftingActions {                           // each checks MANAGE_HUTS through ManagedHut, then re-shows the window
    public boolean learn(UUID player, BlockPos hut, String recipeId);
    public boolean remove(UUID player, BlockPos hut, String recipeId);    // refused for a custom recipe
    public boolean toggle(UUID player, BlockPos hut, int index);
    public boolean move(UUID player, BlockPos hut, int index, int delta, boolean fullMove);
}
```

Les apprenables sont triées par identifiant de sortie : le nom affiché est traduit côté plugin. Le motif de refus est une clé : `hycolony.ui.recipes.refused.full|incompatible|unknown_to_player`.

- [ ] **Step 1 : tests qui échouent** : `playerWithoutManageHutsCannotLearn`, `learnReShowsTheWindow` (`FakeUi` a reçu la vue du bâtiment), `customRecipeCannotBeRemoved`, `viewListsLearnableRecipesSortedAndExcludesLearnedOnes`, `fullHutShowsTheRefusalOnLearnableLines`.
- [ ] **Step 2 à 4.**
- [ ] **Step 5 :** ajoute les clés en-US et fr-FR :
  - `hycolony.ui.building.tab.recipes` = « Recipes » / « Recettes » ;
  - `hycolony.ui.recipes.count` = `{p0} / {p1}` ;
  - `hycolony.ui.recipes.learned` / `.learnable` / `.learn` / `.remove` / `.enable` / `.disable` / `.up` / `.down` / `.custom` / `.disabled` ;
  - les trois `refused.*`.

  Commit `feat(core): recipes tab view and actions (MC WindowListRecipes)`.

---

### Task 15 : réparation au chargement

**Fichiers :**
- Modifier : `colony/persistence/ColonySerializer.java` (`heal`).
- Tester : `colony/persistence/CraftingHealTest.java`.
- [ ] **Step 1 : tests qui échouent** :
  - `learnedRecipeMissingFromTheRegistryIsRemoved` : l'identifiant est dans le module mais pas dans le registre ;
  - `tasksOfUnknownRequestsAreDropped` : un jeton de `CraftingTasks` sans requête ;
  - la colonie est chaque fois marquée à réécrire (`isDirty`).
- [ ] **Step 2 à 4.** `heal` parcourt les bâtiments qui ont un `CraftingModule` et les citoyens dont le métier est un `Crafter`.
- [ ] **Step 5 : commit** `fix(core): heal crafting modules and crafter queues on load`.

---

### Task 16 : plugin, catalogue des recettes Hytale et tables des prefabs

**Préalable :** utilise le skill `hytale-api`. Vérifie dans `build/vineflower/hytale-server` puis note dans `docs/research/plugin-b-api.md` (nouvelle section « Recettes et tables ») :
- `CraftingRecipe.getAssetMap()`, `getInput()`, `getOutputs()`, `getPrimaryOutput()`, `getBenchRequirement()` (`Type`, `Id`, `Categories`, `RequiredTierLevel`), `isKnowledgeRequired()` (nom exact à vérifier) ;
- les types de ressources et les tags d'objet : comment lister les objets d'un `ResourceTypeId` et d'un `ItemTag` (voir `docs/research/prefab-obtainability.md`, qui a déjà fait ce calcul) ;
- `Bench` du `BlockType` : `getId()`, `getTierLevel(int)`, `BenchTierLevel.UpgradeRequirement` et ses catégories ;
- l'objet qui pose un bloc de table ;
- `BenchBlock` sur un holder de prefab (`Holder<ChunkStore>.getComponent(BenchBlock.getComponentType())`) et `getTierLevel()` ;
- la pose : comment régler `BenchBlock.setTierLevel(n)` après `setBlock`, et l'état de bloc `Tier<n>` (`BenchBlock.java:119`) ;
- les recettes connues d'un joueur (`KnowledgeRequired`, voir `LearnRecipeInteraction`, `UpdateKnownRecipes`).

**Fichiers :**
- Créer :
  - `plugin/src/main/java/dev/hycolony/plugin/crafting/HytaleRecipeCatalog.java` (le port ; attrape tout `RuntimeException`, journalise la première fois puis en FINE) ;
  - `crafting/RecipeConversion.java` (asset → `Recipe`) ;
  - `plugin/src/main/resources/hycolony/crafting.json` : `{"jobs":{}, "reduceable":{"ingredients":[],"excludedProducts":[]}}`.
- Modifier :
  - `prefab/PrefabCells.java` : `Resolved(BlockState state, boolean container, Optional<Workstation> workstation)`, qui lit `BenchBlock.TierLevel` (1 par défaut) et ne garde que les tables de type `Crafting` ;
  - `prefab/HytaleBlueprintSource.java` : il passe `workstation` à `BlueprintEntry` ;
  - `HytaleWorldBlocks.setBenchTier` ;
  - `subplugin/SubPlugins.java` : fusion de `crafting.json` (profondeur 2) ;
  - le câblage de `CraftingSetup` à la place de `RecipeCatalog.NONE` de la Task 3 (`grep -rn "RecipeCatalog.NONE" plugin`).
- [ ] **Step 1 :** vérifications API, puis notes dans `plugin-b-api.md`.
- [ ] **Step 2 :** implémenter. Le catalogue est construit une fois, au premier accès sur le thread du monde, après le chargement des assets. Il est reconstruit si les recettes changent (`LoadedAssetsEvent` de `CraftingRecipe`, à vérifier).
- [ ] **Step 3 :** `./gradlew build` → vert.
- [ ] **Step 4 : commit** `feat(plugin): Hytale recipe catalog, plan benches with tiers, crafting.json`.

---

### Task 17 : plugin, onglet Recettes

**Fichiers :**
- Créer : `plugin/src/main/java/dev/hycolony/plugin/ui/hut/RecipesTab.java`, `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/RecipesTab.ui`, `RecipeRow.ui`.
- Modifier : `ui/hut/HutTabs.java` (`case RecipesView r -> new RecipesTab(manager, player, view.pos(), r)`).
- **Modèles à suivre :** `BuilderOrdersTab.java` et `BuilderOrdersTab.ui` / `BuilderOrderRow.ui`. Chaque ligne affiche :
  - la sortie (nom traduit par `Message.translation` de la clé d'objet et la quantité) ;
  - les ingrédients sur une ligne, avec `.TextSpans`, jamais `.Text`, pour un paramètre traduit (CLAUDE.md § 7) ;
  - la table ;
  - les boutons.

  Les boutons appellent `manager.crafting().learn/remove/toggle/move`. Maj + Monter se lit dans l'événement si Hytale le fournit (vérifie `CustomUIEventBindingType`). Sinon, Monter/Descendre ne déplacent que d'une case, et deux boutons « tout en haut » et « tout en bas » sont ajoutés.
- **Outil :** utilise l'éditeur UI de l'utilisateur et son analyseur pour valider les `.ui` (voir la mémoire `reference-hytale-ui-editor` ; demande le chemin à l'utilisateur s'il manque).
- [ ] **Step 1 :** implémenter.
- [ ] **Step 2 :** `./gradlew build` → vert.
- [ ] **Step 3 : commit** `feat(plugin): recipes tab in hut windows`.

---

### Task 18 : selftest, documentation et relectures

- [ ] **Step 1 :** `/hycolony selftest` gagne une ligne « recettes » dans la commande existante (`plugin/.../command/`, sur le modèle de `HutTypesSelfTest`) :
  - nombre de recettes du catalogue (> 0) ;
  - `Plant_Seeds_Wheat` connue avec la table `Farmingbench` ;
  - `benchUpgradeCost("Farmingbench", 1, 2)` non vide.
- [ ] **Step 2 :** `docs/TESTING.md` : ajoute la section « Fabrication ». Elle sera déroulée avec le fermier (SP3b-2) : apprendre une recette, voir le maximum, désactiver, réordonner, table de niveau trop bas → recette absente des apprenables.
- [ ] **Step 3 :** reporte les 12 écarts de la spec dans le tableau des écarts de la spec SP1+2 (§ 11, `docs/superpowers/specs/2026-09-25-hycolony-sp12-requests-construction-design.md`).
- [ ] **Step 4 :** `./gradlew build` → vert. Commit `docs: crafting selftest, testing checklist and deviations`.
- [ ] **Step 5 :** relecture indépendante (CLAUDE.md § 9.3) :
  - agent `hycolony-reviewer` sur toute la plage des commits de SP3b-1, avec la spec et ce plan en contexte ;
  - agent `mc-fidelity-checker` sur `crafting/**`, contre les classes MC citées.

  Corrige leurs remarques, puis fais relire les corrections à leur tour.
- [ ] **Step 6 :** ne lance pas le serveur. Annonce à l'utilisateur que SP3b-1 est prêt et que le test en jeu viendra avec le fermier (SP3b-2).
