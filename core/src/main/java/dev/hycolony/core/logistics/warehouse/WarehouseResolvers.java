package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.CreatesResolvers;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.Resolver;
import java.util.List;

/** The resolvers a warehouse offers the colony (MC {@code BuildingWareHouse.createResolvers}). */
final class WarehouseResolvers implements CreatesResolvers {
    /** Its stock resolver, after the building's own. */
    @Override
    public List<Resolver> createResolvers(Colony colony, Building building) {
        return List.of(new WarehouseStockResolver(colony, building));
    }
}
