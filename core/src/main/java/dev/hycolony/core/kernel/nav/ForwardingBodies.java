package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.List;
import java.util.Optional;

/** Citizen bodies that hand every call to {@link #bodies}: the base of a decorator that changes only a few of them. */
abstract class ForwardingBodies implements CitizenBodies {
    /** The bodies decorated. */
    protected final CitizenBodies bodies;

    ForwardingBodies(CitizenBodies bodies) {
        this.bodies = bodies;
    }

    @Override
    public Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName) {
        return bodies.spawn(world, near, colonyId, citizenId, displayName);
    }

    @Override
    public boolean isAlive(BodyId body) {
        return bodies.isAlive(body);
    }

    @Override
    public int healthPercent(BodyId body) {
        return bodies.healthPercent(body);
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        return bodies.position(body);
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        bodies.moveTo(body, target);
    }

    @Override
    public NavStatus navStatus(BodyId body) {
        return bodies.navStatus(body);
    }

    @Override
    public List<Vec3> path(BodyId body) {
        return bodies.path(body);
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        bodies.setDisplayName(body, name);
    }

    @Override
    public void despawn(BodyId body) {
        bodies.despawn(body);
    }

    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        bodies.setHeldItem(body, item);
    }

    @Override
    public void setArmor(BodyId body, List<Optional<ItemAmount>> pieces) {
        bodies.setArmor(body, pieces);
    }

    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        bodies.playAnimation(body, animation);
    }

    @Override
    public void lookAt(BodyId body, Vec3 target) {
        bodies.lookAt(body, target);
    }

    @Override
    public void teleport(BodyId body, Vec3 target) {
        bodies.teleport(body, target);
    }

    @Override
    public void setMovementSpeed(BodyId body, double factor) {
        bodies.setMovementSpeed(body, factor);
    }

    @Override
    public boolean sleepIn(BodyId body, BlockPos bed) {
        return bodies.sleepIn(body, bed);
    }

    @Override
    public boolean isInBed(BodyId body) {
        return bodies.isInBed(body);
    }

    @Override
    public void wakeUp(BodyId body) {
        bodies.wakeUp(body);
    }
}
