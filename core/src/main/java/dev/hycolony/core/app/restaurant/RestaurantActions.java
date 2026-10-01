package dev.hycolony.core.app.restaurant;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.ManagedHut;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.restaurant.RestaurantMenuModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.UUID;

/**
 * A dining hall window's buttons (MC AlterRestaurantMenuItemMessage and AssignFilterableItemMessage): the menu's
 * {@code <<} and X, the Fuel tab's toggles. Each needs MANAGE_HUTS (a refusal is told) and re-shows the hut's window
 * after a change; a full menu or a food citizens may not eat changes nothing, silently as MC.
 */
public final class RestaurantActions {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public RestaurantActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /** MC addMenuItem: {@code food} joins the hall's menu; false without the right, a hall, or room for it. */
    public boolean addToMenu(UUID player, BlockPos hutPos, ItemKey food) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        RestaurantMenuModule menu = h == null
                ? null
                : h.building().module(RestaurantMenuModule.class).orElse(null);
        if (h == null || menu == null || !menu.add(h.colony(), h.building(), food)) {
            return false;
        }
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** MC removeMenuItem: {@code food} leaves the hall's menu, its request cancelled; false if it was not on it. */
    public boolean removeFromMenu(UUID player, BlockPos hutPos, ItemKey food) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        RestaurantMenuModule menu = h == null
                ? null
                : h.building().module(RestaurantMenuModule.class).orElse(null);
        if (h == null || menu == null || !menu.remove(h.colony(), h.building(), food)) {
            return false;
        }
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** MC AssignFilterableItemMessage on the fuel list: allows {@code fuel}, or no longer; false without the right. */
    public boolean toggleFuel(UUID player, BlockPos hutPos, ItemKey fuel) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        FuelListModule fuels =
                h == null ? null : h.building().module(FuelListModule.class).orElse(null);
        if (h == null || fuels == null) {
            return false;
        }
        fuels.toggle(h.colony(), fuel);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** MC ResetFilterableItemMessage: the fuel list goes back to its defaults; false without the right. */
    public boolean resetFuels(UUID player, BlockPos hutPos) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        FuelListModule fuels =
                h == null ? null : h.building().module(FuelListModule.class).orElse(null);
        if (h == null || fuels == null) {
            return false;
        }
        fuels.reset();
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }
}
