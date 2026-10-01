package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import java.util.List;

/**
 * A factor that worsens with the days it stays below 1 (MC TimeBasedHappinessModifier): below 1, its function's value
 * is multiplied by the multiplier of the last threshold its days reached. At each day's end its days grow while the
 * roll-over rule holds (by default: the factor is below 1), else start again at 0.
 */
public final class TimeBasedModifier implements HappinessModifier {
    /** MC Tuple(days, multiplier): from {@code days} days on, the factor is multiplied by {@code multiplier}. */
    public record Threshold(int days, double multiplier) {}

    /** When a day's end counts one more day (MC dayRollOverPredicate). */
    @FunctionalInterface
    public interface RollOver {
        boolean test(TimeBasedModifier modifier, Colony colony, CitizenData citizen);
    }

    /** MC's default roll-over: the factor is below 1. */
    public static final RollOver WHILE_UNHAPPY = (m, colony, citizen) -> m.factor(colony, citizen) < 1;

    private final String id;
    private final double weight;
    private final HappinessFunction function;
    private final RollOver rollOver;
    private final List<Threshold> thresholds;
    private double last;
    private int days;

    public TimeBasedModifier(
            String id, double weight, HappinessFunction function, RollOver rollOver, List<Threshold> thresholds) {
        this.id = id;
        this.weight = weight;
        this.function = function;
        this.rollOver = rollOver;
        this.thresholds = List.copyOf(thresholds);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public double weight() {
        return weight;
    }

    @Override
    public double factor(Colony colony, CitizenData citizen) {
        last = function.apply(colony, citizen);
        return withDays(last);
    }

    @Override
    public double lastFactor() {
        return withDays(last);
    }

    /** MC getFactor: below 1, the base times the multiplier of the last threshold reached. */
    private double withDays(double base) {
        double factor = base;
        if (base < 1.0) {
            for (Threshold t : thresholds) {
                if (days >= t.days()) {
                    factor = base * t.multiplier();
                }
            }
        }
        return factor;
    }

    @Override
    public void dayEnd(Colony colony, CitizenData citizen) {
        if (rollOver.test(this, colony, citizen)) {
            days++;
        } else {
            reset();
        }
    }

    @Override
    public void reset() {
        days = 0;
    }

    @Override
    public int days() {
        return days;
    }

    /** Restores the saved days and last computed value (MC read with persist). */
    void restore(int savedDays, double lastValue) {
        this.days = Math.max(0, savedDays);
        this.last = lastValue;
    }

    /** The function's last value, before the days' multiplier; for saving. */
    double lastBase() {
        return last;
    }
}
