package dev.hycolony.plugin.ui.logistics;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.colony.ui.logistics.WarehouseTabs;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/**
 * The warehouse's Couriers (MC CourierAssignmentModuleView), Stock (MC WindowHutAllInventory) and Tasks (MC
 * WarehouseRequestTaskModuleView) tabs.
 *
 * <p>Deviation from MC: the couriers list only shows; MC's assignment window also lets the player attach or detach a
 * courier and cycle the hiring mode, here the core attaches every courier automatically.
 */
public final class WarehouseTabsRenderer {
    private WarehouseTabsRenderer() {}

    /** Fills the three tabs' groups of {@code Building.ui}. */
    public static void render(UICommandBuilder ui, WarehouseTabs w) {
        ui.set(
                "#CouriersCount.Text",
                Message.translation("hycolony.ui.warehouse.couriers")
                        .param("p0", String.valueOf(w.couriers().size()))
                        .param("p1", String.valueOf(w.maxCouriers())));
        if (w.couriers().isEmpty()) {
            ui.set("#CouriersEmpty.Visible", true);
            ui.set("#CouriersEmpty.Text", Message.translation("hycolony.ui.warehouse.noCouriers"));
        }
        for (int i = 0; i < w.couriers().size(); i++) {
            String row = "#Couriers[" + i + "]";
            ui.append("#Couriers", "Pages/HyColony/WorkerRow.ui");
            ui.set(row + " #Name.Text", w.couriers().get(i));
            ui.set(row + " #Button.Visible", false);
        }
        stock(ui, w.stock());
        TaskRows.render(ui, "#WarehouseTasks", "#WarehouseTasksEmpty", w.queue());
    }

    private static void stock(UICommandBuilder ui, List<ItemAmount> stock) {
        if (stock.isEmpty()) {
            ui.set("#StockEmpty.Visible", true);
            ui.set("#StockEmpty.Text", Message.translation("hycolony.ui.warehouse.stockEmpty"));
        }
        for (int i = 0; i < stock.size(); i++) {
            ItemAmount a = stock.get(i);
            String row = "#Stock[" + i + "]";
            ui.append("#Stock", "Pages/HyColony/StockRow.ui");
            ui.set(row + " #Icon.ItemId", a.item().id());
            ui.set(row + " #Name.Text", ColonyPage.itemName(a.item().id()));
            ui.set(row + " #Count.Text", String.valueOf(a.count()));
        }
    }
}
