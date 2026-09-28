package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.cutter.CutterView;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.List;

/** Draws a {@link CutterView} into Cutter.ui and binds its buttons to CutterPage's actions. */
final class CutterDrawing {
    private static final String PAGES = "Pages/HyColony/";

    private CutterDrawing() {}

    /**
     * Draws view: tabs, shapes, the slots' labels, and the preview with the craft buttons; preparing shows a spinner
     * in place of a previewed variant that does not exist yet.
     */
    static void draw(UICommandBuilder ui, UIEventBuilder events, CutterView view, boolean preparing) {
        tabs(ui, events, view.tabs());
        shapes(ui, events, view.shapes());
        slotLabels(ui, view.slotLabelKeys());
        preview(ui, events, view.preview(), preparing);
    }

    /** One icon tab per group, the open one marked and named above the shapes. */
    private static void tabs(UICommandBuilder ui, UIEventBuilder events, List<CutterView.Tab> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            CutterView.Tab tab = tabs.get(i);
            String button = "#TabButtons[" + i + "]";
            ui.append("#TabButtons", PAGES + "CutterTabButton.ui");
            ui.set(button + " #Icon.ItemId", tab.iconKey());
            ui.set(button + " #Selected.Visible", tab.selected());
            ui.set(button + ".TooltipText", Message.translation(tab.nameKey()));
            bind(events, button, "group", i);
            if (tab.selected()) {
                ui.set("#GroupName.Text", Message.translation(tab.nameKey()));
            }
        }
    }

    /**
     * One icon button per shape of the open group, in the slots' materials once their variant exists, the chosen one
     * disabled and named above the slots.
     */
    private static void shapes(UICommandBuilder ui, UIEventBuilder events, List<CutterView.ShapeButton> shapes) {
        for (int i = 0; i < shapes.size(); i++) {
            CutterView.ShapeButton shape = shapes.get(i);
            String button = "#Shapes[" + i + "]";
            ui.append("#Shapes", PAGES + "CutterShapeButton.ui");
            ui.set(button + " #Icon.ItemId", shownItem(shape.itemId(), shape.templateKey()));
            ui.set(button + ".TooltipText", itemName(shape.templateKey()));
            ui.set(button + ".Disabled", shape.selected());
            bind(events, button, "shape", i);
            if (shape.selected()) {
                ui.set("#ShapeName.Text", itemName(shape.templateKey()));
            }
        }
    }

    /** Names each material slot the chosen shape uses (« Cadre », « Centre »…); a slot it does not use is unnamed. */
    private static void slotLabels(UICommandBuilder ui, List<String> keys) {
        for (int i = 0; i < CutterSlots.COUNT; i++) {
            // An empty text, not a hidden label: a hidden one leaves the layout and shifts the other under slot 2.
            if (i < keys.size()) {
                ui.set("#SlotLabel" + i + ".Text", Message.translation(keys.get(i)));
            } else {
                ui.set("#SlotLabel" + i + ".Text", "");
            }
        }
    }

    /** The preview icon and text, and the craft buttons, each enabled only when that many crafts are possible. */
    private static void preview(
            UICommandBuilder ui, UIEventBuilder events, CutterView.Preview preview, boolean preparing) {
        int max = preview instanceof CutterView.Ready ready ? ready.maxCrafts() : 0;
        switch (preview) {
            case CutterView.Empty _ -> {
                ui.set("#Preview.Visible", false);
                ui.set("#PreviewText.Text", Message.translation("hycolony.ornament.cutter.placeMaterials"));
            }
            case CutterView.Ready ready -> {
                // Deviation from MC: a spinner, then the template's icon if creating fails, for the moment the
                // variant takes to be created (CutterPreviewVariants asks for it once the slots stay unchanged).
                boolean waits = preparing && Item.getAssetMap().getAsset(ready.itemId()) == null;
                ui.set("#Preview.Visible", !waits);
                ui.set("#PreviewSpinner.Visible", waits);
                ui.set("#Preview.ItemId", shownItem(ready.itemId(), ready.templateKey()));
                ui.set(
                        "#PreviewText.Text",
                        Message.translation("hycolony.ornament.cutter.quantity")
                                .param("p0", String.valueOf(ready.quantity())));
            }
            case CutterView.Refused refused -> {
                ui.set("#Preview.Visible", false);
                ui.set(
                        "#PreviewText.Text",
                        HytaleNotifier.toMessage(
                                Msg.of(refused.reasonKey(), refused.params().toArray(String[]::new))));
            }
        }
        ui.set("#CraftButton.Disabled", max < 1);
        ui.set("#Craft10Button.Disabled", max < 10);
        ui.set("#CraftAllButton.Disabled", max < 1);
        bind(events, "#CraftButton", "craft", -1);
        bind(events, "#Craft10Button", "craft10", -1);
        bind(events, "#CraftAllButton", "craftAll", -1);
    }

    /** itemId when it exists (a variant is created on demand), else templateKey. */
    private static String shownItem(String itemId, String templateKey) {
        return Item.getAssetMap().getAsset(itemId) != null ? itemId : templateKey;
    }

    /** The item's translated name; its id when it is not loaded. */
    static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : item.getTranslationMessage();
    }

    /** Binds a button's click to an action and its list index. */
    private static void bind(UIEventBuilder events, String selector, String action, int index) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action).append("Index", String.valueOf(index)),
                false);
    }
}
