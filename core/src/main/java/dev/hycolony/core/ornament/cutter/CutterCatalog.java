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

    /** The group ids in DO's order (sorted by id); a group without shapes is absent. */
    public List<String> groups() {
        return List.copyOf(byGroup.keySet());
    }

    /** group's shapes in manifest order; empty for an unknown group. */
    public List<OrnamentShape> shapes(String group) {
        return byGroup.getOrDefault(group, List.of());
    }
}
