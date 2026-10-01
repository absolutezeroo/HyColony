package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;

/**
 * A fixed factor that lasts a number of days (MC ExpirationBasedHappinessModifier over a StaticHappinessSupplier): it
 * is {@code value} while {@code 0 < days <= period}, then neutral (1). It starts with {@code days = period} and loses
 * one at each day's end.
 */
public final class ExpirationModifier implements HappinessModifier {
    private final String id;
    private final double weight;
    private final double value;
    private final int period;
    private int days;

    public ExpirationModifier(String id, double weight, double value, int period) {
        this.id = id;
        this.weight = weight;
        this.value = value;
        this.period = period;
        this.days = period;
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
        return lastFactor();
    }

    @Override
    public double lastFactor() {
        return days > 0 && days <= period ? value : 1.0;
    }

    @Override
    public void dayEnd(Colony colony, CitizenData citizen) {
        if (days > 0) {
            days--;
        }
    }

    @Override
    public void reset() {
        days = period;
    }

    @Override
    public int days() {
        return days;
    }

    /** Its fixed value while it lasts. */
    public double value() {
        return value;
    }

    /** The days it lasts from a reset. */
    public int period() {
        return period;
    }

    /** Restores the saved days left (MC read). */
    void restoreDays(int savedDays) {
        this.days = Math.max(0, savedDays);
    }
}
