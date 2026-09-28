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

    /** Draws view: tabs, shapes, slots, preview with the craft buttons, and the player's materials. */
    static void draw(UICommandBuilder ui, UIEventBuilder events, CutterView view) {
        tabs(ui, events, view.tabs());
        shapes(ui, events, view.shapes());
        slots(ui, events, view.slots());
        preview(ui, events, view.preview());
        materials(ui, events, view.materials());
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

    /** One icon button per shape of the open group, the chosen one disabled and named above the slots. */
    private static void shapes(UICommandBuilder ui, UIEventBuilder events, List<CutterView.ShapeButton> shapes) {
        for (int i = 0; i < shapes.size(); i++) {
            CutterView.ShapeButton shape = shapes.get(i);
            String button = "#Shapes[" + i + "]";
            ui.append("#Shapes", PAGES + "CutterShapeButton.ui");
            ui.set(button + " #Icon.ItemId", shape.templateKey());
            ui.set(button + ".TooltipText", itemName(shape.templateKey()));
            ui.set(button + ".Disabled", shape.selected());
            bind(events, button, "shape", i);
            if (shape.selected()) {
                ui.set("#ShapeName.Text", itemName(shape.templateKey()));
            }
        }
    }

    /** One row per material slot: its chosen material, label and have / need; the selected one disabled. */
    private static void slots(UICommandBuilder ui, UIEventBuilder events, List<CutterView.Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            CutterView.Slot slot = slots.get(i);
            String row = "#Slots[" + i + "]";
            ui.append("#Slots", PAGES + "CutterSlotButton.ui");
            ui.set(row + " #Icon.Visible", !slot.itemId().isEmpty());
            if (!slot.itemId().isEmpty()) {
                ui.set(row + " #Icon.ItemId", slot.itemId());
            }
            ui.set(row + " #Label.Text", Message.translation(slot.labelKey()));
            ui.set(
                    row + " #Count.Text",
                    Message.translation("hycolony.ornament.cutter.have")
                            .param("p0", String.valueOf(slot.have()))
                            .param("p1", String.valueOf(slot.need())));
            ui.set(row + ".Disabled", slot.selected());
            bind(events, row, "slot", i);
        }
    }

    /** The preview icon and text, and the craft buttons, each enabled only when that many crafts are possible. */
    private static void preview(UICommandBuilder ui, UIEventBuilder events, CutterView.Preview preview) {
        int max = preview instanceof CutterView.Ready ready ? ready.maxCrafts() : 0;
        switch (preview) {
            case CutterView.Empty _ -> {
                ui.set("#Preview.Visible", false);
                ui.set("#PreviewText.Text", Message.translation("hycolony.ornament.cutter.placeMaterials"));
            }
            case CutterView.Ready ready -> {
                // Deviation from MC: the template's icon until the variant exists (its icon is painted on creation).
                boolean exists = Item.getAssetMap().getAsset(ready.itemId()) != null;
                ui.set("#Preview.ItemId", exists ? ready.itemId() : ready.templateKey());
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

    /** The player's materials the selected slot accepts, each with how many they hold. */
    private static void materials(UICommandBuilder ui, UIEventBuilder events, List<CutterView.Material> materials) {
        ui.set("#NoMaterials.Visible", materials.isEmpty());
        for (int i = 0; i < materials.size(); i++) {
            CutterView.Material material = materials.get(i);
            String button = "#Materials[" + i + "]";
            ui.append("#Materials", PAGES + "CutterMaterialButton.ui");
            ui.set(button + " #Icon.ItemId", material.itemId());
            ui.set(button + " #Count.Text", String.valueOf(material.count()));
            ui.set(button + ".TooltipText", itemName(material.itemId()));
            bind(events, button, "material", i);
        }
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
