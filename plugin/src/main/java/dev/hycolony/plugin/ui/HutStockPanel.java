package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;

/**
 * The hut window's inventory summary sub-view (MC WindowHutAllInventory, opened by the {@code allinventory} button of
 * every hut's main page): what the hut and its racks hold, most first. Open/closed is page state.
 *
 * <p>Deviation from MC: a sub-view of the Main tab with a Back button rather than a separate window, like Build
 * options.
 */
final class HutStockPanel {
    private final List<ItemAmount> stock;
    private boolean open;

    HutStockPanel(List<ItemAmount> stock) {
        this.stock = stock;
    }

    boolean isOpen() {
        return open;
    }

    /** Takes over {@code previous}'s open state, for a live refresh. */
    void keepStateOf(HutStockPanel previous) {
        open = previous.open;
    }

    /** Binds the Main tab's button; fills the list while open. */
    void render(UICommandBuilder ui, UIEventBuilder events) {
        ColonyPage.bind(events, "#StockButton", "stock");
        if (!open) {
            return;
        }
        ColonyPage.bind(events, "#StockBackButton", "stockBack");
        if (stock.isEmpty()) {
            ui.set("#StockEmpty.Visible", true);
            ui.set("#StockEmpty.Text", Message.translation("hycolony.ui.building.stockEmpty"));
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

    /** Opens or closes the sub-view; returns true if {@code act} was one of its buttons (the page must redraw). */
    boolean handle(ColonyPage.Act act) {
        switch (act.action) {
            case "stock" -> open = true;
            case "stockBack" -> open = false;
            default -> {
                return false;
            }
        }
        return true;
    }
}
