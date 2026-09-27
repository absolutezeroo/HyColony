package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequesterId;
import java.util.Optional;

/** Where a requester stands, as the warehouse resolvers and the couriers see it (MC {@code IRequester.getLocation}). */
public final class RequesterLocation {
    private RequesterLocation() {}

    /**
     * MC {@code request.getRequester().getLocation()}: a building, or one of its resolvers asking for a child; empty
     * for an unknown requester.
     */
    public static Optional<BlockPos> of(Colony colony, RequesterId id) {
        for (Building building : colony.buildings().all()) {
            if (building.requesterId().equals(id)) {
                return Optional.of(building.position());
            }
            for (Resolver resolver : building.resolvers()) {
                if (resolver.requesterId().equals(id)) {
                    return Optional.of(resolver.location());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * MC getRequesterDisplayName: the citizen who asks, else the building, or the building whose resolver asks (a
     * warehouse's delivery); else the raw requester id.
     */
    public static String displayName(Colony colony, Request r) {
        if (r.citizenId() != -1) {
            return colony.citizens().get(r.citizenId()).map(CitizenData::name).orElse("");
        }
        return of(colony, r.requester())
                .flatMap(colony.buildings()::at)
                .map(Building::displayName)
                .orElse(r.requester().value());
    }
}
