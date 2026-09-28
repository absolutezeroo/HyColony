# Établi de l'architecte (Domum Ornamentum DO-2a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a player-built block, the architect's cutter, whose window crafts any Domum Ornamentum variant from 1 or 2 materials, creating the variant at runtime with the DO-1 engine.

**Architecture:** the core (`core/.../ornament/cutter`) holds the rules (groups, recipe check, quantities, window view and actions) as pure tested Java; the plugin (`plugin/.../ornament/cutter`) opens a custom page plus a vanilla container window on the block's 2-slot container, renders the core's view and orchestrates crafting (validate, request the variant off the world thread, re-validate, consume, give). The block itself is generated into the DO pack by `tools/domum`.

**Tech Stack:** Java 25, JUnit 5, Gson (core); Hytale server 0.6.8 API (plugin); Python 3 + Pillow (generator); Gradle.

**Spec:** `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do2-cutter-design.md` (read it first). DO-1 context: `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md`.

## Where this runs

- **Task 3 is LOCAL**: it needs the Hytale assets zip (OAuth-only, `%USERPROFILE%/.gradle/caches/hytale-assets/release-0.6.8-Assets.zip`) to run `tools/domum/generate.py` / `check.py`. It is done in the user's local session **before** the cloud hand-off; its commit is already on the branch when the cloud starts. A cloud session never edits `tools/domum` nor `plugin/src/subplugins/DomumOrnamentum`.
- **Tasks 1, 2, 4-7 run in the cloud**: they need only `./gradlew build` (the Hytale server jar resolves from Maven without auth). To read Hytale API sources, run `./gradlew decompileServerJar injectServerJavadocsIntoDecompiledSources` once: they land in `build/vineflower/hytale-server/`.
- **Never launch the Hytale server** (no `runServer`, no `HytaleServer.jar`): the user tests in game after the branch is done.

## Global Constraints

- Read `CLAUDE.md` fully before coding: core has no `com.hypixel` import; ≤ 400 lines per file (300 aimed), ≤ 40 lines per method, ≤ 5 params, ≤ 15 files per package; records for values; `Optional` never `null` returns; English identifiers and Javadoc on every class and non-trivial method; `./gradlew spotlessApply` before each commit; `./gradlew build` green before each commit (tests, spotless, PMD, file sizes).
- Commits: `type(module): description` (`feat(core):`, `feat(plugin):`, `docs:`…), explicit `git add <paths>` (never `-A`/`.`), end the message with the session's attribution line; never commit `config.json` / `config.json.bak`.
- Every player-visible text is a translation key present in both `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang` and `fr-FR/hycolony.lang`, params `{p0}`, `{p1}`; the file keys have no `hycolony.` prefix, the code sends `hycolony.<key>`.
- Every MC/DO system ported cites its source in Javadoc; every deviation has a `Deviation from MC: …` comment and a spec line.
- Every change is reviewed by an independent `hycolony-reviewer` agent before the next task (CLAUDE.md § 9.3).
- World thread only for game state; `OrnamentVariantRegistry.request` must never be awaited on a world thread.

## Existing code this plan builds on (DO-1, already on the branch)

- `core/src/main/java/dev/hycolony/core/ornament/`:
  - `OrnamentShape(String id, String templateKey, String group, List<String> slotTags, boolean optionalSecond, int cutterQuantity)`, `int slotCount()`;
  - `ShapeCatalog` (`parse(String)`, `Optional<OrnamentShape> shape(String id)` case-insensitive, `List<OrnamentShape> all()`, `skipped()`, `retain(Predicate)`);
  - `MaterialTags(Map<String, Set<String>> tags)` (`accepts(tag, blockId)`, `materials(tag)`);
  - `VariantKey(OrnamentShape shape, List<String> materials)` (`blockTypeKey()` = `<template>__<m1>[__<m2>]`, `id()`);
  - `VariantRequests.check(OrnamentShape, List<String> materials, MaterialTags)` → sealed `Result`: `Accepted(VariantKey key)` or `Refused(String reasonKey, int slot, Set<String> allowed)`; reason keys `hycolony.ornament.badCount` (slot -1) and `hycolony.ornament.badMaterial` (slot 0-based); an optional second slot left out repeats the first.
- `plugin/src/main/java/dev/hycolony/plugin/ornament/`:
  - `Ornaments.register(JavaPlugin plugin, IdMap ids)`: returns at once when `ids.ornamentTags()` is empty (DO pack off); builds `OrnamentVariantRegistry` and at `LoadAssetEvent` (priority `PRIORITY_LOAD_LATE`) calls `ornaments.start(new Catalogs(shapes, MaterialCatalog.load(tags)))`; registers `OrnamentCommand`.
  - `registry/OrnamentVariantRegistry`: `Optional<Catalogs> catalogs()` (empty before start), `boolean known(VariantKey)`, `CompletableFuture<List<OrnamentVariant>> request(List<VariantKey>)` (creates off-thread, 30 s timeout); `record Catalogs(ShapeCatalog shapes, MaterialCatalog materials)`.
  - `runtime/MaterialCatalog`: `MaterialTags tags()`, `Optional<String> texture(String blockId)`.
  - `api/OrnamentVariant(VariantKey key, int blockId)`.
  - `debug/VariantGift(PlayerRef player, Ref<EntityStore> ref)` gives 16 of a variant (pattern for giving: `SimpleItemContainer.addOrDropItemStack(store, ref, InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST), new ItemStack(itemId, count))`).
  - `debug/OrnamentCommand.say(PlayerRef, String key, String... params)` sends a translated message.
- UI patterns: `plugin/src/main/java/dev/hycolony/plugin/ui/ColonyPage.java` (an `InteractiveCustomUIPage<Act>` whose buttons send `Action` + `Index`; `bind(events, selector, action[, index])`; `rebuild()`), `TabBar.java` (tab row), `HutStockPanel.java` + `resources/Common/UI/Custom/Pages/HyColony/StockRow.ui` (an `ItemIcon #Icon` set by `ui.set(row + " #Icon.ItemId", itemId)`), `ui/PageEvents.guard` (never throw from a UI event). Copy these patterns; do not extend `ColonyPage` (it needs a `ColonyManager`).
- Block use pattern: `plugin/src/main/java/dev/hycolony/plugin/block/FlowerPotSystem.java` (`EntityEventSystem<EntityStore, UseBlockEvent.Pre>`, runs after `BlockUseProtectionSystem`, `event.getBlockType().getId()`, `event.getTargetBlock()`, `event.getContext()`); registered in `HyColonyPlugin`/`BlockSystems` with `getEntityStoreRegistry().registerSystem(...)`.

## Verified Hytale API (0.6.8, `build/vineflower/hytale-server/com/hypixel/hytale/`)

- Block container: `server/core/modules/block/components/ItemContainerBlock` (`Component<ChunkStore>`, `getComponentType()`, `SimpleItemContainer getItemContainer()`, `Map<UUID, ContainerBlockWindow> getWindows()`), declared in a block's JSON as `"BlockEntity": {"Components": {"ItemContainerBlock": {"Capacity": 2}}}` (vanilla chest `Furniture_Crude_Chest_Small`: `Capacity: 18`); its contents persist with the chunk and drop on break like a chest's.
- Opening it (copy `server/core/modules/interaction/interaction/config/server/OpenContainerInteraction.interactWithBlock`):
  ```java
  ChunkStore chunkStore = world.getChunkStore();
  Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
  Store<ChunkStore> chunks = chunkStore.getStore();
  Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(chunks, sectionRef, x, y, z);
  ItemContainerBlock box = chunks.getComponent(blockRef, ItemContainerBlock.getComponentType());
  BlockSection section = chunks.getComponent(sectionRef, BlockSection.getComponentType());
  int rotation = section.getRotationIndex(x, y, z);
  ContainerBlockWindow window = new ContainerBlockWindow(x, y, z, rotation, blockType, box.getItemContainer());
  box.getWindows().putIfAbsent(playerUuid, window) == null  // one window per player
  window.registerCloseEvent(e -> box.getWindows().remove(playerUuid, window));
  ```
- A custom page with windows: `PageManager.openCustomPageWithWindows(Ref<EntityStore> ref, Store<EntityStore> store, CustomUIPage page, Window... windows)` (`server/core/entity/entities/player/pages/PageManager.java:208`), from `player.getPageManager()`. **[in-game]** whether the client draws the container slots beside a custom page: the user checks it (first step of the new TESTING section); fallback in the Review Focus.
- A container's changes: `ItemContainer.registerChangeEvent(EventPriority, Consumer)` (used by `WindowManager.setWindow0`); `getItemStack(short slot)`, `removeItemStackFromSlot(short slot, int quantity)`-style methods: check `SimpleItemContainer` / `ItemContainer` in the decompiled sources for the exact names before use and note them in `docs/research/plugin-b-api.md`.
- Block use with no vanilla action: the block's JSON declares `"Interactions": {"Use": {"Interactions": [{"Type": "Simple"}]}}` (as the Decorations flower pots do) and a `UseBlockEvent.Pre` system does the work.

## Review Focus

1. The player changes or takes a material while a new variant is being created → nothing consumed, nothing given, a message (Task 6: re-validation test in `CutterCraftTest` + plugin flow).
2. Two players use the same cutter and both click Fabriquer on one set of materials → at most one craft consumes them (re-validation on the world thread before consuming; Task 6).
3. The client does not draw the container slots next to a custom page → the user reports it at the first step of the new TESTING section; fallback documented in the spec (page with slot buttons taking from the player's inventory), not built now.
4. The DO pack is off or the catalogs are not loaded yet (a cutter placed before a restart with the pack disabled) → using the block does nothing and logs once (Task 4).
5. An empty required slot, a material outside the tag, a 1-material shape with junk in slot 2 → refused or ignored without consuming anything (Task 1 tests).

---

### Task 1: core cutter recipe and catalog

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/ornament/cutter/CutterCatalog.java`, `CutterCraft.java`, `SlotContent.java`
- Test: `core/src/test/java/dev/hycolony/core/ornament/cutter/CutterCatalogTest.java`, `CutterCraftTest.java`

**Interfaces:**
- Consumes: `OrnamentShape`, `ShapeCatalog`, `MaterialTags`, `VariantKey`, `VariantRequests` (DO-1).
- Produces:
  - `public record SlotContent(String itemId, int quantity)` with `static SlotContent EMPTY` (`""`, 0) and `boolean isEmpty()`.
  - `public final class CutterCatalog`: `static CutterCatalog of(ShapeCatalog shapes)`, `List<String> groups()` (sorted by id, only groups with shapes), `List<OrnamentShape> shapes(String group)` (manifest order; empty for an unknown group).
  - `public final class CutterCraft`: `static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags)`; `public sealed interface Result permits Ready, Refused`; `record Ready(VariantKey key, int quantity, List<Integer> consumed)`; `record Refused(String reasonKey, int slot, Set<String> allowed)`; reason keys `hycolony.ornament.cutter.emptySlot` (slot = the empty one) or those of `VariantRequests`.

- [ ] **Step 1: write the failing tests**

```java
package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.ornament.ShapeCatalog;
import org.junit.jupiter.api.Test;

class CutterCatalogTest {
    private static final ShapeCatalog SHAPES = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [
              {"id": "Shingle", "template": "T1", "group": "cshingle", "slots": ["a", "b"], "cutterQuantity": 4},
              {"id": "TimberFrame_Plain", "template": "T2", "group": "btimberframe", "slots": ["a", "b"], "cutterQuantity": 4},
              {"id": "Shingle_Flat", "template": "T3", "group": "cshingle", "slots": ["a", "b"], "cutterQuantity": 4},
              {"id": "Fence", "template": "T4", "group": "avanilla", "slots": ["a"], "cutterQuantity": 1}
            ]}""");

    @Test
    void groupsFollowDosOrderAndKeepManifestOrderWithin() {
        CutterCatalog catalog = CutterCatalog.of(SHAPES);
        assertEquals(java.util.List.of("avanilla", "btimberframe", "cshingle"), catalog.groups());
        assertEquals(java.util.List.of("Shingle", "Shingle_Flat"),
                catalog.shapes("cshingle").stream().map(s -> s.id()).toList());
    }

    @Test
    void unknownGroupHasNoShapes() {
        assertTrue(CutterCatalog.of(SHAPES).shapes("ilight").isEmpty());
    }
}
```

```java
package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CutterCraftTest {
    private static final String STONE = "Rock_Stone_Brick";
    private static final String OAK = "Wood_Hardwood_Planks";
    private final OrnamentShape frame = new OrnamentShape(
            "TimberFrame_Plain", "HyColony_DO_TimberFrame_Plain", "btimberframe", List.of("frame", "centre"), false, 4);
    private final OrnamentShape paperWall = new OrnamentShape(
            "PaperWall", "HyColony_DO_PaperWall", "hpaperwall", List.of("frame", "centre"), false, 6);
    private final OrnamentShape fancyDoor = new OrnamentShape(
            "FancyDoor_Full", "HyColony_DO_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final OrnamentShape slab = new OrnamentShape("Slab", "HyColony_DO_Slab", "avanilla", List.of("slab"), false, 2);
    private final MaterialTags tags = new MaterialTags(Map.of(
            "frame", Set.of(OAK), "centre", Set.of(STONE), "fancy", Set.of(OAK), "slab", Set.of(STONE)));

    private static SlotContent one(String id) {
        return new SlotContent(id, 1);
    }

    @Test
    void validMaterialsGiveDosQuantityAndConsumeOneOfEachRequiredSlot() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), tags);
        assertEquals("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.key().blockTypeKey());
        assertEquals(4, ready.quantity());
        assertEquals(List.of(0, 1), ready.consumed());
        assertEquals(6, ((CutterCraft.Ready) CutterCraft.check(paperWall, List.of(one(OAK), one(STONE)), tags)).quantity());
    }

    @Test
    void emptyRequiredSlotIsRefusedAndNamed() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), tags);
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(1, refused.slot());
    }

    @Test
    void materialOutsideTheTagIsRefusedWithItsSlot() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(STONE), one(STONE)), tags);
        assertEquals("hycolony.ornament.badMaterial", refused.reasonKey());
        assertEquals(0, refused.slot());
        assertEquals(Set.of(OAK), refused.allowed());
    }

    @Test
    void emptyOptionalSecondRepeatsTheFirstAndConsumesOnlyIt() {
        var ready = (CutterCraft.Ready) CutterCraft.check(fancyDoor, List.of(one(OAK), SlotContent.EMPTY), tags);
        assertEquals(List.of(OAK, OAK), ready.key().materials());
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void secondSlotOfAOneMaterialShapeIsIgnoredAndKept() {
        var ready = (CutterCraft.Ready) CutterCraft.check(slab, List.of(one(STONE), one("Soil_Dirt")), tags);
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void reValidationOnChangedSlotsGivesAnotherAnswer() {
        var before = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), tags);
        var after = CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), tags);
        assertEquals(CutterCraft.Refused.class, after.getClass());
        assertEquals(4, before.quantity());
    }
}
```

- [ ] **Step 2: run** `./gradlew :core:test --tests '*cutter*'` → FAIL (classes missing).

- [ ] **Step 3: implement**

```java
package dev.hycolony.core.ornament.cutter;

/** One cutter slot: the item in it (empty id when none) and how many. */
public record SlotContent(String itemId, int quantity) {
    public static final SlotContent EMPTY = new SlotContent("", 0);

    /** True when the slot holds nothing. */
    public boolean isEmpty() {
        return itemId.isEmpty() || quantity <= 0;
    }
}
```

```java
package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.ShapeCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The cutter's groups and their shapes (MC DO ArchitectsCutterScreen: a row of groups, then the group's variants),
 * groups in DO's order (their ids sort as DO's: avanilla, btimberframe, cshingle...), shapes in manifest order.
 */
public final class CutterCatalog {
    private final Map<String, List<OrnamentShape>> byGroup;

    private CutterCatalog(Map<String, List<OrnamentShape>> byGroup) {
        this.byGroup = byGroup;
    }

    /** The groups having at least one shape in shapes. */
    public static CutterCatalog of(ShapeCatalog shapes) {
        Map<String, List<OrnamentShape>> groups = new TreeMap<>();
        for (OrnamentShape shape : shapes.all()) {
            groups.computeIfAbsent(shape.group(), g -> new ArrayList<>()).add(shape);
        }
        Map<String, List<OrnamentShape>> frozen = new LinkedHashMap<>();
        groups.forEach((group, list) -> frozen.put(group, List.copyOf(list)));
        return new CutterCatalog(frozen);
    }

    public List<String> groups() {
        return List.copyOf(byGroup.keySet());
    }

    /** group's shapes in manifest order; empty for an unknown group. */
    public List<OrnamentShape> shapes(String group) {
        return byGroup.getOrDefault(group, List.of());
    }
}
```

```java
package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.core.ornament.VariantRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The architect's cutter recipe (MC DO ArchitectsCutterRecipe): each of the shape's slots must hold a material of its
 * tag; crafting gives the shape's cutter quantity and takes 1 from each slot used (remove(1)). Slots past the shape's
 * material count are ignored and kept.
 */
public final class CutterCraft {
    static final String EMPTY_SLOT = "hycolony.ornament.cutter.emptySlot";

    private CutterCraft() {}

    /** What crafting shape from slots gives, or why it cannot. */
    public sealed interface Result permits Ready, Refused {}

    /** The variant, how many are given, and the slots losing 1 each. */
    public record Ready(VariantKey key, int quantity, List<Integer> consumed) implements Result {
        public Ready {
            consumed = List.copyOf(consumed);
        }
    }

    /** Refused: a translation key, the slot at fault (-1 when none), the materials that slot accepts. */
    public record Refused(String reasonKey, int slot, Set<String> allowed) implements Result {
        public Refused {
            allowed = Set.copyOf(allowed);
        }
    }

    /** Checks slots (the cutter's, in order) for shape against tags. */
    public static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags) {
        List<String> materials = new ArrayList<>();
        List<Integer> consumed = new ArrayList<>();
        for (int slot = 0; slot < shape.slotCount(); slot++) {
            SlotContent content = slot < slots.size() ? slots.get(slot) : SlotContent.EMPTY;
            boolean optional = shape.optionalSecond() && slot == shape.slotCount() - 1;
            if (content.isEmpty()) {
                if (optional) {
                    continue;
                }
                return new Refused(EMPTY_SLOT, slot, tags.materials(shape.slotTags().get(slot)));
            }
            materials.add(content.itemId());
            consumed.add(slot);
        }
        return switch (VariantRequests.check(shape, materials, tags)) {
            case VariantRequests.Accepted accepted -> new Ready(accepted.key(), shape.cutterQuantity(), consumed);
            case VariantRequests.Refused refused -> new Refused(refused.reasonKey(), refused.slot(), refused.allowed());
        };
    }
}
```

- [ ] **Step 4: run** `./gradlew spotlessApply` then `./gradlew build` → green (fix PMD findings by extracting helpers, never by adding to the allowlists).
- [ ] **Step 5: commit** `feat(core): Domum Ornamentum cutter recipe and groups` (`git add core/src/main/java/dev/hycolony/core/ornament/cutter core/src/test/java/dev/hycolony/core/ornament/cutter`).

### Task 2: core cutter view and actions

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/ornament/cutter/CutterView.java`, `CutterActions.java`
- Test: `core/src/test/java/dev/hycolony/core/ornament/cutter/CutterActionsTest.java`

**Interfaces:**
- Consumes: Task 1 (`CutterCatalog`, `CutterCraft`, `SlotContent`), `MaterialTags`.
- Produces:
  - `public record CutterView(List<Tab> tabs, List<ShapeButton> shapes, List<String> slotLabelKeys, Preview preview)`;
    `record Tab(String group, String nameKey, boolean selected)` (`nameKey` = `hycolony.ornament.cutter.group.<group>`);
    `record ShapeButton(String shapeId, String templateKey, boolean selected)` (the plugin shows the template item's icon and name);
    `sealed interface Preview permits Empty, Ready, Refused`: `record Empty()`, `record Ready(String itemId, boolean exists, int quantity)` (`itemId` = the variant key; `exists` filled by the plugin through `withExists`), `record Refused(String reasonKey, int slot)`.
    `slotLabelKeys`: one key per slot of the selected shape: `hycolony.ornament.cutter.slot.<tag>` (the DO tag, e.g. `timber_frames_frame`).
  - `public final class CutterActions`: `CutterActions(CutterCatalog catalog, MaterialTags tags)`; state `String group`, `String shapeId` (first group and its first shape at start); `void selectGroup(int index)` (selects its first shape; out of range ignored); `void selectShape(int index)` (out of range ignored); `Optional<OrnamentShape> shape()`; `CutterView view(List<SlotContent> slots)`.

- [ ] **Step 1: failing tests** (`CutterActionsTest`): the start view selects the first group and its first shape; `selectGroup(1)` changes tabs' `selected` and picks that group's first shape; an out-of-range index changes nothing; the preview is `Empty` with no material, `Ready` (itemId = expected variant key, quantity 4) with valid materials, `Refused` (reason and slot) with a bad one; `slotLabelKeys` has 2 keys for a 2-material shape and 1 for a 1-material one. Use the `CutterCatalogTest` manifest and `CutterCraftTest` tags as fixtures.
- [ ] **Step 2: run** → FAIL.
- [ ] **Step 3: implement** (preview: all slots of the selected shape empty → `Empty`; else `CutterCraft.check` → `Ready(key.blockTypeKey(), false, quantity)` or `Refused(reasonKey, slot)`; `Ready` has `Ready withExists(boolean)`).
- [ ] **Step 4: run** `./gradlew spotlessApply` then `./gradlew build` → green.
- [ ] **Step 5: commit** `feat(core): Domum Ornamentum cutter window view and actions`.

### Task 3 (LOCAL, before the cloud hand-off): the cutter block in the DO pack

**Files:** Create `tools/domum/blocks/cutter.py`; Modify `tools/domum/generate.py`, `tools/domum/check_pack.py`; regenerate `plugin/src/subplugins/DomumOrnamentum/`.

- Produces the item `HyColony_DO_ArchitectsCutter` (pack `Server/Item/Items/HyColony/DO/`): the vanilla builder's bench look (`CustomModel: Blocks/Benches/Builder.blockymodel`, `CustomModelTexture: Blocks/Benches/Builder_Texture.png`, `HitboxType: Bench_Architect`, `Icon: Icons/ItemsGenerated/Bench_Architects.png`, `IconProperties` of `Bench_Builders`), `VariantRotation: NESW`, `Material: Solid`, `DrawType: Model`, `Opacity: Transparent`, `Support.Down: Full`, stone sounds and particles, `Gathering.Breaking.GatherType: Benches`, `BlockEntity.Components.ItemContainerBlock.Capacity: 2`, `Interactions.Use: {"Interactions": [{"Type": "Simple"}]}`, `MaxStack: 1`, `Categories: ["Furniture.Benches"]`, name key `item.do.architectscutter.name` (« Architect's cutter » / « Établi de l'architecte »), and the recipe `{"Input": [{"ItemId": "Ingredient_Bar_Iron", "Quantity": 1}, {"ItemId": "Rock_Stone_Half", "Quantity": 3}, {"ResourceTypeId": "Wood_Trunk", "Quantity": 3}], "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}]}` (DO-gen `recipes/architectscutter.json`: 1 iron ingot, 3 stone slabs, 3 logs).
- Test in `check_pack.py`: the item exists with those keys, its recipe's inputs, its `ItemContainerBlock` capacity 2; `validate_pack` passes.
- Commit `feat(plugin): Domum Ornamentum architect's cutter block`.

### Task 4: plugin: opening the cutter

**Files:** Create `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterSystem.java`, `CutterOpener.java`; Modify `plugin/src/main/java/dev/hycolony/plugin/ornament/Ornaments.java` (register the system only when the pack is on); Modify `docs/research/plugin-b-api.md` (a § 26 with what was checked: `ItemContainerBlock`, `ContainerBlockWindow`, `openCustomPageWithWindows`, the container read/remove methods).

- `CutterSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre>`, after `BlockUseProtectionSystem` (as `FlowerPotSystem`): when `event.getBlockType().getId()` equals `"HyColony_DO_ArchitectsCutter"` and the event is not cancelled, calls `CutterOpener.open(...)`; catches `RuntimeException`, cancels the event and logs SEVERE (CLAUDE.md § 4). Before the catalogs are loaded (`registry.catalogs()` empty), it sends `hycolony.ornament.failed` with `load` and returns.
- `CutterOpener.open(World world, Ref<EntityStore> player, Vector3i pos, OrnamentVariantRegistry registry)`: reads the block's `ItemContainerBlock` (code in « Verified Hytale API »); a missing component logs a WARNING once, then FINE; builds a `ContainerBlockWindow` on its container (one per player: `putIfAbsent` in `getWindows()`, removed on close), a `CutterPage` (Task 5) and calls `openCustomPageWithWindows(ref, store, page, window)`.
- Build green; commit `feat(plugin): Domum Ornamentum architect's cutter opens its window`.

### Task 5: plugin: the cutter page

**Files:** Create `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterPage.java`, `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Cutter.ui`, `CutterShapeButton.ui`.

- `CutterPage extends InteractiveCustomUIPage<CutterPage.Act>` (copy `ColonyPage.Act` codec: `Action`, `Index`); holds `CutterActions`, the block's `ItemContainer` and the registry.
- `build`: appends `Pages/HyColony/Cutter.ui`; renders the tab row like `TabBar.render` (one `TabButton.ui` per group, label `nameKey`, the open one disabled, action `group`); one `CutterShapeButton.ui` per shape (an `ItemIcon #Icon` with `.ItemId` = `templateKey`, a `Button` with action `shape` + index, the selected one disabled); the slot labels; the preview (an `ItemIcon` with the variant key when `exists` — `Item.getAssetMap().getAsset(key) != null` — else the template key, a `Label` « × quantity », or the translated refusal with `{p0}` = slot number (1-based)); the « Fabriquer » button (action `craft`, disabled unless the preview is `Ready`).
- Slot contents: read the container's 2 slots into `List<SlotContent>` (item id and quantity; empty slot → `SlotContent.EMPTY`) on every build; register `itemContainer.registerChangeEvent(...)` on open (unregister on close) to `rebuild()` so the preview follows the slots.
- Events go through a `PageEvents.guard`-style wrapper: never throw; `group` → `selectGroup(index)`, `shape` → `selectShape(index)`, then `rebuild()`; `craft` → Task 6.
- Build green; commit `feat(plugin): Domum Ornamentum architect's cutter window`.

### Task 6: plugin: crafting

**Files:** Create `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterCrafting.java`; Modify `CutterPage.java`.

- `CutterCrafting.craft(World world, Ref<EntityStore> player, ItemContainer slots, OrnamentShape shape, OrnamentVariantRegistry registry, MaterialTags tags)` (5+ params: pass a `record CraftRequest(...)`):
  1. on the world thread: `CutterCraft.check(shape, read(slots), tags)`; `Refused` → message with its `reasonKey` ({p0} = slot number, 1-based; {p1} = up to 12 accepted materials for `badMaterial`), stop;
  2. `registry.request(List.of(ready.key()))`; never `join()`/`get()` on the world thread;
  3. `whenComplete` → `world.execute(() -> ...)`: on failure, message `hycolony.ornament.failed` (`create`), stop; if the player's `Ref` is no longer valid, stop (nothing consumed); `CutterCraft.check` again on the **current** slots: it must be `Ready` with the same `key().blockTypeKey()`, else message `hycolony.ornament.cutter.changed`, stop;
  4. remove 1 from each slot of `consumed` (verify the removal succeeded; if one fails, put back what was removed and stop), then give `quantity` of the variant with `SimpleItemContainer.addOrDropItemStack(...)` (pattern of `VariantGift`), message `hycolony.ornament.cutter.crafted` ({p0} quantity, {p1} item name via `Item.getTranslationMessage()`), `rebuild()` the page.
  A `world.execute` refused (world stopping) is logged SEVERE, nothing consumed.
- Build green; commit `feat(plugin): Domum Ornamentum architect's cutter crafts variants`.

### Task 7: texts and docs

**Files:** Modify `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang`, `fr-FR/hycolony.lang`, `docs/TESTING.md`, `docs/research/domum-ornamentum.md` (a « DO-2a » paragraph in the synthesis), the DO-2a spec (limits found).

- Keys (en-US / fr-FR, same params):
  - `ornament.cutter.title` = Architect's cutter / Établi de l'architecte;
  - `ornament.cutter.group.avanilla` = Vanilla / Vanilla; `.btimberframe` = Timber frames / Colombages; `.cshingle` = Shingles / Bardeaux; `.ddoor` = Doors / Portes; `.etrapdoor` = Trapdoors / Trappes; `.fpanel` = Panels / Panneaux; `.gpillar` = Pillars / Piliers; `.hpaperwall` = Framed panes / Vitres encadrées; `.kpost` = Posts / Poteaux;
  - `ornament.cutter.slot.<tag>` for the 18 DO tags of `tools/domum/tags.py` `TAG_GROUPS` (e.g. `timber_frames_frame` = Frame / Cadre, `timber_frames_center` = Centre / Centre, `shingles_roof` = Roof / Toit, `shingles_support` = Support / Support, `paper_wall_frame` = Frame / Cadre, `paper_wall_center` = Pane / Vitre, others = Material / Matériau);
  - `ornament.cutter.craft` = Craft / Fabriquer; `ornament.cutter.emptySlot` = Material {p0} is missing. / Il manque le matériau {p0}.; `ornament.cutter.changed` = The materials changed, nothing was crafted. / Les matériaux ont changé, rien n'a été fabriqué.; `ornament.cutter.crafted` = {p0} x {p1} crafted. / {p0} × {p1} fabriqué(s).
- Check both files have the same key set (`.claude/skills/add-lang-key` command).
- `docs/TESTING.md`: a « Établi de l'architecte (DO-2a) » section numbered from the last step + 1, with at least, first: the slots show beside the window (else stop and report: Review Focus 3); craft the cutter at the Workbench; place and orient it; craft a timber frame (4), a shingle (4), a paper wall (6), a fancy door with one material (2), a slab (2); refusal outside the tag and with an empty slot; change a material while a new pair is being created (nothing lost); two players on one cutter; break a full cutter (materials drop); full inventory; restart with materials inside; DO pack off.
- Build green; commit `docs: Domum Ornamentum architect's cutter in-game checks`.

After Task 7: a whole-branch `hycolony-reviewer` and an `mc-fidelity-checker` pass against DO's `ArchitectsCutterContainer`, `ArchitectsCutterRecipe` and `ArchitectsCutterScreen` at commit `82729d6c9dc0499b256b36b4506d0d9ef20e8aec`; their fixes are reviewed too. Then hand back to the user for the in-game test (never launch the server).
