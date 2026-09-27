package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/** The warehouse's Stock tab (MC WindowHutAllInventory): every item it holds, most held first. */
final class WarehouseStockTab implements HutTab {
    private final List<ItemAmount> stock;

    WarehouseStockTab(List<ItemAmount> stock) {
        this.stock = stock;
    }

    @Override
    public String document() {
        return "Pages/HyColony/WarehouseStockTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.stock";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        if (stock.isEmpty()) {
            ui.set(root + " #StockEmpty.Visible", true);
            ui.set(root + " #StockEmpty.Text", Message.translation("hycolony.ui.warehouse.stockEmpty"));
        }
        String list = root + " #Stock";
        for (int i = 0; i < stock.size(); i++) {
            ItemAmount a = stock.get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/StockRow.ui");
            ui.set(row + " #Icon.ItemId", a.item().id());
            ui.set(row + " #Name.Text", ColonyPage.itemName(a.item().id()));
            ui.set(row + " #Count.Text", String.valueOf(a.count()));
        }
    }
}
