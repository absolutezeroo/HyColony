# SP3a research: MineColonies miner and quarry, and the Hytale 0.6.8 world (trees, ground, ladders, NPC navigation)

Date: 2026-09-26. Sources:
- MineColonies `version/main`, fetched raw. `core/...` = `src/main/java/com/minecolonies/core/...` and `api/...` = `src/main/java/com/minecolonies/api/...`.
- Blueprints from `src/main/resources/blueprints/minecolonies/colonial/infrastructure/mineshafts/`, decoded from their NBT.
- Hytale: the decompiled server under `build/vineflower/hytale-server` and `release-0.6.8-Assets.zip` (`Server/...`).

---

## A. MineColonies

### A1. Miner building: `core/colony/buildings/workerbuildings/BuildingMiner.java`

- **Class**: `extends AbstractBuildingStructureBuilder`, so the miner is a "builder" that places blueprints. Schematic name `miner`, max level 5 (`CONST_DEFAULT_MAX_BUILDING_LEVEL`).
- **Registration** (`apiimp/initializer/ModBuildingsInitializer`, `core/colony/buildings/modules/BuildingModules`). The hut carries these modules:
  - `MINER_WORK`: `MinerBuildingModule`, job miner, primary skill Strength, secondary Stamina, no work in rain, 1 worker;
  - `QUARRIER_WORK`: same as above, but for the quarrier job. **The quarrier is employed at the miner hut**;
  - `MINER_CRAFT`, `MINER_LEVELS` (`MinerLevelManagementModule`), `MINER_SETTINGS`, `MINER_GUARD_ASSIGN`, `BUILDING_RESOURCES`, `MIN_STOCK`, `STATS_MODULE`.
- **Settings** (`MINER_SETTINGS`):
  - `FILL_BLOCK`: `BlockSetting`, default **cobblestone**. It fills unsafe floor cells, fluids and mined ores;
  - `MAX_DEPTH`: `IntSetting`, default **-100**;
  - `RECIPE_MODE`;
  - `USE_SHEARS`: false.
- **Anchor tags in the hut blueprint**: `TAG_LADDER` and `TAG_COBBLE` (positioned tags). `ladderLocation` is the top of the ladder column; `cobbleLocation` is the block behind it. The vector `ladder − cobble` (a unit X/Z vector) gives the **shaft orientation** and the rotation of every structure (`getRotationFromVector`: +X→1, +Z→2, −X→3, −Z→4).
- **keepX**, the items the worker keeps. Tools are kept between level `TOOL_LEVEL_WOOD_OR_GOLD` and `getMaxEquipmentLevel()`:
  - ladder ×64, torch ×64, cobblestone ×64, fill block ×64;
  - pickaxe ×1, shovel ×1, axe ×1, shears ×1.
- **`getResourceBatchMultiplier()`**: 4 when **no quarrier** is assigned, otherwise 1. The miner then requests 4× the batch.
- **Depth limit by hut level** (`getDepthLimit`):
  - Constants: `MINING_LEVELS = {48, 16, -16, -100}` and `buildingY = ladder.y − 5`.
  - The code walks the list, keeps each level below `buildingY` and decrements the hut level for each one kept; it stops when the counter reaches 0. With a surface hut this gives **L1 → y 48, L2 → 16, L3 → −16, L4 and L5 → −100**.
  - `normalizeMaxDepth` then applies `max(worldMinY+5, limit)`. If the player changed `MAX_DEPTH`, it applies `max(limit, setting)` instead, so the setting can only make the mine **shallower**.
- **Tool tier by hut level** (`api/colony/buildings/IBuilding.getMaxEquipmentLevel`):
  - L0 → 1 (stone), L1..L4 → the hut level (L1 stone, L2 iron, L3 diamond, L4 netherite);
  - L5 → unlimited (`WOOD_HUT_LEVEL = 0`, `BASIC_TOOL_LEVEL = 1`, wood/gold = 0).
- **Recipe**: planks, a gold sceptre and a **wooden pickaxe**. The quarry recipes need an **iron pickaxe** (simple) or a **diamond pickaxe** (medium). See `src/datagen/.../recipes/*.json`.
- `JobMiner` / `JobQuarrier`: disease modifier 2, saturation factor 1.2, fire immunity through research.

### A2. Levels and nodes

#### `core/entity/ai/workers/util/MinerLevel.java`

A level stores:
- `depth` (y);
- `Map<Vec2i, MineNode> nodes`;
- a FIFO `openNodes` queue;
- `ladderNode` (SHAFT);
- the level-sign position.

**Construction of a level**. With `v = ladder − cobble`:
- `cobbleCenter = cobble − 3v`: a `LADDER_BACK` node, COMPLETED;
- `ladderCenter = cobble + 4v`: the `SHAFT` node, COMPLETED;
- the 4 neighbours of the shaft node (N/S/E/W at **7** blocks), minus the one on `cobbleCenter`: `TUNNEL` nodes, AVAILABLE, added to the open queue.

**Children on completion** (`closeNextNode(rotation, node)`), counted from the node's own rotation:
- TUNNEL: straight (1 child);
- BEND_RIGHT: +3 (1 child);
- BEND_LEFT: +1 (1 child);
- CROSS_THREE_LEFT_RIGHT: +1 and +3;
- CROSS_THREE_TOP_LEFT: 0 and +3;
- CROSS_THREE_TOP_RIGHT: 0 and +1;
- CROSSROAD: 0, +1 and +3.

Rotation index to direction: 0=E, 1=S, 2=W, 3=N. A child is skipped if the cell already has a node or if there is **fluid at (x, depth+2, z)**. A new child gets a **uniformly random style** from `SIDE_NODES` = {TUNNEL, CROSSROAD, BEND_RIGHT, BEND_LEFT, CROSS_THREE_LEFT_RIGHT, CROSS_THREE_TOP_LEFT, CROSS_THREE_TOP_RIGHT}. The node then becomes COMPLETED and leaves `openNodes`.

**Choosing the next node** (`getRandomNode(oldNode)`):
- With `builtNodes = nodes − open − 2` of **10 or fewer**, the level takes `openNodes.peek()` (breadth-first).
- Above 10, there is a **3/4** chance of `oldNode.getRandomNextNode`: a random N/S/E/W neighbour at distance 7 (the switch actually picks among 3 of the 4, a bug), with up to 3 steps back up the parents. Otherwise the level falls back to the queue.

#### `core/entity/ai/workers/util/MineNode.java`

- Coordinates `x`, `z` (the node centre), a `parent` (Vec2i), a style, a status, an optional rotation.
- `DISTANCE_TO_NEXT_NODE = 7`.
- **NodeStatus**: `AVAILABLE`, `IN_PROGRESS`, `COMPLETED`.
- **NodeType** and the blueprint it uses:

| NodeType | Blueprint |
|---|---|
| SHAFT | `infrastructure/mineshafts/minermainshaft` |
| LADDER_BACK | none |
| TUNNEL | `infrastructure/mineshafts/minerx2top` |
| CROSSROAD | `infrastructure/mineshafts/minerx4` |
| BEND_RIGHT | `infrastructure/mineshafts/minerx2right` |
| BEND_LEFT | `infrastructure/mineshafts/minerx2left` |
| CROSS_THREE_LEFT_RIGHT | `infrastructure/mineshafts/minerx3leftright` |
| CROSS_THREE_TOP_LEFT | `infrastructure/mineshafts/minerx3topleft` |
| CROSS_THREE_TOP_RIGHT | `infrastructure/mineshafts/minerx3topright` |
| UNDEFINED | none |

#### `core/colony/buildings/modules/MinerLevelManagementModule.java`

- Holds `levels` (a list, appended in order, so deeper levels come later), `currentLevel`, `activeNode`, `oldNode` and `startingLevelShaft`.
- `getStartingLevelShaft()`: the stored value while no level exists, otherwise `lastLevel.depth − 6`. **So there is a new level every 6 blocks of depth.**
- `repairLevel(i)` re-issues the `minermainshaft` work order at that level.

#### Mine blueprints (colonial style, decoded)

- `minermainshaft`, **9×4×9**: the level platform around the shaft.
  - Floor of spruce slabs; four log pillars with a fence frame; 4 torches.
  - The 4-block ladder column on one edge, with cobblestone behind it; a wall sign that becomes the level sign.
  - The border is `blocksolidsubstitution`.
- `minerx2top` (tunnel), **7×5×7**:
  - one open corridor, 5 wide and 2 high;
  - a cobblestone/fence support frame in the middle, stair caps and a wall torch;
  - a cobblestone line on the floor;
  - everything else is `blocksolidsubstitution`.
- `minerx4` (crossroad) and `minerx2right` (bend): 7×5×7 as well, with log and fence frames, cobblestone stairs and 2–3 wall torches.
- Structurize placeholders:
  - `blocksolidsubstitution`: keep the existing block if it is solid, otherwise place the fill block;
  - `blocksubstitution`: do not touch;
  - air: mine.

### A3. Miner AI: `core/entity/ai/workers/production/EntityAIStructureMiner.java`

`extends AbstractEntityAIStructureWithWorkOrder`: node and level mining go through the normal builder pipeline (LOAD_STRUCTURE, then BUILDING_STEP / MINE_BLOCK). Only the shaft is hand-coded.

**Constants**

| Name | Value |
|---|---|
| NODE_DISTANCE | 7 |
| SHAFT_RADIUS | 3 (7×7 shaft) |
| SAFE_CHECK_RANGE | 5 (11×11 floor check) |
| SHAFT_BASE_DEPTH | 8 |
| LIQUID_CHECK_RANGE | 5 |
| OTHER_SIDE_OF_SHAFT | 6 |
| COBBLE_REQUEST_BATCHES | 32 |
| LADDER_REQUEST_BATCHES | 10 |
| MAX_BLOCKS_MINED | 64 (dump after `hutLevel × 64` actions) |

**States**

- `startWorkingAtOwnBuilding`: if the hut has no ladder or cobble tag, it raises the "invalid mineshaft" blocking interaction. It resumes an existing `WorkOrderMiner`, otherwise goes to PREPARING and then `MINER_CHECK_MINESHAFT`.
- **`checkMineShaft`**: with `lastLadder` = the y of the lowest ladder (`WorkerUtil.getLastLadder`):
  - above the depth limit → `MINER_MINING_SHAFT`;
  - otherwise → `MINER_MINING_NODE`;
  - if there is no level at all, "needs better hut".

  **The shaft is dug all the way to the hut's depth limit first. Tunnels start only after that, on the deepest level.** An upgraded hut resumes the shaft.
- **`doShaftMining`** (`getNextBlockInShaftToMine`): one layer at a time, at `y = lastLadder`, over the **7×7** area centred on `ladder + 3v`.
  1. It first replaces every fluid in a **11×11** ring (radius 3+2) with the fill block.
  2. It then mines the nearest non-air block, iterating from +x to −x so that it ends against a wall. The distance cost is `d²(ladder) + d²(last)²`.
  3. The standing position is an adjacent air cell closest to the ladder.
  4. When the layer is empty, it calls `advanceLadder`.
- **`advanceLadder`**:
  1. Requests cobblestone (32) and ladders (10).
  2. If the ladder is damaged, goes to `MINER_REPAIRING_LADDER`.
  3. **Secures the floor**: on the 11×11 area at `lastLadder − 2` around the shaft centre (`secureBlock`), every cell that does not block motion, holds a fluid **or is an ore** is mined and replaced with the fill block (torches are allowed).
  4. `startingLevelShaft` is initialised to `nextCobble.y − 4` on the first descent. If `nextCobble.y < startingLevelShaft` → `MINER_BUILDING_SHAFT`.
  5. Otherwise it mines and places **cobblestone (netherrack in the Nether) at `cobble.xz`** and a **ladder at `ladder.xz`**, both at `lastLadder − 1`. The ladder faces the cobblestone.
- **`doShaftBuilding`**: `initStructure(null, ...)` creates a `WorkOrderMiner` for `minermainshaft` at `(ladder + 3v, lastLadder + 2)` with the shaft rotation, then `LOAD_STRUCTURE`.
  On completion (`executeSpecificCompleteActions`), the file name contains `minermainshaft`, so a `MinerLevel` is added at `depth = workOrder.y` (if none exists at that depth). The level sign is found in the blueprint (`WorkerUtil.findFirstLevelSign`) and updated.
- **`executeNodeMining` / `searchANodeToMine`**:
  - It takes `module.getActiveNode()`. If the level is exhausted, it moves to `levelId − 1` (the next shallower level).
  - Rotation = the direction from the parent node to the node (+X 0, +Z 1, −X 2, −Z 3).
  - If the parent is not the shaft and is not COMPLETED, it goes back to the parent first.
  - The miner walks to the **parent centre** at level depth. If the parent is the shaft, it walks to `ladder + 6v` instead (`OTHER_SIDE_OF_SHAFT`).
  - `executeStructurePlacement`:
    1. Marks the node IN_PROGRESS.
    2. Replaces every fluid **source** in a 9×9 box, from `depth − 1` to `depth + 5`, with the fill block.
    3. Creates the `WorkOrderMiner` for the node blueprint at `(node.x, depth, node.z)`.
  - On completion: `closeNextNode`, `activeNode = null`, `oldNode = worked node`, and the sign is updated.
- **`doMining`**:
  - If the target is not an ore, it first mines any **ore adjacent** (6 faces) to it.
  - After breaking, it keeps following adjacent ores (vein following), so a whole ore vein touching the tunnel is taken.
  - `shouldSilkTouchBlock` = `isOre`: ores are harvested as ore blocks.
  - `shallReplaceSolidSubstitutionBlock` = `isOre`: ores in "keep solid" cells are mined anyway and replaced with the fill block.
- **`triggerMinedBlock`** (lucky ores):
  - It applies when the block is in `ModTags.oreChanceBlocks` (stone-like blocks).
  - Chance: `rand×100 ≤ luckyBlockChance (config, default 1) × (1 + MORE_ORES research)`.
  - A hit rolls the loot table `miner/lucky_ore{hutLevel}`:

    | Hut level | Loot (weight) |
    |---|---|
    | L1 | coal 64, copper 48 |
    | L2 | + iron 32, gold 16 |
    | L3 | + redstone 8, lapis 4 |
    | L4/L5 | + diamond 2, emerald |

  - Every mined block also counts in the colony statistics (ORES_MINED, BLOCKS_MINED).
- `checkIfCanceled` drops a work order whose path contains "quarry". That guards against the shared-building conflict with the quarrier.

**Timing, skills and XP** (`core/entity/ai/workers/AbstractEntityAIInteract`):
- Break speed uses the primary skill (Strength); place speed uses the secondary skill (Stamina).
- Delay = `500 × 0.85^(skill/2) × hardness / toolSpeed × (1 − BLOCK_BREAK_SPEED research)`.
- XP = **0.05 per mined block** (`XP_PER_BLOCK`), plus the builder's structure-completion XP for levels and nodes.

**What the miner requests**:
- ladders (batches of 10) and cobblestone or the fill block (batches of 32), through `checkIfRequestForItemExistOrCreate`;
- the node and level blueprint resources through the builder resource pipeline: cobblestone, spruce logs, slabs, fences, cobblestone stairs, torches, a sign;
- the tools pickaxe, shovel, axe and shears, at a tier within the hut's `getMaxEquipmentLevel`. `holdEfficientTool` requests the right tool per block.

### A4. Quarry

**Buildings**: `simplequarry` and `mediumquarry`. `largequarry` is **commented out** in `ModBuildingsInitializer`.
- Both are `DefaultBuildingInstance`s with **max level 1** and a single `QuarryModule`: `new QuarryModule(32)` for simple, `(64)` for medium.
- `QuarryModule`:
  - 1 assigned citizen, job `quarrier`;
  - auto-hires a quarrier (hired at a miner hut) who has no quarry yet;
  - holds an `isFinished` flag;
  - `getAdditionalCorners()` = `(0, height, 0)`, which extends the footprint by 32 or 64 blocks, so nothing else can be built over the pit;
  - adds a `StationRequestResolver`.
- `JobQuarrier`: `findQuarry()` returns the quarry building whose `QuarryModule` has this citizen. `IJobWithExternalWorkStations`.

**Pit blueprints** (colonial):
- `simplequarry1`, the surface hut: 18×9×25 (barrels, a small shelter);
- **`simplequarryshaft1`, the pit: 18×24×25**. Inside a 16×16 footprint, the air (to dig) goes from about 230 cells per layer at the top down to 15 at the bottom (a funnel; ≈3 200 cells in total). Each layer keeps **about 6 cobblestone blocks and 1 cobblestone stair, forming a spiral staircase down the wall**, plus fences, fence gates, torches and chains.
- `mediumquarryshaft1`: 34×39×58, about 22 000 air cells, with deepslate walls, rails, logs and ≈120 wall torches.
- Most of the rest is `blocksubstitution` (do not touch) and `blocksolidsubstitution` (keep solid or fill).
- The shaft path is the quarry's blueprint path with `1.blueprint` replaced by `shaft1.blueprint`, or a `shaft=` tag at the hut origin.

**AI**: `core/entity/ai/workers/production/EntityAIQuarrier.java`, `extends AbstractEntityAIStructureWithWorkOrder<JobQuarrier, BuildingMiner>`.

1. **Start**. Without a quarry → "no quarry" blocking interaction. A finished quarry → "finished quarry" interaction. Otherwise the quarrier walks to the quarry.
2. **`loadRequirements`**: creates a `WorkOrderMiner` for the shaft blueprint at **`quarry.pos.below(2)`**, with the quarry's rotation, claimed by the miner hut.
3. **Structure handler**: stages `{BUILD_SOLID, DECORATE, CLEAR}` with a `LayerBlueprintIterator`, starting at the **top layer** (`sizeY − 1`).
4. **Per layer** (`goToNextStage`):
   - **BUILD_SOLID** places the solid blocks of layer L (walls, stair steps). Non-solid and deco blocks are skipped.
   - **DECORATE** works on layer **L+1**: torches, fences, gates and rails placed on the blocks just built. It is skipped on the top layer.
   - **CLEAR** works on layer L: it mines every cell that differs from the blueprint.
     - Cells that already hold the correct block are skipped (`skipClearing`).
     - Ores in solid-substitution cells are mined (`shallReplaceSolidSubstitutionBlock`).
     - Fluids next to the target are replaced with the fill block (`doMining`).
   - Then it moves to layer **L−1**.
5. **End**. The dig ends at layer 0, or when CLEAR reaches the world's minimum build height. `executeSpecificCompleteActions` then calls `QuarryModule.setFinished()`. **A quarry is a one-shot dig**; the building stays "finished".
6. **Material requests** (`requestMaterials`) are layer by layer too: the SOLID requirements of layer L, then the DECO requirements of L+1.
7. **Other rules**:
   - Drops are **dumped into the quarry building** (`getBuildingToDump`), not into the miner hut.
   - Dump after `hutLevel × 128` actions.
   - Working position: a random position within 5 of the block, with a reach of 10.
   - XP and the timing formula are the same as the miner's (0.05 per block).
   - `getSolidSubstitution` = the fill block.

**Difference from the builder's REMOVE and CLEAR**:
- The builder's CLEAR empties the **whole** footprint top-down, and **ore drops are destroyed**. It then builds bottom-up.
- REMOVE deconstructs placed blocks.
- The quarry interleaves **per layer, top-down**: first place the supports for this layer, then decorate the layer above, then dig this layer. It keeps every drop, ores included. The pit is carved so that the staircase and walls always exist before the ground under them is dug.

### A5. Complexity for a port, and progression

| | Miner | Quarry |
|---|---|---|
| New state | level list, node graph (7-grid, 10 styles, random growth), active/old node, starting shaft y | `isFinished`, the current layer |
| New AI code | shaft digger (7×7 layer, floor securing 11×11, fluids), ladder/cobble column, level trigger, node selection, parent back-tracking, ore vein following, lucky ores | a layer iterator over the existing builder: 3 stages per layer in a different order, plus request-by-layer |
| Assets | 1 platform + 7 node schematics (+ a ladder column) | 1 pit schematic (+ a surface hut) |
| Reuse of our builder | nodes and levels = builder work orders; the shaft is custom | nearly all of it: work order + blueprint + CLEAR/BUILD, with a new iteration order |
| Estimate | large (≈1 000 MC lines, many edge cases) | small to medium (≈700 MC lines, mostly builder plumbing we already have) |

**Player progression in MineColonies**:
- The **miner hut** is cheap (wooden pickaxe recipe) and is usually the 2nd to 4th hut, after the builder, then the lumberjack and the miner, with the warehouse and couriers next. It is **the** early source of cobblestone: the 7×7 shaft yields ≈45 blocks per layer, plus the tunnels, plus ores.
- The quarry needs an iron pickaxe to craft. It is a mid-game block that gives a big one-shot pile of stone. Its quarrier also works out of a miner hut.
- So MineColonies players get **early stone from the miner**, not from the quarry.

---

## B. Hytale 0.6.8

### B1. Trees

**Block keys** (per species `S` ∈ Oak, Birch, Ash, Beech, Aspen, Cedar, Fir, Maple, Redwood, Palm, ...; `Server/Item/Items/Wood/<S>/`):
- `Wood_S_Trunk`: the log. Cube, `VariantRotation: Pipe` (it can lie horizontally in branches), tags `Type=[Wood, Sap_Source, Trunk]` and `Family=[S]`, resource types `Wood_S`, `Wood_Trunk`, `Wood_All`, `Fuel`...;
- `Wood_S_Trunk_Full`: `Parent: Wood_S_Trunk`, a different top texture. It is the inner or bark-all-round log;
- `Wood_S_Trunk_Half` and `Wood_S_Trunk_Stairs`: building shapes, not found in tree prefabs;
- `Wood_S_Branch_Short`, `_Long`, `_Corner`: model blocks with tag `Type=[Wood, Tree, Branch]`;
- `Wood_S_Roots`: `Material: Empty`, a soft drop;
- `Plant_Leaves_S`: `Group: Leaves`, tag `Family=Leaves`.

**Structure**: trees are **prefabs** (`Server/Prefabs/Trees/<Species>/Stage_N/*.prefab.json`, 1 126 files, about 90 families including `*_Stumps` and `*_Logs`).
- Oak has Stage_00 to Stage_7.
- `Oak_Stage3_001`: 209 blocks, footprint x/z ±5, y −3..15. 152 leaves, 17 trunk and 9 trunk_full blocks, 31 branches. The trunk is a single column with a couple of offsets; the **trunk goes 3 blocks below the anchor** (into the soil).
- `Oak_Stage5_*`: 800–1 100 blocks, about 21×26×21. **Multi-block trunks** (a 3×3 to 5×5 base) of 150+ `Trunk_Full` blocks, trunk cells down to y −5, many branches, 500–750 leaves.

  **A tree is therefore not "a column of logs" as in Minecraft.** A lumberjack must flood-fill connected `Wood_<S>_*` blocks of the same `Family` tag.

**Placed by worldgen**: yes, as prefabs.
- `Server/HytaleGenerator/Assignments/*/…Trees.json` references `"Path": "Trees/Oak/Stage_5"` with weights.
- The legacy `Server/World/Default/Zones/*/Masks/Trees.json` lists which blocks a tree prefab may overwrite.

**Saplings**:
- Items `Plant_Sapling_<S>` (29 species, `Server/Item/Items/Plant/`). Every species has `Parent: Plant_Sapling_Oak`.
- The block is a model with `Support.Down = Type=Soil`, a `BlockEntity.Components.FarmingBlock` and `Farming.Stages.Default`:
  1. the sapling block itself;
  2. prefab `Trees/Oak/Stage_00/*`;
  3. prefab `Stage_0/*`;
  4. prefab `Stage_1/*`;
  5. prefab `Stage_2/*`;
  6. prefab `Stage_3/*`, the final stage, with no duration.

  Each timed stage lasts **40 000–60 000 game seconds**. Each prefab stage has `ReplaceMaskTags: [Soil]` and `TolerateObstructionsBelowY: -1`. `ActiveGrowthModifiers: Fertilizer, Water, LightLevel`.
- **Time**: durations are in game time (`FarmingSystems` / `FarmingUtil`, `WorldTimeResource`). A default day is 1728 s of daylight plus 1152 s of night of real time = 86 400 game seconds, so **30 game s per real s**. A stage lasts ≈22–33 real minutes, so a sapling reaches Stage_3 in **≈1.9–2.8 real hours**, before modifiers.
- **Growth**: each prefab stage replaces the previous one. It **waits while the volume is obstructed** (`PrefabFarmingStageData`: any non-air, non-`Soil` block obstructs).
- **Planting so that it grows**: set `Plant_Sapling_<S>` on a `Type=Soil` block with `BlockOperations.setBlock(..., settings)` **without bit 2**. `BlockOperations.setBlock` clones `blockType.getBlockEntity()` (the FarmingBlock) unless `(settings & 2) != 0`. Our `HytaleWorldBlocks.placeBlock` uses `SetBlockSettings.NONE`, so it already works. `FarmingSystems.OnFarmBlockAdded` then starts the stage timer.
- **Getting saplings**:
  - No drop list in 0.6.8 yields a sapling or tree seed. `Tree_Leaves` only gives `Ingredient_Fibre`, and no `Server/Drops` file references `Plant_Sapling_*` or `Plant_Seeds_<tree>`.
  - The sapling recipe is **15 `Ingredient_Life_Essence` at the Farmingbench, tier 4**.
  - A `Plant_Seeds_<S>` item places the sapling (`Seed_Place`, `BlockTypeToPlace`), but nothing drops it.
  - Breaking a sapling returns the sapling.

  So MineColonies' "leaves drop saplings, the lumberjack replants" loop has **no vanilla source**. We must decide on one: for example, the lumberjack gets a sapling per felled tree as a colony rule, or saplings are requested through the request system.

**Leaf "decay" is block support physics, not random ticks.** (`builtin/blockphysics/BlockPhysicsUtil`, `BlockPhysicsSystems`.)
- Leaves: `Support.BlockSides = {TagId Type=Trunk, TagId Type=Branch, FaceType Branch; Family=Leaves counts as "Ignored"}` and `MaxSupportDistance: 4`.
- Trunk: `Support.Down = Full face` (`AllowSupportPropagation: false`), horizontal `Type=Trunk` "Ignored", `MaxSupportDistance: 5`, `IgnoreSupportWhenPlaced: true`.
- Branches: `Support.Up/Down` with a `Branch` face type, `MaxSupportDistance: 6`.
- When a block's support distance reaches 0, `applyBlockPhysics` removes it according to `SupportDropType`; the default is `BREAK`, which drops through `BlockHarvestUtils.naturallyRemoveBlockByPhysics`. That drop uses the `Gathering.Physics` drop first, then Breaking, then Soft, and spawns **item entities in the world**.
- **Consequence**: cutting a trunk makes the unsupported trunk, branch and leaf blocks break in a cascade and scatter drops on the ground. Our `breakBlock` only suppresses drops for the block it breaks (`NO_DROP_ITEMS`), not for the cascade. **[in-game]** Check the cascade on a worldgen tree.
- For a lumberjack, fell **top-down**, or leaves-and-branches first then trunks, removing every connected block ourselves, or pick up item entities afterwards (MineColonies' lumberjack does pick up items).

**Gather types**:
- Trunks and branches: `Breaking.GatherType = "Woods"`, no quality, so there is **no tier gate for wood**.
- Hatchet `Woods` power: Crude 0.15, Wood 0.2, Copper 0.2, Iron 0.3, Thorium/Cobalt/Adamantite/Mithril/Onyxium 0.5. Unarmed 0.03. A block has 1.0 health, so a Crude hatchet needs 7 hits and bare hands 34.
- Leaves: `Soft` (any tool, one hit); Shears are listed in `Gathering.Tools`.
- `SoftWoods` is only used by 2 Hyspace deco blocks: irrelevant.

**Drops**:

| Block | Breaking | Physics |
|---|---|---|
| `Wood_S_Trunk` | `ItemId Wood_S_Trunk` ×1 | the same |
| `Wood_S_Trunk_Full` | inherits `Wood_S_Trunk` | the same |
| `Wood_S_Branch_*` | `DropList Wood_Branch`: 50% (0–2 sticks + 0–1 tree sap), 50% (1 stick) | `Wood_Branch` |
| `Wood_S_Roots` | Soft `Wood_Oak_Branch_Physics` | — |
| `Plant_Leaves_S` | Soft `Tree_Leaves` = fibre, weight 50 | `Tree_Leaves_Physics` = fibre, weight 2. Palm leaves drop 1–5 `Wood_Palm_Trunk` |

A trunk also has a tool state: Scraper → `Stripped`, dropping `Bark` (1–2 `Ingredient_Tree_Bark`).

### B2. Ground and ores

**World and layers**:
- World height 320 (`math/util/ChunkUtil.HEIGHT`, `MIN_Y = 0`).
- Bedrock (`Rock_Bedrock`, unbreakable) fills y 0 up to 5–10 (`Zones/Layers/Bedrock.json`).
- In the legacy zone generator (`Server/World/Default/Zones`), each biome's `Layers.Default` is the bulk rock under a thin soil cover:
  - Zone 1 (starting zone): **`Rock_Stone`**, with soil layers `Soil_Grass`/`Soil_Dirt`/`Soil_Gravel`/`Soil_Pathway`... and pockets of `Rock_Chalk`/`Rock_Marble`;
  - Zone 2: `Rock_Sandstone`;
  - Zone 3: `Rock_Shale`/`Rock_Basalt`;
  - Zone 4: volcanic rock.
- Water (sea level) is at **y 114**, and the Zone-1 terrain is about y 115–125. So there are ≈100 blocks of rock between the surface and bedrock. Minecraft has ≈127 above −64.
- Rock drops:
  - `Rock_Stone` → **`Rock_Stone_Cobble`**, `Rock_Shale` → `Rock_Shale_Cobble`, and so on;
  - `Rock_Chalk` drops itself; `Soil_Grass` → `Soil_Dirt`;
  - `Rock_Basalt` needs quality 1 (any pickaxe); other rocks need quality 0.

**Ores** (`Server/Item/Items/Ore/*`, `Server/World/Default/Ores/Zone*`): an ore block is `Ore_<Metal>_<Host>` and drops **`Ore_<Metal>` plus the host cobblestone**. `*_Cracked` variants have a 20% ore chance and gather type Rocks/Soils.

| Ore | Hosts | Zones, column height (legacy generator) |
|---|---|---|
| Copper | Stone, Sandstone, Shale | Zone 1: y 0–55..118 in stone/basalt/marble. Zones 0/2/3: 0–80..150 |
| Iron | Stone, Basalt(+Cracked), Sandstone, Shale, Slate, Volcanic | y 0–80 (Zone 4: 0–150). Zone 1 fills basalt pockets |
| Gold | Stone, Basalt, Calcite, Sandstone, Shale, Volcanic | calcite pockets, height factor around y 102–110 |
| Silver | Stone, Basalt, Sandstone, Shale, Slate, Volcanic | height factor 150–160 (high, in mountains) |
| Thorium | Mud(+Cracked), Sandstone | Zone 2, y 0–25..120 |
| Cobalt | Shale, Slate(+Cracked) | Zone 3, y 0–25..160 |
| Adamantite | Magma(+Cracked) | Zone 4 |
| Mithril | Stone | — |

The new `HytaleGenerator` also has cave-node ore veins (`Zones/*/Cave/Ores/*.node.json`). Treat these numbers as indicative.

**Tool gating** (see the "Tier rule" in `plugin-b-api.md`: `spec.getQuality() < breaking.getQuality()` = no damage):
- Ores use a **gather type per metal** (`OreCopper`, `OreIron`, `OreSilver`, `OreGold`, `OreThorium`, `OreCobalt`, `OreAdamantite`) with **quality 0 for all except `Ore_Adamantite_Magma` (4) and `Ore_Mithril_Stone` (Rocks, 5)**.
- Every pickaxe has a spec for every ore gather type. So **copper, iron, silver, gold, thorium and cobalt are not tier-gated**; lower tiers are only much slower:
  - wood pickaxe OreIron 0.071 → 15 hits, Rocks 0.1 → 10 hits;
  - iron pickaxe OreIron 0.25 → 4 hits.
- Pickaxe `Rocks` quality: Wood 1, Crude 1, Scrap 1, Copper 2, Iron 3, Thorium/Cobalt 4, Adamantite 5, Mithril/Onyxium 6.
- `OreAdamantite` quality 4 is only on the Thorium, Cobalt, Adamantite, Mithril and Onyxium pickaxes.
- Unarmed: Rocks 0.035 (≈29 hits), Ore* 0.001.
- **Mapping MineColonies' "tool level by hut level"**: use the `Rocks` quality ladder (1 crude/wood, 2 copper, 3 iron, 4 thorium/cobalt, 5 adamantite, 6 mithril). There is no hard "iron needs stone pickaxe" rule to port, apart from adamantite and mithril.

### B3. Ladders and NPC navigation

- **Ladder blocks**: `Furniture_<Set>_Ladder`, 17 sets: Crude, Village, Lumberjack, Kweebec, Ancient, Desert, Feran, Jungle, Tavern, Frozen_Castle, Human_Ruins, Scarak_Hive, the Temple_* sets...
  - The basic one is **`Furniture_Crude_Ladder`**: 2 per `Wood_Lightwood_Softwood` resource at the Builders bench (StructuralCrafting/Ladder).
  - Block settings: `Material: Solid`, `HitboxType: Ladder`, `VariantRotation: NESW`, `PlacementSettings.RotationMode: BlockNormal`, **`MovementSettings.IsClimbable: true`**, gather type Woods.
- **NPCs cannot climb ladders**:
  - `BlockMovementSettings.isClimbable` is only copied into the client packet, for player movement. The NPC code never reads it.
  - `MotionControllerWalk.isClimbable(...)` only means "may step onto" (no damage, not in the `Fence` block set).
  - The walk controller's "Climb" is a **ledge step-up** of at most `MaxClimbHeight` (default **1.3**) plus an ascent animation type (`Walk/Jump/Climb/Fly`).
  - The only other vertical mode is `MotionControllerFly`/`Dive` (flying or swimming roles).
- **Drops**: `MaxDropHeight` defaults to **3.0** (+3 when the DROP constraint is relaxed). The path finder will not plan a fall deeper than that. Our `HyColony_Citizen` role sets no override, so it uses 1.3 and 3.
- **Path finding** (`hytale-api-spike.md`): A* in `BodyMotionFind`/`AStarBase`, probing with the motion controller, so it only walks and steps. `MaxPathLength` 200, `MaxOpenNodes` 200, `MaxTotalNodes` 900. **It never digs**; the AI must break blocks itself, as MineColonies does.
- **Walkable vertical access**:
  - a staircase of **full blocks**: 1 up per 1 forward, within 1.3;
  - `*_Stairs` blocks;
  - slabs;
  - each step needs 2 blocks of headroom.

  A 1×1 ladder column is **not** walkable; a 1-wide spiral or straight stair is.
- Citizens are `Invulnerable: true` in our role, so a fall does no damage, but the path finder still refuses drops over 3.

---

## C. Recommendation

1. **Port the quarry first** (simple quarry only; keep the medium quarry for later; the large quarry is disabled in MineColonies anyway).
   - It is mostly our existing builder: a work order, a pit prefab and our CLEAR/BUILD primitives. The new parts are a top-down per-layer loop (BUILD_SOLID(L) → DECORATE(L+1) → CLEAR(L) → L−1), request-by-layer, "finished" and dump-to-quarry.
   - Its blueprint **already uses a spiral staircase of full blocks and stairs**, which Hytale NPCs can walk. Ladders are not needed.
   - It keeps the MineColonies rules and constants: 1 quarrier at the miner hut, `below(2)` anchor, max level 1, footprint +32, dump every `hutLevel × 128` actions, 0.05 XP per block, ores mined and fill for fluids.
   - It is a **deviation from MineColonies progression** (there the quarry is mid-game and the miner is the early stone source). This is acceptable for SP3a, whose goal is a self-sufficient construction colony: the simple pit yields ≈3 200 blocks of `Rock_Stone_Cobble` plus ores, enough for the early huts. Put a requirement on the quarry (for example miner hut level 1 and a crude pickaxe) rather than MineColonies' iron-pickaxe recipe, and document the deviation.
2. **The miner comes next (SP3b)**, with these Hytale adaptations:
   - **The ladder column becomes a walkable descent**. Recommended: a **spiral stair inside the 7×7 shaft**, leaving one step block unmined per layer and advancing one ring cell per layer. It is the same pattern as the quarry pit, so both share one "stair spiral" helper. The ladder and cobblestone "column" requests become cobblestone and stair requests. Keep `COBBLE_REQUEST_BATCHES 32`; drop `LADDER_REQUEST_BATCHES`, or keep ladders as decoration only. Teleporting a citizen between levels is the non-faithful fallback.
   - **Depth levels**: MineColonies' `{48, 16, −16, −100}` assumes y −64..320 with sea level 63. Rescale to Hytale (sea level 114, bedrock ≤10): for example `{sea−15, sea−47, sea−79, bedrock+5}` ≈ `{99, 67, 35, 15}`, keeping "a level every 6 blocks", `worldMin+5` → `bedrock max (10)+5`, and `MAX_DEPTH` shallower-only.
   - **Node schematics** must be authored as Hytale prefabs. MineColonies' `.blueprint` files use Minecraft blocks and Structurize placeholders:
     - prefab cells that are **absent** = keep (≈ `blocksubstitution`);
     - `Empty` = mine;
     - `blocksolidsubstitution` has no prefab equivalent. Implement it in code: if the cell is not solid, holds a fluid or is an ore, mine it and place the fill block (`Rock_Stone_Cobble` by default).
   - **Ores**: "is ore" = `breaking.getGatherType().startsWith("Ore")` or an `Ore_*` id (26 blocks). Vein following (6 neighbours) and "silk touch" map to taking the `Ore_<Metal>` drop list as is.
   - **Lucky ores**: there is no coal, redstone, lapis, diamond or emerald. Re-table per hut level with Hytale ores (for example L1 copper; L2 + iron; L3 + silver and gold; L4/L5 + thorium or cobalt), 1% base chance on `Rock_*` host blocks.
   - **Tool tier by hut level** → the pickaxe `Rocks` quality ladder (L1 crude/wood 1, L2 copper 2, L3 iron 3, L4 thorium/cobalt 4, L5 any).
   - Keep the fluid rules (`Water_Source` → fill). The generator masks show water inside ore pockets.
3. **Lumberjack** (same sub-project):
   - A tree = a flood-fill of connected `Wood_<S>_Trunk|Trunk_Full|Branch_*|Roots` blocks sharing the `Family` tag, plus the `Plant_Leaves_<S>` blocks around them. Trees are multi-block and go 3–5 blocks below the surface anchor.
   - Fell top-down, or break branches and leaves first, so that the support-physics cascade does not scatter item entities; otherwise add item pickup.
   - Replanting: `Plant_Sapling_<S>` on `Type=Soil` via `setBlock` with settings NONE. Growth takes ≈2–3 real hours to Stage_3 and needs a clear volume.
   - There is **no vanilla sapling drop**, so choose a colony rule (for example one sapling returned per felled tree) and document it as a deviation.
4. **In-game checks before coding**:
   - the physics cascade when a worldgen trunk is removed through our adapter;
   - that citizens walk a 1-wide full-block spiral down 20+ layers with the default 1.3 step and 3 drop;
   - that a sapling placed through `HytaleWorldBlocks` grows (look for a FarmingBlock entity on the block).
