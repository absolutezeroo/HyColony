# Audit global HyColony : 00, inventaire brut

```
ÉTAT : phase 0 terminée. Commit audité : 2f9a11eb (branche sp0-foundations), arbre propre hormis
PROMPT_AUDIT_HYTALE_MINECOLONIES.md (non suivi). Date : 2026-09-29.
Fichiers lus : CLAUDE.md, AGENTS.md, README.md, settings.gradle.kts, gradle.properties, build.gradle.kts racine,
build-logic/src/main/kotlin/*.gradle.kts, gradle/libs.versions.toml, gradle-wrapper.properties, les 4 manifest.json,
les 3 listes d'exceptions, config/pmd/ruleset.xml, .github/workflows/gradle.yml, docs/BACKLOG.md, docs/TESTING.md
(plan), docs/research/update-7/README.md, docs/research/architecture/{audit-A-core,audit-B-requests-plugin,
audit-duplication-couplage}.md, plugin/HyColonyPlugin.java, WorldRuntime.java, WorldRuntimes.java,
ColonyTickSystem.java, HyBlockUIPlugin.java, HyDomumPlugin.java, HyVanillaPlugin.java, core ArchitectureTest.java et
FeatureDependenciesTest.java.
Audit terminé : 01-architecture.md, 02-findings-A..M et completude, 03-verification.md, AUDIT.md (rapport final).
Script des métriques : scratchpad/metrics.sh (Git Bash, LC_ALL=C.UTF-8) ; sortie brute : scratchpad/metrics.txt.
```

## 1. Faits du build

| Fait                            | Valeur                                                                                                                            | Source                                                              |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| Gradle                          | 9.5.1 (wrapper)                                                                                                                   | `gradle/wrapper/gradle-wrapper.properties`                          |
| Toolchain                       | Java 25 par foojay (`java_version = 25`)                                                                                          | `gradle.properties:15`, `build-logic/.../hy.java-checks.gradle.kts` |
| Hytale                          | `0.7.0-pre.4`, patchline `pre-release`, `ServerVersion >=0.7.0-pre.4 <0.8.0`                                                      | `gradle.properties:18,62,65,68`                                     |
| Bibliothèques                   | gson 2.11.0, jspecify 1.0.1, junit 6.1.3, archunit 1.5.1                                                                          | `gradle/libs.versions.toml`                                         |
| Outils du build                 | Error Prone 2.50.0 + NullAway 0.14.2 (JSpecify, ERROR), PMD 7.28.0, palantir-java-format 2.99.0, ArchUnit                         | `hy.java-checks.gradle.kts`                                         |
| `--enable-preview`              | absent (cmd 14 : aucun hit)                                                                                                       | build scripts                                                       |
| Contrôles maison                | `checkFileSizes` (400 l. / 15 fichiers), `checkSectionDividers`, `checkModApis`, `checkPmdBaseline` (liste PMD forcée à rétrécir) | `hy.java-checks.gradle.kts`                                         |
| CI                              | `.github/workflows/gradle.yml` : `./gradlew build` sur chaque push, JDK 25 temurin                                                | —                                                                   |
| `./gradlew build --offline`     | **VERT** (10 s, 93 tâches, 25 exécutées)                                                                                          | `scratchpad/build.log`                                              |
| `./gradlew compileJava --rerun` | VERT, **16 avertissements** (voir § 7)                                                                                            | `scratchpad/compile.log`                                            |
| Tests                           | core : 1 175 tests (159 classes), domum/core + vanilla/core : 73 ; 1 224 `@Test` au total                                         | `*/build/test-results/test/*.xml`                                   |
| PMD (rapports du build)         | core : 1 violation tolérée ; plugin : 18 ; les 5 autres projets : 0                                                               | `*/build/reports/pmd/main.xml`                                      |
| Historique                      | 745 commits depuis le 2026-09-25 (5 jours)                                                                                        | `git log`                                                           |

### Manifests

| Mod       | `Main`                                 | `Dependencies`                                                                                                              | `IncludesAssetPack`                                                                                                                    |
|-----------|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| hycolony  | `dev.hycolony.plugin.HyColonyPlugin`   | `Hytale:AssetModule=*`, `Hytale:NPC=*`, `HyColony:hyblockui==0.1.0`, `HyColony:hydomum==0.1.0`, `HyColony:hyvanilla==0.1.0` | true (`Server/{Entity,Item,Languages,NPC,Prefabs}`, `Common/{BlockTextures,Icons,Items,UI}`, `hycolony/{id-map,styles,crafting}.json`) |
| hydomum   | `dev.hydomum.plugin.HyDomumPlugin`     | `Hytale:AssetModule=*`, `HyColony:hyblockui==0.1.0`                                                                         | true (398 fichiers, 117 JSON)                                                                                                          |
| hyvanilla | `dev.hyvanilla.plugin.HyVanillaPlugin` | `Hytale:AssetModule=*`                                                                                                      | true (2 112 fichiers, 79 JSON : tapis, pots)                                                                                           |
| hyblockui | `dev.hyblockui.HyBlockUIPlugin`        | `Hytale:AssetModule=*`                                                                                                      | true (22 fichiers)                                                                                                                     |

Aucun manifest ne déclare `Hytale:EntityModule` ni `Hytale:BlockModule` (règle [COMMUNAUTÉ] du prompt) ; les mods tournent en jeu (`docs/TESTING.md`). Les manifests sont générés par `hytale-tools` depuis `gradle.properties` et le `build.gradle.kts` de chaque mod (`hy.hytale-mod.gradle.kts:15-27`).

### Listes d'exceptions (dettes connues)

- `gradle/file-size-allowlist.txt` : **vide**.
- `gradle/package-size-allowlist.txt` : **vide**.
- `config/pmd/known-violations.txt` : 16 paires, toutes dans `plugin/` sauf une :
  - `CouplingBetweenObjects core/.../request/RequestManager.java` (jugée légitime par l'audit B § 1.1) ;
  - `HytaleItemCatalog` : CognitiveComplexity, CouplingBetweenObjects, CyclomaticComplexity, GodClass, NPathComplexity ;
  - `HytaleCitizenBodies` : CouplingBetweenObjects, CyclomaticComplexity, ExcessiveImports, GodClass ;
  - `HytaleUiPort` : CouplingBetweenObjects, ExcessiveImports ;
  - `HyColonyCommand` : CouplingBetweenObjects, ExcessiveImports ;
  - `WorldRuntime` : ExcessiveImports ;
  - `HytalePlayerInventory` : CompareObjectsWithEquals.

## 2. Taille

| Projet                         | Fichiers | Lignes     | Tests                       |
|--------------------------------|----------|------------|-----------------------------|
| `core/src/main/java`           | 345      | 26 137     | 187 fichiers, 27 145 lignes |
| `plugin/src/main/java`         | 124      | 12 051     | — (selftest en jeu)         |
| `domum/core/src/main/java`     | 13       | 942        | 8 fichiers, 713 lignes      |
| `domum/plugin/src/main/java`   | 30       | 2 772      | —                           |
| `vanilla/core/src/main/java`   | 2        | 97         | 3 fichiers, 129 lignes      |
| `vanilla/plugin/src/main/java` | 5        | 262        | —                           |
| `blockui/src/main/java`        | 15       | 712        | —                           |
| **Total production**           | **534**  | **42 973** | 1 224 `@Test`               |

Simulations de ports (`core/src/test/.../testing`) : FakeBlueprints, FakeBodies, FakeCatalog, FakeClock, FakeContainers, FakeNotifier, FakePlayerInventory, FakePlayers, FakePreviews, FakeUi, FakeWorld, FakeWorldBlocks, FakeWorldEffects, FakeRecipeCatalog, FakeFarming, FakeResolver ; plus `TestContexts`, `TestJobs`.

Assets : 41 fichiers `.ui` (1 681 lignes ; les plus gros `WandPage.ui` 225, `Building.ui` 219, `Citizen.ui` 128, `TownHall.ui` 127) ; `hycolony/id-map.json` (42 clés : items, blocks, npc, effets, farming…) ; `hycolony/styles.json` ; `hycolony/crafting.json` ; 5 paires de `.lang` en-US/fr-FR (341 + 45 + 72 + 56 + 1 clés).

## 3. Les 30 plus grosses classes (cmd 1-3, 11, 12)

Colonnes : LOC physiques, méthodes (estimation shell, bruitée), imports, branches (estimation shell), indentation max (espaces). « Rôle apparent » = première ligne de Javadoc trouvée, **non vérifié** avant lecture.

| Fichier                                                     | LOC | Méth. | Imp. | Branches | Indent | Rôle apparent (non vérifié)                                        |
|-------------------------------------------------------------|-----|-------|------|----------|--------|--------------------------------------------------------------------|
| `core/.../crafting/job/CraftingWork.java`                   | 366 | 50    | 15   | 53       | 24     | A crafter's work (MC AbstractEntityAICrafting)                     |
| `plugin/.../adapter/HytaleCitizenBodies.java`               | 354 | 44    | 43   | 45       | 28     | CitizenBodies adapter (spawn, nav, gestes)                         |
| `plugin/.../adapter/HytaleItemCatalog.java`                 | 342 | 42    | 25   | 52       | 24     | ItemCatalog over the Hytale asset maps                             |
| `plugin/.../command/HyColonyCommand.java`                   | 329 | 21    | 39   | 28       | 32     | /hycolony et sous-commandes                                        |
| `core/.../construction/workorder/WorkManager.java`          | 324 | 48    | 19   | 51       | 32     | A colony's work orders (MC WorkManager)                            |
| `core/.../app/action/HutActions.java`                       | 279 | 40    | 20   | 41       | 24     | What players do to huts (MC AbstractBlockHut)                      |
| `core/.../crafting/module/CraftingModule.java`              | 272 | 38    | 20   | 28       | 32     | A hut's crafting module (MC AbstractCraftingBuildingModule)        |
| `core/.../app/wand/WandActions.java`                        | 271 | 32    | 12   | 21       | 24     | The build tool buttons (Structurize)                               |
| `core/.../job/work/WorkerStock.java`                        | 263 | 35    | 22   | 37       | 20     | A worker's items (MC AbstractEntityAIBasic dumpInventory…)         |
| `core/.../construction/blueprint/StructurePlan.java`        | 260 | 28    | 15   | 27       | 28     | Precomputed, immutable work lists for one work order               |
| `core/.../crafting/request/CraftingProductionResolver.java` | 259 | 35    | 25   | 32       | 20     | MC AbstractCraftingProductionResolver                              |
| `core/.../construction/builder/BuilderBlockWork.java`       | 254 | 31    | 16   | 35       | 32     | Works the block the structure step chose (MC doMining + mineBlock) |
| `core/.../construction/builder/BuilderAI.java`              | 248 | 34    | 12   | 29       | 16     | MC AbstractEntityAIStructure…                                      |
| `plugin/.../adapter/HytaleUiPort.java`                      | 246 | 30    | 41   | 15       | 28     | Renders core view models with Hytale custom pages                  |
| `core/.../app/persistence/ColonySerializer.java`            | 246 | 33    | 29   | 27       | 32     | The saved colony (§ 5)                                             |
| `core/.../citizen/CitizenAI.java`                           | 240 | 25    | 18   | 36       | 24     | MC CitizenAI.calculateNextState                                    |
| `core/.../request/RequestManager.java`                      | 239 | 36    | 12   | 14       | 20     | MC StandardRequestManager (façade)                                 |
| `plugin/.../subplugin/SubPlugins.java`                      | 237 | 28    | 20   | 19       | 32     | Optional sub-plugins bundled in the jar                            |
| `core/.../request/RequestSerializer.java`                   | 236 | 30    | 21   | 29       | 32     | RequestManager <-> JSON                                            |
| `core/.../colony/Colony.java`                               | 233 | 34    | 17   | 12       | 16     | L'agrégat colonie                                                  |
| `core/.../farming/job/FieldPass.java`                       | 232 | 27    | 15   | 34       | 28     | MC EntityAIWorkFarmer.workAtField                                  |
| `core/.../farming/job/FarmWork.java`                        | 230 | 37    | 14   | 43       | 20     | MC EntityAIWorkFarmer prepareForFarming                            |
| `plugin/.../adapter/HytaleWorldBlocks.java`                 | 229 | 26    | 25   | 25       | 20     | WorldBlocks over the section API                                   |
| `domum/plugin/.../cutter/CutterPage.java`                   | 228 | 17    | 29   | 20       | 40     | The architect's cutter window (DO ArchitectsCutterScreen)          |
| `core/.../citizen/CitizenManager.java`                      | 220 | 39    | 12   | 28       | 24     | (pas de Javadoc de classe trouvée par l'heuristique)               |
| `core/.../building/Building.java`                           | 220 | 33    | 23   | 7        | 16     | Le bâtiment et ses modules                                         |
| `core/.../logistics/warehouse/WarehouseStockResolver.java`  | 215 | 33    | 16   | 21       | 20     | MC AbstractWarehouseRequestResolver                                |
| `plugin/.../ui/hut/RecipesTab.java`                         | 213 | 24    | 12   | 22       | 28     | MC WindowListRecipes                                               |
| `plugin/.../ui/citizen/CitizenPage.java`                    | 211 | 29    | 18   | 36       | 16     | MC AbstractWindowCitizen                                           |
| `plugin/.../prefab/HytaleBlueprintSource.java`              | 209 | 19    | 22   | 18       | 60     | Blueprints read from the prefabs                                   |

Aucun fichier de production ne dépasse 400 lignes (`checkFileSizes` vert, liste vide) ; **aucun ne dépasse 366**.

## 4. Hits bruts

### Cmd 5, état statique mutable (2 hits)

- `plugin/.../ui/highlight/GlowingBlock.java:41` : `private static volatile Optional<String> effect = Optional.empty();` (assigné dans `HyColonyPlugin.setup:57` via `useEffect`).
- `blockui/.../api/UiSounds.java:18` : `private static boolean warned;` (drapeau « premier échec journalisé »).

Plus une map statique repérée par la cmd 10 : `plugin/.../ui/highlight/Highlights.java:27` : `private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();` (final mais mutable, portée serveur).

### Cmd 6, singletons : aucun. Cmd 9, sérialisation Java native : aucune.

### Cmd 10, concurrence (hits réels, après filtrage des `Optional.get()`)

| Fichier:ligne                                                   | Construction                                                                                                 |
|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `plugin/.../ColonyTickSystem.java:20`                           | `ConcurrentHashMap` des accumulateurs par monde                                                              |
| `plugin/.../WorldRuntimes.java:18,57`                           | `ConcurrentHashMap` par monde ; `CompletableFuture.runAsync(rt::saveAll, world).join()` dans `remove(World)` |
| `plugin/.../command/BodySelfTest.java:56-58`                    | `CompletableFuture.runAsync(...)` puis `future.get()`                                                        |
| `plugin/.../prefab/HytaleBlueprintSource.java:73-74,104`        | cache `ConcurrentHashMap`, `runAsync` (préchargement)                                                        |
| `plugin/.../ui/highlight/Highlights.java:27`                    | `static final ConcurrentHashMap`                                                                             |
| `domum/plugin/.../cutter/CutterCraftQueue.java:84`              | `CompletableFuture.delayedExecutor(...).execute(() -> world.execute(...))`                                   |
| `domum/plugin/.../cutter/CutterPreviewVariants.java:62`         | `delayedExecutor` puis `world.execute`                                                                       |
| `domum/plugin/.../cutter/CutterGroupMemory.java:15`             | `ConcurrentHashMap`                                                                                          |
| `domum/plugin/.../persistence/VariantStore.java:39,65`          | `synchronized load/add`                                                                                      |
| `domum/plugin/.../registry/OrnamentVariantRegistry.java:39-116` | `ConcurrentHashMap<VariantKey, CompletableFuture>`, `supplyAsync`, `allOf().thenApply(join)`                 |
| `domum/plugin/.../registry/VariantBuilder.java:29`              | `ConcurrentHashMap`                                                                                          |
| `domum/plugin/.../runtime/VariantAssets.java:46-49,160`         | 3 `ConcurrentHashMap`, `asset.getBlob()`                                                                     |
| `domum/plugin/.../runtime/Textures.java:26`                     | `asset.getBlob().join()`                                                                                     |

Aucun `new Thread`, `Executors.new*`, `ThreadLocal`, `Thread.sleep`, thread virtuel ni `ScopedValue`.

### Cmd 13, cycles directs entre paquets (heuristique : 2 nœuds, par `import`)

578 arêtes `dev.hy*`. Cycles à deux nœuds (26) :

- cœur, attendus par la matrice figée de `FeatureDependenciesTest` : `colony↔building`, `colony↔building.module`, `colony↔citizen`, `colony↔construction.workorder`, `colony↔crafting.module`, `colony↔job`, `job↔citizen` ;
- cœur, intra-fonctionnalité : `app↔app.action`, `app↔app.view`, `building↔building.module`, `farming.job↔farming.hut`, `request↔request.resolver` ;
- plugin : `plugin↔{adapter,block,command,goggles,npc,prefab,subplugin,ui.wand}`, `adapter↔{block,ui,ui.citizen}`, `ui↔{ui.hut,ui.logistics}`.

`ArchitectureTest` impose `beFreeOfCycles()` sur les sous-paquets de `construction` et de `crafting` seulement.

### Cmd 14, preview / API héritées / suppressions

- Aucun `enable-preview`, `SecurityManager`, `Unsafe`, `finalize`, `loadLibrary`, `native`.
- `@SuppressWarnings` (8) : `unchecked` ×3 (`HytaleCitizenBodies:103`, `HytaleBlockStates:99,104`), `deprecation` (`HyColonySection:15`), `PMD.CompareObjectsWithEquals` ×3 (`CitizenInventoryWindow:57`, `CitizenInventoryWindows:138`, `CitizenPage:190`, `HeldWindows:23` avec `ReferenceEquality`), `NullAway` (`CitizenItemContainer:101`).

### Cmd 15, idiomes

records 212 ; `sealed` 13 ; `case X(...)`/`case X x ->` 32 ; threads virtuels 0 ; `ScopedValue` 0 ; interfaces 54 ; classes abstraites 2 (`core/.../job/Job.java:12`, `plugin/.../ui/ColonyPage.java:32`).

`Optional` en **champ** : `core/.../farming/field/FarmField.java:19-20` (`owner`, `seed`), `core/.../farming/job/FarmWork.java:41` (`status`), `plugin/.../adapter/HytaleWorldEffects.java:43` (`tillSound`), `plugin/.../block/HytaleBlockStates.java:26-28` (tableaux d'`Optional`), record `WandSession.anchor` (`app/wand/WandSession.java:10`). `Optional` en **paramètre** : `FarmField.setOwner/setSeed:37,50`, `FieldWalk.setPrevPos:51`, `WorkerHands.hold:28`, `CitizenInventoryActions.placed:82`, `WandPlacement.locationRefusal:90`, `GlowingBlock.useEffect:46`, `FlowerPot.use:38`, `FlowerPotBlocks.block:40`.

`return null;` dans le cœur (25) : `CitizenAI` ×5, `BuilderAI` ×4, `BuilderBlockWork` ×7, `BuilderGathering` ×2, `StructureLoader`, `WorkSpot`, `AITarget`, `TickingTransition`, `SavedJson`, `ForcedInsert` (à vérifier : suppliers de transition « pas de changement d'état », motif MC `TickingTransition`).

### Cmd 16, ECS des plugins

Systèmes (19) : `ColonyTickSystem` (TickingSystem), `CitizenBodyLifecycleSystem` et `CitizenFireImmunitySystems.Grant` (RefSystem), `GogglesSystems.Visibility` (EntityTickingSystem), `ExplosionProtectionSystem` (WorldEventSystem), et 14 `EntityEventSystem` (`HutBlockSystems.{Place,Break,Use}`, `FieldBlockSystems.{Place,Break,Use}`, `ProtectionSystems.{Place,Break}`, `BlockUseProtectionSystem`, `GogglesSystems.ArmorChange`, `CitizenUseSystem`, `CutterSystem`, `FlowerPotSystem`, + `CitizenFireImmunitySystems.Guard`). `isParallel` : jamais surchargé. `getGroup/getDependencies/getHierarchyScope` : 10 mentions. Composants : `CitizenTag` (codec, id `HyColonyCitizen`) et `MoveTarget` (sans codec) ; 2 `registerComponent` (`HyColonyComponents.java:19-20`). Mutations directes du store : `HytalePreviewPort.java:111`, `GlowingBlock.java:89,98` (toutes après `world.execute`, à confirmer en phase 1). Registres : `getEntityStoreRegistry` ×?, `getEventRegistry`, `getCommandRegistry` (détail en phase 1).

### Cmd 17, API 0.7.0

Code Java : aucun symbole supprimé ou restreint (le build compile avec Error Prone). Hits non décisifs : `SoundUtil.playSoundEvent3d` (`HytaleWorldEffects.java:141,161`, modificateur à vérifier ≤ 1.0) et `playSoundEvent2dToPlayer` (`UiSounds.java:29`) ; `ByteBuffer` de `VariantAssets.java:144` est du CRC, pas un paquet réseau ; le commentaire `VariantBlockType.java:76` cite `WorldChunk.setBlockInteractionState`. Aucune borne 319/320. Aucun nom JSON renommé (`DurationMs`, `SmallerThan`…) dans les 4 packs ni dans `tools/`. Mentions de 0.6.8 hors `update-7/` : `tools/blueprint/check_placeholders.py:99`, `tools/domum/families.py:165,232,243`, `tools/domum/tags.py:27,33`, `docs/native-ui-textures.md:3`, et une vingtaine de pages de `docs/research/` (recherches datées, pas du code). Liste « À migrer » d'`update-7/README.md` : à pointer en phase 2 (axe compat).

### Cmd 18, fidélité MC

249 fichiers de production citent une source `MC X` ; 169 commentaires `Deviation from MC:`. Fichiers de cœur **sans** citation `MC …` (heuristique, 164) : surtout `kernel/port` (11), `request` (10), `kernel/item` (8), `colony` (8), `citizen` (8), `building/module` (8), `request/model` (7), `kernel/ai` (7), `kernel/persist` (6). Beaucoup sont des ports, records et infrastructure sans contrepartie MC directe : à trier en axe M.

### Cmd 19, parité des `.lang` : **aucune différence** de clés entre en-US et fr-FR sur les 5 fichiers.

### Cmd 20, robustesse et textes

- `throw new` dans les adaptateurs `Hytale*` : **aucun**.
- `catch (RuntimeException)` : 96 sites (adaptateurs, systèmes, pages) ; `catch (Exception)` : `HyColonyCommand.java:239` ; `catch (Exception | Error)` : `HytaleBlueprintSource.java:169` ; `catch (RuntimeException | LinkageError | AWTError)` : `VariantBuilder.java:52,80`.
- Boucles `while (true)` : aucune.
- `Message.raw(` : 7 sites (`ColonyPage.java:144,161`, `FieldsTab.java:92`, `RecipesTab.java:122,125,128`, `CutterDrawing.java:144`), tous des replis (id brut quand la traduction manque) ou des séparateurs `" - "`.
- Identifiants d'assets en dur (heuristique) : `RecipesSelfTest.java:9` (`Plant_Seeds_Wheat`), `FarmingIds.java:34,38` (`Soil_Dirt_Tilled`, `Tool_Fertilizer`, replis), `CitizenFireImmunitySystems.java:45` (`Immunity_Fire`), `PrefabCells.java:30` (`Block_Spawner_Block`), `WandInteraction.java:29` (`HyColony_Build_Tool`), `CutterSystem.java:22` (`HyDomum_ArchitectsCutter`). Déjà listés dans `docs/BACKLOG.md` (« Audit du code du 2026-09-29 »).
- `.Text` : 73 usages, `.TextSpans` : 26 (à croiser avec les traductions imbriquées en axe H).

### Cmd 21, visibilité par sous-paquet du cœur (public / total)

Tout public : `app/ui` 12/12, `building` 8/8, `building/module` 8/8, `citizen` 11/11, `colony/permission` 6/6, `construction/blueprint` 5/5, `construction/shared` 5/5, `farming/field` 6/6, `job` 10/10, `job/work` 6/6, `kernel/ai` 9/9, `kernel/item` 11/11, `kernel/persist` 6/6, `kernel/port` 15/15, `request/model` 11/11. Bien encapsulés : `app/view` 1/11, `app/wand` 1/8, `app/persistence` 1/4, `construction/builder` 4/15, `crafting/request` 1/8, `farming/job` 2/7, `logistics/courier` 3/13, `request` 7/15.

## 5. Ce que les audits précédents ont déjà établi (à ne pas redécouvrir)

- **Audit A (cœur, HEAD 71f9e7e)** : composition plutôt qu'héritage (1 seule classe abstraite `Job`, profondeur 1) ; `Building.module(Class)` ne rend qu'un module (à traiter avant école/défense) ; `ColonyContext` est un record, pas un service locator, mais 36 classes l'appellent ; le cycle par `Colony` est l'agrégat MC, à figer et non à casser ; 8 priorités dont plusieurs faites depuis (`WorkerStock` extrait dans `job/work`, `CoreFeatures.register`, `FeatureDependenciesTest`).
- **Audit B (requêtes, plugin, persistance)** : `RequestManager` est une façade (exception PMD légitime) ; lecture non tolérante d'un type de requête inconnu (corrigé depuis : fixture `colony-v3-unknown-request.json`) ; `HytaleItemCatalog`, `HyColonyCommand`, `HytaleUiPort` à découper (toujours dans `known-violations.txt`) ; onglets de huttes par module (fait : `decd4efe`).
- **Audit duplication/couplage** : CPD ≥ 50 tokens : 14 blocs dans core, 29 dans plugin, max 88 tokens ; `colony↔construction` seul cycle dense ; 44 fichiers préexistants modifiés pour ajouter le métier de courrier.
- **`docs/BACKLOG.md` « Audit du code du 2026-09-29 : reste à faire »** : `CLAUDE.md` § 1 à mettre à jour (`app/ui/UiPort`, fait le 2026-09-29), contrôles Python dans `pre-push`, ~55 lignes > 120 colonnes, `BuilderBlockWork.fetchTool` vs `ToolRequests.missing`, découpage des classes PMD du plugin, décision de jeu dans `HytaleUiPort` (retrait de la fondation), `RequestsPage`/`CitizenRequestsTab` choisissent la fenêtre à ré-afficher, ids d'assets hors id-map, règles du cutter dans `domum/plugin` sans test, outils Python.
- **`docs/research/update-7/README.md`** : 5 bloquants (faits, le build est vert sur pre.4) ; liste « À migrer (non bloquant) » : `HytaleItemCatalog.toolType` (Metals/GoblinMetal), `BenchTiers.set` → `notifyTierUpgraded`, `FarmBlocks.java:96` `markNeedsSaving`, `HytaleWorldQuery.isLoaded` par section, `tools/decorations/generate.py:21` et `tools/domum/tags.py:18`, `sp4-sleep-home.md:189`, Javadoc `InventoryDrop` citant 0.6.8.

## 6. Recensement des paquets (fichiers Java par paquet)

- **core** (`dev.hycolony.core`) : racine 2 (`CoreFeatures`, `FeaturePack`) ; `app` 6, `app/action` 9, `app/goggles` 2, `app/persistence` 4, `app/ui` 12, `app/view` 11, `app/wand` 8 ; `building` 8, `building/module` 8 ; `citizen` 11 ; `colony` 11, `colony/permission` 6, `colony/territory` 2 ; `construction/blueprint` 5, `construction/builder` 15, `construction/hut` 4, `construction/resources` 6, `construction/shared` 5, `construction/workorder` 10 ; `crafting/job` 10, `crafting/module` 12, `crafting/recipe` 12, `crafting/request` 8, `crafting/task` 3 ; `farming` 2, `farming/field` 6, `farming/hut` 7, `farming/job` 7 ; `job` 10, `job/work` 6 ; `kernel` 5, `kernel/ai` 9, `kernel/config` 4, `kernel/event` 1, `kernel/item` 11, `kernel/nav` 7, `kernel/persist` 6, `kernel/port` 15 ; `logistics/courier` 13, `logistics/pickup` 5, `logistics/warehouse` 13 ; `request` 15, `request/model` 11, `request/resolver` 2.
- **plugin** (`dev.hycolony.plugin`) : racine 6, `adapter` 15, `block` 11, `command` 8, `config` 8, `crafting` 7, `farming` 3, `goggles` 1, `item` 1, `npc` 10, `prefab` 6, `subplugin` 4, `ui` 10, `ui/citizen` 9, `ui/field` 1, `ui/highlight` 5, `ui/hut` 11, `ui/logistics` 1, `ui/townhall` 5, `ui/wand` 2.
- **domum/core** : `api` 4, `core` 3, `core/cutter` 6. **domum/plugin** : racine 4, `api` 2, `cutter` 11, `debug` 2, `persistence` 1, `registry` 2, `runtime` 8.
- **vanilla/core** : `core` 2. **vanilla/plugin** : racine 2, `api` 1, `block` 2. **blockui** : racine 1, `api` 14.

Paquets à 15 fichiers (limite) : `construction/builder`, `kernel/port`, `request`, `plugin/adapter`.

## 7. Avertissements de compilation (`compileJava --rerun`, 16)

| Avertissement                                             | Sites                                                                                                                                                                                                    |
|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `[EnumOrdinal]`                                           | `plugin/.../ui/field/FieldPage.java` ×2, `ui/BuildOptionsPanel.java` ×2, `ui/wand/WandPage.java`, `block/HutBlockSystems.java`, `core/.../job/HiringMode.java`, `core/.../farming/field/FieldStage.java` |
| `[ReferenceEquality]`                                     | `CitizenInventoryWindows.java`, `HytaleBlockBreaker.java`, `HytaleWorldBlocks.java`, `HytalePlayerInventory.java`, `HytaleItemCatalog.java:223`                                                          |
| `[removal]` `Player.getPlayerRef()` déprécié pour retrait | `plugin/.../ui/highlight/HighlightMarkers.java:24`                                                                                                                                                       |
| `[ShortCircuitBoolean]`                                   | `CitizenInventoryWindow.java`                                                                                                                                                                            |
| `[ArrayRecordComponent]`                                  | `plugin/.../prefab/PrefabStyles.java`                                                                                                                                                                    |
| Note javac « deprecated API » (sans `-Xlint:deprecation`) | plugin                                                                                                                                                                                                   |
