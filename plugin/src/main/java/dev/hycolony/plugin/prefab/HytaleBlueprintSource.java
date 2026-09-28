package dev.hycolony.plugin.prefab;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.plugin.IdMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Blueprints read from the prefabs listed in {@link PrefabStyles}. Never throws: an unknown style/type/level,
 * a missing prefab or a failed parse gives {@link Optional#empty()} and one logged warning.
 *
 * <p>Rotation: the int is the hut's {@code yaw().ordinal()} (SP0). {@code PrefabRotation.VALUES} is
 * {@code ROTATION_0/90/180/270}, built on {@code Rotation.None/Ninety/OneEighty/TwoSeventy} in that same order, so
 * {@code VALUES[i] == PrefabRotation.fromRotation(Rotation.VALUES[i])}. The buffer turns every entry's own rotation by
 * the same amount ({@code PrefabRotation.getRotation(int)} adds the yaw), so the house turns with the hut block.
 *
 * <p>Entries: {@code filler == 0} only (placing the origin rebuilds the fillers), each resolved by
 * {@link PrefabCells}: {@code Empty}, {@code Block_Spawner_Block} and {@code Editor_*} dropped, except that a level
 * marked {@code spawnerChests} (warehouse, courier) turns a chest spawner into the style's empty chest
 * ({@code blueprint.spawnerChest.<style>} in the id-map); state ids ({@code *...}) normalised to their default state
 * like {@code HytaleWorldBlocks.get}; a fluid-only cell becomes {@code ~fluid:<FluidKey>}, rotation 0 (a block with a
 * fluid in it keeps the block: one state per cell). A block that cannot rotate ({@code VariantRotation.None}) gets
 * rotation 0, since the buffer adds the yaw to every block and natural terrain would never match. Prefab chances use
 * {@code new Random(0)}, so every load sees the same blueprint. Other block-entity data in the prefab (chest
 * contents, spawner loot) is ignored.
 *
 * <p>Depth: vanilla prefabs mean "absent = keep the terrain", and their lower layers are foundations meant to sink
 * into the ground. Only the floor layer (hut-relative y = -1, the hut stands on it) and above are kept, so the
 * builder neither digs out nor refills the ground under the house. The bounds (the CLEAR box) start one layer
 * higher, at the hut's level: floor cells the prefab leaves absent keep their ground instead of becoming a ditch,
 * and the floor entries are still placed (SOLID mines a differing ground block first).
 *
 * <p>{@link #prewarm(PrefabStyles)} parses the prefabs off the world thread at startup; a later load then reads the cached
 * buffer. Results are cached per (style, type, level, rotation).
 */
public final class HytaleBlueprintSource implements BlueprintSource {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Hut-relative y of the floor the hut stands on: nothing below it is part of the blueprint. */
    private static final int FLOOR_Y = -1;

    /** id-map block key prefix of the style's empty chest that replaces a chest spawner. */
    private static final String SPAWNER_CHEST_KEY = "blueprint.spawnerChest.";

    /** A rotated, anchor-relative prefab cell. */
    private record Cell(int x, int y, int z, BlockState state, boolean container, Optional<Workstation> workstation) {}

    private static final AtomicBoolean PREWARMED = new AtomicBoolean();

    private final PrefabStyles styles;
    private final IdMap ids;
    private final Map<String, Optional<Blueprint>> cache = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /** Blueprints of {@code styles}, whose spawner chests come from {@code ids}. */
    public HytaleBlueprintSource(IdMap ids, PrefabStyles styles) {
        this.styles = styles;
        this.ids = ids;
    }

    /**
     * Parses every prefab of {@code styles} in the background, once (like vanilla's prefab editor, which
     * calls {@code getCached} in {@code supplyAsync}). Assets must be loaded. Logs the time taken; never throws.
     */
    public static void prewarm(PrefabStyles styles) {
        if (!PREWARMED.compareAndSet(false, true)) {
            return;
        }
        // runAsync's Future is discarded: attach a backstop for anything that escapes the try/catch below
        // (the RuntimeException cases already log there), so no failure is ever silently dropped (CLAUDE.md sec 4).
        var _ = CompletableFuture.runAsync(() -> {
                    long start = System.nanoTime();
                    int n = 0;
                    for (String prefab : styles.prefabs()) {
                        try {
                            Path path = PrefabStore.get().findAssetPrefabPath(prefab);
                            if (path != null
                                    && getCached(
                                                    path,
                                                    e -> LOG.at(Level.WARNING).withCause(e).log(
                                                            "HyColony blueprint: cannot pre-load %s", prefab))
                                            .isPresent()) {
                                n++;
                            }
                        } catch (RuntimeException e) {
                            LOG.at(Level.WARNING).withCause(e).log("HyColony blueprint: cannot pre-load %s", prefab);
                        }
                    }
                    LOG.at(Level.INFO).log(
                            "HyColony blueprint: pre-loaded %d prefabs in %d ms",
                            n, (System.nanoTime() - start) / 1_000_000);
                })
                .exceptionally(e -> {
                    LOG.at(Level.WARNING).withCause(e).log("HyColony blueprint: pre-load task failed unexpectedly");
                    return null;
                });
    }

    @Override
    public List<String> styles() {
        return styles.styles();
    }

    @Override
    public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
        int rot = rotation & 3;
        return cache.computeIfAbsent(style + '|' + buildingTypeId + '|' + level + '|' + rot, k -> {
            PrefabStyles.Level entry = styles.level(style, buildingTypeId, level);
            if (entry == null) {
                warnOnce("no styles.json entry for " + style + " / " + buildingTypeId + " / level " + level, null);
                return Optional.empty();
            }
            try {
                return read(style, entry, PrefabRotation.VALUES[rot]);
            } catch (RuntimeException e) {
                warnOnce("cannot read prefab " + entry.prefab(), e);
                return Optional.empty();
            }
        });
    }

    /** The prefab's buffer; empty (logged once) when Hytale cannot load it. */
    private Optional<IPrefabBuffer> buffer(Path path, String prefab) {
        return getCached(path, e -> warnOnce("cannot load prefab " + prefab, e));
    }

    /**
     * PrefabBufferUtil.getCached; empty, after {@code onFailure}, when it fails. It reports a malformed or missing
     * prefab as {@code java.lang.Error} and may sneak-throw an IOException; JVM errors are not caught.
     */
    private static Optional<IPrefabBuffer> getCached(Path path, Consumer<Throwable> onFailure) {
        try {
            return Optional.of(PrefabBufferUtil.getCached(path));
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Exception | Error e) {
            onFailure.accept(e);
            return Optional.empty();
        }
    }

    private Optional<Blueprint> read(String style, PrefabStyles.Level entry, PrefabRotation r) {
        Path path = PrefabStore.get().findAssetPrefabPath(entry.prefab());
        if (path == null) {
            warnOnce("prefab not found: " + entry.prefab(), null);
            return Optional.empty();
        }
        Optional<IPrefabBuffer> loaded = buffer(path, entry.prefab());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        IPrefabBuffer buf = loaded.get();

        // Pass 1: rotated, anchor-relative cells. The hut cell is only known afterwards (default = lowest layer).
        String chest = entry.spawnerChests() ? ids.blockId(SPAWNER_CHEST_KEY + style) : null;
        List<Cell> cells = new ArrayList<>();
        int lowestY = readCells(buf, r, chest, cells);
        if (cells.isEmpty()) {
            warnOnce("prefab has no blocks: " + entry.prefab(), null);
            return Optional.empty();
        }

        // Pass 2: the unrotated hut cell, turned like the entries, then everything made hut-relative.
        BlockPos hut = PrefabStyles.rotate(r, PrefabStyles.hutCell(entry.hutOffset(), buf, lowestY));
        List<BlueprintEntry> entries = hutRelative(cells, hut);
        BlockPos low = PrefabStyles.relative(buf.getMinX(r), buf.getMinY(), buf.getMinZ(r), hut);
        BlockPos min = new BlockPos(low.x(), Math.max(low.y(), FLOOR_Y + 1), low.z());
        BlockPos max = PrefabStyles.relative(buf.getMaxX(r), buf.getMaxY(), buf.getMaxZ(r), hut);
        return Optional.of(new Blueprint(entry.prefab(), List.copyOf(entries), min, max));
    }

    /** Adds the prefab's non-filler cells, rotated by {@code r}, to {@code cells}; returns their lowest y. */
    private static int readCells(IPrefabBuffer buf, PrefabRotation r, @Nullable String chest, List<Cell> cells) {
        int[] lowestY = {Integer.MAX_VALUE};
        buf.forEach(
                IPrefabBuffer.iterateAllColumns(),
                (int x,
                        int y,
                        int z,
                        int blockId,
                        Holder<ChunkStore> holder,
                        int support,
                        int rotation,
                        int filler,
                        PrefabBufferCall call,
                        int fluidId,
                        int fluidLevel) -> {
                    if (filler != 0) {
                        return;
                    }
                    PrefabCells.resolve(blockId, holder, rotation, fluidId, chest)
                            .ifPresent(c -> {
                                lowestY[0] = Math.min(lowestY[0], y);
                                cells.add(new Cell(x, y, z, c.state(), c.container(), c.workstation()));
                            });
                },
                null,
                null,
                new PrefabBufferCall(new Random(0), r));
        return lowestY[0];
    }

    /** The cells as entries relative to {@code hut}, without the hut cell itself nor anything below the floor. */
    private static List<BlueprintEntry> hutRelative(List<Cell> cells, BlockPos hut) {
        List<BlueprintEntry> entries = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            BlockPos offset = PrefabStyles.relative(c.x(), c.y(), c.z(), hut);
            if (offset.y() >= FLOOR_Y && (offset.x() != 0 || offset.y() != 0 || offset.z() != 0)) {
                entries.add(new BlueprintEntry(offset, c.state(), c.container(), c.workstation()));
            }
        }
        return entries;
    }

    private void warnOnce(String message, @Nullable Throwable cause) {
        if (warned.add(message)) {
            if (cause == null) {
                LOG.at(Level.WARNING).log("HyColony blueprint: %s", message);
            } else {
                LOG.at(Level.WARNING).withCause(cause).log("HyColony blueprint: %s", message);
            }
        }
    }
}
