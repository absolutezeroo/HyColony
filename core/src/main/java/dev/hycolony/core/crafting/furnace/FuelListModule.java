package dev.hycolony.core.crafting.furnace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The fuels a hut's furnace user may burn (MC ItemListModule FUEL_LIST): the catalog's defaults (MC coal and charcoal)
 * until a player changes the list. A courier never takes an allowed fuel from the hut, and the worker keeps
 * {@link #CARRIED_FUEL} on a dump (MC BuildingCook.buildingRequiresCertainAmountOfItem). Deviation from MC: MC keeps
 * whole stacks while less than 64 are kept (up to 127); HyColony's keep rules count items, so exactly 64 stay.
 */
public final class FuelListModule implements PersistentModule, KeepsItems, ProvidesTab {
    /** MC STACKSIZE: the allowed fuel a worker keeps on itself when it dumps. */
    static final int CARRIED_FUEL = 64;

    private final Set<ItemKey> fuels = new LinkedHashSet<>();
    /** Whether a player changed the list: until then it is the catalog's defaults (MC's constructor defaults). */
    private boolean edited;

    /** The allowed fuels, in the order they were allowed. */
    public Set<ItemKey> fuels(Colony colony) {
        if (!edited) {
            return new LinkedHashSet<>(
                    colony.context().ports().cooking().catalog().defaultFuels());
        }
        return new LinkedHashSet<>(fuels);
    }

    /** MC ItemListModule.isItemInList. */
    public boolean allows(Colony colony, ItemKey item) {
        return fuels(colony).contains(item);
    }

    /**
     * MC AssignFilterableItemMessage: allows {@code item}, or no longer allows it; an item that does not burn is
     * ignored.
     */
    public void toggle(Colony colony, ItemKey item) {
        if (!colony.context().ports().cooking().catalog().fuels().contains(item)) {
            return;
        }
        if (!edited) {
            fuels.addAll(fuels(colony));
            edited = true;
        }
        if (!fuels.remove(item)) {
            fuels.add(item);
        }
    }

    /** MC ResetFilterableItemMessage: back to the catalog's defaults. */
    public void reset() {
        fuels.clear();
        edited = false;
    }

    /** The courier leaves every allowed fuel in the hut; a dumping worker keeps {@link #CARRIED_FUEL}. */
    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        Set<ItemKey> allowed = fuels(colony);
        return List.of(
                new KeepRule(allowed::contains, Integer.MAX_VALUE, false),
                new KeepRule(allowed::contains, CARRIED_FUEL, true));
    }

    /** MC ItemListModuleView: every fuel of the catalog, the allowed ones first (MC applySorting). */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        Set<ItemKey> allowed = fuels(colony);
        return new FuelListView(colony.context().ports().cooking().catalog().fuels().stream()
                .map(f -> new FuelListView.Row(f, allowed.contains(f)))
                .sorted(Comparator.comparing(r -> !r.allowed()))
                .toList());
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("edited", edited);
        JsonArray arr = new JsonArray();
        fuels.forEach(f -> arr.add(f.id()));
        out.add("fuels", arr);
    }

    /** Tolerant (CLAUDE.md § 5): a non-string entry is skipped. */
    @Override
    public void read(JsonObject in) {
        fuels.clear();
        edited = SavedJson.boolOr(in.get("edited"), false);
        for (JsonElement e : SavedJson.arrayOr(in.get("fuels"))) {
            if (e instanceof JsonPrimitive p && p.isString()) {
                fuels.add(new ItemKey(p.getAsString()));
            }
        }
    }
}
