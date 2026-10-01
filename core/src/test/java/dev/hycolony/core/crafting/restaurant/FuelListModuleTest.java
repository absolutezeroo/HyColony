package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.furnace.FuelListView;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.HutKeep;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MC ItemListModule FUEL_LIST and BuildingCook.buildingRequiresCertainAmountOfItem, tested in the dining hall (its only
 * hut, and the fixture's).
 */
class FuelListModuleTest extends DiningHallFixture {
    private static final ItemKey LOG = new ItemKey("log");

    FuelListModuleTest() {
        t.cooking.fuels.add(LOG);
    }

    @Test
    void theDefaultsUntilAPlayerChangesTheList() {
        assertEquals(Set.of(CHARCOAL), fuel().fuels(colony));
        fuel().toggle(colony, LOG);
        assertEquals(Set.of(CHARCOAL, LOG), fuel().fuels(colony));
        fuel().toggle(colony, CHARCOAL);
        fuel().toggle(colony, new ItemKey("stone")); // burns not: ignored
        assertEquals(Set.of(LOG), fuel().fuels(colony));

        JsonObject saved = new JsonObject();
        fuel().write(saved);
        FuelListModule back = new FuelListModule();
        back.read(saved);
        assertEquals(Set.of(LOG), back.fuels(colony));
    }

    @Test
    void theTabListsEveryFuelAndWhetherItIsAllowed() {
        assertEquals(
                new FuelListView(List.of(new FuelListView.Row(CHARCOAL, true), new FuelListView.Row(LOG, false))),
                fuel().tab(colony, hall, UUID.randomUUID()));
    }

    @Test
    void aCourierTakesNoFuelAndTheWaiterKeepsAStack() {
        HutKeep pickup = HutKeep.of(colony, hall, false);
        assertEquals(0, pickup.removable(new ItemAmount(CHARCOAL, 500)));
        HutKeep dump = HutKeep.of(colony, hall, true);
        assertEquals(36, dump.removable(new ItemAmount(CHARCOAL, 100)));
        assertEquals(5, dump.removable(new ItemAmount(LOG, 5)));
        assertFalse(fuel().allows(colony, LOG));
    }

    @Test
    void resetGoesBackToTheDefaults() {
        fuel().toggle(colony, LOG);
        fuel().toggle(colony, CHARCOAL);
        fuel().reset();
        assertEquals(Set.of(CHARCOAL), fuel().fuels(colony));
        t.cooking.defaultFuels.add(LOG); // the catalog's defaults again, not a copy
        assertEquals(Set.of(CHARCOAL, LOG), fuel().fuels(colony));
    }
}
