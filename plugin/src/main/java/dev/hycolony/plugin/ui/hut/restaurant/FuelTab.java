package dev.hycolony.plugin.ui.hut.restaurant;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.crafting.furnace.FuelListView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.HutTab;
import java.util.UUID;

/**
 * A furnace user's Fuel page (MC ItemListModuleWindow for FUEL_LIST): a filter, then each fuel with its On/Off switch,
 * the allowed ones first; Reset to default goes back to MC's defaults. The core checks MANAGE_HUTS and re-shows.
 */
public final class FuelTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final FuelListView view;
    private String filter = "";

    public FuelTab(ColonyManager manager, UUID player, BlockPos hut, FuelListView view) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Fuel.ui";
    }

    @Override
    public String icon() {
        return "fuel";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.fuel";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Desc.Text", Message.translation(descKey()));
        ui.set(root + " #Filter.Value", filter);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                root + " #Filter",
                EventData.of("Action", "fuelFilter").append("@Name", root + " #Filter.Value"),
                false);
        ColonyPage.bind(events, root + " #Reset", "fuelReset");
        int line = 0;
        for (FuelListView.Row r : view.rows()) {
            if (!ItemNames.matches(player, r.item(), filter)) {
                continue;
            }
            String row = root + " #Fuels[" + line++ + "]";
            ui.append(root + " #Fuels", "Pages/HyColony/Mc/FuelRow.ui");
            ui.set(row + " #Icon.ItemId", r.item().id());
            ui.set(row + " #Name.TextSpans", ColonyPage.itemName(r.item().id()));
            ui.set(row + " #Switch.Text", Message.translation("hycolony.ui.fuel." + (r.allowed() ? "on" : "off")));
            ColonyPage.bindRef(events, row + " #Switch", "fuelToggle", r.item().id());
        }
    }

    /** The switch of a fuel, Reset to default, or the filter. The core re-shows the window after a change. */
    @Override
    public void handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "fuelToggle" -> manager.hutWindows().restaurant().toggleFuel(player, hut, new ItemKey(act.ref()));
            case "fuelReset" -> manager.hutWindows().restaurant().resetFuels(player, hut);
            case "fuelFilter" -> filter = act.name();
            default -> {} // BuildingPage offers every action to every tab
        }
    }

    /** The filter changes the list only: nothing in the core, so the page redraws itself. */
    @Override
    public boolean redraws(ColonyPage.Act act) {
        return act.action().equals("fuelFilter");
    }
}
