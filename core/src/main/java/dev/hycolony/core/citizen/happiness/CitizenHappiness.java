package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.TimeBasedModifier.Threshold;
import dev.hycolony.core.colony.Colony;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A citizen's happiness: its modifiers and their weighted mean. Port of MC CitizenHappinessHandler: the ten modifiers
 * every citizen has, the expiring ones events add, the day's end, and the cached result (recomputed only after a
 * change, as MC). Research's HAPPINESS effect is 0.
 */
public final class CitizenHappiness {
    /** MC HappinessConstants.MAX_HAPPINESS. */
    public static final double MAX_HAPPINESS = 10;
    /** MC Colony.getOverallHappiness of a colony without citizens. */
    public static final double EMPTY_COLONY_HAPPINESS = 5.5;
    /** MC COMPLAIN_DAYS_* : the first threshold of homelessness, unemployment, health and idling. */
    static final int COMPLAIN_DAYS = 7;
    /** MC DEMANDS_DAYS_* : the second threshold. */
    static final int DEMANDS_DAYS = 14;

    /** MC: a HashMap, so the windows list the modifiers in MC's own order. */
    private final Map<String, HappinessModifier> modifiers = new HashMap<>();

    private double cached = -1;

    /** MC CitizenHappinessHandler(data): the static and time-based modifiers. */
    public CitizenHappiness() {
        add(new StaticModifier(HappinessIds.SCHOOL, 1.0, HappinessFactors::school));
        add(new StaticModifier(HappinessIds.SECURITY, 4.0, HappinessFactors::security));
        add(new StaticModifier(HappinessIds.SOCIAL, 2.0, HappinessFactors::social));
        add(new StaticModifier(HappinessIds.MYSTICAL_SITE, 1.0, HappinessFactors::mysticalSite));
        add(new StaticModifier(HappinessIds.FOOD, 3.0, HappinessFactors::food));
        add(timeBased(HappinessIds.HOMELESSNESS, 3.0, HappinessFactors::housing, 0.75, 0.5));
        add(timeBased(HappinessIds.UNEMPLOYMENT, 2.0, HappinessFactors::unemployment, 0.75, 0.5));
        add(timeBased(HappinessIds.HEALTH, 2.0, HappinessFactors::health, 0.5, 0.1));
        add(timeBased(HappinessIds.IDLEATJOB, 1.0, HappinessFactors::idleAtJob, 0.5, 0.1));
        add(new TimeBasedModifier(
                HappinessIds.SLEPTTONIGHT,
                1.5,
                HappinessFactors::sleptTonight,
                (m, colony, citizen) -> true,
                List.of(new Threshold(0, 2.0), new Threshold(2, 1.6), new Threshold(3, 1.0))));
    }

    /** MC's complain-then-demand modifiers: x {@code complain} after 7 days, x {@code demand} after 14. */
    private static TimeBasedModifier timeBased(
            String id, double weight, HappinessFunction function, double complain, double demand) {
        return new TimeBasedModifier(
                id,
                weight,
                function,
                TimeBasedModifier.WHILE_UNHAPPY,
                List.of(new Threshold(COMPLAIN_DAYS, complain), new Threshold(DEMANDS_DAYS, demand)));
    }

    /** MC Colony.getOverallHappiness: the citizens' mean happiness; 5.5 without citizens. */
    public static double overall(Colony colony) {
        Collection<CitizenData> citizens = colony.citizens().all();
        if (citizens.isEmpty()) {
            return EMPTY_COLONY_HAPPINESS;
        }
        double sum = 0;
        for (CitizenData c : citizens) {
            sum += c.happiness().happiness(colony, c);
        }
        return sum / citizens.size();
    }

    /** MC addModifier: adds or replaces the modifier of that id. */
    public void add(HappinessModifier modifier) {
        modifiers.put(modifier.id(), modifier);
        cached = -1;
    }

    /** MC resetModifier: a time-based or expiring modifier starts its days again; nothing for another id. */
    public void reset(String id) {
        HappinessModifier m = modifiers.get(id);
        if (m instanceof TimeBasedModifier || m instanceof ExpirationModifier) {
            m.reset();
            cached = -1;
        }
    }

    public Optional<HappinessModifier> get(String id) {
        return Optional.ofNullable(modifiers.get(id));
    }

    /** MC getModifiers: every modifier, in MC's order. */
    public Collection<HappinessModifier> modifiers() {
        return Collections.unmodifiableCollection(modifiers.values());
    }

    /**
     * MC processDailyHappiness, at nightfall: each modifier ends its day (MC's daily complaints wait for the citizen
     * interaction system); the happiness is computed afresh next time.
     */
    public void dayEnd(Colony colony, CitizenData citizen) {
        for (HappinessModifier m : new ArrayList<>(modifiers.values())) {
            m.dayEnd(colony, citizen);
        }
        cached = -1;
    }

    /**
     * MC getHappiness: 10 x the weighted mean of the factors that are not neutral (1), capped at 10; cached until the
     * next change. Deviation from MC: with every factor neutral MC divides 0 by 0 (NaN); here the citizen is fully
     * happy (10).
     */
    public double happiness(Colony colony, CitizenData citizen) {
        if (cached == -1) {
            double total = 0;
            double weights = 0;
            for (HappinessModifier m : modifiers.values()) {
                double factor = m.factor(colony, citizen);
                if (factor == 1.0) {
                    continue;
                }
                total += factor * m.weight();
                weights += m.weight();
            }
            cached = weights == 0 ? MAX_HAPPINESS : Math.min(MAX_HAPPINESS * (total / weights), MAX_HAPPINESS);
        }
        return cached;
    }
}
