package dev.hycolony.plugin.prefab;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import dev.hycolony.core.kernel.BlockPos;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pure part of {@link HytaleBlueprintSource}: the {@code hycolony/styles.json} table and the hut-cell math. Needs no
 * running server ({@link PrefabRotation} is a plain enum, {@link IPrefabBuffer} only read through its getters).
 *
 * <p>Format: {@code { style: { buildingTypeId: { level: { "prefab": "<path under Server/Prefabs>", "hutOffset": [x,y,z],
 * "spawnerChests": true } } } }}; {@code spawnerChests} is optional (false).
 * {@code hutOffset} is in the prefab file's own coordinates (the x/y/z written in the {@code .prefab.json}, before the
 * anchor is subtracted), unrotated. Absent: the default cell, see {@link #hutCell}.
 */
public final class PrefabStyles {
    /** {@code spawnerChests}: chest spawners become the style's empty chest (see HytaleBlueprintSource). */
    record Level(String prefab, @Nullable int[] hutOffset, boolean spawnerChests) {}

    private final Map<String, Map<String, Map<String, Level>>> table;

    private PrefabStyles(Map<String, Map<String, Map<String, Level>>> table) {
        this.table = table;
    }

    /** The table read from {@code json}: the core's hycolony/styles.json merged with the enabled packs' fragments. */
    public static PrefabStyles of(JsonElement json) {
        Map<String, Map<String, Map<String, Level>>> t = new Gson()
                .fromJson(json, new TypeToken<LinkedHashMap<String, Map<String, Map<String, Level>>>>() {}.getType());
        return new PrefabStyles(t == null ? Map.of() : t);
    }

    /** Every distinct prefab path, in file order. */
    Set<String> prefabs() {
        Set<String> out = new LinkedHashSet<>();
        // Gson keeps a JSON null as a null value at any depth ({"kweebec": null}): skip it like a missing entry.
        table.values().stream()
                .filter(Objects::nonNull)
                .flatMap(types -> types.values().stream())
                .filter(Objects::nonNull)
                .flatMap(levels -> levels.values().stream())
                .filter(l -> l != null && l.prefab() != null)
                .forEach(l -> out.add(l.prefab()));
        return out;
    }

    /** Style keys in file order: the core's, then each sub-plugin's in its {@code Order}. */
    List<String> styles() {
        return List.copyOf(table.keySet());
    }

    /** The entry, or null when the style, type or level is unknown or the entry is malformed. */
    @Nullable
    Level level(String style, String buildingTypeId, int level) {
        Map<String, Map<String, Level>> types = table.get(style);
        Map<String, Level> levels = types == null ? null : types.get(buildingTypeId);
        Level l = levels == null ? null : levels.get(Integer.toString(level));
        if (l == null || l.prefab() == null || (l.hutOffset() != null && l.hutOffset().length != 3)) {
            return null;
        }
        return l;
    }

    /**
     * The hut cell, anchor-relative and unrotated: {@code hutOffset - anchor}, or by default the centre X/Z (floor)
     * of the unrotated box of {@code buf} on its lowest non-empty layer {@code lowestY}.
     */
    static int[] hutCell(@Nullable int[] hutOffset, IPrefabBuffer buf, int lowestY) {
        if (hutOffset != null) {
            return new int[] {
                hutOffset[0] - buf.getAnchorX(), hutOffset[1] - buf.getAnchorY(), hutOffset[2] - buf.getAnchorZ()
            };
        }
        return new int[] {
            Math.floorDiv(buf.getMinX() + buf.getMaxX(), 2), lowestY, Math.floorDiv(buf.getMinZ() + buf.getMaxZ(), 2)
        };
    }

    /** An anchor-relative cell turned by {@code r} (the same turn the prefab buffer applies to its entries). */
    static BlockPos rotate(PrefabRotation r, int[] cell) {
        return new BlockPos(r.getX(cell[0], cell[2]), cell[1], r.getZ(cell[0], cell[2]));
    }

    /** A rotated anchor-relative position made relative to the rotated hut cell. */
    static BlockPos relative(int x, int y, int z, BlockPos hut) {
        return new BlockPos(x - hut.x(), y - hut.y(), z - hut.z());
    }
}
