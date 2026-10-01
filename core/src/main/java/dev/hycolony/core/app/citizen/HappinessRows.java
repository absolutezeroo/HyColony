package dev.hycolony.core.app.citizen;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.CitizenHappiness;
import dev.hycolony.core.citizen.happiness.HappinessModifier;
import dev.hycolony.core.colony.Colony;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The citizen window's Happiness tab (MC CitizenWindowUtils.updateHappiness): one row per modifier whose last factor is
 * not neutral, in MC's order, with its mood icon.
 */
public final class HappinessRows {
    /** MC: above this a factor below 1 is only "slightly negative". */
    private static final double SLIGHTLY = 0.75;

    /** MC's icons: happy, satisfied, unsatisfied, unhappy. */
    public enum Mood {
        POSITIVE,
        NEUTRAL,
        SLIGHTLY_NEGATIVE,
        NEGATIVE
    }

    /** One modifier shown: its id (the tail of its name and description keys) and its mood. */
    public record Row(String id, Mood mood) {}

    private HappinessRows() {}

    /** MC updateHappiness: the modifiers whose factor (as last computed, MC getFactor(null)) is not 1. */
    public static List<Row> of(CitizenHappiness happiness) {
        List<Row> rows = new ArrayList<>();
        for (HappinessModifier m : happiness.modifiers()) {
            double value = m.lastFactor();
            if (value != 1.0) {
                rows.add(new Row(m.id(), mood(value)));
            }
        }
        return List.copyOf(rows);
    }

    /**
     * MC WindowCitizenPage.fillHappinessList: every modifier some citizen has, in MC's order (a HashMap of their
     * ids), with the mood of its mean factor (as last computed) over all citizens.
     */
    public static List<Row> colony(Colony colony) {
        Map<String, Double> sums = new HashMap<>();
        for (CitizenData c : colony.citizens().all()) {
            c.happiness().happiness(colony, c); // MC serializes each citizen's happiness first, which refreshes them
            for (HappinessModifier m : c.happiness().modifiers()) {
                sums.merge(m.id(), m.lastFactor(), Double::sum);
            }
        }
        int count = colony.citizens().all().size();
        List<Row> rows = new ArrayList<>(sums.size());
        sums.forEach((id, sum) -> rows.add(new Row(id, mood(sum / count))));
        return List.copyOf(rows);
    }

    /** MC: the colony's mean happiness with one decimal rounded up, without a trailing ".0" ("#.#", CEILING). */
    public static String overall(Colony colony) {
        DecimalFormat format = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.CEILING);
        return format.format(CitizenHappiness.overall(colony));
    }

    /** MC updateHappiness's icon for {@code value}. */
    static Mood mood(double value) {
        if (value > 1.0) {
            return Mood.POSITIVE;
        }
        if (value == 1.0) {
            return Mood.NEUTRAL;
        }
        return value > SLIGHTLY ? Mood.SLIGHTLY_NEGATIVE : Mood.NEGATIVE;
    }
}
