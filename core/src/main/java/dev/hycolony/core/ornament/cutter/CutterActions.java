package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One player's cutter window state: the open group and the chosen shape (MC DO ArchitectsCutterContainer
 * clickMenuButton, called by ArchitectsCutterScreen), the material chosen for each slot and the slot the next chosen
 * material fills. Opening a group chooses its first shape; chosen materials stay across shapes.
 *
 * <p>Deviation from MC: DO's slots hold the blocks the player puts in them; here, as in Hytale's crafting benches,
 * the player picks materials from their inventory and crafting takes them from it.
 */
public final class CutterActions {
    /** Accepted materials listed at most in a refusal: a stone tag holds hundreds. */
    static final int LISTED = 12;

    /** DO-1's refusal of a material outside its slot's tag; its text starts with the /hyornament command prefix. */
    private static final String COMMAND_BAD_MATERIAL = "hycolony.ornament.badMaterial";

    /** The same refusal worded for the cutter's preview, with the same parameters. */
    private static final String BAD_MATERIAL = "hycolony.ornament.cutter.badMaterial";

    /** DO's cutter has as many input slots as the largest component count (ArchitectsCutterContainer). */
    private static final int MAX_SLOTS = 2;

    private final CutterCatalog catalog;
    private final MaterialTags tags;
    private final String[] chosen = new String[MAX_SLOTS];
    private int group;
    private int shape;
    private int slot;

    /** A window open on catalog's first group and its first shape, checking materials against tags. */
    public CutterActions(CutterCatalog catalog, MaterialTags tags) {
        this.catalog = catalog;
        this.tags = tags;
        Arrays.fill(chosen, "");
    }

    /**
     * The open group's index. The plugin keeps it per player and replays it through selectGroup on reopening, which
     * chooses the group's first shape (MC DO ArchitectsCutterScreen groupIndexCache, replayed in renderBg through
     * clickMenuButton, which takes variant {@code get(0)}).
     */
    public int group() {
        return group;
    }

    /** Opens the group at index (and its first shape); an index out of range changes nothing. */
    public void selectGroup(int index) {
        if (index >= 0 && index < catalog.groups().size()) {
            group = index;
            shape = 0;
            slot = Math.max(0, Math.min(slot, slotCount() - 1));
        }
    }

    /** Chooses the open group's shape at index; an index out of range changes nothing. */
    public void selectShape(int index) {
        if (index >= 0 && index < shapes().size()) {
            shape = index;
            slot = Math.max(0, Math.min(slot, slotCount() - 1));
        }
    }

    /** Makes the slot at index the one the next chosen material fills; an index out of range changes nothing. */
    public void selectSlot(int index) {
        if (index >= 0 && index < slotCount()) {
            slot = index;
        }
    }

    /** Puts itemId in the selected slot, then selects the shape's next empty slot, if any. */
    public void choose(String itemId) {
        if (shape().isEmpty()) {
            return;
        }
        chosen[slot] = itemId;
        IntStream.range(0, slotCount())
                .filter(i -> chosen[i].isEmpty())
                .findFirst()
                .ifPresent(i -> slot = i);
    }

    /** The chosen shape; empty when the catalog has none. */
    public Optional<OrnamentShape> shape() {
        List<OrnamentShape> shapes = shapes();
        return shape < shapes.size() ? Optional.of(shapes.get(shape)) : Optional.empty();
    }

    /** The chosen materials as recipe slots, each holding what inventory (item id -> count) has of it. */
    public List<SlotContent> slots(Map<String, Integer> inventory) {
        return IntStream.range(0, slotCount())
                .mapToObj(i -> chosen[i].isEmpty()
                        ? SlotContent.EMPTY
                        : new SlotContent(chosen[i], inventory.getOrDefault(chosen[i], 0)))
                .toList();
    }

    /** The window for a player holding inventory (item id -> count). */
    public CutterView view(Map<String, Integer> inventory) {
        List<String> groups = catalog.groups();
        List<CutterView.Tab> tabs = IntStream.range(0, groups.size())
                .mapToObj(i -> new CutterView.Tab(
                        groups.get(i),
                        "hycolony.ornament.cutter.group." + groups.get(i),
                        catalog.shapes(groups.get(i)).getFirst().templateKey(),
                        i == group))
                .toList();
        List<OrnamentShape> shapes = shapes();
        List<CutterView.ShapeButton> buttons = IntStream.range(0, shapes.size())
                .mapToObj(i -> new CutterView.ShapeButton(
                        shapes.get(i).id(), shapes.get(i).templateKey(), i == shape))
                .toList();
        return shape().map(s -> new CutterView(
                        tabs, buttons, slotViews(s, inventory), materials(s, inventory), preview(s, inventory)))
                .orElseGet(() -> new CutterView(tabs, buttons, List.of(), List.of(), new CutterView.Empty()));
    }

    /** The open group's shapes; empty when the catalog has no group. */
    private List<OrnamentShape> shapes() {
        List<String> groups = catalog.groups();
        return groups.isEmpty() ? List.of() : catalog.shapes(groups.get(group));
    }

    private int slotCount() {
        return shape().map(OrnamentShape::slotCount).orElse(0);
    }

    /** Each slot of shape: its label, its material, how many the player has and how many one craft takes. */
    private List<CutterView.Slot> slotViews(OrnamentShape shape, Map<String, Integer> inventory) {
        return IntStream.range(0, shape.slotCount())
                .mapToObj(i -> new CutterView.Slot(
                        "hycolony.ornament.cutter.slot." + shape.slotTags().get(i),
                        chosen[i],
                        chosen[i].isEmpty() ? 0 : inventory.getOrDefault(chosen[i], 0),
                        need(shape, chosen[i]),
                        i == slot))
                .toList();
    }

    /** How many of itemId one craft of shape takes: one per slot holding it, at least one. */
    private int need(OrnamentShape shape, String itemId) {
        long uses = IntStream.range(0, shape.slotCount())
                .filter(i -> !itemId.isEmpty() && itemId.equals(chosen[i]))
                .count();
        return (int) Math.max(1, uses);
    }

    /** The player's materials the selected slot accepts, by id. */
    private List<CutterView.Material> materials(OrnamentShape shape, Map<String, Integer> inventory) {
        String tag = shape.slotTags().get(slot);
        return inventory.entrySet().stream()
                .filter(e -> e.getValue() > 0 && tags.accepts(tag, e.getKey()))
                .map(e -> new CutterView.Material(e.getKey(), e.getValue()))
                .sorted((a, b) -> a.itemId().compareTo(b.itemId()))
                .toList();
    }

    /**
     * Empty while no material is chosen, else the recipe's answer for what the player holds.
     *
     * <p>Deviation from MC: DO's input slots refuse a block outside their tag ({@code mayPlace}) and its output slot
     * just stays empty on a mismatch; here the preview says why nothing can be crafted.
     */
    private CutterView.Preview preview(OrnamentShape shape, Map<String, Integer> inventory) {
        boolean none = IntStream.range(0, shape.slotCount()).allMatch(i -> chosen[i].isEmpty());
        if (none) {
            return new CutterView.Empty();
        }
        return switch (CutterCraft.check(shape, slots(inventory), tags)) {
            case CutterCraft.Ready ready ->
                new CutterView.Ready(
                        ready.key().blockTypeKey(),
                        shape.templateKey(),
                        ready.quantity(),
                        CutterCraft.maxCrafts(ready, inventory));
            case CutterCraft.Refused refused ->
                new CutterView.Refused(previewKey(refused.reasonKey()), params(shape, refused));
        };
    }

    /** The preview's key for a refusal key: the cutter's own badMaterial text, any other key as is. */
    private static String previewKey(String reasonKey) {
        return COMMAND_BAD_MATERIAL.equals(reasonKey) ? BAD_MATERIAL : reasonKey;
    }

    /** A refusal's message parameters: the slot number (1-based) and some accepted materials, or the count. */
    private static List<String> params(OrnamentShape shape, CutterCraft.Refused refused) {
        if (refused.slot() < 0) {
            return List.of(shape.id(), String.valueOf(shape.slotCount()));
        }
        List<String> params = new ArrayList<>(List.of(String.valueOf(refused.slot() + 1)));
        // Only badMaterial's message lists accepted materials ({p1}); emptySlot's takes the slot only.
        if (COMMAND_BAD_MATERIAL.equals(refused.reasonKey())) {
            List<String> sorted = refused.allowed().stream().sorted().toList();
            String listed = String.join(", ", sorted.subList(0, Math.min(LISTED, sorted.size())));
            params.add(sorted.size() > LISTED ? listed + ", ..." : listed);
        }
        return params;
    }
}
