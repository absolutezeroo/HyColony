# SP3b: Hytale 0.6.8 farming, verified (for the colony farmer)

Sources, verified 2026-09-28:
- **S** = decompiled server `build/vineflower/hytale-server/com/hypixel/hytale/` (paths below are relative to it).
- **Z** = `release-0.6.8-Assets.zip`, entries `Server/...`.
- **MC** = `github.com/ldtteam/minecolonies`, branch `version/main`.

Extends `sp3a-mc-miner-hytale-world.md` § Saplings (sapling stages, game-time scale **30 game s per real s**, planting with `BlockOperations.setBlock` without bit 2). Not repeated here.

Items marked **[in-game]** are verified in source only.

## 0. Answer in short

- **Crops do produce `Ingredient_Life_Essence`.** Every mature vanilla crop drops it: 2–4 for wheat, up to 8–9 for potato and onion. So the colony farmer can feed the lumberjack's saplings.
- **Normal crops return no seed.** A mature normal crop drops the crop item, essence and a **0.5 %** chance of the *Eternal* seed. Replanting costs a new seed, crafted from essence at the Farmingbench (2 essence for wheat or lettuce, no tier). The net essence per wheat harvest is still positive.
- **Eternal crops regrow in place** after a *harvest* (not a break). No replant is needed, and they drop essence every cycle.
- **Growth needs no water, light or tilled soil.** These are only speed multipliers: fertilizer ×2, water ×2.5, light ×2, so up to ×10. The base maturity of most crops is **86 400 game s = 1 game day ≈ 48 real minutes**.
- **Maturity** is readable from the block id: `*<Crop>_Block_State_Definitions_StageFinal`.
- **Harvesting from code**: `FarmingUtil.harvest(...)` exists and is public. For a normal crop, breaking it with the soft drop list gives the same drops, so our `HytaleBlocks.drops` already does it.

## 1. `Ingredient_Life_Essence`: every source

Item: `Z:Server/Item/Items/Ingredient/Ingredient_Life_Essence.json` (MaxStack 100, Quality Uncommon). A full-text scan of every `Server/**/*.json` found no other sources than the ones below. It covered drops, items, NPC, prefabs, worldgen and barter.

### 1.1 Drop semantics (needed to read the tables)

- `MultipleItemDropContainer.populateDrops` (`server/core/asset/type/item/config/container/MultipleItemDropContainer.java:50-58`) rolls each child independently. The child drops if `child.getWeight() >= random*100`, so **`Weight` is a percentage**. The default weight is 100 (`ItemDropContainer.java:25`), meaning always.
- The quantity is uniform and inclusive: `ItemDrop.getRandomQuantity` = `nextInt(max-min+1)+min` (`ItemDrop.java:59-61`). The defaults are min = max = 1 (l. 29-30).
- `ChoiceItemDropContainer` makes a weighted choice between its children (`ChoiceItemDropContainer.java:51-58`).

### 1.2 Crops (the renewable source)

`Z:Server/Drops/Crop/<C>/Drops_Plant_Crop_<C>_StageFinal[_Harvest].json`. For **normal** crops, the soft (break) list and the Harvest list are **identical**.

| Crop | Normal: crop item | Normal: essence | Normal: other | Eternal (harvest): crop / essence | Eternal (break): + seed |
|---|---|---|---|---|---|
| Wheat | `Plant_Crop_Wheat_Item` 1–2 | 2–4 | `Plant_Seeds_Wheat_Eternal` 0.5 % | 1–2 / 1–2 | `Plant_Seeds_Wheat_Eternal` 1 |
| Lettuce | 1 | 2–4 | eternal seed 0.5 % | 1 / 1–2 | eternal seed 1 |
| Carrot | 1 | 3–6 | eternal seed 0.5 % | 1 / 2–3 | eternal seed 1 |
| Corn | 2–4 | 3–6 | eternal seed 0.5 % | 2–4 / 2–3 | eternal seed 1 |
| Cauliflower | 1 | 4–7 | eternal seed 0.5 % | 1 / 3–4 | eternal seed 1 |
| Turnip | 1 | 4–7 | eternal seed 0.5 % | 1 / 3–4 | eternal seed 1 |
| Aubergine | 1 | 5–8 | eternal seed 0.5 % | 1 / 4–5 | eternal seed 1 |
| Pumpkin | 1 | 5–8 | eternal seed 0.5 % | 1 / 4–5 | eternal seed 1 |
| Tomato | 1–3 | 6–8 | eternal seed 0.5 % | 1–3 / 5–6 | eternal seed 1 |
| Chilli | 1–3 | 6–8 | eternal seed 0.5 % | 1–3 / 5–6 | eternal seed 1 |
| Cotton | 1–3 | 7–8 | eternal seed 0.5 % | 1–3 / 6–7 | eternal seed 1 |
| Rice | 1–3 | 7–8 | eternal seed 0.5 % | 1–3 / 6–7 | eternal seed 1 |
| Potato | 2–4 | 8–9 | eternal seed 0.5 % | 2–4 / 7–8 | eternal seed 1 |
| Onion | 2–4 | 8–9 | eternal seed 0.5 % | 2–4 / 7–8 | eternal seed 1 |

Other plants:
- Apple (`Drops_Plant_Crop_Apple_StageFinal*`): `Plant_Fruit_Apple` 1, plus essence 0–1 at 90 %.
- Berry bushes (`Drops_Plant_Crop_Berry[_Wet|_Winter]_StageFinal*`): berries 1 (+1 at 3 %), essence 1 at **5 %**, stick at 20 %.

**Unripe crops** (`..._Stage1/2/3`, `..._Block`) drop only their own seed (`Plant_Seeds_<C>` or `_Eternal`), never essence.

### 1.3 Wild grass (`Plant_Crop_Wild_Grass_Block`): fast, self-seeding, but no survival source

- Block file: `Z:Server/Item/Items/Plant/Crop/Grass/Plant_Crop_Wild_Grass_Block.json`.
- Stages: `default`, `Stage1` and `Stage2` last **1000–3000 game s each**, then `Stage3` is final. Maturity takes 3000–9000 game s, **≈1.7–5 real minutes**.
- Breaking `Stage3` (`Wild_Grass_3`) drops `Ingredient_Fibre` 10–15, **essence 0–2** and `Plant_Seeds_Wild` 1. The seed comes back, so it sustains itself.
- The harvest lists (`Wild_Grass_2_Harvest`, `Wild_Grass_3_Harvest`) drop no seed.
- **`Plant_Seeds_Wild` has no recipe and no drop outside this plant.** Scanning `Server/Prefabs`, `HytaleGenerator` and `World` found no `Plant_Crop_Wild_Grass*` and no `Plant_Seeds_Wild`. So it cannot be obtained in vanilla survival, only by command or creative. Using it would be a deviation.
- `Z:Server/Drops/Ingredients/Wild_Grass_1.json` (fibre 2–5, essence 0–1) is referenced by nothing.

### 1.4 Non-renewable sources

- NPC: `Z:Server/Drops/NPCs/Elemental/Drop_Spirit_Root.json` (essence 2–3 in a Choice), used by `Server/NPC/Roles/Elemental/Spirit/Spirit_Root.json`. It is the only NPC drop list that contains essence.
- Prefab loot tables (a Choice inside Multiple):
  - `Z:Server/Drops/Prefabs/Zone1|2_Encounters_Tier1..4.json`: essence 1–3 up to 3–8, weight 10;
  - `Zone1|3_Kweebec_Tier1..3.json`: 1–2 up to 1–3, weight 30;
  - `Portals_Oasis.json`: 4–8.
  These tables are referenced by 144 and 18 prefabs, for example `Server/Prefabs/Npc/Kweebec/Redwood/Small_Plot/House/Kweebec_Redwood_Small_Plot_House_002.prefab.json`.
- Admin kits `Server/MacroCommands/Kit_Workbench1|3.json`.
- The Kweebec merchant (`Z:Server/BarterShops/Kweebec_Merchant.json`) only **consumes** essence (for example 20 essence for 3 spices, 30 for `Plant_Crop_Berry_Block`), never sells it.

### 1.5 Crafting to and from essence (Farmingbench, category `Essence`)

- `Ingredient_Life_Essence_Concentrated` = 100 essence, no tier. It is reversible: `Life Essence Recipes/Ingredient_Life_Essence_100.json` turns 1 concentrated back into 100 essence.
- Crop → essence, `Z:Server/Item/Items/Ingredient/Life Essence Recipes/Ingredient_Life_Essence_<C>.json`, 1 crop item each:

| Input | Essence out | Tier |
|---|---|---|
| Wheat | 1 | 2 |
| Lettuce | 3 | 2 |
| Carrot | 4 | 3 |
| Corn | 1 | 3 |
| Cauliflower | 6 | 4 |
| Turnip | 6 | 4 |
| Aubergine | 8 | 5 |
| Pumpkin | 8 | 5 |
| Tomato | 3 | 6 |
| Chilli | 3 | 6 |
| Cotton | 3 | 7 |
| Rice | 3 | 7 |
| Potato | 4 | 8 |
| Onion | 4 | 8 |

## 2. Soil

### 2.1 Tilling

- Hoes: `Tool_Hoe_Crude` (MaxDurability 100), `Tool_Hoe_Copper`, `Tool_Hoe_Iron` (300) and `Tool_Hoe_Thorium` (`Z:Server/Item/Items/Tool/Hoe/`). `Interactions.Secondary = "Hoe_Till"`.
- `Z:Server/Item/Interactions/Weapons/Hoe/Attacks/Till/Hoe_Till.json` is a `ChangeBlock` with these `Changes` to `Soil_Dirt_Tilled`: `Soil_Dirt`, `Soil_Dirt_Burnt`, `Soil_Dirt_Cold`, `Soil_Dirt_Dry`, `Soil_Grass`, `Soil_Grass_Burnt`, `Soil_Grass_Cold`, `Soil_Grass_Deep`, `Soil_Grass_Dry`, `Soil_Grass_Full`, `Soil_Grass_Sunny`, `Soil_Leaves`, `Soil_Mud`, `Soil_Mud_Dry`, `Soil_Needles`, `Soil_Pathway`. It is followed by `ModifyInventory AdjustHeldItemDurability -1`, so **1 durability per tilled block**.
- Tilled block: `Z:Server/Item/Items/Soil/Dirt/Soil_Dirt_Tilled.json`.
  - Tags `Type=Soil`, `Family=Dirt`, `SubType=Tilled`, and `BlockEntity.Components.TilledSoil`.
  - States `Fertilized`, `Watered` and `Fertilized_Watered`: `*Soil_Dirt_Tilled_State_Definitions_<S>` (texture only).
  - Breaking it gives `Soil_Mud_Dry`.
- Component: `TilledSoilBlock` (`builtin/adventure/farming/states/TilledSoilBlock.java`), registered as `"TilledSoil"` (`FarmingPlugin.java:105`). Its fields are `planted`, `fertilized`, `externalWater`, `wateredUntil` and `decayTime`, with public getters and setters.
- **Tilling from code**: `BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, id("Soil_Dirt_Tilled"), type, rotation, 0, settings)` (`server/core/universe/world/chunk/BlockOperations.java:127`) **without bit 2**. It clones `blockType.getBlockEntity()` (l. 76-80), then `FarmingSystems.OnSoilAdded` (`FarmingSystems.java:386-448`) sets the decay timer. This is the same path as saplings (sp3a). **[in-game]**

### 2.2 Tilled soil reverts

- `Farming.SoilConfig` of `Soil_Dirt_Tilled`: `Lifetime` 103 680–129 600 game s (1.2–1.5 game days ≈ **58–72 real minutes**), `TargetBlock: "Soil_Mud_Dry"`.
- `FarmingSystems.Ticking.tickSoil` (l. 556-660):
  - unplanted soil with no crop above, once `decayTime` has passed → `setBlock(Soil_Mud_Dry)`;
  - a crop above (`hasCropAbove`: the block above has `Farming.Stages`, l. 55-62) → `decayTime = null`, so it never decays;
  - when the crop disappears → a new lifetime is drawn (`updateSoilDecayTime`, l. 72-99).
- `Soil_Mud_Dry` can be tilled again. Fertilization is lost with the block.

### 2.3 Water (growth modifier ×2.5)

`Z:Server/Farming/Modifiers/Water.json`: `Fluids: [Water_Source, Water]`, `Weathers: [Zone1_Rain, Zone1_Rain_Light, Zone1_Storm, Zone3_Rain]`. Logic in `builtin/adventure/farming/config/modifiers/WaterGrowthModifierAsset.java`:

- Water block: only if the block **below the crop** has a `TilledSoil` component and one of its **4 horizontal neighbours at soil level** holds a listed fluid (`checkIfWaterSource`, `getNeighbourFluids`). This is **radius 1, not MC's 4**.
- Rain: a listed weather at the crop's environment, and nothing but air or the same block above up to y = 320 (`checkIfRaining`). This does not require tilled soil.
- Watering can: `TilledSoilBlock.wateredUntil` is in the future (`isSoilWaterExpiring`).
- Watering can item: `Tool_Watering_Can` (recipe 3 `Ingredient_Bar_Iron` at the Farmingbench), with state `Filled_Water` (MaxDurability 50).
  - `Z:Server/Item/Interactions/Tools/Watering_Can_Use.json` is a `UseWateringCan` with `Duration: 86400` (1 game day), `RadiusX/Z: 1`. A `_3x3` variant has radius 3.
  - It sets `wateredUntil` (`UseWateringCanInteraction.java:68, 151`).
- Tilled soil gets the texture state `Watered` (`TilledSoilBlock.computeBlockType`).

### 2.4 Fertilizer (growth modifier ×2)

- `Tool_Fertilizer` (`Z:Server/Item/Items/Tool/Feedbag/Tool_Fertilizer.json`): MaxStack 1, MaxDurability 5, `Secondary: Fertilizer_Use`.
  - Recipe (Farmingbench `Farming`, tier 3): 10 `Ingredient_Poop` + 10 essence + 5 `Vegetables`.
- `Fertilizer_Use` is a `FertilizeSoil` (`FertilizeSoilInteraction.java:127`) that sets `TilledSoilBlock.fertilized = true` on the targeted soil, or on the soil under the targeted crop. Each use costs 1 durability.
- Nothing ever resets `fertilized` (grep: the only `setFertilized` call is `FertilizeSoilInteraction.java:127`). **It is permanent while the soil block lives.** Reverting to `Soil_Mud_Dry` loses it. **[in-game]**
- `FertilizerGrowthModifierAsset` applies ×2 only if the block below the crop is fertilized `TilledSoil`.
- The developer tool `Tool_Growth_Potion` (`ChangeFarmingStage` ±1 stage, no recipe) is the only "bonemeal". It is not a survival item.

### 2.5 Light (growth modifier ×2)

`Z:Server/Farming/Modifiers/LightLevel.json` with `RequireBoth: false`. The bonus applies if:
- `sunlightFactor × skyLight ∈ [5, 15]`; or
- R, G and B artificial light are each in `[5, 127]`.

The code is `LightLevelGrowthModifierAsset.java:84-110`. On the planting tick with sunlight only, the multiplier is ×0.6 of that. `Darkness.json` exists but no crop lists it.

## 3. Crops

### 3.1 Ids

- Seeds: `Plant_Seeds_<C>` and `Plant_Seeds_<C>_Eternal` for C ∈ {Wheat, Lettuce, Carrot, Corn, Cauliflower, Turnip, Aubergine, Pumpkin, Tomato, Chilli, Cotton, Rice, Potato, Onion}. They are in `Z:Server/Item/Items/Plant/Crop/<C>/`, with parent `Template_Seeds`.
- Crop blocks: `Plant_Crop_<C>_Block` and `Plant_Crop_<C>_Block_Eternal` (the latter's parent is the former). Harvest item: `Plant_Crop_<C>_Item`.
- Stage block ids: `*Plant_Crop_<C>_Block_State_Definitions_<Stage>`, for example `*Plant_Crop_Wheat_Block_State_Definitions_StageFinal`. Worldgen prefabs use exactly these keys: 141 prefabs for berry, 78 for carrot, 32 for wheat and so on.
- The seed's `Secondary` is `Seed_Condition` (`Z:Server/Item/Interactions/Crops/Seed_Condition.json`): a `BlockCondition` that requires `Soil_Dirt_Tilled` or a `SubType=Planter` block, face Up. It then runs `Seed_Place`, a `PlaceBlock` of `BlockTypeToPlace` with `RemoveItemInHand`.
- The block's `Support.Down` accepts any `Type=Soil` or `SubType=Planter` (`Template_Crop_Block.json`). **So only the player's seed item requires tilled soil; the block does not.**

### 3.2 Stages and durations (game s)

Each stage is `Type: BlockState` (`BlockStateFarmingStageData`). The blocks come from `Z:.../Plant_Crop_<C>_Block.json` (`Farming.Stages.<StartingStageSet>`).

| Crop | Stages to `StageFinal` | Base maturity (game s) | Real time ×1 | Real time ×10 |
|---|---|---|---|---|
| Wheat, Corn, Cotton, Rice, Turnip, Pumpkin | `default` 10 500, `Stage1`–`Stage3` 25 300 each | 86 400 | 48 min | ≈4.8 min |
| Lettuce, Carrot, Cauliflower, Aubergine, Onion | `default` 10 500, `Stage1`–`Stage2` 37 950 each | 86 400 | 48 min | ≈4.8 min |
| Chilli | `default` 10 500, `Stage1`–`Stage4` 18 975 | 86 400 | 48 min | ≈4.8 min |
| Tomato (set `Starting`) | `default` 10 500, `Stage1`–`Stage5` 15 180 | 86 400 | 48 min | ≈4.8 min |
| Potato (normal) | `default` 10 500, `Stage1`–`Stage3` 37 950 | 124 350 | 69 min | ≈6.9 min |
| Potato Eternal | as wheat | 86 400 | 48 min | |
| Apple, berry bushes | `default` 86 400 / 80 000 | 86 400 / 80 000 | 48 / 44 min | |

- Eternal crops share the normal crop's `Default` stages. Their `Harvested` set restarts at `Stage1`, so regrowth is 86 400 − 10 500 = 75 900 game s ≈ 42 real minutes.
- Onion (normal) gives `StageFinal` its own `Duration` of 82 800–165 600, a timer after maturity with no visual effect (§ 3.3).
- `Template_Crop_Block` alone (9 600–11 400, then 28 800–30 600 ×2) is not used by real crops.
- **Conditions**: none are required. `FarmingUtil.tickFarming` (`FarmingUtil.java:59-205`) only multiplies the speed by the `ActiveGrowthModifiers` (`Fertilizer`, `Water`, `LightLevel`, all three on every crop). Growth goes at ×1 with no water, in the dark, on untilled soil.
  - There are **no seasons**: no match for "season" in the server sources.
  - Growth catches up with elapsed game time when the chunk reloads (`remainingTimeSeconds = now − lastTickGameTime`). The modifiers are evaluated at that moment. **[in-game]**
- The duration of a stage is `min + (max−min) × HashUtil.random(generation, x, y, z)`. It is deterministic per position and generation (l. 131-133).

### 3.3 Reading maturity from the server

- Each state is its own `BlockType`. `BlockStateFarmingStageData.apply` switches the block to `originBlockType.getBlockForState(state)` using `setBlock(..., settings 2)`, which keeps the block entity (`BlockStateFarmingStageData.java:62-120`).
- **Recommended test**: block id == `getBlockKeyForState(lastStage.getState())` of the base crop.
  - `lastStage` = `blockType.getFarming().getStages().get(getStartingStageSet())[last]`, cast to `BlockStateFarmingStageData` (`getState()`, l. 28).
  - In practice the key is `*Plant_Crop_<C>_Block[_Eternal]_State_Definitions_StageFinal`.
  - `BlockType.getBlockKeyForState` / `getBlockForState` are in `BlockType.java:1382-1397`.
- Alternative: `blockType.getGathering().isHarvestable()` (`BlockGathering.java:108`). Only `StageFinal` declares `Gathering.Harvest` in the assets.
- **Do not use "no `FarmingBlock` component" as the maturity test.**
  - `tickFarming` removes the block entity once the last stage is reached (the last stage has no `Duration` → `removeEntity`, l. 124-128).
  - Onion keeps it for 82 800–165 600 more game s.
  - A crop pasted by a prefab may never have had one.
- Progress is readable if needed:
  - `BlockModule.getBlockEntity(World, x, y, z)` (`server/core/modules/block/BlockModule.java:280`), then `store.getComponent(ref, FarmingBlock.getComponentType())`;
  - `FarmingBlock.getGrowthProgress()` (float = stage index + fraction), `getCurrentStageSet()` (`builtin/adventure/farming/states/FarmingBlock.java`, component `"FarmingBlock"`, `FarmingPlugin.java:106`).

### 3.4 Harvesting: three vanilla paths

1. **Break (soft)**. Any hit breaks the crop (`Gathering.Soft`, weapons excluded: `IsWeaponBreakable: false`, `BlockHarvestUtils.java:1118-1126`). It drops `Drops_Plant_Crop_<C>[_Eternal]_StageFinal`. The block is removed.
   - For **normal** crops this is the same list as Harvest.
   - For **eternal** crops it additionally returns the eternal seed, but the plant is destroyed.
2. **Use key ("Harvest")**. `BreakBlockInteraction` with `Harvest=true` calls `BlockHarvestUtils.performPickupByInteraction` (`BreakBlockInteraction.java:118-139`; `BlockHarvestUtils.java:707-800`). The drops of `Gathering.Harvest` go to the inventory, and the block is removed (`removeBlock`).
   - The hint `server.interactionHints.harvest` sits on `StageFinal`.
   - **[in-game]**: which root interaction triggers it for a normal crop.
3. **`HarvestCrop` interaction** (`HarvestCropInteraction.java` → `FarmingUtil.harvest`). Eternal crops declare it on `StageFinal.Interactions.Use`. The sickle (`Sickle_Swing_*_Selector.json`, `HitBlock`) also uses it, at 1 durability per block. `FarmingUtil.harvest0` (l. 267-359) works as follows:
   - `StageSetAfterHarvest != null` (eternal crops, apple, berry): gives the Harvest drops, switches `FarmingBlock` to the `Harvested` set with progress 0, `scheduleTick`, and applies `newStages[0]` (back to `Stage1`). **The plant stays.**
   - Otherwise (normal crops, whose `Farming` replaces the template's, so `StageSetAfterHarvest` is null, cf. `FarmingData` codec with `append` and not `appendInherited`): gives the Harvest drops and sets `BlockType.EMPTY`. **The seed is not replanted.**

**Replant**: vanilla never replants a normal crop. The seed must come from the unripe-break drop (the seed back), a Farmingbench craft, or loot. The tilled soil stays (it is "planted" until the next soil tick, then gets a new lifetime).

## 4. Server APIs for the plugin

| Need | API (verified) |
|---|---|
| Till | `BlockOperations.setBlock(ChunkStore, Ref<ChunkStore> sectionRef, int x, int y, int z, int id, BlockType, int rotation, int filler, int settings)` (`BlockOperations.java:127`) with `Soil_Dirt_Tilled`, bit 2 off. This clones the `TilledSoil` entity (l. 76-80), then `OnSoilAdded` starts decay. Our `HytaleWorldBlocks.placeBlock` (settings NONE) already does this (see sp3a). |
| Plant so that it grows | Same call with `Plant_Crop_<C>_Block` on the soil. It clones `FarmingBlock`. `FarmingSystems.OnFarmBlockAdded` (`FarmingSystems.java:275-383`) sets `CurrentStageSet`, `LastTickGameTime`, finds the stage index of the placed block (so placing a `Stage2` state starts at stage 2) and runs `tickFarming(..., initialTick=true)`. **[in-game]** |
| Read the stage | Block id vs `getBlockKeyForState(...)` (§ 3.3), or `FarmingBlock.getGrowthProgress()`. |
| Harvest with vanilla logic | `FarmingUtil.harvest(ComponentAccessor<ChunkStore> chunkStore, ComponentAccessor<EntityStore> entityStore, @Nullable Ref<EntityStore> ref, Vector3i pos)` (`FarmingUtil.java:207`, `public static`). Returns `false` if the block has no `Gathering.Harvest` (immature) or `WorldConfig.isBlockGatheringAllowed()` is off. With `ref == null` the drops **spawn as item entities** at the block centre (`giveDrops`, l. 361-378: `ItemComponent.generateItemDrops`). With a ref, they go through `ItemUtils.interactivelyPickupItem`. **[in-game]** for an NPC ref. It handles eternal regrowth by itself. |
| Harvest while keeping the drops in code | `BlockHarvestUtils.getDrops(BlockType, int qty, @Nullable String itemId, @Nullable String dropListId)` (`BlockHarvestUtils.java:817`) with `blockType.getGathering().getHarvest().getItemId()` / `getDropListId()` (`HarvestingDropType.java:49, 53`). Then `setBlock(EMPTY)` for a normal crop. For an eternal crop, reproduce the `harvest0` branch (`harvest0` and `giveDrops` are `protected`). For normal crops, our existing `HytaleBlocks.drops(type)` (`plugin/.../adapter/HytaleBlocks.java:83-95`, soft list) already gives exactly the harvest drops. |
| Fertilize | `TilledSoilBlock.setFertilized(true)` on the soil's block entity, plus `blockSection.setTicking(x, y, z, true)` on the soil and the crop, as `FertilizeSoilInteraction` does (l. 127-140). |
| Water | `TilledSoilBlock.setWateredUntil(now + 86400 s)`, plus `setTicking` and `scheduleTick(soilIndex, wateredUntil)`, as `UseWateringCanInteraction` does (l. 140-170). Game time: `WorldTimeResource.getGameTime()`. |
| Advance a stage ("bonemeal") | There is no public helper. `ChangeFarmingStageInteraction` (l. 160-290) sets `FarmingBlock.setGrowthProgress(i)`, calls `scheduleTick(blockIndex, now)` and `stages[i].apply(...)`. To be reproduced only if a colony rule needs it. |

## 5. Farmingbench (`Farmingbench`, `Z:Server/Item/Items/Bench/Bench_Farming.json`)

- Built at the Workbench from 6 `Wood_Trunk` and 20 `Ingredient_Fibre`.
- Categories: `Farming`, `Seeds`, `Saplings`, `Essence`, `Planters`, `Decorative`.
- **Tiers**: the bench starts at 1 (`BenchBlock.java:50`). `TierLevels[i]` describes level i+1 (`Bench.getTierLevel`, `Bench.java:140-142`), and its `UpgradeRequirement` leads to i+2, so there are 8 levels. A recipe is locked if `tier < RequiredTierLevel` (`CraftingRecipe.java:315-325`); when absent it is 0.
- Upgrades:
  - 1→2: 50 essence, 5 wheat, 5 lettuce, 5 `Wood_Softwood_Trunk`;
  - 2→3: 1 concentrated, 10 carrot, 10 corn, 10 `Wood_Lightwood_Trunk`;
  - 3→4: 2 concentrated, 20 cauliflower, 20 turnip, 20 `Wood_Hardwood_Trunk`;
  - 4→5: 3 concentrated, 30 aubergine, 30 pumpkin, 30 drywood;
  - 5→6: 4 concentrated, 40 tomato, 40 chilli, 40 darkwood;
  - 6→7: 5 concentrated, 50 cotton, 50 rice, 50 redwood;
  - 7→8: 6 concentrated, 60 potato, 60 onion, 60 goldenwood.

Recipes relevant to a farmer or a lumberjack (tier in brackets, `-` = none):

- **Seeds**, `Seeds` category:
  - `Plant_Seeds_Wheat` and `Lettuce` = 2 essence [-];
  - `Carrot` and `Corn` = 3 [2];
  - `Cauliflower` and `Turnip` = 5 [3];
  - `Aubergine` and `Pumpkin` = 6 [4];
  - `Tomato` and `Chilli` = 6 [5];
  - `Cotton` and `Rice` = 8 [6];
  - `Potato` and `Onion` = 10 [7];
  - `Plant_Seeds_Sunflower` ×2 = 5 essence + 1 `Plant_Flower_Tall_Yellow` [-].
- **Eternal seeds** = 10× the seed's essence + crop items + 1 normal seed, at one tier higher. For example `Plant_Seeds_Wheat_Eternal` = 20 essence + 10 wheat + 1 `Plant_Seeds_Wheat` [2]; potato and onion eternal = 100 essence + 50 crop [8].
- **Saplings** (`Saplings`), in essence:
  - `Aspen`, `Beech` = **5 [0]**, available on a fresh bench;
  - `Birch`, `Camphor`, `Gumboab` = 10 [3];
  - `Oak`, `Ash`, `Amber`, `Bamboo`, `Banyan`, `Ice`, `Jungle`, `Palo`, `Sallow`, `Spruce`, `Spruce_Frozen`, `Stormbark`, `Windwillow` = 15 [4];
  - `Azure`, `Fig_Blue`, `Maple`, `Redwood`, `Spiral` = 20 [5];
  - `Cedar`, `Wisteria_Wild` = 25 [6];
  - `Bottletree`, `Dry`, `Fire`, `Petrified` = 30 [7];
  - `Palm` = 35 [8];
  - `Apple` = 1 concentrated + 4 `Plant_Fruit_Apple` [4].
- **Farming**:
  - `Tool_Hoe_Crude` (4 `Wood_Trunk` + 3 `Rock`) [-], `Copper` [2], `Iron` [4], `Thorium` [6];
  - `Tool_Sickle_Crude` [2], `Copper` [4], `Iron` [5];
  - `Tool_Watering_Can` (3 iron bars) [-];
  - `Tool_Fertilizer` [3];
  - `Container_Bucket` [-];
  - `Coop_Chicken` [3] (50 essence).
- There is no compost block and no "compost" item. Fertilizer is the only one.

## 6. Mapping to MineColonies farmer

MC reference: `src/main/java/com/minecolonies/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java`.
- States: `PREPARING` → `FARMER_HOE` → `FARMER_PLANT` → `FARMER_HARVEST`.
- Methods: `hoeIfAble`/`findHoeableSurface`, `tryToPlant`/`findPlantableSurface`, `findHarvestableSurface` (`isMaxAge`).
- Compost: `isCompost` accepts `ModItems.compost` or `Items.BONE_MEAL`, then `growCrops`/`attemptGrow`.
- Constants: `XP_PER_HARVEST = 0.5`, `DEFAULT_DELAY = 40`, `MAX_BLOCKS_MINED = 64`.
- The field is `FarmField`, with seed, stage `EMPTY`/`HOED`/`PLANTED`, radius per direction, and a spiral walk `nextValidCell`.
- Summary obtained via WebFetch. Re-read the file before porting a constant.

| MC | Hytale 0.6.8 | Gap |
|---|---|---|
| Hoe (`ItemHoe`, damage 1 per tilled block) | `Tool_Hoe_*`, `Hoe_Till` = `ChangeBlock` + 1 durability | Equivalent. Tillable-block list in § 2.1. |
| Farmland (`Blocks.FARMLAND`) | `Soil_Dirt_Tilled` + `TilledSoil` component | MC farmland reverts to dirt when dry and empty. Here it reverts to `Soil_Mud_Dry` after 58–72 real minutes if empty, whatever the water. |
| Hydration (water within 4 blocks, moisture 0–7) | Water modifier ×2.5: water **horizontally adjacent** to the soil, rain, or watering can (1 game day) | MC: dry farmland slows growth. Here, no water means ×1, never a block. |
| Seeds (`farmField.getSeed()`, seeds dropped by the crop) | `Plant_Seeds_<C>`, placed as `Plant_Crop_<C>_Block` | **Major gap**: a normal mature crop returns **no seed**. Only an unripe break returns the seed, and the eternal seed drops at 0.5 %. The farmer must craft its seeds (2–10 essence) or use eternal seeds. |
| Bonemeal / compost (`growCrops`, one stage) | `Tool_Fertilizer`: ×2 speed, permanent on the soil; **no one-stage item** (only the dev tool `Tool_Growth_Potion`) | MC's compost step has no survival equivalent. The closest faithful port is fertilizing the plot. |
| Mature crop (`CropBlock.isMaxAge`) | Block id `*..._State_Definitions_StageFinal` (or `Gathering.isHarvestable()`) | Equivalent. |
| Harvest (break, drops, replant) | Normal crop: soft or harvest drops, then the block is emptied. Eternal crop: harvest leaves the plant at `Stage1`. | Replanting a normal crop needs a seed in stock. Eternal crops remove the replant step. |
| Crop growth (random ticks, light ≥ 9 required) | Timed stages, 48 real minutes base, ×10 at best, no light requirement | Different model: deterministic and time-based. |
| Scarecrow / field block | `Deco_Scarecrow` exists (Farmingbench `Decorative`, 10 essence + pumpkin …) but is decorative | The field block is ours to define (as the MC field). |

**Consequences for SP3b (proposal, not decided):**
- A wheat plot is enough to produce essence: 2–4 per mature crop, minus 2 for the replacement seed, plus 1–2 wheat.
- Aspen and Beech saplings cost 5 essence with no bench tier, which is cheaper than Oak (15, tier 4).
- Fertilized tilled soil with a water block next to it and daylight reaches ×10, about 5 real minutes per crop.
- If vanilla is followed, the farmer must also re-till any plot left empty for more than one hour.
