# SP3b research: farmer, farm fields, scarecrow (MineColonies `version/main`)

Source: `ldtteam/minecolonies`, branch `version/main`, commit `6b4e03e` (`6b4e03e337fcb886b4ab8cbe21f9b6731818769d`, 2026-09-28). Paths are shortened:
`api/…` = `src/main/java/com/minecolonies/api/…`, `core/…` = `src/main/java/com/minecolonies/core/…`.
Ticks: 20 ticks = 1 s. "Delay" means the tick interval of an `AITarget` in the `TickRateStateMachine`.
The generic worker rules (dump, pickup, `checkForToolOrWeapon`, max equipment level, rain) are in `sp3a-mc-logistics-lumberjack.md` § 0 and are not repeated here.
Vanilla Minecraft behaviour that the code calls but that is not in the MC repo is marked **[vanilla, not verified here]**.

Files:
- `core/colony/buildings/workerbuildings/BuildingFarmer.java` (building, `FarmerFieldsModule`, `FarmerFieldsModuleView`, `CraftingModule`)
- `core/colony/jobs/JobFarmer.java`
- `core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java`
- `core/colony/buildingextensions/{FarmField,AbstractBuildingExtensionModule}.java`
- `core/colony/buildings/modules/BuildingExtensionsModule.java`
- `core/colony/managers/RegisteredStructureManager.java` (colony-level extension list)
- `core/blocks/{BlockScarecrow,MinecoloniesCropBlock,MinecoloniesFarmland}.java`, `core/tileentities/TileEntityScarecrow.java`
- `core/network/messages/server/colony/building/fields/{AssignFieldMessage,AssignmentModeMessage,FarmFieldPlotResizeMessage,FarmFieldRegistrationMessage,FarmFieldUpdateSeedMessage}.java`
- `core/client/gui/containers/WindowField.java` + `gui/windowfield.xml`; `core/client/gui/modules/building/FarmFieldsModuleWindow.java` + `gui/layouthuts/layoutfarmfields.xml`; `core/colony/buildings/moduleviews/FieldsModuleView.java`
- `core/colony/buildings/modules/BuildingModules.java`, `apiimp/initializer/ModBuildingsInitializer.java`
- `core/generation/defaults/{DefaultResearchProvider,DefaultItemTagsProvider,DefaultRecipeProvider}.java`, `core/generation/defaults/workers/DefaultFarmerCraftingProvider.java`

---

## 1. Building (`BuildingFarmer`)

### 1.1 Registration and modules
- Schematic `"farmer"`, `MAX_BUILDING_LEVEL = 5`, view `EmptyView`, hut block `blockHutFarmer` (en_us name "Farm"). Hut recipe: `registerHutRecipe1(…, WOODEN_HOE)` and `registerHutRecipe1x2(…, STONE_HOE, "stone")` (`DefaultRecipeProvider`).
- `canBeGathered() = true` ("the farmer both gathers and crafts things now, like the lumberjack").
- Modules, in registration order (`ModBuildingsInitializer`, `ModBuildings.farmer`):

  | Producer | Module | Content |
  |---|---|---|
  | `FARMER_WORK` (`"farmer_work"`) | `BuildingFarmer.CraftingModule` / `CraftingModuleView` | player-taught crafting recipes (see 1.6). **Note the names are swapped**: `FARMER_WORK` is the crafting module |
  | `FARMER_CRAFT` (`"farmer_craft"`) | `CraftingWorkerBuildingModule(farmer, primary=Stamina, secondary=Athletics, canWorkingDuringRain=false, size=b->1)` / `WorkerBuildingModuleView` | the worker slot: **1 farmer per hut**, no work in rain. Crafting-speed and recipe-improvement skills default to primary/secondary |
  | `FARMER_FIELDS` (`"farmer_fields"`) | `BuildingFarmer.FarmerFieldsModule` / `FarmerFieldsModuleView` | field assignment (1.3) |
  | `FARMER_SETTINGS` (`"farmer_settings"`) | `SettingsModule` with `FERTILIZE = BoolSetting(true)` and `RECIPE_MODE = CrafterRecipeSetting` | settings (1.4) |
  | `CRAFT_TASK_VIEW` | | crafting task list |
  | `MIN_STOCK` | | minimum stock |
  | `STATS_MODULE` | | statistics (1.7) |

  (`core/colony/buildings/modules/BuildingModules.java` lines 122-132.)
- `JobFarmer extends AbstractJobCrafter<EntityAIWorkFarmer, JobFarmer>`, model `FARMER_ID`, `getSaturationFactor() = 1.2`.

### 1.2 Level rules
| Level | Effect | Source |
|---|---|---|
| 0 | `prepareForFarming` sets JobStatus STUCK and stays in PREPARING while `building.getBuildingLevel() < 1` | `EntityAIWorkFarmer.prepareForFarming` |
| L | **max owned fields = building level** (L1 → 1 … L5 → 5) | `FarmerFieldsModule.getMaxExtensionCount()` returns `building.getBuildingLevel()` |
| L | hoe/axe level range `[0, getMaxEquipmentLevel()]` (sp3a § 0) | `keepX`, `getHoeSlot` |
| L | max taught recipes = `(int)(2^L × (1 + research RECIPES) × EXTRA_RECIPE_MULTIPLIER 5)` → 10, 20, 40, 80, 160 without research (the wiki gives the same numbers) | `AbstractCraftingBuildingModule.getMaxRecipes`, `canLearnManyRecipes()=true` |
| 3 | research `technology/biodegradable` (unlocks the composter hut) and `technology/letitgrow` (unlocks plantation) require farmer L3 | `DefaultResearchProvider` |
| 3/4/5 | farming-yield researches (§ 6) | same |

Advancement: `MAX_FIELDS` is triggered for every colony player when `module.getOwnedExtensions().size() == building.getMaxBuildingLevel()` (5), checked at each `prepareForFarming`.

### 1.3 Fields module (`BuildingExtensionsModule` + `FarmerFieldsModule`)
A field is a colony-level "building extension" (`IBuildingExtension`) keyed by `ExtensionId(position, type)`; the building only stores which one it is working on. Ownership lives on the field (`buildingId`).

- `getMatchingExtension(pred)` = all colony extensions of type `farmField` matching `pred`.
- `getOwnedExtensions()` = those whose `getBuildingId()` equals this building's id. `getFreeExtensions()` = those with no owner (`!isTaken()`).
- `canAssignExtension(ext)` = `owned.size() < getMaxExtensionCount()` **and** `canAssignExtensionOverride(ext)` = `ext instanceof FarmField && !seed.isEmpty()`. **A field without a seed can never be assigned.**
- `assignExtension(ext)`: if allowed, `ext.setBuilding(building.getID())`, `markDirty()` (which also marks the colony extension list dirty for client sync). Returns the success.
- `freeExtension(ext)`: `resetOwningBuilding()`, `markDirty()`, and if it is the current extension, `resetCurrentExtension()`.
- **Automatic claim** (`onColonyTick` → `claimExtensions`): if `!shouldAssignManually` (default **false**, i.e. automatic), walk `getFreeExtensions()` and assign **the first one that succeeds, then stop** (at most one field per colony tick). The colony tick of buildings runs from `Colony.worldTickSlow`, every `MAX_TICKRATE = 500` ticks (`TickRateConstants`), only for buildings in loaded chunks. Order of free extensions is the `HashMap` order of the colony list (no distance sorting server-side).
- **Manual mode** (`AssignmentModeMessage` sets `setAssignManually`): only the hut window assigns/frees (`AssignFieldMessage` → `assignExtension` / `freeExtension`). The GUI only enables the per-field button in manual mode.
- **Picking the field to work on** (`getExtensionToWorkOn()`):
  1. if `currentExtensionId` resolves to an existing extension, return it;
  2. otherwise iterate owned extensions: the first one **not in `checkedExtensions`** becomes current and is returned;
  3. otherwise pick the one with the smallest `lastDay` **strictly below today's colony day** (`lastUsedExtensionDay` starts at `colony.getDay()`); if none (every field was finished today), `currentExtensionId = null` and the method returns null.
- `resetCurrentExtension()`: `checkedExtensions[current] = colony.getDay()`, `current = null`. So **a field that got a productive pass is not picked again until the next colony day** (the colony day increments at dawn in `Colony.checkDayTime`, polled every 20 ticks).
- Persistence (module NBT): `"assign"` (bool), `"currex"` (current `ExtensionId`), and a checked-extensions list. **MC bug**: `serializeNBT` writes the list under `TAG_LIST = "List"` with the id put on the outer compound (not on the entry) and the day as a long, while `deserializeNBT` reads `TAG_BUILDING_EXTENSIONS = "building_extensions"` and the day as an int from the outer compound. In practice **`checkedExtensions` is never restored after a reload**, so every owned field becomes "never checked" again.

Colony side (`RegisteredStructureManager`):
- `addBuildingExtension(ext)`: `putIfAbsent` by id, mark dirty if new. `removeBuildingExtension(pred)`: remove all matching, mark dirty. `addBuildingExtensionIfMissing(type, pos, player)`: create if absent and send the extension list to that player.
- `cleanUpBuildings` (every 500 ticks): removes every extension whose position is loaded and is **outside the colony** or **`!isValidPlacement`** (`FarmField`: the block at its position is not `blockScarecrow`).
- On colony load: extensions are read from `"building_extensions"` (legacy key `TAG_FIELDS` if present). Then for each taken extension, the owner is reset if the building no longer exists, or if its first `BuildingExtensionsModule` is missing or expects another extension class.
- **Removing the farmer hut does not free its fields** at runtime; only the next colony load does (above). The client view also clears it (`FieldsModuleView` line 168) for display.

### 1.4 Settings
| Key | Type | Default | Effect |
|---|---|---|---|
| `minecolonies:fertilize` ("Request Fertilizer") | `BoolSetting` | **true** | `requestFertilizer()`; when compost and bone meal are both absent from hut and inventory, the farmer requests fertilizer (3.3) |
| `RECIPE_MODE` | `CrafterRecipeSetting` | (crafter default) | standard crafter recipe-selection mode |

There is no setting for field size, seeds or hoeing; those live on the field.

### 1.5 Items kept, eating
- `keepX` (constructor): **1 hoe** with level in `[TOOL_LEVEL_WOOD_OR_GOLD 0, getMaxEquipmentLevel()]` and **1 axe** in the same range (both `Tuple(1, true)`). The axe is kept although the farmer AI never uses one.
- `getRequiredItemsAndAmount()` adds, for every owned `FarmField` with a seed, **64 of that seed item** (`ItemStack.isSameItem`), `Tuple(64, true)`.
- `canEat(stack)` returns false for the seed of any owned field and for `Items.WHEAT`; otherwise `super.canEat`.

### 1.6 Crafting (`BuildingFarmer.CraftingModule extends AbstractCraftingBuildingModule.Crafting`)
- Supports `SMALL_CRAFTING` and `LARGE_CRAFTING`; recipe must have intermediate AIR and be compatible with the farmer tags: `isRecipeCompatibleBasedOnTags(recipe, "farmer")` = product matches `crafterProduct[farmer]` minus exclusions, or else inputs match `crafterIngredient[farmer]`.
  - `farmer` ingredient tag: `HAY_BLOCK`, `GRASS`, `FERN`.
  - `farmer` product tag: `HAY_BLOCK`, `#forge:seeds`, `blockCompostedDirt`, `MELON`, `COARSE_DIRT`, `FERMENTED_SPIDER_EYE`, `GLISTERING_MELON_SLICE`, `MUD_BRICKS`, `PACKED_MUD`, `MUDDY_MANGROVE_ROOTS` (`DefaultItemTagsProvider` lines 319-333).
- Built-in custom recipes (`DefaultFarmerCraftingProvider`): `carved_pumpkin` (1 pumpkin → carved pumpkin, required tool **shears**) and `mud` (dirt + large water bottle → mud, loot table `LOOT_TABLE_LARGE_BOTTLE` to return the bottle).
- `getAdditionalRecipesForDisplayPurposesOnly` / `getAdditionalLootTables`: JEI-only pseudo-recipes "seed → crop loot table, intermediate farmland, tool hoe" (MC crops use their preferred farmland and show their biome restriction; stem seeds show the fruit). **They are never executed**; farming is not done through the crafting system.
- Crafting runs through the standard `AbstractEntityAICrafting` flow (`GET_RECIPE` / `QUERY_ITEMS` / `CRAFT`) when the job has a crafting task (3.1).

### 1.7 Statistics
- Colony statistics (`StatisticsManager.increment(key, colonyDay)`): `LAND_TILLED = "land_tilled"` once per hoed cell, `CROPS_HARVESTED = "crops_harvested"` once per harvested crop.
- Building `STATS_MODULE`: `ITEM_OBTAINED + ";" + item.getDescriptionId()` incremented by the count of every drop received through `mineBlock` (`onBlockDropReception`).

---

## 2. Field model

### 2.1 Scarecrow block (`BlockScarecrow`, registry name `blockhutfield`, en_us "Field")
- Two-block tall (`HALF` lower/upper, `FACING`, `LANTERN` bool). Placement needs the block above to be replaceable; the lower half needs a sturdy face below. Recipe: `" H " / "SLS" / " S "` with H = hay block or pumpkin, L = leather, S = stick.
- Right-click with a lantern (when `LANTERN` is false) sets `LANTERN = true` (light like a lantern), consumes the lantern; it drops back on break.
- The field data belongs to the **lower half** (`getFieldBasePos`). The tile entity (`TileEntityScarecrow`) exists only on the lower half.
- **Registration with the colony**:
  - `setPlacedBy` (server): if the position belongs to a colony (`getColonyByPosFromWorld`), `addBuildingExtension(FarmField.create(pos, world))`; `create` copies the tile entity's radii.
  - `use` (server, right-click): `addBuildingExtensionIfMissing(farmField, basePos, player)` for the colony at that position; client side it opens `WindowField`.
  - `TileEntityScarecrow.getCurrentColony()` on the client sends `FarmFieldRegistrationMessage` (legacy, "TODO: Remove in 1.20.2"): the server creates the field if missing.
- **Destruction** (`playerWillDestroy`, `wasExploded`): the other half is removed; `removeBuildingExtension(type == farmField && pos == basePos)`.
- Scarecrow type (`ScareCrowType`, render only) is random and not persisted.

### 2.2 `FarmField` (extends `AbstractBuildingExtensionModule`)
| Field | Default | Notes |
|---|---|---|
| position | scarecrow lower half | part of the id |
| `buildingId` (owner) | null | NBT `"owner"` |
| `seed` | `ItemStack.EMPTY` | always normalised to count 1 (`getSeed()` also resets the count to 1) |
| `radii[4]` | `{5,5,5,5}` (`DEFAULT_RANGE = 5`) | order = `Direction.get2DDataValue()`: **S=0, W=1, N=2, E=3** |
| `fieldStage` | `EMPTY` | `Stage` enum |

- `MAX_RANGE = 20`. `setRadius(dir, r)` stores `min(r, 20)`.
- **Size budget**: the server (`FarmFieldPlotResizeMessage`) rejects `size < 0`, and rejects a growth (`size > current`) when `sum(radii) − current + size > MAX_RANGE`. So **the four radii together may not exceed 20** (default 5+5+5+5 = 20: one side can only grow when another shrinks). It then updates the tile entity (`setFieldSize`, capped at 20) and the matching `FarmField`.
- Field footprint: cells `(x, z)` with `-W ≤ x ≤ E` and `-N ≤ z ≤ S` (x toward east, z toward south), **excluding the centre (0,0)** where the scarecrow stands. Worked Y: see `getSurfacePos` (3.5).
- `isNoPartOfField(world, pos)` = `pos` is air, **or** the block above `pos` is a `FenceBlock`, `FenceGateBlock` or `WallBlock`. Fences/walls standing on a cell exclude it.
- `isValidPlacement(colony)` = the block at the field position is `blockScarecrow`.
- `Stage`: `EMPTY` (icon iron hoe, label "Hoe"), `HOED` (icon wheat seeds, label "Plant"), `PLANTED` (icon durum, label "Harvest"). `nextState()` = ordinal + 1, wrapping `PLANTED → EMPTY`. The labels describe the action the stage leads to.
- Seed change (`FarmFieldUpdateSeedMessage`): `setSeed(newSeed)` on the colony field; no stage reset.
- Persistence (`serializeNBT`): `"owner"` (BlockPos, optional), `"seed"` (ItemStack NBT), `"radius"` (int array), `"stage"` (enum name). Colony list key `"building_extensions"`. The tile entity also persists `"radius"`.

---

## 3. AI (`EntityAIWorkFarmer extends AbstractEntityAICrafting<JobFarmer, BuildingFarmer>`)

### 3.1 Constants and skills
| Name | Value | Use |
|---|---|---|
| `MAX_BLOCKS_MINED` | 64 | `getActionsDoneUntilDumping()` and `getActionRewardForCraftingSuccess()` |
| `DEFAULT_DELAY` | 40 ticks | base per-cell delay |
| `SMALLEST_DELAY` | 1 tick | floor of the per-cell delay |
| `DELAY_DIVIDER` | 1 | |
| `XP_PER_HARVEST` | 0.5 | per harvested crop, on top of `XP_PER_BLOCK = 0.05` from `mineBlock` |
| `MAX_DEPTH` | 5 | surface search depth up/down |
| `getLevelDelay()` | `(int) max(1, 40 − (primarySkill / 2.0) × 1)` | delay after each processed cell. Primary = **Stamina** |
| `getBreakSpeedLevel()` | secondary skill = **Athletics** | used by `mineBlock` timing: `(int)(500 × 0.85^(Athletics/2) × hardness / toolSpeed × (1 − BLOCK_BREAK_SPEED research))`. Crops have hardness 0 (`instabreak`), so harvesting is instant |
| `getLargestCell()` | `(int)(2 × MAX_RANGE + 1)² = 1681` | cell index bound |
| visible status | `FARMING_ICON` (`textures/icons/work/farmer.png`, "Farming") | set in `prepareForFarming` (when working a field) and in `workAtField` |
| render meta | `"working"` in FARMER_PLANT and FARMER_HARVEST | `updateRenderMetaData` |

Inherited delays used: `TICKS_SECOND = 20`, `STANDARD_DELAY = 5`, `PICKUP_ATTEMPTS = 10`. `worker.setCanPickUpLoot(true)` (vanilla item pickup).

### 3.2 State machine
Targets registered by the farmer: `PREPARING → prepareForFarming` (20 ticks), `FARMER_HOE`, `FARMER_PLANT`, `FARMER_HARVEST → workAtField` (5 ticks each).
Inherited from `AbstractEntityAICrafting`: `IDLE --hasWorkToDo--> START_WORKING` (20), `IDLE → idle()` (20, never reached because `hasWorkToDo()` is overridden to **always true**), `START_WORKING → decide` (5), `QUERY_ITEMS` (5), `GET_RECIPE` (5), `CRAFT` (`HIT_DELAY = 10`).
Inherited from `AbstractEntityAIBasic`: the `INVENTORY_FULL` dump event (checked every 100 ticks) and `dumpInventory` (20), `NEEDS_ITEM` (40), `GATHERING_REQUIRED_MATERIALS → getNeededItem` (20), `PAUSED`, etc.

| State | Behaviour |
|---|---|
| IDLE | `hasWorkToDo()` is true → START_WORKING |
| START_WORKING → `decide()` | `super.decide()`: set visible status WORKING; **walk to the hut** (stay in START_WORKING while walking); if `actionsDone >= 64` stay (the dump event fires); else `getNextCraftingState()`: no crafting task → `IDLE`, which the farmer's `decide` **maps to PREPARING**; with a crafting task → INVENTORY_FULL / QUERY_ITEMS / GET_RECIPE (normal crafter flow) |
| PREPARING → `prepareForFarming()` | see 3.3 |
| FARMER_HOE / FARMER_PLANT / FARMER_HARVEST → `workAtField()` | see 3.4 |
| GATHERING_REQUIRED_MATERIALS | `getNeededItem`: find the hut rack holding `needsCurrently` (compost, 64), walk there (gives up waiting after 10 attempts), transfer; then `getStateAfterPickUp()` = START_WORKING |
| INVENTORY_FULL | generic dump (sp3a § 0.1) |

`wantInventoryDumped()`: true (and clears the flag) when `shouldDumpInventory` is set (after each completed field pass) or `actionsDone >= 64`.

`canGoIdle()` (used by `CitizenAI` to allow leisure): if `getExtensionToWorkOn() == null` → `!super.hasWorkToDo()` (i.e. idle allowed when no crafting task), otherwise false. Note `getExtensionToWorkOn()` has the side effect of selecting the current field.

### 3.3 `prepareForFarming()` (PREPARING, every 20 ticks)
1. JobStatus IDLE. Building missing or level < 1 → JobStatus STUCK, stay PREPARING.
2. Owned fields == 5 → advancement `MAX_FIELDS`.
3. **Fertilizer**: `inHut = hasBuildingEnoughElseCount(building, isCompost, 1)`, `inInv = count(isCompost)`, where `isCompost` = `ModItems.compost` or `Items.BONE_MEAL`.
   - `inHut + inInv <= 0`: if `FERTILIZE` is on and the worker has **no open `StackList` request**, create async `StackList([compost ×1, bone_meal ×1], "com.minecolonies.coremod.request.fertilizer", count = STACKSIZE 64, minCount = 1)`. Continue.
   - else if `inInv <= 0 && inHut > 0`: `needsCurrently = (isCompost, 64)` → **GATHERING_REQUIRED_MATERIALS** (fetch up to 64 from the hut). Note this happens even when `FERTILIZE` is off.
4. No owned field → BLOCKING interaction `"entity.farmer.nofreefields"` ("Place more fields to get me working."), JobStatus STUCK → IDLE.
5. `field = module.getExtensionToWorkOn()`. Null → IDLE (every field done today). Non-`FarmField` → log warning → IDLE.
6. `checkForToolOrWeapon(hoe)` true (missing hoe; the request is created there) → JobStatus STUCK, stay PREPARING.
7. Visible status FARMING_ICON, JobStatus WORKING. Then by stage:
   - `PLANTED` and `checkIfShouldExecute(field, findHarvestableSurface != null)` → **FARMER_HARVEST**.
   - `HOED` → `canGoPlanting(field)`.
   - `EMPTY` and `checkIfShouldExecute(field, findHoeableSurface != null)` → **FARMER_HOE**.
   - otherwise (nothing to do in this stage): `field.nextState()`; `if (++skippedState >= 4) { skippedState = 0; didWork = true; module.resetCurrentExtension(); }` → IDLE.

`checkIfShouldExecute(field, predicate)`: loop `workingOffset = nextValidCell(field)`; null → false; else test `predicate(scarecrow.below().south(z).east(x))`; the first cell passing returns true and **leaves `workingOffset` on that cell**. The predicates have side effects (below): the hoe scan destroys replaceable plants, and the harvest scan spends fertilizer on immature crops.

`canGoPlanting(field)`:
- seed item found in inventory (`findFirstSlotInInventoryWith(seed.getItem())`) → **FARMER_PLANT**;
- else walk to the hut (PREPARING while walking);
- then `seeds.setCount(64)` (mutates the field's seed copy; `getSeed()` resets it to 1 later) and `checkIfRequestForItemExistOrCreateAsync(seeds, 64, 1)` (matchNBT true): true if ≥ 1 already in inventory, or if ≥ 1 could be transferred from the hut (it transfers up to 64); otherwise it creates an async `Stack(seed, 64, min 1)` request when there is no open or completed deliverable request matching the seed, and returns false.
- If it returned false: **`field.nextState()`** (HOED → PLANTED, the planting pass is skipped). Always → PREPARING.

### 3.4 `workAtField()` (FARMER_HOE / FARMER_PLANT / FARMER_HARVEST, every 5 ticks)
1. `field = module.getCurrentExtension()`; not a `FarmField` → IDLE. Set visible status.
2. If `workingOffset != null`:
   - `position = scarecrow.below().south(offset.z).east(offset.x)` (Y = scarecrow Y − 1).
   - `walkToSafePos(position.above())` (range 4); while walking stay in the state.
   - equip the hoe (first hoe slot within level range) in the main hand.
   - FARMER_HOE: `hoeIfAble` false → stay (mining in progress).
   - FARMER_PLANT: `tryToPlant` false → **PREPARING** (seed ran out).
   - FARMER_HARVEST: `harvestIfAble` false → stay.
   - `building.setPrevPos(position)`, `setDelay(getLevelDelay())`. This happens for **every visited cell, including cells where nothing was done**.
3. `workingOffset = nextValidCell(field)`. If null (pass finished): `shouldDumpInventory = true`; `field.nextState()`; `module.markDirty()`; `if (didWork || ++skippedState >= 4) { module.resetCurrentExtension(); skippedState = 0; }`; `didWork = false`; `prevPos = null`; → IDLE.
4. Otherwise stay in the state.

So the farmer walks the whole field cell by cell in every pass, and a field advances exactly one stage per pass.

**Cell order** (`nextValidCell`): if `workingOffset == null` the index restarts at −1. Then loop `cell++`; `cell == 1681` → null. Otherwise:
```
ring     = max(1, floor((sqrt(cell + 1) + 1) / 2))
ringCell = cell - (4 (ring-1)^2 + 4 (ring-1))
facing   = Direction.from2DDataValue(floorDiv(ringCell, 2 ring))     // 0 S, 1 W, 2 N, 3 E
if facing on Z axis: x = (N ? -1 : 1) * (ring - ringCell % (2 ring));  z = (N ? -1 : 1) * ring
else:                x = (W ? -1 : 1) * ring;  z = (E ? -1 : 1) * (ring - ringCell % (2 ring))
repeat while -z > radius N || x > radius E || z > radius S || -x > radius W
```
It is a square spiral outward from the scarecrow, ring by ring, 8·ring cells per ring. Ring 1 order: (1,1), (0,1), (−1,1), (−1,0), (−1,−1), (0,−1), (1,−1), (1,0). The cell index and offset live on the **building** (`cell`, `workingOffset`, `prevPos`, persisted as `"cell"`, `"workingoffset"`, `"prevpos"`).

The AI fields `didWork`, `skippedState`, `shouldDumpInventory` are not persisted.

### 3.5 Per-cell actions
**Surface** (`getSurfacePos(pos, depth=0)`): null if `|depth| > 5` or the chunk is not loaded. A block counts as "solid" if `isSolid()` and not pumpkin, melon or cobweb, **or** it is a liquid. Solid: if `depth < 0` return pos, else recurse up (`depth+1`). Not solid: if `depth > 0` return `pos.below()`, else recurse down (`depth−1`). Result: the top solid block of the column within ±5 of scarecrow Y − 1.

**Hoe** (`findHoeableSurface(pos, field)` then `hoeIfAble`):
- Null (nothing to do) if: no surface; `isNoPartOfField`; the block above is a `CropBlock`, the scarecrow, or a `MinecoloniesCropBlock`; the surface is not in `BlockTags.DIRT` and is neither MC farmland nor vanilla `FarmBlock`; or the surface already **is the right farmland for the field's seed** (`isRightFarmLandForCrop`: for an MC crop seed (`ItemCrop`) the crop's `preferredFarmland`, otherwise any vanilla `FarmBlock`).
- Side effect: if the block above is replaceable (`canBeReplaced`) and not an MC crop, `world.destroyBlock(above, drop=true)` (drops fall on the ground).
- Returns the surface. (The following `getToolModifiedState(HOE_TILL)` check is dead code, because the preceding test already guarantees the surface is not the right farmland.)
- `hoeIfAble`: if a surface was found **and** `checkForToolOrWeapon(hoe)` is false: `mineBlock(surface.above())` (instant if air; otherwise a timed mine with drops, +0.05 XP, +1 action); when done: `didWork = true`, equip hoe, swing, `createCorrectFarmlandForSeed` (MC crop seed → its preferred farmland (`farmland` or `floodedfarmland`), otherwise vanilla `FARMLAND`, default state), damage the hoe by 1, `decreaseSaturationForContinuousAction`, stat `LAND_TILLED`. Returns true.
- Missing hoe mid-pass: `checkForToolOrWeapon` creates the request and sets STUCK, but `hoeIfAble` returns true, so the cell is **skipped** and the pass continues.
- Any dirt-tag block is converted directly (grass, podzol, coarse dirt, mud… **[vanilla, not verified here]** for the tag content), and vanilla farmland is replaced by MC farmland (or the reverse) when the seed needs the other kind.

**Plant** (`findPlantableSurface` then `plantCrop`):
- Null if: no surface; `isNoPartOfField`; above is `CropBlock`, `StemBlock` or `MinecoloniesCropBlock`; the surface is the scarecrow; or the surface is **not** the right farmland. Null → `tryToPlant` returns true (cell skipped).
- `plantCrop(seed, surface)`: false if the seed is empty or **not in the inventory** (→ PREPARING). If the seed item is a `BlockItem` whose block is a `CropBlock`, `StemBlock` or `MinecoloniesCropBlock` **and** that block's default state `canSurvive` at `surface.above()`:
  - melon or pumpkin seeds: if `prevPos != null` and the block above `prevPos` is not air, skip (return true). This leaves every other cell free for the fruit (the check looks at the previously visited cell, Y = scarecrow Y).
  - otherwise place the crop's default state (age 0), `decreaseSaturationForContinuousAction`, remove 1 seed, `didWork = true`.
  - Otherwise (seed is not a plantable crop block, or cannot survive there) return true without planting.
- MC crop `canSurvive`: light ≥ 8 or sky visible, block below is exactly its `preferredFarmland`, and the biome is in its `preferredBiome` tag (if any).
- No XP for planting.

**Harvest** (`findHarvestableSurface` then `harvestIfAble`):
- Look at `above = surface.above()`:
  - `Blocks.PUMPKIN` or `Blocks.MELON` → harvestable.
  - vanilla `CropBlock`: max age → harvestable. Otherwise, if the inventory holds compost/bone meal: remove 1 (`shrinkItemCountInItemHandler`), send `CompostParticleMessage`, `crop.growCrops(world, pos, state)` **[vanilla: + random 2..5 ages, capped; not verified here]**, re-read the block; harvestable only if now at max age. No fertilizer → not harvestable.
  - `MinecoloniesCropBlock` (`AGE` 0..6, max 6): same, but growth is `attemptGrow` (one step, see § 4).
  - anything else (stems included) → not harvestable.
- `harvestIfAble`: `mineBlock(surface.above())` (drops into the inventory, +0.05 XP, +1 action); when done: `didWork = true`, stat `CROPS_HARVESTED`, **+0.5 XP**. Returns false while mining.
- **Fertilizer is spent both in the PREPARING scan and in the pass**: each immature crop the scan or the pass looks at consumes one compost/bone meal while any is in the inventory.

**Drops** (`mineBlock`): `BlockPosUtil.getBlockDrops(world, pos, fortune(tool), tool = hoe in hand, worker)`, then `increaseBlockDrops`: with research effect `FARMING = s > 0`, **each drop stack is doubled with probability `s`** (`random.nextDouble() < s`). Drops go straight into the citizen inventory (`transferItemStackIntoNextBestSlotInItemHandler`), not onto the ground. Then `onBlockDropReception` (statistics), block broken with the tool in hand.

### 3.6 Requests the farmer creates
| What | When | Request |
|---|---|---|
| Hoe | `checkForToolOrWeapon(hoe)` in PREPARING and in `hoeIfAble` | `Tool(hoe, 0, max(maxEquip, 0))` (sp3a § 0) |
| Fertilizer | PREPARING, no compost/bone meal in hut + inventory, `FERTILIZE` on, no open StackList | `StackList([compost, bone_meal], "fertilizer", 64, min 1)`, async |
| Seeds | `canGoPlanting`, HOED stage, seed not in inventory and not in the hut | `Stack(seed, 64, min 1, matchNBT true)`, async, if none open or completed for that seed |
| Pickup | after each dump (generic) | sp3a § 0.1 / 2.6 |
| Crafting inputs | crafting tasks (generic crafter) | |

### 3.7 Dumping and counters
- `actionsDone` is incremented by `mineBlock` only when a real block is broken: each harvest (+1), each non-air block removed above a hoed cell (+1). Air above a hoed cell, planting and fertilizing do not count.
- Dump triggers (event every 100 ticks, when interruptible): inventory full, `actionsDone >= 64`, or `shouldDumpInventory` (set at the end of **every** field pass). The dump keeps 1 hoe, 1 axe, 64 of each owned field's seed, plus the generic food/request keeps; then a Pickup request.
- Saturation: `decreaseSaturationForContinuousAction` on each hoed cell and each planted seed; job saturation factor 1.2.

### 3.8 Timeline of a field (derived from the code above)
Day N, field EMPTY: PREPARING scan finds a hoeable cell → HOE pass over every cell (walk + `getLevelDelay` per cell) → stage HOED, `didWork` → field locked until day N+1 → dump.
If nothing needed hoeing, the stage is skipped in PREPARING (`skippedState++`) and the farmer goes straight to HOED (no lock until 4 skips in a row).
HOED: seeds in inventory → PLANT pass → PLANTED, locked for the day. No seeds anywhere → Stack request, stage jumps to PLANTED without planting.
PLANTED: something mature (or made mature with fertilizer) → HARVEST pass → EMPTY, locked. Nothing mature → skip to EMPTY, then EMPTY (farmland already right, crops above) → skip to HOED → HOED plant pass (every cell occupied, `didWork` false) → PLANTED, and the pass counts as a skip.
The farmer walks back to the hut on every IDLE → START_WORKING → `decide` cycle.

---

## 4. Crop handling

| Kind | Planting | Mature when | Harvest |
|---|---|---|---|
| vanilla `CropBlock` (wheat, carrots, potatoes, beetroot…) | seed item's block placed on vanilla `FarmBlock` | `isMaxAge` | mine the crop; loot table with the hoe as tool |
| vanilla `StemBlock` (melon/pumpkin seeds) | on vanilla farmland, every other visited cell (prevPos rule) | never harvested | the **fruit** (`Blocks.PUMPKIN` / `Blocks.MELON`) standing on a field cell is mined; `getSurfacePos` treats the fruit as non-solid so the cell's ground is found under it |
| `MinecoloniesCropBlock` (15 crops) | on its `preferredFarmland`, in its biome | `AGE >= 6` | mine; loot table |
| sugar cane, cocoa, nether wart, sweet berries, bamboo, cactus… | **not handled** (not `CropBlock`/`StemBlock`/MC crop; `plantCrop` returns true without planting). The plantation hut covers some of these | | |

MC crops (`apiimp/initializer/ModBlocksInitializer.java` lines 127-147), `(name, farmland, dropped from, biome tag)`:
`bell_pepper` (farmland, grass/tall grass, temperate), `cabbage` (farmland, fern, cold), `chickpea` (farmland, grass/tall grass/dead bush, dry), `durum` (farmland, grass/tall grass, any), `eggplant` (any), `garlic` (any), `onion` (any), `soybean` (farmland, grass/tall grass/fern, humid), `tomato` (temperate), `rice` (**floodedfarmland**, seagrass/small dripleaf, humid), `butternut_squash` (cold), `corn` (temperate), `mint` (any), `nether_pepper` (dry), `peas` (humid). All others use `farmland`.

`MinecoloniesCropBlock`: `AGE` 0..6, no collision, instabreak, destroyed by pistons (`PushReaction.DESTROY`). `attemptGrow(state, level, pos)`: needs the area loaded and raw brightness ≥ 9; then, if a random horizontal neighbour is loaded, stands on the same farmland block, is air **and** the colony has research `GREEN_REVOLUTION`, a new crop (age 0) is placed there instead; otherwise `AGE + 1` up to 6. A player right-clicking a mature MC crop with a hoe gets the drops and resets it to age 0. It breaks (becomes air) when `canSurvive` fails.

`MinecoloniesFarmland` (`farmland` height 15/16, `floodedfarmland` height 13/16 and waterlogged): `MOISTURE` 0..7. Random tick: if a solid block is above → dirt. Not raining and no water within x/z ±4, y −1..0 (or a farmland water ticket): moisture −1, or at 0 turn to dirt unless a sustainable plant is above; wet: moisture = 7. Then if an MC crop is above and `rng.nextInt(100) <= growthChance` (**4**, or **12** while raining) → `attemptGrow`. **MC crops only grow through their farmland's random tick.** Trampling turns it to dirt (see Soft Shoes, § 6).

The farmer never waters, never places water, and never replants on its own outside a PLANT pass.

---

## 5. UI

### 5.1 Hut window
Wiki (`https://minecolonies.com/wiki/buildings/farmer/`): main page, crafting recipes, tasks, settings, fields, minimum stock (plus statistics from `STATS_MODULE`).

**Fields tab** (`FarmFieldsModuleWindow`, `layouthuts/layoutfarmfields.xml`, 190×244; tab icon `textures/gui/modules/field.png`, title "Fields"):
- label "Assign fields to worker" and a toggle button `assignmentMode` showing the hiring on/off text (manual = "on"); sends `AssignmentModeMessage`.
- label `fieldCount`: "%d/%d fields in use" (owned, max = building level).
- scrolling list `fields` (164×145), one 30 px row per field that is free or owned by this hut, sorted owned first, then by distance to the hut (`FieldsComparator`, integer Euclidean distance):
  - `icon`: the field's seed;
  - `dist`: "Distance: %s %s" (`<n>m` + short direction), or a long "above/below" text;
  - `nextstagetext` "Stage:" + `nextstageicon` = stage icon, tooltip "Current: %s" / "Next: %s" (only when a seed is set, hidden otherwise);
  - `assign` button (14×15): check texture when owned; enabled only in manual mode; disabled when `!canAssignField` with a red tooltip: "You already reached the limit of fields." (count reached) or "Field has no seed set".
  - Clicking toggles `AssignFieldMessage(assign = !isTaken)`.

Settings tab: "Request Fertilizer" (on by default) and the recipe mode.

### 5.2 Field window (`WindowField`, `gui/windowfield.xml`, 190×130, opened by right-clicking the scarecrow)
- Title "Field" (`block.minecolonies.blockhutfield`).
- `current-farmer`: "Farmer: %s" (first citizen assigned to the owning hut) or "Farmer: No one".
- `biome`: "Biome: " + biome name; `climate`: the crop climate tags the biome belongs to (comma separated).
- `select-seed` button "Pick seed": opens `WindowSelectRes` listing every item that is in `#forge:seeds`, or a `BlockItem` of a `CropBlock`, or an `ItemCrop` plantable in this biome; the choice sends `FarmFieldUpdateSeedMessage`. `current-seed` item icon shows the seed.
- Four direction buttons `dir-resize-{north,south,west,east}` (24×24, `textures/gui/scarecrow.png`) around a centre icon; the label is the radius. Tooltip: "Northward field size" etc. plus the relative direction for the player ("Opposite you", "To your left", "To your right", "Nearest to you").
- **Click** (left only in code): `leftOver = 20 − sum(radii)`; `newRadius = (current % min(current + leftOver, 20)) + 1`. The radius cycles 1, 2, …, `current + leftOver`, then back to 1. With the default 5/5/5/5, clicking a side sets it to 1, which frees 4 for the others. Sends `FarmFieldPlotResizeMessage`. The minimum reachable radius is 1 (0 only via a hand-crafted message).
- The seed/owner widgets are hidden when the scarecrow is not in a colony.
- **Wiki mismatch**: the wiki says "right-clicking will decrease the area" and "max size is 5 blocks in each direction, or 11×11". The code has no right-click handler and allows up to 20 in one direction as long as the four radii sum to ≤ 20.

---

## 6. Research affecting the farmer (`DefaultResearchProvider`, technology tree)

| Research | Requirement | Cost | Effect |
|---|---|---|---|
| `technology/biodegradable` | farmer L3 | 64 bone meal | unlocks the composter hut |
| `technology/bonemeal` (parent biodegradable) | farmer **or** composter L3 | 64 wheat seeds | `FARMING` level 1 = **0.1** |
| `technology/dung` | farmer or composter L4 | 128 wheat seeds | `FARMING` 2 = **0.25** |
| `technology/compost` | farmer or composter L5 | 256 wheat seeds | `FARMING` 3 = **0.5** |
| `technology/fertilizer` | smeltery L3 | 512 wheat seeds | `FARMING` 4 = **0.75** |
| `technology/magiccompost` | — | 2048 wheat seeds | `FARMING` 5 = **2** (always doubles) |
| `technology/greenrevolution` (parent biodegradable) | farmer L4 | 32 compost | `GREEN_REVOLUTION`: MC crops may spread to a free neighbour instead of aging |
| `technology/letitgrow` (parent biodegradable) | farmer L3 | 16 compost | unlocks the plantation |
| `technology/softshoes` | — | 16 white wool, 16 feathers | `SOFT_SHOES`: `EventHandler.onCropTrample` cancels farmland trampling by a citizen whose job is `JobFarmer` |

`FARMING` ("effects/farmingmultiplier", "Farmers Harvest +%s%% Crops") is the chance per drop stack to double it (3.5). General research that also applies: `BLOCK_BREAK_SPEED` (mining time), `RECIPES` (max recipes), the citizen inventory/rain/saturation researches.

---

## 7. Porting notes for Hytale (core capabilities needed)

**Maps onto existing HyColony pieces**: `TickRateStateMachine`/`AITarget`/`AIEventTarget`; the generic worker base (dump with keeps, pickup request, `checkForToolOrWeapon`, `GATHERING_REQUIRED_MATERIALS`) from SP3a; `WorkerModule` (Stamina/Athletics, 1 worker, no rain work); `Job.actionsDone`, `JobXp`, `Skills`; `ToolRequest`, `StackRequest`; `Colony.day()`; the settings module; the minimum-stock and statistics modules when they exist.

**New core pieces**:
1. **Building extension registry** (colony-level): `FieldRegistry` of `FarmField` keyed by position (+ type), with add / add-if-missing / remove, a "dirty" flag for views, persistence under the colony, and the 500-tick cleanup (outside colony or scarecrow gone) plus the load-time owner repair. The plantation later reuses it.
2. **`FarmField` record/state**: owner, seed (item id), `radii[S,W,N,E]` with the sum-≤-20 rule, stage `EMPTY/HOED/PLANTED` with `nextState`.
3. **Fields module** on the farmer hut: max = level, auto-claim one free seeded field per 500-tick colony tick unless manual, manual assign/free actions, `getExtensionToWorkOn` with per-field "last checked day", `resetCurrentExtension`. Decide whether to reproduce the MC persistence bug of `checkedExtensions` (never restored) or fix it with a `Deviation from MC` note.
4. **Farmer AI** with the exact states, delays, spiral `nextValidCell`, `skippedState`/`didWork` logic, `getLevelDelay`, per-building persisted `cell`/`workingOffset`/`prevPos`.
5. **Farmer-specific building rules**: keep 1 hoe + 1 axe + 64 of each owned field's seed; forbid eating field seeds and wheat; `FERTILIZE` setting (default true); crafting module restricted to the farmer tag lists (Hytale equivalents to choose).

**Ports the plugin must implement** (named by capability, no Hytale specifics):
- **Field block events**: scarecrow placed / broken / used (open field window, register if missing), with the lower-half position as the id; field size stored on the block entity or only in the core (Hytale choice).
- **Terrain queries** per column: "is solid" (with the pumpkin/melon/cobweb exceptions), "is liquid", "is air", "is replaceable", "is fence/gate/wall", "is in dirt tag", "is farmland (which kind)", "is scarecrow", chunk loaded.
- **Crop queries**: classify a block as vanilla-like crop / stem / MC crop / fruit (pumpkin, melon) / other; current age and max age; "can this seed's crop survive here" (light, farmland kind, biome); which farmland a seed needs; biome of a position and its climate tags (for the seed list and MC crop restrictions).
- **World edits**: destroy block with drops on the ground (replaceable plants), set farmland of a kind, place a crop at age 0, grow a crop (fertilizer: vanilla-like random 2..5 ages, MC-like one step), break a block collecting its drops into the citizen inventory with a given tool (loot + research doubling), damage the held tool.
- **Farmland random-tick simulation** if Hytale has no equivalent: moisture, reversion to dirt, 4 % / 12 % growth chance for MC-style crops, trampling (and its cancellation by Soft Shoes). This is a game rule; if Hytale's own crops grow by themselves, the core only needs the maturity query.
- **Item identities**: hoe equipment type and levels, compost and bone meal (fertilizer set), the seed catalogue for the "Pick seed" list.
- **Navigation**: walk to a safe position within 4 blocks of a cell, walk to the hut.
- **Visual**: particles for fertilizer, swing animation, "working" render state, visible status icon (all optional/cosmetic).
- **UI**: hut "Fields" tab view (list rows: seed, distance + direction, stage + next stage, assign toggle, count "n/max", assignment-mode toggle) and the field window view (owner name, biome, climate, seed picker, four radius buttons with the cycle formula).

**MC bugs and quirks to decide on** (port faithfully or fix with a `Deviation from MC` note):
- `checkedExtensions` of `BuildingExtensionsModule` is written and read under different keys, so it is lost on reload;
- `canGoPlanting` mutates the field's seed stack count to 64 (harmless, reset by `getSeed()`);
- `findHoeableSurface` has dead tool-modification code and destroys replaceable plants during the PREPARING scan;
- the harvest scan in PREPARING spends fertilizer;
- a missing hoe mid-pass silently skips cells;
- the farmer keeps an axe it never uses;
- removing the hut does not free its fields until the next colony load;
- `FARMER_WORK`/`FARMER_CRAFT` names are swapped (naming only);
- the wiki's "5 per direction / right-click decreases" does not match the code.

**In-game unknowns**: none of this has been checked against a running MineColonies; everything above is read from source. The vanilla behaviours marked **[vanilla, not verified here]** (`BlockTags.DIRT` content, `CropBlock.growCrops` increment) come from Minecraft 1.20.1, not from the MC repo.
