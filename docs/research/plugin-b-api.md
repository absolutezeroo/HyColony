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
(The `~fluid:` pseudo-key is a suggestion, so that `BlockCatalog.kind` can return `FLUID`.)

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

## 2. ItemCatalog and BlockCatalog

- **Item for a block**: `BlockType.getItem()` → `@Nullable Item` (the asset container key). A block defined inside an item JSON has **the same id** as the item (`Item.processConfig`: `if (hasBlockType) blockId = id`). For a state id (`*…`), use `getDefaultStateKey()` first. This container item is not always the one that **places** the block (a wall torch, a large chest): see § 51.
- **Block for an item**: `Item.hasBlockType()` and `Item.getBlockId()` (null when there is no block).
- `Item.getMaxStack()`: when absent it is filled in as 100, or 1 for a tool, weapon, armor or builder tool. The vanilla chest uses 25.
- **Tool category**: every vanilla tool carries specs for *all* gather types, so read the category from `Item.getPlayerAnimationsId()`, which is inherited from the parent: `"Pickaxe"`, `"Hatchet"` (axe), `"Shovel"`. Tool items are `Server/Item/Items/Tool/{Pickaxe,Hatchet,Shovel}/Tool_<Kind>_<Material>.json`, with `Categories: ["Items.Tools"]` and `Tags.Type: ["Tool"]`.
- **Tool tier**: the `Quality` of the spec for the matching gather type (for example the pickaxe's `Rocks` spec: Crude 1, Iron 3). Speed: that spec's `getPower()` (or `ItemTool.getSpeed()`).
  - Pickaxes in 0.7.0-pre.5 (`Rocks` Quality): Wood 1, Crude 1, Copper 2, Iron 3, Thorium 4, Cobalt 4, Adamantite 5, Mithril 6, Onyxium 6; hatchets (`Woods`) and shovels (`Soils`) carry no Quality. HyColony's tool level is `Quality - 1`, so a hut of level 0-1 takes Wooden/Crude/Copper tools, 2 Iron, 3 Thorium/Cobalt, 4 Adamantite, 5 any (Mithril/Onyxium). The builder's and farmer's help texts (`ui.info.builder.1`, `ui.info.farmer.1`) say so in place of MC's wood/stone/iron/diamond/enchanted (2026-10-02); Hytale has diamond and emerald only as gems (`Rock_Gem_*`), and no enchantment.
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
- **Cible inatteignable** : `Seek` (`BodyMotionFind`) suit par défaut le meilleur chemin partiel. L'option `UseBestPath` vaut vrai par défaut (« Use best partial path if goal can't be reached », `BuilderBodyMotionFindBase.java:97`). Quand l'A* échoue, `BodyMotionFindBase.java:513` appelle `findBestPath`, qui garde le nœud exploré le plus proche du but selon l'estimation (`BodyMotionFind.java:174-176`). Viser un bloc plein, comme le bloc d'une hutte, mène donc au point atteignable le plus proche, parfois un toit. HyColony vise plutôt une case libre à côté du bloc (`core/.../colony/BlockApproach`).

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

- **Inventaire et glisser-déposer dans une page personnalisée (vérifié en jeu, 2026-09-28, établi DO-2a)** :
  - **Une fenêtre ne s'affiche pas seule.** `openCustomPageWithWindows(ref, store, page, window)` ouvre la page et la fenêtre (id ≥ 0), mais le client ne dessine aucune fenêtre à côté d'une page personnalisée. C'est à la page d'afficher une `ItemGrid` pour elle.
  - **Remplir la grille par `ItemStacks`.** Il faut `ui.set("#Grid.ItemStacks", ItemStack[])` (`ItemStack.CODEC` est dans `UICommandBuilder.CODEC_MAP` ; `ItemStack.EMPTY` pour une case vide, jamais `null`) et `ui.set("#Grid.InventorySectionId", section)`, avec `AreItemsDraggable: true`. La section vaut -1 pour la barre rapide, -2 pour le sac (`InventoryComponent.*_SECTION_ID`), ou l'id d'une fenêtre ouverte.
  - **Avec `Slots` (des `ItemGridSlot`), rien ne marche.** C'est pourtant ce que fait `EntitySpawnPage`. La grille s'affiche, mais ni glisser, ni clic, ni aucun événement. Sans contenu envoyé par le serveur, la grille reste vide.
  - **Maj+clic** : natif (`SmartMoveItemStack`), rien à faire côté serveur.
  - **Glisser** : le client dessine le glisser, puis envoie un événement `Dropped` sur la grille d'arrivée, qui doit être liée à `CustomUIEventBindingType.Dropped`.
    - Clés reçues : `SlotIndex`, `SourceInventorySectionId`, `SourceSlotId`, `SourceItemGridIndex`, `ItemStackId`, `ItemStackQuantity`, `PressedMouseButton`, plus les données de la liaison.
    - Le client **ne déplace rien** : le serveur appelle `InventoryUtils.moveItem(ref, fromSection, fromSlot, quantity, toSection, toSlot, store)`, comme `InventoryPacketHandler.handle(MoveItemStack)`. `moveItem` respecte les filtres de case du conteneur d'arrivée (`setSlotFilter(FilterActionType.ADD, …)`).
    - Brique réutilisable : le mod HyBlockUI, `blockui/…/dev/hyblockui/api` (`InventoryGrids`, `InventoryDrop`, `InventoryMoves`, `InventoryWatch`, `ReturningContainerWindow`).
  - **Événements de souris sur une grille.** Le client refuse de lier `Activating`, `RightClicking`, `DoubleClicking`, `MouseButtonReleased` et `DragCancelled` à une `ItemGrid` (« Failed to Apply CustomUi Binding », déconnexion). Il accepte les types `Slot*` et `Dropped`.
  - **Avatar** : `CharacterPreviewComponent` dans une page personnalisée montre le personnage du joueur (vérifié en jeu).
  - **Contenu rendu à la fermeture.** À la déconnexion, `WindowManager.closeAllWindows` appelle `onClose0` : c'est là qu'on rend le contenu d'une fenêtre au joueur (`ReturningContainerWindow`, comme `StructuralCraftingWindow.onClose0`).
  - **Textures d'une page personnalisée (vérifié en jeu, 2026-09-28).** Une page ne charge que les textures du pack d'assets envoyé par le serveur (`Common/...`). Les fichiers d'interface du client (`Client/Data/Game/Interface/...`) lui sont inaccessibles. Quatre chemins vers `Slot.png` ont été essayés, aucun ne s'affiche : `Pages/Inventory/Slot.png` (celui du client), `InGame/Pages/Inventory/Slot.png`, `Interface/InGame/Pages/Inventory/Slot.png` et `Game/Interface/InGame/Pages/Inventory/Slot.png`. Le client ne journalise rien. Pour un rendu identique, les textures sont donc copiées dans le pack (`docs/native-ui-textures.md`). `Common/UI/ItemQualities/Slots/SlotDefault.png` (serveur) est une quasi-copie de `Slot.png`.
  - **Case utilitaire.** Un objet posé dans l'utilitaire depuis une autre section devient l'objet actif (`InventoryPacketHandler.handle(MoveItemStack)`, l. 399-416 : `InventoryActiveSlotRequestEvent`, `InventoryUtils.setActiveSlot`, paquet `SetActiveSlot`). Une page qui affiche l'utilitaire doit faire de même (aucune ne l'affiche aujourd'hui). Le joueur a 4 cases utilitaires (`PlayerSystems.PlayerInitSystem`).

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
- **Progress bar**: the `Common.ui` template `@ProgressBar` (a `ProgressBar` 284 × 6 with the `Common/ProgressBar*.png` textures). Vanilla use, from `Pages/UIGallery/Categories/ProgressContent.ui`: `$C.@ProgressBar #Bar { @Anchor = (Bottom: 4, Left: 0); Value: 0.75; }`. `Value` is a float in [0, 1], set with `ui.set(sel + " #Bar.Value", 0.5f)` (`MemoriesPage`, `PrefabEditorSaveSettingsPage`). A bare `ProgressBar` takes its own `Background` (a colour works) and `BarTexturePath`, as `Mc/SkillLine.ui`'s 2 px XP bar. A row's `Anchor` can be changed at runtime with `setObject(sel + ".Anchor", anchor)` (`PointInspectorPage`), as the request tree indents its children.

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
- **Mais `getData()` ne doit pas rendre `null` pour une porte (vu en jeu le 2026-09-28).** `BlockOperations.setBlockInteractionState` (l. 394) et `WorldChunk.setBlockInteractionState` (l. 331) ne changent l'état d'un bloc que si `getData() != null` : une porte créée jouait son son (`DoorInteraction`) et restait fermée. Correctif (`VariantBlockType`) : `getData()` est redéfinie pour rendre le `data` du gabarit, le champ `data` restant `null`. Le codec (`BlockType.CODEC`, `asset -> asset.data`, l. 93), donc `AssetStore.loadContainedAssets`, ainsi que `getItem`, `getDefaultStateKey` et `toPacket` lisent le **champ** : aucun des pièges ci-dessus ne revient. Effet de bord voulu : `ConnectedBlockTagShape` et `BlockConditionInteraction` lisent les tags du gabarit. `DoorInteraction` (l. 267, 380) passe par `getItem()`, que `VariantBlockType` rend déjà (l'objet de la variante).
- **Piège : `loadAssets` sur le thread du monde bloque le serveur pour toujours** (vu en jeu le 2026-09-27, vidage de threads : `WorldThread - default` parqué dans `AssetStore.loadAssets0` sur `writeLock`). `World.tick` garde le verrou de lecture de `AssetRegistry.ASSET_LOCK` pendant tout le tick, tâches et commandes comprises (`server/core/universe/world/World.java:376-403`, verrou l. 380-398 ; lignes relues le 2026-09-30). `loadAssets` prend le verrou d'écriture dans `loadAssets0` (`assetstore/AssetStore.java:833-921`), et un `ReentrantReadWriteLock` ne passe pas de la lecture à l'écriture. Il faut donc charger hors du thread du monde (`CompletableFuture.runAsync`), puis revenir par `world.execute` pour poser le bloc, comme le vanilla `BlockSpawnerSettingsPage.writeAssetAsync` (l. 433-453, sur `HytaleServer.SCHEDULED_EXECUTOR`). En 0.7.0-pre.5, le verrou est pris aux l. 372-390 de `World.java`, et `IWorldChunks.waitForFutureWithoutLock` (qui relâchait le verrou pendant l'attente d'un futur) a disparu avec `IWorldChunks`. `addCommonAsset` ne prend pas ce verrou.
- **Charger à chaud** : `BlockType.getAssetStore().loadAssets(packKey, List.of(bt), AssetUpdateQuery.DEFAULT)` (`assetstore/AssetStore.java:463-509`) attribue un nouvel index (`BlockTypeAssetMap.putAll0`, l. 137-195), puis `HytaleAssetStore.handleRemoveOrUpdate` diffuse `BlockTypePacketGenerator.generateUpdatePacket` (`server/core/asset/HytaleAssetStore.java:88-111`) : un `UpdateBlockTypes` `AddOrUpdate` avec `maxId` et les drapeaux de reconstruction de `RebuildCache.DEFAULT` (tous `true`, `AssetUpdateQuery.java`) (`BlockTypePacketGenerator.java:43-66`).
- **Poser par id** : `World.setBlock(x, y, z, key)` a disparu en 0.7.0-pre.5 avec `IChunkAccessorSync` (`World` n'implémente plus d'accesseur de chunk). On pose avec `BlockOperations.setBlock` sur la référence de section (`universe/world/chunk/BlockOperations.java:37, 138`), qui prend un index : l'appelant résout la clé (`BlockType.getAssetMap().getIndex`). Un id inconnu au chargement d'un tronçon devient un `BlockType` « Unknown » (`BlockType.getBlockIdOrUnknown`, l. 2242-2257).
- **Vérifié en jeu (2026-09-27)** : pas fiable. Après chaque démarrage, avec la séquence de `d67fe4c`, seuls les un ou deux premiers blocs générés s'affichent sans reconnexion (les autres ordres essayés échouent dès le premier) ; après reconnexion, tous s'affichent (détail dans `docs/research/domum-ornamentum.md` B.6). Chaque reconstruction fait aussi scintiller l'image.

## 18. Météo, vitesse des PNJ et générateurs de coffres (SP3a, tâche 11)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`. Recherche complète : `docs/research/sp3a-warehouse-courier-hytale.md` § 1 et § 3.

- **Pluie à une position** (`HytaleWorldQuery.isRainingAt`) :
  - `WeatherResource` (`builtin/weather/resources/WeatherResource.java`) se lit par `world.getEntityStore().getStore().getResource(WeatherResource.getResourceType())`, comme `WeatherSetCommand.setForcedWeather` (l. 34-38). `getForcedWeatherIndex()` vaut 0 sans météo forcée ; `getWeatherIndexForEnvironment(env)` renvoie `Integer.MIN_VALUE` tant que `WeatherSystem` n'a rien tiré (`defaultReturnValue`, l. 17-19) ;
  - l'environnement du bloc : `world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z))` (`null` si non chargé), puis `BlockChunk.getComponentType()` et `BlockChunk.getEnvironment(x, y, z)` en coordonnées monde, 0 hors de [0, 320[ (`server/core/universe/world/chunk/BlockChunk.java:310-320`). Même ordre que `WorldSupport.getCurrentWeatherIndex` (`server/npc/role/support/WorldSupport.java:273-291`) : forcée d'abord, sinon par environnement ;
  - `Weather.getAssetMap()` est un `IndexedLookupTableAssetMap` dont `getAsset(int)` rend `null` hors bornes (`assetstore/map/IndexedLookupTableAssetMap.java:70-72`), donc `MIN_VALUE` ne lève rien. `Weather.getParticle()` (`server/core/asset/type/weather/config/Weather.java:594`) rend un `protocol.WeatherParticle` au champ public `systemId` ;
  - les systèmes de particules de pluie et de neige sont dans l'id-map (`precipitationParticles`), vérifiés par `IdMap.validate` (`ParticleSystem`) : `Rain`, `Rain_Light`, `Rain_Heavy`, `Snow_Light`, `Snow_Heavy`, `Snow_Storm` (fichiers `Server/Particles/Weather/{Rain,Snow}/*.particlesystem` du ZIP) ;
  - `/weather set <id>` et `/weather reset` passent par `WeatherResource.setForcedWeather` (`builtin/weather/commands/WeatherSetCommand.java:30-38`, `WeatherResetCommand.java:19`) : de quoi tester en jeu.
- **Vitesse de marche d'un PNJ** (`plugin/npc/body/CitizenSpeed`, avec `BodySpeeds` qui y ajoute le ralentissement de la faim) :
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
- `name` : identifiant du pack, par convention `new PluginIdentifier(manifest).toString()` (`Groupe:Nom`, l. 109, 392-393). Un même nom déjà présent : si la source existante l'emporte (`PackSource.overrides` = ordinal plus petit, `assetstore/AssetPack.java:129-131`, ordre `CLI < CLASSPATH < MODS < RUNTIME`), le nouveau est ignoré et la méthode renvoie `true` ; à source égale, erreur SEVERE et `false` (l. 419-435). Les appelants vanilla arrêtent alors le serveur (l. 110-113, 405-408). Choisir un nom unique par sous-pack (`HyColony:hycolony_<Nom>`).
- **Le nom doit être de la forme `groupe:nom`** : au `LoadAssetEvent`, chaque pack est indexé par `PluginIdentifier.fromString(assetPack.getName())` (`AssetModule.java:511`), qui lève `IllegalArgumentException` sans exactement un `:` (`common/plugin/PluginIdentifier.java:84-90`), ce qui ferait échouer le chargement de tous les assets. Un nom comme `HyColony_Styles_Outlander` est donc interdit ; HyColony utilise `new PluginIdentifier(manifest).toString()`, soit `HyColony:hycolony_<Nom>` (groupe `HyColony` et nom `hycolony` de notre manifeste de plugin).
- **Pas de dépendance dans le manifeste d'un sous-pack** : `Mod.calculateLoadOrder` lève `ModLoadOrderException` pour une dépendance absente (`common/plugin/Mod.java:60-120`), transformée en `IllegalStateException` qui arrête le chargement des assets (`AssetModule.java:516-521`). Tous les packs dépendent déjà implicitement des packs de classpath du groupe `Hytale` (`Mod.java:48-52`, 76-78).
- `manifest` : un `PluginManifest` déjà construit, **pas lu par `registerPack`**. Le fichier `manifest.json` n'est lu que par le chemin « dossier `mods/` » (`loadPackManifest`, l. 325-358). Le manifeste sert ensuite à l'ordre de chargement (`Mod.calculateLoadOrder`, l. 508-519, dépendances du manifeste) et à la vérification `ServerVersion` (l. 130-170, faite seulement dans `setup()` d'`AssetModule`, donc pas pour un pack ajouté plus tard). On peut le décoder avec `PluginManifest.CODEC` comme l. 331-335.
- `path` (l. 437-454) :
  - fichier `.zip` ou `.jar` : ouvert en `FileSystems.newFileSystem(path, null)`, racine du pack = **racine du zip**, pack marqué immuable ;
  - sinon : utilisé tel quel comme racine, immuable seulement si `CommonAssetsIndex.hashes` est présent à la racine (l. 443).
  - Il n'y a pas de paramètre « sous-dossier dans un jar » : un jar donne toujours sa racine. Les sous-packs rangés dans `subplugins/<Nom>.zip` dans notre jar ne sont donc **pas** vus par le pack principal (qui ne lit que `Common/` et `Server/` à la racine) ni atteignables en passant le chemin du jar.
- Structure d'un pack : `Common/…` (assets envoyés au client, `server/core/asset/common/CommonAssetModule.java:153`, 446-489), `Server/<chemin du store>` (assets serveur, `AssetModule.java:597-600`), `Server/Languages/<locale>/*.lang` (`server/core/modules/i18n/I18nModule.java:223`), `Server/Prefabs/…` (`server/core/prefab/PrefabStore.java:262`).
- Moment de l'appel. Ordre de démarrage (`server/core/HytaleServer.java:342-395`) : `pluginManager.setup()` → `LoadAssetEvent` → `pluginManager.start()`.
  - Appelé dans `setup()` du plugin : `hasLoaded` est faux, le pack est seulement ajouté à la liste (l. 455-461) ; il est chargé avec tous les autres au `LoadAssetEvent` (l. 498-526), après tri par dépendances. C'est le moment recommandé.
  - Appelé plus tard (`start()`, commande) : `AssetPackRegisterEvent` est émis (l. 463). Il charge les stores serveur (`AssetModule.java:202`), les `Common/` (`CommonAssetModule.java:104`, puis `RequestCommonAssetsRebuild` diffusé l. 179), les langues (`I18nModule.java:96`) et les rôles PNJ (`server/npc/NPCPlugin.java:515`). L'éditeur d'assets fait exactement cela à chaud (`builtin/asseteditor/AssetEditorPlugin.java:853`, `server/core/ui/browser/AssetPackSaveBrowser.java:550`). À noter : `AssetRegistry.ASSET_LOCK` est pris en écriture pendant la diffusion (l. 456-466), donc jamais depuis le thread d'un monde qui tient déjà un verrou d'assets (voir la mémoire « world thread asset lock »).
- Envoi au client : oui. `CommonAssetModule` parcourt `Common/` de **chaque** pack enregistré (l. 99-104) et l'envoie (`sendAsset`, l. 216-218). Les assets serveur (`Server/`) sont chargés côté serveur pour chaque pack (`AssetRegistryLoader.loadAssets(event, pack)`, l. 524-525 et 202).
- Comment notre plugin livre aujourd'hui ses assets : `plugin/src/main/resources/manifest.json` a `"IncludesAssetPack": true` ; `PluginManager` met alors le jar du plugin dans `classpathAssetPacks` (`server/core/plugin/PluginManager.java:691-692`, 737-738), qu'`AssetModule.setup()` enregistre avec `PackSource.CLASSPATH` (`AssetModule.java:108-115`) ; un plugin chargé plus tard passe par `registerAssetPackIfNeeded` (`PluginManager.java:823-847`, source `RUNTIME`). Racine du pack = racine du jar, d'où `Common/` et `Server/` directement sous `plugin/src/main/resources/`.

**Pack dans le jar ou extrait ?** `registerPack` exige un `Path`. Trois options, par ordre de sûreté :

1. **Recommandé : un zip par sous-pack, extrait sur disque.** Le build produit `subplugins/<Nom>.zip` (avec `Common/`, `Server/`) dans le jar ; au `setup()`, le plugin le copie dans son dossier de données (si absent ou différent) puis appelle `registerPack(id, cheminDuZip, manifest, PackSource.RUNTIME)`. C'est exactement le cas d'un zip de `mods/` : racine = racine du zip, pack immuable, donc pas de surveillance de fichiers ni d'écriture dans le pack (le cache `.lpf` des prefabs d'un pack immuable va sous `.cache/prefabs/<pack>/`, `server/core/prefab/selection/buffer/PrefabBufferUtil.java:135-149`).
2. Dossier extrait sur disque : marche aussi, mais le pack est **mutable** (pas de `CommonAssetsIndex.hashes`) : surveillé par l'`AssetMonitor`, le cache `.lpf` est écrit à côté des prefabs (`PrefabBufferUtil.java:151`) et `CommonAssetModule` **supprime** tout fichier `*.hash` qu'il trouve (`CommonAssetModule.java:467-469`).
3. Déconseillé : un `Path` d'un `FileSystem` zip ouvert sur notre propre jar, pointant `subplugins/<Nom>.zip`. Le code n'appelle jamais `toFile()` sur ces chemins (`FileCommonAsset.getBlob0` utilise `Files.readAllBytes`, `server/core/asset/common/asset/FileCommonAsset.java:30`), mais le pack serait mutable : l'`AssetMonitor` enregistrerait un chemin zip sur le `WatchService` du système par défaut (`server/core/asset/monitor/PathWatcherThread.java:42`, 135), et le cache `.lpf` serait écrit **dans le jar** (zipfs ouvert en écriture par défaut). **[in-game]** non essayé.

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
- **Côté HyColony, en revanche, c'est bloquant** : `IdMap.validate()` vérifie chaque id de `id-map.json` (`plugin/.../IdMap.java:100-115`) et, au moindre manque, `WorldRuntimes.enableIfIdsValid` **désactive tout HyColony** (`plugin/.../WorldRuntimes.java:73-81`). Les fragments `id-map.json` d'un sous-pack désactivé ne doivent donc jamais être fusionnés. Un plan de `styles.json` dont le prefab manque est déjà toléré : `findAssetPrefabPath` renvoie `null` (`server/core/prefab/PrefabStore.java:321-331`) et `HytaleBlueprintSource` renvoie vide avec un avertissement unique (`plugin/.../prefab/HytaleBlueprintSource.java:131-154`).

### 21.5 Lecture actuelle de `styles.json`, `id-map.json` et des prefabs

- `styles.json` et `id-map.json` : `SubPlugins.idMap()` et `SubPlugins.styles()` lisent le fichier du cœur (`/hycolony/<fichier>` du classpath) puis y fusionnent le fragment de chaque pack activé, `/subplugins/<Nom>/hycolony/<fichier>` (`plugin/.../subplugin/SubPlugins.java:132-156`, `BundledPacks.fragment`). La fusion est faite par `JsonFragments` (`core/.../kernel/config/JsonFragments.java`), sur un niveau pour l'id-map et deux pour les styles ; une clé définie deux fois est journalisée SEVERE et la première l'emporte. `IdMap.of` et `PrefabStyles.of` reçoivent le JSON fusionné.
- Les fragments restent sur le classpath, hors du zip d'assets : ils ne passent pas par `AssetModule`, et un pack désactivé n'est jamais fusionné.
- Prefabs : par clé via `PrefabStore.get().findAssetPrefabPath(prefab)` (`HytaleBlueprintSource.java:98`, 149), qui cherche `Server/Prefabs/<clé>` dans **tous les packs enregistrés**, dans l'ordre de la liste (`PrefabStore.java:321-331`). Un prefab livré par un sous-pack est donc trouvé sans changement, dès que le pack est enregistré.

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
- **Un asset invalide dans un pack immuable arrête tout le serveur** : un pack enregistré depuis un `.zip` ou un `.jar` est immuable (`asset/AssetModule.java:444-448`). `AssetRegistryLoader.loadAssets0` passe alors `shouldFail = assetPack.isImmutable() && !IGNORE_BROKEN_MODS` (`asset/AssetRegistryLoader.java:240-241`) et, au moindre asset en échec, `event.failed(shouldFail, "Mod … failed to load…")` (l. 310-315). `HytaleServer` voit `LoadAssetEvent.isShouldShutdown()` et arrête le serveur (« Asset validation FAILED », `HytaleServer.java:355-364`). Nos sous-plugins sont des zips : une icône hors racine ou un fichier manquant ne désactive pas seulement le pack, il empêche le serveur de démarrer. D'où les contrôles du générateur (`tools/blockpaint/pack_rules.py`) et du build (`checkSubpluginAssets`).

## 24. Textures et icône d'un bloc vanilla (`domum/plugin/…/runtime/MaterialCatalog`)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/server/core/asset/type/`.

- `BlockType.getTextures()` (`blocktype/config/BlockType.java:1483`) renvoie `BlockTypeTextures[]`, une entrée par variante pondérée (`Weight`) ; vide ou `null` pour un bloc sans `Textures`.
- `BlockTypeTextures.getNorth()`/`getSouth()`/`getEast()`/`getWest()`/`getUp()`/`getDown()` (`blocktype/config/BlockTypeTextures.java:119-139`) : chemin `Common/` de la texture de chaque face (`isUniform()` l. 155 : les six identiques).
- `BlockType.getDrawType()` (l. 1536) : `com.hypixel.hytale.protocol.DrawType` (`Cube`, `CubeWithModel`, `Model`…).
- `BlockType.getItem()` (l. 1368) : l'objet du bloc, ou `null` ; `Item.getIcon()` (`item/config/Item.java:1071`) : chemin `Common/` de son icône.
- Recherche par clé : `BlockType.getAssetMap().getAsset(String)` (`assetstore/map/DefaultAssetMap.java:59`), `null` si absente.

## 25. Copier un `Item` : interactions déjà traitées (`domum/plugin/…/runtime/DynamicBlockTypeFactory.VariantItem`)

Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/server/core/`.

- Le constructeur de copie `Item(Item other)` reprend `other.interactions` tel quel (`asset/type/item/config/Item.java:715`) : la table **déjà traitée** par `processConfig` (l. 1257-1286), qui a complété au décodage chaque type absent par les interactions « à mains nues » de l'objet (`UnarmedInteractions` de son `PlayerAnimationsId`), puis par celles de `"Empty"` (`putIfAbsent`).
- Une interaction écrite dans le JSON de l'objet devient un asset contenu nommé `"*" + clé + "_" + chemin` (`assetstore/AssetExtraInfo.java:42`). Retirer une entrée de la copie ne rétablit donc pas le repli : il faut le refaire (`modules/interaction/interaction/UnarmedInteractions.getAssetMap()`, `DEFAULT_UNARMED_ID = "Empty"`). Pour un bloc, le repli `Block` donne `Secondary = Block_Secondary`, qui pose le bloc ; `Empty` n'a pas de `Secondary`.

## 26. Recettes et tables (`plugin/crafting/`, `block/BenchTiers`, `prefab/PrefabCells`)

Vérifié dans les sources décompilées de 0.6.8 et dans `Assets.zip`, le 2026-09-28.

- **Recettes** : `CraftingRecipe.getAssetMap().getAssetMap()` (`server/core/asset/type/item/config/CraftingRecipe.java`). La carte contient aussi les recettes déclarées dans un objet (`Item.Recipe`), avec l'identifiant `<objet>_Recipe_Generated_0` (`Item.java:1337-1352`). Accesseurs :
  - `getInput()` et `getOutputs()` renvoient des `MaterialQuantity[]` ;
  - `getPrimaryOutput()` peut être null pour un fichier de `Server/Item/Recipes` ; `processConfig` recopie alors la sortie principale dans `outputs` ;
  - recettes autonomes `Server/Item/Recipes/**/<Id>.json` (identifiant = nom du fichier, seule `Input` est obligatoire, `AssetRegistryLoader.java:634-643`) : le jeu en livre environ 400 (`Salvage/**`, type `Processing`). HyVanilla y met la recoloration des lits (type `Crafting`, 2026-09-29) ;
  - `getBenchRequirement()` renvoie un **tableau** de `protocol.BenchRequirement` (`type`, `id`, `categories`, `requiredTierLevel`) ;
  - `isKnowledgeRequired()`.
- **Plusieurs tables pour une recette** : c'est le cas de 28 recettes sur 1 984. HyColony garde la première exigence de type `BenchType.Crafting` (`Fieldcraft` compris). Les types `Processing`, `DiagramCrafting` et `StructuralCrafting` sont hors portée.
- **Ingrédients** (`MaterialQuantity`) :
  - `getItemId()`, `getResourceTypeId()`, `getQuantity()` ;
  - un tag n'est exposé que par `getTagIndex()`, sans getter pour son nom (`AssetRegistry.getOrCreateTagIndex`). Un seul `ItemTag` apparaît dans toutes les recettes de 0.6.8 : HyColony écarte cette recette.
  - Correspondance d'un type de ressource : l'objet liste le type dans `Item.getResourceTypes()` (`ItemContainer.getMatchingResourceType`). Les 1 304 déclarations de 0.6.8 ont toutes `Quantity: 1`.
  - Objets cachés (0.7.0-pre.5, vérifié le 2026-10-02) : Hytale écarte les qualités `Developer`, `Technical`, `Debug` et `Template` (`builtin/buildertools/BlockColorIndex.EXCLUDED_QUALITIES`), comparées à `Item.getQualityIndex()` par l'index de `ItemQuality.getAssetMap().getIndexOrDefault(id, -1)`. Les `Prototype_*`, `Debug_*`, fenêtres `*_Test` et `Furniture_Cybercity_Windows*` de type `Fuel` sont tous `Developer`, sauf `Debug_MusicEmitter_*`. `Quality` n'est pas hérité du `Parent` (`append`, `Item.java:159`), contrairement à `ResourceTypes` : les quatre `_Debug/Debug_MusicEmitter_*` n'ont aucune qualité et héritent les types de leur parent `Wood_Wisteria_Wild_Trunk_Full` (`Fuel`, `Charcoal`, `Wood_*`). Ce sont les seuls objets `Debug_`/`Prototype_` dans ce cas. `ResourceTypeIndex` écarte les qualités cachées et les identifiants `Debug_`.
  - `Item.isState()` n'écarte pas que des états de blocs : il est vrai pour le seau de lait (`Container_Bucket` et `Deco_Bucket` aux états `Filled_*`, seuls fournisseurs de `Milk_Bucket`) et pour les raretés de poisson (`Fish`). Il ne faut donc pas filtrer dessus.
- **Recette d'un objet vanilla, surcharge entre packs, casse des blocs posés** (pre.5, 2026-10-02) : voir `barrel-recipe.md`. En bref :
  - une recette autonome doit porter `Output` **et** `PrimaryOutput` (`CraftingRecipe.java:236-239`) ;
  - un asset de même clé dans un pack chargé plus tard **remplace** l'asset vanilla, sans fusion ni avertissement (`DefaultAssetMap.java:303-304`, `AssetStore.java:67`) ;
  - `BlockGathering.UseDefaultDropWhenPlaced` rend le bloc lui-même s'il a été posé par un joueur, car la case est alors marquée deco (`BlockHarvestUtils.java:959-965`, `BlockPlaceUtils.java:438-440`). `BlockOperations.setBlock` ne marque pas la case.
- **Recettes connues d'un joueur** : `Player.getPlayerConfigData().getKnownRecipes()`, un ensemble d'**identifiants d'objet de sortie principale**, pas de recettes (`CraftingManager.isValidBenchForRecipe`, l. 452-466).
- **Tables** : `BlockType.getBench()`, avec `getType()` (`protocol.BenchType`) et `getId()`.
  - Catégories : `CraftingBench.getCategories()[i].getId()`.
  - Niveaux : `Bench.getTierLevel(t)` lit `TierLevels[t - 1]`, et `getUpgradeRequirement(t).getInput()` donne le coût pour passer du niveau `t` à `t + 1` (`Bench.java:140-147`, `CraftingManager.finishTierUpgrade`).
  - Plusieurs blocs partagent un identifiant de table : `Bench_Farming` (7 niveaux, catégories `Farming`, `Seeds`, `Saplings`, `Essence`, `Planters`, `Decorative`) et `Bench_Trough` (0 niveau, catégorie `All`) sont tous deux `Farmingbench`.
  - Les montées de niveau de l'établi de fermier demandent des **types de ressource** (`Wood_Softwood_Trunk` × 5, puis `Wood_Lightwood_Trunk` × 10, etc.).
- **Niveau d'une table dans un prefab** : le composant `BenchBlock` du holder de la case porte `TierLevel` et `UpgradeItems` (`builtin/crafting/component/BenchBlock.java`, vu dans `Server/Prefabs/Cave/Klops/...`). Sans composant, le niveau vaut 1.
- **Monter une table posée** : on reproduit la fin de `CraftingManager.finishTierUpgrade` (l. 790-866) :
  1. `BlockModule.getBlockEntity(store, section, x, y, z)` ;
  2. les composants `BenchBlock` et `BlockModule.BlockStateInfo` ;
  3. `setTierLevel(n)` ;
  4. `BlockOperations.setBlockInteractionState(chunkStore, section, x, y, z, BenchBlock.getBaseBlockType(type), bench.getTierStateName(), true)` ;
  5. `info.markNeedsSaving()`.
- **[in-game]** Restent à voir en jeu :
  - l'aspect `Tier<N>` d'une table posée par le constructeur ;
  - l'ouverture de cette table par un joueur au bon niveau ;
  - le nombre de recettes que journalise `Recipe catalog: %d craftable recipes`.

## 27. Agriculture du fermier (`plugin/farming/`)

Vérifié dans les sources décompilées et dans `Assets.zip` le 2026-09-28. Complète `sp3b-hytale-farming.md` § 4.

- **Graine → bloc de culture** :
  - la graine ne porte pas son bloc directement. Il est dans `InteractionVars.SeedId.Interactions[0].BlockTypeToPlace` de son asset (`Plant_Seeds_Wheat` → `Plant_Crop_Wheat_Block`) ;
  - HyColony liste explicitement les 28 couples (14 cultures, normales et éternelles) dans l'id-map, section `farming.seeds` ;
  - les plants d'arbres, les herbes sauvages, les potions de test et le tournesol ont aussi un `SeedId`, mais ne sont pas des graines de champ.
- **Labour et plantation** : `WorldBlocks.place` pose le bloc avec `SetBlockSettings.NONE`, sans le bit 2. Le composant de bloc (`TilledSoil`, `FarmingBlock`) est donc cloné, et le sol se dégrade et la culture pousse comme ceux d'un joueur (§ 4 de la recherche). **[in-game]**
- **Stade d'une culture** : `HytaleWorldBlocks.get` rend le bloc de base, quel que soit le stade. Pour le stade exact, on lit l'identifiant dans `BlockSection.get` puis `BlockType.getAssetMap().getAsset(id)`. La culture est mûre si `getGathering().isHarvestable()` (`BlockGathering.java:108`) ; seul `StageFinal` déclare une récolte.
- **Récolte** :
  - les drops viennent de `BlockHarvestUtils.getDrops(type, 1, harvest.getItemId(), harvest.getDropListId())` (`BlockHarvestUtils.java:817`, `HarvestingDropType` l. 49 et 53) ;
  - le bloc est ensuite cassé par `WorldBlocks.breakBlock` (`NO_DROP_ITEMS`), dont les drops sont ignorés ;
  - une culture éternelle est reposée à son premier stade. Écart : Hytale la renvoie à `Stage1` par `FarmingUtil.harvest`, un peu plus loin.
- **Engrais** : `BlockModule.getComponent(TilledSoilBlock.getComponentType(), world, x, y, z)`, puis `setFertilized(true)`, puis `BlockSection.setTicking(x, y, z, true)` sur le sol et sur le bloc au-dessus (`FertilizeSoilInteraction.java:120-140`).
- **Houes** : `Tool_Hoe_*` n'ont pas de `ItemTool` exploitable (elles labourent par l'interaction `Hoe_Till`). L'id-map leur donne un niveau (`farming.hoes` : Crude 0, Copper 1, Iron 2, Thorium 3), et leurs usages valent `Item.getMaxDurability()`, puisque le labour coûte 1 de durabilité par bloc.
- **Collision d'un bloc (HyDomum, dessus des murets)** : `BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex()).get(rotationIndex).getDetailBoxes()` donne ses boîtes tournées, en blocs depuis le coin du bloc (`Box.min`/`max`, champs publics) ; `Full` est préchargé à l'index 0, et un bloc sans hitbox reçoit la boîte pleine (`RotatedVariantBoxes`). Seul un bloc `Material: Solid` (`BlockType.getMaterial()`, `protocol.BlockMaterial`) entre en collision (`BlockDataProvider`, l. 74-80) : une torche de bois a une hitbox mais pas de collision.
- **Barrières de champ** : 74 blocs, ceux dont l'identifiant finit par `_Fence` ou `_Fence_Gate`, et les murets de pierre `Rock_*_Wall`, plus les clôtures, portillons et murets HyDomum (`HyDomum_Fence`, `HyDomum_FenceGate`, `HyDomum_Wall`). Seuls 5 blocs portent le tag `SubType=Fence`. Liste dans `farming.fieldBarriers`. Un id d'état (`*X_State_Definitions_Y` : angle, T, portillon ouvert) compte comme son bloc, une variante HyDomum (`T__matériau`) comme son gabarit (`HytaleFarming.barrierKey`).
- **[in-game]** Restent à voir en jeu :
  - la pousse d'une culture posée par le fermier ;
  - la maturité lue au stade final ;
  - les drops de récolte dans l'inventaire du fermier ;
  - l'aspect « engraissé » d'une case.

**Gestes du labour, comme un joueur.**
- Le labour du joueur est l'interaction `Hoe_Till` (`Server/Item/Interactions/Weapons/Hoe/Attacks/Till/Hoe_Till.json`, type `ChangeBlock`) :
  - animation d'objet `Till` du jeu d'animations `Hoe` (`PlayerAnimationsId` de la houe ; `Server/Item/Animations/Hoe.json` : `Till`, `SwingLeft`, `SwingRight`) ;
  - son `SFX_Hoe_T1_Till` au centre du bloc (`WorldSoundEventId`) : `ChangeBlockInteraction` le résout par `SoundEvent.getAssetMap().getIndex(id)` (`Integer.MIN_VALUE` si inconnu), puis `SoundUtil.playSoundEvent3d` ;
  - les mêmes sons pour les 4 houes (T1).
- HyColony joue `AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Hoe", "Till", store)` (`HytaleCitizenBodies`, `BodyAnimation.TILL`) et le son de l'id-map `farming.tillSound` (`HytaleWorldEffects.tilled`). **[in-game]**

**Mettre un bloc en surbrillance pour un joueur.** Le paquet `DisplayDebug` (`protocol/packets/player/DisplayDebug.java`, id 114) dessine une forme (`DebugShape` : Sphere, Cylinder, Cone, Cube, Frustum, Sector, Disc, Donut) placée par une matrice 4×4 (`Matrix4dUtil.asFloatData`), avec une couleur, une durée en secondes, des drapeaux (`DebugUtils.FLAG_FADE` = 1, `FLAG_NO_WIREFRAME` = 2, `FLAG_NO_SOLID` = 4) et une opacité. `DebugUtils.add` l'envoie à tous les joueurs du monde ; `PlayerRef.getPacketHandler().write(packet)` l'envoie à un seul. HyColony s'en sert pour « Localiser » un champ (`plugin/ui/highlight/Highlights`, générique : une `Highlight` = des boîtes + un marqueur). `ClearDebugShapes` (id 115) efface toutes les formes du joueur. Vérifié en jeu : les formes s'affichent, mais ne se voient **pas** à travers les murs.

**Marqueur sur la carte pour un joueur.** `World.getWorldMapManager().addMarkerProvider(key, provider)` ; un `WorldMapManager.MarkerProvider` reçoit `update(World, Player, MarkersCollector)` pour chaque joueur, sur le thread de la carte, et ajoute `new MapMarkerBuilder(id, image, new Transform(x, y, z)).withName(Message).build()` par `collector.add` ou `addIgnoreViewDistance`. Images vanilla : `Common/UI/WorldMap/MapMarkers/*.png` (`Coordinate.png`, `Home.png`, `Warp.png`…). `Player.getPlayerRef()` est déprécié mais présent en 0.6.8. **[in-game]** : affichage sur la boussole (très probable, voir § 29).

**Barre de durabilité d'une `ItemGrid`.** Le schéma de l'éditeur UI donne à `ItemGridStyle` : `DurabilityBar` (UIPath), `DurabilityBarBackground` (PatchStyle ou chaîne), `DurabilityBarAnchor`, `DurabilityBarColorStart`, `DurabilityBarColorEnd` ; les piles portent `Durability` et `MaxDurability`. Aucune `.ui` vanilla ne s'en sert (l'inventaire du joueur est natif). Sans ces propriétés, la grille ne dessine pas de barre. HyColony les renseigne avec `Common/ProgressBarFill.png` et `Common/ProgressBar.png` (grilles du citoyen et du joueur). **[in-game]**

## 28. Mods multiples (essai du 2026-09-28)

Trois mods jetables, `HyColony:spikeui` ← `spikedomum` ← `spikecolony`, ont été construits par le workspace AzureDoom 1.0.51 et lancés en dev (`runAllMods`) puis en production (jars dans `mods/`). Ce sont les résultats du plan 1 de `specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md`. Les observations brutes sont dans `spike/RESULTS.md`, sur la branche `spike/multi-mod`.

### 28.1 Build et manifeste
- **Plugin Gradle.** À la racine, `id("com.azuredoom.hytale-tools") version "1.0.51" apply false`, puis `apply(plugin = "com.azuredoom.hytale-workspace")` : les deux plugins viennent du même jar, chargé une seule fois.
- **Identité.** Surcharger `modId` et `mainClass` dans chaque module suffit : aucune erreur « Duplicate workspace plugin identifier ».
- **Description et auteurs.** `Description` et `Authors` ne sont pas surchargés et viennent du `gradle.properties` racine : chaque mod doit fixer `modDescription` et `modCredits`.
- **Version des dépendances.** `"HyColony:spikeui": "0.1.0"` passe `validateManifest` et le chargement, mais **ce n'est pas une version exacte**. Une version nue dont le patch vaut 0 est une plage : ici `>=0.1.0 <0.2.0` (`SemverRange.fromString`, l. 173-209). Une version nue dont le patch n'est pas nul est refusée, avec un message qui conseille `=`. La version exacte s'écrit `"=0.1.0"`, soit `Groupe:Nom==0.1.0` dans `manifest_dependencies` (`ManifestUtils.parseDepMap` coupe sur le premier `=`).
- **Contenu des jars.** Chaque jar ne contient que son paquet, son `manifest.json` et son pack. Deux choses l'assurent :
  - `bundleAssetEditorRuntime = false` retire `asseteditor/**` ;
  - le `compileOnly` entre mods n'embarque rien.
- **`config.json` dans les ressources.** Un `config.json` placé dans `src/main/resources` **est embarqué dans le jar** : il faut l'exclure.
- **Ordre de chargement.** Les dépendances se chargent en premier, en dev comme en production : `spikeui`, puis `spikedomum`, puis `spikecolony`.

### 28.2 Classes
- **En production**, chaque jar a son propre `PluginClassLoader`. Une classe d'API n'existe qu'en **un seul exemplaire** : `SpikeUi` a le même chargeur et le même `identityHashCode`, vu des trois mods. Les appels entre mods passent par le chargeur pont (§ « Faits vérifiés » de la spec).
- **En dev** (`runAllMods`), un seul chargeur, `AppClassLoader`, sert pour tout. Le dev ne vérifie donc pas l'isolation.

### 28.3 Assets entre packs (vérifié en jeu, en dev et en production)

Toutes les références suivantes fonctionnent :
- **Modèle `.ui`.** Un `.ui` inclut le modèle d'un autre pack par chemin relatif : `$B = "../SpikeUI/Box.ui";` puis `$B.@Box {}`.
- **Texture dans le modèle.** Ce modèle se sert d'une texture relative à **son propre** dossier (`"Tex.png"`), et elle s'affiche.
- **Texture d'un autre pack.** Elle est référencée par chemin relatif (`"../SpikeUI/Tex.png"`). Le fichier est nommé `Tex@2x.png`, comme les textures natives.
- **`.ui` ajouté par le serveur.** Le serveur ajoute le `.ui` d'un autre pack : `ui.append("#Host", "Pages/SpikeUI/Panel.ui")`.
- **Traduction.** Une clé d'un autre mod s'écrit `%spikeui.hello` : le préfixe est le nom du fichier `spikeui.lang`.

### 28.4 Dépendance manquante (production)

Un mod **qui a un pack** et dont une dépendance manque **arrête tout le serveur**.
- **Le plugin est refusé proprement.** Le journal indique « SEVERE [PluginManager] Failed to load 'HyColony:spikecolony' because the dependency 'HyColony:spikedomum' could not be found! », et les autres mods se chargent.
- **Son pack d'assets reste enregistré.** « Loaded pack: HyColony:spikecolony from SpikeColony-0.1.0.jar » : le pack a le même manifeste que le plugin.
- **Le calcul de l'ordre des packs échoue.** `AssetModule.loadAllAssetPacks` lève « IllegalStateException: Failed to calculate asset pack load order », causée par « ModLoadOrderException: Missing required dependencies: HyColony:spikecolony requires: [HyColony:spikedomum] » (`Mod.calculateLoadOrder`).
- **Le serveur s'arrête.** « Shutting down... 'client.disconnection.shutdownReason.crash.startFailed' ».

Pour HyColony, qui a un pack : sans HyDomum, le serveur ne démarre pas, et le journal nomme la dépendance manquante.

### 28.5 Config et données
- **Staging en dev.** `stageAllModAssets` recrée `run/mods/HyColony_<nom>/` à chaque lancement. Sous Windows, chaque fichier est un **lien dur** vers les ressources et les classes, pas un lien symbolique.
- **Packs en dev.** Les packs du classpath passent en premier : « Asset pack … already registered (CLASSPATH), skipping MODS ». Pour les mods dont le pack est sur le classpath, les assets et les traductions sont donc lus **directement dans `src/main/resources`**. Un mod sans ressources sur le classpath est lu depuis `run/mods/HyColony_<nom>` : c'est le cas de `spikedomum`.
- **Config en dev.** Un `config.json` dans les ressources, lié en dur, **est lu** au démarrage (`Counter` 100 → 101). La première écriture du mod le **remplace** par un fichier neuf, et l'ancien lien devient `config.json.bak`. C'est le comportement de `BsonUtil.writeDocumentSync`, qui **remplace** le fichier. Le fichier des ressources ne change donc pas, et le lancement suivant repart de sa valeur.
- **Écriture sur place.** Une écriture **sur place**, par exemple `Files.write` sur un fichier existant, traverse le lien dur et modifie les ressources. Ce cas se déduit du fonctionnement d'un lien dur ; l'essai ne l'a pas testé.
- **Données en dev.** Ce qu'un mod écrit dans `run/mods/<mod>/` est perdu à chaque lancement. `run/universe/` survit.
- **En production.** Rien n'est effacé : `mods/HyColony_<nom>/config.json` et les données persistent d'un lancement à l'autre. Un mod qui n'écrit rien n'a pas de dossier.

### 28.6 HyColony et HyLens en production (protocole, à dérouler)

Les essais de la spec `specs/2026-09-30-hycolony-api-hylens-design.md` § 8, numérotés 235 à 241 dans `docs/TESTING.md`. Les résultats attendus se déduisent des faits déjà vérifiés, dont le piège 1.9 (le rechargement de HyColony échoue) ; ils restent **[in-game]** tant que l'utilisateur ne les a pas déroulés.

| Essai | Attendu | Pourquoi |
|---|---|---|
| Les cinq ensemble | HyLens se charge après HyColony et HyBlockUI | Arêtes vers les dépendances dures (§ 28.1 ; `hycolony-api.md` § 3) |
| Sans HyColony | Le serveur s'arrête et nomme `HyColony:hycolony` | HyLens a un pack (§ 28.4) |
| HyColony sans HyLens | Tout marche | Personne ne dépend de HyLens |
| Une seule copie de l'API | `ApiVersion.class` identique vu des deux mods (`/hylens selftest`, `api class`) | `:api` est embarqué dans le seul jar de HyColony, HyLens le voit par le pont (§ 28.2 ; `checkBundled`) |
| Arrêt de HyLens | Son abonnement se ferme, la pause est levée, son filtre de paquet est retiré, ses spectateurs sortent du mode spectateur ; `/plugin unload` le retire aussi du démarrage (`HytaleServerConfig.setBoot`), `/plugin load` l'y remet | Registres de HyLens défaits à l'arrêt ; `track`, `pause` liés au propriétaire (spec § 4.1) ; `MapSend.stop` appelle `PacketAdapters.deregisterInbound`, statique, donc jamais défait par Hytale |
| `/plugin reload` de HyColony | HyLens est déchargé d'abord et ne revient pas ; le second `setup()` de HyColony **échoue** (SEVERE, HyColony en FAILED) | `PluginManager.unload`/`reload` (`hycolony-api.md` § 3) ; piège 1.9 de `pieges-portage.md` (audit D-1, non corrigé) : l'attendu « aucune exception » de la spec § 8, essai 6, ne tiendra qu'une fois D-1 corrigé |
| Monde désactivé | HyLens dit « indisponible » | `HyColonyApi.world` rend vide (spec § 4.1) ; la désactivation est globale (`WorldRuntimes.enableIfIdsValid`) : un id invalide dans `hycolony/id-map.json` désactive tous les mondes |

Pour la copie unique de l'API, `/hylens selftest` (ligne `api class`) charge `dev.hycolony.api.ApiVersion` par le chargeur de la classe qui implémente `HyColonyApi`, celui de HyColony, et la compare à la sienne : la même classe, c'est le même `identityHashCode`. Une classe différente voudrait dire que `:api` est chargé deux fois.

## 29. Voir un bloc à travers les murs (recherche du 2026-09-28)

But : un équivalent de l'effet « Glowing » de Minecraft pour l'épouvantail d'un champ. Chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`.

**Aucun drapeau de profondeur n'existe côté serveur.** Aucune occurrence de `XRay`, `SeeThrough`, `RenderOnTop`, `IgnoreDepth`, `AlwaysOnTop`, `DepthTest` ni `Silhouette` dans les sources décompilées. Le rendu (tests de profondeur) est entièrement côté client ; les données du client (`%APPDATA%/Hytale/install/release/package/game/latest/Client/Data`) ne contiennent ni shader ni schéma lisible (`Game/Schema` n'a que `PlayerSkin.json`, `Game/ShaderTextures` que des PNG). Tout ce qui suit est donc **[in-game]** pour la question « visible à travers un mur ».

**`DisplayDebug`.** `protocol/DebugFlags.java` : `None` 0, `Fade` 1, `NoWireframe` 2, `NoSolid` 4, `ALL` 7. Aucun autre bit. Le paquet (`protocol/packets/player/DisplayDebug.java`) n'a que `shape`, `matrix`, `color`, `time`, `flags`, `frustumProjection`, `opacity` : aucune option de profondeur. Vérifié en jeu : caché par les blocs.

**ModelVFX sur une entité (piste la plus proche du Glowing).**
- Asset `Server/Entity/ModelVFX/*.json` (`server/core/asset/type/modelvfx/config/ModelVFX.java:40-84`) : `SwitchTo` (`Disappear`, `PostColor`, `Distortion`, `Transparency`, `protocol/SwitchTo.java`), `EffectDirection`, `AnimationDuration`, `AnimationRange`, `LoopOption`, `CurveType`, `HighlightColor`, `HighlightThickness`, `UseBloomOnHighlight`, `UseProgessiveHighlight` (sic), `NoiseScale`, `NoiseScrollSpeed`, `PostColor`, `PostColorOpacity`. Le paquet `protocol/ModelVFX.java` porte exactement ces champs : pas de drapeau de profondeur.
- Un `EntityEffect` le référence par `ApplicationEffects.ModelVFXId` (`server/core/asset/type/entityeffect/config/ApplicationEffects.java:113`). Exemple vanilla le plus « lumineux » : `Server/Entity/Effects/Drop/Drop_Legendary.json` (`Infinite`, `ModelVFXId: Drop_Legendary`, particules `Drop_Legendary`) avec `Server/Entity/ModelVFX/Drop_Legendary.json` (`SwitchTo: PostColor`, `HighlightColor: #ffdb91`, `HighlightThickness: 5.0`, `UseBloomOnHighlight: true`, `LoopOption: Loop`). Autres effets à ModelVFX : `Drop_Epic/Rare/Uncommon`, `GameMode/Creative`, `Spectator`, `Carry_Dropped_Block` (`SwitchTo: Transparency`), `Status/Burn`, `Status/Freeze`…
- Application : `EffectControllerComponent.addEffect(ref, entityEffect, componentAccessor)` (`server/core/entity/effect/EffectControllerComponent.java:151`, variante avec durée et `OverlapBehavior` l. 167). Il faut que l'entité ait un `EffectControllerComponent` ; `LivingEntityEffectSystem.canApplyEffect` (l. 180) ne vérifie que les `ApplyConditions` de l'effet. L'effet est un état de l'entité : tous les joueurs qui la voient le voient (aucun masquage par joueur trouvé pour une entité non joueur ; `HiddenPlayersManager` ne concerne que les joueurs).
- **Entité-bloc comme support.** `BlockEntity.assembleDefaultBlockEntity(time, blockTypeKey, position)` (`server/core/entity/entities/BlockEntity.java:75`) crée une entité qui affiche un bloc (avec `DespawnComponent` 120 s, `TransformComponent`, `Velocity`, `UUIDComponent`). Précédents vanilla :
  - `CarriedBlockSystems.ApplyDroppedBlockEntityEffect` (`server/core/modules/interaction/components/CarriedBlockSystems.java:64-86`) applique déjà un EntityEffect à ModelVFX (`Carry_Dropped_Block`) à une `BlockEntity`, qui reçoit `EffectControllerComponent` par `holder.ensureComponent` (`CarriedBlock.java:207`) ;
  - l'ancre de l'éditeur de prefabs (`builtin/buildertools/prefabeditor/PrefabEditingMetadata.java:123-133`) : `BlockEntity` `Editor_Anchor` au centre du bloc (+0.5), `removeComponent(DespawnComponent)`, `Intangible.INSTANCE`, `EntityScaleComponent(1.05F)`, puis `store.addEntity(holder, AddReason.SPAWN)` via `world.execute`.
  - La physique ne tourne que si l'entité a `Velocity` (`BlockEntitySystems.Ticking`, archétype `Transform + BlockEntity + Velocity`, `server/core/modules/entity/BlockEntitySystems.java:229-238`) ; `initPhysics` active `setMoveOutOfSolid(true)`, donc une entité-bloc placée dans l'épouvantail serait poussée hors du bloc si elle garde `Velocity`. Retirer `Velocity` devrait la figer (**[in-game]**).
  - Recette candidate : `assembleDefaultBlockEntity(time, "<bloc épouvantail>", centre)`, retirer `DespawnComponent` et `Velocity`, ajouter `Intangible`, `EntityScaleComponent(1.05F)`, `ensureComponent(EffectControllerComponent)`, puis `addEffect` d'un EntityEffect HyColony à ModelVFX (`HighlightThickness`, `UseBloomOnHighlight`). Le surlignage suit la silhouette du modèle ; **[in-game]** : s'il traverse les murs (rien dans le code ne l'indique), et le rendu d'une entité-bloc agrandie qui chevauche le vrai bloc.
- Test sans code : `/entity effect <effet> [durée]` (`server/core/command/commands/world/entity/EntityEffectCommand.java`, cible l'entité regardée, durée par défaut 100 s) sur un PNJ avec `Drop_Legendary`, puis le regarder derrière un mur. `/spawnblock <bloc> <position>` (`SpawnBlockCommand.java`) crée une entité-bloc **sans** `EffectControllerComponent`, donc `/entity effect` n'a pas d'effet sur elle.
- **Vérifié en jeu (2026-09-28)** : la lueur `Drop_Legendary` sur un PNJ ne se voit **pas** à travers les murs. Le ModelVFX est donc exclu pour une surbrillance à travers les blocs.
- HyColony s'en sert pour « Localiser » (`plugin/ui/highlight/GlowingBlock`) : une entité-bloc du même bloc, `Velocity` retirée, `Intangible`, `EntityScaleComponent(1.05)`, `EffectControllerComponent`, un `DespawnComponent` d'une minute et l'effet de l'id-map `highlightEffect` ; plus propre que les formes de débogage, même si elle ne traverse pas les murs. **[in-game]** : l'entité reste-t-elle immobile dans le bloc réel ?

**Particules.** `ParticleSystem.IsImportant` (`server/core/asset/type/particle/config/ParticleSystem.java:72-78`) : « still renders when it is outside the player's field of vision, beyond the `CullDistance` or occluded » ; cela désactive l'élimination du système entier quand il est caché, pas forcément le test de profondeur de chaque particule (**[in-game]**). Aucun système vanilla ne l'utilise. `ParticleSpawner.CameraOffset` (l. 289-296, borne −10..10) rapproche la particule de la caméra (valeur négative) « to fix z-fighting » : un décalage de −10 pourrait faire passer la particule devant un mur proche, bricolage **[in-game]**. `IntersectionHighlight` (l. 265-270) ne fait que recolorer la partie d'une particule qui touche un bloc. `RenderMode` (`FXRenderMode` : `BlendLinear`, `BlendAdd`, `Erosion`, `Distortion`) : pas d'option de profondeur.

**Outils du constructeur (paquets vers le client).**
- `BuilderToolShowAnchor` (id 415 : `x`, `y`, `z`) et `BuilderToolHideAnchors` (id 416) : envoyés par `PrefabEditingMetadata.java:159` avec `writeNoCache`. Rendu hors mode créatif et à travers les murs : **[in-game]**.
- `BuilderToolLaserPointer` (id 419 : `playerNetworkId`, `start*`, `end*`, `color`, `durationMs`) : un rayon d'un joueur vers un point (`builtin/buildertools/tooloperations/LaserPointerOperation.java:63-73`, diffusé à tous). Pourrait tracer un trait de l'œil du joueur vers l'épouvantail ; le rayon partirait du joueur, donc visible même si sa cible est cachée **[in-game]**.
- `BuilderToolSelectionUpdate` (id 409) est un paquet client → serveur ; `EditorSelection` n'est envoyé que dans `EditorBlocksChange` (id 222, aperçu de presse-papiers). Non retenus.
- `InteractionConfiguration.DisplayOutlines`/`DebugOutlines` (`server/core/modules/interaction/interaction/config/InteractionConfiguration.java:27-34`) : contour de la cible visée, pas à travers les murs.

**Nameplate.** `new Nameplate(text)` (`server/core/entity/nameplate/Nameplate.java:59`) sur une entité, comme les marqueurs d'objectifs (`builtin/adventure/objectives/commands/ObjectiveLocationMarkerCommand.java:77`). Visible par tous ; à travers les murs **[in-game]**.

**Boussole.** Les `MarkerProvider` ne tournent que si le monde a `IsCompassUpdating` (`WorldConfig.java:193`, « Whether the compass is updating in this world ») : `WorldMapTracker.java:194` appelle `markerTracker.updatePointsOfInterest` seulement si `world.isCompassUpdating()`, qui interroge tous les fournisseurs (`MapMarkerTracker.java:69-100`) et envoie `UpdateWorldMap`. Les objectifs passent par exactement le même chemin (`builtin/adventure/objectives/markers/ObjectiveMarkerProvider.java`, `collector.add(marker.toProto())`, marqueur sans composant). Les marqueurs n'ont aucun champ « boussole » (`protocol/packets/worldmap/MapMarker.java` : `id`, `name`, `markerImage`, `transform`, `contextMenuItems`, `components`) ; le choix d'afficher sur la boussole est côté client. Des mods publiés affirment qu'un `MarkerProvider` s'affiche sur la boussole (Pathfinder : « custom marker provider that ensures markers are visible on your compass from far away », https://www.curseforge.com/hytale/mods/pathfinder). Le client a des textes `hud.compass.*`, `settings.hideCompass`, `map.playerListEntry.tooltip.compass = Track on Compass` (`Client/Data/Shared/Language/en-US/client.lang`). Conclusion : très probable, **[in-game]** pour HyColony ; `addIgnoreViewDistance` évite la coupure par la distance de vue.

## 30. Update 7 (0.7.0-pre.4) : ce qui change dans ce mémo

Recherche du 2026-09-29, détail et preuves dans `docs/research/update-7/a-java-api.md`. Les § 1 à 29 restent justes pour la 0.6.8 épinglée ; en U7 (chemins relatifs aux sources U7 décompilées) :

- **§ 19, fissures** : `BlockHealthChunk` n'est plus que décodé (`@Deprecated(forRemoval = true)`), et `getBlockHealthChunkComponentType()` comme `damageBlock(...)` disparaissent. La santé vit sur la **section** : `BlockHealthSection.getComponentType()`, `getHealth(x, y, z)`, `damage(x, y, z, amount, Instant now)`, puis `ChunkSection.markNeedsSaving()` (`server/core/modules/blockhealth/BlockHealthSection.java:51, 59, 88`). La réplication se fait en fin de tick, vers les joueurs qui ont la section chargée (`BlockHealthSystems.java:271-345`). `BlockOperations.setBlock` efface la santé du bloc remplacé (`BlockOperations.java:80`).
- **§ 13, point d'apparition** : `ISpawnProvider.getSpawnPoint` est retiré. Il faut `getSpawnPointAsync(World, UUID)`, qui rend un `CompletableFuture<Transform>` (`universe/world/spawn/ISpawnProvider.java:28`). Sur le thread du monde, lire par `getNow(null)`, jamais `join()` : `FitToHeightMapSpawnProvider` (le fournisseur par défaut d'un monde généré, `worldgen/IWorldGen.java:26`) peut finir sur ce même thread.
- **§ 18, environnement d'un bloc** : `BlockChunk.getEnvironment(int, int, int)` porte `@RestrictedApi` (réservé à BuilderTools), donc Error Prone échoue. Il faut `EnvironmentSection.get(x, y, z)` sur la référence de `getChunkSectionReferenceAtBlock` (`universe/world/chunk/section/EnvironmentSection.java:63, 88`). `WeatherResource.getEffectiveWeatherIndex(env)` donne la météo forcée, sinon celle de l'environnement (`builtin/weather/resources/WeatherResource.java:43`).
- **Manifestes** : `">=0.6.8 <0.7.0"` ne couvre pas `0.7.0-pre.4`, à cause de la règle des pré-versions de `SemverRange` (`common/semver/SemverRange.java:44-62`). Le serveur avertit et continue. La forme qui convient est `">=0.7.0-pre.4 <0.8.0"`.
- Sans changement pour nous : fenêtres et pages (`CustomUIPage`, `InteractiveCustomUIPage`, `PageManager`, `WindowManager`, `UICommandBuilder`, `UIEventBuilder`), `ItemContainer`, `BlockSection` et `BlockOperations.setBlock` (même signature), commandes, codecs, événements ECS. Les systèmes ne voient que les entités racines par défaut (`QuerySystem.getHierarchyScope()` = `ROOT`), et aucune entité n'a de parent, ni chez nous ni dans le vanilla.

## 31. Jour et nuit (0.7.0-pre.4, vérifié le 2026-09-29)

- **Heures fixes de l'horloge de jeu** : le jour occupe 60 % des 24 heures de jeu (`WorldTimeResource.DAYTIME_PORTION_PERCENTAGE = 0.6F`, `server/core/modules/time/WorldTimeResource.java:45-48`).
  - `NIGHTTIME_SECONDS` = 34 560 s de jeu.
  - `SUNRISE_SECONDS = NIGHTTIME_SECONDS / 2` = 17 280 s, soit **4 h 48**.
  - Coucher = `SUNRISE_SECONDS + DAYTIME_SECONDS` = 69 120 s, soit **19 h 12**.
- **Durée réelle** : 1 728 s réelles de jour et 1 152 s de nuit par défaut (`Server/GameplayConfigs/Default.json:89-90` de l'archive 0.7.0-pre.4 ; le défaut Java de `asset/type/gameplay/WorldConfig.java:119-120` est de 1 151 s), soit 48 minutes par jour. Chaque monde peut les changer (`WorldConfig.DaytimeDurationSeconds`, `universe/world/World.java:586-588`). L'horloge de jeu avance à un rythme différent le jour et la nuit, pour que les heures de lever et de coucher restent fixes (`WorldTimeResource.java:119-136`).
- **Test prêt à l'emploi** : `isScaledDayTimeWithinRange(min, max)` (l. 603) compare `scaledTime`, qui vaut 0,25 au lever et 0,75 au coucher (`updateScaledTime`, l. 276-285). Le jour est donc `isScaledDayTimeWithinRange(0.25, 0.75)`.
- **Lumière** : `sunlightFactor = clamp(sin(2π × (t − demi-nuit) / jour) + 0.2, 0, 1)` (l. 271-273). Il fait donc déjà un peu clair avant le lever.
- **HyColony** : `HytaleGameClock.isDaytime` utilise `isScaledDayTimeWithinRange(0.25, 0.75)`, bornes incluses (`MathUtil.within`). C'est corrigé le 2026-09-29 : avant, il faisait jour de 6 h à 20 h, 1 h 12 après le vrai lever et jusqu'à 48 minutes après le vrai coucher.

## 32. Prefabs MineColonies : cases vides, bon sol, liste des blocs (2026-09-29)

Sources U7 (0.7.0-pre.4). Contexte : `docs/research/structurize-placeholders.md`.

- **`Empty` explicite dans un prefab** : `BsonPrefabBufferDeserializer` crée une entrée pour chaque bloc du tableau `blocks`, `Empty` compris (`builder.newBlockEntry(y)`), et `PrefabBuffer.forEach` les rend toutes, avec `blockId == 0` (`BlockType.EMPTY_ID`), sans filtre (`prefab/selection/buffer/impl/PrefabBuffer.java:670-720`). Une case absente du fichier n'est jamais visitée. Une entrée `fluids` sans bloc crée elle aussi une entrée (`blockId` 0, `fluidId` du fluide).
- **Forme d'un bloc** : `BlockType.getDrawType()` rend `Empty`, `GizmoCube`, `Cube`, `Model` ou `CubeWithModel` (`protocol/DrawType.java`), et `getMaterial()` rend `Empty` ou `Solid`. Assets : les feuilles sont `DrawType: Model`, `Group: Leaves` (`Server/Item/Items/Plant/Leaves/*.json`). Les minerais sont `CubeWithModel`. `Soil_Dirt_Tilled` hérite de `Template_Soil`. L'état `Full` d'une demi-dalle est un `Cube` alors que sa base est un `Model` : il faut juger l'état par son propre `BlockType`, qui est un asset à part entière (`*X_State_Definitions_Y`).
- **Tous les blocs** : `BlockType.getAssetMap().getAssetMap()` est une `Map<String, BlockType>`, comme l'utilisent `TriggerVolumesPlugin` et `ArgTypes`.
- **Fluide d'eau** : l'asset fluide `Water_Source` existe (`Server/Item/Block/Fluids/Water_Source.json`), ainsi que `Water` et `Water_Finite`.
- **Blocs éditeur** : `Editor_Empty` est un cube transparent, `Quality: Technical`, `Categories: ["Tool.PrefabEditing"]`, `SubCategory: "PrefabBlocks"`, `Group: "@Tech"`, sans `Material` (`Server/Item/Items/Editor/Editor_Empty.json`). Nos deux blocs de substitution reprennent ce modèle. Leur rendu et leur place dans le menu créatif sont **[in-game]**.

## 33. Faces d'appui d'un bloc (`Supporting`) : un bloc `Model` n'en a aucune (2026-09-29)

- `BlockType.processConfig` (`server/core/asset/type/blocktype/config/BlockType.java:1969-1975`) : sans clé `Supporting`, un bloc reçoit les six faces pleines (`ALL_SUPPORTING_FACES`) **seulement** s'il est `DrawType` `Cube`, `CubeWithModel` ou `GizmoCube` **et** `Material: Solid`. Tout autre bloc, dont un `Model` plein, reçoit une map vide : rien ne tient dessus ni contre lui (torche, lanterne, torche murale). Symptôme vu en jeu sur les colombages HyDomum.
- Format JSON (`MergedEnumMapCodec`, noms en CamelCase) : `"Supporting": {"Up": [{}], "Down": [{}], "North": [{}], "South": [{}], "East": [{}], "West": [{}]}`. Une entrée `{}` vaut `FaceType: "Full"` (valeur par défaut de `BlockFaceSupport`), sans `Filler`.
- La rotation garde le `FaceType` (`getSupporting(rotationIndex)`) ; un `BlockType` copié (`super(template)`, variantes HyDomum) recopie `supporting`. Les définitions d'état héritent du `Supporting` du bloc : un état plus étroit doit déclarer le sien. Modèle vanilla : `Rock_Stone_Brick_Pillar_Middle` ne déclare que `Up` et `Down`.
- À appliquer à tout futur bloc `Model` plein (HyDomum, HyVanilla…).

## 34. Hauteur du monde : une section hors de [0, 320) n'existe jamais (2026-09-30)

- `math/util/ChunkUtil.java` : `MIN_Y = 0`, `HEIGHT = 320`, `HEIGHT_SECTIONS = 10`. Une colonne a dix sections de 32 blocs ; aucune ne couvre y < 0 ni y ≥ 320, même une fois le chunk chargé.
- Conséquence : « la section est-elle chargée ? » ne suffit pas pour savoir si une case est chargée. `HytaleWorldBlocks.isLoaded` répond `true` hors de cette hauteur, pour qu'aucune attente (chantier non chargé, `BuilderAI.structureStep`) ne dure toujours ; la pose y échoue ensuite, et la case est sautée comme avant.

## 35. L'API de HyColony pour d'autres mods : arrêt du propriétaire, fil, pause (2026-09-30)

Tâche 6 du plan `2026-09-30-hycolony-api-hylens.md`, sources de 0.7.0-pre.4.

- **Lier un nettoyage à l'arrêt d'un autre plugin.** `EventRegistry.register(EventRegistration<K, E>)` exige `E extends IBaseEvent<K>` (`event/EventRegistry.java`). `IBaseEvent<K>` est une interface vide (`event/IBaseEvent.java`), donc une classe marqueur jamais publiée suffit. Le constructeur `EventRegistration(Class<E>, BooleanSupplier isEnabled, Runnable unregister)` (`event/EventRegistration.java`) prend le nettoyage en `Runnable`.
- **Quand ce nettoyage tourne.** `Registry.register` ajoute la désinscription aux `shutdownTasks` du plugin, une `CopyOnWriteArrayList` sûre entre fils (`registry/Registry.java`, `server/core/plugin/PluginBase.java`). À l'arrêt, `PluginBase.shutdown0` passe en `SHUTDOWN`, puis `cleanup` coupe les registres et lance chaque tâche dans l'ordre inverse, sur le fil qui arrête le plugin. Une inscription faite après l'arrêt lance le nettoyage, puis lève `IllegalStateException("Registry is not enabled!")`. `PluginBase.state` n'est pas `volatile` : un autre fil ne peut pas le lire sans risque.
- **Identifier une instance de plugin.** `PluginBase.getIdentifier()` rend un `PluginIdentifier`. HyColony y ajoute l'`identityHashCode` de l'instance : un plugin rechargé est un autre propriétaire (`bridge/OwnerBinding.key`).
- **Fil du monde.** `World.isInThread()` existe (hérité de `server/core/util/thread/TickingThread.java` ; avant 0.7.0-pre.5, aussi via `IWorldChunks`). `ComponentAccessor.getExternalData()` rend l'`EntityStore`, dont `getWorld()` donne le monde d'une entité (`component/ComponentAccessor.java`, `.../storage/EntityStore.java:177`). `Ref.isValid()` (`component/Ref.java:125`).
- **Arrêter un citoyen sans `Frozen`.** Notre `MoveTarget` (`plugin/npc`) pilote le `Seek` du rôle. `mt.active = false` arrête la marche, comme le fait déjà `lookAt`, et `navStatus` rend alors IDLE. Une marche simple repart au tick suivant, puisque le statut n'est plus MOVING ; une marche vers un bloc compte IDLE comme une navigation finie, et s'arrête là si elle est déjà à portée (`BodyWalker.navDone`). Pendant le pas à pas, les pas tournent au rythme du temps et les corps ne sont arrêtés qu'une fois les pas épuisés : arrêtés à chaque pas, ils ne bougeraient jamais, et l'anti-blocage finirait par les téléporter. `Frozen` n'est pas employé, car il est sauvegardé avec le PNJ. Effet en jeu : **[in-game]**.
- **Autosauvegarde en pause.** L'horloge du cœur (`HytaleGameClock`) n'avance qu'avec les ticks du cœur. `WorldRuntime.runCore` compte donc les ticks dus à part pour sauvegarder, en pause ou non.

## 36. HyLens : suivre une entité en spectateur (2026-09-30)

Tâche 9 du plan `2026-09-30-hycolony-api-hylens.md`, sources de 0.7.0-pre.4.

- **Permission par défaut d'une commande.** Sans `requirePermission` ni `requireNoPermission`, `setOwner` génère un nœud (`command/system/AbstractCommand.java:138-139`). Sans groupe, `putRecursivePermissionGroups` ne le donne à aucun groupe (l. 237-253), et `hasPermission` d'une sous-commande sans groupe vérifie aussi son parent (l. 804-816). Seul `*`, tenu par `hytale:Admin`, y a donc droit : une commande sans groupe est réservée aux opérateurs.
- **Suivre une entité** comme `/spectate target` (`command/commands/player/SpectateCommand.java:86-108, 226-255`) : `GameModeTypes.enter(ref, store, "Spectator")` si le joueur ne regarde pas déjà (`Spectating.isSpectating`), puis `store.putComponent(ref, Spectating.getComponentType(), new Spectating(cible))`. `GameModeTypes.enter` lève sur un identifiant inconnu (`resolveOrThrow`) : `GameModeTypes.isValidType` le vérifie avant (`modules/entity/gamemode/GameModeTypes.java:53-55, 168-185`). L'entité visée : `TargetUtil.getTargetEntity(ref, rayon, includeItemDrops, accessor)` (`util/TargetUtil.java:651`), rayon de 32 comme `/spectate target`.
- **Cible perdue.** `SpectatorSystems.FollowTarget` passe en caméra libre, avec le message `server.commands.spectate.targetLost`, quand la cible n'est plus valide, change de store, meurt ou n'a plus de `TransformComponent` (`modules/entity/spectator/SpectatorSystems.java:96-133`). Un corps de citoyen refait ou déchargé coupe donc la caméra, sans arrêter le suivi de HyLens.
- **Le mode est sauvegardé.** `GameModeTypes.exit` retire le composant `PersistentGameModeType` (l. 271-300) : sans sortie, un joueur reconnecté serait encore spectateur. HyLens le fait sortir à sa déconnexion. `Universe.removePlayer` publie `PlayerDisconnectEvent` sur le fil de l'appelant, **avant** de mettre le retrait du joueur dans la file de son monde (`universe/Universe.java:1676-1700`)  : hors du fil du monde, un `world.execute` lancé depuis l'écouteur passe avant ce retrait **[in-game]**.
- **Arguments.** Un argument optionnel s'écrit `--nom=valeur`. Une variante d'usage (`addUsageVariant`) est choisie par son nombre **exact** de mots (`variantCommands.get(n)`, `command/system/AbstractCommand.java:835-836`), et le découpage se fait aux espaces : une variante qui prend un nom de plusieurs mots n'est jamais atteinte. Un argument requis `ArgTypes.GREEDY_STRING` (`arguments/types/ArgTypes.java:127`) rend sa commande `allowsExtraArguments` (l. 1179-1181) : elle accepte un mot ou plus (l. 752-764), et l'argument reçoit le reste de la ligne brut, sans le nom des commandes (`extractGreedyRawTail`, l. 880-909), guillemets compris. Pour « sans argument, ou un nom », HyLens met donc le nom sur la commande, et la forme sans argument dans une variante de zéro mot.
- **Déconnexion sur le fil du monde.** `Universe.removePlayer` peut être appelé depuis le fil du monde (une expulsion dans un `world.execute`, `InventoryPacketHandler.java:447-452`) : il retire alors le joueur tout de suite (`Universe.java:1687-1693`). Un écouteur de `PlayerDisconnectEvent` qui veut encore toucher au joueur le fait donc sur place quand `world.isInThread()`, sinon par `world.execute`. À l'arrêt normal du serveur, `ShutdownEvent` déconnecte tout le monde avant l'arrêt des plugins (`HytaleServer.shutdown0`, l. 517-518), écouteurs encore inscrits.

## 37. HyLens : un HUD personnalisé rafraîchi (2026-09-30)

Tâche 10 du plan `2026-09-30-hycolony-api-hylens.md`, sources de 0.7.0-pre.4.

- **Afficher, remplacer, retirer.** `Player.getHudManager()` (`server/core/entity/entities/Player.java:469`). `HudManager.addCustomHud(playerRef, hud)` garde le HUD sous sa clé et appelle `show()`, qui construit le document puis envoie `update(true, …)` ; un autre HUD sous la même clé est d'abord retiré. `getCustomHud(key)` le rend, `removeCustomHud(playerRef, key)` l'efface chez le client (`entity/entities/player/hud/HudManager.java:61, 129-162`).
- **Mettre à jour.** `CustomUIHud.update(clear, builder)` envoie un paquet `CustomHud(key, zOrder, clear, commandes)` sans cache (`hud/CustomUIHud.java`). Avec `clear = false`, les commandes s'appliquent au document déjà affiché : `clear("#Lines")`, puis un `append("#Lines", "…ui")` par ligne et `set("#Lines[i] #Text.TextSpans", message)`, comme nos pages (`ui/builder/UICommandBuilder.java`).
- **Clé.** Le mode spectateur affiche son propre HUD sous la clé `Spectating` (`modules/entity/spectator/SpectatingHud.java`) : HyLens prend `HyLensWatch`.
- **Cadence.** Un `TickingSystem<EntityStore>` reçoit le `dt` de chaque monde, sur son fil (`component/system/tick/TickingSystem`), comme `ColonyTickSystem`. Le coût d'un rafraîchissement toutes les 0,5 s est **[in-game]**.

## 38. HyLens : dessiner pour un seul joueur (2026-09-30)

Tâche 11 du plan `2026-09-30-hycolony-api-hylens.md`, sources de 0.7.0-pre.4.

- **Le paquet.** `DisplayDebug(forme, matrice float[16], couleur, durée en secondes, drapeaux, paramètres, opacité)` (`protocol/packets/player/DisplayDebug.java:52-68`). Formes : `Sphere`, `Cylinder`, `Cone`, `Cube`, `Frustum`, `Sector`, `Disc`, `Donut` (`protocol/DebugShape.java`) ; pas de ligne (`DebugUtils.addLine` en construit une avec un cylindre, mais l'envoie à tout le monde). Drapeaux : `FLAG_FADE` 1, `FLAG_NO_WIREFRAME` 2, `FLAG_NO_SOLID` 4 (`server/core/modules/debug/DebugUtils.java:53-56`).
- **Un seul joueur.** `DebugUtils.add` écrit le paquet à tous les joueurs du monde (l. 108-112). Pour l'opérateur seul, on écrit le paquet sur son `PlayerRef.getPacketHandler()`, comme `WildernessDebugShapeSystem` (`builtin/adventure/wilderness/debug/WildernessDebugShapeSystem.java`).
- **Les unités.** Le cube unité est centré sur l'origine, de côté 1 (la zone sauvage se dessine au centre du chunk, échelle 32). Le cylindre unité est centré et suit l'axe Y local : la tige de `DebugUtils.addArrow` est placée à mi-longueur puis étirée en Y (l. 195-209). Une ligne de A à B est donc un cylindre au milieu de A et B, tourné de Y vers B − A (`Quaterniond.rotationTo`), d'échelle (épaisseur, longueur, épaisseur).
- **Le chemin de la navigation.** `/npc debug set VisPath` (`server/npc/commands/NPCDebugCommand.java`, drapeau `RoleDebugFlags.VisPath`, lu par `BodyMotionFindBase`) dessine le chemin A\* d'un PNJ ; rendu et cible visée **[in-game]**.

## 39. Monter et descendre : le rôle du citoyen (2026-09-30)

- **Ce que le contrôleur `Walk` permet.** `MaxClimbHeight` (défaut 1,3), `JumpHeight` (défaut 0,5), `MaxDropHeight` (défaut 3,0, plus 3 si la contrainte est relâchée) (`server/npc/movement/controllers/builders/BuilderMotionControllerWalk.java:162-181, 342-351`). `MotionControllerWalk.tryClimb` (l. 2676-2716) cherche une marche jusqu'à `MaxClimbHeight + JumpHeight`, puis **annule toute montée au-dessus de `MaxClimbHeight`** (l. 2694-2695) : `JumpHeight` n'est que l'arc du saut au-dessus de la marche. La sonde de l'A\* (`probeMove`, via `computeClimbHeight(…, maxClimbHeight, …)`, l. 2288) applique la même limite : la montée maximale d'un PNJ est `MaxClimbHeight`, pour marcher comme pour planifier.
- **`MaxDropHeight` arrête aussi le mouvement**, pas seulement la planification : `isDropBlocked` stoppe le PNJ au bord d'un vide plus profond (l. 1800-1809, 1928, `validateGroundPosition` l. 2496-2501), et la sonde de l'A\* l'applique (l. 2162, 2192, 2328). Le « +3 relâché » ne vaut pas pour `Seek` (`BodyMotionFindBase.java:95-103`, sans contrainte relâchée).
- **Le piège vu en jeu (2026-09-30).** Avec les défauts, un citoyen tombé dans un trou de 2 blocs n'en sortait plus, et la navigation cherchait des chemins de plus en plus bas ; la téléportation de l'anti-blocage ne l'empêchait pas d'y retomber.
- **Réglage.** `HyColony_Citizen.json` : `MaxClimbHeight 2` (une marche de 2,0 passe : tolérance 1e-5 au mouvement, 2e-5 à la sonde), puis `MaxClimbHeight 3` le 2026-10-01, comme un joueur de Hytale qui escalade un rebord de 3 blocs. Le rôle de test `Test_Ascent_Animation` monte aussi 2 (sans saut). La descente garde le défaut de 3 : la limiter empêcherait un citoyen posé sur un toit ou au bord d'un rebord de 3 blocs de descendre. Avec une montée de 3, un citoyen sort d'un trou de 3 blocs ; un trou de 4 reste une descente sans retour, dont seul l'anti-blocage le sort, en le téléportant vers sa cible. Vu en jeu le 2026-10-01 : un joueur de Hytale saute une marche de 1 ou 2 blocs et n'escalade (`builtin/mantling/MantlingPlugin`) qu'un rebord de 3. Effet de bord : Hytale range une descente en « chute » (DROPPING) quand elle dépasse `MaxClimbHeight` (`MotionControllerWalk.initiateDescend` l. 2418, mesurée jusqu'à `MaxDropHeight`) ; avec une montée de 3, une descente de 3 blocs devient une descente contrôlée (DESCENDING, l. 1239-1280), l'animation restant `Fall`. Le PNJ saute toute marche d'au moins `MinJumpHeight` (0,6) quand il a la place au-dessus (`tryClimb` l. 2696-2709, état `jumping`) : c'est le saut vu sur 2 blocs.
- **Animation de la montée (2026-10-01).** Le contrôleur `Walk` d'un PNJ n'a qu'une animation pour toute montée, `AscentAnimationType` (`Walk` par défaut, `Jump`, `Climb` = l'échelle `ClimbUp`, `Fly`, `Idle` ; `MotionControllerWalk.updateAscendingStates` l. 308-353, `BuilderMotionControllerWalk` l. 242-251) ; le rôle de test `Test_Ascent_Animation` monte 2 avec `Climb`. L'escalade de rebord d'un joueur (`MantleUp`, `Mantle_Up.blockyanim`, modèle `Player`) vient de l'état `MovementStates.mantling`, que seul le client d'un joueur pose (`builtin/mantling/MantlingPlugin`, fonction client `Mantling`) et qu'aucun contrôleur de PNJ ne touche (`MotionControllerBase.updateMovementState`, l. 310-360, n'écrit jamais `mantling`). L'état d'un PNJ est envoyé tel quel aux joueurs qui le voient (`MovementStatesSystems.TickingSystem`, copie de `mantling` l. 206). `CitizenMantleSystem` (plugin HyColony) le pose après `npc.systems.MovementStatesSystem` tant que le contrôleur est en `MotionKind.ASCENDING` sur un rebord de 3 blocs (`MaxClimbHeight 3` depuis le 2026-10-01 : en jeu, un PNJ ne montre pas le rebord sur 2 blocs, et un joueur de Hytale saute 2 blocs et n'escalade qu'à partir de 3), jugé une seule fois au premier tick de la montée (blocs solides devant, un et deux au-dessus des pieds, dans le sens où le corps regarde ; mémoire dans `MoveTarget`) : jugée plus tard, le corps monté, un bloc plus haut un peu plus loin passerait pour cette marche. Que le client joue `MantleUp` sur un PNJ reste **[in-game]** (`docs/TESTING.md` point 223).
- **Écart à MC.** MC monte `PathingConstants.MAX_JUMP_HEIGHT = 1.3` (`api/util/constant/PathingConstants.java:44`, `AbstractPathJob.handleTargetNotPassable`) : l'ancien réglage lui était fidèle. MC descend jusqu'à 9 blocs (`AbstractPathJob.checkDrop`, `canDrop`). Conséquence de l'écart : un mur plein de 3 blocs (enclos, champ) devient franchissable par un citoyen ; une clôture reste infranchissable (`isClimbable`, ensemble `Fence`). Consigné dans la spec sp12 (« Déplacements du constructeur »).

## 40. Le chemin d'un PNJ (2026-10-01)

- **Où il vit.** `Seek` est `BodyMotionFind` (`NPCPlugin.java:902`, `registerCoreComponentType("Seek", BuilderBodyMotionFind::new)`), sous-classe de `BodyMotionFindBase`, qui garde le chemin de son A\* dans `protected final PathFollower pathFollower` (`corecomponents/movement/BodyMotionFindBase.java:63`), sans accesseur. `PathFollower.getCurrentWaypoint()` (public) rend un `IWaypoint` (`navigation/IWaypoint.java` : `getPosition()`, `next()`) : la liste chaînée des points restants, le prochain d'abord, à hauteur des pieds.
- **Pas de chemin en marche droite.** Sans obstacle, `BodyMotionFindBase` dirige le PNJ droit sur sa cible sans A\* (`computeSteering`, l. 295-310) : `getCurrentWaypoint()` est alors nul. L'A\* n'est lancé que si la sonde de la marche droite échoue : `shouldSkipSteering` (`BodyMotionFind.java:77-87`) passe par `probeMove`, qui monte les marches ; `SkipSteering` et `UsePathfinder` valent vrai par défaut (`BuilderBodyMotionFindBase.java:177-179`). Sur une colline praticable, le chemin est donc vide : le corps va droit en plan en suivant le sol, d'où la ligne posée sur le sol par HyLens.
- **Le VisPath de Hytale ne convient pas.** Il remplit `DebugSupport.pathVisDataList` (`recordPathVisualization`, l. 838-845) et `RoleSystems` le dessine par `DebugUtils`, qui l'envoie à **tous** les joueurs du monde (`DebugUtils.java:110`) ; changer les drapeaux de débogage retire la plaque de nom (`docs/TESTING.md` point 222).
- **Ce que fait HyColony.** `HyColonySeek` (plugin, `npc/`) étend `BodyMotionFind` et lit `pathFollower` (au plus 64 points) ; son constructeur est celui de Seek, son builder `BuilderBodyMotionFind` dont seul `build` change, enregistré sous `"HyColonySeek"` comme le capteur `HyColonyTarget`. Le rôle du citoyen l'utilise au lieu de `"Seek"`. `HytaleCitizenBodies.path` le trouve par `Role.getLastBodySteeringMotion()` (public, `role/Role.java:1470`). Le cœur y ajoute les étapes de ses propres détours (`DetouringBodies.path`), et l'API de débogage l'expose (`CitizenDebugSnapshot.path`, `@Experimental`).

## 41. Horloge, lits et sommeil d'un PNJ (2026-10-01)

Revérification de `sp4-sleep-home.md` § B (écrit pour 0.6.8) sur les sources de 0.7.0-pre.4, pour la spec `2026-10-01-hycolony-sp4-home-sleep-design.md` § 4 et § 6. Chemins relatifs à `com/hypixel/hytale/`.

**Horloge.**

- **Constantes réelles.** `DAYTIME_SECONDS = (int)(SECONDS_PER_DAY * 0.6F)`, `NIGHTTIME_SECONDS = (int)(SECONDS_PER_DAY * 0.39999998F)`, `SUNRISE_SECONDS = NIGHTTIME_SECONDS / 2` (`server/core/modules/time/WorldTimeResource.java:45-48`). `SECONDS_PER_DAY` n'est pas une constante de compilation (l. 37) : le calcul se fait à l'exécution, en `float`. 86400 × 0,39999998f arrondi en `float` donne 34559,996, d'où **`NIGHTTIME_SECONDS = 34559`**, **`SUNRISE_SECONDS = 17279`** (04:47:59) et `DAYTIME_SECONDS = 51840` (calcul IEEE refait hors de la JVM : le hook du dépôt refuse de charger le jar du serveur ; à confirmer par un `/hycolony selftest` **[in-game]**). Le coucher tombe donc à 69119 s (19:11:59), pas à 04:48 / 19:12 pile comme le disait `sp4-sleep-home.md`.
- **Lire l'heure.** `getGameDateTime()` rend le `LocalDateTime` (l. 310), `getDayProgress()` la fraction `SECOND_OF_DAY / 86400` (l. 651-653), `getCurrentHour()` l'heure entière (l. 639). Le temps « mis à l'échelle » (`scaledTime`, l. 56, calculé l. 276-288 : 0,25 au lever, 0,75 au coucher) est **privé** : on ne le lit que par `isScaledDayTimeWithinRange` (l. 603-607), comme `HytaleGameClock.isDaytime()`. Ses bornes viennent de `float` (51840,004 et 17279,998, l. 264-266) : elles diffèrent d'au plus une seconde de jeu de celles de `tick()`.
- **Durées réelles du monde.** `World.getDaytimeDurationSeconds()` et `World.getNighttimeDurationSeconds()` (`server/core/universe/world/World.java:586-602`) : la surcharge du monde (`worldConfig.get…Override()`), sinon `getGameplayConfig().getWorldConfig()` (codec `DaytimeDurationSeconds` / `NighttimeDurationSeconds`, `server/core/asset/type/gameplay/WorldConfig.java:70-79`, défauts du code 1728 / **1151** l. 119-120). `zip:Server/GameplayConfigs/Default.json` → `World` donne 1728 / 1152, `Sleep.WakeUpHour 4.79`, `AllowedSleepHoursRange [19.5, 4.79]` (inchangé depuis 0.6.8). `WorldTimeResource.getSecondsPerTick(World)` (l. 72-77) ne rend qu'une moyenne sur la journée.
- **Comment l'horloge avance** (`tick`, l. 93-150). Hors pause (`world.getWorldConfig().isGameTimePaused()`, l. 117) et hors interpolation (l. 104-116), elle place l'heure de jeu `s` (secondes du jour, entier, l. 118) sur un cycle réel de `D + N` secondes : `x = (s − SUNRISE) · D / DAYTIME_SECONDS` de jour (`SUNRISE ≤ s < SUNRISE + DAYTIME`), sinon `x = D + floorMod(s − SUNRISE − DAYTIME, 86400) · N / NIGHTTIME_SECONDS` (l. 125-129), avance `x` de `dt` secondes réelles, puis reconvertit (l. 131-137). Chaque phase avance donc à vitesse constante. **Ticks réels avant une heure de jeu cible `s_c`** : `20 · floorMod(x(s_c) − x(s), D + N)`, avec la même fonction `x`. En pause, l'heure n'arrive jamais. Pendant une interpolation (`startDayTimeInterpolation`, l. 397-431, commandes d'administration), la vitesse est autre et l'estimation fausse.
- **20 ticks/s.** Le fil du monde tourne à 30 ticks/s par défaut (`server/core/util/thread/TickingThread.java:17, 30-31` ; `World.java:188`, réglable par `/world tps`), et `dt` est le temps réel écoulé (l. 55-73), multiplié par la dilatation du temps du monde (`World.java:376-379`). `ColonyTickSystem` (plugin) cumule ce `dt` en ticks de cœur de 0,05 s, au plus 10 par passage. `WorldTimeSystems.Ticking` passe **le même** `dt` à `WorldTimeResource.tick` (`server/core/modules/time/WorldTimeSystems.java:55-58`) : ticks de cœur et horloge restent cohérents, même dilatés.
- **Passage de nuit par les joueurs.** `StartSlumberSystem` (toutes les 0,3 s), quand tous les joueurs du monde dorment, vise `WakeUpHour` : `(int) 4.79 = 4` h et `(int)(0,79 · 60) = 47` min, soit **04:47:00**, le jour même ou le lendemain (`builtin/beds/sleep/systems/world/StartSlumberSystem.java:57-98`). Le saut n'est pas immédiat : `WorldSlumber` dure `max(3, ceil(heures / 6))` secondes réelles (l. 100-105), l'horloge continue normalement pendant ce temps, puis `UpdateWorldSlumberSystem` pose `setGameTime(start + progression · (cible − start))` (`UpdateWorldSlumberSystem.java:48-83`). Si un joueur se réveille avant la fin, l'heure saute à une heure **intermédiaire** de la nuit. 04:47:00 tombe 59 s de jeu avant le lever de `tick()` : il reste environ 2 s réelles de nuit (59 · 1152 / 34559).

**Lits.**

- `BlockType.getBeds()` et `getSeats()` existent toujours (`server/core/asset/type/blocktype/config/BlockType.java:1656-1661`, codecs `Seats` et `Beds` l. 379-388, type `RotatedMountPointsArray`). Un bloc qui a des `Seats` est d'abord un siège (`builtin/mounts/BlockMountAPI.java:73-83`).
- `zip:Server/Item/RootInteractions/Block/Block_Bed.json` : `Interactions [{"Type": "Bed"}]`, tag `Type = Bed`. Les lits vanilla ont `Interactions {"Use": "Block_Bed", "Primary": "Check_Can_Break_Respawn"}`, un seul point `Beds`, `BlockEntity.Components.RespawnBlock`. Nouveau en 0.7.0 : `Furniture_Goblin_Bed` (`Offset -0.1, 0.4, 0.7`).
- **HyVanilla.** Les 20 `vanilla/plugin/src/main/resources/Server/Item/Items/HyVanilla/HyVanilla_Bed_*.json` ont tous `Beds` (un point, `Offset -0.1, 0.1, 0.8`, `Yaw 0`), `Use: Block_Bed` et `RespawnBlock` : `getBeds() != null` les reconnaît.
- **Bloc de base.** `BedInteraction` reçoit la position de **base** du lit : `SimpleBlockInteraction` la résout par `BlockOperations.resolveBaseBlockPosition(ChunkStore, BlockPosition)` (`server/core/modules/interaction/interaction/config/client/SimpleBlockInteraction.java:105-107`, `server/core/universe/world/chunk/BlockOperations.java:400`). `mountOnBlock` sur une cellule de remplissage créerait un autre `BlockMountComponent`, avec un point de couchage décalé et une occupation à part.

**Coucher un PNJ.**

- **Signature.** `public static BlockMountAPI.BlockMountResult mountOnBlock(Ref<EntityStore> entity, CommandBuffer<EntityStore> commandBuffer, Vector3i targetBlock, Vector3d interactPos)` (`BlockMountAPI.java:28-112`). Résultat scellé `Mounted(BlockType blockType, MountedComponent component)` ou `DidNotMount {CHUNK_NOT_FOUND, CHUNK_REF_NOT_FOUND, BLOCK_REF_NOT_FOUND, INVALID_BLOCK, ALREADY_MOUNTED, UNKNOWN_BLOCKMOUNT_TYPE, NO_MOUNT_POINT_FOUND}` (l. 114-128). Rien n'y est propre au joueur : elle pose la position et la rotation du `TransformComponent` **tout de suite** (l. 97-103), ajoute `MountedComponent(blockRef, Vector3f(), BlockMountType.Bed)` **par le CommandBuffer** (l. 105-106), et écrit directement dans le `ChunkStore` (entité du bloc, `BlockMountComponent`, l. 62-89). `interactPos` ne sert qu'à choisir le point le plus proche (`findAvailableSeat`, `BlockMountComponent.java:93-110`, qui saute les points pris et oublie les entités invalides, l. 65-68).
- **Obtenir un `CommandBuffer` hors d'un système.** Le constructeur est `protected` (`component/CommandBuffer.java:27`) et `Store.takeCommandBuffer()` est package-private (`component/Store.java:167`). La seule voie publique est `Store.forEachChunk(Query, BiConsumer<ArchetypeChunk, CommandBuffer>)` (l. 1396-1426) : le tampon est appliqué (`consume`) à la fin, après le déverrouillage. Il faut une requête qui trouve au moins un morceau (par exemple le type `CitizenTag`) et n'appeler `mountOnBlock` qu'une fois. Autre voie : un `EntityTickingSystem` sur les citoyens, qui reçoit son `CommandBuffer` et traite une demande posée sur un composant, comme `MoveTarget` ; le résultat n'arrive alors qu'au tick suivant. Dans les deux cas, pas depuis un système en cours de traitement (`Store.assertWriteProcessing`, l. 2299-2303) : depuis le tick de la colonie, c'est permis, comme pour `teleport`.
- **Systèmes des montures** (`builtin/mounts/MountPlugin.java:75-119`). `MountedComponent` et `BlockMountComponent` sont enregistrés **sans codec** (l. 75, 78-80). `RemoveMountedHolder` retire `MountedComponent` au déchargement (`MountSystems.java:630-652`), et `RemoveMounted` libère alors la place (l. 554-581, `handleMountedRemoval` l. 74-96). Les systèmes propres au joueur filtrent sur `PlayerInput` : `HandleMountInput` (l. 145-253, requête l. 169) et `PlayerMount` (l. 431-504, requête l. 449). `TrackerUpdate` envoie `MountedUpdate(0, offset, BlockMount, BlockMount(type, position, rotation, index du bloc))` aux spectateurs de toute entité visible montée (l. 831-931). `TrackerRemove`, à la descente d'un bloc, arrête l'animation du créneau `Movement` (l. 818-820) puis envoie `queueRemove(…, Mounted)`. `RemoveBlockSeat` descend les occupants si le lit est cassé (l. 506-552).
- **Une téléportation fait descendre.** `TeleportMountedEntity` retire `MountedComponent` dès qu'un `Teleport` est ajouté à une entité montée (`MountSystems.java:654-709`, l. 684-688). Une téléportation anti-blocage pendant le sommeil sort donc le citoyen du lit.
- **Effet de bord du réveil.** `WakeUpOnDismountSystem` réagit à **toute** entité qui quitte un lit (requête `MountedComponent` seule, `builtin/beds/sleep/systems/player/WakeUpOnDismountSystem.java:36-61`) et lui pose `PlayerSomnolence.AWAKE`, enregistré sans codec (`builtin/beds/BedsPlugin.java:44, 51`). Ensuite, quand les joueurs passent la nuit, `StartSlumberSystem` met `Slumber` sur **toute** entité qui a `PlayerSomnolence` (l. 73-75), et `UpdateWorldSlumberSystem`, à la fin, lui remet `AWAKE` et retire sa monture de bloc (l. 61-71). Un citoyen qui a déjà dormi une fois est donc descendu du lit à la fin d'un passage de nuit, en pleine nuit si un joueur s'est réveillé tôt. `EnterBedSystem` reste filtré sur `PlayerRef` (`EnterBedSystem.java:44, 86-112`).
- **Descendre.** `store.tryRemoveComponent(ref, MountedComponent.getComponentType())`, comme `DismountCommand.java:31`.
- **Le PNJ continue de « marcher ».** Aucun code `npc` ne lit `MountedComponent`. `SteeringSystem` appelle `MotionController.steer` à chaque tick, même sans pilotage (`server/npc/systems/SteeringSystem.java:141, 164`). `MotionControllerWalk.computeMove` (l. 1045) applique alors gravité et collisions (l. 1248-1267). Un PNJ monté reste donc soumis à la physique ; qu'il glisse hors du point de couchage, côté serveur ou à l'écran, reste **[in-game]**. Le composant `Frozen` arrête les systèmes `SteppableTickingSystem` du PNJ (rôle, pilotage, états de mouvement, vitesse : `server/npc/systems/SteppableTickingSystem.java:52-65`, sous-classes `RoleSystems`, `SteeringSystem`, `MovementStatesSystem`, `ComputeVelocitySystem`…), mais il est **sauvegardé** (codec `"Frozen"`, `server/core/modules/entity/EntityModule.java:436`).
- **`MovementStates.sleeping`** (`protocol/MovementStates.java:39`). Aucun code serveur ne le met à vrai : seul le client d'un joueur l'envoie. Le contrôleur d'un PNJ écrit ses états champ par champ, sans jamais toucher `sleeping`, `sitting` ni `mounting` (`MotionControllerBase.updateMovementState`, l. 310-432) : une valeur posée par l'adaptateur reste. Elle est copiée aux spectateurs (`server/core/entity/movement/MovementStatesSystems.java:206-212`) et bascule la boîte de collision (`ModelSystems.java:534-535`, `Model.java:279`, `SleepingOffset -1` du modèle `Player`).
- **Animations.** `zip:Server/Models/Human/Player.json` a `Sleep`, `Sleep2` (sans `Looping`), `Sit`, `Sit2`, `SitGround` (en boucle) et `MountIdle`. `PlayerTestModel_V` (l'`Appearance` de `HyColony_Citizen.json`) a `Parent: Player` et ne redéfinit que `Crouch`, `CrouchWalk` et `CrouchWalkBackward` : il hérite de `Sleep`. Que le client couche un PNJ sur le seul `MountedUpdate` de type `Bed` reste **[in-game]**.
- **Les PNJ vanilla ne se couchent jamais dans un lit.** `mountOnBlock` n'est appelé que par `BedInteraction` (l. 93) et `SeatingInteraction` (`builtin/mounts/interactions/SeatingInteraction.java:51`), tous deux pour un joueur. `ActionMount` (`builtin/mounts/npc/ActionMount.java`) sert à monter un joueur **sur** un PNJ. Les PNJ qui dorment le font sur place, par leur rôle : `"BodyMotion": {"Type": "Nothing"}` et `PlayAnimation` sur le créneau `Status` (`Laydown` puis `Sleep`, `zip:Server/NPC/Roles/Intelligent/Temple/Temple_Goblin_Scrapper_Sleep_Static.json` ; aussi `Component_Trork_Instruction_Sleep.json`, `Template_Feran_Civilian.json`).

**Notre adaptateur aujourd'hui** (`plugin/.../adapter/HytaleCitizenBodies.java`, fil du monde seulement, l. 49).

- **Arrêter la navigation** : `MoveTarget.active = false` sur le composant du corps (`haltAll` l. 107-116, `lookAt` l. 318-321, `teleport` l. 373-376), que lit le capteur `HyColonyTarget` (`npc/SensorHyColonyTarget.java:30`). Aucun appel à Hytale.
- **Téléporter** : différé par `world.execute` (l. 350), `MotionController.translateToAccessiblePosition` à ±10 blocs puis `isValidPosition` (l. 362-369), puis `Store.addComponent(ref, Teleport, Teleport.createExact(to, rotation))` (l. 377). Le `Store` directement, jamais un `CommandBuffer`.

**Ce que fait HyColony (SP4, 2026-10-01).** `npc/CitizenBeds` appelle `mountOnBlock` dans `Store.forEachChunk(BiPredicate)` (`component/Store.java:1370-1393`), la variante sans requête qui s'arrête dès que le prédicat renvoie vrai : un seul appel, sur le premier morceau, et le tampon est appliqué à la fin. Au lever, il retire `MountedComponent` (`Store.tryRemoveComponent`, l. 1274), puis, différé par `world.execute`, le `PlayerSomnolence` que `WakeUpOnDismountSystem` met par le `CommandBuffer` (`WakeUpOnDismountSystem.java:56-61`). La particule `Server/Particles/NPC/Emotions/Sleepy.particlesystem` existe bien dans les assets de 0.7.0-pre.4 (id `Sleepy`, id-map `sleepParticle`). **[in-game]** : pose couchée, glissement, poussée, particule.

**Qui prend un lit occupé** (`builtin/beds/interactions/BedInteraction.java:51-130`).

- Seule une entité `Player` passe. Seul le propriétaire du `RespawnBlock` appelle `mountOnBlock` (l. 85-99). Si un citoyen occupe l'unique point, il reçoit `server.interactions.didNotMount` avec `NO_MOUNT_POINT_FOUND` (l. 94-95). Un non-propriétaire reçoit `respawnPointClaimed` ou la page de point de réapparition. Personne ne déloge un citoyen.
- `mountOnBlock` ne regarde pas le `RespawnBlock` : un citoyen peut prendre le lit d'un joueur, et le joueur ne peut plus s'y coucher. Un citoyen qui vise un lit déjà pris par un joueur ou un autre citoyen reçoit `NO_MOUNT_POINT_FOUND`.

## 42. Cacher un objet de la bibliothèque créative (2026-10-01)

- **Champ `Variant`** d'un objet (`server/core/asset/type/item/config/Item.java:386-389`, champ `protected boolean variant` l. 566) : « If this item is marked as a variant, then we filter it out of the item library menu by default, unless the player chooses to display variants. » 125 objets vanilla l'ont, 22 autres seulement sur certains de leurs `State` (`Rock_Stone_Cobble_Corner`, `Rock_Stone_Brick_Roof_Hollow`, `Deco_Lantern_Ceiling`…, `zip:Server/Item/Items/`). `toPacket` l'envoie (`packet.variant`, l. 787).
- **Côté serveur, il ne change rien.** `Item.isVariant()` (l. 1069) n'a aucun appelant : pose, casse, objets lâchés, artisanat et commandes `give` ignorent le champ. Le filtre est fait par le client.
- **Sans catégorie, l'objet reste trouvable.** `Categories` vide le sort des onglets, mais la recherche de la bibliothèque le trouve encore (constaté en jeu sur les variantes de HyDomum). Que `Variant` le masque aussi de la recherche reste **[in-game]**.
- **Groupe repliable (comme l'inventaire créatif de Minecraft Bedrock) : non trouvé.** Aucun champ documenté ne le décrit. Deux champs restent à examiner côté client :
  - `Set` (`Item.java:187`, sans documentation, aucun appelant serveur) regroupe une même famille d'objets : 171 valeurs dans `zip:Server/Item/Items/`, dont `Rock_Stone` (47 objets : bloc, briques, escaliers, toits, murets…) et `Build` (56). Son usage par le client (bibliothèque, outils de construction ?) est **[in-game]** ;
  - les `Children` d'une `ItemCategory` (catégories imbriquées, § B.10 de `domum-ornamentum.md`).
- HyDomum : `VariantItem` (`domum/plugin/.../runtime/DynamicBlockTypeFactory.java`) pose `variant = true` sur chaque matériau créé.

## 43. Joueur par nom, listes déroulantes et infobulles riches (2026-10-01)

Vérifié dans les sources décompilées de 0.7.0-pre.4.

- **Joueur en ligne par nom** : `Universe.getPlayerByUsername(String, NameMatching)` (`server/core/universe/Universe.java:1479`). `NameMatching` (`server/core/NameMatching.java:10-20`) a `EXACT`, `EXACT_IGNORE_CASE`, `STARTS_WITH`, `STARTS_WITH_IGNORE_CASE` ; `DEFAULT` = `STARTS_WITH_IGNORE_CASE`.
- **Joueur hors ligne par nom** : pas de cache local de profils comme le `ProfileCache` de Minecraft. Les commandes du jeu (`/whitelist add`, `ArgTypes.GAME_PROFILE_LOOKUP_ASYNC`, `server/core/command/system/arguments/types/ArgTypes.java:207-252`) cherchent d'abord parmi les joueurs en ligne, puis appellent le service web de profils : `ServerAuthManager.getInstance().getProfileServiceClient().getProfileByUsernameAsync(name, sessionToken)` (`server/core/auth/ProfileServiceClient.java:224`), avec `getSessionToken()` (`ServerAuthManager.java:339`, null sans session). La réponse est un `CompletableFuture<PublicGameProfile>` (`getUuid()`, `getUsername()`), hors du fil du monde : on la ramène par `World.execute(Runnable)` (`World` implémente `Executor`, l. 1120). **[in-game]** : comportement sans connexion au service.
- **Liste déroulante** : `DropdownEntryInfo(LocalizableString label, String value)` (`server/core/ui/DropdownEntryInfo.java:31`), posée par `ui.set("#X.Entries", List)` puis `.Value` ; le choix revient par l'événement `ValueChanged` avec `@… = "#X.Value"` (`BlockSpawnerSettingsPage.java:177-199`). `LocalizableString.fromString` pour une donnée, `fromMessageId` pour une clé.
- **Infobulle riche** : `.TooltipTextSpans` accepte un `Message` imbriqué (vanilla `MemoriesPage.java:227`).

## 44. Coucher un PNJ à l'écran : l'animation `Sleep` sur le créneau `Status` (2026-10-01)

Constat en jeu (2026-10-01) : un citoyen monté sur un lit (`BlockMountAPI.mountOnBlock`, § 41) avec `MovementStates.sleeping = true` est dessiné **debout** sur le lit, un joueur dans le même lit est dessiné couché. Sources : décompilé de 0.7.0-pre.4 (chemins relatifs à `com/hypixel/hytale/`) et `zip:` = `pre-release-0.7.0-pre.4-Assets.zip` du cache Gradle (même taille, 3 808 952 006 octets, que l'`Assets.zip` de l'installation `pre-release`).

**Ce que le serveur envoie pour un joueur couché, et que le PNJ n'a pas : rien.**

- Aucun système du lit ne joue d'animation : ni `builtin/beds` ni `builtin/mounts` n'appellent `playAnimation` ; le seul appel d'animation des montures arrête le créneau `Movement` à la descente (`builtin/mounts/MountSystems.java:819`).
- Ce que reçoivent les spectateurs d'un dormeur : `MountedUpdate(0, offset, controller, BlockMount(Bed, position, rotation, bloc))` pour **toute** entité montée visible (`MountSystems.TrackerUpdate`, l. 831-931), et `MovementStatesUpdate` à chaque changement, `sleeping` compris (`server/core/entity/movement/MovementStatesSystems.java:166-170`, `MovementStates.equals` compare `sleeping`, `protocol/MovementStates.java:535`). Le PNJ les reçoit tous deux.
- Le reste est réservé au joueur lui-même : `PlayerSomnolence` (posé par `BedInteraction.java:97`, sans codec ni mise à jour réseau), `UpdateSleepState` écrit au seul dormeur (`builtin/beds/sleep/systems/player/UpdateSleepPacketSystem.java:70, 94`), `EnterBedSystem` filtré sur `PlayerRef` (l. 44, messages seulement).
- La seule différence restante est la nature de l'entité (joueur ou PNJ) côté client : le client d'un spectateur choisit lui-même l'animation de mouvement d'un joueur à partir de `MovementStates.sleeping` (aucun serveur ne lui envoie `Sleep`), et ne le fait visiblement pas pour un PNJ. Pourquoi ne se tranche **que dans le client**, non lu (§ 5 de la demande).

**Les PNJ vanilla se couchent par `PlayAnimation` sur le créneau `Status`, sans lit ni monture.**

- `zip:Server/NPC/Roles/Intelligent/Temple/Temple_Goblin_Scrapper_Sleep_Static.json` : `BodyMotion Nothing`, `Status` `Laydown` (l. 33-35), puis `Status` `Sleep` une fois (`Sensor Any Once`, l. 61-66). Même motif pour les bêtes et les intelligents (`_Core/Components/ActionLists/Component_ActionList_Sleep.json` : `Laydown` ; `_Core/Components/Instructions/Component_Instruction_Play_Animation.json` : créneau `Status`, utilisé par `Component_Instruction_Wild_Sleep_State` avec `Sleep`) ; le réveil repasse par `Status` (`Component_ActionList_Wake.json` : `Wake`) ou vide le créneau (`Component_Instruction_Clear_Status_Animation.json` : `PlayAnimation` `Status` sans `Animation`).
- L'action `PlayAnimation` appelle `NPCEntity.playAnimation(ref, slot, animationId, store)` (`server/npc/corecomponents/audiovisual/ActionPlayAnimation.java:62`), l'appel public que le plugin peut faire lui-même.
- **Un humanoïde dérivé de `Player` qui s'allonge** : `Outlander_Peon` (`zip:Server/NPC/Roles/Intelligent/Faction/Outlander/Outlander_Peon.json:3, 16`, `Template_Trork_Melee`, `BedBlockSet Trork_Bedroll`). Le modèle `Outlander_Peon` → `Outlander` → `Parent: Player` (`Characters/Player_With_Face.blockymodel`, racine `Origin` → `Pelvis`) ne redéfinit pas `Sleep` : il joue **le `Sleep` du joueur**. Le Trork/Outlander marche jusqu'à un bloc du lit (capteur `Block` sur `BedBlockSet`, `Reserve`), puis joue `Status` `Sleep` (`Component_Trork_Instruction_Idle.json:759-806`). Pas de `mountOnBlock`.

**Les animations de sommeil couchent tout le corps par le nœud `Pelvis`.**

- `zip:Common/Characters/Player.blockymodel` : `Origin` (racine, à 0) → `Pelvis` (y 51) → `Belly`, cuisses… : `Pelvis` porte tout le corps.
- `zip:Common/Characters/Animations/Flavor/Sleep.blockyanim` (le `Sleep` de `Player.json`, l. 873-878, sans `Looping` donc **en boucle** : défaut `looping = true`, `server/core/asset/type/model/config/ModelAsset.java:599, 628`) anime 24 nœuds, dont **`Pelvis` : orientation (-0,707107 ; 0 ; 0 ; 0,707107), soit -90° autour de X (sur le dos), et position y -42** (l'hypothèse « seulement tête, torse, membres » est fausse). `Sleep2.blockyanim` : `Pelvis` (-0,5 ; 0,5 ; -0,5 ; 0,5), y -39 (sur le côté). `Common/NPC/Human/Animations/Sleep.blockyanim` a le même `Pelvis` mais aucun modèle de `Server/Models` ne le cite.
- Même principe chez les PNJ : `Pelvis` à ~90° dans `Sleep` du Trork (y -55), du Kweebec Sapling (y -17), du Feran (y -32), du Goblin (y -31).
- `PlayerTestModel_V` hérite bien de `Sleep` : `AnimationSets` d'un enfant est **fusionné** avec celui du parent (`MapUtil.combineUnmodifiable(model.animationSetMap, m)`, `ModelAsset.java:235-241`). `NPCEntity.playAnimation(…, "Sleep")` passe donc le contrôle d'existence (`NPCEntity.java:276`).

**Ce que fait `NPCEntity.playAnimation(ref, AnimationSlot.Status, "Sleep", acc)`** (`server/npc/entities/NPCEntity.java:254-293`).

- Hors créneau `Action`, il refuse (avertissement) un id absent des `AnimationSets` du modèle (l. 276-278), puis écrit l'id dans `ActiveAnimationComponent` (l. 285-288 ; composant posé sur tout PNJ, `server/npc/systems/RoleBuilderSystem.java:228`) et envoie `PlayAnimation(networkId, null, "Sleep", Status)` aux joueurs qui voient le PNJ (`AnimationUtils.java:48-83`). Rien n'est renvoyé si l'id est déjà celui du créneau (l. 286).
- **Nouveaux spectateurs** : `ModelSystems.AnimationEntityTrackerUpdate` leur envoie `ActiveAnimationsUpdate` (tous les créneaux) dès qu'ils voient l'entité (`server/core/modules/entity/system/ModelSystems.java:97-101, 109`).
- **Non persisté** : `ActiveAnimationComponent` est enregistré sans codec (`server/core/modules/entity/EntityModule.java:393`, comparer `Frozen` l. 436). `MountedComponent` est lui aussi retiré au déchargement (§ 41) : au rechargement, le citoyen n'est plus ni monté ni couché, et c'est au plugin de recoucher.
- **Lever** : `playAnimation(ref, AnimationSlot.Status, null, acc)` vide le créneau et envoie l'arrêt (le contrôle d'existence ne s'applique pas à `null`, l. 276), comme `SpawnMarkerSystems.java:642` et `Component_Instruction_Clear_Status_Animation`. À faire à **chaque** lever, y compris après une descente sans nous (téléportation, lit cassé, passage de nuit, § 41) : sinon le citoyen marcherait couché.
- Accès : `store.getComponent(ref, NPCEntity.getComponentType())` (`NPCEntity.java:154`), sur le fil du monde.

**Un modèle et une animation à nous : possible, inutile ici.** Un modèle `Server/Models/…/X.json` avec `"Parent": "PlayerTestModel_V"` et ses propres `AnimationSets` (fusionnés avec ceux du parent) serait choisi par le rôle via `"Appearance": "X"` (id d'asset du modèle). Une entrée d'`AnimationSets` : `{"Animations": [{"Animation": "<chemin sous Common/>.blockyanim", "Speed", "BlendingDuration", "Looping", "Weight", "FootstepIntervals", "SoundEventId", "PassiveLoopCount"}], "NextAnimationDelay"}` (clés du codec, `ModelAsset.java`). Un `.blockyanim` est un JSON `{"formatVersion": 1, "duration": 90, "holdLastKeyframe": false, "nodeAnimations": {"<nœud>": {"position": [{"time", "delta": {x, y, z}, "interpolationType": "smooth"}], "orientation": [{"time", "delta": {x, y, z, w}, "interpolationType"}], "shapeStretch": [], "shapeVisible": [], "shapeUvOffset": []}}}` (exemple réel : `Flavor/Sleep.blockyanim`) ; l'unité de `duration` et de `time` n'est pas vérifiée. Le `Sleep` du joueur couche déjà le corps : aucun asset nouveau n'est nécessaire.

**[in-game]**

- Que le client couche le citoyen avec `Status` `Sleep` alors qu'il est monté et `sleeping` (superposition avec l'animation de mouvement que le client choisit, peut-être `MountIdle`), et à la même hauteur qu'un joueur : `Pelvis` y -42 est relatif à la position du corps, posée au point de couchage par `mountOnBlock`.
- Que l'orientation suive le lit (le lacet vient du point de couchage, § 41).

## 45. Métadonnées d'un objet tenu, titre d'un conteneur et biome d'une colonne (2026-10-01)

Sources : décompilé de 0.7.0-pre.4 (chemins relatifs à `com/hypixel/hytale/server/core/`).

- `ItemStack.withMetadata(String key, BsonValue)` renvoie une copie avec la clé posée. `getMetadata()` est `@Deprecated` et renvoie un clone du document (`inventory/ItemStack.java` l. 175-178). `getFromMetadataOrNull(key, Codec)` décode sans clone, mais lève si la valeur n'a pas le type du codec (l. 670-673). Le presse-papiers lit par `getMetadata()` pour rester tolérant au type.
- `ItemContainer.replaceItemStackInSlot(slot, ancien, nouveau)` ne remplace que si la case tient encore `ancien` (garde par `isStackableWith`), puis envoie la mise à jour (`inventory/container/ItemContainer.java` l. 207-211). `.succeeded()` dit si la case a changé. Un objet déplacé ou jeté entre-temps reste donc intact.
- `InteractionContext.getHeldItem()`, `getHeldItemContainer()` et `getHeldItemSlot()` donnent l'objet, le conteneur et la case de l'interaction en cours (`entity/InteractionContext.java` l. 413-424).
- Biome d'une colonne (fenêtre du champ, `HytaleWorldQuery.biome`) : `world.getChunkStore().getGenerator()` est un `worldgen.chunk.ChunkGenerator` pour un monde généré, puis `getZoneBiomeResultAt(int seed, int x, int z).getBiome().getName()` avec `(int) world.getWorldConfig().getSeed()`. C'est le motif de `worldgen/BiomeDataSystem.java` l. 60-73 (biome du joueur) et de `NPCMemory.java` l. 305-307. Le nom est l'identifiant brut du générateur, non traduit. Un monde sans ce générateur n'a pas de biome.
- `ContainerWindow.getData()` est le `windowData` envoyé dans `OpenWindow`, vide par défaut. `BenchWindow` y met `name`, une clé de traduction. Que le client affiche `name` pour une fenêtre de conteneur n'est **pas vérifié** (**[in-game]**, `TESTING.md` point 72).

## 46. Faire apparaître un PNJ dans une colonne (`HytaleCitizenBodies.spawn`, 2026-10-02)

Sources : décompilé de 0.7.0-pre.5 (`server/npc/NPCPlugin.java`, `server/spawning/SpawningContext.java`).

- `NPCPlugin.spawnNPCWithColumnProbe(store, rôle, groupe, world, x, z, yHint, rotation, postSpawn)` ne teste **qu'une colonne** (x, z) (l. 1149-1194). Il renvoie `FAIL_INVALID_POSITION` quand `SpawningContext.set` échoue ou quand `canSpawn()` refuse la place du modèle.
- `SpawningContext.set(world, x, y, z)` exige un chunk et une section **qui tournent** (`resolveTickingChunk`, `isTickingSection`, l. 544-545). Il échantillonne la colonne de `y - 16` à `y + 16` (`maxVerticalOffset` 16, l. 559-561) et choisit l'espace libre le plus proche de `y` (`selectGap`). Le sol retenu est le dessus du bloc plein sous cet espace : dans la colonne d'un bloc de hutte (`Material: Solid`), c'est le dessus de la hutte.
- Constat du 2026-10-02 : la seule colonne `x + 1` de l'hôtel de ville était refusée, et la colonie n'avait aucun citoyen. Le cœur parcourt maintenant les colonnes autour, dans l'ordre de MC (`CitizenArrival`), et saute la colonne d'un bâtiment.

## 47. Pose d'un bloc à la main : case visée, accroupi, échange de cases (`HutPlaceSystem`, 2026-10-02)

Sources : décompilé de 0.7.0-pre.5.

- `PlaceBlockEvent.getTargetBlock()` est la case où va le bloc, pas la case cliquée (`PlaceBlockInteraction` l. 146 et 202-227) ; l'événement ne donne pas la face cliquée. Annulé, il laisse l'objet dans l'inventaire (`BlockPlaceUtils.placeBlock` l. 104-109 sort avant `removeItemStackFromSlot`, l. 141). Seules les interactions d'un joueur créent cet événement : `ports().blocks().place` ne le déclenche pas.
- Un joueur accroupi : `MovementStatesComponent.getMovementStates().crouching`, posé par le client (`PlayerInput.SetMovementStates`, l. 227-234) et lu côté serveur par `ConditionInteraction` (l. 110).
- `InventoryComponent.getCombined(store, ref, HOTBAR_FIRST)` vaut barre d'action puis sac (`InventoryComponent` l. 156) ; la capacité de la barre vient du composant `InventoryComponent.Hotbar`. `ItemContainer.replaceItemStackInSlot(slot, attendu, nouveau)` renvoie une transaction dont `succeeded()` dit si la case tenait bien `attendu`.

## 48. Objet en main, distance de vue et hauteur du monde (`ColonyBorderSystem`, 2026-10-02)

Sources : décompilé de 0.7.0-pre.5.

- Objet en main : `InventoryComponent.getItemInHand(store, ref)` (`InventoryComponent` l. 291-300) renvoie l'objet actif de l'outil s'il est utilisé, sinon celui de la barre d'action (`Hotbar.getActiveItem`).
- Distance de vue : `Player.getViewRadius()` (l. 673-675), en tronçons de 32 blocs (`ChunkUtil.SIZE`), bornée par le `MaxViewRadius` du serveur ; `getClientViewRadius()` est la valeur demandée par le client. `EntityViewer.viewRadiusBlocks` la donne en blocs (commande `/player viewradius get`).
- Hauteur du monde : `ChunkUtil.HEIGHT` = 320 (10 sections de 32).
- `ClearDebugShapes` (sans champ) efface **toutes** les formes `DisplayDebug` du joueur qui le reçoit (`DebugUtils.clear` l. 83-89 l'envoie à tout le monde).

## 49. Apparition des PNJ : lumière, suppression par zone, point d'attache (2026-10-02)

Sources : décompilé de 0.7.0-pre.5 et assets pre.5. Détail et citations : `docs/research/colony-bounds-and-mob-spawns.md` § 3 à 5.

- La lumière est une condition d'apparition seulement si l'asset déclare `LightRanges` (`NPCSpawn` l. 64-82) : 11 apparitions du monde sur 98, 55 balises sur 96. Les monstres de surface (squelettes, loups, araignées) ne la déclarent pas.
- Aucun événement d'apparition annulable. Le blocage par zone passe par `SpawnSuppression` (asset `NPC/Spawn/Suppression`) + une entité `SpawnSuppressionComponent`/`TransformComponent`/`UUIDComponent` ajoutée en `AddReason.SPAWN` sur le thread du monde ; grain : chunk Hytale de 32, hauteur ± rayon ; persistant ; sans effet sur `NPCPlugin.spawnNPC*`.
- Point d'attache : `NPCEntity.leashPoint`, capteur `Leash` (`Range`), corps `WanderInCircle`/`WanderInRect` ; notre rôle citoyen n'en utilise aucun.
- **Un système par classe** : `ComponentRegistry.registerSystem` indexe les systèmes par leur classe (`systemClasses.put(system.getClass(), …)`, l. 1218) et refuse une seconde instance de la même (« System of type … is already registered! », l. 646-647) ; le contournement `registerSystem(system, true)` est `@Deprecated(forRemoval = true)` (l. 622-623). Deux instances d'un même système générique (un `RefChangeSystem` par composant) font échouer la mise en place du plugin : il faut une sous-classe par instance. Vu en jeu le 2026-10-02.

## 50. Ce que le joueur porte sur lui : armure et emplacements utilitaires (2026-10-02)

Source : décompilé de 0.7.0-pre.5, `server/core/inventory/InventoryComponent.java`.

- Un joueur a un composant par section : `Hotbar` (-1), `Storage` (-2), `Armor` (-3, une case par `ItemArmorSlot`), `Utility` (-5, 4 cases, filtre `getUtility().isUsable()` : l'équivalent de la main secondaire de MC), `Tool` (-8), `Backpack` (-9). `getInventory()` donne l'`ItemContainer` de chacun.
- `getCombined(accessor, ref, types...)` met en cache un `CombinedItemContainer` dans le composant `Combined`, par **contenu** du tableau de types (`Object2ObjectOpenCustomHashMap` dont la stratégie compare par `Arrays.hashCode`/`Arrays.equals`, l. 452-463) : un tableau neuf de même contenu retrouve l'entrée en cache. `HytalePlayerInventory.equipped` lit simplement `Armor` et `Utility` par `store.getComponent`, chacun dans son ordre.
- `ItemContainer.forEach` saute les cases vides (l. 1221-1227).

## 51. L'objet qui pose un bloc, et la pose « comme un joueur » (2026-10-02)

Source : décompilé de 0.7.0-pre.5 et `pre-release-0.7.0-pre.5-Assets.zip` ; audit `audit-monde-hytale.md` A-15. Code : `plugin/item/HytaleBlockItems`, `HytaleItemSources`.

- `BlockType.getItem()` est l'objet **conteneur** du bloc, pas forcément celui qui le pose. Un objet pose une variante par `BlockType.getPlacementSettings()` : `getWallPlacementOverrideBlockId()`, `getFloorPlacementOverrideBlockId()`, `getCeilingPlacementOverrideBlockId()` (`BlockPlacementSettings.java:118-128`) ; l'objet d'un `Item` est le `BlockType` de `item.getBlockId()`. `Furniture_Crude_Torch` pose `Wood_Torch_Wall` au mur.
- Casse : `BlockHarvestUtils.getDrops` (l. 647-667) donne `ItemId` × `Quantity` de `Gathering.Breaking` (ou `Soft`), ou les tirages de `DropList`, ou l'objet du bloc s'il n'y a ni l'un ni l'autre. Une `DropList` en ligne est un asset contenu, lisible par `ItemDropList.getAssetMap().getAsset(id)` ; un `SingleItemDropContainer` de premier niveau donne toujours son `ItemDrop` (pas de tirage, `populateDrops`). `Furniture_Crude_Chest_Large` (deux petits coffres reliés par `ChestConnectedBlockTemplate`, hitbox `Chest_Large`) se casse en 2 `Furniture_Crude_Chest_Small`.
- `CraftingRecipe.getAssetMap()` contient aussi les recettes portées par les objets ; `getPrimaryOutput()` et `getOutputs()` donnent les objets fabriqués.
- Pose comme un joueur : `BlockOperations.setBlock` remet à zéro la marque « déco » de la case ; `BlockPlaceUtils` (l. 438-440) la repose par `BlockPhysics.markDeco(store, section, x, y, z)` quand `blockType.canBePlacedAsDeco()` (`ignoreSupportWhenPlaced` ou `Gathering.UseDefaultDropWhenPlaced`). `markDeco` ajoute au besoin le composant `BlockPhysics` à la section (changement structurel, même contexte que `FluidSection` dans `HytaleWorldBlocks.place`). Une case marquée se casse en son propre objet (`BlockHarvestUtils.java:959-965`). **[in-game]** pour la pose du bâtisseur.
- Un type de ressource peut compter des objets introuvables. `Plant_Fern_Jungle_Trunk`, `Plant_Fern_Trunk` et `Plant_Fern_Wet_Giant_Trunk` (`Server/Item/Items/Plant/Grass/`) ont `Parent: Wood_Oak_Trunk`, donc ses types `Wood_Oak`, `Wood_Hardwood`, `Wood_Hardwood_Trunk` et `Wood_Trunk` (`Wood/Oak/Wood_Oak_Trunk.json:114-126`) et sa casse (`Breaking.ItemId: Wood_Oak_Trunk`). Aucune recette, liste de butin ou troc ne les donne : ces blocs existent dans le monde (prefabs `Server/Prefabs/Cave/Nodes/Rock_Volcanic/Jungle/…` et `Server/Prefabs/Plants/Jungle/Ferns/Island/…`, jungles souterraines selon l'utilisateur), mais leur objet ne se ramasse jamais. Trié par id, le premier objet de `Wood_Hardwood_Trunk` était `Plant_Fern_Jungle_Trunk` ; `ResourceTypeIndex.items` met désormais en tête les objets qui ont une source (`HytaleItemSources`), vu par `/hycolony selftest` le 2026-10-03.
- Faits de bloc lus par `HytaleBlockTraits` (2026-10-03, `domaine1-suite.md`) :
  - bloc plein : `FluidTicker.isFullySolid(BlockType)` (matériau `Solid`, dessiné `Cube` ou `CubeWithModel`), et la hitbox pleine `BlockType.getHitboxType()` = `BlockBoundingBoxes.DEFAULT` (`"Full"`, la valeur par défaut ; `Soil_Mud` a `Block_Seven_Eighth`) ;
  - fluide sous un bloc : une case a un bloc (`BlockSection`) et un fluide (`FluidSection.getFluidId`) indépendants ; `HytaleWorldBlocks.get` rend le bloc, `fluidAt` le fluide, et la pose d'un fluide (`HytaleSections.placeFluid`) l'écrit sous le bloc présent ;
  - feuille : `BlockType.getGroup()` vaut `Leaves` pour toutes les `Plant_Leaves_*` (le set `Leaves` ne prend que les ids finissant par `Leaves`) ;
  - source de fluide : `Fluid.getMaxFluidLevel() == 1` (8 pour un fluide qui coule) ;
  - forme libre : `BlockType.getConnectedBlockRuleSet()` est un `CustomTemplateConnectedBlockRuleSet`, dont `getShapeTemplateAsset().getId()` nomme le gabarit (`WallConnectedBlockTemplate` pour 80 clôtures, portillons, barreaux et murs vanilla) et `getShapeNameToBlockPatternMap()` les blocs de chaque forme.

## 52. Nager, monter aux échelles et aux lianes : ce que permet un PNJ (2026-10-03)

Source : décompilé `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `npc/` pour `server/npc/`) et `pre-release-0.7.0-pre.5-Assets.zip` (`gradle.properties` épingle `0.7.0-pre.5`, pas pre.4) ; MC dans `sources/minecolonies/src/main/java/com/minecolonies/` (abrégé `MC/`). Complète l'audit `audit-monde-hytale.md` D-4 (échelles) et D-6 (eau), et le § 39.

**Plusieurs contrôleurs, aucun basculement automatique.**
- `MotionControllerList` accepte plusieurs contrôleurs, rangés par type (`npc/movement/controllers/builders/BuilderMotionControllerMap.java:40-42`). Le contrôleur actif est choisi une fois : `InitialMotionController`, **sinon au hasard** s'il y en a plusieurs (`npc/role/builders/BuilderRole.java:935-943`, `npc/role/Role.java:688-698`). Le nom actif est sauvé dans `NPCEntity` et restauré au chargement (`Role.java:659`, `npc/systems/RoleSystems.java:483-490`).
- `MovementMode` (`WALK`, `WADE`, `UNDERWATER_WALK` → `Walk` ; `DIVE` → `Dive` ; `FLY`, `npc/movement/MovementMode.java:5-10`) ne sert qu'à l'apparition (`BuilderMotionControllerMap.canSpawn` l. 97-130, `getModeToController` l. 169-188).
- En jeu, seuls deux mouvements changent de contrôleur : `TakeOff` → `"Fly"` et `Land` → `"Walk"` (`npc/corecomponents/movement/BodyMotionTakeOff.java:42-50`, `BodyMotionLand.java:51`). Rien ne bascule à l'entrée ou à la sortie de l'eau. `Role.setActiveMotionController(ref, npc, name, accessor)` est public (`Role.java:613-647`) : un plugin peut basculer lui-même (désactive l'ancien, active le nouveau, prévient les instructions).
- Aucun rôle vanilla n'a `Walk` et `Dive` ensemble : les 6 rôles `Dive` (`Template_Swimming_Passive.json:74`, `Template_Swimming_Aggressive.json:110`, 4 rôles de test) n'ont que lui. Le crocodile n'a que `Walk` (`Creature/Reptile/Crocodile.json` → `Template_Predator.json:451-463`) ; crabe et homard ont `Walk` avec `BreathesInWater: true` (`Aquatic/Marine/Crab.json`). Le seul couple vanilla est `Walk` + `Fly` des oiseaux, avec `TakeOff`/`Land` (`_Core/Tests/Birds/Test_Bird_Land.json:8-132`) ; `TakeOff` exige les deux contrôleurs (`BuilderBodyMotionTakeOff.java:59-60`).

**Un `Walk` dans l'eau.**
- `inWater` vaut vrai si un fluide occupe la case des pieds (`MotionControllerWalk.java:733-757`) ; la vitesse horizontale suit alors `FluidFX.movementSettings.horizontalSpeedMultiplier`. La chute est bornée par `MaxSinkSpeedFluid` (défaut 4,0, `BuilderMotionControllerWalk.java:111-119`, `MotionControllerWalk.java:983`, `1288-1301`) : il n'y a pas de flottaison, **il coule et marche au fond**. `Walk` ne pose jamais `swimming` (l. 355, 398) ; `inFluid` est posé pour tous (`MotionControllerBase.java:302`).
- Respiration : `Role.couldBreathe` dit vrai dans un fluide seulement avec `BreathesInWater`, dans l'air avec `BreathesInAir` (`Role.java:1146-1152`, défauts vrai/faux `BuilderRole.java:729-730`). `Role.canBreathe` renvoie toujours vrai si le rôle est `Invulnerable` (l. 1142-1144) ; c'est lui qui répond à `BreathingCheckEvent` (`npc/systems/NPCSystems.java:300-305`, `server/core/entity/EntityUtils.java:99-121`). La noyade (10 dégâts par seconde une fois l'oxygène vide, `DROWNING`) vient de `DamageSystems.CanBreathe` (`server/core/modules/entity/damage/DamageSystems.java:622-675`). **Notre citoyen est `Invulnerable` : il ne se noie pas**, et `couldBreatheCached` (copie de `CachedStatsComponent.canBreathe`, `PositionCacheSystems.java:292-295`) reste vrai sous l'eau, donc les contraintes d'évasion `BREATHE`+`WADE` (`BodyMotionBase.java:36-44`, `MotionControllerBase.java:1333-1342`) ne s'appliquent jamais à lui.
- Positions valides : `isValidWalkPosition` (`MotionControllerWalk.java:2865-2879`) teste `role.couldBreathe` (pas `canBreathe`) aux yeux (`breathingDepth` = yeux), puis aux pieds + `constraintDepth` sauf si `WADE` est relâché (`util/WalkBreathingRules.java`). `constraintDepth` vaut `min(0,25, yeux × 0,5)` pour un rôle qui ne respire que l'air, la hauteur des yeux s'il respire les deux (`util/WalkFluidDepth.java`, appel l. 456-457). `BREATHE` relâché saute tout le test.
- `HyColonySeek` ne déclare pas `RelaxedConstraints` : mode ancien, `RelaxedMoveConstraints` vrai par défaut → `{WADE}` (`BuilderBodyMotionFindBase.java:122-142`, `BodyMotionBase.java:16-34`, `RelaxedConstraint.DEFAULT_WHEN_RELAXED`). Ces contraintes vont à la sonde, à l'A\* et au mouvement (`BodyMotionFindBase.java:184-186`). Le citoyen **entre donc dans l'eau tant que ses yeux restent hors de l'eau**, et la sonde refuse plus profond (nuance de l'audit D-6, qui dit qu'il n'y entre pas) **[in-game]**.
- Déjà immergé : la sonde ne coupe un pas que si la position de départ est valide (`MotionControllerWalk.java:2170-2173`, `2228-2231`), et le mouvement ajoute `WADE` quand la position courante est invalide (l. 1680-1684). Un citoyen tombé en eau profonde marche donc au fond vers sa cible, et ne sort que par une berge d'au plus `MaxClimbHeight` (3) au-dessus de ses pieds ; sinon l'anti-blocage le téléporte **[in-game]**.
- L'A\* de Hytale coûte la seule distance (`navigation/AStarBase.java:562-564`, `measureWalkCost` protégé) : aucun coût d'eau. Il est créé dans le constructeur de `BodyMotionFindWithTarget` (`new AStarWithTarget()`, l. 57-58) : `HyColonySeek` ne peut pas en changer le coût.

**`Dive`.**
- Seul mode `DIVE` (`MotionControllerDive.java:35`). « Dans l'eau » = une case de fluide dont le haut atteint `pieds + swimDepth` (`util/PositionProbeWater.java:25-48`) ; `SwimDepth` est relatif, entre les yeux et le haut de la boîte (l. 744-756). Il ne se dirige que dans l'eau (`canSteer`, l. 444-447) et un pas qui sortirait de l'eau est coupé par bissection (l. 363-369, 401-404) : **un `Dive` ne sort jamais de l'eau seul**. Hors de l'eau il tombe (`MaxFallSpeed`, l. 241). Il vise une profondeur entre le fond + `MinDepthAboveGround` et la surface − `swimDepth` − `MinDepthBelowSurface` (l. 108-131). Paramètres : `MaxSwimSpeed`, `MaxDiveSpeed`, `MaxSinkSpeed`, `SwimDepth`, `SinkRatio`, `MinDiveDepth`, `MaxDiveDepth`, `MinDepthAboveGround`, `MinDepthBelowSurface`, `MinWaterDepth`, `MaxWaterDepth` (`BuilderMotionControllerDive.java:65-187`) ; exemple `Template_Swimming_Passive.json:72-88`.
- En nage, l'état est `SWIMMING` et pose `movementStates.swimming` (`MotionControllerBase.java:318-322`, `363-372`). `Seek` marche avec `Dive` (les modèles nageurs l'utilisent, `Template_Swimming_Passive.json:168`).
- Animations : le modèle `Player` (parent de `PlayerTestModel_V`) a `SwimIdle`, `Swim`, `SwimFast`, `SwimFloat`, `SwimSink`, `SwimDive*`, les `Fluid*` (marche dans l'eau = animations de marche) et `ClimbIdle`, `ClimbUp`, `ClimbDown`, `ClimbLeft`, `ClimbRight` (`Server/Models/Human/Player.json:441-620`, fichiers `Characters/Animations/Swim/*.blockyanim`, `Characters/Animations/Climb/*.blockyanim`). Quel état le client traduit en quelle animation sur un PNJ reste **[in-game]**.

**Échelles et lianes.**
- `BlockMovementSettings` (`IsClimbable`, `ClimbUpSpeedMultiplier`, `ClimbDownSpeedMultiplier`, `ClimbLateralSpeedMultiplier`, `server/core/asset/type/blocktype/config/BlockMovementSettings.java:10-46`) n'est lu que pour le paquet client ; `isClimbable()` (l. 147) n'a aucun appelant, ni dans `npc/` ni dans le mouvement des entités (recherche `climbable|ladder` sur tout le décompilé). Un plugin le lit par `BlockType.getMovementSettings()` (`BlockType.java:1650`). La montée d'échelle du joueur est faite par son client (`ClimbSpeed 0.035`, `ClimbSpeedLateral`, `ClimbUpSprintSpeed`, `ClimbDownSprintSpeed`, `Server/Entity/MovementConfig/Default.json:23-26`, unité non vérifiée).
- `MotionControllerWalk.isClimbable` ne veut dire que « peut être enjambé » (pas de dégâts, hors de l'ensemble `Fence`, l. 2832-2837). L'animation `Climb` d'`AscentAnimationType` pose `movementStates.climbing` pendant toute montée de marche (l. 307-356).
- Une échelle est `Material: Solid` avec la hitbox `Ladder`, une plaque de 0,15 bloc contre une face (`Server/Item/Block/Hitboxes/Furniture/Ladder/Ladder.json`, `Furniture_Crude_Ladder.json:65-69`). `computeClimbHeight` lève la boîte jusqu'au-dessus des obstacles qui la chevauchent, dans la limite de `MaxClimbHeight` (`MotionControllerWalk.java:2636-2710`) : une colonne d'échelle de 3 blocs au plus, avec de la place au-dessus, devrait se franchir comme une marche ; plus haute, non **[in-game]**.
- Blocs `IsClimbable: true` (`Server/Item/Items/`) : les 18 `Furniture_*_Ladder`, 11 `Furniture_*_Trapdoor`, `Deco_Rope`, `Deco_Iron_Chain_Small`, `Deco_SpiderWeb_Straight`, et des plantes : **`Plant_Vine`** (liane murale, hitbox `Ladder`, sans `Material` donc `Empty` par défaut `BlockType.java:873`, support `North` plein ou `Up` `Family=Vine`, recette au `Farmingbench`, `Plant/Plant_Vine.json:10-71`), `Plant_Vine_Thick_Vertical` (`Solid`, hitbox `Rope`), `Plant_Vine_Thick_Roots`, `Plant_Liana_Void` (hitbox `Ladder`), `Plant_Roots_Cave`, `Plant_Roots_Leafy` (`Empty`), `Plant_Barnacles`, les `Plant_Moss_Cave_*`. Les lianes existent donc ; un PNJ traverse une liane `Empty` sans s'y accrocher.
- Leviers d'un plugin pour monter le corps :
  - changer de contrôleur : `Role.setActiveMotionController` (ci-dessus). `Fly` vole en 3D mais est un vol d'oiseau (`MinAirSpeed` 0,1, `MaxClimbAngle` 45°, roulis, builder `WorkInProgress`, `BuilderMotionControllerFly.java:60-127`) et ne se dirige qu'en l'air (l. 612-614) ; inadapté sans réglages **[in-game]** ;
  - vitesse : `Role.setVelocity(Vector3d, VelocityConfig, ignoreDamping)` (`Role.java:1158-1162`) ; tant qu'une vitesse externe existe le PNJ est « poussé » et ne se dirige plus (`MotionControllerBase.java:1068-1075`), `Walk` le décolle du sol et applique la gravité (`MotionControllerWalk.java:473-482`, `1005-1009`). C'est l'analogue du `setDeltaMovement(0, 0.1, 0)` de MC ;
  - téléportation : `componentAccessor.addComponent(ref, Teleport.getComponentType(), Teleport.createExact(pos, rotation))`, comme `BodyMotionTeleport` (l. 86-99) et notre `BodyTeleport` ; le rendu de pas répétés chez le client est **[in-game]** ;
  - animation : poser `movementStates.climbing` après `npc.systems.MovementStatesSystem`, comme `CitizenMantleSystem` pose `mantling` (§ 39) ; que le client joue alors `ClimbUp` est **[in-game]**.
  - le chemin : l'A\* de Hytale n'explore que par `probeMove` du contrôleur actif ; une colonne plus haute que `MaxClimbHeight` n'y passe jamais. Un passage par échelle doit donc être planifié hors de l'A\* (étapes de détour du cœur, comme `DetouringBodies`, § 40).

**MineColonies.**
- Nage : le navigateur des citoyens a `setCanFloat(true)` et `canSwim` (`MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:155-157`, `api/entity/citizen/AbstractEntityCitizen.java:345-349`), vitesse ×`CITIZEN_SWIM_BONUS` 2,0 dans l'eau (l. 69, `getSpeedFactor` navigateur l. 606-616), et ne sont pas poussés par le courant (`isPushedByFluid` faux, l. 705-708). L'A\* nage en surface : un nœud au-dessus d'un liquide est accepté si `canSwim` et que c'est de l'eau (`MC/core/entity/pathfinding/pathjobs/AbstractPathJob.java:1584-1588`, `1655-1670`). Coûts (`MC/core/entity/pathfinding/PathingOptions.java`) : entrée dans l'eau `swimCostEnter` 24 (l. 53), puis `swimCost` 4 par nœud (l. 43), plus `divingCost` 4 la tête sous l'eau (l. 73), sans bonus de route (`AbstractPathJob.java:1037-1116`). Le `MovementHandler` monte le corps vers la hauteur voulue dans l'eau (`navigation/MovementHandler.java:120-128`). `EntityAIFloat` (`EntityCitizen.java:351`, `MC/core/entity/ai/minimal/EntityAIFloat.java:41-73`) : les yeux dans l'eau et pas d'air au-dessus → chemin `PathJobEscapeWater` (rayon `FOLLOW_RANGE` × 5, `walkUnderWater`, coûts d'eau à 1) et navigation en pause 15 s. Ni noyade spécifique ni respiration dans le code de MC.
- Échelles : `PathfindingUtils.isLadder` (`MC/core/entity/pathfinding/PathfindingUtils.java:367-375`, `378-…`) accepte `LadderBlock` et le tag `freeClimbBlocks` toujours, les autres blocs `CLIMBABLE` (lianes…) seulement avec `canClimbAdvanced`. Il n'y a **pas** de `canUseLadders` : les échelles sont toujours permises ; `canUseRails` et `canClimbAdvanced` sont faux par défaut (`PathingOptions.java:88`, `110`) et donnés par les recherches `effects/rails` et `effects/vinesunlock` (`EntityCitizen.java:741-742`, `841-872` ; recherche `civilian/vines` : 64 lianes, résidence niveau 3, `src/datagen/generated/minecolonies/data/minecolonies/researches/civilian/vines.json`). Un nœud d'échelle explore vers le haut et le bas (`AbstractPathJob.java:684-688`), monter une échelle ne coûte pas de saut (l. 1056), un grimpable autre qu'une échelle coûte `nonLadderClimbableCost` 3 de plus (l. 1099-1101), on ne tombe pas d'une échelle (l. 1611). Mouvement : `handleLadders` centre le corps devant l'échelle, puis `doLadderMovement` vise un point décalé de 0,8 vers l'échelle et 1 bloc plus haut, avec une poussée `setDeltaMovement(0, 0.1, 0)` hors d'un `LadderBlock` ; à la descente, accroupi et `setYya(-0.5)` (`navigation/MinecoloniesAdvancedPathNavigate.java:741-875`). La montée elle-même est la physique d'échelle de Minecraft (hors des sources de MC, non vérifiée ici).

**Pistes (§ 6 : le système suit MC, le monde Hytale).**
- **Nage, la plus fidèle** : rôle `Walk` + `Dive` avec `InitialMotionController: "Walk"` (sinon le contrôleur de départ est tiré au hasard) ; un système du plugin bascule en `Dive` quand le corps `Walk` est dans l'eau et que ses yeux le seraient, et revient à `Walk` quand le `Dive` touche le fond près d'une berge ou n'est plus dans l'eau ; `Dive` réglé pour nager en surface (`MinDepthBelowSurface` 0, `MaxDiveDepth` faible, `SwimDepth` qui garde la tête dehors). Pour que le chemin traverse l'eau, `HyColonySeek` relâche `["Wade", "Breathe"]` (l'A\* de `Walk` accepte alors l'eau, au fond) ; le coût d'eau de MC (24 + 4 par nœud) ne peut pas aller dans l'A\* de Hytale, il reste au cœur (choix de cases, `BlockApproach`). Les vitesses de nage suivent le monde Hytale. Tout est **[in-game]** : bascule sans à-coup, profondeur de nage, sortie par la berge, animation `Swim`, et la descente au fond d'un lac de plus de 3 blocs (`MaxDropHeight`).
- **Nage, la plus simple** : garder `Walk` seul et relâcher `Breathe` : le citoyen marche au fond (le `walkUnderWater` de `PathJobEscapeWater`), sans nage. Écart à marquer.
- **Échelles et lianes** : le cœur planifie les passages d'échelle comme MC (nœuds `isLadder`, coûts, lianes seulement avec la recherche `vinesunlock`, « liane » = bloc `IsClimbable` qui n'est pas une `*_Ladder`), et le plugin fait la montée : au pied de la colonne, il centre le corps, le monte par vitesse ou par petites téléportations à la vitesse d'échelle d'un joueur de Hytale, pose `climbing`, puis rend la main à `HyColonySeek` en haut. Variante minimale : téléporter en haut de la colonne. **[in-game]** : vitesse et rendu, `ClimbUp`, colonne ≤ 3 franchie seule par `Walk`.

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
