package dev.hycolony.core.citizen.happiness;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;

/** A factor that is what its function says right now (MC StaticHappinessModifier over a DynamicHappinessSupplier). */
public final class StaticModifier implements HappinessModifier {
    private final String id;
    private final double weight;
    private final HappinessFunction function;
    private double last;

    public StaticModifier(String id, double weight, HappinessFunction function) {
        this.id = id;
        this.weight = weight;
        this.function = function;
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
        return last;
    }

    @Override
    public double lastFactor() {
        return last;
    }

    /** Restores the factor last computed (MC DynamicHappinessSupplier's saved value). */
    void restoreLast(double value) {
        this.last = value;
    }
}
