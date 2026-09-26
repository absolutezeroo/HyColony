# Plan B adapters: verified Hytale 0.6.8 API cheat sheet

Written 2026-09-26. Checked against the decompiled server (`build/vineflower/hytale-server/com/hypixel/hytale/`, noted `…/`) and the assets zip `%USERPROFILE%\.gradle\caches\hytale-assets\release-0.6.8-Assets.zip` (60 695 entries). Nothing here has been run in game. Items that are verified only in the source, and whose client-side result is still unknown, are flagged **[in-game]**.

Common imports used below:

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;   // BlockType, BlockGathering, BlockBreakingDropType, RotationTuple, Rotation
import com.hypixel.hytale.server.core.asset.type.item.config.*;        // Item, ItemTool, ItemToolSpec
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.*;           // ItemContainer, CombinedItemContainer, SimpleItemContainer
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.{BlockSection, FluidSection};
import com.hypixel.hytale.server.core.universe.world.storage.{ChunkStore, EntityStore};
```

Threading: every call below runs on the world thread, which is where ECS systems, `handleDataEvent` and `world.execute(...)` run.

---

## 1. WorldBlocks

### Read a block without loading its chunk
- `ChunkStore.getChunkSectionReferenceAtBlock(int x, int y, int z)` → `@Nullable Ref<ChunkStore>`. Its javadoc says it returns the reference "if it is already loaded in memory or null", so it **never loads**. It is the best `isLoaded` test: it is per section, finer than the column test `getChunkReference(ChunkUtil.indexChunkFromBlock(x, z))` used by `HytaleWorldQuery`.
- `BlockSection` (component of the section ref): `int get(x,y,z)` returns the runtime block index; `int getRotationIndex(x,y,z)`; `RotationTuple getRotation(x,y,z)`; `int getFiller(x,y,z)`, where 0 is the origin block and anything else is a packed offset to the origin.
- Id ↔ key: `BlockType.getAssetMap().getAsset(int)` returns a `BlockType`, then `getId()` gives the key. `getIndex(String)` returns `Integer.MIN_VALUE` if the key is unknown.
- **Air** is `BlockType.EMPTY_ID == 0`, key `"Empty"`, `BlockType.EMPTY`. Prefabs also contain explicit `"Empty"` entries.
- **Fluids are not blocks.** They live in `FluidSection` on the same section ref: `getFluidId(x,y,z)` (0 = `Fluid.EMPTY_ID`), `getFluidLevel`, `getFluid` → `Fluid.getId()` (for example `Water_Source`).
- **State variants**: block ids starting with `*` (for example a chest's `OpenWindow` state) are states. Normalise them with `type.getDefaultStateKey()`, which is non-null for a state (the key comes from `StateData`: `"*" + key + "_" + state`).
- Rotation: store the `RotationTuple` index as the core `BlockState.rotation`. `RotationTuple.get(i)` has `.yaw()`/`.pitch()`/`.roll()` (enum `Rotation`: `None, Ninety, OneEighty, TwoSeventy`), and `RotationTuple.of(yaw, pitch, roll).index()`.

```java
Optional<BlockState> get(BlockPos p) {
    ChunkStore cs = world.getChunkStore();
    Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
    if (sec == null) return Optional.empty();                       // not loaded, nothing forced
    Store<ChunkStore> st = cs.getStore();
    BlockSection bs = st.getComponent(sec, BlockSection.getComponentType());
    int id = bs.get(p.x(), p.y(), p.z());
    if (id == BlockType.EMPTY_ID) {
        FluidSection fs = st.getComponent(sec, FluidSection.getComponentType());
        int fluid = fs == null ? 0 : fs.getFluidId(p.x(), p.y(), p.z());
        if (fluid != 0) return Optional.of(new BlockState(new BlockKey("~fluid:" + Fluid.getAssetMap().getAsset(fluid).getId()), 0));
    }
    BlockType t = BlockType.getAssetMap().getAsset(id);
    String key = t.getId().startsWith("*") && t.getDefaultStateKey() != null ? t.getDefaultStateKey() : t.getId();
    return Optional.of(new BlockState(new BlockKey(key), bs.getRotationIndex(p.x(), p.y(), p.z())));
}
```
(The `~fluid:` pseudo-key is a suggestion, so that `ItemCatalog.kind` can return `FLUID`.)

### Place a block (section API; creates its default block entity/container)
`…/universe/world/chunk/BlockOperations.java`:
- `static boolean setBlock(ChunkStore chunkStore, Ref<ChunkStore> sectionRef, int x, int y, int z, int id, BlockType blockType, int rotation, int filler, int settings)`, javadoc "Not yet stable". Here `x, y, z` are **world** coordinates (the body masks them with `& 31`). With `filler == 0` and settings without `NO_UPDATE_STATE (2)`, it clones `blockType.getBlockEntity()` into place, so a chest gets its `ItemContainerBlock`. Without `NO_SET_FILLER (8)` it also generates the filler blocks. `BlockSetCommand` calls it as `setBlock(cs, sec, x, y, z, index, type, 0, 0, 0)`.
- `static boolean testPlaceBlock(ComponentAccessor<ChunkStore> accessor, BlockSection section, int wx, int wy, int wz, BlockType type, int rotationIndex)` rejects any occupied cell of the hitbox. The overload `(…, int rotationIndex, TestBlockFunction func)` with `func.test(x,y,z, BlockType other, int rot, int filler)` returns true to allow replacing a cell. Empty and `Material.Empty` cells always pass.
- `SetBlockSettings` (…/universe/world/SetBlockSettings.java): `NONE 0, NO_NOTIFY 1, NO_UPDATE_STATE 2, NO_SEND_PARTICLES 4, NO_SET_FILLER 8, NO_BREAK_FILLER 16, PHYSICS 32, FORCE_CHANGED 64, NO_UPDATE_NEIGHBOR_CONNECTIONS 128, PERFORM_BLOCK_UPDATE 256, NO_UPDATE_HEIGHTMAP 512, NO_SEND_AUDIO 1024, NO_DROP_ITEMS 2048, NO_FIRE_ON_BREAK 4096`.

```java
boolean place(BlockPos p, BlockState s, boolean withContainer) {
    ChunkStore cs = world.getChunkStore();
    Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
    int id = BlockType.getAssetMap().getIndex(s.key().id());
    if (sec == null || id == Integer.MIN_VALUE) return false;
    BlockType type = BlockType.getAssetMap().getAsset(id);
    BlockSection bs = cs.getStore().getComponent(sec, BlockSection.getComponentType());
    // builder already cleared the spot; allow replacing leftovers such as grass
    if (!BlockOperations.testPlaceBlock(cs.getStore(), bs, p.x(), p.y(), p.z(), type, s.rotation(), (x, y, z, o, r, f) -> true)) return false;
    int settings = withContainer ? SetBlockSettings.NONE : SetBlockSettings.NO_UPDATE_STATE; // NO_UPDATE_STATE skips the block entity
    BlockOperations.setBlock(cs, sec, p.x(), p.y(), p.z(), id, type, s.rotation(), 0, settings);
    return true;
}
```

### Break with no drops, then compute the drops ourselves
- `BlockHarvestUtils.naturallyRemoveBlock(Vector3i pos, @Nullable BlockType type, int filler, int quantity, String itemId, String dropListId, int setBlockSettings, Ref<ChunkStore> sectionRef, ComponentAccessor<EntityStore> entityStore, ComponentAccessor<ChunkStore> chunkStore)`. It resolves a filler back to its origin, plays the break sound unless `NO_SEND_AUDIO`, clears the block health and calls `setBlock(…EMPTY…)`. It spawns drops only if `(settings & 2048) == 0`. It fires **no** `BreakBlockEvent`; `performBlockBreak(...)` does, but only when given an entity ref.
- `static List<ItemStack> getDrops(BlockType type, int quantity, @Nullable String itemId, @Nullable String dropListId)`. If both ids are null it returns `type.getItem()` × quantity. A drop list is rolled `quantity` times through `ItemModule`, so ores are random.
- Where the values come from: `BlockGathering g = type.getGathering()` gives `getBreaking()` (a `BlockBreakingDropType` with `getGatherType() / getQuality() / getQuantity() / getItemId() / getDropListId()`), or `getSoft()` (`SoftBlockDropType`: `getItemId / getDropListId`, quantity 1), or `getHarvest()`. The vanilla player path (`damageSingleBlock`) uses breaking when a tool spec matches, otherwise soft.
- **Container contents always drop on the ground**: `ItemContainerSystems.OnAddedOrRemoved.onEntityRemove` calls `dropAllItemStacks()` on any removal except `UNLOAD`, whatever the settings. Empty the container first with `getItemContainer().removeAllItemStacks()` (it returns the stacks).

```java
List<ItemAmount> breakBlock(BlockPos p) {
    ChunkStore cs = world.getChunkStore();
    Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
    if (sec == null) return List.of();
    BlockSection bs = cs.getStore().getComponent(sec, BlockSection.getComponentType());
    BlockType type = BlockType.getAssetMap().getAsset(bs.get(p.x(), p.y(), p.z()));
    if (type == null || type == BlockType.EMPTY) return List.of();
    List<ItemStack> out = new ArrayList<>();
    ItemContainerBlock c = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
    if (c != null) out.addAll(c.getItemContainer().removeAllItemStacks());     // before removal, or it hits the ground
    BlockGathering g = type.getGathering();
    BlockBreakingDropType b = g == null ? null : g.getBreaking();
    if (b != null) out.addAll(BlockHarvestUtils.getDrops(type, Math.max(1, b.getQuantity()), b.getItemId(), b.getDropListId()));
    else if (g != null && g.getSoft() != null) out.addAll(BlockHarvestUtils.getDrops(type, 1, g.getSoft().getItemId(), g.getSoft().getDropListId()));
    BlockHarvestUtils.naturallyRemoveBlock(new Vector3i(p.x(), p.y(), p.z()), type, bs.getFiller(p.x(), p.y(), p.z()),
            0, null, null, SetBlockSettings.NO_DROP_ITEMS, sec, world.getEntityStore().getStore(), cs.getStore());
    return toAmounts(out);
}
```
Fluid removal: `store.ensureAndGetComponent(sec, FluidSection.getComponentType()).setFluid(x, y, z, 0, (byte) 0)`, the same call `PrefabUtil.paste` uses.

### Hardness, tool type, tool tier
Hytale has **no per-block hardness**. Block health is normalised to 1.0, and each hit removes `ItemToolSpec.getPower()` for the block's `GatherType` (`BlockHarvestUtils.damageSingleBlock`). The spec is looked up in:
- the held tool's `ItemTool.getSpecs()` (the first spec whose `getGatherType()` equals the block's); otherwise
- the unarmed default `ItemToolSpec.getAssetMap().getAsset(gatherType)` (assets `Server/Item/Unarmed/Gathering/*.json`, keyed by gather type): Rocks 0.035, Woods 0.03, Soils 0.1, SoftBlocks 1, Benches 0.5, Ore* 0.001, VolcanicRocks 0.001.

**Tier rule** (`getSpecPowerDamageBlock`): if `spec.getQuality() < breaking.getQuality()`, the tool cannot damage the block at all. "Tool tier needed" is therefore `breaking.getQuality()`; it is 0/absent for 789 of 799 `Rocks` blocks and 1, 2 or 5 for a few.

GatherType tally over the vanilla block items: Rocks 799, Woods 511, SoftBlocks 156, Soils 156, VolcanicRocks 35, Benches 21, Unbreakable 7, OreGold/OreIron/OreSilver 6, OreCopper 3, OreCobalt/OreThorium/SoftWoods 2, OreAdamantite 1, Pickaxe_Tier0 1.
- Tool type: `Rocks|VolcanicRocks|Ore*` → PICKAXE, `Woods|SoftWoods` → AXE, `Soils` → SHOVEL, anything else → empty.
- Suggested hardness: `0.05f / unarmedPower(gatherType)`. This gives Rocks ≈1.4, Woods ≈1.7, Soils 0.5 and SoftBlocks 0.05, which is close to MineColonies, but Ore* would come out at 50. Clamp that to about 3. (ponytail: heuristic, replace it with a table if the balance is off.)

## 2. ItemCatalog

- **Item for a block**: `BlockType.getItem()` → `@Nullable Item` (the asset container key). A block defined inside an item JSON has **the same id** as the item (`Item.processConfig`: `if (hasBlockType) blockId = id`). For a state id (`*…`), use `getDefaultStateKey()` first.
- **Block for an item**: `Item.hasBlockType()` and `Item.getBlockId()` (null when there is no block).
- `Item.getMaxStack()`: when absent it is filled in as 100, or 1 for a tool, weapon, armor or builder tool. The vanilla chest uses 25.
- **Tool category**: every vanilla tool carries specs for *all* gather types, so read the category from `Item.getPlayerAnimationsId()`, which is inherited from the parent: `"Pickaxe"`, `"Hatchet"` (axe), `"Shovel"`. Tool items are `Server/Item/Items/Tool/{Pickaxe,Hatchet,Shovel}/Tool_<Kind>_<Material>.json`, with `Categories: ["Items.Tools"]` and `Tags.Type: ["Tool"]`.
- **Tool tier**: the `Quality` of the spec for the matching gather type (for example the pickaxe's `Rocks` spec: Crude 1, Iron 3). Speed: that spec's `getPower()` (or `ItemTool.getSpeed()`).
- **Max durability**: `Item.getMaxDurability()` (double; Crude 150, Iron 250; `<= 0` means unbreakable, see `ItemStack.isUnbreakable()`). Loss per hit: the `ItemTool.getDurabilityLossBlockTypes()[i].getDurabilityLossOnHit()` that matches the block set (the vanilla pickaxe has 0.25 for the Stone/Rock/Ores/Soil/Wood sets), otherwise `Item.getDurabilityLossOnHit()`. Suggested core value: `durability = (int)(maxDurability / lossOnHit)` hits.
- **Ore**: `breaking.getGatherType().startsWith("Ore")` (26 vanilla ore blocks under `Items/Ore/**`).
- **Kind**:
  - `AIR`: id `Empty`;
  - `FLUID`: the pseudo-key above;
  - `UNBREAKABLE`: `getGathering() == null` (for example `Rock_Bedrock`, which has no Gathering) or gather type `"Unbreakable"`, which appears in the portal blocks and has no spec;
  - `NON_SOLID`: `getMaterial() == BlockMaterial.Empty`, where `com.hypixel.hytale.protocol.BlockMaterial` is `{Empty, Solid}` (151 plants and decorations);
  - `SOLID`: everything else.
- **Harmful fluid** (`isHarmful`): `Fluid.getDamageToEntities()` (`DamageToEntities`, inherited) is **0 for every vanilla fluid** in 0.6.8. Lava (`Lava_Source`, and `Lava` through `Parent`) and `Fire` burn through an `Interactions.Collision` entry (`ApplyEffect` `Lava_Burn` / `Burn`). `Fluid.isTrigger()` is true when any interaction key is a collision type (`Fluid.processConfig`, `afterDecode`), and the collision module checks the same pair (`CollisionConfig`: `fluid.getDamageToEntities() > 0` or `newFluid.isTrigger()`). Water, Poison, Slime, Tar have neither. Source: `server/core/asset/type/fluid/Fluid.java`, `Server/Item/Block/Fluids/*.json`. **[in-game]** that `isTrigger()` is true for the inherited `Lava` fluid (not only `Lava_Source`).

```java
Optional<ToolInfo> tool(ItemKey k) {
    Item it = Item.getAssetMap().getAsset(k.id());
    if (it == null || it.getTool() == null) return Optional.empty();
    ToolType type = switch (String.valueOf(it.getPlayerAnimationsId())) {
        case "Pickaxe" -> ToolType.PICKAXE; case "Hatchet" -> ToolType.AXE; case "Shovel" -> ToolType.SHOVEL; default -> null; };
    if (type == null) return Optional.empty();
    String gather = switch (type) { case PICKAXE -> "Rocks"; case AXE -> "Woods"; case SHOVEL -> "Soils"; };
    for (ItemToolSpec s : it.getTool().getSpecs())
        if (gather.equals(s.getGatherType())) return Optional.of(new ToolInfo(type, s.getQuality(), s.getPower()));
    return Optional.of(new ToolInfo(type, 0, it.getTool().getSpeed()));
}
```

## 3. ContainerAccess (ItemContainerBlock)

- `BlockModule.getComponent(ItemContainerBlock.getComponentType(), World world, int x, int y, int z)` → `@Nullable ItemContainerBlock`. It returns null when the section is not loaded (no load) or the block has no container. `ProtectionSystems` already uses it.
- `ItemContainerBlock.getItemContainer()` → `SimpleItemContainer`, created lazily. `getCapacity()`, `getWindows()` → `Map<UUID, ContainerBlockWindow>`.
- `ItemContainer`:
  - `ItemStackTransaction addItemStack(ItemStack)`: defaults allOrNothing=false, fullStacks=false, filter=true. `tx.getRemainder()` is empty when everything fit (`ItemStack.isEmpty(rem)`). It throws `IllegalArgumentException` if `getItem()` is null.
  - `removeItemStack(ItemStack)`: defaults **allOrNothing=true**. Use `removeItemStack(stack, false, true)` for a partial remove; the remainder is null when everything was removed.
  - `countItemStacks(Predicate<ItemStack>)`, `forEach((short slot, ItemStack s) -> …)` (non-empty slots only), `getItemStack(short)`, `removeItemStackFromSlot(short slot, int qty)`, `getCapacity()`, `removeAllItemStacks()`.
- **Matching trap**: removal matches through `ItemStack.isStackableWith`, which compares **durability, maxDurability, quality and metadata**. A worn tool will not match `new ItemStack(id, n)`. Remove by slot, matching on `getItemId()`. `new ItemStack(id, qty)` throws when qty ≤ 0 or id is `"Empty"`.

```java
int extract(List<BlockPos> cs, ItemKey item, int max) {
    int left = max;
    for (BlockPos p : cs) {
        ItemContainerBlock b = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
        if (b == null) continue;
        ItemContainer c = b.getItemContainer();
        for (short s = 0; s < c.getCapacity() && left > 0; s++) {
            ItemStack st = c.getItemStack(s);
            if (ItemStack.isEmpty(st) || !st.getItemId().equals(item.id())) continue;
            int n = Math.min(left, st.getQuantity());
            c.removeItemStackFromSlot(s, n);
            left -= n;
        }
    }
    return max - left;
}
ItemAmount insert(List<BlockPos> cs, ItemAmount a) {
    ItemStack rem = new ItemStack(a.item().id(), a.count());
    for (BlockPos p : cs) {
        ItemContainerBlock b = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
        if (b == null) continue;
        rem = b.getItemContainer().addItemStack(rem).getRemainder();
        if (ItemStack.isEmpty(rem)) return null;
    }
    return new ItemAmount(a.item(), rem.getQuantity());
}
// count: c.countItemStacks(s -> s.getItemId().equals(id)); contents: c.forEach((slot, s) -> map.merge(s.getItemId(), s.getQuantity(), Integer::sum))
```

## 4. PlayerInventory

- `InventoryComponent.getCombined(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ComponentType<…>... types)` → `CombinedItemContainer` (an `ItemContainer`), cached per entity. `InventoryComponent.HOTBAR_FIRST = {Hotbar, Storage}`. Other orders exist: `HOTBAR_STORAGE_BACKPACK`, `EVERYTHING`, and so on. `BarterPage` uses exactly `getCombined(store, ref, InventoryComponent.HOTBAR_FIRST)`.
- The API is the same `ItemContainer` API as §3. `forEach` walks the hotbar slots, then storage: "inventory order".
- UUID → Ref/Store, and "in this world": `Universe.get().getPlayer(uuid)` → `@Nullable PlayerRef`; `pr.getReference()` → `@Nullable Ref<EntityStore>`; `ref.getStore()`. The player is in this world when `ref.getStore() == world.getEntityStore().getStore()`. Alternatives: `pr.getWorldUuid()`, or `world.getPlayerRefs()` as used by `HytalePlayerDirectory`.

```java
private ItemContainer inv(UUID player) {
    PlayerRef pr = Universe.get().getPlayer(player);
    Ref<EntityStore> ref = pr == null ? null : pr.getReference();
    Store<EntityStore> store = world.getEntityStore().getStore();
    if (ref == null || !ref.isValid() || ref.getStore() != store) return null;   // offline or other world
    return InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
}
ItemAmount give(UUID p, ItemAmount a) {
    ItemContainer c = inv(p);
    if (c == null) return a;
    ItemStack rem = c.addItemStack(new ItemStack(a.item().id(), a.count())).getRemainder();
    return ItemStack.isEmpty(rem) ? null : new ItemAmount(a.item(), rem.getQuantity());
}
// take: the same slot loop as ContainerAccess.extract (worn tools!)
```
The legacy `Inventory` class is deprecated; its methods now delegate to `InventoryUtils`.

## 5. Prefabs (BlueprintSource)

- `PrefabStore.get().findAssetPrefabPath(String key)` → `@Nullable Path` (searches every asset pack under `Server/Prefabs/`). Its javadoc says it returns the `.prefab.json` path even when only a `.lpf` sibling exists. `getAssetPrefabFromAnyPack(key)` → `BlockSelection` also exists, but it is not needed.
- `PrefabBufferUtil.getCached(Path)` → `IPrefabBuffer` (a new `PrefabBufferAccessor` on a weakly cached `PrefabBuffer`). The first call parses the JSON and writes an LPF cache under `.cache/prefabs`, so it can be slow: load it off the world thread.
- `IPrefabBuffer.forEach(ColumnPredicate<T>, BlockConsumer<T>, @Nullable EntityConsumer<T>, @Nullable ChildConsumer<T>, T call)` with `T extends PrefabBufferCall`.
  - `new PrefabBufferCall(Random random, PrefabRotation rotation)` (public fields `random`, `rotation`). **`random` must be non-null**: entries with a `chance` are skipped when `chance < random.nextFloat()`. Use a fixed seed so each pass sees the same blueprint.
  - `IPrefabBuffer.iterateAllColumns()` is the column predicate.
  - `BlockConsumer.accept(int x, int y, int z, int blockId, @Nullable Holder<ChunkStore> holder, int supportValue, int rotation, int filler, T call, int fluidId, int fluidLevel)`, the parameter order confirmed in `PrefabBuffer.PrefabBufferAccessor.forEach`. `x` and `z` are **already rotated**. All coordinates are **relative to the anchor**: the deserializer subtracts `anchorX/Y/Z`. `rotation` is a `RotationTuple` index that is already rotated (`PrefabRotation.getRotation`). `filler` is also rotated.
  - `blockId` is the **runtime** `BlockType` index (`PrefabUtil.paste` calls `BlockType.getAssetMap().getAsset(blockId)`), so the key is `getAsset(blockId).getId()`. 0 means `Empty`.
- **Filler flag**: place only `filler == 0` entries; `setBlock` rebuilds the fillers. Filler entries of multi-block furniture (doors, beds, tables) appear in the prefab with `filler != 0`.
- `holder` carries prefab block-entity components (for example `{"BlockSpawner":{…}}`, `{"RespawnBlock":{}}`, chest contents). Apply one with `BlockEntity.setBlockEntity(ComponentAccessor<ChunkStore>, Ref<ChunkStore> sectionRef, BlockComponentSection, x, y, z, BlockType, int rotation, Holder<ChunkStore>)` (`…/modules/block/BlockEntity.java`), as `PrefabUtil` does.
- Filter `Block_Spawner_Block`: it is a random-block placeholder resolved by `BlockSpawnerPlugin`, and the vanilla houses contain 1 to 4 of them. Filter `Editor_*` too.
- **Bounds per rotation**: `getMinX(PrefabRotation)`, `getMaxX(rot)`, `getMinZ(rot)`, `getMaxZ(rot)`, `getMinY()`, `getMaxY()` (anchor-relative, inclusive); `getAnchorX/Y/Z()`.
- `PrefabRotation`: `ROTATION_0/90/180/270`, `VALUES`, `fromRotation(Rotation)`, `getRotation()`, `getX(x,z)`/`getZ(x,z)`, `rotate(Vector3i)`. Hut yaw → rotation: `PlaceBlockEvent.getRotation()` returns a `RotationTuple`, whose `yaw()` is a `Rotation` that goes to `PrefabRotation.fromRotation(...)`. SP0 stores `yaw().ordinal()` as the int.

```java
Optional<Blueprint> load(String key, int rot) {
    Path path = PrefabStore.get().findAssetPrefabPath(key);                  // "Npc/Outlander/Houses/Tier1/Outlander_Houses_Tier1_001.prefab.json"
    if (path == null) return Optional.empty();
    IPrefabBuffer buf = PrefabBufferUtil.getCached(path);
    PrefabRotation r = PrefabRotation.VALUES[rot & 3];
    List<Entry> out = new ArrayList<>();
    buf.forEach(IPrefabBuffer.iterateAllColumns(),
        (x, y, z, blockId, holder, support, rotation, filler, call, fluidId, fluidLevel) -> {
            if (filler != 0) return;
            String k = BlockType.getAssetMap().getAsset(blockId).getId();
            if (k.equals("Block_Spawner_Block") || k.startsWith("Editor_")) return;
            out.add(new Entry(x, y, z, k, rotation));                        // "Empty" = must be air
        }, null, null, new PrefabBufferCall(new Random(0), r));
    // bounds: buf.getMinX(r)..buf.getMaxX(r), buf.getMinY()..buf.getMaxY(), buf.getMinZ(r)..buf.getMaxZ(r)
    return Optional.of(new Blueprint(out /*…*/));
}
```

**Prefab paths confirmed in the assets zip** (entries under `Server/Prefabs/`). Every file in the research table exists, and the research block counts equal the entries minus `Empty`:

| Level | Outlander | entries (Empty) | Kweebec Redwood | entries (Empty) |
|---|---|---|---|---|
| 1 | `Npc/Outlander/Houses/Tier0/Outlander_Houses_Tier0_005.prefab.json` | 808 (40) | `Npc/Kweebec/Redwood/Small_Plot/House/Kweebec_Redwood_Small_Plot_House_001.prefab.json` | 391 (57) |
| 2 | `…/Tier1/Outlander_Houses_Tier1_001` | 1193 (196) | `…/Small_Plot/House/…_Small_Plot_House_002` | ✓ |
| 3 | `…/Tier2/Outlander_Houses_Tier2_001` | 1945 (475) | `…/Normal_Plot/House/Kweebec_Redwood_Normal_Plot_House_001` | ✓ |
| 4 | `…/Tier2/Outlander_Houses_Tier2_003` | 2580 (5) | `…/Normal_Plot/House/…_Normal_Plot_House_002` | ✓ |
| 5 | `…/Tier3/Outlander_Houses_Tier3_005` | 7030 (1) | `Npc/Kweebec/Redwood/Medium_House/Kweebec_Redwood_Medium_House_002` | 6722 (349) |

Also available: Outlander Tier0 001-006, Tier1 001-008, Tier2 001-003, Tier3 001-005; Kweebec Oak/Autumn/Swamp houses and Redwood `Large_House_001-003`. The JSON format is `{version, blockIdVersion, anchorX, anchorY, anchorZ, blocks:[{x,y,z,name[,rotation][,filler][,components]}]}`, where `name` is the BlockType key.

## 6. NPC: held item and animations

### Held item
`com.hypixel.hytale.server.npc.util.InventoryHelper`:
- `static boolean setHotbarItem(Ref<EntityStore> ref, @Nullable String itemId, byte slot, ComponentAccessor<EntityStore> acc)`: validates the item key and the slot, then calls `hotbar.setItemStackForSlot(slot, new ItemStack(id, 1))`.
- `static void setHotbarSlot(Ref<EntityStore> ref, byte slot, ComponentAccessor<EntityStore> acc)` makes that slot active.
- `static boolean useItem(ref, @Nullable String name, acc)`: finds or creates the item in the hotbar and selects it. A null or empty name calls `clearItemInHand` instead.
- `static boolean clearItemInHand(ref, byte slotHint, acc)`.
- The client sees the change through `InventoryUtils` building an `EquipmentUpdate` with `rightHandItemId = getItemInHand(...)`.
- Role JSON: `HotbarSize` is an int in 3..8 and **defaults to 3**, so our `HyColony_Citizen` role already has a hotbar and needs no change. `InventorySize` is 0..36 (default 0). Also available: `OffHandSlots` 0..4, `HotbarItems` [], `OffHandItems` [], `DefaultOffHandSlot` -1, `PossibleInventoryItems` (drop list). Source: `…/server/npc/role/builders/BuilderRole.java`.

```java
public void setHeldItem(BodyId body, Optional<ItemKey> item) {
    Ref<EntityStore> ref = ref(body);
    if (ref == null) return;
    if (item.isEmpty()) { InventoryHelper.clearItemInHand(ref, (byte) 0, store()); return; }
    if (InventoryHelper.setHotbarItem(ref, item.get().id(), (byte) 0, store())) InventoryHelper.setHotbarSlot(ref, (byte) 0, store());
}
```

### Animation
- `com.hypixel.hytale.server.core.entity.AnimationUtils.playAnimation(Ref<EntityStore> ref, AnimationSlot slot, @Nullable String itemAnimationsId, @Nullable String animationId, boolean sendToSelf, ComponentAccessor<EntityStore> acc)`. There is also a 5-argument overload `(ref, slot, String itemAnimationsId, String animationId, acc)`.
- `com.hypixel.hytale.protocol.AnimationSlot` is `{Movement, Status, Action, Face, Emote, ServerAction}`.
- For slots other than `Action` and `Emote`, the id must exist in the **model's** `AnimationSets`, otherwise the call only logs a warning. `Action` skips that check and sends `PlayAnimation(networkId, itemAnimationsId, animationId, slot)` to viewers.
- `NPCEntity.playAnimation(ref, slot, animationId, acc)` does not take an item-animations id.
- None of the models has build or mine animations:
  - `PlayerTestModel_V` has `Parent: Player` and defines only `Crouch*`;
  - `Player` has locomotion, `Sit`/`Sleep`, `Death`/`Hurt` and so on;
  - `Kweebec_Sapling` also has `Interact`, `Wave`, `Cheer`, `Feed`, `Console`, …;
  - `Outlander` models have `Eat`, `Kneel`, … .
- The work animations live in the **item animation sets** `Server/Item/Animations/<Id>.json`:
  - `Block`: `Build`, `Interact`, `SwingRight`/`SwingLeft`/`SwingDown`;
  - `Pickaxe` (parent `Sword`): `Mine`;
  - `Hatchet` (parent `Sword`): `Chop`;
  - `Shovel`: `Dig`;
  - `Default`: `Interact`, `SwingLeft`/`SwingRight`.

```java
public void playAnimation(BodyId body, BodyAnimation a) {
    Ref<EntityStore> ref = ref(body);
    if (ref == null) return;
    switch (a) {
        case BUILD -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Block", "Build", store());
        case MINE  -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Pickaxe", "Mine", store());
    }
}
```
**[in-game]** No vanilla server code calls `playAnimation` with an item-animations id on an NPC. Whether the client plays `Block/Build` on a `PlayerTestModel_V` NPC still has to be tested. Fallback: `Default` + `Interact`/`SwingRight`.

## 7. Detecting container changes

- There is **no ECS event for block-container changes**. `InventoryChangeEvent` (…/event/events/ecs) covers **entity** inventories only: it is dispatched by `InventorySystems` for `InventoryComponent`s.
- Per container: `ItemContainer.registerChangeEvent(Consumer<ItemContainer.ItemContainerChangeEvent>)` → `EventRegistration` (`unregister()`). The record is `(ItemContainer container, Transaction transaction)`. Vanilla guards with `world.isInThread()` before touching world state, so do the same. The registration dies with the component, on chunk unload or break: register lazily again when the chunk comes back.
- Window close: `Window.registerCloseEvent(Consumer<Window.WindowCloseEvent>)` → `EventRegistration`. The open windows of a block are in `ItemContainerBlock.getWindows()` (`Map<UUID, ContainerBlockWindow>`), keyed by player UUID.
- **Recommended**: hut storage opens only through our "Open storage" button (§8), which creates the window itself, so hook `registerCloseEvent` there. For vanilla chests or racks opened with `Open_Container`, use a `UseBlockEvent.Post` system (pattern of `TownHallBlockSystems.Use`, query `PlayerRef`), then `getWindows().get(playerUuid)` and `registerCloseEvent`. **[in-game]** It is unverified whether the window is already in the map when `Post` fires: `UseBlockInteraction` calls `context.execute(root)` and then fires Post. The fallback is `registerChangeEvent`.

## 8. Hut block asset (chest-like, our UI on Use)

The vanilla model is `Server/Item/Items/Furniture/Crude/Furniture_Crude_Chest_Small.json`: `BlockEntity.Components.ItemContainerBlock.Capacity: 18`, `Interactions.Use: "Open_Container"` (`Server/Item/Interactions/Block/Open_Container.json` = `{"Type":"OpenContainer"}`), `State.Definitions.OpenWindow/CloseWindow` for the lid animation, plus a `ConnectedBlockRuleSet` that merges two chests into a large one. **Drop that rule set.** `ItemContainerBlock` JSON keys: `Capacity` (short > 0, default 20), `Droplist`, `ItemContainer`.

`Server/Item/Items/HyColony/HyColony_Hut_Builder.json` (mirrors `HyColony_TownHall.json`):
```json
{
  "TranslationProperties": { "Name": "hycolony.item.hut.builder.name" },
  "MaxStack": 1,
  "Icon": "Icons/ItemsGenerated/Furniture_Crude_Chest_Small.png",
  "Categories": [ "Furniture.Containers" ],
  "PlayerAnimationsId": "Block",
  "BlockType": {
    "Material": "Solid", "DrawType": "Model", "Opacity": "Transparent",
    "CustomModel": "Blocks/Decorative_Sets/Crude/Chest_Small.blockymodel",
    "CustomModelTexture": [ { "Texture": "Blocks/Decorative_Sets/Crude/Chest_Small_Texture.png", "Weight": 1 } ],
    "VariantRotation": "NESW",
    "Gathering": { "Breaking": { "GatherType": "Woods" } },
    "BlockEntity": { "Components": { "ItemContainerBlock": { "Capacity": 27 } } },
    "State": { "Definitions": {
      "CloseWindow": { "InteractionSoundEventId": "SFX_Chest_Wooden_Close", "CustomModelAnimation": "Blocks/Animations/Chest/Chest_Close.blockyanim" },
      "OpenWindow":  { "InteractionSoundEventId": "SFX_Chest_Wooden_Open",  "CustomModelAnimation": "Blocks/Animations/Chest/Chest_Open.blockyanim" } } },
    "Interactions": { "Use": "HyColony_Hut_Use" },
    "Support": { "Down": [ { "FaceType": "Full" } ] },
    "BlockParticleSetId": "Wood", "BlockSoundSetId": "Wood", "PhysicalMaterialId": "Wood"
  },
  "ItemSoundSetId": "ISS_Blocks_Wood"
}
```
Add `Interactions/HyColony/HyColony_Hut_Use.json` = `{"Type":"Simple"}` and `RootInteractions/HyColony/HyColony_Hut_Use.json` = `{"Interactions":["HyColony_Hut_Use"]}`, as for the town hall.

Why this works: the player's "Use" runs `UseBlockInteraction.doInteraction`, which fires `UseBlockEvent.Pre` **before** the block's root interaction. Cancelling it (as `TownHallBlockSystems.Use` does) stops the interaction, and we open our page. If the hut block were ever placed with `NO_UPDATE_STATE`, it would have no container.

**"Open storage" button**: this is what `OpenContainerInteraction.interactWithBlock` does. Classes: `ContainerBlockWindow` (…/entity/entities/player/windows), `Page` (`com.hypixel.hytale.protocol.packets.interface_`), `PageManager.setPageWithWindows(Ref, Store, Page, boolean canCloseThroughInteraction, Window...)` → boolean.

```java
// in the hut page's handleDataEvent(ref, store, data), when data.action == "storage":
BlockPos p = hutPos;
ChunkStore cs = world.getChunkStore();
Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
ItemContainerBlock c = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
if (sec == null || c == null) return;
BlockSection bs = cs.getStore().getComponent(sec, BlockSection.getComponentType());
BlockType type = BlockType.getAssetMap().getAsset(bs.get(p.x(), p.y(), p.z()));
ContainerBlockWindow w = new ContainerBlockWindow(p.x(), p.y(), p.z(), bs.getRotationIndex(p.x(), p.y(), p.z()), type, c.getItemContainer());
UUID uuid = playerRef.getUuid();
Map<UUID, ContainerBlockWindow> windows = c.getWindows();
if (windows.putIfAbsent(uuid, w) != null) return;                       // already open
Player player = store.getComponent(ref, Player.getComponentType());
if (player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, w)) {
    w.registerCloseEvent(e -> { windows.remove(uuid, w); core.onHutStorageClosed(p); });   // MUST remove, or it never reopens
    // optional lid animation: BlockOperations.setBlockInteractionState(cs, sec, x, y, z, type, "OpenWindow", false) (and "CloseWindow" on close)
} else windows.remove(uuid, w);
```
`PageManager.openCustomPageWithWindows(Ref, Store, CustomUIPage, Window...)` also exists and would show our page together with the container window. **[in-game]** How the client lays that out is unknown.

## 9. UI (.ui and page Java)

- Layout files live under `Common/UI/Custom/` in our pack. They are appended by the path relative to that folder: `ui.append("Pages/HyColony/TownHall.ui")`. This is verified: it works in SP0.
- **Dynamic lists**: an empty container `Group #List { LayoutMode: TopScrolling; ScrollbarStyle: $C.@DefaultScrollbarStyle; }`. Then add rows with `ui.append("#List", "Pages/HyColony/Row.ui")` and address them as `"#List[" + i + "] #Child.Prop"`. Use `ui.clear("#List")` before a rebuild (as `BarterPage` does). Other `UICommandBuilder` calls: `set(selector, String|Message|boolean|int|float|double)`, `setNull`, `remove`, `appendInline`, `insertBefore`.
- **Buttons with parameters**:
  - bind with `events.addEventBinding(CustomUIEventBindingType.Activating, "#List[3] #Button", EventData.of("Action", "provide").append("Index", "3"), false)`. The last argument is `locksInterface`; `RightClicking` also exists;
  - keys starting with `@` read a UI value: `new EventData().append("@Name", "#RenameInput.Value")`, as in TownHallPage;
  - decode with `BuilderCodec` + `KeyedCodec<>("Index", Codec.STRING)` and parse the int yourself, as `BarterPage.BarterEventData` does;
  - after each event call `sendUpdate(new UICommandBuilder(), false)`, or refresh with `this.build(...)` into fresh builders followed by `sendUpdate(cmd, events, true)` (BarterPage `refreshUI`). `rebuild()` and `close()` are protected helpers on `CustomUIPage`.
- **Button elements** from `Common.ui`: `$C.@TextButton`, `$C.@SecondaryTextButton`, `$C.@BackButton`, `$C.@TextField`, `$C.@PageOverlay`, `$C.@DecoratedContainer`, `$C.@Title`. Plain `Button #Id { … }` also exists, as in `ItemRepairElement.ui`.
- **Item icons** (vanilla `Common/UI/Custom/Pages/*.ui`):
  - `ItemIcon #Icon { Anchor: (Width: 32, Height: 32); }`, set with `ui.set(sel + " #Icon.ItemId", itemId)` (`ItemRepairElement.java`);
  - `ItemSlot #Slot { ShowQualityBackground: true; ShowQuantity: false; }`, set with `ui.set(sel + " #Slot.ItemId", itemId)` (`BarterPage`, `RespawnPage`). The vanilla pages overlay the quantity with a `Label`;
  - `ItemSlotButton #Btn { … }`: a clickable slot, used as the row button in `BarterTradeRow.ui`;
  - `ItemGrid` (`SlotsPerRow`, `Style: (SlotSize, SlotIconSize, …)`): used for window-backed grids; not needed here.
- **Progress bar**: the `Common.ui` template `@ProgressBar` (a `ProgressBar` 284 × 6 with the `Common/ProgressBar*.png` textures). Vanilla use, from `Pages/UIGallery/Categories/ProgressContent.ui`: `$C.@ProgressBar #Bar { @Anchor = (Bottom: 4, Left: 0); Value: 0.75; }`. `Value` is a float in [0, 1], set with `ui.set(sel + " #Bar.Value", 0.5f)` (`MemoriesPage`, `PrefabEditorSaveSettingsPage`). Used by `SkillRow.ui`.

Row for the builder's resource list (red/orange/green like MineColonies):
```
Group { LayoutMode: Left; Anchor: (Height: 40);
  ItemIcon #Icon { Anchor: (Width: 32, Height: 32); }
  Label #Name { FlexWeight: 1; Padding: (Horizontal: 8); }
  Label #Count { Style: (TextColor: #94a7bb); }
  $C.@TextButton #AddButton { @Anchor = (Left: 8); Text: %hycolony.ui.resources.add; }
}
```
```java
ui.append("#Resources", "Pages/HyColony/ResourceRow.ui");
ui.set(row + " #Icon.ItemId", r.item().id());
ui.set(row + " #Count.Text", r.available() + " / " + r.needed());
ui.set(row + " #Count.Style.TextColor", color);                        // "#962f2f" style strings, as in BarterPage
events.addEventBinding(CustomUIEventBindingType.Activating, row + " #AddButton", EventData.of("Add", String.valueOf(i)), false);
```
A row using `$C.@TextButton` must declare `$C = "../../Common.ui";` at its top, as `TownHall.ui` does. `CitizenRow.ui` has no such line because it uses no `$C` templates.

---

## Could not verify

1. **Client rendering of item animations on NPCs** (`AnimationSlot.Action` with `"Block"/"Build"` or `"Pickaxe"/"Mine"` on `PlayerTestModel_V`) and whether they loop or play once. Only the server packet path is verified.
2. Whether the NPC's held item shows in its hand without an explicit equipment sync. `EquipmentUpdate` is built from `getItemInHand`, but I did not trace when NPC equipment is re-sent.
3. `UseBlockEvent.Post` timing relative to the opened `ContainerBlockWindow` (§7).
4. `openCustomPageWithWindows` layout on the client (§8).
5. Thread on which `ItemContainerChangeEvent` fires for player window actions. Vanilla code handles both cases, so guard with `world.isInThread()`.
6. `BlockOperations.setBlock` is marked "Not yet stable", and Update 7 is expected to change it.
7. Load cost of `PrefabBufferUtil.getCached` for large prefabs, and whether calling it off the world thread is safe. There is lock-based caching, but I did not audit `loadBuffer` for world-thread assumptions.
8. The hardness formula and the tool-type mapping from gather types are **our heuristics**: Hytale has no equivalent concept. `Pickaxe_Tier0` and `SoftWoods` (1 and 2 blocks) are unmapped.
9. The unarmed `ItemToolSpec` JSONs have no `GatherType` field, but the asset store is keyed by `getGatherType`. I did not trace how the key is filled in (probably from the file name).
10. Fluid replication after `FluidSection.setFluid` outside `PrefabUtil`: it is the same call, but no client check was done.
11. The `hytale-docs` MCP search returned only generic pages for these topics. Nothing in this sheet relies on it.

## Verified in game (2026-09-26)

- A `Message` with a nested `Message` param (`param(key, Message)`, stored in `messageParams`) renders as `{key}` when set on a label `.Text`. Set it on `.TextSpans` instead (vanilla `PortalDeviceActivePage` does this). Buttons: avoid nested params, use one full key per variant.
