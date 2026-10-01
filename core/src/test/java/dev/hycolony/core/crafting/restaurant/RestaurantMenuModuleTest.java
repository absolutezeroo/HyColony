package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.request.BrokenRequests;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC RestaurantMenuModule: the menu, its stock requests and what the hall keeps. */
class RestaurantMenuModuleTest extends DiningHallFixture {
    private List<StackRequest> asked() {
        return colony.requests().byRequester(hall.requesterId()).stream()
                .filter(r -> r.state().isBefore(RequestState.COMPLETED))
                .map(Request::requestable)
                .filter(StackRequest.class::isInstance)
                .map(StackRequest.class::cast)
                .toList();
    }

    @Test
    void onlyFoodCitizensMayEatJoinsTheMenuFivePerLevel() {
        assertFalse(menu().add(colony, hall, MEAT)); // raw: it cooks
        assertFalse(menu().add(colony, hall, new ItemKey("stone")));
        for (int i = 0; i < RestaurantMenuModule.STOCK_PER_LEVEL; i++) {
            assertTrue(menu().add(colony, hall, t.catalog.food("food" + i, 4, 0)));
        }
        assertTrue(menu().full(hall));
        assertFalse(menu().add(colony, hall, steak)); // MC hasReachedLimit
        hall.setLevel(2);
        assertTrue(menu().add(colony, hall, steak));
    }

    @Test
    void eachTickAsksForTheDishThenForItsRawFoodUntilTheHallHoldsAStackALevel() {
        menu().add(colony, hall, steak);

        menu().onColonyTick(colony, hall);
        assertEquals(List.of(new StackRequest(steak, 64, 1, false)), asked());

        menu().onColonyTick(colony, hall); // the dish's request is still open: now the raw food
        assertEquals(List.of(new StackRequest(steak, 64, 1, false), new StackRequest(MEAT, 64, 1, false)), asked());

        stock(steak, 40);
        stock(MEAT, 30);
        menu().onColonyTick(colony, hall);
        assertTrue(asked().isEmpty()); // 40 + 30 >= 64: both cancelled
    }

    @Test
    void aDeliveredRequestNotYetReceivedDoesNotStopTheHallAskingAgain() {
        menu().add(colony, hall, pie); // no raw item: only the dish is asked
        menu().onColonyTick(colony, hall);
        Request first = colony.requests().byRequester(hall.requesterId()).getFirst();
        colony.requests().updateState(first.token(), RequestState.COMPLETED); // no waiter receives it

        menu().onColonyTick(colony, hall);

        assertEquals(List.of(new StackRequest(pie, 64, 1, false)), asked()); // MC: a completed request is not open
    }

    @Test
    void theRawFoodIsAskedOnlyWhileTheDishRequestIsNotPastInProgress() {
        menu().add(colony, hall, steak);
        menu().onColonyTick(colony, hall);
        Request dish = colony.requests().byRequester(hall.requesterId()).getFirst();
        BrokenRequests.setState(colony.requests(), dish.token(), RequestState.FOLLOWUP_IN_PROGRESS);

        menu().onColonyTick(colony, hall);
        assertEquals(List.of(new StackRequest(steak, 64, 1, false)), asked()); // still open, past IN_PROGRESS

        BrokenRequests.setState(colony.requests(), dish.token(), RequestState.IN_PROGRESS);
        menu().onColonyTick(colony, hall);
        assertEquals(List.of(new StackRequest(steak, 64, 1, false), new StackRequest(MEAT, 64, 1, false)), asked());
    }

    @Test
    void nothingIsAskedWhileTheHallIsNotLoaded() {
        menu().add(colony, hall, steak);
        t.world.unloaded.add(HALL);
        menu().onColonyTick(colony, hall);
        assertTrue(asked().isEmpty());
    }

    @Test
    void aDishAlreadyOnTheMenuIsNotAddedTwice() {
        assertTrue(menu().add(colony, hall, steak));
        assertFalse(menu().add(colony, hall, steak));
        assertEquals(Set.of(steak), menu().menu());
    }

    @Test
    void aSmallerGapAsksForLess() {
        menu().add(colony, hall, pie);
        stock(pie, 50);
        menu().onColonyTick(colony, hall);
        assertEquals(List.of(new StackRequest(pie, 14, 1, false)), asked());
    }

    @Test
    void takingADishOffTheMenuCancelsItsRequest() {
        menu().add(colony, hall, pie);
        menu().onColonyTick(colony, hall);
        assertTrue(menu().remove(colony, hall, pie));
        assertTrue(asked().isEmpty());
        assertFalse(menu().remove(colony, hall, pie));
    }

    @Test
    void theHallKeepsAStackALevelOfEachDishAndItsRawFood() {
        hall.setLevel(2);
        menu().add(colony, hall, steak);
        List<KeepRule> rules = menu().keepRules(colony, hall);
        assertEquals(2, rules.size());
        assertTrue(rules.get(0).matches().test(steak));
        assertEquals(128, rules.get(0).amount());
        assertTrue(rules.get(1).matches().test(MEAT));
        assertFalse(rules.get(0).inventory());
    }

    @Test
    void theMenuIsSavedAndAFoodNoLongerEdibleLeavesIt() {
        menu().add(colony, hall, steak);
        menu().add(colony, hall, pie);
        JsonObject saved = new JsonObject();
        menu().write(saved);
        RestaurantMenuModule back = new RestaurantMenuModule();
        back.read(saved);
        assertEquals(Set.of(steak, pie), back.menu());

        t.catalog.foods.remove(pie);
        back.onColonyTick(colony, hall);
        assertEquals(Set.of(steak), back.menu());
    }

    @Test
    void theHallIsStaffedOnceItHasAWaiter() {
        assertFalse(menu().hasWaiter(colony, hall));
        hire();
        assertTrue(menu().hasWaiter(colony, hall));
    }
}
