package dev.hycolony.core.ornament.cutter;

import java.util.List;

/**
 * What the cutter window shows (MC DO ArchitectsCutterScreen, laid out as Hytale's crafting benches): the group tabs,
 * the group's shapes, each material slot of the chosen shape with the material chosen for it, the player's materials
 * the selected slot accepts, and the preview of what crafting gives.
 */
public record CutterView(
        List<Tab> tabs, List<ShapeButton> shapes, List<Slot> slots, List<Material> materials, Preview preview) {
    public CutterView {
        tabs = List.copyOf(tabs);
        shapes = List.copyOf(shapes);
        slots = List.copyOf(slots);
        materials = List.copyOf(materials);
    }

    /** A group tab: its id, its name key, the item shown as its icon (its first shape's template), whether open. */
    public record Tab(String group, String nameKey, String iconKey, boolean selected) {}

    /** A shape button: shown with its template item's icon and name. */
    public record ShapeButton(String shapeId, String templateKey, boolean selected) {}

    /**
     * A material slot of the chosen shape: its label key, the material chosen for it ("" when none), how many the
     * player has and how many one craft takes, and whether clicking a material fills this slot.
     */
    public record Slot(String labelKey, String itemId, int have, int need, boolean selected) {}

    /** A material the player holds that the selected slot accepts, and how many they hold. */
    public record Material(String itemId, int count) {}

    /** What crafting gives now. */
    public sealed interface Preview permits Empty, Ready, Refused {}

    /** No material chosen yet. */
    public record Empty() implements Preview {}

    /**
     * The variant item id (it may not exist yet: show the template's icon then), how many a craft gives, and how many
     * crafts the player's materials allow at most.
     */
    public record Ready(String itemId, String templateKey, int quantity, int maxCrafts) implements Preview {}

    /** Why crafting is refused: a translation key and its parameters. */
    public record Refused(String reasonKey, List<String> params) implements Preview {
        public Refused {
            params = List.copyOf(params);
        }
    }
}
