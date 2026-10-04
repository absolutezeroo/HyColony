package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.core.kernel.port.body.BodyHealth;
import dev.hycolony.core.kernel.port.body.BodySeats;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The three body ports at once (a test steers one fake): moves and work, health, seats. */
public final class FakeBodies implements CitizenBodies, BodyHealth, BodySeats {
    public static final class Body {
        public final int colonyId, citizenId;
        public String name;
        public Vec3 position;
        public Vec3 target;
        public NavStatus status = NavStatus.IDLE;
        /** What {@link FakeBodies#path} reports. */
        public List<Vec3> path = List.of();

        public boolean alive = true;
        /** What {@link FakeBodies#healthPercent} reports. */
        public int healthPercent = 100;
        /** What {@link FakeBodies#defensePercent} reports. */
        public int defensePercent;
        /** Health on Hytale's scale; {@link FakeBodies#heal} raises it up to {@link #maxHealth}. */
        public double health = 100;

        public double maxHealth = 100;
        /** Whether it is slowed down by starving. */
        public boolean starving;
        /** The seat it sits on; null standing. */
        public @Nullable BlockPos seat;

        public ItemKey held;
        /** What it shows worn, by armour slot; empty until {@link FakeBodies#setArmor}. */
        public List<Optional<ItemAmount>> armor = List.of();

        public BodyAnimation lastAnimation;
        /** The bed it lies in; null standing. */
        public @Nullable BlockPos inBed;

        public int animations;
        /** The last walking speed factor set (1 = normal). */
        public double speed = 1;

        Body(int colonyId, int citizenId, String name, Vec3 position) {
            this.colonyId = colonyId;
            this.citizenId = citizenId;
            this.name = name;
            this.position = position;
        }
    }

    public final Map<BodyId, Body> bodies = new LinkedHashMap<>();
    public boolean refuseSpawn;
    /** Positions where spawn fails, as when the world has no room there. */
    public final Set<BlockPos> refuseSpawnAt = new HashSet<>();
    /** Positions with no room in any column 5 blocks around them, the core's whole search (CitizenArrival). */
    public final Set<BlockPos> refuseSpawnAround = new HashSet<>();
    /** The positions that are beds a body may lie in. */
    public final Set<BlockPos> beds = new HashSet<>();
    /** Beds someone outside the colony (a player) lies in. */
    public final Set<BlockPos> takenBeds = new HashSet<>();
    /** The positions that are seats a body may sit on. */
    public final Set<BlockPos> seats = new HashSet<>();
    /** Seats someone outside the colony (a player) sits on. */
    public final Set<BlockPos> takenSeats = new HashSet<>();
    /** When set (and {@link #navEndsAt} is not), moveTo teleports the body to its target and reports ARRIVED. */
    public boolean instant;
    /** When set (and not instant), moveTo never moves the body and navStatus stays MOVING: a nav that never ends. */
    public boolean frozen;
    /**
     * When set, every moveTo ends there at once with {@link #navEndStatus}, wherever it was sent: a nav that ends
     * elsewhere, as Hytale's best partial path onto a roof. Wins over {@link #instant} and {@link #frozen}.
     */
    public @Nullable Vec3 navEndsAt;
    /** When set, reading a nav's status throws, as a broken adapter would: a job AI's machine then fails. */
    public boolean failNav;
    /** How a nav sent elsewhere by {@link #navEndsAt} ends. */
    public NavStatus navEndStatus = NavStatus.ARRIVED;
    /** Every teleport target, in call order. */
    public final List<Vec3> teleports = new ArrayList<>();
    /** Every moveTo target, in call order. */
    public final List<Vec3> moves = new ArrayList<>();
    /** Every lookAt target, in call order. */
    public final List<Vec3> looks = new ArrayList<>();
    /** Every setDisplayName call, in call order. */
    public final List<String> renames = new ArrayList<>();
    /** Every body told to get up, in order, lying or not. */
    public final List<BodyId> wakeUps = new ArrayList<>();

    private long next = 1;

    /** Simulates a body that already exists in the world (e.g. loaded from a chunk). */
    public BodyId existing(int colonyId, int citizenId, Vec3 pos) {
        BodyId id = new BodyId(next++);
        bodies.put(id, new Body(colonyId, citizenId, "", pos));
        return id;
    }

    public long aliveCount() {
        return bodies.values().stream().filter(b -> b.alive).count();
    }

    @Override
    public Optional<BodyId> spawn(WorldKey world, BlockPos near, int colonyId, int citizenId, String displayName) {
        boolean noRoomAround = refuseSpawnAround.stream()
                .anyMatch(p -> Math.abs(p.x() - near.x()) <= 5 && Math.abs(p.z() - near.z()) <= 5);
        if (refuseSpawn || noRoomAround || refuseSpawnAt.contains(near)) {
            return Optional.empty();
        }
        BodyId id = new BodyId(next++);
        bodies.put(id, new Body(colonyId, citizenId, displayName, Vec3.center(near)));
        return Optional.of(id);
    }

    @Override
    public boolean isAlive(BodyId body) {
        Body b = bodies.get(body);
        return b != null && b.alive;
    }

    @Override
    public int healthPercent(BodyId body) {
        return isAlive(body) ? bodies.get(body).healthPercent : 0;
    }

    @Override
    public int defensePercent(BodyId body) {
        return isAlive(body) ? bodies.get(body).defensePercent : 0;
    }

    @Override
    public double health(BodyId body) {
        return isAlive(body) ? bodies.get(body).health : 0;
    }

    @Override
    public double maxHealth(BodyId body) {
        return isAlive(body) ? bodies.get(body).maxHealth : 0;
    }

    @Override
    public void heal(BodyId body, double amount) {
        if (isAlive(body)) {
            Body b = bodies.get(body);
            b.health = Math.min(b.maxHealth, b.health + amount);
        }
    }

    @Override
    public void setStarving(BodyId body, boolean starving) {
        if (isAlive(body)) {
            bodies.get(body).starving = starving;
        }
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        return isAlive(body) ? Optional.of(bodies.get(body).position) : Optional.empty();
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        Body b = bodies.get(body);
        moves.add(target);
        b.target = target;
        b.status = NavStatus.MOVING;
        Vec3 elsewhere = navEndsAt;
        if (elsewhere != null) {
            b.position = elsewhere;
            b.status = navEndStatus;
        } else if (instant && !frozen) {
            b.position = target;
            b.status = NavStatus.ARRIVED;
        }
    }

    @Override
    public void teleport(BodyId body, Vec3 target) {
        Body b = bodies.get(body);
        teleports.add(target);
        b.position = target;
        b.status = NavStatus.IDLE;
    }

    @Override
    public void setMovementSpeed(BodyId body, double factor) {
        Body b = bodies.get(body);
        if (b != null) {
            b.speed = factor;
        }
    }

    @Override
    public List<Vec3> path(BodyId body) {
        Body b = bodies.get(body);
        return b == null ? List.of() : b.path;
    }

    @Override
    public NavStatus navStatus(BodyId body) {
        if (failNav) {
            throw new IllegalStateException("failing fake nav");
        }
        return bodies.get(body).status;
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        bodies.get(body).name = name;
        renames.add(name);
    }

    @Override
    public void lookAt(BodyId body, Vec3 target) {
        looks.add(target);
    }

    @Override
    public void despawn(BodyId body) {
        Body b = bodies.get(body);
        if (b != null) b.alive = false;
    }

    @Override
    public void setHeldItem(BodyId body, Optional<ItemKey> item) {
        bodies.get(body).held = item.orElse(null);
    }

    @Override
    public void setArmor(BodyId body, List<Optional<ItemAmount>> pieces) {
        bodies.get(body).armor = List.copyOf(pieces);
    }

    @Override
    public void playAnimation(BodyId body, BodyAnimation animation) {
        Body b = bodies.get(body);
        b.lastAnimation = animation;
        b.animations++;
    }

    /** A bed in {@link #beds} that no body nor {@link #takenBeds} holds: the body lies on it. */
    @Override
    public boolean sleepIn(BodyId body, BlockPos bed) {
        Body b = bodies.get(body);
        boolean taken = takenBeds.contains(bed) || bodies.values().stream().anyMatch(o -> bed.equals(o.inBed));
        if (b == null || !b.alive || !beds.contains(bed) || taken) {
            return false;
        }
        b.inBed = bed;
        b.position = Vec3.center(bed);
        b.status = NavStatus.IDLE;
        return true;
    }

    @Override
    public boolean isInBed(BodyId body) {
        Body b = bodies.get(body);
        return b != null && b.inBed != null;
    }

    /** Stands up beside the bed, one block east. */
    @Override
    public void wakeUp(BodyId body) {
        wakeUps.add(body);
        Body b = bodies.get(body);
        if (b != null && b.inBed != null) {
            b.position = Vec3.center(b.inBed.offset(1, 0, 0));
            b.inBed = null;
        }
    }

    /** A seat in {@link #seats} that no other body nor {@link #takenSeats} holds: the body sits on it. */
    @Override
    public boolean sitOn(BodyId body, BlockPos seat) {
        Body b = bodies.get(body);
        if (b == null || !b.alive || !seats.contains(seat)) {
            return false;
        }
        if (seat.equals(b.seat)) {
            return true;
        }
        if (isSeatTaken(seat)) {
            return false;
        }
        b.seat = seat;
        b.position = Vec3.center(seat);
        b.status = NavStatus.IDLE;
        return true;
    }

    @Override
    public boolean isSeatTaken(BlockPos seat) {
        return takenSeats.contains(seat) || bodies.values().stream().anyMatch(o -> seat.equals(o.seat));
    }

    @Override
    public void standUp(BodyId body) {
        Body b = bodies.get(body);
        if (b != null) {
            b.seat = null;
        }
    }
}
