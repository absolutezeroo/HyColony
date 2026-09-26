# Prefab obtainability audit (Hytale 0.6.8)

Research only. Scope: the 10 vanilla prefabs listed in `plugin/src/main/resources/hycolony/styles.json` (styles `outlander` and `kweebec`, levels 1-5; townhall, builder and residence share the same prefab per level). The question: can a survival player obtain every item the builder requests? One unobtainable item stalls a build forever.

Sources: `release-0.6.8-Assets.zip` (`Server/Prefabs`, `Server/Item/Items`, `Server/Item/Recipes`, `Server/Item/ResourceTypes`, `Server/Drops`, `Server/World`, `Server/HytaleGenerator`, `Server/BarterShops`) and the decompiled server in `build/vineflower/hytale-server`. Scripts: `load.py`, `prefabs.py`, `sources.py`, `classify.py`, `gen.py`, `gen2.py`, `alt.py` in the session scratchpad (not committed).

## TL;DR

- **No prefab is fully obtainable as it stands.** Every level of both styles has at least one blocking item: 14 to 428 blocks per level.
- **Worst offenders:** `Wood_Redwood_Trunk_Full` (460 blocks), the Kweebec furniture set (`Furniture_Kweebec_Platform` 97, `_Ladder` 27, `_Window` 23, `_Shelf` 20, ...), tree branches (`Wood_*_Branch_*`, 282), `Furniture_Village_Crate` (82, its recipe points at a bench that does not exist), `Furniture_Ancient_Barrel` (25) and `Survival_Trap_Spike_Wood_Large` (28).
- **Kweebec L5 is the worst prefab:** 4362 items, 428 of them blocked. Half the prefab is a giant redwood (2137 trunk, leaf, branch and root blocks). It also needs 1186 smooth `Rock_Basalt`, which only a furnace makes (2 cobble each).
- **Several items that look like ruin-only variants are fine.** The deadwood roof, stairs and planks craft at the Builders bench from `Wood_Deadwood`, which petrified trunks provide. Leaves and winter grass drop themselves when broken with Shears (`Tool_Shears_Basic`, Farmingbench).
- **A cheap generic fix clears 5 of the 34 blocking items (107 distinct items overall) with no visual change:** request the block's own *break drop* instead of `BlockType.getItem()` when the drop is a single item (`*_Trunk_Full` → `*_Trunk`, `Roof_Hollow` → `Roof`, `Wood_Torch_Wall` → `Furniture_Crude_Torch`, `Rubble_Shale_Medium` → `Rubble_Shale`). The remaining items need a per-block substitution table.
- **Recommendation:** keep all 10 prefabs and add a substitution map in the adapter (section 5). No vanilla Kweebec or Outlander house is fully obtainable either, so swapping prefabs would only swap one substitution list for another. The one exception to consider is Kweebec L5 (see section 4).

## 1. Method

### 1.1 Items needed per prefab (mirrors `HytaleBlueprintSource`)
- A prefab JSON `blocks[]` entry has `x,y,z,name[,rotation,filler,components]`. The buffer subtracts the anchor (`BsonPrefabBufferDeserializer`), and `hutOffset` is in the same raw coordinates, so hut-relative `y = y_json - hutOffset.y`. The script keeps entries with `y >= -1` and `filler == 0`, and skips the hut cell itself.
- Filtered out: `Empty`, `Block_Spawner_Block` and `Editor_*`. A state id `*Base_State_Definitions_X` becomes `Base`. None of the 10 prefabs has a `fluids` section.
- Block to item: `BlockType.getItem()` is the item asset that *contains* the `BlockType` (`data.getContainerKey(Item.class)`), so block id = item id. All block names resolved to items: no unknown ids, and no free placements.

### 1.2 Obtainability model
Item JSONs are resolved with `Parent` inheritance (deep merge; `Recipe` is not inherited because it uses `append`, not `appendInherited`, in `Item.CODEC`). An item is **obtainable** if a fixed-point search reaches it from these seeds:

- **Gather (natural):** the drop of a block that occurs in natural generation. Natural sources are `Server/World/Default` (excluding `PrefabPatterns`, `Monuments_*`, villages, towns and cities), `Server/HytaleGenerator`, `Server/PrefabList`, and the prefab folders `Trees/`, `Plants/`, `Rock_Formations/` and `Cave/`. Cave nodes that are really structures (Goblin, Skeleton, Crypt, Ruin, Mine, Klops, Surface_Corner camps) are excluded. Blocks found only in `Npc/`, `Monuments/`, `Dungeon/` or `Mineshaft*` count as structure loot, which is finite and non-renewable, so they are **not obtainable**.
- **Drops follow `BlockHarvestUtils`:** break uses `Gathering.Breaking`, or `Soft` when there is no `Breaking`. `ItemId` and/or `DropList` give those items. With neither, `getDrops()` falls back to `blockType.getItem()`, the block itself. `Harvest` (F-pickup) and each `Tools[]` entry (for example `{"Type":"Shears"}`) follow the same rule, so an empty entry drops the block itself. Drop lists (`Server/Drops/**`) are expanded recursively.
- **Mob:** any item in `Server/Drops/NPCs/**`. **Barter:** `Server/BarterShops/*` outputs whose inputs are obtainable.
- **Craft:** item `Recipe` fields and `Server/Item/Recipes/**` (salvage). All inputs must be obtainable: `ItemId`, `ResourceTypeId` (any obtainable item listing that id in `ResourceTypes`) or `ItemTag`. One `BenchRequirement` must also be satisfiable: `Fieldcraft` (pocket crafting), or a bench block whose `BlockType.Bench.Id` matches, whose `Categories` contain the recipe's categories, and which is itself obtainable. This check is recursive to any depth, not only one level down. For example, `Wood_Deadwood_Roof` needs `RT:Wood_Deadwood`, which comes from `Wood_Deadwood_Planks`, which needs `RT:Wood_Deadwood_Trunk`, which comes from the natural `Wood_Petrified_Trunk`. The chain resolves to **obtainable**.
- **Placed-block breaking:** breaking a placed obtainable block also yields its drops.
- **Uncertain:** the data points both ways, or the only source is a small, finite set of natural cave decorations.

Valid bench ids in 0.6.8: `Alchemybench, Arcanebench, Armory, Armor_Bench, Builders, Campfire, Cookingbench, Farmingbench (Bench_Farming, Bench_Trough), Furnace, Furniture_Bench, Loombench, Salvagebench, Tannery, Weapon_Bench, Workbench` plus `Fieldcraft`. Recipes also reference `TODO` (58), `Architects`/`Architectsbench` (5), `ArmorBench` (4) and `Furniture_Misc` (2). **No bench declares those ids, so those recipes can never be crafted.**

### 1.3 Limits
- The worldgen scan matches block ids in natural-generation JSON by token. It can over-report, for example a block named in a biome file that never actually spawns, but every *blocking* verdict below was checked by hand.
- Tier: none of the 107 needed items has a `RequiredTierLevel` or `KnowledgeRequired` recipe. Among the replacements, only the seed recipes need Farmingbench T4.
- Mob and barter sources did not decide any needed item.


## 2. Per-prefab totals

Counts are items requested, which equal blocks placed. KWE = kweebec, OUT = outlander; the digit is the level.

| Level | Prefab | Items needed | Obtainable | Not obtainable | Uncertain | Distinct items (ok / not / unc) |
|---|---|---:|---:|---:|---:|---|
| kweebec 1 | `Kweebec_Redwood_Small_Plot_House_001` | 156 | 84 | 72 | 0 | 7 / 7 / 0 |
| kweebec 2 | `Kweebec_Redwood_Small_Plot_House_002` | 422 | 329 | 93 | 0 | 16 / 8 / 0 |
| kweebec 3 | `Kweebec_Redwood_Normal_Plot_House_001` | 569 | 429 | 140 | 0 | 20 / 10 / 0 |
| kweebec 4 | `Kweebec_Redwood_Normal_Plot_House_002` | 1100 | 925 | 175 | 0 | 18 / 10 / 0 |
| kweebec 5 | `Kweebec_Redwood_Medium_House_002` | 4362 | 3934 | 428 | 0 | 26 / 16 / 0 |
| outlander 1 | `Outlander_Houses_Tier0_005` | 173 | 146 | 26 | 1 | 10 / 6 / 1 |
| outlander 2 | `Outlander_Houses_Tier1_001` | 644 | 613 | 14 | 17 | 22 / 6 / 1 |
| outlander 3 | `Outlander_Houses_Tier2_001` | 982 | 945 | 22 | 15 | 22 / 4 / 1 |
| outlander 4 | `Outlander_Houses_Tier2_003` | 1696 | 1642 | 22 | 32 | 28 / 3 / 2 |
| outlander 5 | `Outlander_Houses_Tier3_005` | 1296 | 1193 | 57 | 46 | 32 / 8 / 2 |

### 2.1 Full item multiset per prefab

Status and how to obtain each item. "RT:" is a resource type (any item carrying it); the bench is written `Id [categories]`.

| Item | KWE1 | KWE2 | KWE3 | KWE4 | KWE5 | OUT1 | OUT2 | OUT3 | OUT4 | OUT5 | Status | How |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| `Cloth_Roof_Hide` |  |  |  |  |  | 3 |  |  |  |  | NOT | no recipe, no natural source |
| `Cloth_Roof_Hide_Flat` |  |  |  |  |  | 9 |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Ancient_Barrel` |  |  |  |  |  |  | 4 | 5 | 10 | 6 | NOT | no recipe, no natural source |
| `Furniture_Ancient_Torch` |  |  |  |  |  | 1 |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Crude_Brazier` |  |  |  |  |  | 1 | 1 |  |  | 1 | NOT | no recipe, no natural source |
| `Furniture_Faun_Stool` |  |  |  |  |  |  | 1 |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Door` |  |  | 1 | 1 | 1 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Ladder` |  |  | 3 |  | 24 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Platform` | 26 |  | 8 |  | 63 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Shelf` | 4 | 4 | 2 | 3 | 7 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Statue` |  |  |  | 1 |  |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Stool` |  |  | 1 | 2 | 5 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Table` |  |  |  | 1 | 1 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Wardrobe` |  |  | 1 | 1 | 1 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Kweebec_Window` |  | 4 | 7 | 10 | 2 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Furniture_Tavern_Barrel` |  |  |  |  |  | 2 |  |  |  | 2 | NOT | no recipe, no natural source |
| `Plant_Crop_Aubergine_Block` | 2 | 2 |  |  |  |  |  |  |  |  | NOT | no recipe, no natural source |
| `Plant_Crop_Lettuce_Block` |  | 1 |  |  |  |  |  |  |  |  | NOT | no recipe, no natural source |
| `Plant_Crop_Pumpkin_Block` | 3 | 1 |  |  |  |  |  |  |  |  | NOT | no recipe, no natural source |
| `Rubble_Shale_Medium` |  |  |  |  |  |  |  |  |  | 1 | NOT | no recipe, no natural source |
| `Wood_Cedar_Branch_Corner` | 11 |  |  |  | 7 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Cedar_Branch_Long` | 2 |  |  |  | 57 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Cedar_Branch_Short` |  |  |  |  | 4 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Cedar_Trunk_Full` | 24 |  |  |  | 12 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Deadwood_Roof_Hollow` |  |  |  |  |  | 10 |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Fir_Branch_Long` |  |  |  |  |  |  |  |  |  | 27 | NOT | no recipe, no natural source |
| `Wood_Redwood_Branch_Corner` |  | 13 | 19 | 17 | 29 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Redwood_Branch_Long` |  | 5 | 7 | 5 | 61 |  | 6 | 6 |  | 6 | NOT | no recipe, no natural source |
| `Wood_Redwood_Roots` |  |  |  |  | 7 |  |  |  |  |  | NOT | no recipe, no natural source |
| `Wood_Redwood_Trunk_Full` |  | 63 | 91 | 134 | 147 |  | 1 | 8 | 8 | 8 | NOT | no recipe, no natural source |
| `Wood_Torch_Wall` |  |  |  |  |  |  | 1 | 3 | 4 | 6 | NOT | no recipe, no natural source |
| `Furniture_Village_Counter` |  |  |  |  |  |  |  |  | 1 |  | uncertain | recipe exists but targets bench id `Furniture_Misc`, which no bench block declares -> recipe unreachable |
| `Furniture_Village_Crate` |  |  |  |  |  | 1 | 17 | 15 | 31 | 18 | uncertain | recipe exists but targets bench id `Furniture_Misc`, which no bench block declares -> recipe unreachable |
| `Survival_Trap_Spike_Wood_Large` |  |  |  |  |  |  |  |  |  | 28 | uncertain | only in `Cave/Nodes/Rock_Volcanic/Undead` cave nodes (finite), no recipe |
| `Bench_Furnace` |  |  |  |  |  |  |  |  | 1 |  | obtainable | craft: 6x RT:Wood_Trunk + 6x RT:Rock @ Workbench [Workbench_Crafting] |
| `Container_Bucket` |  |  |  |  |  |  |  |  |  | 1 | obtainable | craft: 3x RT:Wood_All + 1x Ingredient_Bar_Iron @ Farmingbench [Farming] |
| `Deco_Mug` |  |  |  |  |  |  |  | 1 | 1 | 1 | obtainable | craft: 1x RT:Wood_Planks + 1x Ingredient_Stick @ Furniture_Bench [Furniture_Misc] |
| `Deco_Rope` |  | 2 | 1 | 1 | 1 |  |  |  |  |  | obtainable | craft: 1x Ingredient_Fibre @ Builders [Bricks]; gather: natural block |
| `Deco_Scroll` |  |  |  |  |  |  |  |  | 1 |  | obtainable | craft: 4x Ingredient_Fibre + 2x Ingredient_Stick @ Furniture_Bench [Furniture_Misc] |
| `Furniture_Crude_Bed` |  |  |  |  |  | 2 |  |  |  |  | obtainable | craft: 3x Ingredient_Fibre + 2x Ingredient_Hide_Light @ Fieldcraft [Tools] or Workbench [Workbench_Survival] |
| `Furniture_Crude_Candle` |  |  |  |  |  | 1 | 1 | 3 | 1 | 3 | obtainable | craft: 1x Ingredient_Tree_Sap + 1x Ingredient_Bar_Copper @ Furniture_Bench [Furniture_Lighting] |
| `Furniture_Crude_Door` |  |  |  |  |  | 3 |  |  |  | 1 | obtainable | craft: 2x RT:Wood_Lightwood_Softwood @ Builders [Door] |
| `Furniture_Crude_Torch` |  |  |  |  |  |  |  |  |  | 4 | obtainable | craft: 1x Ingredient_Fibre + 1x Ingredient_Tree_Sap + 1x Ingredient_Stick @ Fieldcraft [Tools] or Workbench [Workbench_Survival] |
| `Furniture_Kweebec_Bed` |  |  | 1 | 2 | 2 |  |  |  |  |  | obtainable | craft: 3x RT:Wood_All + 4x Ingredient_Fibre @ Furniture_Bench [Furniture_Beds] |
| `Furniture_Kweebec_Candle` | 1 |  | 2 | 1 | 2 |  |  |  |  |  | obtainable | craft: 2x Ingredient_Fibre + 2x Ingredient_Tree_Sap @ Furniture_Bench [Furniture_Lighting] |
| `Furniture_Kweebec_Lantern` |  | 1 | 1 | 1 | 2 |  |  |  |  |  | obtainable | craft: 2x Ingredient_Bar_Iron + 1x Furniture_Crude_Candle + 1x Ingredient_Tree_Sap @ Furniture_Bench [Furniture_Lighting] |
| `Furniture_Lumberjack_Bed` |  |  |  |  |  |  | 1 | 1 | 4 | 1 | obtainable | craft: 3x Wood_Hardwood_Planks + 4x Ingredient_Fibre + 2x Ingredient_Hide_Heavy + 1x Cloth_Block_Wool_White @ Furniture_Bench [Furniture_Beds] |
| `Furniture_Lumberjack_Chair` |  |  |  |  |  |  |  |  | 1 | 1 | obtainable | craft: 2x RT:Wood_Redwood @ Builders [Chair] |
| `Furniture_Lumberjack_Door` |  |  |  |  |  |  | 2 | 2 | 2 | 2 | obtainable | craft: 2x RT:Wood_Redwood @ Builders [Door] |
| `Furniture_Lumberjack_Ladder` |  |  |  |  |  |  | 4 | 6 |  | 6 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Ladder] |
| `Furniture_Lumberjack_Lantern` |  |  |  |  |  |  | 2 |  |  |  | obtainable | craft: 2x Ingredient_Bar_Iron + 1x Furniture_Crude_Candle + 1x Ingredient_Tree_Sap @ Furniture_Bench [Furniture_Lighting] |
| `Furniture_Lumberjack_Platform` |  |  |  |  |  |  | 31 | 19 | 5 | 19 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Platform] |
| `Furniture_Lumberjack_Shelf` |  |  |  |  |  |  | 4 | 8 | 10 | 7 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Shelf] |
| `Furniture_Lumberjack_Table` |  |  |  |  |  |  | 1 | 1 | 2 | 1 | obtainable | craft: 4x RT:Wood_Redwood @ Builders [Table] |
| `Furniture_Lumberjack_Wardrobe` |  |  |  |  |  |  | 1 | 1 | 1 | 1 | obtainable | craft: 4x RT:Wood_Redwood @ Builders [Wardrobe] |
| `Furniture_Lumberjack_Window` |  |  |  |  |  | 1 | 2 | 6 | 10 | 6 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Window] |
| `Furniture_Tavern_Ladder` |  |  |  |  |  |  |  |  |  | 1 | obtainable | craft: 1x RT:Wood_Darkwood @ Builders [Ladder] |
| `Plant_Crop_Mushroom_Block_Brown` |  | 3 |  | 25 | 6 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Crop_Mushroom_Block_Yellow` |  | 9 | 5 | 27 | 23 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Crop_Mushroom_Common_Brown` |  | 4 | 4 | 10 | 14 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Crop_Mushroom_Shelve_Brown` |  | 2 | 3 | 7 | 3 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Crop_Mushroom_Shelve_Yellow` | 1 | 1 | 3 | 3 | 5 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Flower_Common_White` |  |  |  |  | 2 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Flower_Flax_Blue` |  |  |  |  | 7 |  |  |  |  |  | obtainable | gather: natural block |
| `Plant_Grass_Winter` |  | 3 | 3 | 7 | 22 |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Plant_Grass_Winter_Short` |  | 2 | 3 | 7 | 24 |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Plant_Grass_Winter_Tall` |  | 2 | 1 | 3 | 25 |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Plant_Leaves_Cedar` |  |  |  |  | 274 |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Plant_Leaves_Redwood` |  |  |  |  | 769 |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Plant_Moss_Block_Green` |  | 14 | 20 | 24 | 9 |  |  |  |  |  | obtainable | craft: 4x Plant_Moss_Green + 2x Ingredient_Fibre @ Farmingbench [Decorative]; gather: natural block |
| `Plant_Moss_Green` |  |  |  |  | 2 |  |  |  |  |  | obtainable | gather: break natural block Plant_Moss_Block_Green |
| `Plant_Moss_Rug_Green` | 12 | 13 | 13 | 36 | 54 |  |  |  |  |  | obtainable | craft: 4x Plant_Moss_Green + 2x Ingredient_Fibre @ Farmingbench [Decorative]; gather: natural block |
| `Plant_Moss_Short_Green` |  |  | 1 |  | 2 |  |  |  |  |  | obtainable | craft: 4x Plant_Moss_Green + 2x Ingredient_Fibre @ Farmingbench [Decorative]; gather: natural block |
| `Plant_Moss_Wall_Green` |  |  | 1 |  |  |  |  |  |  |  | obtainable | gather: natural block (needs Shears; plain break drops sticks/saplings/nothing) |
| `Rock_Basalt` |  | 56 | 57 | 179 | 1186 |  |  |  |  |  | obtainable | craft: 2x RT:Rock_Basalt @ Furnace [] |
| `Rock_Basalt_Cobble` |  |  |  |  |  |  |  |  |  | 65 | obtainable | gather: break natural block Rock_Basalt |
| `Rock_Basalt_Cobble_Half` |  |  |  |  |  |  |  |  |  | 4 | obtainable | craft: 1x RT:Rock_Basalt @ Builders [HalfSlab]; gather: natural block |
| `Rock_Basalt_Cobble_Roof` |  |  |  |  |  |  |  |  |  | 145 | obtainable | craft: 1x RT:Rock_Basalt @ Builders [Roof] |
| `Rock_Basalt_Cobble_Roof_Flat` |  |  |  |  |  |  |  |  |  | 30 | obtainable | craft: 1x RT:Rock_Basalt @ Builders [Roof] |
| `Rock_Shale` |  |  |  |  |  |  | 11 | 37 | 96 | 35 | obtainable | craft: 2x RT:Rock_Shale @ Furnace [] |
| `Rock_Shale_Brick` |  |  |  |  |  |  | 25 | 73 | 212 | 77 | obtainable | craft: 1x RT:Rock_Shale @ Builders [Bricks]; gather: natural block |
| `Rock_Shale_Brick_Half` |  |  |  |  |  |  | 35 |  | 36 |  | obtainable | craft: 1x RT:Rock_Shale_Brick @ Builders [HalfSlab]; gather: natural block |
| `Rock_Shale_Brick_Smooth` |  |  |  |  |  |  |  |  | 3 |  | obtainable | craft: 1x RT:Rock_Shale @ Builders [Smooth] |
| `Rock_Shale_Cobble` |  |  |  |  |  |  |  |  | 37 |  | obtainable | gather: break natural block Rock_Shale |
| `Rubble_Shale` |  |  |  |  |  |  |  |  |  | 3 | obtainable | craft: 1x Rock_Shale_Cobble @ Salvagebench []; gather: natural block |
| `Soil_Ash` |  |  |  |  |  |  |  |  |  | 1 | obtainable | gather: natural block |
| `Soil_Clay_Brick` |  |  |  |  |  |  |  | 66 | 42 |  | obtainable | craft: 1x RT:Clays @ Builders [Bricks]; gather: natural block |
| `Soil_Clay_Brick_Half` |  |  |  |  |  |  |  | 4 | 3 |  | obtainable | craft: 1x RT:Clays @ Builders [HalfSlab] |
| `Soil_Dirt` |  |  |  |  | 309 |  |  |  |  |  | obtainable | gather: break natural block Soil_Mud |
| `Soil_Dirt_Cold` |  |  | 4 |  |  | 25 | 125 | 122 | 206 | 116 | obtainable | craft: 1x RT:Soils + 1x Ingredient_Fibre @ Farmingbench [Decorative]; gather: natural block |
| `Soil_Grass` |  | 21 | 26 | 45 | 255 |  |  |  |  |  | obtainable | craft: 1x RT:Soils + 1x Ingredient_Fibre @ Farmingbench [Decorative] |
| `Soil_Needles` | 23 | 33 | 73 | 65 | 166 |  |  |  |  |  | obtainable | craft: 1x RT:Soils + 1x Ingredient_Fibre @ Farmingbench [Decorative] |
| `Soil_Pathway` | 14 |  |  |  |  |  |  |  |  |  | obtainable | craft: 1x RT:Soils + 1x Ingredient_Fibre @ Farmingbench [Decorative] |
| `Soil_Pathway_Half` | 3 |  |  |  |  |  |  |  |  |  | obtainable | craft: 1x Soil_Pathway @ Builders [Structural-Rock]; gather: natural block |
| `Soil_Pebbles_Frozen` |  |  |  |  |  | 26 | 4 |  |  | 81 | obtainable | craft: 3x Rubble_Shale @ Farmingbench [Decorative] |
| `Wood_Cedar_Trunk` | 30 |  |  |  | 148 |  |  |  |  |  | obtainable | gather: natural block |
| `Wood_Deadwood_Roof` |  |  |  |  |  | 31 | 103 | 145 | 244 |  | obtainable | craft: 1x RT:Wood_Deadwood @ Builders [Roof] |
| `Wood_Deadwood_Roof_Flat` |  |  |  |  |  | 3 | 17 | 28 | 39 |  | obtainable | craft: 1x RT:Wood_Deadwood @ Builders [Roof] |
| `Wood_Deadwood_Stairs` |  |  |  |  |  |  |  |  |  | 14 | obtainable | craft: 1x RT:Wood_Deadwood @ Builders [Stairs] |
| `Wood_Fir_Trunk` |  |  |  |  |  |  |  |  |  | 145 | obtainable | gather: break natural block Wood_Fir_Trunk_Full |
| `Wood_Redwood_Beam` |  |  |  |  |  | 4 |  |  |  |  | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Beam] |
| `Wood_Redwood_Fence` |  |  |  |  |  |  |  |  | 6 |  | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Wall] |
| `Wood_Redwood_Planks` |  |  |  |  |  |  | 97 | 196 | 333 | 196 | obtainable | craft: 1x RT:Wood_Redwood_Trunk @ Builders [WoodPlanks] |
| `Wood_Redwood_Planks_Half` |  |  |  |  |  |  | 26 | 6 | 8 | 6 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [HalfSlab] |
| `Wood_Redwood_Stairs` |  |  |  |  |  |  | 30 | 37 | 53 | 37 | obtainable | craft: 1x RT:Wood_Redwood @ Builders [Stairs] |
| `Wood_Redwood_Trunk` |  | 163 | 207 | 482 | 622 | 50 | 85 | 173 | 272 | 173 | obtainable | gather: natural block |
| `Wood_Softwood_Fence` |  |  |  |  |  |  | 6 | 10 | 13 | 10 | obtainable | craft: 1x RT:Wood_Softwood @ Builders [Wall]; gather: natural block |

## 3. Non-obtainable and uncertain items, with replacements

Fix kinds: **A** = request the block's own break drop and still place the original block (no visual change; generic rule, section 5.1). **B** = substitute another block of the same role and shape family. **C** = crop: request the seed, which places the same block at stage 0, or skip. **D** = skip (place nothing).

| Item | KWE1 | KWE2 | KWE3 | KWE4 | KWE5 | OUT1 | OUT2 | OUT3 | OUT4 | OUT5 | Total | Status | Fix | Replacement (request) | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
| `Wood_Redwood_Trunk_Full` |  | 63 | 91 | 134 | 147 |  | 1 | 8 | 8 | 8 | 460 | NOT | A | `Wood_Redwood_Trunk` | break drop of the block; same log, bark on all faces vs. rings on top |
| `Wood_Cedar_Trunk_Full` | 24 |  |  |  | 12 |  |  |  |  |  | 36 | NOT | A | `Wood_Cedar_Trunk` | break drop of the block |
| `Wood_Torch_Wall` |  |  |  |  |  |  | 1 | 3 | 4 | 6 | 14 | NOT | A | `Furniture_Crude_Torch` | break drop; the crude torch has `PlacementSettings.WallPlacementOverrideBlockId = Wood_Torch_Wall`, so a player placing a crude torch on a wall gets exactly this block |
| `Wood_Deadwood_Roof_Hollow` |  |  |  |  |  | 10 |  |  |  |  | 10 | NOT | A | `Wood_Deadwood_Roof` | break drop of the block; same roof family (hollow underside). Every `*_Roof_Hollow` is unobtainable except gold brick |
| `Rubble_Shale_Medium` |  |  |  |  |  |  |  |  |  | 1 | 1 | NOT | A | `Rubble_Shale` | break/harvest drop of the block |
| `Furniture_Kweebec_Platform` | 26 |  | 8 |  | 63 |  |  |  |  |  | 97 | NOT | B | `Furniture_Lumberjack_Platform` | same role; redwood, Builders [Platform] |
| `Wood_Redwood_Branch_Long` |  | 5 | 7 | 5 | 61 |  | 6 | 6 |  | 6 | 96 | NOT | B | `Wood_Redwood_Beam` | no branch is obtainable (break -> `Wood_Branch` drop list = sticks); beam is the closest thin log. Check rotation mapping (branch vs beam `VariantRotation`) |
| `Furniture_Village_Crate` |  |  |  |  |  | 1 | 17 | 15 | 31 | 18 | 82 | uncertain | B | `Furniture_Crude_Chest_Small` | crate recipe unreachable (bench `Furniture_Misc`); a chest keeps the container role |
| `Wood_Redwood_Branch_Corner` |  | 13 | 19 | 17 | 29 |  |  |  |  |  | 78 | NOT | B | `Wood_Redwood_Beam` | no branch is obtainable; loses the elbow shape (no corner beam exists) |
| `Wood_Cedar_Branch_Long` | 2 |  |  |  | 57 |  |  |  |  |  | 59 | NOT | B | `Wood_Darkwood_Beam` | cedar trunks carry resource type `Wood_Darkwood` |
| `Survival_Trap_Spike_Wood_Large` |  |  |  |  |  |  |  |  |  | 28 | 28 | uncertain | B | `Wood_Softwood_Fence` | palisade look without trap damage; Builders [Wall] |
| `Furniture_Kweebec_Ladder` |  |  | 3 |  | 24 |  |  |  |  |  | 27 | NOT | B | `Furniture_Lumberjack_Ladder` | Builders [Ladder] |
| `Wood_Fir_Branch_Long` |  |  |  |  |  |  |  |  |  | 27 | 27 | NOT | B | `Wood_Hardwood_Beam` | fir trunks carry resource type `Wood_Hardwood` |
| `Furniture_Ancient_Barrel` |  |  |  |  |  |  | 4 | 5 | 10 | 6 | 25 | NOT | B | `Furniture_Crude_Chest_Small` | no barrel is obtainable; container stays a container. Visual-only alternative: Wood_Softwood_Trunk_Half |
| `Furniture_Kweebec_Window` |  | 4 | 7 | 10 | 2 |  |  |  |  |  | 23 | NOT | B | `Furniture_Lumberjack_Window` | Builders [Window] |
| `Furniture_Kweebec_Shelf` | 4 | 4 | 2 | 3 | 7 |  |  |  |  |  | 20 | NOT | B | `Furniture_Lumberjack_Shelf` | Builders [Shelf] |
| `Wood_Cedar_Branch_Corner` | 11 |  |  |  | 7 |  |  |  |  |  | 18 | NOT | B | `Wood_Darkwood_Beam` | cedar = `Wood_Darkwood`; loses the elbow shape |
| `Cloth_Roof_Hide_Flat` |  |  |  |  |  | 9 |  |  |  |  | 9 | NOT | B | `Cloth_Roof_Orange_Flat` | flat member of the same cloth-roof family; Builders [Roof] from 1 orange wool |
| `Furniture_Kweebec_Stool` |  |  | 1 | 2 | 5 |  |  |  |  |  | 8 | NOT | B | `Furniture_Village_Stool` | Builders [Stool] (hardwood); or Furniture_Crude_Stool |
| `Wood_Cedar_Branch_Short` |  |  |  |  | 4 |  |  |  |  |  | 4 | NOT | B | `Wood_Darkwood_Beam` | cedar = `Wood_Darkwood` resource type; Builders [Beam] |
| `Furniture_Tavern_Barrel` |  |  |  |  |  | 2 |  |  |  | 2 | 4 | NOT | B | `Furniture_Crude_Chest_Small` | no barrel is obtainable; chest keeps the container role |
| `Furniture_Kweebec_Door` |  |  | 1 | 1 | 1 |  |  |  |  |  | 3 | NOT | B | `Furniture_Lumberjack_Door` | Builders [Door]; keep the door state (`CloseDoorOut`) normalisation |
| `Furniture_Kweebec_Wardrobe` |  |  | 1 | 1 | 1 |  |  |  |  |  | 3 | NOT | B | `Furniture_Lumberjack_Wardrobe` | Builders [Wardrobe]; container |
| `Cloth_Roof_Hide` |  |  |  |  |  | 3 |  |  |  |  | 3 | NOT | B | `Cloth_Roof_Orange` | same cloth-roof shape family (all 6 shapes exist per colour); Builders [Roof] from 1 wool. No Hide/Leather cloth roof is craftable |
| `Furniture_Crude_Brazier` |  |  |  |  |  | 1 | 1 |  |  | 1 | 3 | NOT | B | `Furniture_Crude_Torch` (cheap) or `Furniture_Temple_Light_Brazier` | only obtainable braziers are the temple ones (Furniture_Bench, marble + charcoal + azure petals) |
| `Furniture_Kweebec_Table` |  |  |  | 1 | 1 |  |  |  |  |  | 2 | NOT | B | `Furniture_Lumberjack_Table` | Builders [Table] |
| `Furniture_Ancient_Torch` |  |  |  |  |  | 1 |  |  |  |  | 1 | NOT | B | `Furniture_Crude_Torch` | floor torch, same support (Down) |
| `Furniture_Faun_Stool` |  |  |  |  |  |  | 1 |  |  |  | 1 | NOT | B | `Furniture_Crude_Stool` | Builders [Stool] |
| `Furniture_Village_Counter` |  |  |  |  |  |  |  |  | 1 |  | 1 | uncertain | B | `Furniture_Lumberjack_Table` | counter recipe unreachable (bench `Furniture_Misc`); Builders [Table] |
| `Plant_Crop_Pumpkin_Block` | 3 | 1 |  |  |  |  |  |  |  |  | 4 | NOT | C | `Plant_Seeds_Pumpkin` or skip | seed item places this block (`Seed_Place`, `BlockTypeToPlace`) at stage 0; crop then grows. Seeds: harvest drop, or Farmingbench T4 [Seeds] |
| `Plant_Crop_Aubergine_Block` | 2 | 2 |  |  |  |  |  |  |  |  | 4 | NOT | C | `Plant_Seeds_Aubergine` or skip | same as pumpkin; Farmingbench T4 [Seeds] |
| `Plant_Crop_Lettuce_Block` |  | 1 |  |  |  |  |  |  |  |  | 1 | NOT | C | `Plant_Seeds_Lettuce` or skip | same as pumpkin; Farmingbench [Seeds] |
| `Wood_Redwood_Roots` |  |  |  |  | 7 |  |  |  |  |  | 7 | NOT | D | `Empty` (skip) or `Wood_Redwood_Trunk_Half` | decorative flare at a tree base; break drop is a stick drop list |
| `Furniture_Kweebec_Statue` |  |  |  | 1 |  |  |  |  |  |  | 1 | NOT | D | `Empty` (skip) | no statue in the game is obtainable |


## 4. Recommendation per style and level

"After A" means the generic break-drop rule of section 5.1 is applied. "Blocked" counts are in blocks (items requested).

| Level | Blocks still blocked with no fix | after A only | after A+C | after A+B+C+D |
|---|---:|---:|---:|---:|
| kweebec 1 | 72 | 48 | 43 | 0 |
| kweebec 2 | 93 | 30 | 26 | 0 |
| kweebec 3 | 140 | 49 | 49 | 0 |
| kweebec 4 | 175 | 41 | 41 | 0 |
| kweebec 5 | 428 | 269 | 269 | 0 |
| outlander 1 | 27 | 17 | 17 | 0 |
| outlander 2 | 31 | 29 | 29 | 0 |
| outlander 3 | 37 | 26 | 26 | 0 |
| outlander 4 | 54 | 42 | 42 | 0 |
| outlander 5 | 103 | 88 | 88 | 0 |

| Style / level | Prefab | Verdict | Why |
|---|---|---|---|
| kweebec 1 | Small_Plot_House_001 | **Keep + substitute** | 48 blocked after A: Kweebec platform and shelf, cedar branches, crops. Lumberjack furniture is the same redwood family and craftable. |
| kweebec 2 | Small_Plot_House_002 | **Keep + substitute** | Best Kweebec house: 30 blocked after A, mostly branches (→ `Wood_Redwood_Beam`) and roots. |
| kweebec 3 | Normal_Plot_House_001 | **Keep + substitute** | 49 after A: branches, platform, window. |
| kweebec 4 | Normal_Plot_House_002 | **Keep + substitute** | 41 after A. Its 1 `Furniture_Kweebec_Statue` must be skipped. |
| kweebec 5 | Medium_House_002 | **Keep + substitute, or swap** | 269 after A (63 platform, 122 branches, 24 ladder). Half the build is a natural redwood (2137 tree blocks, including 769 leaves that need Shears) plus 1186 furnace-made `Rock_Basalt`. Consider a smaller top level: no fully obtainable alternative exists, but `Kweebec_Redwood_Normal_Plot_House_002` (current L4, 1814 blocks in total) is far cheaper. |
| outlander 1 | Houses_Tier0_005 | **Keep + substitute, or swap to `Outlander_Houses_Tier0_006`** | 17 after A: hide cloth roofs (→ orange cloth roof), barrels, crate, ancient torch, brazier. Tier0_006 has only 5 hard blocks in the whole prefab (2 `Cloth_Roof_Hide_Flap`, 1 ancient torch, 1 brazier, 1 ancient barrel), but its `hutOffset` would need to be re-picked. |
| outlander 2 | Houses_Tier1_001 | **Keep + substitute** | 29 after A: 17 crates, 6 redwood branches, 4 ancient barrels, 1 brazier. `Houses_Tier1_007` (15 hard in total) is slightly cleaner. |
| outlander 3 | Houses_Tier2_001 | **Keep + substitute** | 26 after A: crates, branches, barrels. `Houses_Tier2_002` is similar (25 hard). |
| outlander 4 | Houses_Tier2_003 | **Keep + substitute** | 42 after A: 31 crates, 10 barrels, 1 counter. After the crate substitution it is the cleanest large Outlander house. |
| outlander 5 | Houses_Tier3_005 | **Keep + substitute** | 88 after A: 28 spike traps, 27 fir branches, 18 crates, 6 barrels. Tier3_004 (67 hard) is the least bad Tier3; the difference is not worth re-picking. |

**Swap candidates** (whole prefab, all y, "hard" = blocking items not fixed by rule A and not crops). No house prefab of either style scores 0. Zero-score prefabs exist but are not buildings (`Kweebec_Redwood_Layout_*`, `Kweebec_Redwood_Rock_*`, `Outlander_Camps_Tier3_Mountain_Base_001`).

| Prefab | Blocks | Hard blocked | Main offenders |
|---|---:|---:|---|
| `Npc/Kweebec/Oak/Houses_Small/Kweebec_Oak_Houses_Small_001` | 1375 | 7 | Kweebec window, sign, painting, wardrobe |
| `Npc/Kweebec/Redwood/Small_Plot/House/Kweebec_Redwood_Small_Plot_House_002` (current L2) | 647 | 31 | redwood branches, roots, Kweebec window |
| `Npc/Kweebec/Redwood/Small_Plot/House/Kweebec_Redwood_Small_Plot_House_003` | 345 | 38 | Kweebec platform, branches, shelf |
| `Npc/Outlander/Houses/Tier0/Outlander_Houses_Tier0_006` | 795 | 5 | hide cloth flap, ancient torch, brazier, barrel |
| `Npc/Outlander/Houses/Tier1/Outlander_Houses_Tier1_007` | 711 | 15 | village crate, ancient barrel, faun stool |
| `Npc/Outlander/Houses/Tier2/Outlander_Houses_Tier2_002` | 1939 | 25 | crates, redwood branches, barrels |
| `Npc/Outlander/Houses/Tier3/Outlander_Houses_Tier3_004` | 7338 | 67 | fir branches, spike traps, crates |

Bottom line: the problem is a small set of **decoration families that vanilla never made craftable**: Kweebec furniture, branches and roots, barrels, hide cloth roofs, `Village_Crate`/`Counter` and the ancient/faun pieces. It is not the choice of prefab. One substitution table fixes all 10 levels, and it is needed whichever prefabs are chosen.

## 5. Implementation notes and runtime detection

### 5.1 Rule A, generic and asset-driven: request the break drop
The `Blueprint` still places the original block id (the look is unchanged); only the *requested item* changes. When `BlockType.getItem()` is not obtainable:
1. `g = blockType.getGathering()`; `p = g.getBreaking() != null ? g.getBreaking() : g.getSoft()`.
2. If `p.getItemId() != null && p.getDropListId() == null` and that item is obtainable, request it.
3. Also reverse-index `BlockPlacementSettings.get{Wall,Floor,Ceiling}PlacementOverrideBlockId()` over all items (the placing item → the placed override block). This gives `Wood_Torch_Wall` → `Furniture_Crude_Torch` even without step 2.

Rule A covers `*_Trunk_Full`, `*_Roof_Hollow` (the hollow roofs of every material drop their straight roof), `Wood_Torch_Wall` and `Rubble_*_Medium`.

### 5.2 Rule C, crops
Build `seedFor[block]` from items whose `InteractionVars.*.Interactions[].BlockTypeToPlace` names the crop block (`Seed_Place`). Request the seed; the crop grows from stage 0. Alternatively, treat `Plant_Crop_*_Block` like `Block_Spawner_Block` and skip it.

### 5.3 Rules B and D, the substitution table
Ship a data file, for example a `substitutions` block in `styles.json` or a separate `hycolony/substitutions.json`, mapping `blockId → blockId` (or → `Empty` to skip). Apply it in `HytaleBlueprintSource` right after the state normalisation. Keep the entry's rotation, since the replacements keep the shape family: branch and beam are both `VariantRotation: Pipe`; roofs map to roofs of the same family and suffix; doors map to doors. The table in section 3 is the initial content.

### 5.4 Obtainability check at runtime (`/hycolony selftest` and startup validation)
All the inputs exist as loaded asset maps, so no zip parsing is needed:

| Data | API (0.6.8) |
|---|---|
| Items, resource types | `Item.getAssetMap()`, `Item.getResourceTypes()` (`ItemResourceType.id`) |
| Recipes (item `Recipe` fields are generated into this map with id `CraftingRecipe.generateIdFromItemRecipe`) | `CraftingRecipe.getAssetMap()`: `getInput()` → `MaterialQuantity.getItemId()/getResourceTypeId()/getTagIndex()`, `getOutputs()`, `getPrimaryOutput()`, `getBenchRequirement()` (id, categories, `requiredTierLevel`) |
| Benches | `BlockType.getBench()` → `getId()`, categories, tiers; or `CraftingPlugin.getBenchRecipes(BenchType, benchId, category)` |
| Block drops | `BlockType.getGathering()` → `getBreaking()/getSoft()/getHarvest()/getPhysics()` (`getItemId()`, `getDropListId()`), `getToolData()` (`BlockToolData.getItemId()/getDropListId()`); empty entry → `blockType.getItem()` (see `BlockHarvestUtils.getDrops`) |
| Drop lists | `ItemDropList.getAssetMap().getAsset(id).getContainer().getAllDrops(list)` |
| Placement overrides | `BlockType.getPlacementSettings().get{Wall,Floor,Ceiling}PlacementOverrideBlockId()` |
| Seeds | item `InteractionVars` → `BlockTypeToPlace` |

Algorithm (the same fixed point as `classify.py`):
1. `valid benches` = `Fieldcraft` ∪ {`getBench().getId()` of every BlockType}.
2. Seeds: `obtainable` = drops of a *natural block set* ∪ all items from `Server/Drops/NPCs` drop lists ∪ barter outputs.
3. Repeat until nothing changes: add the outputs of every recipe whose bench requirement is satisfiable (bench valid, bench item obtainable, categories match) and whose inputs are all obtainable. Also add the drops of every obtainable block, including tool drops such as Shears.
4. For each styles.json prefab, load the blueprint through the real `HytaleBlueprintSource`. Apply rules A to D, then list entries whose requested item is not obtainable, and print `style/level: item xN`.

The hard part at runtime is the **natural block set**, because worldgen is not an item-level registry. Options, from cheapest to most accurate:
- (a) Ship the natural set this audit computed (`src.pkl` → a small resource list) and regenerate it per Hytale version with the script.
- (b) Approximate: blocks whose `Gathering.Breaking.GatherType` is `Rocks`, `Soils`, `Woods` or `SoftBlocks` and whose item id starts with `Rock_`, `Soil_`, `Wood_*_Trunk`, `Plant_` or `Ore_`, minus `*_Full`, `Branch` and `Roots`. This is good enough for a warning.
- (c) Walk the loaded `PrefabStore` for `Trees/`, `Plants/` and `Rock_Formations/` plus the loaded worldgen asset maps. This is heavier and only fits `selftest`, not startup.

Startup validation should warn but not fail. It should list every styles.json entry with unobtainable items after substitution, so a Hytale update that removes a recipe is caught before a builder stalls in game. `selftest` can reuse the same code and also print the per-prefab item totals (section 2).
