package dev.hycolony.core.ornament.cutter;

import java.util.List;

/**
 * What the cutter window shows (MC DO ArchitectsCutterScreen, laid out as Hytale's crafting benches): the group
 * tabs, the group's shapes, a label per material slot of the chosen shape and the preview of what crafting gives.
 */
public record CutterView(List<Tab> tabs, List<ShapeButton> shapes, List<String> slotLabelKeys, Preview preview) {
    public CutterView {
        tabs = List.copyOf(tabs);
        shapes = List.copyOf(shapes);
        slotLabelKeys = List.copyOf(slotLabelKeys);
    }

    /** A group tab: its id, its name key, the item shown as its icon (its first shape's template), whether open. */
    public record Tab(String group, String nameKey, String iconKey, boolean selected) {}

    /**
     * A shape button: named after its template item, shown with itemId's icon, the variant the slots make for it
     * (it may not exist yet: show the template's icon then), or its template when they make none.
     */
    public record ShapeButton(String shapeId, String templateKey, String itemId, boolean selected) {}

    /** What crafting gives now. */
    public sealed interface Preview permits Empty, Ready, Refused {}

    /** No material placed. */
    public record Empty() implements Preview {}

    /**
     * The variant item id (it may not exist yet: show the template's icon then), how many a craft gives, and how many
     * crafts the slots allow at most.
     */
    public record Ready(String itemId, String templateKey, int quantity, int maxCrafts) implements Preview {}

    /** Why crafting is refused: a translation key and its parameters. */
    public record Refused(String reasonKey, List<String> params) implements Preview {
        public Refused {
            params = List.copyOf(params);
        }
    }
}
