package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/**
 * The citizen's Inventory tab: its items, and a button opening the citizen's container (MC OpenInventoryMessage).
 *
 * <p>Deviation from MC: MC's Inventory tab opens the container straight away; here the tab still lists the items, read
 * without the permission the container needs, and its button opens the container.
 */
final class CitizenInventoryTab {
    private CitizenInventoryTab() {}

    static void render(UICommandBuilder ui, UIEventBuilder events, List<ItemAmount> items) {
        ColonyPage.bind(events, "#OpenInventoryButton", "openInventory");
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
