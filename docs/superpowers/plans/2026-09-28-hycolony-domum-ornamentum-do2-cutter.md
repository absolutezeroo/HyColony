# Établi de l'architecte (Domum Ornamentum DO-2a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a player-built block, the architect's cutter, whose window crafts any Domum Ornamentum variant from 1 or 2 materials, creating the variant at runtime with the DO-1 engine.

**Architecture:** the core (`core/.../ornament/cutter`) holds the rules as pure tested Java: groups, recipe check, quantities, window view and actions. The plugin (`plugin/.../ornament/cutter`) opens a custom page plus a vanilla container window on the block's 2-slot container, renders the core's view, and orchestrates crafting: validate, request the variant off the world thread, re-validate, consume, give. The block itself is generated into the DO pack by `tools/domum`.

**Tech Stack:** Java 25, JUnit 5, Gson (core); Hytale server 0.6.8 API (plugin); Python 3 + Pillow (generator); Gradle.

**Spec:** `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do2-cutter-design.md` (read it first). DO-1 context: `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md`.

## Where this runs

- **Task 3 is LOCAL**: it needs the Hytale assets zip (OAuth-only, `%USERPROFILE%/.gradle/caches/hytale-assets/release-0.6.8-Assets.zip`) to run `tools/domum/generate.py` / `check.py`. The user's local session does it **before** the cloud hand-off; its commit is on the branch when the cloud starts. A cloud session never edits `tools/domum` nor `plugin/src/subplugins/DomumOrnamentum`.
- **Tasks 1, 2, 4-7 run in the cloud**: they need only `./gradlew build` (the Hytale server jar resolves from Maven without auth). To read Hytale API sources, run `./gradlew decompileServerJar injectServerJavadocsIntoDecompiledSources` once; they land in `build/vineflower/hytale-server/`. Every Hytale call below was checked there (0.6.8); if a signature differs, stop and report instead of guessing.
- **Cloud environment setup** (found on the first cloud run), in this order:
  - `git config core.hooksPath .githooks` (CLAUDE.md § 10, once per clone) ;
  - the network must allow `maven.hytale.com` (Hytale server jar) and `maven.azuredoom.com` (hytale-tools runtime); the hytale-tools plugin also adds other mod repositories (`HytaleRepositoryConfigurer`: `maven.hytale-mods.dev`, `maven.hytalemodding.dev`, `repo.helpch.at`, and a resolver on `api.modtale.net`); a blocked one may still fail a resolution. Without them `:plugin` neither compiles nor decompiles, and the `pre-push` hook (`./gradlew build`) refuses every push. **If `./gradlew decompileServerJar` cannot fetch the server jar, stop and tell the user**: never commit `plugin/` code that has not compiled, nor a Hytale call not checked in `build/vineflower` (CLAUDE.md § 1) ;
  - Gradle and the `hytale-tools` plugin need a Java 25 JVM, and foojay's downloads are blocked: `apt-get install -y openjdk-25-jdk-headless`, then `org.gradle.java.home=/usr/lib/jvm/java-25-openjdk-amd64` in `~/.gradle/gradle.properties` ;
  - `gradlew` is checked in without its executable bit (`100644`), so the hooks cannot run `./gradlew`: locally, `chmod +x gradlew` and `git config core.fileMode false`. The lasting fix, a separate `build: gradlew is executable` commit (`git update-index --chmod=+x gradlew`), is outside DO-2a: propose it to the user ;
  - Maven Central sometimes answers 429: run the command again.
- **Never launch the Hytale server** (no `runServer`, no `HytaleServer.jar`): the user tests in game once the branch is done.

## Global Constraints

- Read `CLAUDE.md` fully before coding. Core rules:
  - no `com.hypixel` import in `core/` ;
  - files: 300 lines aimed, 400 max; methods 40 lines max; 5 parameters max (beyond that, a record); 15 files max per package ;
  - records for values; `Optional`, never a returned `null` ;
  - English identifiers, and a short Javadoc on every class and non-trivial method.
- Build: `./gradlew spotlessApply`, then `./gradlew build` green before each commit (tests, spotless, PMD, file sizes). A PMD finding is fixed by extracting a helper, never by adding to `config/pmd/known-violations.txt` or the size allowlists (they only shrink).
- Commits: `type(module): description`, explicit `git add <paths>` (never `-A` or `.`), message ending with the session's attribution line. Never commit `config.json` or `config.json.bak`.
- Every player-visible text is a translation key present in both `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang` and `fr-FR/hycolony.lang`, with the same `{p0}`, `{p1}` params. File keys have no `hycolony.` prefix; the code sends `hycolony.<key>`.
- Every MC/DO system cites its source in Javadoc (DO: `ldtteam/Domum-Ornamentum`, commit `82729d6c9dc0499b256b36b4506d0d9ef20e8aec`). Every deviation has a `Deviation from MC: …` comment and a spec line.
- Every task is reviewed by an independent `hycolony-reviewer` agent before the next one, and its fixes are reviewed again (CLAUDE.md § 9.3).
- Game state lives on the world thread only. Never `join()`/`get()` a `CompletableFuture` there.

## Existing code this plan builds on (DO-1, on the branch)

- `core/src/main/java/dev/hycolony/core/ornament/`:
  - `record OrnamentShape(String id, String templateKey, String group, List<String> slotTags, boolean optionalSecond, int cutterQuantity)`, `int slotCount()` ;
  - `ShapeCatalog`: `static parse(String)`, `Optional<OrnamentShape> shape(String id)` (case ignored), `List<OrnamentShape> all()` ;
  - `record MaterialTags(Map<String, Set<String>> tags)`: `boolean accepts(tag, blockId)`, `Set<String> materials(tag)` ;
  - `record VariantKey(OrnamentShape shape, List<String> materials)`: `blockTypeKey()` (`<template>__<m1>[__<m2>]`, also the item id), `id()` ;
  - `VariantRequests.check(OrnamentShape, List<String> materials, MaterialTags)` returns a sealed `Result`:
    - `Accepted(VariantKey key)` ;
    - `Refused(String reasonKey, int slot, Set<String> allowed)`, with reason keys `hycolony.ornament.badCount` (slot -1, params {p0} shape id and {p1} count) and `hycolony.ornament.badMaterial` (0-based slot, params {p0} slot number and {p1} accepted materials) ;
    - an optional second slot left out repeats the first.
- `plugin/src/main/java/dev/hycolony/plugin/ornament/`:
  - `Ornaments.register(JavaPlugin plugin, IdMap ids)` (called from `HyColonyPlugin.setup`):
    - returns at once when `ids.ornamentTags()` is empty (DO pack off) ;
    - otherwise builds `OrnamentVariantRegistry ornaments`, registers `new OrnamentCommand(ornaments)`, and at `LoadAssetEvent` calls `ornaments.start(new Catalogs(shapes, MaterialCatalog.load(...)))`.
  - `registry/OrnamentVariantRegistry` and its `record Catalogs(ShapeCatalog shapes, MaterialCatalog materials)`:
    - `Optional<Catalogs> catalogs()`, empty before start ;
    - `CompletableFuture<List<OrnamentVariant>> request(List<VariantKey>)`, which creates off-thread with a 30 s timeout.
  - `runtime/MaterialCatalog`: `MaterialTags tags()`.
  - `api/OrnamentVariant(VariantKey key, int blockId)`.
- Messages to a player: `playerRef.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params...)))` (`dev.hycolony.plugin.adapter.HytaleNotifier`, `dev.hycolony.core.kernel.port.Msg`), as `ornament/debug/OrnamentCommand.say` does.
- Giving items (as `ornament/debug/VariantGift`): `SimpleItemContainer.addOrDropItemStack(store, ref, InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST), new ItemStack(itemId, count))`.
- UI patterns to imitate:
  - `plugin/src/main/java/dev/hycolony/plugin/ui/ColonyPage.java`: its `Act` codec (`Action`, `Index`) and its `bind(...)` helpers ;
  - `ui/PageEvents.guard` ;
  - `ui/TabBar.java`, with `Pages/HyColony/TabButton.ui` ;
  - `WandPage.ui`: a side `$C.@Container` that leaves room for the inventory, and `LayoutMode: LeftCenterWrap` for a wrapping row ;
  - `StockRow.ui` with `HutStockPanel` (`ItemIcon #Icon`, `ui.set(sel + ".ItemId", id)`).
- Block use pattern: `plugin/src/main/java/dev/hycolony/plugin/block/FlowerPotSystem.java`, an `EntityEventSystem<EntityStore, UseBlockEvent.Pre>` that runs after `BlockUseProtectionSystem` and does not cancel on success.

## Verified Hytale API (0.6.8, `build/vineflower/hytale-server/com/hypixel/hytale/`)

- `server/core/modules/block/components/ItemContainerBlock` (`Component<ChunkStore>`):
  - `static ComponentType<ChunkStore, ItemContainerBlock> getComponentType()` ;
  - `SimpleItemContainer getItemContainer()` ;
  - `Map<UUID, ContainerBlockWindow> getWindows()` ;
  - declared in a block JSON as `"BlockEntity": {"Components": {"ItemContainerBlock": {"Capacity": 2}}}`. Vanilla chests do it, and their contents persist and drop on break.
- `server/core/modules/block/BlockModule`: `@Nullable static Ref<ChunkStore> getBlockEntity(World world, int x, int y, int z)`.
- `server/core/universe/world/chunk/section/BlockSection`: `static getComponentType()`, `int getRotationIndex(int x, int y, int z)`. The section: `world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z)`, `world.getChunkStore().getStore()`.
- `server/core/entity/entities/player/windows/ContainerBlockWindow(int x, int y, int z, int rotationIndex, BlockType blockType, ItemContainer itemContainer)`, `registerCloseEvent(Consumer<...>)`. Usage model: `OpenContainerInteraction.interactWithBlock`, which keeps one window per player (`getWindows().putIfAbsent(uuid, window)`) and removes it on close.
- `server/core/entity/entities/player/pages/PageManager.openCustomPageWithWindows(Ref<EntityStore> ref, Store<EntityStore> store, CustomUIPage page, Window... windows)` returns `boolean`. It comes from `Player.getPageManager()`.
  - **[in-game]**: whether the client draws the container slots beside a custom page ; the user checks it first (Review Focus 3).
- `InteractiveCustomUIPage<T>(PlayerRef, CustomPageLifetime, BuilderCodec<T>)`: `build(Ref, UICommandBuilder, UIEventBuilder, Store)`, `handleDataEvent(Ref, Store, T)`, `rebuild()` (protected), `onDismiss(Ref, Store)`.
- `server/core/inventory/container/ItemContainer`:
  - `ItemStack getItemStack(short slot)` (null or empty when none) ;
  - `ItemStackSlotTransaction removeItemStackFromSlot(short slot, int quantityToRemove)` and `addItemStackToSlot(short slot, ItemStack)` (both `.succeeded()`) ;
  - `EventRegistration<...> registerChangeEvent(Consumer<ItemContainer.ItemContainerChangeEvent>)`, stopped with `registration.unregister()` (`com.hypixel.hytale.registry.Registration`).
- `server/core/inventory/ItemStack`: `getItemId()`, `getQuantity()`, `static boolean isEmpty(@Nullable ItemStack)`, `new ItemStack(String itemId, int quantity)`.
- `server/core/event/events/ecs/UseBlockEvent.Pre`: `getTargetBlock()` (`org.joml.Vector3i`), `getBlockType()`, `getContext()`, `isCancelled()`.

## Review Focus

1. **The player changes or takes a material while a new variant is being created.** Nothing is consumed, nothing given, and a message is shown. Pinned by `CutterCraftTest.reValidationOnChangedSlotsGivesAnotherAnswer` (Task 1) and the step 3 re-check of `CutterCrafting` (Task 6).
2. **Two players click Fabriquer on one set of materials.** At most one craft consumes it: every consume re-validates on the world thread, then checks each removal's `succeeded()` (Task 6).
3. **The client does not draw the container slots next to a custom page.** The user reports it at the first step of the new TESTING section. The fallback (a page with slot buttons taking materials from the player's inventory) goes to the spec, not built now.
4. **The DO pack is off, or the catalogs are not loaded yet.** Using the block sends `hycolony.ornament.failed` (`load`) and nothing else (Task 4).
5. **Bad slot contents.** An empty required slot or a material outside the tag is refused, and nothing is consumed; junk in slot 2 of a 1-material shape is ignored and kept (Task 1 tests).

## Amendments from the Task 1-2 reviews (apply them in Tasks 4-7)

Tasks 1 and 2 are done and reviewed on the branch; their committed code differs from the code blocks below, and **the committed code wins**:
- `CutterCatalog` orders groups and shapes by DO's `SortedBlocks` indexes (`CutterOrder`), not by id: tabs are avanilla, btimberframe, cshingle, etrapdoor, ddoor, fpanel, hpaperwall, gpillar, kpost.
- `CutterCraft` gives `max(slotCount, cutterQuantity)` (DO `ArchitectsCutterRecipe.assemble`).
- `CutterActions` previews a `hycolony.ornament.badMaterial` refusal under the key `hycolony.ornament.cutter.badMaterial` (same params, no `[hyornament]` prefix). It has a public `int group()`: the open group's index.

What Tasks 4-7 must add:
- **Tasks 4-5, last group remembered (MC DO `ArchitectsCutterScreen.groupIndexCache`)**:
  - DO: the cutter reopens on the player's last group, with that group's first shape: `renderBg` (l.94-98) replays `clickMenuButton(groupIndexCache)`, which takes variant `get(0)` (`ArchitectsCutterContainer` l.201). `variantIndexCache` (Screen l.71, l.100-107, l.349) is then never replayed, since the variant is already set: **do not remember the shape** (commit `85240b2` removed that on purpose). The cache is a client static: per player, for every cutter, until the client quits.
  - Here: a `CutterGroupMemory` (plugin, `ornament/cutter`, a role name, not `*Manager`) holds a `ConcurrentHashMap<UUID, Integer>` (worlds have their own threads), in memory only. `Ornaments.register` creates it, forgets a player on `PlayerDisconnectEvent` (`e.getPlayerRef().getUuid()`, as `HyColonyPlugin.onDisconnect`), and hands it to `new CutterSystem(ornaments, memory)`, then to `CutterOpener.open(...)` (5 parameters at most) and to `CutterPage`. Its Javadoc carries `Deviation from MC: forgotten when the player disconnects; DO's client static lives until the client quits, which the server cannot see.`
  - `CutterPage`'s constructor would reach 6 parameters: it takes `PlayerRef` and a record `CutterPage.Setup(World world, ItemContainer slots, OrnamentVariantRegistry registry, OrnamentVariantRegistry.Catalogs catalogs, CutterGroupMemory memory)` (CLAUDE.md § 2).
  - On opening: `actions.selectGroup(memory.group(uuid))` (0 when unknown); after each `group` action: `memory.remember(uuid, actions.group())`.
- **Task 6, creative mode (open question for the user, ask before Task 6)**: DO takes nothing in creative mode (`ArchitectsCutterContainer.onTake`, l.152, `!thePlayer.isCreative()`).
  - If ported, the rule lives in the core, test first: e.g. `CutterCraft.check(shape, slots, tags, boolean creative)` gives an empty `consumed` for a creative player (test `creativePlayerCraftsWithoutConsumingTheMaterials`). The plugin only reads the game mode, as `adapter/HytalePlayerDirectory` does (`player.getGameMode() == GameMode.Creative`), and Task 7 gains a TESTING step « en créatif, rien n'est retiré ».
  - If not, it goes under the spec's « Écarts avec DO ».
- **Task 7, key**: `ornament.cutter.badMaterial` is now in the table below.
- **Task 7, TESTING step 3** gains: « Choisir l'onglet Bardeaux et une autre forme que la première, fermer, rouvrir, puis ouvrir un autre établi : l'onglet Bardeaux est ouvert, sur sa première forme. Se déconnecter et revenir : l'établi rouvre sur Vanilla. »
- **Task 7, spec** (written in French, as the spec):
  - « Écarts avec DO » gains:
    - the preview explains a refusal: DO's `mayPlace` (l.114-124) keeps a block outside the tag out of the slot, and its output stays empty;
    - the materials stay in the block, persist and are shared: DO's cutter has no block entity, and its slots are per player and given back on close (`ArchitectsCutterContainer.removed` → `clearContainer`, l.362-366);
    - creative mode, unless ported;
    - the last group is forgotten when the player disconnects; DO keeps it until the client quits.
  - § En jeu: the window reopens on the player's last group, on its first shape, as DO; the memory is forgotten on disconnect (a deviation, above).
  - § Architecture: `ShapeButton(shapeId, templateKey, selected)` (not a name key nor an icon path), and `CutterActions.group()` with the per-player `CutterGroupMemory`.

---

### Task 1: core recipe and groups

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/ornament/cutter/SlotContent.java`, `CutterCatalog.java`, `CutterCraft.java`
- Test: `core/src/test/java/dev/hycolony/core/ornament/cutter/CutterCatalogTest.java`, `CutterCraftTest.java`

**Interfaces:**
- Consumes: `OrnamentShape`, `ShapeCatalog`, `MaterialTags`, `VariantKey`, `VariantRequests` (DO-1).
- Produces: `SlotContent(String itemId, int quantity)` with `EMPTY` and `isEmpty()`; `CutterCatalog.of(ShapeCatalog)`, `groups()`, `shapes(String group)`; `CutterCraft.check(OrnamentShape, List<SlotContent>, MaterialTags)` returning `Ready(VariantKey key, int quantity, List<Integer> consumed)` or `Refused(String reasonKey, int slot, Set<String> allowed)`; reason key `hycolony.ornament.cutter.emptySlot`.

- [ ] **Step 1: write the failing tests**

`CutterCatalogTest.java`:
```java
package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.ShapeCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class CutterCatalogTest {
    static final ShapeCatalog SHAPES = ShapeCatalog.parse("""
            {"schemaVersion": 1, "shapes": [
              {"id": "Shingle", "template": "HyColony_DO_Shingle", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "TimberFrame_Plain", "template": "HyColony_DO_TimberFrame_Plain", "group": "btimberframe",
               "slots": ["timber_frames_frame", "timber_frames_center"], "cutterQuantity": 4},
              {"id": "Shingle_Flat", "template": "HyColony_DO_Shingle_Flat", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "cutterQuantity": 4},
              {"id": "Slab", "template": "HyColony_DO_Slab", "group": "avanilla",
               "slots": ["slab_materials"], "cutterQuantity": 2}
            ]}""");

    @Test
    void groupsFollowDosOrderAndKeepManifestOrderWithin() {
        CutterCatalog catalog = CutterCatalog.of(SHAPES);
        assertEquals(List.of("avanilla", "btimberframe", "cshingle"), catalog.groups());
        assertEquals(
                List.of("Shingle", "Shingle_Flat"),
                catalog.shapes("cshingle").stream().map(OrnamentShape::id).toList());
    }

    @Test
    void unknownGroupHasNoShapes() {
        assertTrue(CutterCatalog.of(SHAPES).shapes("ilight").isEmpty());
    }
}
```

`CutterCraftTest.java`:
```java
package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CutterCraftTest {
    static final String STONE = "Rock_Stone_Brick";
    static final String OAK = "Wood_Hardwood_Planks";
    static final MaterialTags TAGS = new MaterialTags(Map.of(
            "frame", Set.of(OAK), "centre", Set.of(STONE), "fancy", Set.of(OAK), "slab", Set.of(STONE)));
    private final OrnamentShape frame = new OrnamentShape(
            "TimberFrame_Plain", "HyColony_DO_TimberFrame_Plain", "btimberframe", List.of("frame", "centre"), false, 4);
    private final OrnamentShape paperWall =
            new OrnamentShape("PaperWall", "HyColony_DO_PaperWall", "hpaperwall", List.of("frame", "centre"), false, 6);
    private final OrnamentShape fancyDoor = new OrnamentShape(
            "FancyDoor_Full", "HyColony_DO_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final OrnamentShape slab =
            new OrnamentShape("Slab", "HyColony_DO_Slab", "avanilla", List.of("slab"), false, 2);

    static SlotContent one(String id) {
        return new SlotContent(id, 1);
    }

    @Test
    void validMaterialsGiveDosQuantityAndConsumeOneOfEachRequiredSlot() {
        var ready = (CutterCraft.Ready) CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(
                "HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.key().blockTypeKey());
        assertEquals(4, ready.quantity());
        assertEquals(List.of(0, 1), ready.consumed());
        var wall = (CutterCraft.Ready) CutterCraft.check(paperWall, List.of(one(OAK), one(STONE)), TAGS);
        assertEquals(6, wall.quantity());
    }

    @Test
    void emptyRequiredSlotIsRefusedAndNamed() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS);
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(1, refused.slot());
        assertEquals(Set.of(STONE), refused.allowed());
    }

    @Test
    void materialOutsideTheTagIsRefusedWithItsSlot() {
        var refused = (CutterCraft.Refused) CutterCraft.check(frame, List.of(one(STONE), one(STONE)), TAGS);
        assertEquals("hycolony.ornament.badMaterial", refused.reasonKey());
        assertEquals(0, refused.slot());
        assertEquals(Set.of(OAK), refused.allowed());
    }

    @Test
    void emptyOptionalSecondRepeatsTheFirstAndConsumesOnlyIt() {
        var ready = (CutterCraft.Ready) CutterCraft.check(fancyDoor, List.of(one(OAK), SlotContent.EMPTY), TAGS);
        assertEquals(List.of(OAK, OAK), ready.key().materials());
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void secondSlotOfAOneMaterialShapeIsIgnoredAndKept() {
        var ready = (CutterCraft.Ready) CutterCraft.check(slab, List.of(one(STONE), one("Soil_Dirt")), TAGS);
        assertEquals(List.of(0), ready.consumed());
        assertEquals(2, ready.quantity());
    }

    @Test
    void missingSlotsCountAsEmpty() {
        assertInstanceOf(CutterCraft.Refused.class, CutterCraft.check(frame, List.of(one(OAK)), TAGS));
    }

    @Test
    void reValidationOnChangedSlotsGivesAnotherAnswer() {
        assertInstanceOf(CutterCraft.Ready.class, CutterCraft.check(frame, List.of(one(OAK), one(STONE)), TAGS));
        assertInstanceOf(
                CutterCraft.Refused.class, CutterCraft.check(frame, List.of(one(OAK), SlotContent.EMPTY), TAGS));
    }
}
```

- [ ] **Step 2: run** `./gradlew :core:test --tests '*ornament.cutter*'` → FAIL (classes missing).

- [ ] **Step 3: implement**

`SlotContent.java`:
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

`CutterCatalog.java`:
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
 * The cutter's groups and their shapes (MC DO ArchitectsCutterScreen: a row of groups, then the group's variants).
 * Groups sort by id, which is DO's order (avanilla, btimberframe, cshingle...); shapes keep the manifest's order.
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

`CutterCraft.java`:
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
 * The architect's cutter recipe (MC DO ArchitectsCutterRecipe): each slot of the shape must hold a material of its
 * tag; crafting gives the shape's cutter quantity and takes 1 from each slot used (remove(1)). Slots past the shape's
 * material count are ignored and kept.
 *
 * <p>Deviation from MC: an empty optional second slot is accepted and repeats the first (DO-1 VariantRequests); DO's
 * matches() refuses it.
 */
public final class CutterCraft {
    static final String EMPTY_SLOT = "hycolony.ornament.cutter.emptySlot";

    private CutterCraft() {}

    /** What crafting a shape from the slots gives, or why it cannot. */
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

    /** Checks slots (the cutter's, in order; missing ones count as empty) for shape against tags. */
    public static Result check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags) {
        List<String> materials = new ArrayList<>();
        List<Integer> consumed = new ArrayList<>();
        for (int slot = 0; slot < shape.slotCount(); slot++) {
            SlotContent content = slot < slots.size() ? slots.get(slot) : SlotContent.EMPTY;
            if (content.isEmpty()) {
                if (shape.optionalSecond() && slot == shape.slotCount() - 1) {
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

- [ ] **Step 4: run** `./gradlew spotlessApply` then `./gradlew build` → green.
- [ ] **Step 5: commit**
```bash
git add core/src/main/java/dev/hycolony/core/ornament/cutter core/src/test/java/dev/hycolony/core/ornament/cutter
git commit -m "feat(core): Domum Ornamentum cutter recipe and groups"
```

### Task 2: core window view and actions

**Files:**
- Create: `core/src/main/java/dev/hycolony/core/ornament/cutter/CutterView.java`, `CutterActions.java`
- Test: `core/src/test/java/dev/hycolony/core/ornament/cutter/CutterActionsTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces: `CutterView(List<Tab> tabs, List<ShapeButton> shapes, List<String> slotLabelKeys, Preview preview)` with `Tab(String group, String nameKey, boolean selected)`, `ShapeButton(String shapeId, String templateKey, boolean selected)`, `sealed Preview permits Empty, Ready, Refused` (`Empty()`, `Ready(String itemId, String templateKey, int quantity)`, `Refused(String reasonKey, List<String> params)`); `CutterActions(CutterCatalog, MaterialTags)`: `selectGroup(int)`, `selectShape(int)`, `Optional<OrnamentShape> shape()`, `CutterView view(List<SlotContent>)`.

- [ ] **Step 1: write the failing test**
```java
package dev.hycolony.core.ornament.cutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CutterActionsTest {
    private final CutterActions actions =
            new CutterActions(CutterCatalog.of(CutterCatalogTest.SHAPES), CutterCraftTest.TAGS);

    @Test
    void startsOnTheFirstGroupAndItsFirstShape() {
        CutterView view = actions.view(List.of());
        assertEquals(List.of(true, false, false), view.tabs().stream().map(CutterView.Tab::selected).toList());
        assertEquals("hycolony.ornament.cutter.group.avanilla", view.tabs().getFirst().nameKey());
        assertEquals("Slab", view.shapes().getFirst().shapeId());
        assertTrue(view.shapes().getFirst().selected());
        assertEquals(List.of("hycolony.ornament.cutter.slot.slab_materials"), view.slotLabelKeys());
        assertInstanceOf(CutterView.Empty.class, view.preview());
    }

    @Test
    void selectingAGroupPicksItsFirstShapeAndBadIndexesChangeNothing() {
        actions.selectGroup(2);
        actions.selectShape(1);
        assertEquals("Shingle_Flat", actions.shape().orElseThrow().id());
        actions.selectGroup(1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        actions.selectGroup(9);
        actions.selectShape(-1);
        assertEquals("TimberFrame_Plain", actions.shape().orElseThrow().id());
        assertEquals(2, actions.view(List.of()).slotLabelKeys().size());
    }

    @Test
    void previewShowsTheVariantOrWhyNot() {
        actions.selectGroup(1);
        var ready = (CutterView.Ready) actions.view(List.of(
                        CutterCraftTest.one(CutterCraftTest.OAK), CutterCraftTest.one(CutterCraftTest.STONE)))
                .preview();
        assertEquals("HyColony_DO_TimberFrame_Plain__Wood_Hardwood_Planks__Rock_Stone_Brick", ready.itemId());
        assertEquals("HyColony_DO_TimberFrame_Plain", ready.templateKey());
        assertEquals(4, ready.quantity());
        var refused = (CutterView.Refused) actions.view(List.of(
                        CutterCraftTest.one(CutterCraftTest.OAK), SlotContent.EMPTY))
                .preview();
        assertEquals("hycolony.ornament.cutter.emptySlot", refused.reasonKey());
        assertEquals(List.of("2"), refused.params());
    }
}
```

- [ ] **Step 2: run** `./gradlew :core:test --tests '*CutterActionsTest'` → FAIL.

- [ ] **Step 3: implement**

`CutterView.java`:
```java
package dev.hycolony.core.ornament.cutter;

import java.util.List;

/**
 * What the cutter window shows (MC DO ArchitectsCutterScreen): the group tabs, the group's shapes, a label per
 * material slot of the chosen shape and the preview of what crafting gives.
 */
public record CutterView(List<Tab> tabs, List<ShapeButton> shapes, List<String> slotLabelKeys, Preview preview) {
    public CutterView {
        tabs = List.copyOf(tabs);
        shapes = List.copyOf(shapes);
        slotLabelKeys = List.copyOf(slotLabelKeys);
    }

    /** A group tab: its id, its name key, whether it is open. */
    public record Tab(String group, String nameKey, boolean selected) {}

    /** A shape button: shown with its template item's icon and name. */
    public record ShapeButton(String shapeId, String templateKey, boolean selected) {}

    /** What crafting gives now. */
    public sealed interface Preview permits Empty, Ready, Refused {}

    /** No material placed. */
    public record Empty() implements Preview {}

    /** The variant item id (it may not exist yet: show the template's icon then) and how many a craft gives. */
    public record Ready(String itemId, String templateKey, int quantity) implements Preview {}

    /** Why crafting is refused: a translation key and its parameters. */
    public record Refused(String reasonKey, List<String> params) implements Preview {
        public Refused {
            params = List.copyOf(params);
        }
    }
}
```

`CutterActions.java`:
```java
package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One player's cutter window state: the open group and the chosen shape (MC DO ArchitectsCutterScreen
 * clickMenuButton), and the view they give with the slots' contents. Opening a group chooses its first shape.
 */
public final class CutterActions {
    /** Accepted materials listed at most in a refusal: a stone tag holds hundreds. */
    static final int LISTED = 12;

    private final CutterCatalog catalog;
    private final MaterialTags tags;
    private int group;
    private int shape;

    public CutterActions(CutterCatalog catalog, MaterialTags tags) {
        this.catalog = catalog;
        this.tags = tags;
    }

    /** Opens the group at index (and its first shape); an index out of range changes nothing. */
    public void selectGroup(int index) {
        if (index >= 0 && index < catalog.groups().size()) {
            group = index;
            shape = 0;
        }
    }

    /** Chooses the open group's shape at index; an index out of range changes nothing. */
    public void selectShape(int index) {
        if (index >= 0 && index < shapes().size()) {
            shape = index;
        }
    }

    /** The chosen shape; empty when the catalog has none. */
    public Optional<OrnamentShape> shape() {
        List<OrnamentShape> shapes = shapes();
        return shape < shapes.size() ? Optional.of(shapes.get(shape)) : Optional.empty();
    }

    /** The window for these slot contents. */
    public CutterView view(List<SlotContent> slots) {
        List<String> groups = catalog.groups();
        List<CutterView.Tab> tabs = IntStream.range(0, groups.size())
                .mapToObj(i -> new CutterView.Tab(
                        groups.get(i), "hycolony.ornament.cutter.group." + groups.get(i), i == group))
                .toList();
        List<OrnamentShape> shapes = shapes();
        List<CutterView.ShapeButton> buttons = IntStream.range(0, shapes.size())
                .mapToObj(i -> new CutterView.ShapeButton(shapes.get(i).id(), shapes.get(i).templateKey(), i == shape))
                .toList();
        List<String> labels = shape().map(s -> s.slotTags().stream()
                        .map(tag -> "hycolony.ornament.cutter.slot." + tag)
                        .toList())
                .orElse(List.of());
        return new CutterView(tabs, buttons, labels, shape().map(s -> preview(s, slots)).orElse(new CutterView.Empty()));
    }

    private List<OrnamentShape> shapes() {
        List<String> groups = catalog.groups();
        return groups.isEmpty() ? List.of() : catalog.shapes(groups.get(group));
    }

    /** Empty while the shape's slots are all empty, else the recipe's answer. */
    private CutterView.Preview preview(OrnamentShape shape, List<SlotContent> slots) {
        boolean empty = IntStream.range(0, shape.slotCount())
                .allMatch(i -> i >= slots.size() || slots.get(i).isEmpty());
        if (empty) {
            return new CutterView.Empty();
        }
        return switch (CutterCraft.check(shape, slots, tags)) {
            case CutterCraft.Ready ready -> new CutterView.Ready(
                    ready.key().blockTypeKey(), shape.templateKey(), ready.quantity());
            case CutterCraft.Refused refused -> new CutterView.Refused(refused.reasonKey(), params(shape, refused));
        };
    }

    /** A refusal's message parameters: the slot number (1-based) and some accepted materials, or the count. */
    private static List<String> params(OrnamentShape shape, CutterCraft.Refused refused) {
        if (refused.slot() < 0) {
            return List.of(shape.id(), String.valueOf(shape.slotCount()));
        }
        List<String> params = new ArrayList<>(List.of(String.valueOf(refused.slot() + 1)));
        // Only badMaterial's message lists accepted materials ({p1}); emptySlot's takes the slot only.
        if (refused.reasonKey().equals("hycolony.ornament.badMaterial")) {
            List<String> sorted = refused.allowed().stream().sorted().toList();
            String listed = String.join(", ", sorted.subList(0, Math.min(LISTED, sorted.size())));
            params.add(sorted.size() > LISTED ? listed + ", ..." : listed);
        }
        return params;
    }
}
```

- [ ] **Step 4: run** `./gradlew spotlessApply` then `./gradlew build` → green.
- [ ] **Step 5: commit**
```bash
git add core/src/main/java/dev/hycolony/core/ornament/cutter core/src/test/java/dev/hycolony/core/ornament/cutter
git commit -m "feat(core): Domum Ornamentum cutter window view and actions"
```

### Task 3 (LOCAL, before the cloud hand-off): the cutter block in the DO pack

**Files:**
- Create: `tools/domum/blocks/cutter.py`
- Modify: `tools/domum/generate.py` (call `cutter.generate(ctx)` after the families), `tools/domum/check_pack.py` (a test + its call in `run()`)
- Regenerate: `plugin/src/subplugins/DomumOrnamentum/`

- [ ] **Step 1: write the failing test** in `check_pack.py` (and call it from `run()`):
```python
def cutter_is_a_two_slot_bench():
    """The architect's cutter: the vanilla builder's bench look, 2 container slots, a plain Use the plugin handles,
    crafted at the Workbench from DO's recipe (1 iron ingot, 3 stone slabs, 3 logs)."""
    ctx = generate_into_temp()
    path = ctx.pack / "Server/Item/Items/HyColony/DO/HyColony_DO_ArchitectsCutter.json"
    item = json.loads(path.read_text(encoding="utf-8"))
    block = item["BlockType"]
    assert block["BlockEntity"]["Components"]["ItemContainerBlock"]["Capacity"] == 2
    assert block["Interactions"]["Use"] == {"Interactions": [{"Type": "Simple"}]}
    assert block["CustomModel"] == "Blocks/Benches/Builder.blockymodel"
    inputs = {i.get("ItemId") or i["ResourceTypeId"]: i["Quantity"] for i in item["Recipe"]["Input"]}
    assert inputs == {"Ingredient_Bar_Iron": 1, "Rock_Stone_Half": 3, "Wood_Trunk": 3}
    assert item["Recipe"]["BenchRequirement"][0]["Id"] == "Workbench"
```
- [ ] **Step 2: run** `python tools/domum/check.py` → FAIL (file missing).
- [ ] **Step 3: implement** `tools/domum/blocks/cutter.py`:
```python
"""The architect's cutter block (MC DO ArchitectsCutterBlock): a bench whose 2 container slots hold the materials
the plugin's cutter window crafts with. It wears the vanilla builder's bench, Hytale's own architect bench.

Deviation from MC: DO's recipe (1 iron ingot, 3 stone slabs, 3 logs; DO-gen recipes/architectscutter.json) is made
at Hytale's Workbench, logs as any trunk (Wood_Trunk resource type)."""

from blocks import common
from pack import write_json

IDENT = "HyColony_DO_ArchitectsCutter"
NAME_KEY = "item.do.architectscutter.name"
NAMES = {"en-US": "Architect's cutter", "fr-FR": "Établi de l'architecte"}


def generate(ctx):
    """Writes the cutter item and its names."""
    bench = ctx.assets.item("Bench_Builders")
    block = {key: bench["BlockType"][key] for key in (
        "Material", "DrawType", "Opacity", "CustomModel", "CustomModelTexture", "HitboxType", "VariantRotation",
        "Gathering", "BlockParticleSetId", "BlockSoundSetId", "PhysicalMaterialId", "Support")}
    block["BlockEntity"] = {"Components": {"ItemContainerBlock": {"Capacity": 2}}}
    block["Interactions"] = {"Use": {"Interactions": [{"Type": "Simple"}]}}
    item = {
        "TranslationProperties": {"Name": "hycolony." + NAME_KEY},
        "Icon": bench["Icon"],
        "IconProperties": bench["IconProperties"],
        "Categories": ["Furniture.Benches"],
        "PlayerAnimationsId": "Block",
        "MaxStack": 1,
        "ItemSoundSetId": bench.get("ItemSoundSetId", "ISS_Blocks_Wood"),
        "Recipe": {
            "Input": [{"ItemId": "Ingredient_Bar_Iron", "Quantity": 1}, {"ItemId": "Rock_Stone_Half", "Quantity": 3},
                      {"ResourceTypeId": "Wood_Trunk", "Quantity": 3}],
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}],
        },
        "BlockType": block,
    }
    write_json(ctx.pack / common.ITEMS / (IDENT + ".json"), item)
    for language, name in NAMES.items():
        ctx.lang[language].append(f"{NAME_KEY} = {name}")
```
  In `generate.py`, after the `for family in FAMILIES:` loop and before `manifest.write(ctx)`: `cutter.generate(ctx)` (import it with the other `blocks` modules).
- [ ] **Step 4: run** `python tools/domum/check.py` → OK; `python tools/domum/generate.py`; `./gradlew build` → green.
- [ ] **Step 5: commit**
```bash
git add tools/domum/blocks/cutter.py tools/domum/generate.py tools/domum/check_pack.py plugin/src/subplugins/DomumOrnamentum
git commit -m "feat(plugin): Domum Ornamentum architect's cutter block"
```

### Task 4: plugin: opening the cutter

**Files:**
- Create: `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterSystem.java`, `CutterOpener.java`, `CutterSlots.java`
- Modify: `plugin/src/main/java/dev/hycolony/plugin/ornament/Ornaments.java`, `docs/research/plugin-b-api.md` (a « 26. Établi à emplacements (`plugin/ornament/cutter`) » section listing the API of « Verified Hytale API » above with file:line from `build/vineflower`)

**Interfaces:**
- Consumes: `OrnamentVariantRegistry`, Task 2 (`CutterActions`, `CutterCatalog`), Task 5 (`CutterPage` constructor: write Task 5's class in the same commit if needed, or commit Task 4 with `CutterPage` from Task 5 — do Tasks 4 and 5 in one commit if the build needs it).
- Produces: `CutterSlots.read(ItemContainer) -> List<SlotContent>`; `CutterOpener.open(World, Ref<EntityStore>, Vector3i, OrnamentVariantRegistry)`.

- [ ] **Step 1: `CutterSlots.java`**
```java
package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.hycolony.core.ornament.cutter.SlotContent;
import java.util.ArrayList;
import java.util.List;

/** The cutter block's 2 material slots, read as the core sees them. */
final class CutterSlots {
    static final int COUNT = 2;

    private CutterSlots() {}

    /** Each slot's item and quantity; an empty or missing slot is {@link SlotContent#EMPTY}. */
    static List<SlotContent> read(ItemContainer container) {
        List<SlotContent> slots = new ArrayList<>();
        for (short slot = 0; slot < COUNT; slot++) {
            ItemStack stack = slot < container.getCapacity() ? container.getItemStack(slot) : null;
            slots.add(ItemStack.isEmpty(stack)
                    ? SlotContent.EMPTY
                    : new SlotContent(stack.getItemId(), stack.getQuantity()));
        }
        return slots;
    }
}
```
- [ ] **Step 2: `CutterSystem.java`** (copy `FlowerPotSystem`'s structure: `getDependencies` after `BlockUseProtectionSystem`, `getQuery` = `PlayerRef.getComponentType()`):
```java
    static final String CUTTER = "HyColony_DO_ArchitectsCutter";

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull UseBlockEvent.Pre event) {
        if (event.isCancelled() || !CUTTER.equals(event.getBlockType().getId())) {
            return;
        }
        try {
            CutterOpener.open(store.getExternalData().getWorld(), chunk.getReferenceTo(index), event.getTargetBlock(),
                    registry);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony cutter use failed at %s", event.getTargetBlock());
        }
    }
```
  Javadoc: « A player using the architect's cutter (MC DO ArchitectsCutterBlock.use): opens its window. The use is not cancelled on success (the block's own Use is a no-op). »
- [ ] **Step 3: `CutterOpener.java`**
```java
/**
 * Opens the cutter window on a cutter block: the page, beside a container window on the block's 2 material slots
 * (as vanilla OpenContainerInteraction opens a chest). One window per player on a block; it leaves the block's
 * window list when closed.
 */
final class CutterOpener {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterOpener() {}

    /** Opens the window at pos for player; tells the player when the ornaments are not loaded yet. */
    static void open(World world, Ref<EntityStore> player, Vector3i pos, OrnamentVariantRegistry registry) {
        Store<EntityStore> store = player.getStore();
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        Player playerComponent = store.getComponent(player, Player.getComponentType());
        if (playerRef == null || playerComponent == null) {
            return;
        }
        Optional<OrnamentVariantRegistry.Catalogs> catalogs = registry.catalogs();
        if (catalogs.isEmpty()) {
            playerRef.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.ornament.failed", "load")));
            return;
        }
        Optional<ItemContainerBlock> box = container(world, pos);
        if (box.isEmpty()) {
            LOG.at(Level.WARNING).log("hyornament: cutter at %s has no container", pos);
            return;
        }
        ContainerBlockWindow window = window(world, pos, box.get());
        UUID uuid = playerRef.getUuid();
        if (box.get().getWindows().putIfAbsent(uuid, window) != null) {
            return; // this player already has it open
        }
        CutterPage page = new CutterPage(playerRef, world, box.get().getItemContainer(), registry, catalogs.get());
        if (playerComponent.getPageManager().openCustomPageWithWindows(player, store, page, window)) {
            window.registerCloseEvent(e -> box.get().getWindows().remove(uuid, window));
        } else {
            box.get().getWindows().remove(uuid, window);
        }
    }

    /** The block's container component; empty when the block has none (not a loaded cutter). */
    private static Optional<ItemContainerBlock> container(World world, Vector3i pos) {
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        return blockRef == null
                ? Optional.empty()
                : Optional.ofNullable(world.getChunkStore().getStore().getComponent(
                        blockRef, ItemContainerBlock.getComponentType()));
    }

    /** A container window on the block's slots, turned as the block. */
    private static ContainerBlockWindow window(World world, Vector3i pos, ItemContainerBlock box) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        int rotation = blocks.getRotationIndex(pos.x, pos.y, pos.z);
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x, pos.y, pos.z));
        return new ContainerBlockWindow(pos.x, pos.y, pos.z, rotation, type, box.getItemContainer());
    }
}
```
  Imports: `BlockModule` (`server.core.modules.block`), `ItemContainerBlock` (`server.core.modules.block.components`), `ContainerBlockWindow` (`server.core.entity.entities.player.windows`), `BlockSection` (`server.core.universe.world.chunk.section`), `ChunkStore`/`EntityStore` (`server.core.universe.world.storage`), `Player` (`server.core.entity.entities`), `PlayerRef` (`server.core.universe`), `BlockType` (`server.core.asset.type.blocktype.config`), `org.joml.Vector3i`. Check `BlockSection.get(int, int, int)` returns the block id int (used by `OpenContainerInteraction`, l. 85); `BlockType.getAssetMap().getAsset(int)` exists (`BlockTypeAssetMap.getAsset(int)`).
- [ ] **Step 4: wire it** in `Ornaments.register`, after the command registration:
```java
        plugin.getEntityStoreRegistry().registerSystem(new CutterSystem(ornaments));
```
- [ ] **Step 5: run** `./gradlew spotlessApply` then `./gradlew build` → green (with Task 5's `CutterPage`).
- [ ] **Step 6: commit** (together with Task 5 if the build needs `CutterPage`):
```bash
git add plugin/src/main/java/dev/hycolony/plugin/ornament docs/research/plugin-b-api.md
git commit -m "feat(plugin): Domum Ornamentum architect's cutter opens its window"
```

### Task 5: plugin: the cutter page

**Files:**
- Create: `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterPage.java`, `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Cutter.ui`, `CutterShapeButton.ui`

- [ ] **Step 1: `Cutter.ui`** (a side container like `WandPage.ui`, leaving room for the inventory and the slot window):
```
$C = "../../Common.ui";
$Sounds = "../../Sounds.ui";

$C.@Container {
  Anchor: (Right: 50, Top: 120, Width: 560, Height: 520);

  #Title {
    $C.@Title {
      @Text = %hycolony.ornament.cutter.title;
    }
  }

  #Content {
    LayoutMode: Top;

    Group #TabButtons {
      LayoutMode: LeftCenterWrap;
      Anchor: (Bottom: 8);
    }

    Group #Shapes {
      LayoutMode: LeftCenterWrap;
      FlexWeight: 1;
    }

    Label #ShapeName {
      Style: (RenderBold: true, TextColor: #ffffff);
      Anchor: (Height: 24, Top: 6);
    }

    Label #Slot0 {
      Style: (TextColor: #94a7bb);
      Anchor: (Height: 20);
    }

    Label #Slot1 {
      Style: (TextColor: #94a7bb);
      Anchor: (Height: 20);
    }

    Group {
      LayoutMode: Left;
      Anchor: (Height: 48, Top: 8);

      ItemIcon #Preview {
        Anchor: (Width: 40, Height: 40);
      }

      Label #PreviewText {
        FlexWeight: 1;
        Padding: (Horizontal: 8);
        Style: (TextColor: #ffffff, Wrap: true, VerticalAlignment: Center);
      }

      $C.@TextButton #CraftButton {
        @Text = %hycolony.ornament.cutter.craft;
        @Sounds = $Sounds.@SaveSettings;
        Anchor: (Width: 140, Height: 36);
      }
    }
  }
}
```
  `CutterShapeButton.ui`:
```
$C = "../../Common.ui";

Button #ShapeButton {
  LayoutMode: Center;
  Anchor: (Width: 52, Height: 52, Right: 4, Bottom: 4);
  Style: $C.@SecondaryButtonStyle;

  ItemIcon #Icon {
    Anchor: (Width: 40, Height: 40);
  }
}
```
- [ ] **Step 2: `CutterPage.java`**
```java
/**
 * The architect's cutter window (MC DO ArchitectsCutterScreen): group tabs, the group's shapes, a label per slot and
 * the preview, redrawn from the core's {@link CutterView} whenever an action runs or the slots change. World thread.
 */
final class CutterPage extends InteractiveCustomUIPage<CutterPage.Act> {
    /** A button's event: action and index, as ColonyPage.Act. */
    static final class Act {
        static final BuilderCodec<Act> CODEC = BuilderCodec.builder(Act.class, Act::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (d, v) -> d.index = parse(v),
                        d -> String.valueOf(d.index))
                .add()
                .build();
        String action = "";
        int index = -1;

        private static int parse(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    private final World world;
    private final ItemContainer slots;
    private final OrnamentVariantRegistry registry;
    private final OrnamentVariantRegistry.Catalogs catalogs;
    private final CutterActions actions;
    private final EventRegistration<?, ?> onChange;

    CutterPage(PlayerRef playerRef, World world, ItemContainer slots, OrnamentVariantRegistry registry,
            OrnamentVariantRegistry.Catalogs catalogs) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.world = world;
        this.slots = slots;
        this.registry = registry;
        this.catalogs = catalogs;
        this.actions = new CutterActions(CutterCatalog.of(catalogs.shapes()), catalogs.materials().tags());
        // The preview follows the slots; container changes happen on the world thread.
        this.onChange = slots.registerChangeEvent(e -> rebuild());
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Cutter.ui");
        CutterView view = actions.view(CutterSlots.read(slots));
        tabs(ui, events, view.tabs());
        shapes(ui, events, view.shapes());
        for (int i = 0; i < CutterSlots.COUNT; i++) {
            boolean shown = i < view.slotLabelKeys().size();
            ui.set("#Slot" + i + ".Visible", shown);
            if (shown) {
                ui.set("#Slot" + i + ".Text", Message.translation(view.slotLabelKeys().get(i)));
            }
        }
        preview(ui, events, view.preview());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        PageEvents.guard(getClass(), () -> {
            switch (act.action) {
                case "group" -> actions.selectGroup(act.index);
                case "shape" -> actions.selectShape(act.index);
                case "craft" -> actions.shape().ifPresent(shape -> CutterCrafting.craft(new CutterCrafting.Request(
                        world, ref, slots, shape, catalogs.materials().tags(), registry, this::rebuild)));
                default -> {
                    return;
                }
            }
            rebuild();
        });
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        onChange.unregister();
        super.onDismiss(ref, store);
    }

    private static void tabs(UICommandBuilder ui, UIEventBuilder events, List<CutterView.Tab> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            String button = "#TabButtons[" + i + "]";
            ui.append("#TabButtons", "Pages/HyColony/TabButton.ui");
            ui.set(button + ".Text", Message.translation(tabs.get(i).nameKey()));
            ui.set(button + ".Disabled", tabs.get(i).selected());
            bind(events, button, "group", i);
        }
    }

    private static void shapes(UICommandBuilder ui, UIEventBuilder events, List<CutterView.ShapeButton> shapes) {
        for (int i = 0; i < shapes.size(); i++) {
            CutterView.ShapeButton shape = shapes.get(i);
            String button = "#Shapes[" + i + "]";
            ui.append("#Shapes", "Pages/HyColony/CutterShapeButton.ui");
            ui.set(button + " #Icon.ItemId", shape.templateKey());
            ui.set(button + ".Disabled", shape.selected());
            bind(events, button, "shape", i);
            if (shape.selected()) {
                ui.set("#ShapeName.Text", itemName(shape.templateKey()));
            }
        }
    }

    private static void preview(UICommandBuilder ui, UIEventBuilder events, CutterView.Preview preview) {
        switch (preview) {
            case CutterView.Empty e -> {
                ui.set("#Preview.Visible", false);
                ui.set("#PreviewText.Text", Message.translation("hycolony.ornament.cutter.placeMaterials"));
            }
            case CutterView.Ready ready -> {
                boolean exists = Item.getAssetMap().getAsset(ready.itemId()) != null;
                ui.set("#Preview.ItemId", exists ? ready.itemId() : ready.templateKey());
                ui.set("#PreviewText.Text", Message.raw("x " + ready.quantity()));
            }
            case CutterView.Refused refused -> {
                ui.set("#Preview.Visible", false);
                ui.set("#PreviewText.Text", HytaleNotifier.toMessage(
                        Msg.of(refused.reasonKey(), refused.params().toArray(String[]::new))));
            }
        }
        ui.set("#CraftButton.Disabled", !(preview instanceof CutterView.Ready));
        bind(events, "#CraftButton", "craft", -1);
    }

    private static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : item.getTranslationMessage();
    }

    private static void bind(UIEventBuilder events, String selector, String action, int index) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Index", String.valueOf(index)), false);
    }
}
```
  Imports: copy those of `ui/ColonyPage.java` and `ui/citizen/CitizenPage.java` for the UI builder classes, `Message`, `Item`, `KeyedCodec`, `Codec`, `BuilderCodec`, `CustomPageLifetime`, `CustomUIEventBindingType`, `EventData`; `EventRegistration` (`com.hypixel.hytale.event`); `ItemContainer`; `HytaleNotifier` (`dev.hycolony.plugin.adapter`), `Msg` (`dev.hycolony.core.kernel.port`, `static Msg of(String key, String... params)`), `PageEvents` (`dev.hycolony.plugin.ui`). `PageEvents` and its `guard(Class<?>, Runnable)` are package-private today: **modify `plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java`** to make the class and `guard` `public` (Javadoc unchanged), and add that file to this task's commit.
  If `HytaleNotifier.toMessage` renders a translation whose parameter is itself a translation, set it on `#PreviewText.TextSpans` rather than `.Text` (CLAUDE.md § 7); the parameters here are plain strings, so `.Text` is right.
- [ ] **Step 3: run** `./gradlew spotlessApply` then `./gradlew build` → green.
- [ ] **Step 4: commit**
```bash
git add plugin/src/main/java/dev/hycolony/plugin/ornament/cutter plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Cutter.ui plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/CutterShapeButton.ui
git commit -m "feat(plugin): Domum Ornamentum architect's cutter window"
```

### Task 6: plugin: crafting

**Files:**
- Create: `plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterCrafting.java`

- [ ] **Step 1: implement**
```java
/**
 * Crafting at the architect's cutter (MC DO ArchitectsCutterRecipe.assemble + ArchitectsCutterContainer take): the
 * recipe checked on the world thread, the variant requested off it, the slots checked again once it exists, then 1
 * taken from each slot used and the DO quantity given. Nothing is taken or given when anything changed or failed.
 */
final class CutterCrafting {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterCrafting() {}

    /** One craft: where, who, the block's slots, the shape, the tags, the registry, and the page to redraw. */
    record Request(World world, Ref<EntityStore> player, ItemContainer slots, OrnamentShape shape, MaterialTags tags,
            OrnamentVariantRegistry registry, Runnable redraw) {}

    /** Starts crafting; call it on the world thread. */
    static void craft(Request request) {
        CutterCraft.Result first = CutterCraft.check(request.shape(), CutterSlots.read(request.slots()), request.tags());
        if (!(first instanceof CutterCraft.Ready ready)) {
            return; // the craft button is disabled while the preview is not ready
        }
        var _ = request.registry().request(List.of(ready.key())).whenComplete((variants, error) -> {
            try {
                request.world().execute(() -> finish(request, ready, error));
            } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing was taken
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: cutter craft of %s dropped", ready.key().id());
            }
        });
    }

    /** On the world thread, once the variant exists (or failed): re-check, take, give. */
    private static void finish(Request request, CutterCraft.Ready asked, @Nullable Throwable error) {
        if (!request.player().isValid()) {
            return; // the player left: nothing taken, the variant stays for next time
        }
        Store<EntityStore> store = request.player().getStore();
        PlayerRef player = store.getComponent(request.player(), PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        if (error != null) {
            LOG.at(Level.SEVERE).withCause(error).log("hyornament: cutter could not create %s", asked.key().id());
            say(player, "hycolony.ornament.failed", "create");
            return;
        }
        CutterCraft.Result now = CutterCraft.check(request.shape(), CutterSlots.read(request.slots()), request.tags());
        if (!(now instanceof CutterCraft.Ready ready)
                || !ready.key().blockTypeKey().equals(asked.key().blockTypeKey())) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        if (!take(request.slots(), ready.consumed())) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        String item = ready.key().blockTypeKey();
        SimpleItemContainer.addOrDropItemStack(store, request.player(),
                InventoryComponent.getCombined(store, request.player(), InventoryComponent.HOTBAR_FIRST),
                new ItemStack(item, ready.quantity()));
        say(player, "hycolony.ornament.cutter.crafted", String.valueOf(ready.quantity()), item);
        request.redraw().run();
    }

    /** Takes 1 from each slot; if one fails, gives back what was taken and returns false. */
    private static boolean take(ItemContainer slots, List<Integer> consumed) {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot : consumed) {
            ItemStack before = slots.getItemStack((short) slot);
            ItemStackSlotTransaction removal = slots.removeItemStackFromSlot((short) slot, 1);
            if (ItemStack.isEmpty(before) || !removal.succeeded()) {
                for (int i = 0; i < taken.size(); i++) {
                    slots.addItemStackToSlot(consumed.get(i).shortValue(), taken.get(i));
                }
                return false;
            }
            taken.add(new ItemStack(before.getItemId(), 1));
        }
        return true;
    }

    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }
}
```
  Imports: `ItemStackSlotTransaction` (`server.core.inventory.transaction`), `SimpleItemContainer`, `InventoryComponent`, `ItemStack`, `ItemContainer`, `World`, `PlayerRef`, `Store`/`Ref`, `EntityStore`, `OrnamentShape`, `MaterialTags`, `CutterCraft`, `OrnamentVariantRegistry`, `HytaleNotifier`, `Msg`, `org.jspecify.annotations.Nullable`. Use the exact import paths `VariantGift` and `OpenContainerInteraction` use. The crafted message shows the item id: if you prefer the item's name, send a `Message` built with `Message.translation("hycolony.ornament.cutter.crafted").param("p0", ...)` as other HyColony messages do (grep `param(` in `plugin/`), putting the name on `TextSpans` rules aside (a chat message has no `.Text`/`.TextSpans`).
- [ ] **Step 2: run** `./gradlew spotlessApply` then `./gradlew build` → green.
- [ ] **Step 3: commit**
```bash
git add plugin/src/main/java/dev/hycolony/plugin/ornament/cutter/CutterCrafting.java
git commit -m "feat(plugin): Domum Ornamentum architect's cutter crafts variants"
```

### Task 7: texts and docs

**Files:** Modify `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang`, `fr-FR/hycolony.lang`, `docs/TESTING.md`, `docs/research/domum-ornamentum.md`, the DO-2a spec.

- [ ] **Step 1: keys**, next to the other `ornament.` keys, same order in both files:

| Key | en-US | fr-FR |
|---|---|---|
| `ornament.cutter.title` | Architect's cutter | Établi de l'architecte |
| `ornament.cutter.group.avanilla` | Vanilla | Vanilla |
| `ornament.cutter.group.btimberframe` | Timber frames | Colombages |
| `ornament.cutter.group.cshingle` | Shingles | Bardeaux |
| `ornament.cutter.group.ddoor` | Doors | Portes |
| `ornament.cutter.group.etrapdoor` | Trapdoors | Trappes |
| `ornament.cutter.group.fpanel` | Panels | Panneaux |
| `ornament.cutter.group.gpillar` | Pillars | Piliers |
| `ornament.cutter.group.hpaperwall` | Framed panes | Vitres encadrées |
| `ornament.cutter.group.kpost` | Posts | Poteaux |
| `ornament.cutter.slot.timber_frames_frame` | Frame | Cadre |
| `ornament.cutter.slot.timber_frames_center` | Centre | Centre |
| `ornament.cutter.slot.shingles_roof` | Roof | Toit |
| `ornament.cutter.slot.shingles_support` | Support | Support |
| `ornament.cutter.slot.paper_wall_frame` | Frame | Cadre |
| `ornament.cutter.slot.paper_wall_center` | Pane | Vitre |
| `ornament.cutter.slot.pillar_materials` | Material | Matériau |
| `ornament.cutter.slot.post_materials` | Material | Matériau |
| `ornament.cutter.slot.trapdoors_materials` | Material | Matériau |
| `ornament.cutter.slot.doors_materials` | Material | Matériau |
| `ornament.cutter.slot.fancy_doors_materials` | Material | Matériau |
| `ornament.cutter.slot.fancy_trapdoors_materials` | Material | Matériau |
| `ornament.cutter.slot.fence_materials` | Material | Matériau |
| `ornament.cutter.slot.fence_gate_materials` | Material | Matériau |
| `ornament.cutter.slot.wall_materials` | Material | Matériau |
| `ornament.cutter.slot.stairs_materials` | Material | Matériau |
| `ornament.cutter.slot.slab_materials` | Material | Matériau |
| `ornament.cutter.slot.all_brick_materials` | Material | Matériau |
| `ornament.cutter.craft` | Craft | Fabriquer |
| `ornament.cutter.placeMaterials` | Place the materials in the slots. | Posez les matériaux dans les emplacements. |
| `ornament.cutter.emptySlot` | Material {p0} is missing. | Il manque le matériau {p0}. |
| `ornament.cutter.badMaterial` | Material #{p0} is not accepted here. Accepted: {p1} | Le matériau n° {p0} n'est pas accepté ici. Acceptés : {p1} |
| `ornament.cutter.changed` | The materials changed, nothing was crafted. | Les matériaux ont changé, rien n'a été fabriqué. |
| `ornament.cutter.crafted` | {p0} x {p1} crafted. | {p0} × {p1} fabriqué(s). |

  Check both files have the same key set:
```bash
cd plugin/src/main/resources/Server/Languages && diff <(grep -o '^[^=#]*' en-US/hycolony.lang | sed 's/ *$//' | sort) <(grep -o '^[^=#]*' fr-FR/hycolony.lang | sed 's/ *$//' | sort) && echo "keys match"
```
- [ ] **Step 2: `docs/TESTING.md`**: a section « Établi de l'architecte (DO-2a) » after the DO-1 section, numbered from its last step + 1. Pack on: `"SubPlugins": {"DomumOrnamentum": true}`. Steps:
  1. **Emplacements visibles.** Clic sur l'établi posé : la fenêtre s'ouvre, et les 2 emplacements de l'établi s'affichent à côté de l'inventaire. **Si les emplacements ne s'affichent pas, s'arrêter et le signaler.**
  2. **Fabriquer l'établi** au `Workbench` (catégorie Crafting) : 1 lingot de fer, 3 dalles de pierre, 3 troncs. Le poser : il est tourné vers le joueur et ressemble à l'établi de construction vanilla.
  3. **Onglets et formes.** Les onglets suivent l'ordre vanilla, colombages, bardeaux, trappes, portes, panneaux, vitres encadrées, piliers, poteaux. Chaque forme a son icône, et le nom de la forme choisie s'affiche.
  4. **Fabrications.**
     - Colombage (planches + terre cuite blanche) : 4 objets.
     - Bardeau : 4.
     - Vitre encadrée : 6.
     - Porte ouvragée avec un seul matériau : 2.
     - Dalle : 2.
     - Chaque fois, 1 de chaque matériau utilisé est retiré, et l'icône de la variante apparaît dans l'aperçu une fois créée.
  5. **Refus.** Un matériau hors tag est refusé, avec son numéro et des matériaux acceptés. Un emplacement vide : « Il manque le matériau 2 ». Le bouton est grisé et rien n'est retiré.
  6. **Changement pendant la création.** Nouvelle paire, clic, puis retirer vite un matériau : message « Les matériaux ont changé », rien n'est retiré ni donné.
  7. **Deux joueurs** sur le même établi : ils voient les mêmes emplacements. Deux clics sur un seul jeu de matériaux ne fabriquent qu'une fois.
  8. **Casse et redémarrage.**
     - Casser l'établi plein : les matériaux tombent au sol.
     - Inventaire plein : les objets fabriqués tombent aux pieds du joueur.
     - Redémarrer avec des matériaux dans l'établi : ils y sont toujours.
  9. **Pack coupé.** `"DomumOrnamentum": false` : l'établi n'existe plus, et aucun plantage.
- [ ] **Step 3:** `docs/research/domum-ornamentum.md`, synthesis: a « DO-2a » line pointing at the spec and plan. In the DO-2a spec, add the limits found while coding, if any.
- [ ] **Step 4: run** `./gradlew build` → green.
- [ ] **Step 5: commit**
```bash
git add plugin/src/main/resources/Server/Languages docs/TESTING.md docs/research/domum-ornamentum.md docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do2-cutter-design.md
git commit -m "docs: Domum Ornamentum architect's cutter in-game checks"
```

## After Task 7

- **Whole-branch review.** Run a `hycolony-reviewer` pass on the branch range of these tasks.
- **Fidelity check.** Run an `mc-fidelity-checker` pass against DO's `ArchitectsCutterContainer`, `ArchitectsCutterRecipe` and `ArchitectsCutterScreen` at commit `82729d6c9dc0499b256b36b4506d0d9ef20e8aec`.
- **Fixes.** Review each fix again.
- **Hand-back.** Hand back to the user for the in-game test. Never launch the server.
