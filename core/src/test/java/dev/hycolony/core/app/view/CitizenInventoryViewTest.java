package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The citizen inventory page's side panel: its name, stats and hands (spec citizen inventory, § 4). */
class CitizenInventoryViewTest {
    private static final ItemKey PICK = new ItemKey("Tool_Pickaxe_Iron");
    private static final ItemKey TORCH = new ItemKey("Furniture_Crude_Torch");
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final CitizenData d = new CitizenData(1);

    CitizenInventoryViewTest() {
        d.setName("Bob");
        colony.citizens().restore(d);
    }

    @Test
    void aLivingBodyGivesItsHealthAndMaximumAndItsDefense() {
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        colony.citizens().onBodyLoaded(body, d.id());
        t.bodies.bodies.get(body).health = 13.6;
        t.bodies.bodies.get(body).maxHealth = 24;
        t.bodies.bodies.get(body).defensePercent = 24;
        d.setSaturation(30.7);

        CitizenInventoryView v = CitizenInventoryView.of(colony, d);

        assertEquals("Bob", v.name());
        assertEquals(13, v.health(), "MC CitizenDataView.getHealth: the entity's own, cast as MC casts it");
        assertEquals(24, v.maxHealth(), "MC CitizenDataView.getMaxHealth");
        assertEquals(24, v.defensePercent());
        assertEquals(30, v.saturation());
        assertEquals(60, v.maxSaturation(), "MC ICitizenData.MAX_SATURATION");
    }

    @Test
    void withoutABodyItIsWholeAndUnarmoured() {
        CitizenInventoryView v = CitizenInventoryView.of(colony, d);

        assertEquals(CitizenData.MC_MAX_HEALTH, v.health(), "MC: MAX_HEALTH without its entity");
        assertEquals(CitizenData.MC_MAX_HEALTH, v.maxHealth());
        assertEquals(0, v.defensePercent());
    }

    @Test
    void aDeadOrUnloadedBodyCountsAsNone() {
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        colony.citizens().onBodyLoaded(body, d.id());
        t.bodies.bodies.get(body).health = 3;
        t.bodies.bodies.get(body).defensePercent = 24;
        t.bodies.bodies.get(body).alive = false;

        CitizenInventoryView v = CitizenInventoryView.of(colony, d);

        assertEquals(CitizenData.MC_MAX_HEALTH, v.health());
        assertEquals(0, v.defensePercent());
    }

    @Test
    void theHandsShowTheItemsOfTheSlotsTheyHold() {
        d.inventory().set(4, Optional.of(new ItemAmount(PICK, 1, 3)));
        d.inventory().set(7, Optional.of(new ItemAmount(TORCH, 12)));
        d.equipment().hold(Hand.MAIN, 4);
        d.equipment().hold(Hand.OFF, 7);

        CitizenInventoryView v = CitizenInventoryView.of(colony, d);

        assertEquals(Optional.of(new ItemAmount(PICK, 1, 3)), v.mainHand());
        assertEquals(Optional.of(new ItemAmount(TORCH, 12)), v.offHand());
    }

    @Test
    void aHandHoldingNoSlotOrAnEmptiedOneShowsNothing() {
        d.inventory().set(0, Optional.of(new ItemAmount(TORCH, 3))); // not what a hand holding no slot shows
        d.equipment().hold(Hand.MAIN, 4);

        CitizenInventoryView v = CitizenInventoryView.of(colony, d);

        assertEquals(Optional.empty(), v.mainHand());
        assertEquals(Optional.empty(), v.offHand());
    }
}
