package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.crafting.furnace.FuelRequests;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.BrokenRequests;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.testing.food.FakeCooking;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkCook over AbstractEntityAIUsesFurnace: the campfires, then the service. */
class CookAITest extends DiningHallFixture {
    private static final ItemKey OLD_FUEL = new ItemKey("oldfuel");

    private int carried(ItemKey item) {
        return cook.inventory().count(item);
    }

    private FakeCooking.Station campfire() {
        return t.cooking.stations.get(STATION);
    }

    @Test
    void aFuelNoLongerAllowedIsTakenBack() {
        station();
        campfire().fuel.add(new ItemAmount(OLD_FUEL, 5));
        hire();
        runUntil(() -> campfire().fuel.isEmpty());
        runUntil(() -> stocked(OLD_FUEL) == 5 || carried(OLD_FUEL) == 5);
    }

    @Test
    void whatTheCampfireCookedIsTakenOutForExperienceThenStoredInTheHall() {
        station();
        menu().add(colony, hall, steak);
        campfire().output.add(new ItemAmount(steak, 20));
        hire();
        double xpBefore = cook.skills().experience(Skill.Adaptability);

        runUntil(() -> stocked(steak) == 20); // MC: dumped after each action

        assertTrue(cook.skills().experience(Skill.Adaptability) > xpBefore);
        assertTrue(cook.hunger().pending() >= 0.4 - 1e-9); // MC: two actions for one load
    }

    @Test
    void withoutAnyFuelTheWaiterAsksForAStackPerCampfire() {
        station();
        hire();
        runUntil(() -> colony.requests().byRequester(hall.requesterId()).stream()
                .anyMatch(r -> r.requestable() instanceof StackList));
        StackList asked = colony.requests().byRequester(hall.requesterId()).stream()
                .map(r -> r.requestable())
                .filter(StackList.class::isInstance)
                .map(StackList.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(new StackList(List.of(CHARCOAL), FuelRequests.FUEL, 64, 1), asked);
        run(200);
        assertEquals(
                1,
                colony.requests().byRequester(hall.requesterId()).stream()
                        .filter(r -> r.state().compareTo(RequestState.COMPLETED) <= 0)
                        .count()); // asked once
    }

    @Test
    void aFuelDeliveryNotYetReceivedIsNoLongerAnOpenRequest() {
        FuelRequests.ask(colony, hall, List.of(CHARCOAL), 1);
        Request asked = colony.requests().byRequester(hall.requesterId()).getFirst();
        assertTrue(FuelRequests.open(colony, hall));

        BrokenRequests.setState(colony.requests(), asked.token(), RequestState.COMPLETED); // delivered, not received

        assertFalse(FuelRequests.open(colony, hall)); // MC: the waiter asks again before it receives the delivery
    }

    @Test
    void withNoFuelAllowedNothingIsAsked() {
        station();
        fuel().toggle(colony, CHARCOAL);
        hire();
        run(400);
        assertTrue(colony.requests().byRequester(hall.requesterId()).isEmpty()); // MC: a complaint, an interaction
    }

    @Test
    void theWaiterMayBeCalledToEatInEveryStateButTheDump() {
        for (CookState s : CookState.values()) {
            assertEquals(s != CookState.INVENTORY_FULL, s.isOkayToEat(), s.name());
        }
    }

    @Test
    void rawFoodAndFuelAreFetchedFromTheHallAndTheCampfireFilledAndLit() {
        station();
        menu().add(colony, hall, steak);
        stock(MEAT, 10);
        stock(CHARCOAL, 70);
        hire();

        runUntil(() -> campfire().lit);

        assertEquals(List.of(new ItemAmount(MEAT, 10)), campfire().input);
        assertEquals(List.of(new ItemAmount(CHARCOAL, 64)), campfire().fuel);
    }

    @Test
    void aBurningCampfireWorksFasterWithASkilledWaiter() {
        station();
        campfire().lit = true;
        campfire().input.add(new ItemAmount(MEAT, 1));
        campfire().fuel.add(new ItemAmount(CHARCOAL, 1));
        cook.skills().set(Skill.Adaptability, 25, 0);
        hire();
        run(100);
        assertTrue(campfire().accelerated >= 4 * 4); // (25 / 10) x 2 = 4 ticks each second
    }

    @Test
    void aHungryCitizenInTheHallGetsHalfAgainTheDishItNeeds() {
        station();
        menu().add(colony, hall, steak);
        cook.inventory().insert(new ItemAmount(steak, 20), _ -> 64);
        CitizenData guest = guest(5);

        hire();
        runUntil(() -> guest.inventory().count(steak) > 0);

        assertEquals(9, guest.inventory().count(steak)); // (60 - 5) / 8 = 6, x 1.5 = 9
    }

    @Test
    void aCitizenWhoseInventoryIsFullIsFedInPlace() {
        station();
        menu().add(colony, hall, steak);
        cook.inventory().insert(new ItemAmount(steak, 20), _ -> 64);
        CitizenData guest = guest(5);
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS; i++) {
            guest.inventory().insert(new ItemAmount(new ItemKey("junk" + i), 1), _ -> 1);
        }

        hire();
        runUntil(() -> guest.saturation() > 5);

        assertEquals(CitizenData.MAX_SATURATION, guest.saturation(), 1e-9); // 7 bites of 8 from 5, capped at 60
        assertEquals(20 - 7, cook.inventory().count(steak));
    }

    @Test
    void aHurtManagerInTheHallGetsAMeal() {
        station();
        menu().add(colony, hall, steak);
        cook.inventory().insert(new ItemAmount(steak, 20), _ -> 64);
        UUID owner = colony.permissions().owner();
        t.players.online.put(owner, HALL.offset(1, 0, 1));
        t.players.health.put(owner, 30);

        hire();
        runUntil(() -> t.playerInventory.count(owner, steak) > 0);

        assertEquals(2, t.playerInventory.count(owner, steak)); // 16 nutrition at level 1: 2 steaks of 8
        assertTrue(t.notifier.sent.stream().anyMatch(s -> s.msg().key().equals("hycolony.cook.servePlayer")));
    }

    @Test
    void aCampfirePutOutWithFoodAndFuelIsLitAgain() {
        station();
        campfire().input.add(new ItemAmount(MEAT, 10));
        campfire().fuel.add(new ItemAmount(CHARCOAL, 10));
        hire();
        runUntil(() -> campfire().lit); // Hytale: a player or a full output puts a campfire out
    }

    @Test
    void aCampfireHoldingNothingThatCooksIsLeftOut() {
        station();
        campfire().input.add(new ItemAmount(new ItemKey("stone"), 1));
        campfire().fuel.add(new ItemAmount(CHARCOAL, 10));
        hire();
        run(400);
        assertFalse(campfire().lit); // Hytale would put it out again at once
    }

    @Test
    void aPositionWhoseLoadedBlockIsNoCampfireAnyMoreIsForgotten() {
        BlockKey campfireBlock = new BlockKey("campfire");
        t.cooking.stationBlocks.add(campfireBlock);
        BlockPos broken = HALL.offset(3, 0, 0);
        BlockPos unloaded = HALL.offset(-3, 0, 0);
        BlockPos unreadable = HALL.offset(0, 0, 3);
        t.blocks.blocks.put(broken, new BlockState(new BlockKey("stone"), 0));
        t.blocks.unloaded.add(unloaded);
        t.blocks.blocks.put(unreadable, new BlockState(campfireBlock, 0)); // a campfire whose contents fail to read
        furnaces().addStation(broken);
        furnaces().addStation(unloaded);
        furnaces().addStation(unreadable);
        hire();

        runUntil(() -> !furnaces().stations().contains(broken));
        run(200);

        assertEquals(List.of(unloaded, unreadable), furnaces().stations());
    }

    @Test
    void cookedFoodThatDoesNotAllFitStaysInTheCampfireForTheSameExperience() {
        station();
        campfire().output.add(new ItemAmount(steak, 20));
        t.containers.full = true; // the waiter cannot dump its junk either
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS; i++) {
            cook.inventory().insert(new ItemAmount(new ItemKey("junk" + i), 1), _ -> 1);
        }
        hire();
        double xpBefore = cook.skills().experience(Skill.Adaptability);

        runUntil(() -> cook.skills().experience(Skill.Adaptability) > xpBefore);

        assertEquals(List.of(new ItemAmount(steak, 20)), campfire().output);
    }
}
