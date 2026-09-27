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
  - Effets (vérifié 2026-09-27) : `BlockOperations.setBlock` n'envoie les particules de bloc (et celles des cellules de remplissage, `FillerBlockUtil.ChangeReason.NONE`) que sans `NO_SEND_PARTICLES` ; `BlockHarvestUtils.naturallyRemoveBlock` ne joue le son 3D `BlockSoundEvent.Break` que sans `NO_SEND_AUDIO`, puis passe les réglages à `setBlock`. `setBlock` ne joue aucun son. Le collage créatif (`WorldBlocks.placeQuietly`/`breakQuietly`) utilise `NO_SEND_PARTICLES | NO_SEND_AUDIO`.

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
- **Harmful fluid** (`isHarmful`): `Fluid.getDamageToEntities()` (`DamageToEntities`, inherited) is **0 for every vanilla fluid** in 0.6.8. Lava (`Lava_Source`, and `Lava` through `Parent`) and `Fire` burn through an `Interactions.Collision` entry (`ApplyEffect` `Lava_Burn` / `Burn`). `Fluid.isTrigger()` is true when any interaction key is a collision type (`Fluid.processConfig`, `afterDecode`), and the collision module checks the same pair (`CollisionConfig`: `fluid.getDamageToEntities() > 0` or `newFluid.isTrigger()`). Water, Poison, Slime, Tar have neither. Source: `server/core/asset/type/fluid/Fluid.java`, `Server/Item/Block/Fluids/*.json`. `isTrigger()` is also true for the inherited `Lava` fluid, not only `Lava_Source`: `BuilderCodec.decodeAndInheritJson` (`codec/builder/BuilderCodec.java:516-526`) copies the parent's fields (`inherit`) before decoding the child and then runs `afterDecodeAndValidate`; `Interactions` is inherited from the parent (`Fluid.java:168-172`), so `Fluid::processConfig` (`afterDecode`) sees `Lava_Source`'s `Collision` entry. The collision module then checks `newFluid.isTrigger()` (`server/core/modules/collision/CollisionConfig.java:248-249`).
- **Bloc dangereux** (`isHarmful` hors fluide) : même paire sur le `BlockType` : `getDamageToEntities() > 0` ou `isTrigger()`, vrai dès qu'une clé d'`Interactions` est de type collision (`BlockType.java:2007-2011`). Blocs vanilla concernés en 0.6.8 : `Deco_Fire` (`Collision` → `ApplyEffect Burn`), `Deco_Campfire_Off` (`"Collision": "Block_Damage"`), braseros (`Furniture_Crude_Brazier`, `Furniture_Village_Brazier`), cactus et ronces (`Environmental_Block_Damage`), pièges (`Trap/*`), et quelques plantes sans dégâts (algue ralentissante, fleur d'eau). `Bench_Campfire` (le feu de camp de cuisine) n'a **aucune** interaction de collision ni `DamageToEntities` : d'après les données, il ne brûle pas.
- **Navigation des PNJ et blocs dangereux** : la navigation évite déjà les dégâts (`AvoidBlockDamage`, vrai par défaut et déprécié au profit de `RelaxedConstraints`, `BuilderBodyMotionFindBase.java:121`), mais seulement pour les blocs dont `BlockCollisionData.willDamage` est vrai, c'est-à-dire `DamageToEntities > 0` (`CollisionConfig.java:248-250`, bit `MATERIAL_DAMAGE`), et pour l'escalade (`MotionControllerWalk.isClimbable`, `:2844`). Les interactions de collision (feu, braseros, feu de camp éteint) ne comptent pas : le chemin peut les traverser. Aucune option de rôle ni du contrôleur `Walk` ne change cela (options listées dans `BuilderMotionControllerWalk` et `BuilderBodyMotionFindBase` ; `FenceBlockSet` ne sert qu'à l'escalade). Le seul autre levier, `CollisionResult.setBlockCollisionFilter`, ne peut que faire *ignorer* des blocs. Donner un `DamageToEntities` à ces blocs changerait les dégâts du jeu. HyColony écarte donc les cibles dangereuses (`DangerousCells`) et découpe lui-même chaque marche en segments droits qui contournent les colonnes dangereuses (`SafeRoute`, `DetouringBodies`), que `Seek` suit un par un. **[in-game]**

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
- **Nested appends**: an appended document can hold its own lists. Vanilla `WorldEventInspectorPage` appends a card into `#SideContainer`, then appends items into `"#SideContainer[" + i + "] #Items"` and addresses them as `"#SideContainer[i] #Items[j] #Child"`; the appended root itself takes properties (`"#List[i].Text"`, `.Style`, `.Anchor` in `PrefabTeleportPage`, `PointInspectorPage`; `.Visible` on such a root is not in vanilla, HyColony relies on it, to confirm in game). HyColony's hut tabs use it: each tab `.ui` is appended into `#ModuleTabs` and filled under `#ModuleTabs[k]` (2026-09-27).
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

## 10. Lunettes de constructeur : aperçu en mémoire et casque

Vérifié dans les sources 0.6.8 (détails dans `docs/research/build-goggles-and-wand.md`, partie B).

- **Entité d'aperçu en mémoire** (`plugin/.../adapter/HytalePreviewPort`) : `NetworkId(store.getExternalData().takeNextNetworkId())`, `EntityStore.REGISTRY.getNonSerializedComponentType()` + `NonSerialized.get()` (motif de `builtin/model/pages/ChangeModelPage.java:211-217`), `TransformComponent(Vector3d, new Rotation3f())` et `new PrefabPreview(BlockChange[], FluidChange[], visibleLayerCount, biomeTint, waterTint)` (`server/core/modules/entity/component/PrefabPreview.java:46`). `PrefabPreviewTracker` n'exige que `Visible ∧ PrefabPreview` (`prefabpreview/PrefabPreviewSystems.java:169-173`). Couches : `Integer.MAX_VALUE` = toutes (défaut de `PrefabPreviewCommand`). Teintes par défaut 6004264 et 668501 (constantes privées de `PrefabPreviewSystems`, recopiées). **[in-game]** Le client affiche-t-il une entité qui n'a jamais eu de `PersistentPrefabPreview` ? Le coin du bloc de la hutte est-il la bonne ancre des offsets ?
- **Changements structurels** : `store.addEntity` et `removeEntity` lèvent « Store is currently processing » dans un système ; l'adaptateur passe par `world.execute`, comme `HytaleCitizenBodies`.
- **`BlockChange(x, y, z, blockId, (byte) rotation)`** (`protocol/packets/interface_/BlockChange.java:26`) ; l'id vient de `BlockType.getAssetMap().getIndex(key)`, qui vaut `Integer.MIN_VALUE` si la clé est inconnue (`assetstore/map/BlockTypeAssetMap.java:41`).
- **Filtre de visibilité** : `EntityTickingSystem` du groupe `EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP`, `SystemDependency(AFTER, CollectVisible.class)`, requête `EntityViewer ∧ PlayerRef`. On retire des refs de `EntityViewer.visible` (public) et on incrémente `hiddenCount` (`server/core/modules/entity/system/HideEntitySystems.java`). `isParallel` vaut `false` par défaut (`component/system/tick/EntityTickingSystem.java:21`). **[in-game]**
- **Casque** : `InventoryChangeEvent` (ECS) émis par `InventorySystems.ArmorChangeEventSystem`. `event.getComponentType()` vaut `InventoryComponent.Armor.getComponentType()` ; `event.getInventory().getInventory().getItemStack((short) 0)` est la tête. Écoute par `EntityEventSystem<EntityStore, InventoryChangeEvent>` (motif `ObjectiveInventoryChangeSystem`).
- **Entrée dans un monde** : `PlayerReadyEvent` (`registerGlobal`, clé = nom du monde), émis par `Player.handleClientReady` quand l'entité est déjà dans le monde (`server/core/entity/entities/Player.java:377-390`). `getPlayerRef()` donne la `Ref`, `getPlayer().getWorld()` le monde. `AddPlayerToWorldEvent` arrive trop tôt : le holder n'est pas encore ajouté (`World.java:1349`).
- **Assets** : Hytale 0.6.8 n'a ni verre ni vitre (aucun objet `*Glass*`), les lentilles sont `Ingredient_Crystal_Cyan`. Pas d'icône de lunettes : `Icons/ItemsGenerated/Armor_Diving_Crude_Head.png`. Le `Model` et la `Texture` d'un objet doivent être sous `Blocks/`, `Items/`, `Resources/`, `NPC/`, `VFX/` (ou `BlockTextures/`, `Consumable/`) : `Cosmetics/Head/Goggles.blockymodel` est refusé à la validation (vu en jeu, 2026-09-26). Le modèle et sa texture sont donc copiés dans `Common/Items/HyColony/Build_Goggles*` ; leur racine `Head` est la même que celle des casques vanilla **[in-game]**.

---

## 11. Particules : feux d'artifice

- `ParticleUtil.spawnParticleEffect(String name, Vector3dc position, ComponentAccessor<EntityStore> accessor)` (`server/core/universe/world/ParticleUtil.java`) : collecte les joueurs à moins de `DEFAULT_PARTICLE_DISTANCE` (75 blocs) via `EntityModule.getPlayerSpatialResourceType()`, puis envoie à chacun un paquet `SpawnParticleSystem(name, Position, …)` par `PlayerRef.getPacketHandler().writeNoCache`. Aucun contrôle de l'id côté serveur : un id inconnu ne lève rien (effet côté client **[in-game]**). L'accesseur est `world.getEntityStore().getStore()` (thread du monde). `Vector3dc` est `org.joml`.
- Id = nom du fichier `.particlesystem` (appels vanilla : `"Splash"` = `Server/Particles/_Test/WaterRnD/Splash.particlesystem`). `ParticleSystem.getAssetMap().getAsset(id)` (`asset/type/particle/config/ParticleSystem.java`) sert à valider.
- Assets 0.6.8, `Server/Particles/Spell/Fireworks/` : systèmes `Firework_Mix2` (traînée + rouge/violet, `StartDelay` 0,8 à 0,9 s), `Firework_Mix3` (bleu, violet, rouge, `StartDelay` 0,5 à 1,3 s), `Firework_Mix4` (rouge + violet + éclairs), `Firework_GS` (niveaux de gris, à teinter). `Firework_Red`, `Firework_Purple`, `Firework_Yellow` sont des **spawners** (`Spawners/*.particlespawner`), pas des systèmes : non utilisables seuls.

## 12. Fenêtre de hutte en onglets (vérifié dans le code serveur)

- **Onglets** : motif de `builtin/triggervolumes/ui/TriggerVolumeInspectorPage.java:518-535` (`buildTabs`) et de l'asset `Pages/TriggerVolume/TriggerVolumeInspectorTabButton.ui` (`$C.@SmallSecondaryTextButton #TabButton`, `TextTooltipStyle: $C.@DefaultTextTooltipStyle`), copié dans `Pages/HyColony/TabButton.ui` (seule la largeur passe de 96 à 150). On ajoute un bouton par onglet dans `#TabButtons`, on pose `.Text`, `.Disabled = (onglet choisi)` et un `Activating` ; chaque onglet est un `Group` dont on pose `.Visible` (`TriggerVolumeInspectorPage.java:696-698`). **[in-game]** rendu du bouton désactivé comme onglet actif.
- **Redessiner la page** : `CustomUIPage.rebuild()` (`server/core/entity/entities/player/pages/CustomUIPage.java:118`) rappelle `build` et envoie la page entière (`clear = true`). Sert au changement d'onglet et à la sous-vue « Options de construction ».
- **Page ouverte** : `PageManager.getCustomPage()` (`.../pages/PageManager.java:79`, `@Nullable`) rend la page affichée ; `HytaleUiPort` la passe à la nouvelle page pour garder l'onglet quand le cœur ré-affiche la fenêtre.
- **Mise à jour en place** : `openCustomPage` (`PageManager.java:129-134`) appelle `onDismiss` de l'ancienne page et envoie `CustomPage(isInitial = true, clear = true)` ; `rebuild()` envoie `isInitial = false`. `PageManager` n'a pas de setter de page : pour remplacer le contenu sans réouvrir, `ColonyPage.refreshWith` garde l'objet page enregistré et lui fait relayer ses événements (`handleDataEvent(ref, store, String)`) à la nouvelle page, qui se dessine par `rebuild()`. Chaque envoi incrémente `customPageRequiredAcknowledgments` ; tant que le client n'a pas acquitté, `handleEvent` ignore les événements `Data` (`PageManager.java`, `case Data`) : un clic pendant un rafraîchissement est perdu, d'où un rafraîchissement seulement quand la vue change. Fermeture par le joueur : `Dismiss` remet `customPage` à `null`. **[in-game]** absence de clignotement et de perte de focus avec `isInitial = false`. Un joueur passé dans un autre monde garde un `Ref` valide dont le `Store` appartient au thread de l'autre monde : `Store.getComponent` y lève `IllegalStateException` (`assertThread`). On teste d'abord `ref.getStore().getExternalData().getWorld().isInThread()` (`util/thread/TickingThread.java:223`, simple comparaison de thread ; `Store.getExternalData` et `EntityStore.getWorld` sont de simples accesseurs).
- **Infobulle** : `cmd.set(sel + ".TooltipText", Message.translation(...))` (`TriggerVolumeInspectorPage.java:729`), sur un bouton qui déclare `TextTooltipStyle`. **[in-game]** infobulle d'un bouton `Disabled`.
- **Couleur de fond** : `cmd.set(sel + ".Background", "#2a5a3a")` sur un `Group` qui a un `Background` (`builtin/adventure/shop/barter/BarterPage.java:122`, `Pages/BarterTradeRow.ui`). Sert au cadre vert de l'ordre en cours (`BuilderOrderRow.ui`, posé sur la racine `#BuilderOrders[i]`, comme `#TabButtons[i].Text`). **[in-game]** rendu du cadre.

## 13. Configuration, explosions, spawn, opérateurs (vérifié dans le code serveur)

- **Config en sections** : une section est un `KeyedCodec` vers un autre `BuilderCodec` (`builtin/adventure/camera/asset/camerashake/CameraShake.java:25`). `BuilderCodec.decodeJson` (`codec/builder/BuilderCodec.java:327-339`) crée l'objet par le fournisseur puis n'écrit que les clés présentes ; une clé inconnue est sautée (`:383-386`, `:427-432`). `BuilderField.encode` (`codec/builder/BuilderField.java:147-149`) n'écrit pas un champ dont le getter rend `null` : c'est ce qui permet de lire les anciennes clés plates sans les réécrire. `BuilderField.decodeJson` (`:160-176`) passe `null` au setter sur une valeur JSON `null`. Vérifié hors jeu avec la vraie classe `server/core/util/Config.java` (`load`, `save`) : un ancien fichier plat est relu puis réécrit en sections avec ses valeurs.
- **Explosions** : `ExplosionUtils.processTargetBlocks` (`server/core/entity/ExplosionUtils.java:218`) appelle `BlockHarvestUtils.performBlockDamage` sans entité ; `damageSingleBlock` invoque alors `DamageBlockEvent` au niveau du monde (`server/core/modules/interaction/BlockHarvestUtils.java:1143-1152`, `entityStore.invoke(event)`), annulable (`CancellableEcsEvent`). En 0.6.8, c'est le seul appel sans entité : `BreakBlockInteraction` passe toujours une entité. Un `WorldEventSystem<EntityStore, DamageBlockEvent>` le reçoit (`component/ComponentRegistry.java:692` enregistre le type d'événement de monde avec le système). Annulé : le bloc reste et arrête le souffle (`avoidBlocks`). **Pas de crochet fiable pour les entités** : les dégâts d'explosion viennent d'un `Damage.EnvironmentSource("explosion")` (`ExplodeInteraction.java:60`) ou d'un `Damage.ProjectileSource` (`ProjectileComponent.java:397`, `ExplodeInteraction.java:107`), indiscernable d'un tir. `EnvironmentBreakBlockEvent` n'est pas annulable. **[in-game]**
- **Config malformée** : `PluginBase.preLoad` (`server/core/plugin/PluginBase.java:150-162`) appelle `Config.load` pour chaque `withConfig` ; `Config.load` (`server/core/util/Config.java`) décode par `RawJsonReader.readSync` dans un `supplyAsync`, sans rattraper : JSON invalide (`IOException: Unexpected character`), section qui n'est pas un objet (`CodecException: Failed to decode 'Gameplay'`) ou fichier vide (`IOException: Unexpected EOF!`) font échouer le démarrage. `RawJsonReader.readSyncWithBak` ne rattrape que `IOException` (pas `CodecException`) et renvoie `null` : inutilisable tel quel. HyColony décode donc le fichier dans le constructeur du plugin (avant `withConfig`, `dataDirectory` est déjà posé par `PluginBase`, `:115`) avec un `ExtraInfo` neuf, et le renomme `config.json.broken-<date>` en cas d'échec : `Config.load` prend alors `codec.getDefaultValue()` et `setup` réécrit un fichier valide. Vérifié hors jeu avec la vraie classe `Config` sur les trois cas.
- **Point d'apparition** : `world.getWorldConfig().getSpawnProvider()` (`server/core/universe/world/WorldConfig.java:415`, `@Nullable`), puis `ISpawnProvider.getSpawnPoint(World, UUID)` (`universe/world/spawn/ISpawnProvider.java`) qui rend un `Transform` (`getPosition()`). Le point peut dépendre du joueur (`IndividualSpawnProvider:79`, hachage de l'UUID).
- **Opérateur** : `PermissionsModule.get().getGroupsForUser(uuid).contains(HytalePermissionsProvider.GROUP_ADMIN)` (groupe donné par `/op`, qui a `*`). Hytale n'a pas de niveaux d'opérateur. Le groupe d'une sous-commande se pose par `AbstractCommand.setPermissionGroups(String...)` (`server/core/command/system/AbstractCommand.java:195`, `protected`) ; une liste vide ne laisse que le nœud généré, que seul `*` accorde.

## 14. Player facing (yaw, for `PlayerDirectory.facing`)

- **Body vs. head**: the client's movement packet carries `bodyOrientation` and `lookOrientation` as two separate
  `Direction` fields (`server/core/io/handlers/game/GamePacketHandler.java:421-427`), queued as
  `PlayerInput.SetBody` (writes `TransformComponent.getRotation()`) and `PlayerInput.SetHead` (writes
  `HeadRotation.getRotation()`) respectively (`server/core/modules/entity/player/PlayerInput.java:176-227`). Only
  `HeadRotation` tracks where the player looks (the camera); `TransformComponent`'s rotation is the body/movement
  orientation, which can lag or differ (strafing, free-look). `PlayerSystems.UpdatePlayerRef` queries both components
  as required (`assert ... != null`) on every player entity (`:751-780`), so `HeadRotation` is always present.
- **Units**: `Rotation3f.y` (`x`=pitch, `y`=yaw, `z`=roll, `math/vector/Rotation3f.java:26-90`) is read and written
  directly by `TrigMathUtil.sin`/`cos` with no `Math.toRadians` conversion anywhere in `HeadRotation`,
  `PhysicsMath` or `Rotation3f.lookAt`, so yaw is in **radians**, one full turn = `2*PI`.
- **North**: `HeadRotation.getAxisDirection(pitch, yaw, …)` (`server/core/modules/entity/component/HeadRotation.java:107-121`)
  computes `x = cos(pitch) * -sin(yaw)`, `z = cos(pitch) * -cos(yaw)`. At `yaw = 0` this is `(0, -1)`: **north is
  `-Z` at yaw 0**, matching `PhysicsMath.headingFromDirection`'s same `-sin`/`-cos` convention
  (`server/core/modules/physics/util/PhysicsMath.java:206-208, 369-391`). Increasing yaw turns **counterclockwise**
  (north → west → south → east), i.e. the opposite sense of the port's clockwise quarter (0=north, 1=east, 2=south,
  3=west, same as `Building`/`WorkOrder`/`BlueprintSource.load`): `facing = floorMod(-round(yaw / (PI/2)), 4)`.
  **[in-game]** the sign of `lookOrientation.yaw` as actually sent by the 0.6.8 client was not captured on the wire;
  only the server-side plumbing above was read. The build tool arrows use this facing: in-game check
  `docs/TESTING.md` point 62.

## 15. Baguette de construction : `OpenCustomUI` depuis un objet

- **Enregistrer une page** : `OpenCustomUIInteraction.registerCustomPageSupplier(PluginBase, Class<?>, String id, CustomPageSupplier)` (`server/core/modules/interaction/interaction/config/server/OpenCustomUIInteraction.java:79-89`) enregistre `id` dans `PAGE_CODEC` (un `CodecMapCodec`, clé `Id`) avec un codec qui rend toujours le même fournisseur. Vanilla l'appelle dans `setup` (`builtin/adventure/memories/MemoriesPlugin.java:117`, avec la classe de la page). L'objet le nomme par `"Interactions": { "Primary": { "Interactions": [ { "Type": "OpenCustomUI", "Page": { "Id": "..." } } ] } }`, comme `Server/Item/Items/Tool/Repair_Kit/Tool_Repair_Kit_Crude.json` (assets 0.6.8). **[in-game]** que notre `setup` passe bien avant le décodage des objets de notre pack.
- **`CustomPageSupplier.tryCreate(Ref<EntityStore>, ComponentAccessor<EntityStore>, PlayerRef, InteractionContext)`** (`:151-156`, `@Nullable`) : `firstRun` (`:55-73`) ne l'appelle que si le joueur n'a **aucune** page ouverte (`pageManager.getCustomPage() == null`), puis ouvre la page rendue si elle n'est pas nulle. HyColony rend toujours `null` : `WandActions.open` ouvre elle-même la fenêtre par `UiPort.showWand` (ou la refuse), sur le thread du monde (tick des interactions).
- **Bloc visé** : `InteractionContext.getTargetBlock()` (`server/core/entity/InteractionContext.java:464`, `@Nullable`) lit `Interaction.TARGET_BLOCK`, posé au début de la chaîne à partir du `blockPosition` envoyé par le client, filler ramené au bloc de base (`server/core/entity/InteractionManager.java:1233-1243`). Nul pour un clic dans le vide.
- **Face visée : non disponible**. `InteractionSyncData.blockFace` (`protocol/InteractionSyncData.java:35`, défaut `None`) n'est lu que dans l'état client (`context.getClientState()`) des interactions qui attendent les données du client (`WaitForDataFrom.Client`, par exemple `CarryPlaceBlockInteraction.java:46-49, 96` ou `PlaceBlockInteraction.java:215`). `OpenCustomUIInteraction` (un `SimpleInstantInteraction`) ne l'attend pas : l'ancre de la baguette est donc le bloc visé + 1 en Y (écart noté dans la spec SP1+2 § 11).
- **`PageManager`** : `openCustomPage` (`server/core/entity/entities/player/pages/PageManager.java:129-134`) et `setPage(..., Page.None)` (`:103-108`) appellent `onDismiss` de la page ouverte. C'est pour ça que `WandActions.confirm` ne ferme pas la fenêtre quand un hôtel de ville lance la fondation : la fenêtre de fondation a remplacé celle de la baguette, et la fermer annulerait la fondation.
- **Rendre un ingrédient (reste de fabrication)** : pas de champ « reste » dans `CraftingRecipe`, mais `Output` est un tableau (`server/core/asset/type/item/config/CraftingRecipe.java:45-50`). Pour une recette portée par un objet, `Item` garde ce tableau tel quel s'il a plus d'une sortie et fait de l'objet la sortie principale (`Item.java:1337-1352`) ; `CraftingManager.getOutputItemStacks` donne toutes les sorties (`builtin/crafting/component/CraftingManager.java:608-624`). Seul `DiagramCrafting` refuse plusieurs sorties (`CraftingRecipe.java:136`). Vanilla s'en sert pour rendre un contenant : `Server/Item/Items/Food/Food_Cheese.json` (sorties `Food_Cheese` + `Container_Bucket`). Les lunettes rendent ainsi la baguette. **[in-game]** : la baguette revient bien dans l'inventaire.

- **Page sans fond assombri** : le voile vient seulement de `$C.@PageOverlay` (`Group { Background: #000000(0.45); }`, `Common/UI/Custom/Common.ui:879`). Une page dont la racine est un `$C.@Container` ancré sur un côté n'a pas de voile : `Common/UI/Custom/Pages/EntitySpawnPage.ui` (`Anchor: (Left: 50, Top: 170, Width: 450, Bottom: 120)`), comme `ParticleSpawnPage.ui` et `ChangeModelPage.ui`. **[in-game]** : le client n'ajoute pas de flou ni de voile de lui-même.
- **Icônes de flèches** : `Common/UI/Custom/Common/InputIconKey{Up,Down,Left,Right}_White@2x.png` (48×48, flèches blanches), appelées sans `@2x` (`Hud/ToolsLegends/ToolsLegendsCommon.ui:45`). Pas d'icône de rotation ni de plus/moins dans les assets 0.6.8, et les polices du client (`NunitoSans`, `Lexend`, `NotoSans`) n'ont ni flèches (U+2190-2193) ni ↺/↻ : seuls `°` et `−` y sont.

- **Faire tomber des objets au sol** : `ItemComponent.generateItemDrops(accessor, List<ItemStack>, Vector3d, Rotation3f.IDENTITY)` (`server/core/modules/entity/item/ItemComponent.java:430`) puis `store.addEntities(holders, AddReason.SPAWN)`, exactement ce que fait `BlockHarvestUtils.spawnDrops` (`server/core/modules/interaction/BlockHarvestUtils.java:1368-1371`), à la position `bloc + (0.5, 0, 0.5)` (`:652`). HyColony : `HytaleBlocks.drop`, derrière `WorldBlocks.drop`.

## 16. Inventaire du citoyen : conteneur adossé au cœur

Analyse complète : `docs/research/citizen-inventory-window.md`. Ce que l'implémentation (`plugin/ui/citizen/CitizenItemContainer`, `CitizenInventoryWindow`, `CitizenInventoryWindows`) utilise en plus :

- **Toute écriture passe par `writeAction`** : `SimpleItemContainer.writeAction(Supplier)` et `writeAction(Function, X)` sont protégées et surchargeables (`server/core/inventory/container/SimpleItemContainer.java:128-152`). Les déplacements entre deux conteneurs verrouillent les deux (`ItemContainer.java:369, 411, 549-550, 769`) et les utilitaires `InternalContainerUtilItemStack` passent aussi par `itemContainer.writeAction` (l. 115-524). Les appels s'imbriquent (un déplacement appelle `internal_removeItemStack` sous son propre `writeAction`) : HyColony compte la profondeur et ne compare l'inventaire avant/après qu'au niveau le plus externe. Une transaction qui échoue remet la case (`internal_setSlot(slot, getSlotBefore())`, l. 576-606) : la comparaison avant/après ne voit alors aucun changement.
- **`sendUpdate` ne notifie que les transactions réussies** (`ItemContainer.java:1409-1415`).
- **Renvoi au client sans transaction** : `Window.consumeIsDirty()` est protégée (`windows/Window.java:208`) et `WindowManager.updateWindows()` (`WindowManager.java:415-421`) la lit pour chaque fenêtre, à chaque tick, depuis `PlayerSendInventorySystem` (l. 120). `CitizenInventoryWindow` la surcharge pour ajouter « le compteur `Inventory.changes()` a bougé ». **Fil d'exécution (prouvé)** : `PlayerSendInventorySystem.isParallel` renvoie `maybeUseParallel(...)` (l. 64), qui renvoie toujours `false` en 0.6.8 (`component/system/tick/EntityTickingSystem.java:13-15`) : le système tourne sur le thread du monde. Les déplacements d'objets du client passent aussi par `world.execute` (`server/core/io/handlers/game/InventoryPacketHandler.java:331, 396, 428`). Toute lecture ou écriture du cœur par le conteneur a donc lieu sur le thread du monde. À revérifier à chaque mise à jour de Hytale (`maybeUseParallel` pourrait s'activer). La surcharge ne crée aucun objet : la fenêtre garde une référence directe au `CitizenData`.
- **`ItemStack(String, int)`** (`inventory/ItemStack.java:134`) : durabilité = maximum de l'objet ; `getItem()` renvoie `Item.UNKNOWN` pour un identifiant inconnu (l. 331-334), sans exception. `isUnbreakable()` = `maxDurability <= 0` (l. 192).
- **`Window.equals`** compare classe, id, type et `PlayerRef` (`Window.java:251-266`) ; `WindowManager.getWindow(id)` rend `null` pour une fenêtre fermée et lève une exception pour l'id -1 (l. 272-279), posé seulement quand l'ouverture échoue (l. 104, 154).
- **[in-game]** rendu de `Page.Bench` avec une seule `ContainerWindow` de 27 cases (titre, grille) ; retour visuel quand `cantAddToSlot` refuse un dépôt.

## 17. Texture et `BlockType` créés à l'exécution (expérience `/hycolony dotest`)

Expérience temporaire pour Domum Ornamentum (`docs/research/domum-ornamentum.md` B.6). Le code (`plugin/debug/`) a été retiré ; il reste dans l'historique git (commits `cb2312d` à `d17838f`).

- **Lire une texture chargée** : `CommonAssetRegistry.getByName(nom)` (nom relatif à `Common/`, par exemple `Blocks/Structures/Roofs/Cloth_Roof_Textures/Tent_Blue.png`) rend le `CommonAsset` ou `null` (`server/core/asset/common/CommonAssetRegistry.java:168-172`) ; `getBlob()` rend un `CompletableFuture<byte[]>` (`CommonAsset.java`). Le nom est le chemin sous `Common/` (`CommonAssetModule.java:450, 472`).
- **Ajouter une texture** : `CommonAssetModule.get().addCommonAsset(pack, asset)` enregistre l'asset, envoie une notification à tout l'univers, puis, s'il y a des joueurs, `sendAsset(asset, false)` : `AssetInitialize` + `AssetPart` + `AssetFinalize` diffusés, **sans** `RequestCommonAssetsRebuild` (`CommonAssetModule.java:187-221, 588-611`). `FileCommonAsset(Path, name, bytes)` calcule le hash depuis les octets et relit le fichier si la référence faible est perdue (`asset/FileCommonAsset.java`) : la PNG doit donc rester sur disque. On l'écrit dans un dossier temporaire (`Files.createTempDirectory`), pas dans `getDataDirectory()`, qui pointe vers `plugin/src/main/resources` en développement.
- **Nom du pack du plugin** : `PluginIdentifier.toString()` = `Group:Name` (`common/plugin/PluginIdentifier.java:79-81`), nom sous lequel `PluginManager` enregistre le pack (`server/core/plugin/PluginManager.java:839-842`).
- **Copier un `BlockType`** : constructeur de copie public `BlockType(BlockType)` (`server/core/asset/type/blocktype/config/BlockType.java:1019-1105`) ; ses champs (`id`, `customModelTexture`, `state`, `connectedBlockRuleSet`) sont `protected` sans mutateur (l. 841-927) : une sous-classe les change, comme `BlockType.EMPTY`/`DEBUG_MODEL` (l. 2295-2341). `clone(newKey)` (l. 2226-2236) ne change que l'id.
- **Piège : la copie garde le `data` de la source.** Le constructeur de copie recopie `this.data = other.data` (`BlockType.java:1020`), c'est-à-dire l'`AssetExtraInfo.Data` de la source. Conséquences : `AssetStore.loadContainedAssets` (`assetstore/AssetStore.java:1025-1108`) relit à chaque chargement les assets contenus de la source (les `State.Definitions` de `Cloth_Roof_Blue`) sous notre pack, avec des `UpdateBlockTypes` en plus et des enfants dans `childAssetsMap` ; `getItem()` lit `data.getContainerKey(Item.class)` (l. 1368-1375), donc le paquet annonce l'objet de la source (`toPacket`, l. 1118-1121). Correctif : la sous-classe met `data = null`, comme les blocs vanilla `EMPTY`/`DEBUG_MODEL` (l. 2295-2341). Chaque lecteur tolère `null` : `toPacket` (l. 1338), `getItem` (l. 1369), `getDefaultStateKey` (l. 1401), `loadContainedAssets` (l. 1032, 1065), les tags de `DefaultAssetMap.putAssetTags` (l. 317) et `AssetMapWithIndexes.putAssetTag` (l. 34), `WorldChunk.setBlockInteractionState` (l. 331), `BlockOperations` (l. 394), `ConnectedBlockTagShape` (l. 52), `BlockConditionInteraction` (l. 148), `FireFluidTicker` (l. 165), `NeighbourBlockTagsLocationCondition` (l. 108). Exceptions connues, qui lèvent une `NullPointerException` sur un tel bloc : la commande de débogage `/tagpattern` (`TagPatternCommand.java:49`), la tâche d'objectif « UseBlock » (`builtin/adventure/objectives/task/UseBlockObjectiveTask.java:43`, `getItem().getId()` sans test) et `DoorInteraction` (l. 267, 380), qui ne concerne que les portes. Le bloc généré n'a ni tags ni objet.
- **Piège : `loadAssets` sur le thread du monde bloque le serveur pour toujours** (vu en jeu le 2026-09-27, vidage de threads : `WorldThread - default` parqué dans `AssetStore.loadAssets0` sur `writeLock`). `World.tick` garde le verrou de lecture de `AssetRegistry.ASSET_LOCK` pendant tout le tick, tâches et commandes comprises (`server/core/universe/world/World.java:401-419`). `loadAssets` prend le verrou d'écriture dans `loadAssets0` (`assetstore/AssetStore.java:833-921`), et un `ReentrantReadWriteLock` ne passe pas de la lecture à l'écriture. Il faut donc charger hors du thread du monde (`CompletableFuture.runAsync`), puis revenir par `world.execute` pour poser le bloc, comme le vanilla `BlockSpawnerSettingsPage.writeAssetAsync` (l. 433-453, sur `HytaleServer.SCHEDULED_EXECUTOR`). L'écrivain n'est pas affamé : un monde qui attend un futur relâche le verrou (`IWorldChunks.waitForFutureWithoutLock`, l. 38-57). `addCommonAsset` ne prend pas ce verrou.
- **Charger à chaud** : `BlockType.getAssetStore().loadAssets(packKey, List.of(bt), AssetUpdateQuery.DEFAULT)` (`assetstore/AssetStore.java:463-509`) attribue un nouvel index (`BlockTypeAssetMap.putAll0`, l. 137-195), puis `HytaleAssetStore.handleRemoveOrUpdate` diffuse `BlockTypePacketGenerator.generateUpdatePacket` (`server/core/asset/HytaleAssetStore.java:88-111`) : un `UpdateBlockTypes` `AddOrUpdate` avec `maxId` et les drapeaux de reconstruction de `RebuildCache.DEFAULT` (tous `true`, `AssetUpdateQuery.java`) (`BlockTypePacketGenerator.java:43-66`).
- **Poser par id** : `World.setBlock(x, y, z, key)` (`IChunkAccessorSync.java:62-68`) lève `IllegalArgumentException` sur une clé inconnue (`WorldChunk.java:267-273`). Un id inconnu au chargement d'un tronçon devient un `BlockType` « Unknown » (`BlockType.getBlockIdOrUnknown`, l. 2242-2257).
- **Vérifié en jeu (2026-09-27)** : pas fiable. Après chaque démarrage, avec la séquence de `d67fe4c`, seuls les un ou deux premiers blocs générés s'affichent sans reconnexion (les autres ordres essayés échouent dès le premier) ; après reconnexion, tous s'affichent (détail dans `docs/research/domum-ornamentum.md` B.6). Chaque reconstruction fait aussi scintiller l'image.

## 18. Météo, vitesse des PNJ et générateurs de coffres (SP3a, tâche 11)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`. Recherche complète : `docs/research/sp3a-warehouse-courier-hytale.md` § 1 et § 3.

- **Pluie à une position** (`HytaleWorldQuery.isRainingAt`) :
  - `WeatherResource` (`builtin/weather/resources/WeatherResource.java`) se lit par `world.getEntityStore().getStore().getResource(WeatherResource.getResourceType())`, comme `WeatherSetCommand.setForcedWeather` (l. 34-38). `getForcedWeatherIndex()` vaut 0 sans météo forcée ; `getWeatherIndexForEnvironment(env)` renvoie `Integer.MIN_VALUE` tant que `WeatherSystem` n'a rien tiré (`defaultReturnValue`, l. 17-19) ;
  - l'environnement du bloc : `world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z))` (`null` si non chargé), puis `BlockChunk.getComponentType()` et `BlockChunk.getEnvironment(x, y, z)` en coordonnées monde, 0 hors de [0, 320[ (`server/core/universe/world/chunk/BlockChunk.java:310-320`). Même ordre que `WorldSupport.getCurrentWeatherIndex` (`server/npc/role/support/WorldSupport.java:273-291`) : forcée d'abord, sinon par environnement ;
  - `Weather.getAssetMap()` est un `IndexedLookupTableAssetMap` dont `getAsset(int)` rend `null` hors bornes (`assetstore/map/IndexedLookupTableAssetMap.java:70-72`), donc `MIN_VALUE` ne lève rien. `Weather.getParticle()` (`server/core/asset/type/weather/config/Weather.java:594`) rend un `protocol.WeatherParticle` au champ public `systemId` ;
  - les systèmes de particules de pluie et de neige sont dans l'id-map (`precipitationParticles`), vérifiés par `IdMap.validate` (`ParticleSystem`) : `Rain`, `Rain_Light`, `Rain_Heavy`, `Snow_Light`, `Snow_Heavy`, `Snow_Storm` (fichiers `Server/Particles/Weather/{Rain,Snow}/*.particlesystem` du ZIP) ;
  - `/weather set <id>` et `/weather reset` passent par `WeatherResource.setForcedWeather` (`builtin/weather/commands/WeatherSetCommand.java:30-38`, `WeatherResetCommand.java:19`) : de quoi tester en jeu.
- **Vitesse de marche d'un PNJ** (`plugin/npc/CitizenSpeed`) :
  - le maximum du contrôleur `Walk` (`MaxWalkSpeed` du rôle) est un champ `final` (`server/npc/movement/controllers/MotionControllerBase.java:85, 168`) : aucun mutateur. La vitesse maximale effective est `maxHorizontalSpeed * horizontalSpeedMultiplier (fluide) * effectHorizontalSpeedMultiplier` (`MotionControllerWalk.java:593-595`) ;
  - `effectHorizontalSpeedMultiplier` vient de `NPCEntity.getCurrentHorizontalSpeedMultiplier` (`server/npc/entities/NPCEntity.java:551-585`) : le produit des `ApplicationEffects.HorizontalSpeedMultiplier` de tous les effets actifs (`EffectControllerComponent.getActiveEffectIndexes`). Le cache est vidé à chaque tick par `RoleSystems` (`server/npc/systems/RoleSystems.java:440`) ;
  - tout PNJ a un `EffectControllerComponent` (`RoleBuilderSystem.java:222`, `ensureComponent`). `addInfiniteEffect(ref, index, effect, accessor)` et `removeEffect(ref, index, RemovalBehavior.COMPLETE, accessor)` (`server/core/entity/effect/EffectControllerComponent.java:262-311, 420-470`) ne changent que ce composant (et le modèle si l'effet a un `ModelChange`, absent chez nous) : appelables pendant le tick de la colonie. `hasEffect(int)` (l. 684) ;
  - `ApplicationEffects` a un constructeur protégé (`server/core/asset/type/entityeffect/config/ApplicationEffects.java:171`) : impossible de créer un effet d'un facteur quelconque en code, d'où 20 assets `Server/Entity/Effects/HyColony/HyColony_Speed_105..200.json` (`Infinite`, `HorizontalSpeedMultiplier`), listés dans l'id-map (`speedEffects`) et vérifiés par `IdMap.validate` (`EntityEffect.getAssetMap()`, l. 359). Modèle vanilla : `Server/Entity/Effects/Deployables/Slowness_Totem_Slow.json` ;
  - l'effet est sauvegardé avec l'entité (`EffectControllerComponent.CODEC`) : `CitizenSpeed.apply` retire donc tout autre effet de vitesse HyColony, pas seulement le dernier posé.
  - **[in-game]** : la marche plus rapide, l'absence de teinte ou d'icône (nos effets n'ont ni `StatusEffectIcon` ni teinte).
- **Générateurs de coffres dans les prefabs** (`HytaleBlueprintSource`) :
  - le `Holder<ChunkStore>` passé au consommateur de `IPrefabBuffer.forEach` est celui du bloc dans le prefab (`server/core/prefab/selection/buffer/impl/PrefabBuffer.java:701-726`) ; un générateur y porte le composant `BlockSpawner` (`builtin/blockspawner/state/BlockSpawner.java`, `getBlockSpawnerId()`), lu de la même façon par `BlockSpawnerPlugin.validatePrefabBlock` (l. 96-135) ;
  - `BlockSpawnerTable.getAssetMap().getAsset(id)` puis `getEntries().internalKeys()` (`builtin/blockspawner/BlockSpawnerTable.java`, `common/map/IWeightedMap.java`) donnent les `BlockSpawnerEntry.getBlockName()` possibles ; « générateur de coffre » = l'un d'eux a un `ItemContainerBlock` ;
  - à la résolution en jeu, le bloc tiré prend la rotation du générateur (`RotationMode.INHERIT` par défaut, `BlockSpawnerPlugin.java:198-212`) : le coffre de remplacement garde donc la rotation de la cellule du prefab.

## 19. Coups sur un bloc (`HytaleWorldEffects.blockHit`)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`. Recherche : `docs/research/builder-tools-durability-breaking.md` § B.

- **Son** : `BlockSoundSet.getAssetMap().getAsset(type.getBlockSoundSetIndex()).getSoundEventIndices().getOrDefault(BlockSoundEvent.Hit, 0)`, puis `SoundUtil.playSoundEvent3d(int, SoundCategory, double x, double y, double z, ComponentAccessor<EntityStore>)` (`server/core/universe/world/SoundUtil.java:234`), qui ignore l'index 0 et envoie aux joueurs à portée de l'événement. Une surcharge prend aussi `volumeModifier, pitchModifier` (l. 240). Le coup du joueur utilise (1, 1) (`BlockHarvestUtils.playBlockSound`, l. 1356).
- **Éclats** : `world.getNotificationHandler().sendBlockParticle(x, y, z, blockId, BlockParticleEvent.Hit)` (`WorldNotificationHandler.java:84`), seulement si le chunk est chargé.
- **Fissures** : `BlockHealthChunk` (`server/core/modules/blockhealth/`), sur la colonne `ChunkSection.getChunkColumnReference()` (`chunk/section/ChunkSection.java:60`), type `BlockHealthModule.get().getBlockHealthChunkComponentType()`. `damageBlock(Instant, World, org.joml.Vector3i, float)` garde la clé `Vector3i` dans sa map (une instance neuve à chaque appel) et envoie `UpdateBlockDamage` à tous les joueurs. Temps : `TimeResource.getNow()` de l'`EntityStore`.
- **Casse** : `naturallyRemoveBlock` sans `NO_SEND_AUDIO` (1024) ni `NO_SEND_PARTICLES` (4) joue déjà le son `Break` (`BlockHarvestUtils.java:636-646`), envoie les particules `Break` (`BlockOperations.java:71, 369-379`) et efface la santé (`removeBlock`, l. 1317).
- **[in-game]** : affichage des éclats `Hit` envoyés par le serveur, correspondance santé → étape de fissure.

## 20. Usure sur la pile (`plugin/item/HytaleStacks`)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/server/core/inventory/`. Recherche : `docs/research/builder-tools-durability-breaking.md` § A.

- `ItemStack(String, int)` (`ItemStack.java:134` → l. 96) met `durability = maxDurability = getItem().getMaxDurability()`.
- `withDurability(double)` (l. 369) renvoie une copie bornée à `[0, maxDurability]` ; `getDurability()` (l. 232), `getMaxDurability()` (l. 220).
- `isUnbreakable()` = `maxDurability <= 0` (l. 192) ; `isBroken()` = `!isUnbreakable() && durability == 0` (l. 208).
- `ItemContainer.removeItemStackFromSlot(short, int)` (`container/ItemContainer.java:277`) : la pile lue juste avant dans la case donne la durabilité de la part retirée.
- **[in-game]** : un outil posé avec `withDurability` s'affiche et s'use côté joueur comme un outil vanilla usé.

## 21. Packs d'assets embarqués / sous-plugins

Pour la tâche 7 du plan `docs/superpowers/plans/2026-09-27-hycolony-architecture-subplugins.md`. Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`.

### 21.1 `AssetModule.registerPack`

- Signature : `public boolean registerPack(@Nonnull String name, @Nonnull Path path, @Nonnull PluginManifest manifest, @Nonnull AssetPack.PackSource source)` (`server/core/asset/AssetModule.java:418`). `unregisterPack(String name)` existe (l. 471). Accès : `AssetModule.get()` (l. 82).
- `name` : identifiant du pack, par convention `new PluginIdentifier(manifest).toString()` (`Groupe:Nom`, l. 109, 392-393). Un même nom déjà présent : si la source existante l'emporte (`PackSource.overrides` = ordinal plus petit, `assetstore/AssetPack.java:129-131`, ordre `CLI < CLASSPATH < MODS < RUNTIME`), le nouveau est ignoré et la méthode renvoie `true` ; à source égale, erreur SEVERE et `false` (l. 419-435). Les appelants vanilla arrêtent alors le serveur (l. 110-113, 405-408). Choisir un nom unique par sous-pack (`HyColony:hycolony-outlander`…).
- **Le nom doit être de la forme `groupe:nom`** : au `LoadAssetEvent`, chaque pack est indexé par `PluginIdentifier.fromString(assetPack.getName())` (`AssetModule.java:511`), qui lève `IllegalArgumentException` sans exactement un `:` (`common/plugin/PluginIdentifier.java:84-90`), ce qui ferait échouer le chargement de tous les assets. Un nom comme `HyColony_Styles_Outlander` est donc interdit ; HyColony utilise `new PluginIdentifier(manifest).toString()`, soit `HyColony:hycolony_<Nom>` (groupe `HyColony` et nom `hycolony` de notre manifeste de plugin).
- **Pas de dépendance dans le manifeste d'un sous-pack** : `Mod.calculateLoadOrder` lève `ModLoadOrderException` pour une dépendance absente (`common/plugin/Mod.java:60-120`), transformée en `IllegalStateException` qui arrête le chargement des assets (`AssetModule.java:516-521`). Tous les packs dépendent déjà implicitement des packs de classpath du groupe `Hytale` (`Mod.java:48-52`, 76-78).
- `manifest` : un `PluginManifest` déjà construit, **pas lu par `registerPack`**. Le fichier `manifest.json` n'est lu que par le chemin « dossier `mods/` » (`loadPackManifest`, l. 325-358). Le manifeste sert ensuite à l'ordre de chargement (`Mod.calculateLoadOrder`, l. 508-519, dépendances du manifeste) et à la vérification `ServerVersion` (l. 130-170, faite seulement dans `setup()` d'`AssetModule`, donc pas pour un pack ajouté plus tard). On peut le décoder avec `PluginManifest.CODEC` comme l. 331-335.
- `path` (l. 437-454) :
  - fichier `.zip` ou `.jar` : ouvert en `FileSystems.newFileSystem(path, null)`, racine du pack = **racine du zip**, pack marqué immuable ;
  - sinon : utilisé tel quel comme racine, immuable seulement si `CommonAssetsIndex.hashes` est présent à la racine (l. 443).
  - Il n'y a pas de paramètre « sous-dossier dans un jar » : un jar donne toujours sa racine. Les sous-packs rangés sous `subplugin-assets/<Nom>/` dans notre jar ne sont donc **pas** vus par le pack principal (qui ne lit que `Common/` et `Server/` à la racine) ni atteignables en passant le chemin du jar.
- Structure d'un pack : `Common/…` (assets envoyés au client, `server/core/asset/common/CommonAssetModule.java:153`, 446-489), `Server/<chemin du store>` (assets serveur, `AssetModule.java:597-600`), `Server/Languages/<locale>/*.lang` (`server/core/modules/i18n/I18nModule.java:223`), `Server/Prefabs/…` (`server/core/prefab/PrefabStore.java:262`).
- Moment de l'appel. Ordre de démarrage (`server/core/HytaleServer.java:342-395`) : `pluginManager.setup()` → `LoadAssetEvent` → `pluginManager.start()`.
  - Appelé dans `setup()` du plugin : `hasLoaded` est faux, le pack est seulement ajouté à la liste (l. 455-461) ; il est chargé avec tous les autres au `LoadAssetEvent` (l. 498-526), après tri par dépendances. C'est le moment recommandé.
  - Appelé plus tard (`start()`, commande) : `AssetPackRegisterEvent` est émis (l. 463). Il charge les stores serveur (`AssetModule.java:202`), les `Common/` (`CommonAssetModule.java:104`, puis `RequestCommonAssetsRebuild` diffusé l. 179), les langues (`I18nModule.java:96`) et les rôles PNJ (`server/npc/NPCPlugin.java:515`). L'éditeur d'assets fait exactement cela à chaud (`builtin/asseteditor/AssetEditorPlugin.java:853`, `server/core/ui/browser/AssetPackSaveBrowser.java:550`). À noter : `AssetRegistry.ASSET_LOCK` est pris en écriture pendant la diffusion (l. 456-466), donc jamais depuis le thread d'un monde qui tient déjà un verrou d'assets (voir la mémoire « world thread asset lock »).
- Envoi au client : oui. `CommonAssetModule` parcourt `Common/` de **chaque** pack enregistré (l. 99-104) et l'envoie (`sendAsset`, l. 216-218). Les assets serveur (`Server/`) sont chargés côté serveur pour chaque pack (`AssetRegistryLoader.loadAssets(event, pack)`, l. 524-525 et 202).
- Comment notre plugin livre aujourd'hui ses assets : `plugin/src/main/resources/manifest.json` a `"IncludesAssetPack": true` ; `PluginManager` met alors le jar du plugin dans `classpathAssetPacks` (`server/core/plugin/PluginManager.java:691-692`, 737-738), qu'`AssetModule.setup()` enregistre avec `PackSource.CLASSPATH` (`AssetModule.java:108-115`) ; un plugin chargé plus tard passe par `registerAssetPackIfNeeded` (`PluginManager.java:823-847`, source `RUNTIME`). Racine du pack = racine du jar, d'où `Common/` et `Server/` directement sous `plugin/src/main/resources/`.

**Pack dans le jar ou extrait ?** `registerPack` exige un `Path`. Trois options, par ordre de sûreté :

1. **Recommandé : un zip par sous-pack, extrait sur disque.** Le build produit `subplugin-assets/<Nom>.zip` (avec `Common/`, `Server/`) dans le jar ; au `setup()`, le plugin le copie dans son dossier de données (si absent ou différent) puis appelle `registerPack(id, cheminDuZip, manifest, PackSource.RUNTIME)`. C'est exactement le cas d'un zip de `mods/` : racine = racine du zip, pack immuable, donc pas de surveillance de fichiers ni d'écriture dans le pack (le cache `.lpf` des prefabs d'un pack immuable va sous `.cache/prefabs/<pack>/`, `server/core/prefab/selection/buffer/PrefabBufferUtil.java:135-149`).
2. Dossier extrait sur disque : marche aussi, mais le pack est **mutable** (pas de `CommonAssetsIndex.hashes`) : surveillé par l'`AssetMonitor`, le cache `.lpf` est écrit à côté des prefabs (`PrefabBufferUtil.java:151`) et `CommonAssetModule` **supprime** tout fichier `*.hash` qu'il trouve (`CommonAssetModule.java:467-469`).
3. Déconseillé : un `Path` d'un `FileSystem` zip ouvert sur notre propre jar, pointant `subplugin-assets/<Nom>`. Le code n'appelle jamais `toFile()` sur ces chemins (`FileCommonAsset.getBlob0` utilise `Files.readAllBytes`, `server/core/asset/common/asset/FileCommonAsset.java:30`), mais le pack serait mutable : l'`AssetMonitor` enregistrerait un chemin zip sur le `WatchService` du système par défaut (`server/core/asset/monitor/PathWatcherThread.java:42`, 135), et le cache `.lpf` serait écrit **dans le jar** (zipfs ouvert en écriture par défaut). **[in-game]** non essayé.

### 21.2 Fichiers de langue

- Chaque pack est lu dans l'ordre de la liste (`I18nModule.java:91-96`) ; toutes les langues d'un même code partagent **une seule table** (`languages.computeIfAbsent(languageKey, …)`, l. 314). Les fichiers ne se remplacent pas : les **clés** sont fusionnées.
- Clé = préfixe + `.` + clé du fichier (l. 339). Préfixe = nom du fichier sans `.lang`, précédé des sous-dossiers sous `<locale>/` joints par `.` (`getPrefix`, l. 355-365). Donc `hycolony.lang` → `hycolony.*` ; `hycolony_outlander.lang` → `hycolony_outlander.*` ; `en-US/hycolony/outlander.lang` → `hycolony.outlander.*`.
- Deux packs peuvent chacun fournir `Server/Languages/<locale>/hycolony.lang` : les clés sont fusionnées. En cas de clé en double, **la première chargée gagne** (pas d'écrasement) avec un WARNING « has multiple definitions » si la valeur diffère (l. 341-348). L'ordre est celui des packs après `Mod.calculateLoadOrder` (`AssetModule.java:517-518`), c'est-à-dire par dépendances du manifeste. Un sous-pack ne doit donc pas compter redéfinir une clé du pack principal.
- Réserve : `getPrefix` remplace `File.separatorChar` (`\` sous Windows) par `.` ; dans un zip, le séparateur est `/`. Un sous-dossier sur **plusieurs** niveaux dans un zip donnerait un préfixe avec `/` sous Windows. Un seul niveau (ou pas de sous-dossier) n'est pas touché.
- `AssetPackUnregisterEvent` ne retire aucune traduction (écouteur vide, l. 97).

### 21.3 Enregistrement conditionnel

- Rien ne l'empêche : `registerPack` est public, sans contrôle d'appelant ni de phase. Notre config est déjà lue dans le constructeur (`withConfig`, `plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java:41`), donc `config.get()` est disponible dans `setup()` (l. 51), avant le `LoadAssetEvent`.
- Vanilla fait déjà un enregistrement conditionnel : `loadAndRegisterPack` n'enregistre un pack que si `ModConfig.enabled` / `DisabledByDefault` le permettent (`AssetModule.java:395-414`) ; `WorldGenPlugin` enregistre des packs de version à son `setup` (`builtin/worldgen/WorldGenPlugin.java:125`).
- `unregisterPack(name)` existe (l. 471-496) : ferme le `FileSystem` du pack, puis `AssetPackUnregisterEvent` retire ses assets des stores (l. 203-207) et ses `Common/` (`CommonAssetModule.java:105-133`). Les traductions restent (21.2). À réserver à un usage à chaud ; au démarrage, il suffit de ne pas enregistrer.

### 21.4 Asset d'un pack désactivé encore référencé

- Bloc inconnu dans un **chunk sauvegardé** : `BlockSection` passe par `BlockType.getBlockIdOrUnknown` (`server/core/universe/world/chunk/section/BlockSection.java:884`), qui journalise un WARNING et enregistre à la volée un clone du bloc `Unknown` sous la clé manquante (`server/core/asset/type/blocktype/config/BlockType.java:2242-2257`). Pas de plantage ; le bloc s'affiche « Unknown » **[in-game]**.
- Bloc inconnu dans un **prefab** : même repli (`server/core/prefab/selection/buffer/BsonPrefabBufferDeserializer.java:216`).
- Objet inconnu dans une pile : `ItemStack.getItem()` renvoie `Item.UNKNOWN` (`server/core/inventory/ItemStack.java:331-334`).
- **Côté HyColony, en revanche, c'est bloquant** : `IdMap.validate()` vérifie chaque id de `id-map.json` (`plugin/.../IdMap.java:100-115`) et, au moindre manque, `HyColonyPlugin.validateIds` **désactive tout HyColony** (`HyColonyPlugin.java:140-153`). Les fragments `id-map.json` d'un sous-pack désactivé ne doivent donc jamais être fusionnés. Un plan de `styles.json` dont le prefab manque est déjà toléré : `findAssetPrefabPath` renvoie `null` (`server/core/prefab/PrefabStore.java:321-331`) et `HytaleBlueprintSource` renvoie vide avec un avertissement unique (`plugin/.../prefab/HytaleBlueprintSource.java:131-154`).

### 21.5 Lecture actuelle de `styles.json`, `id-map.json` et des prefabs

- `styles.json` : `PrefabStyles.class.getResourceAsStream("/hycolony/styles.json")` (`plugin/src/main/java/dev/hycolony/plugin/prefab/PrefabStyles.java:49`), donc **classpath du jar**, un seul fichier.
- `id-map.json` : `IdMap.class.getResourceAsStream("/hycolony/id-map.json")` (`plugin/.../IdMap.java:40`), classpath aussi.
- Prefabs : par clé via `PrefabStore.get().findAssetPrefabPath(prefab)` (`HytaleBlueprintSource.java:98`, 149), qui cherche `Server/Prefabs/<clé>` dans **tous les packs enregistrés**, dans l'ordre de la liste (`PrefabStore.java:321-331`). Un prefab livré par un sous-pack est donc trouvé sans changement, dès que le pack est enregistré.
- Conséquence : les fragments `styles.json` / `id-map.json` d'un sous-pack ne sont **pas** trouvés par `getResourceAsStream` (un seul chemin, et `hycolony/` est hors de `Common/` et `Server/`). Deux voies : les lire depuis le classpath sous un chemin propre à chaque sous-pack (`/subplugin-assets/<Nom>/hycolony/styles.json`) pour les seuls sous-packs activés, ou les placer dans le pack et les lire via `AssetModule.get().getAssetPack(id).getRoot().resolve(...)` (`AssetModule.java:545-553`, `AssetPack.getRoot()`).

### 21.6 Aetherhaven (idées seulement)

- Non vérifié : l'arbre public obtenu par l'API GitHub (`api.github.com/repos/gchougland/Aetherhaven/git/trees/HEAD?recursive=1`, peut-être tronqué) ne montre aucun chemin `subplugin-assets`, et `AetherhavenPlugin.java` n'appelle pas `registerPack` (il récupère un pack déjà enregistré dans `start()`). Rien à en tirer pour l'instant.

## 22. Objet tenu utilisé sur un bloc (`plugin/block/FlowerPotSystem`, `FlowerPotUse`)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/server/core/`. Détail et conséquences : `docs/research/carpets-flower-pots.md` § 6.

- `UseBlockEvent.Pre` n'est émis que si le bloc visé déclare une interaction du type utilisé (`modules/interaction/interaction/config/client/UseBlockInteraction.java:70-73`). **L'annuler fait échouer `UseBlock`** (l. 79-82) : le repli `Failed` de l'objet tenu s'exécute alors (`Block_Secondary` → `PlaceModeSelect`, `Empty.Use` → `UseEntity` → `BreakBlock` `Harvest`). Pour « consommer » l'usage sans effet de bord, ne pas annuler et donner au bloc une racine `{"Interactions": [{"Type": "Simple"}]}`.
- Interactions d'un objet tenu : les siennes, puis celles de `UnarmedInteractions` nommées par son `PlayerAnimationsId` (`Block` → `Secondary: Block_Secondary`), puis `Empty` (`Use` → `UseBlock`) (`asset/type/item/config/Item.java:1270-1285`) ; main vide : `Empty` seul (`entity/InteractionContext.java:626-660`).
- Objet tenu : `InteractionContext.getHeldItem()`, `getHeldItemContainer()`, `getHeldItemSlot()`, `setHeldItem(...)` (l. 407-428). En retirer 1 : `container.removeItemStackFromSlot(slot, held, 1)` puis `setHeldItem(transaction.getSlotAfter())`, comme `ModifyInventoryInteraction.firstRun` (`modules/interaction/interaction/config/server/ModifyInventoryInteraction.java:116-150`). Donner ou jeter au sol : `SimpleItemContainer.addOrDropItemStack(accessor, ref, InventoryComponent.getCombined(accessor, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK), stack)`.
- Mode de jeu : `Player.getGameMode()` → `protocol.GameMode` (`Adventure`, `Creative`).
- Changer l'état d'un bloc en gardant sa rotation : comme `ChangeStateInteraction` (`…/config/client/ChangeStateInteraction.java:95-125`), `BlockOperations.setBlock(chunkStore, section, x, y, z, id, type, rotation, 0, 260)` (`PERFORM_BLOCK_UPDATE | NO_SEND_PARTICLES`). Clé d'un état : `"*" + bloc + "_State_Definitions_" + état` (`StateData.java:112`, `AssetExtraInfo.java:42`). **[in-game]** : affichage immédiat côté client.

## 23. Validation des assets d'un pack : un asset invalide arrête le serveur

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/server/core/`. Constaté dans le journal de l'utilisateur le 2026-09-28 (icônes de Decorations hors `Icons/Items`).

- **Racines imposées** (`asset/common/CommonAssetValidator.java:15-35`, contrôle l. 80-99) : un chemin `Common/` référencé doit commencer par une des racines, avoir l'extension, et **exister** (`CommonAssetRegistry.hasCommonAsset`, l. 102-111 ; pour un asset d'UI, `@2x.png` suffit). Ceux qui nous concernent :
  - `ICON_ITEM` (`Item.Icon`, `asset/type/item/config/Item.java:100`) : `png` sous `Icons/ItemsGenerated` ou `Icons/Items` ;
  - `MODEL_ITEM` (`BlockType.CustomModel`, `asset/type/blocktype/config/BlockType.java:178` ; `Item` l. 192) : `blockymodel` sous `Blocks`, `Items`, `Resources`, `NPC`, `VFX` ou `Consumable` ;
  - `TEXTURE_ITEM` (`BlockType.Textures`, l. 150 et 289 ; `CustomModelTexture.Texture`, `CustomModelTexture.java:16` ; `Item` l. 211) : `png` sous `Blocks`, `BlockTextures`, `Items`, `NPC`, `Resources` ou `VFX` ;
  - `ANIMATION_ITEM_BLOCK` (`BlockType` l. 229, `Item` l. 224, 251).
- **Un asset invalide dans un pack immuable arrête tout le serveur** : un pack enregistré depuis un `.zip` ou un `.jar` est immuable (`asset/AssetModule.java:444-448`). `AssetRegistryLoader.loadAssets0` passe alors `shouldFail = assetPack.isImmutable() && !IGNORE_BROKEN_MODS` (`asset/AssetRegistryLoader.java:240-241`) et, au moindre asset en échec, `event.failed(shouldFail, "Mod … failed to load…")` (l. 310-315). `HytaleServer` voit `LoadAssetEvent.isShouldShutdown()` et arrête le serveur (« Asset validation FAILED », `HytaleServer.java:355-364`). Nos sous-plugins sont des zips : une icône hors racine ou un fichier manquant ne désactive pas seulement le pack, il empêche le serveur de démarrer. D'où les contrôles du générateur (`tools/decorations/pack.py`) et du build (`checkSubpluginAssets`).

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
