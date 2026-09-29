# Blocs « dev » de Structurize : comportement du bâtisseur et portage

Recherche du 2026-09-29. Question : que fait le bâtisseur de MineColonies avec l'air, les blocs de substitution et les autres marqueurs des plans (`.blueprint`) ? Combien en ont les 440 plans `medievaloak` de l'utilisateur ? Que fait HyColony aujourd'hui, et quels blocs Hytale peuvent les représenter ?

Sources et abréviations :
- `ST/` = Structurize, tag `v1.20.1-1.0.818` (commit `c2b122be`), la version dont dépend MineColonies `version/main` (`structurize_version=1.20.1-1.0.818` dans `gradle.properties` de MC) : `github.com/ldtteam/structurize/blob/v1.20.1-1.0.818/src/main/java/com/ldtteam/structurize/`.
- `MC/` = MineColonies `version/main` (commit `6b4e03e3`, 2026-09-28) : `src/main/java/com/minecolonies/`.
- `HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/` (Hytale `0.7.0-pre.4`).
- `ZIP` = `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.4-Assets.zip`.

## 1. Le mécanisme commun (Structurize + MineColonies)

### 1.1 Qui décide pour chaque case

- Pour chaque case, `PlacementHandlers.getHandler` prend le **premier** gestionnaire dont `canHandle` accepte l'état du plan (`ST/placement/handlers/placement/PlacementHandlers.java`, bloc `static` l.56-80 et `getHandler`). L'ordre de départ : `Air`, `SolidSubstitution`, `Substitution`, `BlockTagSubstitution`, `BlackListedBlock`, `FluidSubstitution`, puis les blocs ordinaires.
- MineColonies ajoute ses gestionnaires avec `PlacementHandlers.add(handler)`, qui insère **à l'index 3**, donc après `Air`, `SolidSubstitution` et `Substitution` (`ST/.../PlacementHandlers.java`, `add(IPlacementHandler)` ; `MC/core/placementhandlers/PlacementHandlerInitializer.java:22-40`). MC ne remplace donc aucun des trois premiers.
- `MC/core/placementhandlers/SolidPlaceholderPlacementHandler.java` existe, mais il **n'est pas enregistré**. Seul `ItemAssistantHammer` l'utilise (l.109, 171). Le bâtisseur passe par `SolidSubstitutionPlacementHandler` de Structurize.
- Chaque gestionnaire fournit trois choses : `doesWorldStateMatchBlueprintState` (la case est-elle déjà bonne ?), `getRequiredItems` (le coût) et `handle` (la pose).

### 1.2 Le contexte du bâtisseur : pose « fancy »

`BuildingStructureHandler` est le contexte du bâtisseur (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java`). Il fixe :
- `fancyPlacement() = true` (l.345). C'est le mode « Pretty » du Build Tool, le seul que le bâtisseur connaisse ;
- `getSolidBlockForPos(...) = structureAI.getSolidSubstitution(pos)` (l.327-336). Cette méthode renvoie le réglage **`fillblock`** de la hutte du bâtisseur (`AbstractEntityAIStructure.getSolidSubstitution`, `MC/core/entity/ai/workers/AbstractEntityAIStructure.java:1085-1088`). Sa valeur par défaut est **`minecraft:dirt`** (`BUILDER_SETTINGS … .with(BuildingMiner.FILL_BLOCK, new BlockSetting((BlockItem) Items.DIRT))`, `MC/core/colony/buildings/modules/BuildingModules.java:433-439`). Il n'y a **pas** de choix selon le biome : le choix par génération du monde (`BlockUtils.getSubstitutionBlockAtWorld`, `ST/util/BlockUtils.java:167-190`) ne sert qu'au collage créatif (`CreativeStructureHandler.getSolidBlockForPos`, `ST/placement/structure/CreativeStructureHandler.java:136-145`) ;
- `allowReplace() = stage != CLEAR` (l.315). Pendant CLEAR, un bloc du monde renvoie `BREAK_BLOCK` et le bâtisseur le mine. Aux autres étapes, `StructurePlacer.handleBlockPlacement` le retire directement : les drops vont dans son inventaire, par `IPlacementHandler.handleRemoval` (`ST/placement/handlers/placement/IPlacementHandler.java:79-109`) ;
- `isStackFree` : sont gratuits les objets vides, les feuilles et `minecolonies:decorationcontroller` (`ModBlocks.blockDecorationPlaceholder`) (l.306-312) ;
- `isCreative() = Constants.BUILDER_INF_RESOURECES` (l.288).

`StructurePlacer.handleBlockPlacement` (`ST/placement/StructurePlacer.java:216-381`) :
- Une case qui correspond déjà (`doesWorldStateMatchBlueprintState`) donne `SUCCESS`, sans rien faire.
- Sinon, il demande `getRequiredItems` : `MISSING_ITEMS` s'ils manquent.
- Il retire ensuite le bloc du monde s'il n'est pas de l'air.
- Il appelle enfin `handle`. Un résultat **`PASS` ne consomme rien**. Seul `SUCCESS` consomme (l.362-378).

L'itérateur (`ST/placement/AbstractBlueprintIterator.iterateWithCondition`, l.91-117) saute les cases que le prédicat de l'étape exclut. Hors retrait (`!isRemoving()`), il saute aussi celles qui **correspondent déjà**. C'est ce test de correspondance qui protège les cases de substitution pendant CLEAR.

### 1.3 Les étapes du bâtisseur

`AbstractEntityAIStructure.loadStructure` (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:682-730`) choisit la liste des étapes :
- **nouvelle construction** (hutte niveau 0 sans parent) : `CLEAR, BUILD_SOLID, WEAK_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN` ;
- **amélioration** (niveau > 0, ou décoration de niveau connu) : les mêmes **sans CLEAR** ;
- **retrait** : `REMOVE_WATER, REMOVE`.

Ce que fait chaque étape (`structureStep`, l.364-428, et les prédicats l.540-575) :

| Étape | Cases visitées | Opération |
|---|---|---|
| CLEAR | toutes, de haut en bas (`decrement`). `skipClearing` saute une case **`blockfluidsubstitution` du plan**, et un monde qui est de l'air, un fluide, de la bedrock ou un `IBuilderUndestroyable`. L'itérateur saute les cases qui correspondent déjà. | `BLOCK_REMOVAL` : le bâtisseur mine |
| BUILD_SOLID | blocs du plan « vrais solides » (`BlockUtils.canBlockFloatInAir`) et non décoratifs (`skipBuilding`) | pose |
| WEAK_SOLID | solides « faibles » (tag `structurize:weak_solid_blocks`) | pose |
| CLEAR_WATER | couche par couche. Retire le fluide du monde sous toute case du plan **sans fluide**, sauf `blocksubstitution` et `blockfluidsubstitution` (`StructurePlacer.clearWaterStep`, `ST/placement/StructurePlacer.java:484-532`) | retrait de fluide |
| CLEAR_NON_SOLIDS | cases du plan en `AirBlock` dont le monde n'est pas vide (`decrement`) | pose d'air, donc retrait |
| DECORATE | cases non solides, ou décoratives (`isDecoItem` : tag `minecolonies:decoration_items` **ou `BlockFluidSubstitution`**, l.312-315) | pose |
| SPAWN | entités | apparition |

Les demandes de ressources (`AbstractEntityAIStructureWithWorkOrder.requestMaterials`, `MC/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:226-331`) font une passe `GET_RES_REQUIREMENTS` (étapes SOLID, WEAK_SOLID, DECO, ENTITIES). Elles utilisent un `WorkerLoadOnlyStructureHandler`, qui hérite de `LoadOnlyStructureHandler extends CreativeStructureHandler(..., fancy = true)` (`MC/api/util/LoadOnlyStructureHandler.java:22-49`). Le coût est donc lui aussi calculé en mode fancy, et **seulement pour les cases qui ne correspondent pas déjà** au moment du calcul (`StructurePlacer.getResourceRequirements`, l.545-625).

`BlockSubstitution` (et ses variantes solide et fluide, qui partagent `defaultSubstitutionProperties`) n'est ni remplaçable ni traversable, et ne tombe pas. Il fait donc partie de `trueSolidBlocks` (`ST/util/BlockUtils.java:89-100` ; `ST/blocks/schematic/BlockSubstitution.java:30-41`). Les substitutions « simple » et « pleine » passent ainsi à **BUILD_SOLID**. La substitution de fluide est exclue de BUILD_SOLID par `isDecoItem` et passe à **DECORATE**.

## 2. Chaque bloc marqueur

### 2.1 `minecraft:air`

- **Gestionnaire** : `AirPlacementHandler` (`ST/.../PlacementHandlers.java:751-803`).
- **Correspondance** : le monde est de l'air (`worldState.is(Blocks.AIR)`).
- **Coût** : aucun (`getRequiredItems` renvoie une liste vide).
- **Pose** : si la case n'est pas vide, il tue les entités non vivantes de la case (tableaux, cadres), fait `removeBlock` et renvoie `PASS`.
- **Étapes** :
  - CLEAR (nouvelle construction) : tout bloc du monde non-air sous une case d'air du plan est **miné** ;
  - CLEAR_WATER : l'eau du monde y est retirée ;
  - CLEAR_NON_SOLIDS : pour une amélioration, sans CLEAR, les blocs restants y sont retirés.
- **Résultat : « vide forcé »**, pour toute la boîte du plan.

### 2.2 `structurize:blocksubstitution` (« garder le terrain »)

- **Gestionnaire** : `SubstitutionPlacementHandler` (`ST/.../PlacementHandlers.java:1262-1310`).
- **Correspondance** : en fancy, **toujours vraie** (`placementContext.fancyPlacement() || worldState.equals(blueprintState)`). `BlockUtils.areBlockStatesEqual` dit la même chose (`ST/util/BlockUtils.java:400-403`).
- **Coût** : rien en fancy. En mode Complete (outil de scan, collage créatif), il faut le bloc substitut lui-même.
- **Pose** : en fancy, **rien** (`PASS`). En Complete, il pose le bloc substitut.
- **Étapes** : comme la case correspond toujours, l'itérateur la saute à CLEAR, BUILD_SOLID et DECORATE. CLEAR_WATER la saute explicitement. **Le monde garde ce qu'il a** : terre, air, eau ou arbre.

### 2.3 `structurize:blocksolidsubstitution` (« du plein ici »)

- **Gestionnaire** : `SolidSubstitutionPlacementHandler` (`ST/.../PlacementHandlers.java:1205-1260`).
- **Correspondance** (fancy) : `worldState.equals(blueprintState) || BlockUtils.isGoodFloorBlock(worldState)`. `isGoodFloorBlock` (`ST/util/BlockUtils.java:863-866`) accepte :
  - un bloc cube plein (`isGoodFullBlock` : forme `Shapes.block()`), hors tag `structurize:unsuitable_solid_for_placeholder` (qui contient les **feuilles**) ;
  - ou un bloc du tag `structurize:good_solid_for_placeholder` (qui contient la **terre labourée**) (`ST/datagen/BlockTagProvider.java:47-49`).
- **Si le monde a déjà un bon bloc plein** (pierre, terre, planches…) : la case correspond, et elle est sautée partout. On garde le bloc, **sans coût**.
- **Sinon** (air, eau, herbe haute, feuilles, escalier, clôture…) :
  - CLEAR mine le bloc s'il y en a un (sauf fluide) ;
  - CLEAR_WATER retire l'eau ;
  - BUILD_SOLID pose `getSolidBlockForPos(pos)`, c'est-à-dire le **`fillblock` de la hutte du bâtisseur (terre par défaut)**. `handle` renvoie `SUCCESS`, donc l'objet est consommé.
- **Coût** : 1 × `fillblock` par case qui ne correspond pas (`getRequiredItems` fancy : `getItemStackFromBlockState(getSolidBlockForPos(...))`). Il entre dans la liste des ressources demandées.
- **Son** : celui du bloc de remplissage (`BuildingStructureHandler.triggerSuccess`, l.216-225).
- `IStructureHandler.replaceWithSolidBlock` / `shallReplaceSolidSubstitutionBlock` (le bâtisseur renvoie `false`, `MC/core/entity/ai/workers/builder/EntityAIStructureBuilder.java:273-276`) n'a **aucun appelant** dans Structurize 1.0.818 : c'est du code mort.

### 2.4 `structurize:blockfluidsubstitution` (« du fluide ici »)

- **Gestionnaire** : `FluidSubstitutionPlacementHandler` (`ST/.../PlacementHandlers.java:196-286`). MC ne le remplace pas : son `DimensionFluidHandler` ne prend que les `LiquidBlock` et les `BubbleColumnBlock`, c'est-à-dire les vrais `minecraft:water`/`lava`.
- **Correspondance** : le monde a une source de fluide, un bloc `WATERLOGGED` (quelle que soit sa valeur), ou n'importe quel solide (`isAnySolid`). **Le solide n'est pas remplacé.** On ne remplit que l'air, les plantes et les fluides qui ne sont pas des sources.
- **Choix du fluide** : `BlockUtils.getFluidForDimension(world)` (`ST/util/BlockUtils.java:657-671`). C'est le `defaultFluid` du générateur de la dimension : l'eau dans l'Overworld, la lave dans le Nether (`ultraWarm`). Aucun biome ni réglage n'intervient.
- **Pose** (fancy) : si le bloc du monde a la propriété `WATERLOGGED`, il est mis à `true`. Sinon, le fluide de la dimension est posé (`PASS`, sans consommation).
- **Coût** (fancy) : **l'eau est gratuite**. Si le fluide de la dimension est la lave, il faut un seau de lave.
- **Étapes** : sautée à CLEAR (`skipClearing`, l.572-575) et à CLEAR_WATER. Posée à **DECORATE** (`isDecoItem`).

### 2.5 `structurize:blocktagsubstitution` (ancre + étiquettes)

- **Bloc** : `BlockTagSubstitution extends BlockSubstitution implements IAnchorBlock`. Son entité de bloc `structurize:tagsubstitution` porte les données du plan (`blueprintDataProvider` : étiquettes par position) et un bloc de remplacement facultatif (`replacement`) (`ST/blocks/schematic/BlockTagSubstitution.java:17-25` ; `ST/blockentities/BlockEntityTagSubstitution.java:58, 231-245`).
- **Gestionnaire** : `BlockTagSubstitutionPlacementHandler` (`ST/placement/handlers/placement/BlockTagSubstitutionPlacementHandler.java:30-178`). En fancy, il **délègue tout** (correspondance, coût, pose) au gestionnaire du bloc de remplacement.
- **Remplacement absent** : `ReplacementBlock.isEmpty()` vaut « l'état est de l'air » (l.242-245). Le cas se ramène donc à l'`AirPlacementHandler` : vide forcé, sans coût.
  - La lecture d'un compound vide en `AIR` vient de `NbtUtils.readBlockState` de Minecraft, non relu ici.
- **Plans de l'utilisateur** : les 12 blocs sont tous à l'**ancre** d'une décoration sans hutte (`camp`, `ship`, `supplycamp`, `supplyship`…). Ils n'ont **aucun `replacement`**, seulement des étiquettes, par exemple `groundlevel` en `(-1,-1,0)` pour `camp.blueprint`, et `invisible` + `groundlevel` pour `supplycamp.blueprint`.
- **Rôle** : marqueur d'ancre et de niveau du sol. Le bâtisseur le traite comme de l'air.

### 2.6 Blocs de hutte (`minecolonies:blockhut*`)

- **Hutte à l'ancre** : `HutPlacementHandler` (`MC/core/placementhandlers/HutPlacementHandler.java`).
  - Correspondance : état identique. Coût : l'objet hutte (sauf la tour de caserne) plus le contenu de son entité de bloc.
  - À la pose, il écrit le chemin du plan dans l'entité de bloc.
  - `DONT_TOUCH_PREDICATE` (`AbstractEntityAIStructure.java:109-117`) interdit de toucher la hutte à l'ancre quand le monde en a déjà une : le cas normal, puisque le joueur l'a posée.
- **Hutte hors de l'ancre** (tours de caserne, sous-bâtiments) : elle est posée comme un bloc. `BarracksTowerHandler` pose `blockhutbarrackstower` **sans coût** (`MC/core/placementhandlers/BarracksTowerHandler.java`). Les autres huttes demandent leur objet.

### 2.7 Autres marqueurs MineColonies présents dans les plans

- **`minecolonies:blockwaypoint`** (`WayPointBlockPlacementHandler`) : en fancy, il **retire** le bloc, **enregistre un point de passage** de colonie à cette position (`colony.addWayPoint(pos, AIR)`) et ne coûte rien. `BuildingStructureHandler.triggerSuccess` ajoute aussi le point de passage (l.228-231).
- **`minecolonies:decorationcontroller`** : c'est l'ancre des décorations. Il est posé comme un bloc ordinaire, mais son objet est **gratuit** (`isStackFree`).
- **`minecraft:structure_block` / `structure_void`** (`BuilderIgnorePlacementHandler`) : en fancy, rien n'est posé et rien n'est demandé. Aucun n'apparaît dans les plans de l'utilisateur.
- **Tag `structurize:blueprint_blacklist`** (`BlackListedBlockPlacementHandler`, l.1164-1203) : la case correspond toujours et rien n'est posé. Le tag est vide par défaut (`BlockTagProvider.java:51`).

### 2.8 Résumé

| Bloc du plan | Monde déjà… | Le bâtisseur | Coût | Étape |
|---|---|---|---|---|
| `minecraft:air` | air | rien | 0 | — |
| `minecraft:air` | autre | mine / retire | 0 | CLEAR, CLEAR_WATER, CLEAR_NON_SOLIDS |
| `blocksubstitution` | n'importe quoi | rien (terrain gardé) | 0 | — |
| `blocksolidsubstitution` | bloc plein correct | rien | 0 | — |
| `blocksolidsubstitution` | air, fluide, non plein, feuilles | mine si besoin, puis pose le `fillblock` (terre) | 1 `fillblock` | CLEAR, puis BUILD_SOLID |
| `blockfluidsubstitution` | source, bloc waterloggable, solide | rien | 0 | — |
| `blockfluidsubstitution` | air, plante, fluide non source | pose le fluide de la dimension (eau) | 0 (seau de lave si lave) | DECORATE |
| `blocktagsubstitution` sans remplacement | — | comme `air` | 0 | comme air |
| hutte à l'ancre | hutte | intouchable | objet hutte | — |
| `blockwaypoint` | — | retire + point de passage | 0 | selon sa solidité (non vérifié) |

## 3. Fréquence dans les plans de l'utilisateur

Mesure du 2026-09-29 sur `C:\Users\Ctuto\Desktop\minecolonies\medievaloak` : 440 fichiers, 0 échec de lecture, 3 147 398 cases au total. Script : `blueprint.blueprint.load_blueprint`, qui compte les noms de la palette par case.

| Bloc | Cases | Fichiers | Plus gros exemples |
|---|---:|---:|---|
| `minecraft:air` | 2 130 758 | 440 | `education/universitylibrary` (50 503), `education/altlibrary1` (44 789) |
| `structurize:blocksubstitution` | 321 124 | 440 | `education/university2` (8 801), `fundamentals/alttownhall2` (7 637) |
| `structurize:blocksolidsubstitution` | 238 054 | 408 | `fundamentals/alttownhall1` (4 352), `education/altlibrary1` (4 296) |
| `structurize:blockfluidsubstitution` | 12 031 | 12 | `decorations/supplies/ship` et `supplyship` (1 522 chacun), `agriculture/husbandry/altfisherman1` (1 228) |
| `minecraft:water` | 2 605 | 67 | `craftsmanship/carpentry/sawmill5` (313) |
| `minecolonies:blockhut*` | 397 | 367 | 367 à l'ancre ; hors ancre : 23 `barrackstower`, 5 `barracks`, 1 `field`, 1 `library` |
| `minecraft:lava` | 67 | 6 | `craftsmanship/masonry/stonesmeltery5` (28) |
| `minecolonies:blockwaypoint` | 24 | 6 | `decorations/misc/bigwell1..3` (4 chacun) |
| `structurize:blocktagsubstitution` | 12 | 12 | `decorations/supplies/camp`, `ship`, `supplycamp` (1 chacun, à l'ancre) |
| `minecolonies:decorationcontroller` | 46 | — | ancre des décorations |

- La substitution de fluide n'apparaît que dans les 10 plans de pêcheur (`fisherman1..5`, `altfisherman1..5`) et dans `ship`/`supplyship`. Aucun de ces 12 plans n'a de `minecraft:water`.
- **94 % des cases de substitution pleine** (223 373 sur 238 054) sont **sous la couche de sol de la hutte** (y < ancre.y − 1). Il y a aussi 96 754 blocs réels sur 445 431, dans 313 fichiers (par exemple `alttownhall5` : 4 423 blocs, ancre à y = 9). Les plans MineColonies ont des fondations sous l'ancre, ce que la règle `FLOOR_Y` de HyColony ne prévoit pas (§ 4.1).

## 4. HyColony aujourd'hui

### 4.1 Le plugin : lecture des prefabs

- `PrefabCells.resolve` / `block` (`plugin/src/main/java/dev/hycolony/plugin/prefab/PrefabCells.java:43-90`) **supprime** `Empty`, `Block_Spawner_Block` (sauf coffre) et tout `Editor_*`. Une case vide qui contient un fluide devient `~fluid:<FluidKey>`.
- Il n'existe donc **aucune entrée** pour l'air forcé, les substitutions ni l'ancre éditeur : leur sens est perdu à la lecture.
- `HytaleBlueprintSource.hutRelative` (`HytaleBlueprintSource.java:63, 227-237`) **jette tout ce qui est sous `FLOOR_Y = -1`** (relatif à la hutte). La boîte de CLEAR commence à y = 0 (l.190). Cette règle vise les prefabs vanilla, dont les couches basses sont des fondations à enfoncer (Javadoc l.51-55). Pour un plan MineColonies converti, elle supprime les fondations et presque toutes les substitutions pleines (§ 3).

### 4.2 Le cœur

- `StructurePlan.build` (`core/.../construction/blueprint/StructurePlan.java:56-99`) :
  - `clearList` = **toute la boîte** `[min, max]` du plan, sauf la hutte ;
  - `solidList` et `decoList` sont classées selon `BlockKind` (SOLID, NON_SOLID/FLUID) ;
  - `stateAt` ne connaît que les entrées.
- `StructureScan.needsWork` (`core/.../construction/builder/StructureScan.java:48-76`) : CLEAR mine tout bloc minable **ou fluide** de la boîte qui n'est pas l'état prévu à cet endroit, hors hutte.
- **Conséquence** : dans la boîte, une case absente du plan est traitée comme de l'**air MineColonies** (vidée), qu'elle vienne de `Empty`, d'une `blocksubstitution` (absente) ou d'une `blocksolidsubstitution` (`Editor_Block`, ignoré). Le terrain que MC garde (substitution simple) ou remplit (substitution pleine) est **creusé et jamais rempli**. Une substitution de fluide n'est pas prise en compte non plus.
- Étapes HyColony : `CLEAR, SOLID, DECORATE, CLEAR_LEFTOVERS` (`core/.../construction/workorder/Stage.java`). Il n'y a ni CLEAR_WATER, ni CLEAR_NON_SOLIDS, ni WEAK_SOLID. Il n'y a pas non plus de réglage `fillblock` : aucune occurrence de `fill` dans `BuilderSettingsModule` ni dans `construction/resources`.

### 4.3 Le convertisseur (`tools/blueprint/converter.py`)

Avec `Options.editor_blocks = True` (valeur par défaut), `editor_block()` (l.236-252) fait :

| Source MC | Sortie | Règle |
|---|---|---|
| `minecraft:air` | `Empty` | `editor_empty` |
| `structurize:blocksolidsubstitution` | `Editor_Block` | `editor_solid` |
| `structurize:blocksubstitution` | rien (case omise) | `placeholder` (`tables.PLACEHOLDERS`) |
| hutte à l'ancre | `Editor_Anchor` | `editor_anchor` |
| hutte hors de l'ancre | rien | `placeholder` (`tables.is_placeholder` prend tout `minecolonies:blockhut*`) |
| `structurize:blockfluidsubstitution` | rien, **non mappé** | `unmapped` |
| `structurize:blocktagsubstitution` | rien, **non mappé** | `unmapped` |
| `minecolonies:blockwaypoint`, `minecolonies:decorationcontroller` | rien, **non mappé** | `unmapped` |
| `minecraft:water` / `minecraft:lava` | **bloc** `Fluid_Water` / `Fluid_Lava` | `upstream` (`data/default-block-overrides.csv`) |

Ce tableau a été vérifié en lançant `Converter().convert` sur `fisherman1`, `camp`, `bigwell1`, `stonesmeltery5`, `alttownhall1` et `barracks4`.

Deux problèmes relevés en passant :
- **Eau et lave écrites comme blocs.** `Fluid_Water` est un *objet* dont le `BlockType` est un cube `Material: Solid` avec une texture d'eau. Le vrai fluide passe par l'interaction `PlaceFluid` → `Water_Source` (`ZIP:Server/Item/Items/Fluid/Fluid_Water.json`). Un fluide de prefab se met dans le tableau `fluids: [{x, y, z, name, level}]` (`HY/server/core/prefab/selection/buffer/BsonPrefabBufferDeserializer.java:134-168`), avec `name` = `Water_Source` (`MaxFluidLevel: 1`) ou `Lava_Source`. Le convertisseur n'écrivait aucun `fluids` (corrigé le 2026-09-29 : les sources vont dans `fluids`, un fluide qui coule devient `Empty`). Rendu en jeu du bloc `Fluid_Water` : **[in-game]**.
- **Mauvaise ancre dans 6 plans.** `blueprint._find_anchor` prend la première entité de bloc `minecolonies:colonybuilding`. Pour `barracks1..5`, c'est une **tour** (par exemple `barracks4` : ancre (3,5,5) `blockhutbarrackstower`, alors que `primary_offset` = (13,9,10) `blockhutbarracks`). Pour `universitylibrary`, on obtient (24,4,28), alors que `primary_offset` = (25,4,18). Structurize prend `primary_offset` comme ancre (`Blueprint.getPrimaryBlockOffset`).

## 5. Côté Hytale : blocs éditeur et collage de prefabs

### 5.1 Les blocs techniques du pack

Tous sont définis dans `ZIP:Server/Item/Items/Editor/*.json` : `Quality: Technical`, `Group: @Tech`, tag `Type: Editor`, catégorie `Tool.PrefabEditing`. Leurs descriptions viennent de `ZIP:Server/Languages/en-US/server.lang:6593-6598`.

| Id | Rendu | Description officielle |
|---|---|---|
| `Editor_Anchor` | cube transparent, texture `EditorBlockPrefabAnchor.png` | « Marks the anchor point for prefabs » |
| `Editor_Block` | modèle `Blocks/Miscellaneous/Trigger.blockymodel`, texture `EditorBlock.png` | « A visible placeholder block for level design. Does not affect gameplay. » |
| `Editor_Empty` | cube transparent, texture `Editor_Empty.png` | « Represents empty space in prefabs. Use to explicitly clear blocks when a prefab is placed. » |

- `Empty` est le bloc d'air (`BlockType.EMPTY_ID == 0`, `ZIP:Server/BlockTypeList/Empty.json`).
- Le pack n'a **pas** d'autre bloc « Editor_* » : pas de variante fluide, ni solide « à remplir ».
- `Block_Spawner_Block` et `Prefab_Spawner_Block` sont des générateurs, pas des marqueurs.

### 5.2 Ce que l'éditeur de prefabs en fait

**Sauvegarde** (`HY/builtin/buildertools/prefabeditor/saving/PrefabSaver.copyBlocksWithLoadedChunks`, l.130-205) :
- une case `Editor_Block` **n'est jamais écrite** : elle est absente du prefab (`block != editorBlock`) ;
- `Editor_Empty` est écrit comme bloc 0, c'est-à-dire **`Empty` explicite** ;
- l'air ordinaire n'est écrit que si le réglage `isEmpty()` est actif ;
- les entités de bloc `Editor_*` sont exclues (l.224, 264).

**Copie de l'outil de construction** (`HY/builtin/buildertools/BuilderToolsPlugin.java:4044-4120`) :
- un `Editor_Anchor` devient l'ancre de la sélection, et sa case reçoit le bloc voisin non vide ;
- `Editor_Block` est ignoré.

**Collage** (`HY/server/core/util/PrefabUtil.paste`, l.354-470) :
- avec le drapeau technique (2), une case sans bloc ni fluide est collée comme `Editor_Empty`. Avec les drapeaux 2 et 4 (`Flags.test` = `(v & f) == f`, l.671-673), la case d'ancre est collée comme `Editor_Anchor`. C'est ainsi que l'éditeur affiche un prefab ;
- sans ce drapeau, **aucun traitement spécial** : un `Editor_Block` écrit dans un fichier serait posé tel quel dans le monde ;
- sans `force` (1), chaque bloc passe par `BlockOperations.testPlaceBlock`, qui ne pose que sur des cases vides ou de matériau `Empty` (`HY/server/core/universe/world/chunk/BlockOperations.java:204-258`). Une entrée `Empty` ne vide donc une case occupée **qu'avec `force`** ;
- le fluide de chaque case présente dans le buffer est écrit sans condition (`fluidSection.setFluid(..., fluidId, level)`, l.389-390). Une case du prefab sans fluide **efface** donc l'eau du monde à cet endroit.

### 5.3 Correspondance entre les conventions

| Sens | Plan MineColonies | Prefab Hytale (fichier) | Éditeur de prefabs Hytale |
|---|---|---|---|
| vide forcé | `minecraft:air` | `Empty` explicite | `Editor_Empty` |
| garder le terrain | `blocksubstitution` | case absente | rien (ou `Editor_Block`, qui ne sera pas sauvegardé) |
| plein si pas plein | `blocksolidsubstitution` | **aucun équivalent** | **aucun équivalent** |
| fluide si vide | `blockfluidsubstitution` | **aucun équivalent** (un `fluids` impose le fluide) | **aucun équivalent** |
| ancre | hutte / `blocktagsubstitution` / `decorationcontroller` | `anchorX/Y/Z` du prefab | `Editor_Anchor` |

Conséquences pour le portage :
- `Editor_Block` a pour sens Hytale « case absente ». L'utiliser comme « plein si pas plein » reste une convention de HyColony. Elle ne résiste pas à un passage par l'éditeur de prefabs : un `/editprefab` puis une sauvegarde **efface** ces cases. Un `Editor_Block` écrit directement dans le JSON par le convertisseur est bien relu comme un bloc ordinaire par `BsonPrefabBufferDeserializer`, et `PrefabCells` le voit, puis l'ignore.
- Pour porter fidèlement la substitution pleine et celle de fluide, il faut soit :
  - deux blocs marqueurs propres au mod (dans le pack HyColony), lus par `PrefabCells` ;
  - soit une table à côté du prefab (positions par type), comme `styles.json`.

  Dans les deux cas, le cœur a besoin de deux nouveaux types d'entrée : « plein si pas bon sol » (coût : 1 `fillblock`) et « fluide si vide » (gratuit pour l'eau). Il lui faut aussi un réglage `fillblock` dans la hutte du bâtisseur (terre par défaut, comme MC).
- Pour la substitution simple, « case absente » suffit, **à condition** que le CLEAR de HyColony ne vide pas les cases absentes de la boîte. Or aujourd'hui il les vide (§ 4.2). Il faut soit :
  - une entrée explicite « air » (venue d'`Empty`), et une CLEAR qui ne vide que ces cases et les cases des blocs prévus. C'est le comportement de MC, où CLEAR saute ce qui correspond déjà, donc les substitutions ;
  - soit garder la boîte, mais en retirer les positions marquées « garder ».

## 6. Ce qui reste incertain

- Le fait que `NbtUtils.readBlockState` d'un compound vide donne `AIR` (tag de substitution sans `replacement`) vient du code vanilla Minecraft 1.20.1, non relu ici.
- Le rendu en jeu d'un bloc `Fluid_Water` posé par un prefab : **[in-game]**.
- Le comportement d'un `Editor_Block` ou d'un `Editor_Anchor` qu'un joueur pose dans le monde, hors éditeur (il n'a ni collision ni interaction dans le JSON) : **[in-game]**.
- L'étiquette `groundlevel` (décorations sans hutte) sert au placement du plan par rapport au sol, dans l'outil de construction. Son usage exact dans MineColonies n'a pas été suivi ici.
