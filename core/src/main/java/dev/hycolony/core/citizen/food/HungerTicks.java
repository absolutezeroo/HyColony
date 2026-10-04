package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.body.BodyHealth;

/**
 * The periodic hunger of a colony's citizens whose body is alive: the idle saturation decrease, healing and starving
 * slowness (the walking count is {@link CitizenHunger#walked}). Port of MC EntityCitizen's decreaseIdleSaturation and
 * updateHealing (checkHeal); the research effects (SATLIMIT, REGENERATION) are 0.
 */
public final class HungerTicks {
    /** MC CitizenConstants.SATURATION_DECREASE_AFTER: ticks between two idle decreases. */
    public static final int SATURATION_DECREASE_AFTER = 20 * 60;
    /** MC CitizenConstants.HEAL_CITIZENS_AFTER: ticks between two healings. */
    public static final int HEAL_CITIZENS_AFTER = 100;
    /** MC CitizenConstants.LOW_SATURATION: below this, a citizen barely heals. */
    static final double LOW_SATURATION = 6;

    private HungerTicks() {}

    /**
     * MC EntityCitizen.decreaseIdleSaturation, every {@link #SATURATION_DECREASE_AFTER} ticks: in daytime, each awake
     * citizen loses its home's consumption factor times its job's factor, plus its pending work up to half that, halved
     * for a child, times the config's food modifier.
     */
    public static void decreaseIdleSaturation(Colony colony) {
        if (!colony.context().clock().isDaytime()) {
            return; // MC !level().isNight(): nobody gets hungry at night
        }
        for (CitizenData data : colony.citizens().all()) {
            if (data.asleep() || !alive(colony, data)) {
                continue;
            }
            double decrease = FoodRules.consumptionFactor(homeLevel(colony, data));
            decrease *= data.job().map(Job::saturationFactor).orElse(1.0);
            double pending = data.hunger().takePending();
            if (pending != 0) {
                decrease += Math.min(decrease / 2.0, pending);
            }
            if (data.isChild()) {
                decrease /= 2.0;
            }
            data.hunger()
                    .decrease(decrease, colony.context().config().gameplay().foodModifier());
        }
    }

    /**
     * MC EntityCitizen.updateHealing, every {@link #HEAL_CITIZENS_AFTER} ticks: a hurt citizen nobody hurt lately
     * heals 2 when full, its saturation / 60 / 2 below {@link #LOW_SATURATION}, else 1; one at 0 saturation is slowed.
     * Deviation from MC (Hytale world): MC's points on its 20 health → the same share of the body's own maximum (100,
     * {@link CitizenData#MAX_HEALTH}).
     */
    public static void updateHealing(Colony colony) {
        CitizenBodies bodies = colony.context().bodies();
        BodyHealth health = colony.context().health();
        long tick = colony.context().clock().currentTick();
        for (CitizenData data : colony.citizens().all()) {
            BodyId body = colony.citizens().bodyOf(data.id()).orElse(null);
            if (body == null || !bodies.isAlive(body)) {
                continue;
            }
            double saturation = data.saturation();
            double max = health.maxHealth(body);
            if (health.health(body) < max && !data.vitals().hurtMemory().recentlyAttacked(tick)) {
                health.heal(body, healAmount(saturation) * max / CitizenData.MC_MAX_HEALTH);
            }
            health.setStarving(body, saturation <= 0);
        }
    }

    /** MC EntityCitizen.checkHeal's amount for {@code saturation}. */
    static double healAmount(double saturation) {
        if (saturation >= CitizenData.MAX_SATURATION) {
            return 2;
        }
        if (saturation < LOW_SATURATION) {
            return saturation / CitizenData.MAX_SATURATION / 2.0;
        }
        return 1;
    }

    /** The level of its home (MC getBuildingLevelEquivalent), 0 without one. */
    static int homeLevel(Colony colony, CitizenData data) {
        return data.homeBuilding() == null
                ? 0
                : colony.buildings()
                        .at(data.homeBuilding())
                        .map(Building::level)
                        .orElse(0);
    }

    private static boolean alive(Colony colony, CitizenData data) {
        return colony.citizens()
                .bodyOf(data.id())
                .map(colony.context().bodies()::isAlive)
                .orElse(false);
    }
}
