package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FakeBodies implements CitizenBodies {
    public static final class Body {
        public final int colonyId, citizenId;
        public String name;
        public Vec3 position;
        public Vec3 target;
        public NavStatus status = NavStatus.IDLE;
        public boolean alive = true;

        Body(int colonyId, int citizenId, String name, Vec3 position) {
            this.colonyId = colonyId;
            this.citizenId = citizenId;
            this.name = name;
            this.position = position;
        }
    }

    public final Map<BodyId, Body> bodies = new LinkedHashMap<>();
    public boolean refuseSpawn;
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
        if (refuseSpawn) {
            return Optional.empty();
        }
        BodyId id = new BodyId(next++);
        bodies.put(id, new Body(colonyId, citizenId, displayName, Vec3.center(near)));
        return Optional.of(id);
    }

    @Override public boolean isAlive(BodyId body) { Body b = bodies.get(body); return b != null && b.alive; }
    @Override public Optional<Vec3> position(BodyId body) { return isAlive(body) ? Optional.of(bodies.get(body).position) : Optional.empty(); }
    @Override public void moveTo(BodyId body, Vec3 target) { Body b = bodies.get(body); b.target = target; b.status = NavStatus.MOVING; }
    @Override public NavStatus navStatus(BodyId body) { return bodies.get(body).status; }
    @Override public void setDisplayName(BodyId body, String name) { bodies.get(body).name = name; }
    @Override public void despawn(BodyId body) { Body b = bodies.get(body); if (b != null) b.alive = false; }
}
