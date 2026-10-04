package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.hurt.HurtMemory;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.farming.job.FarmerJob;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBodies;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen's idle saturation decrease, healing and walking count. */
class HungerTicksTest {
    private static final double EPS = 1e-9;

    private final TestContexts t = new TestContexts();
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
    private Colony colony = colony();

    private Colony colony() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        c.citizens().restore(citizen);
        c.citizens().onBodyLoaded(body, citizen.id());
        return c;
    }

    private FakeBodies.Body fake() {
        return t.bodies.bodies.get(body);
    }

    @Test
    void homelessCitizenLosesPointThreeAMinuteInDaytime() {
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(59.7, citizen.saturation(), EPS);
    }

    @Test
    void nobodyGetsHungryAtNightNorAsleep() {
        t.clock.daytime = false;
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(60, citizen.saturation(), EPS);
        t.clock.daytime = true;
        citizen.setAsleep(true);
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(60, citizen.saturation(), EPS);
    }

    @Test
    void homeLevelAndJobRaiseTheDecrease() {
        Building home = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 0);
        home.setLevel(5);
        colony.buildings().add(home);
        citizen.setHomeBuilding(home.position());
        citizen.setJob(FarmerJob.TYPE.factory().apply(citizen));
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(60 - 1.5 * 1.2, citizen.saturation(), EPS);
    }

    @Test
    void workActionsAddUpToHalfTheIdleDecreaseOnce() {
        for (int i = 0; i < 10; i++) {
            citizen.hunger().forAction(); // 2.0 pending
        }
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(60 - 0.3 - 0.15, citizen.saturation(), EPS);
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(60 - 0.3 - 0.15 - 0.3, citizen.saturation(), EPS); // the pending work was paid once
    }

    @Test
    void aChildLosesHalfAndTheFoodModifierMultiplies() {
        citizen.setChild(true);
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(59.85, citizen.saturation(), EPS);
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                new ColonyConfig.Gameplay(4, 250, false, 2.0),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony(),
                d.structurize(),
                d.combat());
        colony = colony();
        HungerTicks.decreaseIdleSaturation(colony);
        assertEquals(59.85 - 0.3, citizen.saturation(), EPS);
    }

    @Test
    void losingSaturationEndsJustAte() {
        citizen.hunger().setJustAte(true);
        HungerTicks.decreaseIdleSaturation(colony);
        assertFalse(citizen.hunger().justAte());
    }

    @Test
    void healingFollowsSaturation() {
        assertEquals(2, HungerTicks.healAmount(60));
        assertEquals(1, HungerTicks.healAmount(30));
        assertEquals(3 / 60.0 / 2, HungerTicks.healAmount(3), EPS);
        fake().health = 10;
        HungerTicks.updateHealing(colony);
        assertEquals(20, fake().health, EPS, "MC's 2 points, times 100 / 20 on Hytale's scale");
    }

    @Test
    void healingScalesWithTheBodysOwnMaximum() {
        fake().maxHealth = 40;
        fake().health = 10;
        HungerTicks.updateHealing(colony);
        assertEquals(14, fake().health, EPS, "2 points times 40 / 20");
    }

    @Test
    void aCitizenHurtLatelyDoesNotHeal() {
        fake().health = 10;
        citizen.vitals().hurtMemory().attacked(t.clock.tick);
        HungerTicks.updateHealing(colony);
        assertEquals(10, fake().health, EPS);

        t.clock.tick += HurtMemory.HURT_MEMORY_TICKS;
        HungerTicks.updateHealing(colony);
        assertEquals(20, fake().health, EPS, "the attacker forgotten, it heals again");
    }

    @Test
    void aStarvingCitizenIsSlowedUntilItEats() {
        citizen.setSaturation(0);
        HungerTicks.updateHealing(colony);
        assertTrue(fake().starving);
        citizen.hunger().increase(5);
        HungerTicks.updateHealing(colony);
        assertFalse(fake().starving);
    }

    @Test
    void walkingCostsAContinuousActionEveryTwentyFiveOfWalkDistance() {
        citizen.hunger().walked(new Vec3(0, 64, 0), new Vec3(30, 64, 0)); // 18 walk distance
        citizen.hunger().walked(new Vec3(30, 64, 0), new Vec3(30, 90, 20)); // +12 = 30 > 25
        citizen.hunger().walked(new Vec3(0, 64, 0), new Vec3(500, 64, 0)); // a teleport: not walking
        assertEquals(0.02, citizen.hunger().takePending(), EPS);
    }

    @Test
    void anActiveColonyRunsTheHungerAndTheHealing() {
        t.players.online.put(UUID.randomUUID(), new BlockPos(1, 64, 1));
        colony.claimAround(new BlockPos(0, 64, 0), 1);
        fake().health = 10;

        for (int i = 0; i < HungerTicks.SATURATION_DECREASE_AFTER + 100; i++) {
            t.clock.tick++;
            colony.tick();
        }

        assertTrue(citizen.saturation() < 60);
        assertTrue(fake().health > 10);
    }
}
