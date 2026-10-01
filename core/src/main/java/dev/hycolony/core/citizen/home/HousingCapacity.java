package dev.hycolony.core.citizen.home;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;

/**
 * The colony's housing: its citizens, the places its residences offer, and the configured cap. Port of MC
 * CitizenManager.calculateMaxCitizens and getMaxCitizens, read by the town hall's Statistics (MC WindowStatsPage).
 *
 * <p>Deviation from MC: MC also caps by maxCitizensFromResearch (25 until the CITIZEN_CAP research); without research,
 * only the configuration caps.
 */
public record HousingCapacity(int citizens, int housing, int cap) {
    /** MC WindowStatsPage's colour: green, orange (needs housing) or red (reached the configured limit). */
    public enum Population {
        OK,
        NEEDS_HOUSING,
        CONFIG_LIMITED
    }

    /** The places of the built residences (residents only for a LOCKED one), at least 1, at most the config's cap. */
    public static HousingCapacity of(Colony c) {
        int sum = 0;
        for (Building b : c.buildings().all()) {
            if (b.level() <= 0) {
                continue;
            }
            LivingModule living = b.module(LivingModule.class).orElse(null);
            if (living != null) {
                sum += living.hiringMode() == HiringMode.LOCKED
                        ? living.residents().size()
                        : living.max(b);
            }
        }
        int cap = c.context().config().gameplay().maxCitizenPerColony();
        return new HousingCapacity(c.citizens().all().size(), Math.max(1, Math.min(sum, cap)), cap);
    }

    /** MC WindowStatsPage: green below 90 % of both limits, orange below the cap, red at it. */
    public Population population() {
        if (citizens < cap * 0.9 && citizens < housing * 0.9) {
            return Population.OK;
        }
        return citizens < cap ? Population.NEEDS_HOUSING : Population.CONFIG_LIMITED;
    }

    /** MC: "citizens/max(citizens, housing)", or "citizens/cap" once the configured limit is reached. */
    public int shownMax() {
        return population() == Population.CONFIG_LIMITED ? cap : Math.max(citizens, housing);
    }
}
