package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.body.BodyHealth;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * BodyHealth over the citizen NPCs' Health stat ({@link BodyVitals}), walking speed ({@link BodySpeeds}) and the
 * hostile NPCs near them ({@link BodyThreats}). Never
 * throws (CLAUDE.md § 4): a failed call answers 0 or false, logged as a WARNING the first time, then FINE. World
 * thread only.
 */
public final class HytaleBodyHealth implements BodyHealth {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final Function<BodyId, Optional<Ref<EntityStore>>> entities;
    private final BodyVitals vitals;
    private final BodySpeeds speeds;
    private final BodyThreats threats;
    private boolean warned;

    /** {@code entities} finds a body's loaded entity; {@code speeds} is the one the bodies' job speeds go through. */
    public HytaleBodyHealth(
            World world,
            Function<BodyId, Optional<Ref<EntityStore>>> entities,
            BodyVitals vitals,
            BodySpeeds speeds,
            BodyThreats threats) {
        this.world = world;
        this.entities = entities;
        this.vitals = vitals;
        this.speeds = speeds;
        this.threats = threats;
    }

    /** Through {@link BodyThreats}. */
    @Override
    public Optional<Vec3> nearestThreat(BodyId body, double range) {
        return read("nearestThreat", body, ref -> threats.nearest(ref, range), Optional.<Vec3>empty());
    }

    @Override
    public double health(BodyId body) {
        return read("health", body, vitals::current, 0.0);
    }

    @Override
    public double maxHealth(BodyId body) {
        return read("maxHealth", body, vitals::max, 0.0);
    }

    /** Through {@link BodyVitals#damage}, under Hytale's Crush cause. */
    @Override
    public void damage(BodyId body, double amount) {
        read(
                "damage",
                body,
                ref -> {
                    vitals.damage(ref, amount);
                    return true;
                },
                false);
    }

    @Override
    public void heal(BodyId body, double amount) {
        read(
                "heal",
                body,
                ref -> {
                    vitals.heal(ref, amount);
                    return true;
                },
                false);
    }

    /** Through {@link BodySpeeds}, on top of its job's speed. */
    @Override
    public void setStarving(BodyId body, boolean starving) {
        read(
                "setStarving",
                body,
                ref -> {
                    speeds.setStarving(ref, starving, world.getEntityStore().getStore());
                    return true;
                },
                false);
    }

    /** {@code call} on the body's entity; {@code fallback} for an unknown body or a failed call. */
    private <T> T read(String op, BodyId body, Function<Ref<EntityStore>, T> call, T fallback) {
        return guard(op, () -> entities.apply(body).map(call).orElse(fallback), fallback);
    }

    private <T> T guard(String op, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony: citizen health %s failed", op);
            warned = true;
            return fallback;
        }
    }
}
