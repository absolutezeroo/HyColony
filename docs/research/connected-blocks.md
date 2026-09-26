# Connected blocks (stairs, roofs, fences, walls) in Hytale 0.6.8

Why the builder places straight stairs where the prefab has corners, and how to fix it.
Sources: decompiled server under `build/vineflower/hytale-server` and `release-0.6.8-Assets.zip`.
Package prefix: `com.hypixel.hytale.server.core`.

## TL;DR

- A corner is a **state variant BlockType** (`*Rock_Stone_Brick_Stairs_State_Definitions_Corner_Right`). It is not a rotation and not a separate item.
- Only two server paths ever choose a shape: `ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors`, called by player placement (`BlockPlaceUtils.tryPlaceBlock`) and by block removal (`BlockHarvestUtils.removeBlock`). `BlockOperations.setBlock` never does. `SetBlockSettings.NO_UPDATE_NEIGHBOR_CONNECTIONS` (128) is not read anywhere in 0.6.8, so it has no effect.
- Vanilla prefab pasting (`PrefabUtil.paste`) runs no connection logic. It writes the stored state ids as they are, and vanilla prefabs do store them: about 3,900 `_Corner_Left` entries in the first 3,000 prefab files.
- The bug has two causes. `HytaleBlueprintSource` turns `*..._Corner_Right` into the base (straight) block, and `setBlock(..., NONE)` never computes a shape. Breaking the block and placing it again goes through `BlockPlaceUtils`, which does compute one.
- **Recommendation: option (a).** Place the exact prefab variant, as vanilla paste does. Keep the variant id in both the blueprint and `get`, but only for connected-block *shape* states (see the predicate below).

## 1. How connected blocks work

`BlockType.getConnectedBlockRuleSet()` returns an abstract `connectedblocks.ConnectedBlockRuleSet`. `ConnectedBlocksModule` registers the `"Type"` values below.

| Type | Class | Used by (asset count) |
|---|---|---|
| `Stair` | `builtin.StairConnectedBlockRuleSet` (implements `StairLikeConnectedBlockRuleSet`) | all `*_Stairs` (163) |
| `Roof` | `builtin.RoofConnectedBlockRuleSet` (implements `StairLikeConnectedBlockRuleSet`) | all `*_Roof`, `_Roof_Shallow`, `_Roof_Steep` (241) |
| `CustomTemplate` | `CustomTemplateConnectedBlockRuleSet` plus a `Server/Item/CustomConnectedBlockTemplates/*.json` asset | fences, walls, fence gates, iron bars (`WallConnectedBlockTemplate`, 76), chests (`Chest…`: small to large), doors (`Door…`, `DoorLarge…`), `Village…`, bookshelf, rails |
| `Patterned` | `config.PatternedConnectedBlockRuleSet` | no item in 0.6.8 |

Stair asset (`Build_Black_Stairs.json`, the same for every stair):

```json
"State": { "Definitions": { "Corner_Left": {...}, "Corner_Right": {...},
                            "Inverted_Corner_Left": {...}, "Inverted_Corner_Right": {...} } },
"ConnectedBlockRuleSet": { "Type": "Stair",
  "Straight": {"State": "default"}, "Corner_Left": {"State": "Corner_Left"}, ... }
```

- Each state definition becomes its own BlockType with the key `"*" + baseKey + "_State_Definitions_" + stateName` (`StateData.generateBlockKey`). It has its own model and hitbox, and inherits the parent's fields, including the rule set.
- `ConnectedBlockOutput.resolve(BlockType, BlockTypeAssetMap)` maps `{State, Block}` to an asset index. `"default"` resolves to the base block. `Block` names a different base; roofs use it for `Wood_Softwood_Roof_Hollow`.
- Roof: `Regular` and `Hollow` each carry the 5 stair shapes, plus an optional `Topper` state. `Hollow` is chosen when the block below has no `UP` support face. `Topper` applies to a straight roof above a roof ridge.
- Fence and wall: `TemplateShapeBlockPatterns` maps shape names to block keys, for example `"Corner": "*Wood_Softwood_Fence_State_Definitions_Corner"`, plus `T_Junction`, `Cross_Junction`, `Gate`. The template also picks a **rotation** for the result.

**How the variant is chosen (stairs):** `StairConnectedBlockRuleSet.getConnectedBlockType` reads the block's own yaw and pitch. It looks at the stair **in front** (inverted corner) and the stair **behind** (corner), and at the one beside it to avoid false corners. Only same-Y neighbours count, and they must have the same `MaterialName` (`"Stair"`/`"Roof"`), the same pitch, and a perpendicular yaw. The result is a `ConnectedBlockResult(blockTypeKey, rotationIndex)`. For stairs and roofs the rotation is unchanged; only the BlockType (state) changes. The neighbours' current shapes feed into the decision (`getConnection` checks `otherStairType`), so the outcome can depend on placement order.

## 2. Vanilla placement path

```
PlaceBlockInteraction.tick0(...)                         // interaction/config/client/PlaceBlockInteraction.java:210
 -> BlockPlaceUtils.placeBlock(Ref<EntityStore>, ItemStack, String blockTypeKey, ItemContainer,
        Vector3i placementNormal, Vector3i blockPosition, BlockRotation, byte activeSlot, boolean removeItemInHand,
        Ref<ChunkStore> sectionRef, ComponentAccessor<ChunkStore>, ComponentAccessor<EntityStore>,
        boolean quickReplace, boolean quickRetype, boolean noPhysics)
  -> BlockPlaceUtils.tryPlaceBlock(...)                  // private
     -> BlockOperations.testPlaceBlock(...)
     -> BlockOperations.setBlock(cs, sec, x, y, z, id, type, rot, 0, noPhysics ? 0 : PERFORM_BLOCK_UPDATE)
     -> ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(cs, id, rotationTuple, placementNormal, pos, sec, blockSection)
```

`CarryPlaceBlockInteraction` also calls `placeBlock`. The removal path is `BlockHarvestUtils.naturallyRemoveBlock` -> `removeBlock` -> `setBlock(Empty)` -> `setConnectedBlockAndNotifyNeighbors(cs, index("Empty"), ...)`, which updates the neighbours only.

`ConnectedBlocksUtil` (`universe.world.connectedblocks`):

```java
public static void setConnectedBlockAndNotifyNeighbors(
    ChunkStore chunkStore, int blockTypeId, RotationTuple blockTypeRotation, Vector3ic placementNormal,
    Vector3ic blockPosition, Ref<ChunkStore> sectionRef, BlockSection blockSection)
public static Optional<ConnectedBlockResult> getDesiredConnectedBlockType(
    ChunkStore, Vector3ic coordinate, BlockType currentBlockType, int currentRotation,
    Vector3ic placementNormal, boolean isPlacement)
public static void notifyNeighborsAndCollectChanges(
    ChunkStore, Vector3ic origin, Map<Vector3i, ConnectedBlockResult> desiredChanges, Vector3ic placementNormal)
```

`setConnectedBlockAndNotifyNeighbors` does the following:
1. If the block has a rule set and is not a filler: `getDesiredConnectedBlockType(..., isPlacement=true)`. If the key or rotation differs, `setBlock(..., settings 132)`.
2. Unless the update mode is `IgnoreUpdates`: `updateNeighborsWithDepth`. This is a BFS over the 26 neighbours (3x3x3), depth < 3. Each neighbour with a rule set and `!onlyUpdateOnPlacement()` is re-evaluated with `isPlacement=false` and the zero normal, and rewritten if it changed. The cascade can reach blocks 3 away.

`BlockOperations.setBlock(ChunkStore, Ref<ChunkStore>, int x, int y, int z, int id, BlockType, int rotation, int filler, int settings)` handles heightmap, particles, block entity (state), fillers, and `PERFORM_BLOCK_UPDATE` (256). **It has no connected-block code.** Flag 128 is declared in `SetBlockSettings` but never tested; `ConnectedBlocksUtil` itself passes 132. Our `setBlock(..., NONE)` therefore updates **neither** the placed block **nor** its neighbours.

Side effect for us: `HytaleWorldBlocks.breakBlock` uses `naturallyRemoveBlock`, so the builder's *mining* does reshape neighbouring stairs and fences (vanilla behaviour).

## 3. Fix options

### (a) Place the exact prefab variant (recommended)

Vanilla pastes prefabs this way, so the result matches the prefab exactly and does not depend on placement order or on neighbours. No neighbour cascade touches player blocks next to the building. No new engine call is needed.

What we must not do is keep *every* `*` id. Doors, chests and fence gates inherit the rule set and also have interaction states (`OpenDoorIn`, `OpenWindow`, ...). Those must stay normalised, or a player opening a door would make the builder rebuild it. Keep a `*` id only when it is a **shape output** of its rule set:

```java
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.builtin.StairLikeConnectedBlockRuleSet;

/** The key the builder places and compares: a connected-block shape keeps its variant, other states are their base. */
static String blockKey(BlockType type) {
    String id = type.getId();
    String base = type.getDefaultStateKey();
    if (!id.startsWith("*") || base == null) return id;
    ConnectedBlockRuleSet rs = type.getConnectedBlockRuleSet();
    if (rs instanceof StairLikeConnectedBlockRuleSet) return id;  // stairs and roofs: every state is a shape (corners, Topper)
    if (rs instanceof CustomTemplateConnectedBlockRuleSet t
            && !t.getShapesForBlockType(BlockType.getAssetMap().getIndex(id)).isEmpty()) return id; // fence/wall Corner/T/Cross, Village
    return base;                                                  // door/chest/gate open states, etc.
}
```

Verified signatures: `StairLikeConnectedBlockRuleSet.getStairType(int)`, `CustomTemplateConnectedBlockRuleSet.getShapesForBlockType(int) -> Set<String>` (never null), `BlockType.getDefaultStateKey()`, `BlockType.getConnectedBlockRuleSet()`. Asset scan: every Stair and Roof item has only shape states (`Corner_*`, `Inverted_Corner_*`, `Topper`). Door, chest and gate states are not listed in `TemplateShapeBlockPatterns`, so `getShapesForBlockType` returns an empty set for them.

Changes, all in the adapter; core is untouched:
- `HytaleBlueprintSource` (the `id.startsWith("*")` branch around line 162) and `HytaleWorldBlocks.blockState` (line 280) both use `blockKey(type)`, so blueprint entries and world reads agree. `isDone`/`needsWork` keep plain `BlockState.equals`.
- `HytaleItemCatalog.computeBlock` already maps `*` ids to the base block's item, kind and gathering, so costs and requests don't change.
- `HytaleWorldBlocks.place` needs no change: `getIndex(key)` finds the variant, `testPlaceBlock` uses the variant's hitbox, and `setBlock(..., NONE)` writes it as it is. Bottom-up order doesn't matter because no evaluation happens.
- Prefab rotation: corner states are relative to the block's own yaw, and the buffer adds the hut yaw to each rotation, so rotated huts stay correct. There is no mirroring. Fence shapes carry their rotation in the prefab.

Known limit: if a player later places a stair next to a finished building, vanilla reshapes the builder's stair. Before the final check, the builder sees a mismatch and restores the prefab shape. After it, only air is refilled. That is acceptable, and matches vanilla prefab behaviour.

### (b) Call vanilla's resolver after `setBlock`

```java
// after BlockOperations.setBlock(...) in HytaleWorldBlocks.place; blocks = the section's BlockSection
ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(
        world.getChunkStore(), id, RotationTuple.get(state.rotation()),
        new Vector3i(),                                   // zero normal = "not placed against a face"
        new Vector3i(pos.x(), pos.y(), pos.z()), sec, blocks);
// then the success check must compare the base key: blocks.get(...) may now be a variant id != id
```

Convergence: each placement re-evaluates the placed block (it sees the neighbours already there) and all 26 neighbours (depth 3). So once the last neighbour is placed, every stair has been re-evaluated with the final layout, and bottom-up order is fine. Stair shapes use same-Y neighbours only. Roof `Hollow`/`Topper` look at the block below, which already exists.

Drawbacks compared with (a):
- The result is "what a player gets placing in this order", not the prefab. Stair and roof resolution reads the neighbours' current shapes, so it is order-dependent, and deliberate straight-beside-straight layouts in a prefab get auto-connected.
- It rewrites neighbours outside the plan (player blocks), up to 3 blocks away.
- It needs a zero normal. Door and rails templates use `OnlyOnPlacement` patterns, `PlacementNormal`, or `DontUpdateAfterInitialPlacement`, which expect a real face.
- `place()`'s `blocks.get(...) == id` check starts failing.
- It needs the same `get` normalisation it already has, so it cannot tell a wrong shape from a right one.

**Choose (a).** Use (b) only if the target were "vanilla-like auto shapes" rather than "prefab-exact".

## 4. Other families

| Family | Mechanism | Same bug? | (a) predicate covers it |
|---|---|---|---|
| Stairs | `Stair`, 4 corner states, rotation kept | yes | yes (StairLike) |
| Roofs (plain, shallow, steep, cloth) | `Roof`: regular and hollow (separate `*_Roof_Hollow` block) x 5 shapes, plus `Topper` | yes | yes (StairLike). Hollow corners are `*..._Roof_Hollow_State_Definitions_*` |
| Fences, walls, iron bars | `CustomTemplate` + `WallConnectedBlockTemplate`: `Corner`/`T`/`Cross` states, **rotation also chosen** | yes | yes (`getShapesForBlockType`) |
| Fence gates | Wall template `Gate` = base block; open and closed are door states | shape no; state normalised | gate states stay normalised |
| Chests | `Chest…` template: small to `*_Chest_Large` (a different BlockType, filler-based) | no: `*_Chest_Large` is a plain id, so it is already placed exactly | n/a |
| Doors | `Door…`/`DoorLarge…` (double doors, `DontUpdateAfterInitialPlacement`) | minor | open states stay normalised |
| Glass panes | none in 0.6.8 (windows are furniture, with no rule set) | n/a | n/a |
