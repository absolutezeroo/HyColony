package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.Resolver;
import java.util.List;

/** A module that gives its building extra resolvers, next to its own stock one (MC {@code ICreatesResolversModule}). */
public interface CreatesResolvers extends BuildingModule {
    /** The resolvers {@code building} offers the colony's request system; called once, when it joins the colony. */
    List<Resolver> createResolvers(Colony colony, Building building);
}
