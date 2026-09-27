package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.List;

/** A module whose building keeps some items from dumps and pickups (MC {@code IHasRequiredItemsModule}). */
public interface KeepsItems extends BuildingModule {
    /** The items to keep right now; read afresh on every dump or pickup pass. */
    List<KeepRule> keepRules(Building building, ItemCatalog catalog);
}
