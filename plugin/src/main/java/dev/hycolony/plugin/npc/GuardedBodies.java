package dev.hycolony.plugin.npc;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The citizen bodies port as the core sees it: a port never throws (CLAUDE.md § 4). A call the Hytale adapter fails
 * (a body whose entity lost a component, a store asserting its thread) answers nothing, false or a failed walk; the
 * first failure is logged as a WARNING, the next ones FINE, so a colony tick never throws on a body. Two fallbacks have
 * a cost, acceptable because the adapter's isAlive and position only read a map and a component: a failed isAlive
 * would respawn a body that still exists, and a failed position keeps a walk from ending until it answers again.
 */
public final class GuardedBodies implements CitizenBodies {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final CitizenBodies bodies;
    private boolean warned;

    public GuardedBodies(CitizenBodies bodies) {
        this.bodies = bodies;
    }

    @Override
    public Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName) {
        return guard("spawn", () -> bodies.spawn(world, near, colonyId, citizenId, displayName), Optional.empty());
    }

    @Override
    public boolean isAlive(BodyId body) {
        return guard("isAlive", () -> bodies.isAlive(body), false);
    }

    @Override
    public int healthPercent(BodyId body) {
        return guard("healthPercent", () -> bodies.healthPercent(body), 0);
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        return guard("position", () -> bodies.position(body), Optional.empty());
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        run("moveTo", () -> bodies.moveTo(body, target));
    }

    /** A failed call is a failed walk: the walker then works from where the body stands. */
    @Override
    public NavStatus navStatus(BodyId body) {
        return guard("navStatus", () -> bodies.navStatus(body), NavStatus.FAILED);
    }

    @Override
    public List<Vec3> path(BodyId body) {
        return guard("path", () -> bodies.path(body), List.of());
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        run("setDisplayName", () -> bodies.setDisplayName(body, name));
    }

    @Override
    public void despawn(BodyId body) {
        run("despawn", () -> bodies.despawn(body));
    }

    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        run("setHeldItem", () -> bodies.setHeldItem(body, item));
    }

    @Override
    public void setArmor(BodyId body, List<Optional<ItemKey>> pieces) {
        run("setArmor", () -> bodies.setArmor(body, pieces));
    }

    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        run("playAnimation", () -> bodies.playAnimation(body, animation));
    }

    @Override
    public void lookAt(BodyId body, Vec3 target) {
        run("lookAt", () -> bodies.lookAt(body, target));
    }

    @Override
    public void teleport(BodyId body, Vec3 target) {
        run("teleport", () -> bodies.teleport(body, target));
    }

    @Override
    public void setMovementSpeed(BodyId body, double factor) {
        run("setMovementSpeed", () -> bodies.setMovementSpeed(body, factor));
    }

    /** A failed call is a bed refused: the citizen stands, as without a bed (MC). */
    @Override
    public boolean sleepIn(BodyId body, BlockPos bed) {
        return guard("sleepIn", () -> bodies.sleepIn(body, bed), false);
    }

    @Override
    public boolean isInBed(BodyId body) {
        return guard("isInBed", () -> bodies.isInBed(body), false);
    }

    @Override
    public void wakeUp(BodyId body) {
        run("wakeUp", () -> bodies.wakeUp(body));
    }

    private void run(String op, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            failed(op, e);
        }
    }

    /** {@code call}'s answer, else {@code fallback} after logging the failure. */
    private <T> T guard(String op, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            failed(op, e);
            return fallback;
        }
    }

    private void failed(String op, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony: citizen body %s failed", op);
        warned = true;
    }
}
