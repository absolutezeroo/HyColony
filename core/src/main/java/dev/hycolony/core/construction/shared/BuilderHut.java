package dev.hycolony.core.construction.shared;

import dev.hycolony.core.building.Building;

/**
 * The builder hut's identity, kept apart from its {@code BuildingType} (which needs the builder job) so work orders can
 * recognise builder huts without depending on the builder package. MC BuildingBuilder.
 */
public final class BuilderHut {
    /** The builder hut's building type id. */
    public static final String TYPE_ID = "hycolony:builder";

    /** The builder hut's highest level. */
    public static final int MAX_LEVEL = 5;

    private BuilderHut() {}

    /** Whether {@code b} is a builder hut. */
    public static boolean is(Building b) {
        return TYPE_ID.equals(b.type().id());
    }
}
