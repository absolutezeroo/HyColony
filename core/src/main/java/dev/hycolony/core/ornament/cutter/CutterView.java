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
