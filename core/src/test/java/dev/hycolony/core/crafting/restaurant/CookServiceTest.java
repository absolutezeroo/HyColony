package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkCook.checkForImportantJobs and the serving: who the waiter serves, and who it does not. */
class CookServiceTest extends DiningHallFixture {
    private static final int WHILE = 600;
    private static final int HURT = 30;

    /** A campfire, steak on the menu and 20 steaks on the waiter. */
    private void readyToServe() {
        station();
        menu().add(colony, hall, steak);
        cook.inventory().insert(new ItemAmount(steak, 20), _ -> 64);
    }

    private UUID inHall(UUID player, int health) {
        t.players.online.put(player, HALL.offset(1, 0, 1));
        t.players.health.put(player, health);
        return player;
    }

    @Test
    void aPlayerWhoMayNotManageHutsIsNotServed() {
        readyToServe();
        UUID stranger = inHall(UUID.randomUUID(), HURT);
        hire();
        run(WHILE);
        assertEquals(0, t.playerInventory.count(stranger, steak));
    }

    @Test
    void aManagerAboveHalfHealthIsNotServed() {
        readyToServe();
        UUID owner = inHall(colony.permissions().owner(), CookService.PLAYER_HEALTH_PERCENT);
        hire();
        run(WHILE);
        assertEquals(0, t.playerInventory.count(owner, steak));
    }

    @Test
    void aManagerAlreadyCarryingMenuFoodGetsNoMore() {
        readyToServe();
        UUID owner = inHall(colony.permissions().owner(), HURT);
        t.playerInventory.give(owner, new ItemAmount(steak, 1));
        hire();
        run(WHILE);
        assertEquals(1, t.playerInventory.count(owner, steak));
        assertTrue(t.notifier.sent.isEmpty());
    }

    @Test
    void aManagerWhoCanTakeNothingIsPassedAndTheNextOneServed() {
        readyToServe();
        UUID owner = inHall(colony.permissions().owner(), HURT);
        t.playerInventory.full.add(owner);
        UUID officer = UUID.randomUUID();
        colony.permissions().addPlayer(officer, "B", Permissions.OFFICER);
        inHall(officer, HURT);
        hire();
        runUntil(() -> t.playerInventory.count(officer, steak) > 0);
    }

    @Test
    void aWorkingCitizenIsNotServed() {
        readyToServe();
        CitizenData guest = guest(5);
        guest.setJob(new BuilderJob(guest));
        guest.job().orElseThrow().setWorking(colony, true);
        hire();
        run(WHILE);
        assertEquals(0, guest.inventory().count(steak));
    }

    @Test
    void aCitizenThatJustAteIsNotServed() {
        readyToServe();
        CitizenData guest = guest(5);
        guest.hunger().setJustAte(true);
        hire();
        run(WHILE);
        assertEquals(0, guest.inventory().count(steak));
    }

    @Test
    void aCitizenCarryingMenuFoodIsNotServedAgain() {
        readyToServe();
        CitizenData guest = guest(5);
        guest.inventory().insert(new ItemAmount(steak, 1), _ -> 64);
        hire();
        for (int i = 0; i < WHILE; i++) {
            run(1);
            assertFalse(ai.stateName().contains("SERVE"), ai.stateName()); // not even queued
        }
        assertEquals(1, guest.inventory().count(steak));
    }

    @Test
    void aDishThatDoesNotAllFitIsNotHandedOver() {
        readyToServe();
        t.catalog.maxStacks.put(steak, 4); // 9 steaks need three slots
        CitizenData guest = guest(5);
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS - 1; i++) {
            guest.inventory().insert(new ItemAmount(new ItemKey("junk" + i), 1), _ -> 1);
        }
        hire();
        run(WHILE);
        assertEquals(0, guest.inventory().count(steak));
        assertEquals(20, cook.inventory().count(steak));
    }

    @Test
    void theWaiterDoesNotServeItself() {
        station();
        menu().add(colony, hall, steak);
        stock(steak, 10);
        hire();
        cook.setSaturation(5);
        for (int i = 0; i < WHILE; i++) {
            run(1);
            assertEquals(0, cook.inventory().count(steak));
        }
    }
}
