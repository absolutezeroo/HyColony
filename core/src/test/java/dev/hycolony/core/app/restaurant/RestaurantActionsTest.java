package dev.hycolony.core.app.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.furnace.FurnaceUserModule;
import dev.hycolony.core.crafting.restaurant.DiningHallHut;
import dev.hycolony.core.crafting.restaurant.RestaurantMenuModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AlterRestaurantMenuItemMessage, AssignFilterableItemMessage, and a campfire placed in a dining hall. */
class RestaurantActionsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final BlockPos hallPos = new BlockPos(6, 64, 0);
    private final ItemKey bread = t.catalog.food("bread", 6, 1);
    private final ItemKey charcoal = new ItemKey("charcoal");
    private final Colony colony;
    private final Building hall;

    RestaurantActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        colony.permissions().setRank(carol, "Carol", Permissions.FRIEND);
        hall = Building.create(DiningHallHut.TYPE, hallPos, 0);
        hall.setLevel(1);
        colony.buildings().add(hall);
        t.cooking.fuels.add(charcoal);
    }

    private RestaurantActions actions() {
        return manager.hutWindows().restaurant();
    }

    @Test
    void aManagerSetsTheMenuAndTheFuelsAndTheWindowShowsAgain() {
        assertTrue(actions().addToMenu(alice, hallPos, bread));
        assertEquals(
                Set.of(bread),
                hall.module(RestaurantMenuModule.class).orElseThrow().menu());
        assertTrue(actions().toggleFuel(alice, hallPos, charcoal));
        assertEquals(
                Set.of(charcoal),
                hall.module(FuelListModule.class).orElseThrow().fuels(colony));
        assertTrue(actions().removeFromMenu(alice, hallPos, bread));
        assertTrue(t.ui.shown.containsKey(alice)); // the hut shown again
    }

    @Test
    void resetPutsTheDefaultFuelsBack() {
        t.cooking.defaultFuels.add(charcoal);
        actions().toggleFuel(alice, hallPos, charcoal);
        assertTrue(hall.module(FuelListModule.class).orElseThrow().fuels(colony).isEmpty());

        assertFalse(actions().resetFuels(carol, hallPos));
        assertTrue(actions().resetFuels(alice, hallPos));

        assertEquals(
                Set.of(charcoal),
                hall.module(FuelListModule.class).orElseThrow().fuels(colony));
    }

    @Test
    void aFriendChangesNothing() {
        assertFalse(actions().addToMenu(carol, hallPos, bread));
        assertTrue(hall.module(RestaurantMenuModule.class).orElseThrow().menu().isEmpty());
        assertFalse(actions().toggleFuel(carol, hallPos, charcoal));
    }

    @Test
    void aCampfireAPlayerPlacesInTheHallIsItsToo() {
        BlockKey campfire = new BlockKey("campfire");
        t.cooking.stationBlocks.add(campfire);
        colony.clearDirty();
        manager.huts().placedByPlayer(hallPos.offset(1, 0, 1), new BlockKey("stone"));
        assertFalse(colony.isDirty()); // nothing kept, nothing to save

        manager.huts().placedByPlayer(hallPos.offset(1, 0, 0), campfire);
        manager.huts().placedByPlayer(new BlockPos(40, 64, 40), campfire); // outside every hut
        assertEquals(
                List.of(hallPos.offset(1, 0, 0)),
                hall.module(FurnaceUserModule.class).orElseThrow().stations());
        assertTrue(colony.isDirty());
    }
}
