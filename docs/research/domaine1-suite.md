# Domaine 1, suite : herbe, butin HyDomum, A-17/A-20/A-21

Recherche du 2026-10-03, Hytale 0.7.0-pre.5 (`gradle.properties:19`). Note : `HytaleGoodFloor`, cité plus bas, est devenu `HP/item/HytaleBlockTraits.java` à l'implémentation. Abréviations :
`ST` = `sources/structurize/src/main/java/com/ldtteam/structurize`,
`MC` = `sources/minecolonies/src/main/java/com/minecolonies`,
`DO` = `sources/domum-ornamentum/src/main/java/com/ldtteam/domumornamentum`,
`HY` = `build/vineflower/hytale-server/com/hypixel/hytale`,
`zip:` = `%USERPROFILE%/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip`,
`HC` = `core/src/main/java/dev/hycolony/core`, `HP` = `plugin/src/main/java/dev/hycolony/plugin`.

Les sources MC/Structurize sont la branche Forge **1.20.1** (`sources/minecolonies/gradle.properties`,
`exactMinecraftVersion=1.20.1` ; Structurize idem).

## 1. Herbe : `GrassPlacementHandler`

### 1.1 La règle de Structurize

- `ST/placement/handlers/placement/PlacementHandlers.java:404-451` :
  - `canHandle` (l. 409) : le bloc **du plan** est `Blocks.GRASS_BLOCK` **ou** `Blocks.DIRT` (pas d'autre terre) ;
  - `getRequiredItems` (l. 428-436) : en pose « fancy » (le bâtisseur), un `Blocks.DIRT` ;
  - `doesWorldStateMatchBlueprintState` (l. 439-450) : en pose fancy, la case est faite si le monde y a un bloc du tag
    `BlockTags.DIRT` ; sinon égalité stricte.
- Le bâtisseur est toujours fancy : `MC/core/entity/ai/workers/util/BuildingStructureHandler.java:345-348`
  (`fancyPlacement()` renvoie `true`). Le test est appelé par `ST/placement/StructurePlacer.java:319` et `:604`
  via `IPlacementHandler.doesWorldStateMatchBlueprintState` (`ST/.../IPlacementHandler.java:27-42`).

### 1.2 Le tag `minecraft:dirt` en 1.20.1

Liste exacte (`https://raw.githubusercontent.com/misode/mcmeta/1.20.1-data/data/minecraft/tags/blocks/dirt.json`) :
`dirt`, `grass_block`, `podzol`, `coarse_dirt`, `mycelium`, `rooted_dirt`, `moss_block`, `mud`,
`muddy_mangrove_roots`. **Ni** `farmland` **ni** `dirt_path` n'en sont.

### 1.3 Ce que Hytale offre pour le reconnaître

Valeurs relues dans les items (`zip:Server/Item/Items/Soil/**`, `Plant/Moss/**`), parent résolu.

| Moyen | Lecture serveur | Ce qu'il contient | Verdict |
|---|---|---|---|
| Set `Soil` | `zip:Server/Item/Block/Sets/Soil.json` : `Soil_*` + `Plant_*` sauf `Plant_Leaves*`, `*Water*`, `*Mud` | argiles, briques d'argile, graviers, sable, neige, toutes les plantes | trop large |
| Set `Grass` | `Sets/Grass.json` : `Soil_Grass*` | les 14 herbes | partiel |
| Sets en général | `BlockSetModule.blockInSet` (`HY/server/core/modules/blockset/BlockSetModule.java`) | — | la classe est `@Deprecated(forRemoval = true)` (l. 26) : à éviter |
| `BlockType.getGroup()` | `HY/server/core/asset/type/blocktype/config/BlockType.java:1413` | `Dirt` : `Soil_Dirt*` pleins, mais aussi toutes les argiles, `Soil_Ash`, `Soil_Pathway*` (même demi), `Soil_Dirt_Tilled`, `Soil_Mud_Dry` ; `Grass` : herbes + `Soil_Seaweed_Block` ; `Mud` : `Soil_Mud` ; `Moss` : `Plant_Moss_Block_*` ; `LeavesGround` : `Soil_Leaves*` ; `Cover` : `Soil_Needles` | trop large (argiles) |
| Tags d'asset | `BlockType.getAssetMap().getKeysForTag(AssetRegistry.getTagIndex("Family=Dirt"))` (`HY/assetstore/AssetRegistry.java:73`, `AssetMap.java:36`, usage `HY/builtin/blockphysics/BlockPhysicsUtil.java:408`) | `Family=Dirt` : les `Soil_Dirt*` y compris demi, escaliers et labouré (`SubType=Tilled`) ; `Type=Soil` : 214 blocs dont métaux et briques | possible mais à composer |
| Liste d'ids dans l'id-map | comme `farming.tillable` (`plugin/src/main/resources/hycolony/id-map.json:174-178`) | exactement ce qu'on y écrit | **recommandé** |

Les plantes Hytale tiennent sur `TagId: "Type=Soil"` (`zip:Server/Item/Items/Plant/Grass/Plant_Grass_Lush.json:44-50`,
212 blocs) : c'est l'usage fonctionnel le plus proche du tag `DIRT` de Minecraft (où il sert à `BushBlock.mayPlaceOn`),
mais il prend argiles, graviers, sables et métaux.

**Règle la plus robuste** : une liste explicite dans l'id-map, parce que le tag de MC est lui-même une liste
explicite, que l'id-map est le seul endroit des ids d'assets (CLAUDE.md § 7) et que les sets sont en voie de retrait.

Liste proposée (blocs pleins, `Material: Solid`, `DrawType: Cube`) :

| MC (tag `dirt`) | Hytale |
|---|---|
| `dirt` | `Soil_Dirt`, `Soil_Dirt_Burnt`, `Soil_Dirt_Cold`, `Soil_Dirt_Lush`, `Soil_Dirt_Wet`, `Soil_Dirt_Poisoned` (enfants de `Template_Soil`, groupe `Dirt`, `Family=Dirt`) |
| `coarse_dirt` | `Soil_Dirt_Dry` (déjà la conversion de `tools/blueprint/tables.py:139`) |
| `grass_block` | `Soil_Grass`, `_Burnt`, `_Burnt_Full`, `_Cold`, `_Cold_Full`, `_Deep`, `_Deep_Full`, `_Dry`, `_Dry_Full`, `_Full`, `_Sunny`, `_Sunny_Full`, `_Wet`, `_Wet_Full` (= set `Grass`, groupe `Grass`) |
| `podzol` | `Soil_Needles` (groupe `Cover`), `Soil_Leaves`, `Soil_Leaves_Full` (groupe `LeavesGround`) : sols forestiers couverts, cassés en `Soil_Dirt` |
| `moss_block` | `Plant_Moss_Block_Green`, `_Green_Dark`, `_Blue`, `_Red`, `_Yellow` (groupe `Moss`) |
| `mud` | `Soil_Mud` (groupe `Mud`, hitbox `Block_Seven_Eighth`, casse en `Soil_Dirt`) |
| `mycelium`, `rooted_dirt`, `muddy_mangrove_roots` | pas d'équivalent vérifié (`Soil_Roots_Poisoned`, groupe `Soil`, `SubType=Roots`, est le seul candidat pour `rooted_dirt` : non retenu) |

Exclus, comme dans MC : `Soil_Dirt_Tilled` (= `farmland`), `Soil_Pathway*` (= `dirt_path`), les demi-blocs et
escaliers `Soil_Dirt_*_Half/_Stairs`, `Soil_Dirt_Crystal` (`Type=Rock`, casse en `Tool_Fertilizer_Crystal`),
`Soil_Ash`, `Soil_Seaweed_Block`, argiles, graviers, sables, neige. `Soil_Mud_Dry` (ce que devient la terre labourée,
`zip:.../Soil_Dirt_Tilled.json` `Farming.SoilConfig.TargetBlock`) n'a pas d'équivalent dans le tag (la boue tassée
de MC n'y est pas) : exclu.

### 1.4 HyColony aujourd'hui

- `HC/construction/blueprint/StructurePlan.java:232-234` (`isDone`) délègue à `satisfied` (l. 256-272) : égalité
  stricte (clé + rotation), puis, pour un plan MineColonies seulement (`cells.marked()`), case de remplissage →
  `catalog.isGoodFloor`, case de fluide → `SOLID` ou `FLUID`. Rien pour l'herbe ni la terre.
- Appelants : `HC/construction/builder/StructureScan.java:77`, `HC/construction/builder/PlannedBlocks.java:78`,
  `HC/construction/resources/NeededResources.java:53` (via `isDone`), `HC/app/wand/PasteQueue.java:100`.
- Port : `HC/kernel/port/ItemCatalog.java` (aucun prédicat « terre »). Plugin : `HP/adapter/HytaleItemCatalog.java`,
  qui délègue `isGoodFloor` à `HP/item/HytaleGoodFloor.java`.
- Plans livrés : 164 cases `Soil_Grass` (8 plans) et 841 `Soil_Dirt` dans `plugin/src/subplugins/**/*.prefab.json` ;
  la conversion fait `minecraft:dirt` → `Soil_Dirt`, `grass_block` → `Soil_Grass` (`tools/blueprint/tables.py:137-140`).
- Coût : `Soil_Grass` coûte lui-même (il a une recette au `Farmingbench`, `zip:.../Soil_Grass.json`), écart déjà
  marqué dans `HC/construction/resources/EntryCost.java:60-61` (Structurize demande de la terre).

### 1.5 Où brancher

- Port : deux booléens dans `ItemCatalog`, car MC distingue le bloc du plan (`canHandle` : herbe **ou** terre
  seulement) du bloc du monde (tout le tag) :
  - `isDirt(BlockKey)` : le bloc est dans la liste du tag (id-map, par exemple `blueprint.dirt`) ;
  - `takesAnyDirt(BlockKey)` : le bloc du plan est l'un des deux de `canHandle` (`Soil_Grass`, `Soil_Dirt`, par
    exemple `blueprint.grassHandled` dans l'id-map).
  Un seul `isDirt` appliqué aux deux côtés serait plus court, mais ouvrirait les 245 cases `Soil_Dirt_Dry`
  (`coarse_dirt`) à toute terre, ce que MC ne fait pas.
- Cœur : dans `StructurePlan.satisfied`, **avant** le `if (!cells.marked())` (la règle de Structurize vaut pour tout
  plan) : `if (catalog.takesAnyDirt(e.state().key()) && catalog.isDirt(world.key())) return true;`.
- Tests du cœur (TDD, `StructurePlanTest` ou `BuilderPlaceholdersTest`, `FakeCatalog` avec deux ensembles) :
  `grassCellIsDoneOnAnyDirtTagBlock`, `grassCellIsNotDoneOnTilledSoil`, `coarseDirtCellStillWantsItsOwnBlock`.

### 1.6 Autres gestionnaires qui assouplissent la correspondance

Liste de `ST/.../PlacementHandlers.java:56-79` et `MC/core/placementhandlers/PlacementHandlerInitializer.java:22-40`.

| Gestionnaire | Règle de correspondance (fancy) | HyColony |
|---|---|---|
| `SolidSubstitutionPlacementHandler` (`PlacementHandlers.java:1224-1231`), `SolidPlaceholderPlacementHandler` (MC, l. 124) | égal, ou `isGoodFloorBlock` | porté (case de remplissage) |
| `FluidSubstitutionPlacementHandler` (l. 275-285) | source, bloc noyé, ou `isAnySolid` | porté sans la source : A-20 |
| `SubstitutionPlacementHandler` (l. 1276-1282) | toujours vrai | porté (cases absentes du plan) |
| `AirPlacementHandler` (l. 769-776) | le monde est de l'air | porté (liste `CLEAR`) |
| `GrassPlacementHandler` (l. 439-450) | tag `DIRT` | **non porté** (§ 1.5) |
| `DoorPlacementHandler` (l. 494-523), `DoDoorBlockPlacementHandler` (MC, l. 119) | même bloc, `OPEN`/`POWERED` ignorés | porté autrement : un état non-forme (porte ouverte) se lit comme son bloc de base (`HP/block/HytaleBlockStates.java:60-81`) |
| `GeneralBlockPlacementHandler` (MC, l. 119-128), `DoBlockPlacementHandler` (MC, l. 136) | égal, ou même bloc pour mur, clôture, barreaux, portillon (forme libre) | **non porté** : une forme de clôture garde son id d'état (`HytaleBlockStates.java:71-79`), donc une clôture reformée par un voisin n'est plus « faite ». Le bâtisseur pose sans prévenir les voisins (`docs/superpowers/specs/2026-10-01-hydomum-fence-connections-design.md:21`) : effet seulement après un geste de joueur **[in-game]** |
| `DripStoneBlockPlacementHandler` (l. 1126-1133), `BeehivePlacementHandler` (l. 67), `FieldPlacementHandler` (l. 86), `RackPlacementHandler` (l. 76), `GravePlacementHandler` (l. 85), `NamedGravePlacementHandler` (l. 61), `WayPointBlockPlacementHandler` (l. 72) | même bloc, état libre | équivalent pour la rotation seulement (`onlyTurns`, `StructurePlan.java:236-247`), sans équivalent Hytale pour le reste |
| `NetherrackPlacementHandler` (MC, l. 60) | nylium ou netherrack | sans objet (pas de nylium converti) |
| `SpecialBlockPlacementAttemptHandler` (l. 658-664), `BlackListedBlockPlacementHandler` (l. 1167-1174) | toujours vrai | sans objet (portail de l'End, générateur, œuf ; tag `BLUEPRINT_BLACKLIST`) |
| `BannerPlacementHandler` (l. 1028-1035) | toujours faux | sans objet |
| `BlockGrassPathPlacementHandler` (l. 779-827) | égalité stricte ; **coût** nul si le monde est déjà `Blocks.DIRT` (l. 812-816) | coût non porté (le chemin coûte lui-même, `EntryCost.java:60-61`) |
| `DoublePlantPlacementHandler` (l. 615-622), `FlowerPotPlacementHandler` (l. 711-721), `FallingBlock`, `Fire`, `Container`, `Bed`, `Hut`, `Jigsaw`, `Lectern`, `Infested`, `WeatheredCopper`, `DimensionFluid`, `BarracksTower`, `BuilderIgnore` | égalité stricte | rien à porter |
| `BlockTagSubstitutionPlacementHandler` (`ST/.../BlockTagSubstitutionPlacementHandler.java:96-126`) | délègue au gestionnaire du bloc de remplacement | sans objet (pas de substitution par tag dans nos plans) |

## 2. HyDomum : un bloc variante rend `Wood_Stripped_Deco`

### 2.1 Ce que fait Domum Ornamentum

- Les seules tables de butin JSON (`sources/domum-ornamentum/src/datagen/generated/domum_ornamentum/data/domum_ornamentum/loot_tables/blocks/`,
  56 fichiers) sont des `dropSelf` (`DO/datagen/global/GlobalLootTableProvider.java:35-54`) : briques, blocs
  « extra », tapis flottants, tonneaux, scie de l'architecte.
- Les blocs à matériaux n'ont pas de table : ils redéfinissent `getDrops` et rendent **leur propre objet, avec ses
  matériaux** (NBT `textureData`) : `DO/util/BlockUtils.java:37-43` et `:65-77` (`getMaterializedItemStack`), par
  exemple `DO/block/decorative/PanelBlock.java:191-197` (avec `type`), `DO/block/vanilla/SlabBlock.java:116-120`
  (2 pour une double dalle), `DO/block/vanilla/DoorBlock.java:139-149` (moitié basse seulement).

### 2.2 HyDomum

- 43 gabarits sur 71 de `domum/plugin/src/main/resources/Server/Item/Items/HyDomum/` ont
  `Gathering.Physics.ItemId` **et** `Gathering.Breaking.ItemId` = `Wood_Stripped_Deco` : `Door_Full`,
  `Door_PortManteau`, `Door_VerticallyStriped`, `Door_Waffle`, `FancyTrapdoor_Creeper`, `FancyTrapdoor_Full`, `Fence`,
  `FenceGate`, les 15 `Panel_*`, `PaperWall`, `PaperWall_Tiled`, `Slab`, `Stairs`, les 15 `Trapdoor_*`, `Wall`
  (exemple : `HyDomum_Panel_Full.json`, bloc `Gathering`).
- Les 27 autres (`FancyDoor_*`, `Pillar_*`, `Post_*`, `Shingle*`, `TimberFrame_*`) n'ont pas d'`ItemId` et rendent
  déjà la variante ; `ArchitectsCutter` n'est pas une variante.
- `domum/plugin/src/main/java/dev/hydomum/plugin/runtime/VariantBlockType.java:62-75` ne renomme
  `Breaking.ItemId` que s'il égale la clé du gabarit (cas des états du mur, `"HyDomum_Wall"`, et de la double dalle
  `"HyDomum_Slab"` ×2, `tools/domum/blocks/compat.py:166-171`) ; `Physics.ItemId` n'est jamais renommé.
- Hytale : `BlockHarvestUtils.getDrops` (`HY/server/core/modules/interaction/BlockHarvestUtils.java:647-668`) rend
  `blockType.getItem()` **seulement** si ni `ItemId` ni `DropList` ; sinon l'objet nommé. La casse par un joueur lit
  `Breaking` (l. 907-910) ; la casse par la physique lit `Physics` d'abord (l. 451-467), la porte aussi
  (`HY/.../interaction/config/server/DoorInteraction.java:193-200`). `PhysicsDropType` ne sert qu'à ces deux endroits :
  il ne règle que le butin.

### 2.3 Cause : un accident du générateur, pas un choix

- `tools/domum/blocks/common.py:98-100` copie dans chaque gabarit le `Gathering` du **matériau par défaut**
  (`defaults(ctx, family)`). Pour les familles dont le défaut est `Wood_Stripped_Deco` (`tools/domum/families.py:108-222`,
  bois écorcé de DO, `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md:104`), il recopie
  `Physics/Breaking.ItemId: "Wood_Stripped_Deco"` de `zip:Server/Item/Items/Wood/Wood_Stripped_Deco.json:15-23`.
- Introduit par le commit `09248683` (2026-09-28, « Domum Ornamentum defaults … follow DO ») avec le changement de
  matériau par défaut. Aucune raison documentée : la spec dit l'inverse (« casser n'importe quel état rend cet
  objet », `do1-design.md:65`), et `docs/research/domum-ornamentum.md:456-457` note « casser le bloc rend l'objet »,
  vérifié en jeu le même jour (vraisemblablement avant ce commit).

### 2.4 L'objet de la variante existe et se pose

`domum/plugin/.../runtime/DynamicBlockTypeFactory.java:84-86` et `:166-181` : `VariantItem` a `id` et `blockId` =
`VariantKey.blockTypeKey()` (`<gabarit>__<matériau>…`), donc il pose sa variante ; `VariantBlockType.getItem()`
(l. 89-92) rend cet objet pour le bloc et chacun de ses états. L'id contient `__`, que `HP/item/HytaleItemSources.java:27-37`
reconnaît comme une variante obtenable. Les matériaux sont dans l'id : c'est l'équivalent du NBT `textureData` de DO.

### 2.5 Correctif le plus simple

À la source, dans le générateur : `tools/domum/blocks/common.py:98` ne recopie du matériau que ce qui ne nomme pas
d'objet (garder `Breaking.GatherType`, retirer `Breaking.ItemId`/`DropList` et tout `Physics` qui ne nomme qu'un
objet), puis régénérer : les 43 gabarits perdent leurs deux `ItemId`. `getDrops` tombe alors sur `getItem()`, donc
sur la variante (et sur le gabarit pour un gabarit posé tel quel, comme le `dropSelf` de DO). Les états qui nomment
déjà le gabarit (mur, double dalle) ne changent pas. Ajouter à `tools/domum/check_connected.py` (ou un `check_*`
voisin) une assertion : aucun gabarit ne nomme un objet autre que lui-même dans `Gathering`. Corriger seulement les
JSON serait annulé par la prochaine génération. Le résultat en jeu (casse par un joueur, par la physique, porte) est
**[in-game]**.

## 3. A-17, A-20, A-21 sur pre.5

### 3.1 A-17 : feuilles gratuites

- MC : `MC/core/entity/ai/workers/AbstractEntityAIStructure.java:934-940` (`isBlockFree` : null, eau,
  `BlockTags.LEAVES`, placeholder de décoration), appliqué à la requête (l. 758-761).
- **Correction de l'audit** : le set `Leaves` (`zip:Server/Item/Block/Sets/Leaves.json`, `"*Leaves"`) **ne contient
  pas les feuilles d'arbre**. Le motif est ancré en fin de chaîne (`HY/common/util/StringUtil.java:170-213`,
  `return textPos == text.length()`) et compare l'id complet (`HY/server/core/modules/blockset/BlockSetLookupTable.java`,
  clés = ids des `BlockType`). Il ne prend que `Soil_Leaves` (sol) et `Spawn_TempleLeaves` ; les 45 `Plant_Leaves_*`
  n'y sont pas. Le set `Tree` (`Plant_Leaves*`, `Wood_*`, `*_Roof_*`) prend aussi le bois. Et `BlockSetModule` est
  `@Deprecated(forRemoval = true)`.
- Bonne lecture : `BlockType.getGroup()` = `"Leaves"` (`BlockType.java:1413`) : exactement les 45 `Plant_Leaves_*`
  (ex. `zip:Server/Item/Items/Plant/Leaves/Plant_Leaves_Oak.json:24`), déjà utilisé par `HP/item/HytaleGoodFloor.java:40`.
  Les trois `Plant_Leaves_*_Floor` (sans groupe, `Family=Leaves`) sont des tapis de sol, pas des feuilles de MC.
- Où changer : un port `ItemCatalog.isLeaves(BlockKey)` (plugin : groupe `Leaves`), et
  `HC/construction/resources/EntryCost.of` (l. 34-36) renvoie `List.of()` pour une feuille (l'eau l'est déjà : un
  fluide n'a pas d'objet). Test : `EntryCostTest.leavesAreFree`. Aucun plan livré n'a de feuille (audit A-17).

### 3.2 A-20 : fluide source ou qui coule

- Confirmé : `zip:Server/Item/Block/Fluids/` a des paires source/écoulement : `Water_Source`/`Water`,
  `Lava_Source`/`Lava`, `Poison_*`, `Slime_*`, `Slime_Red_*`, `Tar_*`, `Tar_Void_*`, plus `Water_Finite`, `Fire`,
  `Dungeon_Fire_NoSpread`. Toutes les `*_Source` ont `MaxFluidLevel: 1` et `CanDemote: false` ; les écoulements
  `MaxFluidLevel: 8`, `Parent: <X>_Source`, `CanDemote: true`, `SupportedBy: <X>_Source` (`Water.json`).
- API : `Fluid.getMaxFluidLevel()` (`HY/server/core/asset/type/fluid/Fluid.java:308`),
  `FluidTicker.canDemote()` (`FluidTicker.java:484`, défaut `true` l. 68). `canDemote() == false` **ne suffit
  pas** : `Fire.json` et `Dungeon_Fire_NoSpread.json` ont aussi `CanDemote: false`. Règle sûre : suffixe `_Source`
  ou `getMaxFluidLevel() == 1` (identiques sur les 17 fluides).
- Où changer : `HC/construction/blueprint/StructurePlan.java:270-271` (`kind == FLUID` → `catalog.isFluidSource`),
  retirer l'écart l. 254 ; port `ItemCatalog.isFluidSource(BlockKey)` ; plugin `HytaleItemCatalog` (préfixe
  `HytaleBlockStates.FLUID_PREFIX`, `HP/block/HytaleBlockStates.java:21`). Effet aussi sur le collage
  (`HC/app/wand/PasteQueue.java:100`). Test : `BuilderPlaceholdersTest.fluidCellReplacesFlowingWater` ;
  `fluidCellKeepsAnotherFluid` (l. 192, `Lava_Source`) reste vert si `FakeCatalog` marque la source.

### 3.3 A-21 : « bon sol »

- Confirmé : `BlockType.getHitboxType()`/`getHitboxTypeIndex()` (`BlockType.java:1629-1635`),
  `BlockBoundingBoxes.DEFAULT = "Full"`, `UNIT_BOX` (`HY/server/core/asset/type/blockhitbox/BlockBoundingBoxes.java:28-29`),
  `isFullySupportive()` (`BlockType.java:1732-1734`).
- **Mais la hitbox `Full` n'est pas un indice de cube** : c'est la valeur par défaut (`BlockType.java:906`,
  `hitboxType = "Full"`), donc tout modèle qui n'en déclare pas est « Full ». Relevé sur les items : la règle
  « hitbox `Full` + `Solid` » ajouterait 194 blocs à la règle actuelle (coffres, pots, jardinières, braseros, tuyaux…)
  et n'en retirerait que `Soil_Mud` et `Soil_Snow` (`Block_Seven_Eighth`). Les feuilles ont `Material` par défaut
  `Empty` (`BlockType.java:873`) et `DrawType: Model`.
- `isFullySupportive()` vaut `ALL_SUPPORTING_FACES` par défaut pour `Solid` + `Cube`/`CubeWithModel`/`GizmoCube`
  (`BlockType.java:1905-1910`) : presque la règle actuelle. Et Hytale définit lui-même un bloc plein comme
  `Material == Solid && DrawType in (Cube, CubeWithModel)` : `FluidTicker.isFullySolid`
  (`HY/server/core/asset/type/fluid/FluidTicker.java:345-348`), exactement `HytaleGoodFloor.test` (l. 35-41).
- Verdict révisé : garder la règle actuelle ; réécrire l'écart de `HP/item/HytaleGoodFloor.java:29-30` (« Hytale has
  no such shape on the server » est faux) pour citer `FluidTicker.isFullySolid`. Facultatif : ajouter
  `getHitboxTypeIndex() == 0` pour écarter boue et neige à 7/8, que Structurize écarterait si leur forme n'est pas un
  cube (non vérifié pour la boue de MC, dont le code n'est pas dans `sources/`). Code de plugin : pas de test du cœur
  (`FakeCatalog.isGoodFloor` ne change pas) ; vérification par `/hycolony selftest` ou `docs/TESTING.md`.

## 4. Résumé des correctifs

| Point | Correctif | Fichiers |
|---|---|---|
| Herbe | case `Soil_Grass`/`Soil_Dirt` faite par tout bloc de la liste « tag dirt » | `HC/kernel/port/ItemCatalog.java` (+`isDirt`, `takesAnyDirt`), `HC/construction/blueprint/StructurePlan.java` (`satisfied`), `plugin/src/main/resources/hycolony/id-map.json` (deux listes), `HP/adapter/HytaleItemCatalog.java`, `core/src/test/.../testing/FakeCatalog.java`, test `StructurePlanTest` |
| HyDomum | le générateur ne recopie plus l'`ItemId` du matériau ; régénérer | `tools/domum/blocks/common.py:98`, 43 `domum/plugin/src/main/resources/Server/Item/Items/HyDomum/*.json`, un contrôle `tools/domum/check_*.py` |
| A-17 | feuille (groupe `Leaves`) gratuite | `ItemCatalog` (+`isLeaves`), `EntryCost.of`, `HytaleItemCatalog`, `FakeCatalog`, `EntryCostTest` |
| A-20 | case de fluide faite par une source (`_Source`), pas un écoulement | `ItemCatalog` (+`isFluidSource`), `StructurePlan.satisfied`, `HytaleItemCatalog`, `FakeCatalog`, `BuilderPlaceholdersTest` |
| A-21 | garder la règle, corriger l'écart (citer `FluidTicker.isFullySolid`) | `HP/item/HytaleGoodFloor.java` |
