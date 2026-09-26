package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/**
 * The citizen's Inventory tab, read only.
 *
 * <p>Deviation from MC: MC's tab opens the citizen's container (OpenInventoryMessage); opening an NPC's inventory is
 * not verified on Hytale, so the items are listed.
 */
final class CitizenInventoryTab {
    private CitizenInventoryTab() {}

    static void render(UICommandBuilder ui, List<ItemAmount> items) {
        if (items.isEmpty()) {
            ui.set("#InventoryEmpty.Visible", true);
            ui.set("#InventoryEmpty.Text", Message.translation("hycolony.ui.citizen.inventoryEmpty"));
        }
        for (int i = 0; i < items.size(); i++) {
            String row = "#Inventory[" + i + "]";
            ui.append("#Inventory", "Pages/HyColony/ResourceRow.ui");
            ui.set(row + " #Icon.ItemId", items.get(i).item().id());
            ui.set(row + " #Name.Text", ColonyPage.itemName(items.get(i).item().id()));
            ui.set(row + " #Count.Text", String.valueOf(items.get(i).count()));
            ui.set(row + " #AddButton.Visible", false);
        }
    }
}
