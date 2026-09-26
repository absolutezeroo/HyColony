package dev.hycolony.plugin.adapter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import dev.hycolony.core.kernel.BlockPos;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Pure part of {@link HytaleBlueprintSource}: the {@code hycolony/styles.json} table and the hut-cell math. Needs no
 * running server ({@link PrefabRotation} is a plain enum).
 *
 * <p>Format: {@code { style: { buildingTypeId: { level: { "prefab": "<path under Server/Prefabs>", "hutOffset": [x,y,z] } } } }}.
 * {@code hutOffset} is in the prefab file's own coordinates (the x/y/z written in the {@code .prefab.json}, before the
 * anchor is subtracted), unrotated. Absent: the default cell, see {@link #hutCell}.
 */
final class PrefabStyles {
    static final String FIRST_STYLE = "outlander";

    record Level(String prefab, @Nullable int[] hutOffset) {}

    private final Map<String, Map<String, Map<String, Level>>> table;

    private PrefabStyles(Map<String, Map<String, Map<String, Level>>> table) {
        this.table = table;
    }

    static PrefabStyles parse(Reader in) {
        Map<String, Map<String, Map<String, Level>>> t = new Gson()
                .fromJson(in, new TypeToken<LinkedHashMap<String, Map<String, Map<String, Level>>>>() {}.getType());
        return new PrefabStyles(t == null ? Map.of() : t);
    }

    /** Throws if the bundled file is missing or malformed (a packaging bug, like IdMap). */
    static PrefabStyles loadBundled() {
        try (InputStream in = PrefabStyles.class.getResourceAsStream("/hycolony/styles.json")) {
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read hycolony/styles.json", e);
        }
    }

    /** Every distinct prefab path, in file order. */
    Set<String> prefabs() {
        Set<String> out = new LinkedHashSet<>();
        table.values()
                .forEach(types -> types.values()
                        .forEach(levels -> levels.values().forEach(l -> {
                            if (l != null && l.prefab() != null) {
                                out.add(l.prefab());
                            }
                        })));
        return out;
    }

    /** Style keys in file order, {@code outlander} first. */
    List<String> styles() {
        List<String> out = new ArrayList<>(table.keySet());
        if (out.remove(FIRST_STYLE)) {
            out.addFirst(FIRST_STYLE);
        }
        return List.copyOf(out);
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
     * of the unrotated box on the lowest non-empty layer.
     */
    static int[] hutCell(
            @Nullable int[] hutOffset,
            int anchorX,
            int anchorY,
            int anchorZ,
            int minX,
            int maxX,
            int lowestY,
            int minZ,
            int maxZ) {
        if (hutOffset != null) {
            return new int[] {hutOffset[0] - anchorX, hutOffset[1] - anchorY, hutOffset[2] - anchorZ};
        }
        return new int[] {Math.floorDiv(minX + maxX, 2), lowestY, Math.floorDiv(minZ + maxZ, 2)};
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
