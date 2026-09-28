package dev.hycolony.core.ornament.cutter;

import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One player's cutter window state: the open group and the chosen shape (MC DO
 * ArchitectsCutterContainer.clickMenuButton, called by ArchitectsCutterScreen), and the view they give with the slots'
 * contents. Opening a group chooses its first shape.
 */
public final class CutterActions {
    /** Accepted materials listed at most in a refusal: a stone tag holds hundreds. */
    static final int LISTED = 12;

    /** DO-1's refusal of a material outside its slot's tag; its text starts with the /hyornament command prefix. */
    private static final String COMMAND_BAD_MATERIAL = "hycolony.ornament.badMaterial";

    /** The same refusal worded for the cutter's preview, with the same parameters. */
    private static final String BAD_MATERIAL = "hycolony.ornament.cutter.badMaterial";

    private final CutterCatalog catalog;
    private final MaterialTags tags;
    private int group;
    private int shape;

    /** A window open on catalog's first group and its first shape, checking materials against tags. */
    public CutterActions(CutterCatalog catalog, MaterialTags tags) {
        this.catalog = catalog;
        this.tags = tags;
    }

    /** The open group's index and its chosen shape's index, kept to reopen the window where the player left it. */
    public record Selection(int group, int shape) {}

    /** The current selection. */
    public Selection selection() {
        return new Selection(group, shape);
    }

    /**
     * Reopens a saved selection (MC DO ArchitectsCutterScreen groupIndexCache/variantIndexCache, re-clicked in
     * renderBg): its group when in range, then its shape, or the group's first shape when that is out of range; a group
     * out of range changes nothing.
     *
     * <p>Deviation from MC: DO never resets its variant cache, and a stale index leaves the output empty; here it falls
     * back to the group's first shape. DO's renderBg also re-clicks the group first, which already picks its first
     * variant, so its variant cache is skipped on a normal reopening; here the saved shape is restored.
     */
    public void restore(Selection saved) {
        if (saved.group() < 0 || saved.group() >= catalog.groups().size()) {
            return;
        }
        selectGroup(saved.group());
        selectShape(saved.shape());
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

    /** The window for these slot contents (the cutter's, in order; missing ones count as empty). */
    public CutterView view(List<SlotContent> slots) {
        List<String> groups = catalog.groups();
        List<CutterView.Tab> tabs = IntStream.range(0, groups.size())
                .mapToObj(i -> new CutterView.Tab(
                        groups.get(i), "hycolony.ornament.cutter.group." + groups.get(i), i == group))
                .toList();
        List<OrnamentShape> shapes = shapes();
        List<CutterView.ShapeButton> buttons = IntStream.range(0, shapes.size())
                .mapToObj(i -> new CutterView.ShapeButton(
                        shapes.get(i).id(), shapes.get(i).templateKey(), i == shape))
                .toList();
        Optional<OrnamentShape> chosen = shape();
        List<String> labels = chosen.map(s -> s.slotTags().stream()
                        .map(tag -> "hycolony.ornament.cutter.slot." + tag)
                        .toList())
                .orElse(List.of());
        CutterView.Preview preview = chosen.map(s -> preview(s, slots)).orElseGet(CutterView.Empty::new);
        return new CutterView(tabs, buttons, labels, preview);
    }

    /** The open group's shapes; empty when the catalog has no group. */
    private List<OrnamentShape> shapes() {
        List<String> groups = catalog.groups();
        return groups.isEmpty() ? List.of() : catalog.shapes(groups.get(group));
    }

    /**
     * Empty while the shape's slots are all empty, else the recipe's answer.
     *
     * <p>Deviation from MC: DO's input slots refuse a block outside their tag ({@code mayPlace}) and its output slot
     * just stays empty on a mismatch; here any item can be placed, so the preview says why nothing can be crafted.
     */
    private CutterView.Preview preview(OrnamentShape shape, List<SlotContent> slots) {
        boolean empty = IntStream.range(0, shape.slotCount())
                .allMatch(i -> i >= slots.size() || slots.get(i).isEmpty());
        if (empty) {
            return new CutterView.Empty();
        }
        return switch (CutterCraft.check(shape, slots, tags)) {
            case CutterCraft.Ready ready ->
                new CutterView.Ready(ready.key().blockTypeKey(), shape.templateKey(), ready.quantity());
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
