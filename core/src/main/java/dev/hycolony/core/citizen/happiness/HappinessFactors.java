package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.FoodHistory;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The happiness functions (MC ModHappinessFactorTypeInitializer and CitizenHappinessHandler's static factors). The
 * systems HyColony lacks give MC's value without them: nobody is sick, a pupil or a guard, and no colony has a mystical
 * site.
 */
final class HappinessFactors {
    /** MC: a work hut above this level pleases its worker. */
    private static final int GOOD_WORK_LEVEL = 3;
    /** MC getFoodFactor: each part is capped at 5. */
    private static final double FOOD_PART_CAP = 5.0;
    /** MC getSocialModifier: a citizen at or below this saturation counts as hungry. */
    private static final double HUNGRY_SATURATION = 1;

    private HappinessFactors() {}

    /** MC schoolFunction: a child 2 at school (no school here), else 0; an adult 1. */
    static double school(Colony colony, CitizenData c) {
        return c.isChild() ? 0.0 : 1.0;
    }

    /** MC getGuardFactor: {@code min(guards / (others x 2 / 3), 2)}, both counted from 1. */
    static double security(Colony colony, CitizenData c) {
        double guards = 1;
        double workers = 1;
        for (CitizenData citizen : colony.citizens().all()) {
            if (citizen.job().map(Job::isGuard).orElse(false)) {
                guards++;
            } else {
                workers++;
            }
        }
        return Math.min(guards / (workers * 2 / 3), 2);
    }

    /** MC getSocialModifier: the share of citizens without an unemployed adult, a homeless, a sick or a hungry one. */
    static double social(Colony colony, CitizenData c) {
        double total = colony.citizens().all().size();
        double unhappy = 0;
        for (CitizenData citizen : colony.citizens().all()) {
            if (!citizen.isChild() && citizen.job().isEmpty()) {
                unhappy++;
            }
            if (citizen.homeBuilding() == null) {
                unhappy++;
            }
            if (citizen.saturation() <= HUNGRY_SATURATION) {
                unhappy++;
            }
        }
        return (total - unhappy) / total;
    }

    /** MC getMysticalSiteFactor: {@code max(1, best site level / 2)}; HyColony has no mystical site. */
    static double mysticalSite(Colony colony, CitizenData c) {
        return 1.0;
    }

    /**
     * MC getFoodFactor: neutral without a home or before ten meals; else the mean of the diversity over the home level
     * and the dishes over {@code max(1, level - 2)}, each capped at 5.
     */
    static double food(Colony colony, CitizenData c) {
        int level = level(colony, c.homeBuilding());
        FoodHistory history = c.hunger().history();
        if (level == 0 || !history.isFull()) {
            return 1.0;
        }
        FoodHistory.Stats stats = history.stats(colony.context().ports().catalog());
        double diversity = Math.min(FOOD_PART_CAP, (double) stats.diversity() / level);
        double quality = Math.min(FOOD_PART_CAP, stats.quality() / Math.max(1.0, level - 2.0));
        return (diversity + quality) / 2.0;
    }

    /** MC housingFunction: the home's level / 3, 0 without a home. */
    static double housing(Colony colony, CitizenData c) {
        return c.homeBuilding() == null ? 0.0 : level(colony, c.homeBuilding()) / 3.0;
    }

    /** MC unemploymentFunction: a child 1; without a work hut 0.5; at a work hut above level 3, 2; else 1. */
    static double unemployment(Colony colony, CitizenData c) {
        if (c.isChild()) {
            return 1.0;
        }
        Building work = c.workBuilding() == null
                ? null
                : colony.buildings().at(c.workBuilding()).orElse(null);
        if (work == null) {
            return 0.5;
        }
        return work.level() > GOOD_WORK_LEVEL ? 2.0 : 1.0;
    }

    /** MC healthFunction: sick 0.5, else 1; nobody is sick here (no disease). */
    static double health(Colony colony, CitizenData c) {
        return 1.0;
    }

    /** MC idleatjobFunction: 0.5 while its job is stuck, else 1. */
    static double idleAtJob(Colony colony, CitizenData c) {
        return c.isIdleAtJob() ? 0.5 : 1.0;
    }

    /** MC sleptTonightFunction: a guard 1, anyone else 0.5 (raised by the days slept: the modifier's thresholds). */
    static double sleptTonight(Colony colony, CitizenData c) {
        return c.job().map(Job::isGuard).orElse(false) ? 1.0 : 0.5;
    }

    /** The level of the hut at {@code pos} (MC getBuildingLevelEquivalent); 0 without one. */
    private static int level(Colony colony, @Nullable BlockPos pos) {
        return pos == null ? 0 : colony.buildings().at(pos).map(Building::level).orElse(0);
    }
}
