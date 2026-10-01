package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessIds;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen.eatFoodInteraction: a player feeding a citizen by hand. */
class HandFeedingTest {
    private final TestContexts t = new TestContexts();
    private final ItemKey raw = t.catalog.food("meat", 3, 0);
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));

    HandFeedingTest() {
        t.catalog.cooked.put(raw, new ItemKey("steak")); // raw food is still fed by hand (MC ISFOOD)
        citizen.setSaturation(20);
    }

    @Test
    void anyFoodIsEatenAndNoted() {
        assertEquals(HandFeeding.Outcome.FED, HandFeeding.feed(colony, citizen, raw));
        assertEquals(23, citizen.saturation());
        assertEquals(List.of(raw), citizen.hunger().history().foods());
    }

    @Test
    void aTierThreeDishPleasesForFiveDaysAndALesserOneDoesNot() {
        HandFeeding.feed(colony, citizen, t.catalog.food("stew", 10, 2));
        assertTrue(citizen.happiness().get(HappinessIds.HADGREATFOOD).isEmpty());
        t.clock.tick += HandFeeding.FED_COOLDOWN_TICKS;

        HandFeeding.feed(colony, citizen, t.catalog.food("feast", 12, 3));

        assertTrue(citizen.happiness().get(HappinessIds.HADGREATFOOD).isPresent()); // MC consumeFood
    }

    @Test
    void nonFoodOpensTheWindowInstead() {
        assertEquals(HandFeeding.Outcome.NOT_FOOD, HandFeeding.feed(colony, citizen, new ItemKey("stone")));
    }

    @Test
    void aSecondOfferWithinFiveSecondsIsRefused() {
        HandFeeding.feed(colony, citizen, raw);
        t.clock.tick += HandFeeding.FED_COOLDOWN_TICKS - 1;
        assertEquals(HandFeeding.Outcome.NOT_NOW, HandFeeding.feed(colony, citizen, raw));
        t.clock.tick += 1;
        assertEquals(HandFeeding.Outcome.FED, HandFeeding.feed(colony, citizen, raw));
    }

    @Test
    void poisonIsSwallowedForNothingAndKeepsItBusyLonger() {
        ItemKey shroom = new ItemKey("shroom");
        t.catalog.foods.put(shroom, new FoodInfo(1, 0, true));
        assertEquals(HandFeeding.Outcome.POISONED, HandFeeding.feed(colony, citizen, shroom));
        assertEquals(20, citizen.saturation());
        t.clock.tick += HandFeeding.FED_COOLDOWN_TICKS;
        assertEquals(HandFeeding.Outcome.NOT_NOW, HandFeeding.feed(colony, citizen, raw));
        t.clock.tick += HandFeeding.POISON_COOLDOWN_TICKS;
        assertEquals(HandFeeding.Outcome.FED, HandFeeding.feed(colony, citizen, raw));
    }
}
