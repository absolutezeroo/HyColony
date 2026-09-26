package dev.hycolony.core.citizen;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Port of MineColonies' CitizenManager for SP0: initial citizens, respawn, body binding, AI. */
public final class CitizenManager {
    public static final int RESPAWN_CHECK_TICKS = 5 * 60 * 20;
    public static final int INITIAL_SPAWN_FIRST = 30 * 20;
    public static final int INITIAL_SPAWN_RESET = 60 * 20;
    /** MineColonies' overall happiness of an empty colony; replaced by the happiness system in SP4. */
    public static final double PLACEHOLDER_HAPPINESS = 5.5;

    private final Colony colony;
    private final Map<Integer, CitizenData> citizens = new TreeMap<>();
    private final Map<Integer, BodyId> bodies = new HashMap<>();
    private final Map<Integer, CitizenAI> ais = new HashMap<>();
    private int respawnInterval = INITIAL_SPAWN_FIRST;
    private int citizenRespawnTimer = RESPAWN_CHECK_TICKS;
    private boolean failNextTick;

    public CitizenManager(Colony colony) {
        this.colony = colony;
    }

    private ColonyContext ctx() {
        return colony.context();
    }

    public Collection<CitizenData> all() {
        return Collections.unmodifiableCollection(citizens.values());
    }

    public Optional<CitizenData> get(int id) {
        return Optional.ofNullable(citizens.get(id));
    }

    public Optional<BodyId> bodyOf(int id) {
        return Optional.ofNullable(bodies.get(id));
    }

    public Optional<CitizenState> aiState(int id) {
        return Optional.ofNullable(ais.get(id)).map(CitizenAI::state);
    }

    public Optional<Msg> jobActivity(int id) {
        return Optional.ofNullable(ais.get(id)).flatMap(CitizenAI::jobActivity);
    }

    /** Adds a citizen loaded from disk. */
    public void restore(CitizenData data) {
        citizens.put(data.id(), data);
    }

    /** Every 60 ticks while ACTIVE: record positions. */
    public void tickData() {
        if (failNextTick) {
            failNextTick = false;
            throw new IllegalStateException("test failure");
        }
        for (Map.Entry<Integer, BodyId> e : bodies.entrySet()) {
            ctx().bodies()
                    .position(e.getValue())
                    .ifPresent(p -> citizens.get(e.getKey()).setLastPosition(p));
        }
        if (!bodies.isEmpty()) {
            colony.markDirty();
        }
    }

    /** Every core tick: AI of citizens whose body is alive. */
    public void tickAi() {
        for (Map.Entry<Integer, CitizenAI> e : ais.entrySet()) {
            if (ctx().bodies().isAlive(bodies.get(e.getKey()))) {
                e.getValue().tick();
            }
        }
    }

    /** Slow tick (500 ticks) while ACTIVE. Mirrors MineColonies CitizenManager.onColonyTick. */
    public void onColonyTick() {
        Optional<Building> townHall = colony.buildings().townHall();
        if (townHall.isEmpty()) {
            return;
        }
        if ((citizenRespawnTimer -= 500) < 0) {
            citizenRespawnTimer = RESPAWN_CHECK_TICKS;
            citizens.values().forEach(this::updateBodyIfNecessary);
        }
        if (citizens.size() < ctx().config().initialCitizenAmount()) {
            respawnInterval -= 500 + 60 * townHall.get().level();
            if (respawnInterval <= 0) {
                respawnInterval = INITIAL_SPAWN_RESET;
                spawnInitialCitizen(townHall.get().position());
            }
        }
    }

    private void spawnInitialCitizen(BlockPos townHall) {
        int femaleCount = (int) citizens.values().stream()
                .filter(c -> c.gender() == Gender.FEMALE)
                .count();
        CitizenData data = createAndRegister();
        Gender gender;
        if (citizens.size() == 1) {
            gender = ctx().random().nextBoolean() ? Gender.FEMALE : Gender.MALE;
        } else if (femaleCount < (citizens.size() - 1) / 2.0) {
            gender = Gender.FEMALE;
        } else {
            gender = Gender.MALE;
        }
        data.setGender(gender);
        data.setName(ctx().names().generate(ctx().random(), gender));
        spawnBody(data, townHall);
        colony.log().add("citizenSpawned", colony.day(), data.name());
        colony.markDirty();
        ctx().bus().post(new CitizenSpawned(colony, data));
    }

    /** MineColonies createAndRegisterCivilianData + initForNewCivilian. */
    private CitizenData createAndRegister() {
        int id = 1;
        while (citizens.containsKey(id)) {
            id++;
        }
        CitizenData data = new CitizenData(id);
        data.setSaturation(CitizenData.MAX_SATURATION);
        int levelCap = (int) PLACEHOLDER_HAPPINESS * 2;
        if (citizens.size() < ctx().config().initialCitizenAmount()) {
            levelCap = Math.max(5, levelCap);
        }
        data.setSkills(Skills.initRandom(levelCap, ctx().random()));
        citizens.put(id, data);
        return data;
    }

    private void updateBodyIfNecessary(CitizenData data) {
        BodyId body = bodies.get(data.id());
        if (body != null && ctx().bodies().isAlive(body)) {
            return;
        }
        BlockPos target = data.respawnPosition() != null
                ? data.respawnPosition()
                : data.lastPosition() != null
                        ? data.lastPosition().toBlockPos()
                        : colony.buildings().townHall().map(Building::position).orElse(colony.center());
        if (!ctx().worldQuery().isLoaded(target)) {
            return;
        }
        spawnBody(data, target);
    }

    private void spawnBody(CitizenData data, BlockPos near) {
        ctx().bodies()
                .spawn(
                        ctx().world(),
                        near,
                        colony.id(),
                        data.id(),
                        colony.nameplates().nameFor(data))
                .ifPresent(body -> bind(data, body));
    }

    private void bind(CitizenData data, BodyId body) {
        bodies.put(data.id(), body);
        ais.put(data.id(), new CitizenAI(colony, data, body));
    }

    /** A body tagged with this colony was loaded into the world. */
    public void onBodyLoaded(BodyId body, int citizenId) {
        if (body.equals(bodies.get(citizenId))) {
            return; // already bound to this exact body: don't rebind and reset the AI
        }
        CitizenData data = citizens.get(citizenId);
        BodyId current = bodies.get(citizenId);
        boolean duplicate =
                current != null && !current.equals(body) && ctx().bodies().isAlive(current);
        if (data == null || duplicate) {
            ctx().bodies().despawn(body);
            return;
        }
        bind(data, body);
    }

    public void onBodyUnloaded(BodyId body) {
        bodies.entrySet().removeIf(e -> {
            if (e.getValue().equals(body)) {
                ais.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public void despawnAll() {
        bodies.values().forEach(ctx().bodies()::despawn);
        bodies.clear();
        ais.clear();
    }

    /** Test hook: next tickData throws. */
    public void failNextTickForTest() {
        failNextTick = true;
    }
}
