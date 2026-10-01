package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.body.BodySeats;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * BodySeats over Hytale's seat mount ({@link CitizenSeats}). Never throws (CLAUDE.md § 4): a failed call answers false
 * (a seat refused) or taken, logged as a WARNING the first time, then FINE. World thread only.
 */
public final class HytaleBodySeats implements BodySeats {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Function<BodyId, Optional<Ref<EntityStore>>> entities;
    private final CitizenSeats seats;
    private boolean warned;

    /** {@code entities} finds a body's loaded entity. */
    public HytaleBodySeats(World world, Function<BodyId, Optional<Ref<EntityStore>>> entities) {
        this.entities = entities;
        this.seats = new CitizenSeats(world);
    }

    @Override
    public boolean sitOn(BodyId body, BlockPos seat) {
        return guard(
                "sitOn",
                () -> entities.apply(body).filter(ref -> seats.sitOn(ref, seat)).isPresent(),
                false);
    }

    /** A failed call counts the seat as taken, so nobody is sent to it. */
    @Override
    public boolean isSeatTaken(BlockPos seat) {
        return guard("isSeatTaken", () -> seats.isTaken(seat), true);
    }

    @Override
    public void standUp(BodyId body) {
        guard(
                "standUp",
                () -> {
                    entities.apply(body).ifPresent(seats::standUp);
                    return true;
                },
                false);
    }

    private <T> T guard(String op, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony: citizen seat %s failed", op);
            warned = true;
            return fallback;
        }
    }
}
