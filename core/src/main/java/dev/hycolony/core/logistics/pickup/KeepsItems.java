package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.colony.Colony;
import java.util.List;

/** A module whose building keeps some items from dumps and pickups (MC {@code IHasRequiredItemsModule}). */
public interface KeepsItems extends BuildingModule {
    /**
     * The items {@code building} keeps right now; read afresh on every dump or pickup pass. The colony gives the
     * rules that depend on its requests or citizens (a crafter's tasks) and the item catalog.
     */
    List<KeepRule> keepRules(Colony colony, Building building);
}
