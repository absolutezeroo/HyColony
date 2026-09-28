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
 * both in DO's {@code SortedBlocks} order (avanilla, btimberframe, cshingle, etrapdoor, ddoor...).
 */
public final class CutterCatalog {
    private final Map<String, List<OrnamentShape>> byGroup;
    private final List<String> groups;

    private CutterCatalog(Map<String, List<OrnamentShape>> byGroup) {
        this.byGroup = byGroup;
        this.groups = List.copyOf(byGroup.keySet());
    }

    /** The groups having at least one shape in shapes, each with its shapes, in DO's order. */
    public static CutterCatalog of(ShapeCatalog shapes) {
        Map<String, List<OrnamentShape>> groups = new TreeMap<>(CutterOrder.GROUPS);
        for (OrnamentShape shape : shapes.all()) {
            groups.computeIfAbsent(shape.group(), g -> new ArrayList<>()).add(shape);
        }
        Map<String, List<OrnamentShape>> frozen = new LinkedHashMap<>();
        groups.forEach((group, list) -> {
            // List.sort is stable: shapes DO does not index keep the manifest's order.
            list.sort(CutterOrder.SHAPES);
            frozen.put(group, List.copyOf(list));
        });
        return new CutterCatalog(frozen);
    }

    /** The group ids in DO's order; a group without shapes is absent. */
    public List<String> groups() {
        return groups;
    }

    /** group's shapes in DO's order; empty for an unknown group. */
    public List<OrnamentShape> shapes(String group) {
        return byGroup.getOrDefault(group, List.of());
    }
}
