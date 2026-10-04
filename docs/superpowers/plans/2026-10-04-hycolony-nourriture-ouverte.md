# Nourriture et tags de métier ouverts aux autres mods : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** les aliments, les tags de métier de MC et les postes de cuisson de HyColony se lisent dans des données qu'un autre mod peut compléter, sans que HyColony nomme un banc ou un plat dans son code.

**Architecture :** deux types d'assets de HyColony (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), enregistrés au `setup()` du plugin et lus dans chaque pack. Le cœur garde les règles : la valeur d'un aliment sans fichier (`FoodQuality`) et la fusion et l'usage des tags (`JobTags`, `CraftingRules`). Les ports `FoodCatalog` et `CookingCatalog` ne changent pas : seuls leurs adaptateurs changent de source. Les postes de cuisson deviennent tous les bancs `Processing` qui cuisent un aliment.

**Tech Stack :** Java 25, Gradle, JUnit 5, Gson (cœur), API serveur Hytale 0.7.0-pre.5 (plugin), Python 3.10+ (`tools/food/generate.py`), Kotlin DSL (`plugin/build.gradle.kts`).

**Spec :** `docs/superpowers/specs/2026-10-04-hycolony-nourriture-ouverte-design.md`

## Global Constraints

- Lire `CLAUDE.md` en entier avant de commencer (règle du projet pour tout agent).
- Cœur (`core/`) : Java pur, aucun import `com.hypixel` [build : ArchitectureTest].
- Plugin : aucune règle de jeu ; un port ne lève jamais d'exception ; le premier échec est journalisé, les suivants en FINE.
- Fichiers : 300 lignes visées, 400 au plus [build : `checkFileSizes`] ; 40 lignes par méthode, 5 paramètres au plus ; un paquet a au plus 15 fichiers.
- 120 colonnes, Javadoc courte sur chaque classe et méthode non triviale, source MC citée, écarts marqués `Deviation from MC (Hytale world): …`.
- Formatage : `./gradlew :<module>:spotlessApply -PspotlessIdeHook="<chemin absolu>"` fichier par fichier (d'autres sessions travaillent dans le dépôt) ; le hook `format-java.js` le fait à la fin de chaque tour pour les `.java` écrits.
- Éditer avec les outils Edit et Write, jamais par script Python ou sed.
- `git add <chemins>` explicites ; jamais `config.json` ni `config.json.bak`. Messages `type(module): description` en anglais, terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `./gradlew build` vert avant chaque commit. On ne lance jamais le serveur Hytale.
- Toute API Hytale vérifiée dans `build/vineflower/hytale-server` ; les découvertes vont dans `docs/research/plugin-b-api.md`.
- `git status` avant chaque commit : d'autres sessions ont des fichiers en cours (modèles de huttes, `.lang`…) qu'il ne faut pas indexer.

## Review Focus

1. **Un fichier d'aliment qui nomme un objet inconnu, ou hors bornes** (`Nutrition` 0, `Tier` 7) : il est ignoré et journalisé une fois, HyColony démarre ; le selftest le liste. (Task 4 : `FoodTable.addFiles`, ligne du selftest.)
2. **Une variante ou un ingrédient de la catégorie `Items.Foods`** (`Food_Fish_Raw_Rare`, `Ingredient_Flour`) ne devient jamais un aliment : la liste « Plats possibles » ne change pas. (Task 4 : `FoodTable.isDefaultFood` exclut variantes et non consommables ; selftest « foods by quality (0) » en vanilla.)
3. **Un fragment de `crafting.json` d'un sous-plugin qui garde `includeItems`, `excludeItems` ou `reduceable`** : un avertissement par clé, le reste se lit. (Task 3 : test `oldTagListsInTheFileAreIgnoredWithAWarning`.)
4. **Un fichier de tag au nom inconnu, ou une valeur inconnue** (`chef_tools`, `res:Nope`) : ignoré avec un avertissement, les autres tags restent. (Task 3 : `unknownTagIsSkippedWithOneWarning` ; Task 5 : valeurs inconnues journalisées.)
5. **Le four (`Processing` sans aliment)** n'est pas un poste de cuisson et ses combustibles n'entrent pas dans la liste de la salle à manger. (Task 6 : `CookingBenches` ne garde que les bancs dont une recette donne un aliment ; TESTING point 384.)

---

### Task 1 : garde-fou, § 7 de CLAUDE.md

Les fichiers de cette tâche sont des garde-fous : le hook `guard.js` refuse de les écrire sauf si la session a été lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`. **Si le hook refuse, s'arrêter et demander à l'utilisateur de relancer la session déverrouillée** ; ne jamais contourner le hook. Les tâches 2 et 3 (cœur seul) peuvent avancer en attendant ; la tâche 4 attend celle-ci.

**Files :**
- Modify : `CLAUDE.md` (§ 7, dernier paragraphe)
- Modify : `.claude/agents/ui-lang-checker.md:18`
- Modify : `.claude/skills/hytale-api/SKILL.md:11`

- [ ] **Step 1 : modifier CLAUDE.md § 7**

Remplacer :

```
- Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod (`hycolony/id-map.json`, `hydomum/id-map.json`, `hyvanilla/id-map.json`, `hylens/id-map.json`). Les plans de bâtiments sont dans `hycolony/styles.json`.
```

par :

```
- Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod (`hycolony/id-map.json`, `hydomum/id-map.json`, `hyvanilla/id-map.json`, `hylens/id-map.json`), sauf dans les types d'assets que HyColony ouvre aux autres mods (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), où un fichier nomme l'objet qu'il décrit. Les plans de bâtiments sont dans `hycolony/styles.json`.
```

- [ ] **Step 2 : modifier ui-lang-checker.md, ligne 18**

Après « building plans live in `hycolony/styles.json`. », insérer : « Exception: HyColony's open asset types, `Server/HyColony/Foods/<item>.json` and `Server/HyColony/JobTags/*.json`, name the items they describe. » La phrase « An asset id literal elsewhere in plugin code is a finding. » reste.

- [ ] **Step 3 : modifier la skill hytale-api, ligne 11**

Remplacer « Asset ids used by HyColony live only in `hycolony/id-map.json`. » par « Asset ids used by HyColony live only in `hycolony/id-map.json`, except in its open asset types (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), whose files name the items they describe. »

- [ ] **Step 4 : commit**

```bash
git add CLAUDE.md .claude/agents/ui-lang-checker.md .claude/skills/hytale-api/SKILL.md
git commit -m "docs: food and job tag asset files may name Hytale items (CLAUDE.md § 7)"
```

---

### Task 2 : `FoodQuality`, la valeur d'un aliment sans fichier (cœur)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/kernel/item/FoodQuality.java` (le paquet passe à 15 fichiers, la limite)
- Test : `core/src/test/java/dev/hycolony/core/kernel/item/FoodQualityTest.java`

**Interfaces :**
- Produces : `enum FoodQuality { COMMON, UNCOMMON, RARE; FoodInfo food(); }` (`dev.hycolony.core.kernel.item`).

- [ ] **Step 1 : écrire le test qui échoue**

```java
package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodQualityTest {
    @Test
    void eachRankFeedsTheMedianOfOurFoodsOfItsQuality() {
        assertEquals(new FoodInfo(3, 0, false), FoodQuality.COMMON.food());
        assertEquals(new FoodInfo(8, 2, false), FoodQuality.UNCOMMON.food());
        assertEquals(new FoodInfo(12, 3, false), FoodQuality.RARE.food());
    }
}
```

- [ ] **Step 2 : vérifier qu'il échoue**

Run : `./gradlew :core:test --tests "dev.hycolony.core.kernel.item.FoodQualityTest"`
Expected : échec de compilation, `FoodQuality` introuvable.

- [ ] **Step 3 : écrire l'énumération**

```java
package dev.hycolony.core.kernel.item;

/**
 * What a Hytale food without a HyColony food file gives a citizen, by the rank of its Hytale quality (spec 2026-10-04
 * § 5.3): the median nutrition and tier of our food table's foods of that quality. The id-map maps Hytale's quality
 * ids to these ranks, so the core names no quality.
 *
 * <p>Deviation from MC (Hytale world): MC reads an item's FoodProperties → a Hytale food without a HyColony file takes
 * the median value of our table's foods of its Quality.
 */
public enum FoodQuality {
    /** Hytale's Common, and any quality the id-map does not rank: median of the 30 Common foods. */
    COMMON(3, 0),
    /** Hytale's Uncommon: median of the 9 Uncommon foods (nutrition 6 to 9, tiers 1 and 2). */
    UNCOMMON(8, 2),
    /** Hytale's Rare and above: median of the 4 Rare foods. */
    RARE(12, 3);

    private final FoodInfo food;

    FoodQuality(int nutrition, int tier) {
        this.food = new FoodInfo(nutrition, tier, false);
    }

    /** What a food of this rank gives a citizen; never poisonous, as Hytale marks no food so. */
    public FoodInfo food() {
        return food;
    }
}
```

- [ ] **Step 4 : vérifier qu'il passe**

Run : `./gradlew :core:test --tests "dev.hycolony.core.kernel.item.FoodQualityTest"`
Expected : PASS.

- [ ] **Step 5 : build et commit**

```bash
./gradlew build
git add core/src/main/java/dev/hycolony/core/kernel/item/FoodQuality.java core/src/test/java/dev/hycolony/core/kernel/item/FoodQualityTest.java
git commit -m "feat(core): a food without a HyColony file is valued by its Hytale quality's rank"
```

---

### Task 3 : `JobTags`, les tags de métier de MC (cœur)

**Files :**
- Create : `core/src/main/java/dev/hycolony/core/crafting/recipe/JobTags.java`
- Modify : `core/src/main/java/dev/hycolony/core/crafting/recipe/CraftingRules.java` (listes → `JobTags`, `withTags`)
- Modify : `core/src/main/java/dev/hycolony/core/crafting/recipe/CraftingRulesJson.java` (anciennes clés ignorées)
- Test : `core/src/test/java/dev/hycolony/core/crafting/recipe/JobTagsTest.java` (nouveau)
- Modify (tests) : `core/src/test/java/dev/hycolony/core/crafting/recipe/CraftingRulesTest.java`, `core/src/test/java/dev/hycolony/core/testing/TestContexts.java`, `core/src/test/java/dev/hycolony/core/crafting/module/CraftingHut.java`, `core/src/test/java/dev/hycolony/core/crafting/job/CraftingWorkTest.java`, `core/src/test/java/dev/hycolony/core/crafting/module/RecipeImprovementTest.java`

**Interfaces :**
- Produces :
  - `public record JobTags(Map<String, Set<ItemKey>> tags)` avec `EMPTY`, `REDUCEABLE_INGREDIENT = "reduceable_ingredient"`, `REDUCEABLE_PRODUCT_EXCLUDED = "reduceable_product_excluded"`, `public record TagFile(String tag, List<ItemKey> values)`, `public static JobTags merge(Collection<TagFile> files, Consumer<String> warn)`, `public Set<ItemKey> get(String tag)`, et en paquet `products(String jobId)`, `excludedProducts(String jobId)`, `static boolean isKnown(String tag)`.
  - `CraftingRules.withTags(JobTags tags)` : les mêmes règles de fichier avec ces tags. `CraftingRules.parse(JsonObject, Consumer<String>)` garde sa signature et rend des règles sans tag.

- [ ] **Step 1 : écrire `JobTagsTest` (échoue)**

```java
package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobTagsTest {
    private static final ItemKey BREAD = new ItemKey("Food_Bread");
    private static final ItemKey PIE = new ItemKey("Food_Pie_Apple");

    @Test
    void filesOfOneTagAddUpLikeMinecraftTags() {
        JobTags tags = JobTags.merge(
                List.of(new JobTags.TagFile("chef_product", List.of(BREAD)), new JobTags.TagFile("chef_product", List.of(PIE))),
                w -> fail(w));
        assertEquals(Set.of(BREAD, PIE), tags.get("chef_product"));
    }

    @Test
    void unknownTagIsSkippedWithOneWarning() {
        List<String> warnings = new ArrayList<>();
        JobTags tags = JobTags.merge(List.of(new JobTags.TagFile("chef_tools", List.of(BREAD))), warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(tags.get("chef_tools").isEmpty());
    }

    @Test
    void productTagsAreFoundByTheJobIdWithoutItsNamespace() {
        JobTags tags = JobTags.merge(
                List.of(
                        new JobTags.TagFile("chef_product", List.of(BREAD)),
                        new JobTags.TagFile("chef_product_excluded", List.of(PIE))),
                w -> fail(w));
        assertEquals(Set.of(BREAD), tags.products("hycolony:chef"));
        assertEquals(Set.of(PIE), tags.excludedProducts("hycolony:chef"));
        assertEquals(Set.of(BREAD), tags.products("chef"));
    }

    @Test
    void absentTagIsEmpty() {
        assertTrue(JobTags.EMPTY.get(JobTags.REDUCEABLE_INGREDIENT).isEmpty());
        assertTrue(JobTags.EMPTY.products("hycolony:farmer").isEmpty());
    }

    @Test
    void reduceableAndJobTagsAreKnownButABareSuffixIsNot() {
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_INGREDIENT));
        assertTrue(JobTags.isKnown(JobTags.REDUCEABLE_PRODUCT_EXCLUDED));
        assertTrue(JobTags.isKnown("farmer_product_excluded"));
        assertFalse(JobTags.isKnown("_product"));
        assertFalse(JobTags.isKnown(""));
    }
}
```

- [ ] **Step 2 : vérifier qu'il échoue**

Run : `./gradlew :core:test --tests "dev.hycolony.core.crafting.recipe.JobTagsTest"`
Expected : échec de compilation, `JobTags` introuvable.

- [ ] **Step 3 : écrire `JobTags`**

```java
package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MC's crafter item tags ({@code data/minecolonies/tags/items}: {@code <job>_product}, {@code <job>_product_excluded},
 * {@code reduceable_ingredient}, {@code reduceable_product_excluded}), each the union of every file naming it, as a
 * Minecraft tag adds up its packs' files.
 *
 * <p>Deviation from MC (Hytale world): MC reads Minecraft item tags → HyColony reads them from its
 * {@code Server/HyColony/JobTags} asset files, which any mod may add to (spec 2026-10-04 § 6).
 *
 * @param tags tag name -> its items
 */
public record JobTags(Map<String, Set<ItemKey>> tags) {
    /** No tag at all. */
    public static final JobTags EMPTY = new JobTags(Map.of());

    /** MC CRAFTING_REDUCEABLE's ingredient tag: what an improvement may take off a recipe. */
    public static final String REDUCEABLE_INGREDIENT = "reduceable_ingredient";

    /** MC CRAFTING_REDUCEABLE's product exclusion tag: what is never improved. */
    public static final String REDUCEABLE_PRODUCT_EXCLUDED = "reduceable_product_excluded";

    private static final String PRODUCT = "_product";
    private static final String PRODUCT_EXCLUDED = "_product_excluded";

    /** One tag file: a tag name and the items it adds. */
    public record TagFile(String tag, List<ItemKey> values) {
        public TagFile {
            values = List.copyOf(values);
        }
    }

    public JobTags {
        Map<String, Set<ItemKey>> copy = new LinkedHashMap<>();
        tags.forEach((tag, items) -> copy.put(tag, Set.copyOf(items)));
        tags = Map.copyOf(copy);
    }

    /** Merges {@code files} by tag; a file of a tag HyColony does not read is skipped after one call to {@code warn}. */
    public static JobTags merge(Collection<TagFile> files, Consumer<String> warn) {
        Map<String, Set<ItemKey>> out = new LinkedHashMap<>();
        for (TagFile file : files) {
            if (isKnown(file.tag())) {
                out.computeIfAbsent(file.tag(), t -> new LinkedHashSet<>()).addAll(file.values());
            } else {
                warn.accept("JobTags: unknown tag '" + file.tag() + "' skipped");
            }
        }
        return new JobTags(out);
    }

    /** The items of {@code tag}; empty for a tag no file names. */
    public Set<ItemKey> get(String tag) {
        return tags.getOrDefault(tag, Set.of());
    }

    /** MC {@code <job>_product}: {@code hycolony:farmer} reads {@code farmer_product}. */
    Set<ItemKey> products(String jobId) {
        return get(jobName(jobId) + PRODUCT);
    }

    /** MC {@code <job>_product_excluded}. */
    Set<ItemKey> excludedProducts(String jobId) {
        return get(jobName(jobId) + PRODUCT_EXCLUDED);
    }

    /** Whether HyColony reads {@code tag}: the two reduceable tags, or a job's product or product exclusion tag. */
    static boolean isKnown(String tag) {
        return tag.equals(REDUCEABLE_INGREDIENT)
                || tag.equals(REDUCEABLE_PRODUCT_EXCLUDED)
                || (tag.endsWith(PRODUCT) && tag.length() > PRODUCT.length())
                || (tag.endsWith(PRODUCT_EXCLUDED) && tag.length() > PRODUCT_EXCLUDED.length());
    }

    /** The job id without its namespace, as MC names its tags after the job. */
    private static String jobName(String jobId) {
        int colon = jobId.indexOf(':');
        return colon < 0 ? jobId : jobId.substring(colon + 1);
    }
}
```

- [ ] **Step 4 : vérifier que `JobTagsTest` passe**

Run : `./gradlew :core:test --tests "dev.hycolony.core.crafting.recipe.JobTagsTest"`
Expected : PASS.

- [ ] **Step 5 : réécrire `CraftingRulesTest` pour les tags (échoue)**

Dans `CraftingRulesTest` :

1. `JSON` perd `includeItems`, `excludeItems` et la section `reduceable` :

```java
    static final String JSON = """
        {"jobs":{"farmer":{
            "allow":[{"bench":"Farmingbench","categories":["*"]},{"bench":"Fieldcraft","categories":["Seeds"]}],
            "custom":[{"id":"farmer_wheat_seeds","hytaleRecipe":"Plant_Seeds_Wheat",
                       "minBuildingLevel":1,"maxBuildingLevel":5}]}}}
        """;

    static final JobTags TAGS = tags(
            new JobTags.TagFile("farmer_product", List.of(new ItemKey("Food_Bread"))),
            new JobTags.TagFile("farmer_product_excluded", List.of(new ItemKey("Plant_Sapling_Oak"))),
            new JobTags.TagFile(JobTags.REDUCEABLE_INGREDIENT, List.of(RecipeFixtures.ESSENCE)),
            new JobTags.TagFile(JobTags.REDUCEABLE_PRODUCT_EXCLUDED, List.of(new ItemKey("Plant_Seeds_Wheat"))));

    final CraftingRules rules = CraftingRules.parse(json(JSON), w -> fail(w)).withTags(TAGS);

    private static JobTags tags(JobTags.TagFile... files) {
        return JobTags.merge(List.of(files), w -> fail(w));
    }
```

2. `exclusionWinsOverInclusionLikeMc` devient :

```java
    @Test
    void exclusionWinsOverInclusionLikeMc() {
        CraftingRules both = CraftingRules.parse(json("{\"jobs\":{\"farmer\":{}}}"), w -> fail(w))
                .withTags(tags(
                        new JobTags.TagFile("farmer_product", List.of(new ItemKey("Food_Bread"))),
                        new JobTags.TagFile("farmer_product_excluded", List.of(new ItemKey("Food_Bread")))));
        assertFalse(both.allows("farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
    }
```

3. `subPluginFragmentAddsJobsAndKeysButNeverRedefinesOne` : les fragments perdent `includeItems` et `reduceable`, et les assertions sur `Food_Bread` (fermier) et `Rock_Stone` disparaissent :

```java
    @Test
    void subPluginFragmentAddsJobsAndKeysButNeverRedefinesOne() {
        // As the plugin merges crafting.json (SubPlugins.CRAFTING_DEPTH): job by job, then key by key in a job.
        JsonFragments file = new JsonFragments(2);
        file.add("HyColony", json("""
            {"jobs":{"farmer":{"allow":[{"bench":"Farmingbench","categories":["*"]}]}}}
            """));

        List<JsonFragments.Conflict> conflicts = file.add("Pack", json("""
            {"jobs":{"farmer":{"allow":[]},
                     "baker":{"allow":[{"bench":"Cookingbench","categories":["*"]}]}}}
            """));
        CraftingRules merged = CraftingRules.parse(file.merged(), w -> fail(w));

        assertEquals(List.of(new JsonFragments.Conflict("jobs/farmer/allow", "HyColony", "Pack")), conflicts);
        assertTrue(merged.allows("farmer", RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn")));
        assertTrue(merged.allows("baker", RecipeFixtures.at("Cookingbench", "Pie", "Food_Pie_Apple")));
    }
```

4. Dans `eachBadEntryWarnsOnceAndTheRestIsKept`, le JSON ne change pas (il garde `"includeItems":["Food_Bread",{}]` et `"reduceable":{"ingredients":"Ingredient_Life_Essence"}`, qui donnent chacun un avertissement « no longer read » au lieu d'un avertissement d'entrée invalide : le total reste 7). Retirer seulement la ligne `assertTrue(r.allows("farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));`.

5. Ajouter :

```java
    @Test
    void namespacedJobReadsTheTagsOfItsName() {
        CraftingRules r = CraftingRules.parse(json("{\"jobs\":{\"hycolony:farmer\":{}}}"), w -> fail(w))
                .withTags(TAGS);
        assertTrue(r.allows("hycolony:farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
    }

    @Test
    void withoutTagsNothingIsIncludedNorReduceable() {
        CraftingRules plain = CraftingRules.parse(json(JSON), w -> fail(w));
        assertFalse(plain.allows("farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
        assertFalse(plain.isReduceable(RecipeFixtures.ESSENCE));
        assertTrue(plain.allows("farmer", RecipeFixtures.at("Farmingbench", "Anything", "Plant_Seeds_Corn")));
    }

    @Test
    void oldTagListsInTheFileAreIgnoredWithAWarning() {
        List<String> warnings = new ArrayList<>();
        CraftingRules r = CraftingRules.parse(json("""
            {"jobs":{"farmer":{"includeItems":["Food_Bread"],"excludeItems":[]}},
             "reduceable":{"ingredients":["Ingredient_Life_Essence"]}}
            """), warnings::add);
        assertEquals(3, warnings.size(), warnings::toString);
        assertFalse(r.allows("farmer", RecipeFixtures.at("Cookingbench", "Bread", "Food_Bread")));
        assertFalse(r.isReduceable(RecipeFixtures.ESSENCE));
    }
```

Les autres tests de la classe ne changent pas (ils lisent `rules`, qui a maintenant ses tags).

- [ ] **Step 6 : vérifier qu'il échoue**

Run : `./gradlew :core:test --tests "dev.hycolony.core.crafting.recipe.CraftingRulesTest"`
Expected : échec de compilation, `withTags` introuvable.

- [ ] **Step 7 : `CraftingRules` lit les tags**

Remplacer la Javadoc de classe, `EMPTY`, `JobRules`, les champs, le constructeur, `allows`, `isReduceable` et `isExcludedFromReduction`, et ajouter `withTags` :

```java
/**
 * What each job may learn and improve: the rules of {@code hycolony/crafting.json} (the benches and categories a job
 * learns from, and the custom recipes its hut gets by level, MC CustomRecipe) and the job tags (MC the
 * {@code crafterProduct}, {@code crafterProductExclusions} and {@code CRAFTING_REDUCEABLE} item tags, {@link JobTags}).
 * Deviation from MC: a job allows benches and their categories rather than a list of products, since every Hytale
 * recipe names its bench.
 */
public final class CraftingRules {
    /** No job may learn anything, nothing is reduceable. */
    public static final CraftingRules EMPTY = new CraftingRules(Map.of(), JobTags.EMPTY);
```

```java
    /** What one job may learn by the file, and the custom recipes its hut gets. */
    record JobRules(List<Allow> allow, List<CustomRecipe> custom) {
        JobRules {
            allow = List.copyOf(allow);
            custom = List.copyOf(custom);
        }
    }

    private final Map<String, JobRules> jobs;
    private final JobTags tags;

    CraftingRules(Map<String, JobRules> jobs, JobTags tags) {
        this.jobs = Map.copyOf(jobs);
        this.tags = tags;
    }
```

La Javadoc de `parse` ajoute « without job tags (see {@link #withTags}) ». Puis :

```java
    /** These file rules with {@code tags}, read once the assets are loaded (spec 2026-10-04 § 6.3). */
    public CraftingRules withTags(JobTags tags) {
        return new CraftingRules(jobs, tags);
    }

    /**
     * Whether {@code jobId} may learn {@code recipe}. As MC CraftingUtils.getProductValidatorBasedOnTags, an output in
     * the job's product exclusion tag is refused first and one in its product tag allowed; otherwise the recipe's bench
     * and categories must be allowed. A job absent from the file may learn nothing (MC BuildingFarmer:
     * {@code orElse(false)}).
     */
    public boolean allows(String jobId, Recipe recipe) {
        JobRules job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        ItemKey output = recipe.primaryOutput().item();
        if (tags.excludedProducts(jobId).contains(output)) {
            return false;
        }
        return tags.products(jobId).contains(output) || job.allow().stream().anyMatch(a -> a.accepts(recipe.bench()));
    }
```

```java
    /** Whether an improvement may take one of this ingredient off a recipe (MC crafterIngredient reduceable tag). */
    public boolean isReduceable(ItemKey ingredient) {
        return tags.get(JobTags.REDUCEABLE_INGREDIENT).contains(ingredient);
    }

    /** Whether a recipe making this is never improved (MC crafterProductExclusions reduceable tag). */
    public boolean isExcludedFromReduction(ItemKey product) {
        return tags.get(JobTags.REDUCEABLE_PRODUCT_EXCLUDED).contains(product);
    }
```

Retirer l'import `java.util.Set` s'il n'est plus utilisé hors de `Allow` (il l'est par `Allow` : le garder).

- [ ] **Step 8 : `CraftingRulesJson` ignore les anciennes listes**

Dans `read` :

```java
    /** The rules of the whole file, without tags; a missing {@code jobs} section reads as empty. */
    CraftingRules read(JsonObject json) {
        Map<String, JobRules> jobs = new LinkedHashMap<>();
        JsonObject jobsJson = object(json, "jobs", "crafting.json");
        for (String jobId : jobsJson.keySet()) {
            job(jobsJson.get(jobId), "jobs." + jobId).ifPresent(j -> jobs.put(jobId, j));
        }
        ignoreTagList(json, "reduceable", "crafting.json");
        return new CraftingRules(jobs, JobTags.EMPTY);
    }
```

Dans `job`, avant le `return` :

```java
        ignoreTagList(o, "includeItems", where);
        ignoreTagList(o, "excludeItems", where);
        return Optional.of(new JobRules(allow, custom));
```

Remplacer la méthode `items` (plus utilisée) par :

```java
    /** Warns once that {@code key}, a job tag list crafting.json no longer holds, is ignored (spec 2026-10-04 § 6.2). */
    private void ignoreTagList(JsonObject parent, String key, String where) {
        if (parent.has(key)) {
            warn.accept("crafting.json: " + where + "." + key
                    + " is no longer read: job tags live in Server/HyColony/JobTags");
        }
    }
```

Retirer les imports devenus inutiles (`LinkedHashSet`, `ItemKey`, `Set` si plus utilisés : `allow` utilise `Set.copyOf`, le garder).

- [ ] **Step 9 : les tests qui posaient `reduceable` passent par les tags**

`core/src/test/java/dev/hycolony/core/testing/TestContexts.java`, à côté de `craftingRules` :

```java
    public JobTags jobTags = JobTags.EMPTY;
```

(import `dev.hycolony.core.crafting.recipe.JobTags`).

`core/src/test/java/dev/hycolony/core/crafting/module/CraftingHut.java`, dans le constructeur `(TestContexts t, String rules, BuildingType type)` :

```java
        t.craftingRules =
                CraftingRules.parse(JsonParser.parseString(rules).getAsJsonObject(), w -> {}).withTags(t.jobTags);
```

et la Javadoc de ce constructeur ajoute « under {@code t.jobTags} ».

`core/src/test/java/dev/hycolony/core/crafting/job/CraftingWorkTest.java` : remplacer la constante `REDUCEABLE_ESSENCE` par

```java
    private static final String FARMINGBENCH = """
            {"jobs": {"%s": {"allow": [{"bench": "Farmingbench", "categories": ["*"]}]}}}
            """.formatted(CraftingHut.JOB);

    private static final JobTags REDUCEABLE_ESSENCE =
            JobTags.merge(List.of(new JobTags.TagFile(JobTags.REDUCEABLE_INGREDIENT, List.of(ESSENCE))), w -> {});
```

et dans `lastRunImprovesTheRecipeThenDumps` :

```java
        TestContexts t = new TestContexts();
        t.random = () -> LUCKY;
        t.jobTags = REDUCEABLE_ESSENCE;
        CrafterRig lucky = new CrafterRig(t, FARMINGBENCH, seeds(List.of(), Optional.empty()));
```

(import `dev.hycolony.core.crafting.recipe.JobTags`). Chercher d'autres usages de `REDUCEABLE_ESSENCE` dans le fichier (`grep -n REDUCEABLE_ESSENCE`) et les traiter de même.

`core/src/test/java/dev/hycolony/core/crafting/module/RecipeImprovementTest.java` : remplacer `rules(String excludedProducts)` par

```java
    private static final String FIELDCRAFT = """
            {"jobs": {"%s": {"allow": [{"bench": "Fieldcraft", "categories": ["*"]}]}}}""".formatted(CraftingHut.JOB);

    /** A hut under {@code rules} whose improvements may take off essence, fibre and trunks, never from {@code excluded}. */
    private static CraftingHut hut(String rules, String... excluded) {
        TestContexts t = new TestContexts();
        t.jobTags = JobTags.merge(
                List.of(
                        new JobTags.TagFile(
                                JobTags.REDUCEABLE_INGREDIENT,
                                Stream.of("Ingredient_Life_Essence", "Ingredient_Fibre", "Wood_Oak_Trunk", "Wood_Birch_Trunk")
                                        .map(ItemKey::new)
                                        .toList()),
                        new JobTags.TagFile(
                                JobTags.REDUCEABLE_PRODUCT_EXCLUDED, Stream.of(excluded).map(ItemKey::new).toList())),
                w -> {});
        return new CraftingHut(t, rules, TestCrafters.hut(true, 1));
    }
```

puis :
- ligne 36 : `private final CraftingHut h = hut(FIELDCRAFT);`
- ligne 119 : `CraftingHut excluded = hut(FIELDCRAFT, "Plant_Seeds_Wheat");`
- ligne 195 (`improvedRecipeTheHutCannotHoldIsNotSwappedIn`) :

```java
        CraftingHut benchHut = hut("""
                {"jobs": {"%s": {"allow": [{"bench": "Farmingbench", "categories": ["*"]}]}}}"""
                .formatted(CraftingHut.JOB));
```

(imports `dev.hycolony.core.crafting.recipe.JobTags`, `dev.hycolony.core.testing.TestContexts`, `dev.hycolony.core.testing.crafting.TestCrafters`, `java.util.stream.Stream`).

- [ ] **Step 10 : tous les tests du cœur passent**

Run : `./gradlew :core:test`
Expected : PASS. Un test d'artisanat qui échoue ici a perdu une règle de `reduceable` ou d'`includeItems` : chercher dans `core/src/test` (`grep -rn "includeItems\|excludeItems\|reduceable" core/src/test`) et le passer par `t.jobTags`.

- [ ] **Step 11 : build et commit**

```bash
./gradlew build
git add core/src/main/java/dev/hycolony/core/crafting/recipe/JobTags.java core/src/main/java/dev/hycolony/core/crafting/recipe/CraftingRules.java core/src/main/java/dev/hycolony/core/crafting/recipe/CraftingRulesJson.java core/src/test/java/dev/hycolony/core/crafting/recipe/JobTagsTest.java core/src/test/java/dev/hycolony/core/crafting/recipe/CraftingRulesTest.java core/src/test/java/dev/hycolony/core/testing/TestContexts.java core/src/test/java/dev/hycolony/core/crafting/module/CraftingHut.java core/src/test/java/dev/hycolony/core/crafting/job/CraftingWorkTest.java core/src/test/java/dev/hycolony/core/crafting/module/RecipeImprovementTest.java
git commit -m "feat(core): MC's job tags are their own merged lists, no longer crafting.json's"
```

---

### Task 4 : les aliments, type d'asset de HyColony (plugin)

Attend la tâche 1 (les fichiers d'aliments nomment des objets hors de l'id-map).

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/food/FoodValueAsset.java`
- Create : `plugin/src/main/java/dev/hycolony/plugin/food/FoodTable.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/food/HytaleFoods.java` (table paresseuse)
- Modify : `plugin/src/main/java/dev/hycolony/plugin/food/FoodIds.java` (sans `foods`, avec `foodCategory`, `qualityRanks`)
- Modify : `plugin/src/main/java/dev/hycolony/plugin/IdMap.java:198` (vérifications)
- Modify : `plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java:47-57` (enregistrement)
- Create : `plugin/src/main/java/dev/hycolony/plugin/command/FoodSelfTest.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/command/HyColonyCommand.java:226`
- Modify : `plugin/src/main/resources/hycolony/id-map.json:107-163`
- Create : `plugin/src/main/resources/Server/HyColony/Foods/<id>.json` (50 fichiers)
- Modify : `tools/food/generate.py`, `plugin/build.gradle.kts:75-105` (`checkFoodTooltips`)
- Regenerate : `plugin/src/main/resources/Server/Languages/{en-US,fr-FR}/hycolony_food.lang`
- Modify : `docs/research/plugin-b-api.md` (nouvelle section 53)

**Interfaces :**
- Consumes : `FoodQuality` (Task 2).
- Produces : `FoodValueAsset.register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry)`, `public static Map<String, FoodValueAsset> FoodValueAsset.all()`, `FoodIds.rank(@Nullable String qualityId)`, `FoodIds.isRank(String)`, `FoodTable.load(FoodIds)`.

Les plugins n'ont pas de tests unitaires (CLAUDE.md § 8) : cette tâche se vérifie par le build, `checkFoodTooltips`, le selftest et `docs/TESTING.md` (Task 7).

- [ ] **Step 1 : l'id-map**

Dans `plugin/src/main/resources/hycolony/id-map.json`, la section `food` devient (la table `foods` part dans les fichiers du step 5 : **la recopier d'abord**, elle sert au step 5) :

```json
  "food": {
    "cookingBench": "Campfire",
    "eatParticle": "Food_Eat",
    "defaultFuels": ["Ingredient_Charcoal"],
    "foodCategory": "Items.Foods",
    "qualityRanks": {"Common": "COMMON", "Uncommon": "UNCOMMON", "Rare": "RARE", "Epic": "RARE", "Legendary": "RARE"}
  },
```

- [ ] **Step 2 : `FoodIds`**

```java
package dev.hycolony.plugin.food;

import dev.hycolony.core.kernel.item.FoodQuality;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The {@code food} section of the id-map (sp4b-hytale-food § 8, spec 2026-10-04 § 5): the bench that cooks raw food
 * (MC's furnace), the particle of a citizen eating, the fuels a dining hall allows at first, and how a Hytale food
 * without a HyColony food file is told (its item category) and valued (its quality's rank). The foods themselves are
 * the {@code Server/HyColony/Foods} files ({@link FoodValueAsset}). Absent from an older id-map: no food by default.
 *
 * @param cookingBench the processing bench whose recipes cook food (Hytale's campfire)
 * @param eatParticle the particle system of crumbs at a citizen's mouth
 * @param defaultFuels the fuel item ids a new dining hall allows (MC's coal and charcoal)
 * @param foodCategory the item category of Hytale's foods
 * @param qualityRanks Hytale item quality id -> {@link FoodQuality} constant name
 */
public record FoodIds(
        @Nullable String cookingBench,
        @Nullable String eatParticle,
        @Nullable List<String> defaultFuels,
        @Nullable String foodCategory,
        @Nullable Map<String, String> qualityRanks) {
    /** An id-map without food. */
    public static final FoodIds NONE = new FoodIds(null, null, null, null, null);

    /** The cooking bench id; empty when the id-map has none (nothing cooks). */
    public Optional<String> bench() {
        return Optional.ofNullable(cookingBench);
    }

    public Optional<String> particle() {
        return Optional.ofNullable(eatParticle);
    }

    /** The fuels a new dining hall allows; none in an older id-map. */
    public List<String> fuels() {
        return Objects.requireNonNullElse(defaultFuels, List.of());
    }

    /** The item category of Hytale's foods; empty in an older id-map (no food without a file). */
    public Optional<String> category() {
        return Optional.ofNullable(foodCategory);
    }

    /** Quality id -> rank name, as written; none in an older id-map. */
    public Map<String, String> ranks() {
        return Objects.requireNonNullElse(qualityRanks, Map.of());
    }

    /** The rank of {@code qualityId}: COMMON for a quality the id-map does not rank, or ranks by an unknown name. */
    public FoodQuality rank(@Nullable String qualityId) {
        String name = qualityId == null ? null : ranks().get(qualityId);
        return name != null && isRank(name) ? FoodQuality.valueOf(name) : FoodQuality.COMMON;
    }

    /** Whether {@code name} names a {@link FoodQuality} constant. */
    public static boolean isRank(String name) {
        return Arrays.stream(FoodQuality.values()).anyMatch(q -> q.name().equals(name));
    }
}
```

- [ ] **Step 3 : `IdMap.validate`**

Remplacer la ligne `check(errors, "food item", byId(List.copyOf(food().table().keySet())), item);` par :

```java
        check(
                errors,
                "item quality",
                byId(List.copyOf(food().ranks().keySet())),
                id -> ItemQuality.getAssetMap().getIndexOrDefault(id, Integer.MIN_VALUE) != Integer.MIN_VALUE);
        check(errors, "food quality rank", byId(List.copyOf(food().ranks().values())), FoodIds::isRank);
```

(import `com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality` ; `IndexedLookupTableAssetMap.getIndexOrDefault(String, int)` est celui qu'appelle `Item.java:1254`).

- [ ] **Step 4 : `FoodValueAsset`**

```java
package dev.hycolony.plugin.food;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyColony/Foods/<item id>.json} file (spec 2026-10-04 § 5.1): what eating that item gives a citizen
 * (MC FoodProperties nutrition, IMinecoloniesFoodItem tier, {@code poisonous_food} tag). A HyColony asset type that
 * every pack may add to; a pack loaded later replaces a file of the same name.
 */
public final class FoodValueAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, FoodValueAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyColony/Foods";

    static final AssetBuilderCodec<String, FoodValueAsset> CODEC = AssetBuilderCodec.builder(
                    FoodValueAsset.class,
                    FoodValueAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Nutrition", Codec.INTEGER), (a, v) -> a.nutrition = v, a -> a.nutrition)
            .add()
            .append(new KeyedCodec<>("Tier", Codec.INTEGER), (a, v) -> a.tier = v, a -> a.tier)
            .add()
            .append(new KeyedCodec<>("Poisonous", Codec.BOOLEAN), (a, v) -> a.poisonous = v, a -> a.poisonous)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private int nutrition;
    private int tier;
    private boolean poisonous;

    private FoodValueAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(FoodValueAsset.class, new DefaultAssetMap<String, FoodValueAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(FoodValueAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every food file read, by item id; empty before the assets load or without the type registered. */
    public static Map<String, FoodValueAsset> all() {
        AssetStore<String, FoodValueAsset, DefaultAssetMap<String, FoodValueAsset>> store =
                AssetRegistry.getAssetStore(FoodValueAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    int nutrition() {
        return nutrition;
    }

    int tier() {
        return tier;
    }

    boolean poisonous() {
        return poisonous;
    }
}
```

Si le compilateur signale `loadsAfter` (tableau générique de varargs) ou un cast non vérifié, ajouter `@SuppressWarnings("unchecked")` sur `register` seulement, avec un commentaire qui dit pourquoi. Vérifier dans `build/vineflower/hytale-server/com/hypixel/hytale/assetstore/codec/AssetBuilderCodec.java` que `builder(Class, Supplier, Codec, BiConsumer, Function, BiConsumer, Function)` est bien la signature (comme `ShopAsset.CODEC`, `builtin/adventure/shop/ShopAsset.java:20-27`).

- [ ] **Step 5 : les 50 fichiers d'aliments**

Un fichier par ligne, `plugin/src/main/resources/Server/HyColony/Foods/<id>.json`, avec l'outil Write, au format :

```json
{ "Nutrition": 4, "Tier": 0, "Poisonous": false }
```

| id | Nutrition | Tier | Poisonous |
|---|---|---|---|
| Plant_Fruit_Apple, Plant_Fruit_Azure, Plant_Fruit_Berries_Red, Plant_Fruit_Coconut, Plant_Fruit_Mango, Plant_Fruit_Pinkberry, Plant_Fruit_Poison, Plant_Fruit_Spiral, Plant_Fruit_Windwillow | 4 | 0 | false |
| Food_Egg, Food_Candy_Cane, Food_Fish_Raw | 2 | 0 | false |
| Food_Chicken_Raw | 2 | 0 | true |
| Food_Beef_Raw, Food_Pork_Raw, Food_Wildmeat_Raw, Plant_Crop_Carrot_Item | 3 | 0 | false |
| Plant_Crop_Aubergine_Item, Plant_Crop_Cauliflower_Item, Plant_Crop_Chilli_Item, Plant_Crop_Corn_Item, Plant_Crop_Lettuce_Item, Plant_Crop_Onion_Item, Plant_Crop_Potato_Item, Plant_Crop_Pumpkin_Item, Plant_Crop_Rice_Item, Plant_Crop_Tomato_Item, Plant_Crop_Turnip_Item | 1 | 0 | false |
| Plant_Crop_Mushroom_Glowing_Blue, _Green, _Orange, _Purple, _Red, _Violet (préfixe `Plant_Crop_Mushroom_Glowing`) | 1 | 0 | true |
| Food_Wildmeat_Cooked | 8 | 0 | false |
| Food_Fish_Grilled, Food_Vegetable_Cooked | 5 | 0 | false |
| Food_Bread, Food_Cheese, Food_Popcorn | 6 | 1 | false |
| Food_Kebab_Fruit, Food_Kebab_Meat, Food_Kebab_Mushroom, Food_Kebab_Vegetable | 8 | 2 | false |
| Food_Salad_Berry, Food_Salad_Mushroom | 9 | 2 | false |
| Food_Salad_Caesar, Food_Pie_Apple, Food_Pie_Pumpkin | 12 | 3 | false |
| Food_Pie_Meat | 13 | 3 | false |

Vérifier ensuite que les 50 fichiers redonnent exactement la table recopiée au step 1 (même ids, mêmes valeurs) : `ls plugin/src/main/resources/Server/HyColony/Foods | wc -l` donne 50, et une comparaison id par id avec la copie.

- [ ] **Step 6 : `FoodTable`**

```java
package dev.hycolony.plugin.food;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The foods a citizen eats (spec 2026-10-04 § 5): each {@code Server/HyColony/Foods} file whose item exists, then each
 * other consumable, non-variant item of the id-map's food category, at the value of its quality's rank
 * ({@link dev.hycolony.core.kernel.item.FoodQuality}). Read once the assets are loaded; never throws (a failure
 * answers what was read so far, logged SEVERE).
 */
final class FoodTable {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private FoodTable() {}

    /** Every food, by item. */
    static Map<ItemKey, FoodInfo> load(FoodIds ids) {
        Map<ItemKey, FoodInfo> out = new HashMap<>();
        try {
            Map<String, Item> items = Item.getAssetMap().getAssetMap();
            addFiles(items, out);
            ids.category().ifPresent(category -> addByQuality(items, category, ids, out));
            LOG.at(Level.INFO).log("Foods: %d, %d of them from a food file", out.size(), FoodValueAsset.all().size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony foods could not be read");
        }
        return out;
    }

    /** Each food file of a known item; a file of an unknown item, or out of bounds, is skipped and logged. */
    private static void addFiles(Map<String, Item> items, Map<ItemKey, FoodInfo> out) {
        List<String> unknown = new ArrayList<>();
        for (FoodValueAsset f : FoodValueAsset.all().values()) {
            if (!items.containsKey(f.getId())) {
                unknown.add(f.getId());
                continue;
            }
            try {
                out.put(new ItemKey(f.getId()), new FoodInfo(f.nutrition(), f.tier(), f.poisonous()));
            } catch (IllegalArgumentException e) {
                LOG.at(Level.WARNING).log("HyColony food file %s skipped: %s", f.getId(), e.getMessage());
            }
        }
        if (!unknown.isEmpty()) {
            LOG.at(Level.WARNING).log("HyColony food files of unknown items skipped: %s", unknown);
        }
    }

    /** Each Hytale food without a file, at its quality rank's value (MC FoodUtils.EDIBLE, any modded food). */
    private static void addByQuality(Map<String, Item> items, String category, FoodIds ids, Map<ItemKey, FoodInfo> out) {
        for (Item item : items.values()) {
            if (item != null && isDefaultFood(item, category)) {
                out.putIfAbsent(new ItemKey(item.getId()), ids.rank(qualityId(item)).food());
            }
        }
    }

    /**
     * A consumable item of the food category that is no variant: Hytale's variants (Food_Fish_Raw_Rare…) only carry a
     * recipe whose output is another item, and its library hides them (Item.isVariant).
     */
    private static boolean isDefaultFood(Item item, String category) {
        String[] categories = item.getCategories();
        return item.isConsumable()
                && !item.isVariant()
                && categories != null
                && Arrays.asList(categories).contains(category);
    }

    private static @Nullable String qualityId(Item item) {
        ItemQuality quality = ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
        return quality == null ? null : quality.getId();
    }
}
```

`addByQuality` a 4 paramètres (limite 5). Si PMD signale `continue` ou la méthode `addFiles`, réécrire la boucle avec un `if/else` au lieu du `continue`.

- [ ] **Step 7 : `HytaleFoods` lit `FoodTable`**

Remplacer le champ `foods`, le constructeur, `food` et `foods` :

```java
    private final FoodIds ids;
    private @Nullable Map<ItemKey, FoodInfo> foods;
    private @Nullable Map<ItemKey, ItemKey> cooked;

    public HytaleFoods(FoodIds ids) {
        this.ids = ids;
    }

    @Override
    public Optional<FoodInfo> food(ItemKey item) {
        return Optional.ofNullable(table().get(item));
    }

    /** Every food, by id. */
    @Override
    public List<ItemKey> foods() {
        return table().keySet().stream().sorted(Comparator.comparing(ItemKey::id)).toList();
    }

    /** The foods, read on first use, once the assets are loaded ({@link FoodTable}). */
    private Map<ItemKey, FoodInfo> table() {
        Map<ItemKey, FoodInfo> map = foods;
        if (map == null) {
            map = FoodTable.load(ids);
            foods = map;
        }
        return map;
    }
```

La Javadoc de classe devient : « FoodCatalog over HyColony's food files and Hytale's other foods ({@link FoodTable}), and what the cooking bench's recipes turn each item into (MC the furnace's smelting result). Both are read on first use, the asset maps being loaded by then; an asset reload needs a restart, like {@code HytaleItemCatalog}. Never throws. Deviation from MC: Hytale has no hunger nor nutrition, so the values are HyColony's, after MC's for the matching foods (spec SP4b § 2.2); MC's tier 1 for a plain food of nutrition 12 and saturation 0.8 has no match, and no Hytale food gives back a container (MC's bowl). » Retirer l'import `java.util.HashMap` s'il n'est plus utilisé (il l'est encore par `loadCooked` jusqu'à la tâche 6).

- [ ] **Step 8 : enregistrer le type au `setup()`**

Dans `HyColonyPlugin.setup()`, juste après `ColonyConfig colonyConfig = config.get().toCore();` :

```java
        // HyColony's open asset types, read in every pack once the assets load (spec 2026-10-04 § 5).
        FoodValueAsset.register(getAssetRegistry());
```

(import `dev.hycolony.plugin.food.FoodValueAsset`).

- [ ] **Step 9 : `FoodSelfTest`**

```java
package dev.hycolony.plugin.command;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.food.FoodValueAsset;
import java.util.List;
import java.util.Set;

/** Selftest step: HyColony's food files and Hytale's other foods reached the food catalog (spec 2026-10-04 § 5). */
final class FoodSelfTest {
    private FoodSelfTest() {}

    /** The food files the catalog took, those it skipped, and how many foods it values by quality. */
    static void run(SelfTestReport report, WorldRuntime rt) {
        FoodCatalog foods = rt.manager().context().ports().foods();
        Set<String> files = FoodValueAsset.all().keySet();
        List<String> skipped = files.stream()
                .filter(id -> foods.food(new ItemKey(id)).isEmpty())
                .sorted()
                .toList();
        report.line(
                "food files (" + (files.size() - skipped.size()) + " of " + files.size() + ")",
                !files.isEmpty() && skipped.isEmpty(),
                files.isEmpty() ? "no food file read" : "skipped (unknown item or out of bounds): " + skipped);
        long byQuality = foods.foods().stream().filter(f -> !files.contains(f.id())).count();
        report.line("foods by quality (" + byQuality + ")", true, "");
    }
}
```

Dans `HyColonyCommand`, après `RecipesSelfTest.run(out, rt);` : `FoodSelfTest.run(out, rt);`. Vérifier que `rt.manager().context().ports().foods()` existe (`GamePorts.foods()`, comme `colony.context().ports().foods()` dans le cœur).

- [ ] **Step 10 : le générateur d'infobulles lit les fichiers**

Dans `tools/food/generate.py` :

1. Docstring : « For each food of HyColony's food files (plugin/src/main/resources/Server/HyColony/Foods/<id>.json) this writes: » et « each food headed by a comment the build checks against its food file (plugin's checkFoodTooltips). »
2. Après `ID_MAP = …` : `FOODS = PACK / "Server" / "HyColony" / "Foods"`.
3. Ajouter, avant `main` :

```python
def load_foods():
    """HyColony's food files, by item id, with the keys the tooltips read (Poisonous defaults to false)."""
    foods = {}
    for path in sorted(FOODS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        foods[path.stem] = {"nutrition": data["Nutrition"], "tier": data["Tier"],
                            "poisonous": data.get("Poisonous", False)}
    return foods
```

4. Dans `main` : `foods = load_foods()` remplace `foods = food_ids["foods"]` (`food_ids` reste lu pour `cookingBench` jusqu'à la tâche 6), et la première ligne écrite devient `"# Generated by tools/food/generate.py from Server/HyColony/Foods: do not edit by hand."`.
5. `header` : sa docstring dit « against its food file ».

- [ ] **Step 11 : `checkFoodTooltips` lit les fichiers**

Dans `plugin/build.gradle.kts`, la tâche devient :

```kotlin
val checkFoodTooltips by tasks.registering {
    val resources = layout.projectDirectory.dir("src/main/resources").asFile
    val patchDir = resources.resolve("Server/Patch/HyColony/Food")
    val foodDir = resources.resolve("Server/HyColony/Foods")
    // The languages tools/food/generate.py writes (its TEXTS table).
    val langFiles = listOf("en-US", "fr-FR").map { resources.resolve("Server/Languages/$it/hycolony_food.lang") }
    inputs.files(fileTree(patchDir))
    inputs.files(fileTree(foodDir))
    inputs.file(resources.resolve("hycolony/id-map.json"))
    inputs.files(langFiles)
    val stamp = layout.buildDirectory.file("tmp/checkFoodTooltips.stamp")
    outputs.file(stamp)
    doLast {
        val idMap = groovy.json.JsonSlurper().parse(resources.resolve("hycolony/id-map.json")) as Map<*, *>
        val food = idMap["food"] as Map<*, *>
        val foods = foodDir.listFiles().orEmpty().filter { it.name.endsWith(".json") }
            .associate { it.name.removeSuffix(".json") to (groovy.json.JsonSlurper().parse(it) as Map<*, *>) }
        // Each food's header, then its description line; the bench decides which foods are raw.
        val expected = listOf("# cookingBench=${food["cookingBench"]}") + foods.keys.sorted().flatMap { id ->
            val f = foods.getValue(id)
            listOf(
                "# $id nutrition=${f["Nutrition"]} tier=${f["Tier"]} poisonous=${f["Poisonous"] ?: false}",
                "$id.description")
        }
        val patches = patchDir.listFiles().orEmpty().map { it.name.removeSuffix(".json") }.toSortedSet()
        val problems = mutableListOf<String>()
        if (patches != foods.keys.toSortedSet()) problems += "patches $patches != foods ${foods.keys.sorted()}"
        langFiles.forEach { file ->
            val lines = file.takeIf { it.isFile }?.readLines().orEmpty().drop(1)
                .map { if (it.startsWith("#")) it else it.substringBefore(" = ") }
            if (lines != expected) problems += "${file.parentFile.name}/hycolony_food.lang differs from the food files"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Food tooltips out of date, run python tools/food/generate.py:\n" + problems.joinToString("\n"))
        }
        stamp.get().asFile.apply { parentFile.mkdirs(); writeText("ok\n") }
    }
}
```

- [ ] **Step 12 : régénérer les infobulles**

Run : `python tools/food/generate.py` (premier plan, jamais en arrière-plan)
Expected : `50 foods, N raw`. Le vrai contrôle est le diff : `git diff plugin/src/main/resources/Server/Languages plugin/src/main/resources/Server/Patch` ne montre que la première ligne de chaque `hycolony_food.lang` ; les 50 patchs et toutes les descriptions sont inchangés. Noter N pour la tâche 6.

- [ ] **Step 13 : noter l'API dans la recherche**

Ajouter à `docs/research/plugin-b-api.md`, avant « ## Could not verify », une section « ## 53. Types d'assets d'un plugin, catégorie et qualité d'un objet (2026-10-04) » avec : l'enregistrement par `getAssetRegistry().register(HytaleAssetStore.builder(...).setPath(...).setCodec(...).setKeyFunction(...).loadsAfter(Item.class).build())` dans `setup()` (`builtin/adventure/shop/ShopPlugin.java:29-41`, `server/core/plugin/registry/AssetRegistry.java:18`) ; le chemin `<pack>/Server/<path>` (`assetstore/AssetStore.java:755`) ; la lecture par `assetstore.AssetRegistry.getAssetStore(T.class).getAssetMap().getAssetMap()` (`AssetRegistry.java:34`, `DefaultAssetMap.java:199`) ; `Item.getCategories()` hérité (`Item.java:104-107`), `isConsumable()` hérité (`Item.java:378-381`, `1005`), `isVariant()` (`Item.java:386-389`, `1009`), `getQualityIndex()` et `ItemQuality.getAssetMap().getAsset(int).getId()` (`Item.java:1049`, `1252-1258`) ; `Quality` lu par `append` et non `appendInherited` (`Item.java:159`) : à vérifier en jeu qu'une variante hérite de la qualité de son parent.

- [ ] **Step 14 : build**

Run : `./gradlew build`
Expected : BUILD SUCCESSFUL (`checkFoodTooltips` passe).

- [ ] **Step 15 : commit**

```bash
git status --short
git add plugin/src/main/java/dev/hycolony/plugin/food/FoodValueAsset.java plugin/src/main/java/dev/hycolony/plugin/food/FoodTable.java plugin/src/main/java/dev/hycolony/plugin/food/HytaleFoods.java plugin/src/main/java/dev/hycolony/plugin/food/FoodIds.java plugin/src/main/java/dev/hycolony/plugin/IdMap.java plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java plugin/src/main/java/dev/hycolony/plugin/command/FoodSelfTest.java plugin/src/main/java/dev/hycolony/plugin/command/HyColonyCommand.java plugin/src/main/resources/hycolony/id-map.json plugin/src/main/resources/Server/HyColony/Foods plugin/src/main/resources/Server/Languages/en-US/hycolony_food.lang plugin/src/main/resources/Server/Languages/fr-FR/hycolony_food.lang plugin/build.gradle.kts tools/food/generate.py docs/research/plugin-b-api.md
git commit -m "feat(plugin): foods are HyColony asset files any mod may add to, and Hytale's other foods count by quality"
```

`plugin/src/main/resources/Server/Languages/*/hycolony_food.lang` : n'indexer que ces deux fichiers, pas `hycolony.lang` (une autre session le modifie).

---

### Task 5 : les tags de métier, type d'asset de HyColony (plugin)

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/crafting/JobTagAsset.java`
- Create : `plugin/src/main/java/dev/hycolony/plugin/crafting/HytaleJobTags.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/WorldPorts.java:56`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java` (enregistrement)
- Modify : `plugin/src/main/java/dev/hycolony/plugin/command/RecipesSelfTest.java` (une ligne)
- Modify : `plugin/src/main/resources/hycolony/crafting.json` (anciennes listes retirées)

**Interfaces :**
- Consumes : `JobTags`, `JobTags.TagFile`, `JobTags.merge`, `CraftingRules.withTags` (Task 3) ; `ResourceTypeIndex.load()`, `ResourceTypeIndex.items(String)` (`plugin/crafting`).
- Produces : `JobTagAsset.register(...)`, `public static Map<String, JobTagAsset> JobTagAsset.all()`, `public static JobTags HytaleJobTags.load()`.

- [ ] **Step 1 : `JobTagAsset`**

```java
package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One {@code Server/HyColony/JobTags/<name>.json} file (spec 2026-10-04 § 6.1): items added to one of MC's job tags
 * ({@link dev.hycolony.core.crafting.recipe.JobTags}). Every pack may add files; the files of a tag add up, the file
 * name only keeps them apart (a pack replaces one of ours by reusing its name).
 */
public final class JobTagAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, JobTagAsset>> {
    /** Where the files live under each pack's {@code Server/}. */
    static final String PATH = "HyColony/JobTags";

    static final AssetBuilderCodec<String, JobTagAsset> CODEC = AssetBuilderCodec.builder(
                    JobTagAsset.class,
                    JobTagAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, data) -> a.data = data,
                    a -> a.data)
            .append(new KeyedCodec<>("Tag", Codec.STRING), (a, v) -> a.tag = v, a -> a.tag)
            .add()
            .append(new KeyedCodec<>("Values", Codec.STRING_ARRAY), (a, v) -> a.values = v, a -> a.values)
            .add()
            .build();

    private AssetExtraInfo.@Nullable Data data;
    private String id = "";
    private String tag = "";
    private String[] values = new String[0];

    private JobTagAsset() {}

    /** Registers the type, loaded after the items it names (as Hytale's ShopPlugin.setup). Call once, in setup(). */
    public static void register(com.hypixel.hytale.server.core.plugin.registry.AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(JobTagAsset.class, new DefaultAssetMap<String, JobTagAsset>())
                .setPath(PATH)
                .setCodec(CODEC)
                .setKeyFunction(JobTagAsset::getId)
                .loadsAfter(Item.class)
                .build());
    }

    /** Every job tag file read, by file name; empty before the assets load or without the type registered. */
    public static Map<String, JobTagAsset> all() {
        AssetStore<String, JobTagAsset, DefaultAssetMap<String, JobTagAsset>> store =
                AssetRegistry.getAssetStore(JobTagAsset.class);
        return store == null ? Map.of() : store.getAssetMap().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** The tag the file adds to; empty when the file has none (skipped as unknown). */
    String tag() {
        return tag;
    }

    /** Its values: item ids, or {@code res:<resource type>}. */
    List<String> values() {
        return List.of(values);
    }
}
```

`Codec.STRING_ARRAY` : `codec/Codec.java:118`. Si `values` peut être `null` après lecture d'un fichier sans `Values`, `List.of(values)` lève : protéger par `values == null ? List.of() : List.of(values)` si NullAway l'accepte, sinon garder le défaut `new String[0]` (le setter n'est pas appelé pour une clé absente).

- [ ] **Step 2 : `HytaleJobTags`**

```java
package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.crafting.recipe.JobTags;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * The job tags of every {@code Server/HyColony/JobTags} file ({@link JobTagAsset}) for the core's {@link JobTags}:
 * each value is an item id, or {@code res:<type>} for every item of that resource type (as recipe ingredients). An
 * unknown value is skipped, all logged in one WARNING. Deviation from MC (Hytale world): MC reads Minecraft item tags →
 * HyColony reads its own asset files. Read once the assets are loaded; never throws (a failure answers no tag, SEVERE).
 */
public final class HytaleJobTags {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The prefix of a resource type value, as {@code crafting.json} and the recipe ingredients write it. */
    static final String RESOURCE_PREFIX = "res:";

    private HytaleJobTags() {}

    /** Every tag file merged by tag, in file name order. */
    public static JobTags load() {
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            List<String> unknown = new ArrayList<>();
            List<JobTags.TagFile> files = JobTagAsset.all().values().stream()
                    .sorted(Comparator.comparing(JobTagAsset::getId))
                    .map(a -> new JobTags.TagFile(a.tag(), items(a, resources, unknown)))
                    .toList();
            if (!unknown.isEmpty()) {
                LOG.at(Level.WARNING).log("HyColony job tags: unknown values skipped: %s", unknown);
            }
            return JobTags.merge(files, w -> LOG.at(Level.WARNING).log("HyColony %s", w));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony job tags could not be read; none apply");
            return JobTags.EMPTY;
        }
    }

    /** The items of one file's values; each unknown value is added to {@code unknown} as {@code file: value}. */
    private static List<ItemKey> items(JobTagAsset file, ResourceTypeIndex resources, List<String> unknown) {
        List<ItemKey> out = new ArrayList<>();
        for (String value : file.values()) {
            List<ItemKey> found = value.startsWith(RESOURCE_PREFIX)
                    ? resources.items(value.substring(RESOURCE_PREFIX.length()))
                    : Item.getAssetMap().getAsset(value) == null ? List.of() : List.of(new ItemKey(value));
            if (found.isEmpty()) {
                unknown.add(file.getId() + ": " + value);
            }
            out.addAll(found);
        }
        return out;
    }
}
```

Si Error Prone refuse le ternaire imbriqué, le découper en une méthode `itemsOf(String value, ResourceTypeIndex resources)`.

- [ ] **Step 3 : brancher les tags**

`WorldPorts.java:56` :

```java
                new CraftingSetup(HytaleRecipeCatalog.load(), setup.craftingRules().withTags(HytaleJobTags.load())),
```

et le commentaire au-dessus devient « // Read here, before openStorage loads the colonies: a load drops every learnt recipe it does not know. The job tags are assets, loaded only now (crafting.json was read at setup). » (import `dev.hycolony.plugin.crafting.HytaleJobTags`).

`HyColonyPlugin.setup()`, sous `FoodValueAsset.register(getAssetRegistry());` : `JobTagAsset.register(getAssetRegistry());`, et le commentaire cite « § 5 and § 6 ».

`RecipesSelfTest.run`, à la fin :

```java
        report.line("job tag files (" + JobTagAsset.all().size() + ")", true, "");
```

- [ ] **Step 4 : `crafting.json`**

Retirer les lignes `"includeItems": [],` et `"excludeItems": [],` du fermier, et la section `"reduceable": {…}` avec la virgule qui la précède. Le fichier reste du JSON valide (vérifier : `node -e "require('./plugin/src/main/resources/hycolony/crafting.json')"`).

- [ ] **Step 5 : build et commit**

```bash
./gradlew build
git status --short
git add plugin/src/main/java/dev/hycolony/plugin/crafting/JobTagAsset.java plugin/src/main/java/dev/hycolony/plugin/crafting/HytaleJobTags.java plugin/src/main/java/dev/hycolony/plugin/WorldPorts.java plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java plugin/src/main/java/dev/hycolony/plugin/command/RecipesSelfTest.java plugin/src/main/resources/hycolony/crafting.json
git commit -m "feat(plugin): MC's job tags are HyColony asset files any mod may add to"
```

---

### Task 6 : les postes de cuisson, tout banc qui cuit un aliment (plugin)

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/food/CookingBenches.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/food/HytaleFoods.java` (la cuisson passe à `CookingBenches`)
- Modify : `plugin/src/main/java/dev/hycolony/plugin/food/HytaleCookingCatalog.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/food/FoodIds.java` (sans `cookingBench`)
- Modify : `plugin/src/main/resources/hycolony/id-map.json` (sans `cookingBench`)
- Modify : `tools/food/generate.py`, `plugin/build.gradle.kts` (`checkFoodTooltips`)
- Regenerate : `plugin/src/main/resources/Server/Languages/{en-US,fr-FR}/hycolony_food.lang`
- Modify : `docs/research/plugin-b-api.md` (section 53)

**Interfaces :**
- Consumes : `HytaleFoods.food` (Task 4), `ResourceTypeIndex`.
- Produces : `record CookingBenches(Set<String> benches, Map<ItemKey, ItemKey> cooked, List<ItemKey> fuels)` avec `static CookingBenches load(Predicate<ItemKey> isFood)` et `NONE` ; `HytaleFoods.benches()` (paquet).

- [ ] **Step 1 : `CookingBenches`**

```java
package dev.hycolony.plugin.food;

import static java.util.stream.Collectors.toCollection;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.crafting.ResourceTypeIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Hytale's cooking stations (spec 2026-10-04 § 7, MC every furnace): the processing benches with a recipe whose
 * primary output is a food, what each input of those recipes cooks into, and what their fuel slots burn. Read once the
 * assets are loaded; never throws (a failure answers no station, logged SEVERE).
 *
 * @param benches the ids of the cooking benches
 * @param cooked each recipe input -> its recipe's primary output; the first recipe by bench then recipe id wins
 * @param fuels the items of every resource type a cooking bench's fuel slot takes, without repeats
 */
record CookingBenches(Set<String> benches, Map<ItemKey, ItemKey> cooked, List<ItemKey> fuels) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** No cooking station. */
    static final CookingBenches NONE = new CookingBenches(Set.of(), Map.of(), List.of());

    CookingBenches {
        benches = Set.copyOf(benches);
        cooked = Map.copyOf(cooked);
        fuels = List.copyOf(fuels);
    }

    /** One processing recipe making a food at one bench. */
    private record Cooking(String bench, CraftingRecipe recipe, ItemKey output) {}

    /** Reads the recipes and block types; {@code isFood} tells a food. */
    static CookingBenches load(Predicate<ItemKey> isFood) {
        try {
            ResourceTypeIndex resources = ResourceTypeIndex.load();
            List<Cooking> cookings = cookings(isFood);
            Set<String> benches = cookings.stream().map(Cooking::bench).collect(toCollection(TreeSet::new));
            Map<ItemKey, ItemKey> cooked = new HashMap<>();
            for (Cooking c : cookings) {
                addInputs(c, resources, cooked);
            }
            LOG.at(Level.INFO).log("Cooking: %d items cook at %s", cooked.size(), benches);
            return new CookingBenches(benches, cooked, fuels(benches, resources));
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("Cooking benches could not be read; nothing cooks");
            return NONE;
        }
    }

    /** Every processing recipe whose primary output is a food, by bench then recipe id. */
    private static List<Cooking> cookings(Predicate<ItemKey> isFood) {
        List<Cooking> out = new ArrayList<>();
        for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            MaterialQuantity primary = r == null ? null : r.getPrimaryOutput();
            BenchRequirement[] required = r == null ? null : r.getBenchRequirement();
            if (primary == null || primary.getItemId() == null || required == null) {
                continue;
            }
            ItemKey output = new ItemKey(primary.getItemId());
            for (BenchRequirement b : required) {
                if (b != null && b.type == BenchType.Processing && b.id != null && isFood.test(output)) {
                    out.add(new Cooking(b.id, r, output));
                }
            }
        }
        out.sort(Comparator.comparing(Cooking::bench).thenComparing(c -> c.recipe().getId()));
        return out;
    }

    /** Maps each input of the cooking {@code c}, an item or each item of a resource type, to its output. */
    private static void addInputs(Cooking c, ResourceTypeIndex resources, Map<ItemKey, ItemKey> out) {
        MaterialQuantity[] inputs = c.recipe().getInput();
        if (inputs == null) {
            return;
        }
        for (MaterialQuantity in : inputs) {
            for (ItemKey item : items(in, resources)) {
                out.putIfAbsent(item, c.output());
            }
        }
    }

    private static List<ItemKey> items(@Nullable MaterialQuantity in, ResourceTypeIndex resources) {
        if (in == null) {
            return List.of();
        }
        if (in.getItemId() != null) {
            return List.of(new ItemKey(in.getItemId()));
        }
        return in.getResourceTypeId() == null ? List.of() : resources.items(in.getResourceTypeId());
    }

    /** The items of each fuel slot's resource type of every block whose processing bench is one of {@code benches}. */
    private static List<ItemKey> fuels(Set<String> benches, ResourceTypeIndex resources) {
        Set<String> types = new TreeSet<>();
        for (BlockType type : BlockType.getAssetMap().getAssetMap().values()) {
            if (type != null
                    && type.getBench() instanceof ProcessingBench p
                    && benches.contains(p.getId())
                    && p.getFuel() != null) {
                for (ProcessingBench.ProcessingSlot slot : p.getFuel()) {
                    if (slot != null && slot.getResourceTypeId() != null) {
                        types.add(slot.getResourceTypeId());
                    }
                }
            }
        }
        return types.stream().flatMap(t -> resources.items(t).stream()).distinct().toList();
    }
}
```

Vérifier dans le décompilé avant d'écrire : `CraftingRecipe.getInput()` (type de retour, `MaterialQuantity[]` ou `List`, comme `HytaleFoods.addCooking` l'appelle aujourd'hui), `ProcessingBench.getFuel()` (`ProcessingBench.java:119`) et `ProcessingSlot.getResourceTypeId()` (`ProcessingBench.java:278`). Si PMD refuse le `continue`, inverser la condition.

- [ ] **Step 2 : `HytaleFoods` délègue la cuisson**

Retirer `cooked`, `cookedMap`, `loadCooked`, `addCooking`, `cooksAt` et `inputs` ; ajouter :

```java
    private @Nullable CookingBenches benches;

    /** The cooking stations, read on first use, once the assets are loaded ({@link CookingBenches}). */
    CookingBenches benches() {
        CookingBenches b = benches;
        if (b == null) {
            b = CookingBenches.load(item -> table().containsKey(item));
            benches = b;
        }
        return b;
    }

    /** What a cooking station makes of {@code item}; empty when it does not cook. */
    @Override
    public Optional<ItemKey> cooked(ItemKey item) {
        return Optional.ofNullable(benches().cooked().get(item));
    }

    /** The item that cooks into {@code dish}, the first by id when several do; empty when none does. */
    public Optional<ItemKey> rawFor(ItemKey dish) {
        return benches().cooked().entrySet().stream()
                .filter(e -> e.getValue().equals(dish))
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(ItemKey::id));
    }
```

La Javadoc de classe dit « what the cooking stations' recipes turn each item into ({@link CookingBenches}) ». Retirer les imports devenus inutiles (`BenchRequirement`, `BenchType`, `CraftingRecipe`, `MaterialQuantity`, `ResourceTypeIndex`, `HashMap`, `Level` si plus utilisé).

- [ ] **Step 3 : `HytaleCookingCatalog`**

`isStation` :

```java
    /** A block whose bench is a processing bench that cooks a food ({@link CookingBenches}). */
    @Override
    public boolean isStation(BlockKey block) {
        return stations.computeIfAbsent(block, k -> {
            try {
                BlockType type = BlockType.getAssetMap().getAsset(k.id());
                Bench bench = type == null ? null : type.getBench();
                return bench != null
                        && bench.getType() == BenchType.Processing
                        && foods.benches().benches().contains(bench.getId());
            } catch (RuntimeException e) {
                fail(e);
                return false;
            }
        });
    }

    /** What every cooking station's fuel slot burns. */
    @Override
    public List<ItemKey> fuels() {
        return foods.benches().fuels();
    }
```

Retirer `FUEL_RESOURCE_TYPE`, le champ `fuels` et l'import `ResourceTypeIndex`. La Javadoc de classe : « CookingCatalog over Hytale's assets: a station is a block whose bench cooks a food, the fuels are what the stations' fuel slots burn ({@link CookingBenches}); the defaults are the id-map's; a dish's raw item comes from the cooking recipes ({@link HytaleFoods}). Read on first use, cached; never throws (a failure answers no station, logged once as a WARNING). » Le champ `ids` reste (`defaultFuels`).

- [ ] **Step 4 : `cookingBench` quitte l'id-map**

- `id-map.json` : retirer la ligne `"cookingBench": "Campfire",`.
- `FoodIds` : retirer la composante `cookingBench`, sa ligne `@param`, la méthode `bench()`, et la mention « the bench that cooks raw food (MC's furnace) » de la Javadoc ; `NONE` passe à 4 `null`.
- `grep -rn "\.bench()\|cookingBench" plugin/src/main/java` : plus aucun usage de `FoodIds.bench()`.

- [ ] **Step 5 : le générateur suit la même règle**

Dans `tools/food/generate.py`, remplacer `cooks_at` par :

```python
    def cooking_inputs(self, foods):
        """Every input of a processing recipe whose primary output is a food (an item, or each item of a resource
        type), like the plugin's CookingBenches: what citizens will not eat raw (MC FoodUtils.EDIBLE). Recipes do not
        inherit; a recipe without PrimaryOutput makes its own item."""
        inputs = set()
        for item_id in self.paths:
            recipe = self.own(item_id).get("Recipe") or {}
            output = (recipe.get("PrimaryOutput") or {}).get("ItemId", item_id)
            benches = recipe.get("BenchRequirement") or []
            if output in foods and any(b.get("Type") == "Processing" for b in benches):
                for i in recipe.get("Input") or []:
                    if "ItemId" in i:
                        inputs.add(i["ItemId"])
                    elif "ResourceTypeId" in i:
                        inputs.update(self.of_resource_type(i["ResourceTypeId"]))
        return inputs
```

Dans `main` : retirer `food_ids` et `ID_MAP` (constante comprise), `raw = assets.cooking_inputs(set(foods))`, et la ligne `f"# cookingBench={food_ids['cookingBench']}"` disparaît de `out` (seule reste la ligne « # Generated … »).

- [ ] **Step 6 : `checkFoodTooltips` sans banc**

Dans `plugin/build.gradle.kts` : retirer `inputs.file(resources.resolve("hycolony/id-map.json"))`, les deux lignes `idMap` / `food`, et `listOf("# cookingBench=${food["cookingBench"]}") +` ; le commentaire « the bench decides which foods are raw » devient « each food's header, then its description line ».

- [ ] **Step 7 : régénérer et vérifier**

Run : `python tools/food/generate.py`
Expected : `50 foods, N raw`, le même N qu'à la tâche 4 ; `git diff` des `.lang` ne montre que la ligne `# cookingBench=Campfire` retirée (aucune description ne change : les aliments « trop crus » sont les mêmes).

Run : `./gradlew build`
Expected : BUILD SUCCESSFUL.

- [ ] **Step 8 : noter l'API**

Compléter la section 53 de `docs/research/plugin-b-api.md` : `ProcessingBench.getFuel()` et `ProcessingSlot.getResourceTypeId()` (`server/core/asset/type/blocktype/config/bench/ProcessingBench.java:119`, `:278`), le four `Processing` sans recette d'aliment.

- [ ] **Step 9 : commit**

```bash
git status --short
git add plugin/src/main/java/dev/hycolony/plugin/food/CookingBenches.java plugin/src/main/java/dev/hycolony/plugin/food/HytaleFoods.java plugin/src/main/java/dev/hycolony/plugin/food/HytaleCookingCatalog.java plugin/src/main/java/dev/hycolony/plugin/food/FoodIds.java plugin/src/main/resources/hycolony/id-map.json tools/food/generate.py plugin/build.gradle.kts plugin/src/main/resources/Server/Languages/en-US/hycolony_food.lang plugin/src/main/resources/Server/Languages/fr-FR/hycolony_food.lang docs/research/plugin-b-api.md
git commit -m "feat(plugin): every processing bench that cooks a food is a cooking station, with its own fuels"
```

---

### Task 7 : vérifications en jeu, relectures

**Files :**
- Modify : `docs/TESTING.md` (nouvelle section, points 382 à 386)

- [ ] **Step 1 : les points de test**

À la fin de `docs/TESTING.md` :

```markdown
## Nourriture ouverte aux mods (2026-10-04)

Spec `2026-10-04-hycolony-nourriture-ouverte-design.md`. Rien ne doit changer en jeu : ces points vérifient que les aliments, les tags et les postes de cuisson se lisent dans leurs nouveaux fichiers.

382. **Selftest.** `/hycolony selftest` : « food files (50 of 50) » en vert, « foods by quality (0) », « job tag files (0) ». Au démarrage, le journal dit « Foods: 50, 50 of them from a food file » et « Cooking: … items cook at [Campfire] », sans WARNING « HyColony food files of unknown items ».
383. **Repas et infobulles.** Un citoyen affamé mange du pain : sa barre de faim monte comme avant (12, soit 2 gigots). L'infobulle du pain et celle du poulet cru (« trop cru ») sont celles d'avant.
384. **Salle à manger.** L'onglet Menu, « Plats possibles », montre les mêmes aliments qu'avant, aucune variante (`Food_Fish_Raw_Rare`…). Le serveur cuit toujours au feu de camp (point 298). Un four posé dans la salle n'est pas pris pour un feu de camp ; la liste des combustibles de la salle est celle d'avant.
385. **Fermier.** Le fermier apprend et fabrique toujours ses graines (onglet Recettes) : `crafting.json` n'a plus de listes de tags, rien ne change pour lui.
386. **Aliment sans fichier.** Serveur arrêté, déplacer `plugin/src/main/resources/Server/HyColony/Foods/Food_Bread.json` hors du dépôt (en dev, les assets sont lus dans `src/main/resources`), relancer : le selftest dit « food files (49 of 49) » et « foods by quality (1) » ; un citoyen affamé qui mange du pain gagne 16 (la valeur Uncommon : 8, doublée pour un plat) au lieu de 12. Remettre le fichier à sa place avant de relancer.
```

- [ ] **Step 2 : build complet**

Run : `./gradlew build`
Expected : BUILD SUCCESSFUL.

- [ ] **Step 3 : relectures indépendantes (CLAUDE.md § 9.3)**

Lancer en parallèle, sur la plage de commits du plan :
- `hycolony-reviewer` : correction, tests, taille et responsabilité des classes, ports qui ne lèvent pas, règles de jeu hors du plugin (`FoodQuality`, `JobTags` dans le cœur) ;
- `mc-fidelity-checker` : `JobTags` et `CraftingRules.allows` contre MC `CraftingUtils.getProductValidatorBasedOnTags` et les tags `data/minecolonies/tags/items`, marquages `Deviation from MC (Hytale world)` ;
- `ui-lang-checker` : `hycolony_food.lang` (parité en-US / fr-FR) et l'exception du § 7 (Task 1).

Corriger chaque constat (même mineur), puis faire relire les corrections.

- [ ] **Step 4 : commit**

```bash
git add docs/TESTING.md
git commit -m "docs: in-game checks for foods, job tags and cooking stations read from asset files"
```

Puis donner le feu vert à l'utilisateur pour tester en jeu (points 382 à 386) ; ne jamais lancer le serveur soi-même.
