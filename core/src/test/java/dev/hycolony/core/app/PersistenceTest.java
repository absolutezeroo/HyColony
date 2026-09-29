package dev.hycolony.core.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.shared.ClaimRadius;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    @TempDir
    Path dir;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private ColonyManager manager(TestContexts t) {
        ColonyManager m = t.manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        return m;
    }

    @Test
    void fullRoundTrip() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 1);
        Colony c = m.foundation().confirm(alice, "Rivendell").orElseThrow();
        m.administration().setRank(alice, c.id(), bob, "Bob", Permissions.FRIEND);
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        c.setDay(7);
        c.citizens().all().iterator().next().setLeisureTime(1234);
        m.persistence().saveAll();

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.persistence().loadAll();
        Colony r = reloaded.byId(c.id()).orElseThrow();
        assertEquals("Rivendell", r.name());
        assertEquals(7, r.day());
        assertEquals(Permissions.FRIEND, r.permissions().rankOf(bob).id());
        assertEquals(alice, r.permissions().owner());
        assertEquals(1, r.buildings().townHall().orElseThrow().rotation());
        assertEquals(4, r.citizens().all().size());
        CitizenData a = c.citizens().all().iterator().next();
        CitizenData b = r.citizens().all().iterator().next();
        assertEquals(a.name(), b.name());
        assertEquals(a.skills().level(Skill.Focus), b.skills().level(Skill.Focus));
        assertEquals(1234, b.leisureTime());
        assertEquals(c.log().entries(), r.log().entries());
        assertTrue(reloaded.colonyAt(new BlockPos(10, 64, 10)).isPresent()); // territory rebuilt
    }

    @Test
    void unknownBuildingTypeIsPreservedVerbatim() throws Exception {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        m.persistence().saveAll();
        Path file = dir.resolve("colony-" + c.id() + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject alien = new JsonObject();
        alien.addProperty("type", "future:windmill");
        alien.addProperty("secret", 42);
        json.getAsJsonArray("buildings").add(alien);
        Files.writeString(file, json.toString());

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.persistence().loadAll();
        reloaded.byId(c.id()).orElseThrow().markDirty();
        reloaded.persistence().saveAll();
        String saved = Files.readString(file);
        assertTrue(saved.contains("future:windmill") && saved.contains("\"secret\":42"), saved);
    }

    @Test
    void unknownJobOnLoadLeavesCitizenJobless() {
        TestContexts t = new TestContexts();
        t.jobs.register(TestJobs.TYPE);
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(alice, "Alice")));
        CitizenData citizen = new CitizenData(1);
        citizen.setJob(TestJobs.TYPE.factory().apply(citizen));
        citizen.setWorkBuilding(new BlockPos(5, 64, 5));
        c.citizens().restore(citizen);

        JsonObject json = ColonySerializer.write(c);

        // A fresh context whose JobRegistry never registered TestJobs.TYPE: the job type is unknown on load.
        Colony reloaded = ColonySerializer.read(json, new TestContexts().context(), new TerritoryIndex());
        CitizenData restored = reloaded.citizens().get(1).orElseThrow();
        assertTrue(restored.job().isEmpty());
        assertNull(restored.workBuilding());
    }

    @Test
    void workerWithoutCitizenIsDroppedFromHutOnLoad() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        BlockPos hut = new BlockPos(20, 64, 0);
        m.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), hut, 0);
        Building b = c.buildings().at(hut).orElseThrow();
        assertTrue(b.module(WorkerModule.class)
                .orElseThrow()
                .hire(c, b, new CitizenData(99))); // 99 is not a colony citizen

        Colony clean = ColonySerializer.read(ColonySerializer.write(c), t.context(), new TerritoryIndex());
        assertTrue(clean.buildings()
                .at(hut)
                .orElseThrow()
                .module(WorkerModule.class)
                .orElseThrow()
                .workers()
                .isEmpty());
        assertTrue(clean.isDirty());
        assertFalse(
                ColonySerializer.read(ColonySerializer.write(clean), t.context(), new TerritoryIndex())
                        .isDirty(),
                "a consistent save loads clean");
    }

    @Test
    void newerSchemaIsSkippedAndNeverOverwritten() throws Exception {
        Files.writeString(dir.resolve("colony-9.json"), "{\"schemaVersion\":99,\"id\":9}");
        ColonyManager m = manager(new TestContexts());
        m.persistence().loadAll();
        assertTrue(m.byId(9).isEmpty());
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "New").orElseThrow();
        assertTrue(c.id() > 9);
        m.persistence().saveAll();
        assertEquals("{\"schemaVersion\":99,\"id\":9}", Files.readString(dir.resolve("colony-9.json")));
    }

    @Test
    void bodiesOfUnloadedColonyAreLeftAlone() throws Exception {
        Files.writeString(dir.resolve("colony-9.json"), "{\"schemaVersion\":99,\"id\":9}");
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.persistence().loadAll();
        var body = t.bodies.existing(9, 1, new Vec3(0, 64, 0));
        m.onBodyLoaded(body, 9, 1);
        assertTrue(t.bodies.isAlive(body));
    }

    @Test
    void v1FixtureLoads() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v1.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        ColonyManager m = manager(new TestContexts());
        m.persistence().loadAll();
        Colony c = m.byId(1).orElseThrow();
        assertEquals("Fixture", c.name());
        assertEquals(1, c.citizens().all().size());
    }

    @Test
    void deleteArchivesFile() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        m.persistence().saveAll();
        m.deleteColony(c.id());
        assertTrue(Files.notExists(dir.resolve("colony-" + c.id() + ".json")));
        assertTrue(Files.isDirectory(dir.resolve("archive")));
    }

    /** Fails to load one chosen colony id while delegating everything else to a real FileColonyStorage. */
    private static final class FlakyStorage implements ColonyStorage {
        private final FileColonyStorage delegate;
        private final int failingId;

        FlakyStorage(Path dir, int failingId) {
            this.delegate = new FileColonyStorage(dir);
            this.failingId = failingId;
        }

        @Override
        public List<Integer> colonyIds() throws IOException {
            return delegate.colonyIds();
        }

        @Override
        public int highestIdEverUsed() throws IOException {
            return delegate.highestIdEverUsed();
        }

        @Override
        public Optional<JsonObject> load(int id) throws IOException {
            if (id == failingId) {
                throw new IOException("simulated read failure for colony " + id);
            }
            return delegate.load(id);
        }

        @Override
        public void save(int id, String json) throws IOException {
            delegate.save(id, json);
        }

        @Override
        public void backupVersion(int id, int schemaVersion, String json) throws IOException {
            delegate.backupVersion(id, schemaVersion, json);
        }

        @Override
        public void archive(int id) throws IOException {
            delegate.archive(id);
        }
    }

    @Test
    void loadAllContinuesAfterOneColonyIOException() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony a = m.foundation().confirm(alice, "A").orElseThrow();
        m.foundation().begin(bob, "Bob", new BlockPos(2000, 64, 0), 0);
        Colony b = m.foundation().confirm(bob, "B").orElseThrow();
        m.persistence().saveAll();

        ColonyManager reloaded = new TestContexts().manager();
        reloaded.persistence().setStorage(new FlakyStorage(dir, a.id()), MigrationChain.sp3b());
        reloaded.persistence().loadAll();

        assertTrue(reloaded.byId(a.id()).isEmpty());
        assertTrue(reloaded.byId(b.id()).isPresent());
    }

    /** Simulation: the cells a finished building claimed were lost at the next load. */
    @Test
    void claimsOfBuiltBuildingsSurviveReload() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        BlockPos edge = new BlockPos(70, 64, 0), beyond = new BlockPos(85, 64, 0), unbuilt = new BlockPos(-70, 64, 0);
        m.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), edge, 0);
        m.huts().place(c, ConstructionBuildingTypes.RESIDENCE.id(), unbuilt, 0);
        c.buildings().at(edge).orElseThrow().setLevel(1);
        c.claimAround(edge, ClaimRadius.of(ConstructionBuildingTypes.BUILDER.id(), 1)); // as a finished build does
        assertTrue(c.contains(beyond));
        m.persistence().saveAll();

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.persistence().loadAll();

        Colony r = reloaded.byId(c.id()).orElseThrow();
        assertTrue(r.contains(beyond));
        assertFalse(r.contains(new BlockPos(-85, 64, 0))); // level 0 claims nothing
        assertFalse(r.isDirty());
    }

    /** A's hut (cell 12, level 1) reaches cell 13, B's initial square: loading A first must not steal it. */
    @Test
    void initialSquaresAreClaimedBeforeBuildingsOnReload() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony a = m.foundation().confirm(alice, "A").orElseThrow();
        m.foundation().begin(bob, "Bob", new BlockPos(17 * ClaimCell.SIZE, 64, 0), 0);
        Colony b = m.foundation().confirm(bob, "B").orElseThrow();
        BlockPos hut = new BlockPos(12 * ClaimCell.SIZE, 64, 0), bCell = new BlockPos(13 * ClaimCell.SIZE, 64, 0);
        m.huts().place(a, ConstructionBuildingTypes.BUILDER.id(), hut, 0);
        a.buildings().at(hut).orElseThrow().setLevel(1);
        a.claimAround(hut, ClaimRadius.of(ConstructionBuildingTypes.BUILDER.id(), 1)); // never steals B's cell
        assertTrue(b.contains(bCell));
        m.persistence().saveAll();

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.persistence().loadAll();

        assertTrue(reloaded.byId(b.id()).orElseThrow().contains(bCell));
        assertTrue(reloaded.byId(a.id()).orElseThrow().contains(new BlockPos(11 * ClaimCell.SIZE, 64, 0)));
    }

    @Test
    void citizenOfMissingWorkBuildingIsFreedOnLoad() {
        TestContexts t = new TestContexts();
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(alice, "Alice")));
        CitizenData citizen = new CitizenData(1);
        citizen.setJob(BuilderJob.TYPE.factory().apply(citizen));
        citizen.setWorkBuilding(new BlockPos(5, 64, 5)); // no building there
        c.citizens().restore(citizen);

        Colony reloaded = ColonySerializer.read(ColonySerializer.write(c), t.context(), new TerritoryIndex());

        CitizenData restored = reloaded.citizens().get(1).orElseThrow();
        assertTrue(restored.job().isEmpty());
        assertNull(restored.workBuilding());
        assertTrue(reloaded.isDirty(), "healed state is saved at the next save");
    }

    @Test
    void requestsOfMissingRequesterAreCancelledOnLoad() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.foundation().confirm(alice, "A").orElseThrow();
        BlockPos res = new BlockPos(20, 64, 0);
        m.huts().place(c, ConstructionBuildingTypes.RESIDENCE.id(), res, 0);
        c.requests()
                .createAndAssign(
                        c.buildings().at(res).orElseThrow(), new StackRequest(new ItemKey("Stone"), 4, 4, true), -1);
        c.requests()
                .createAndAssign(
                        c.buildings().townHall().orElseThrow(), new StackRequest(new ItemKey("Stone"), 2, 2, true), -1);
        JsonObject json = ColonySerializer.write(c);
        JsonArray kept = new JsonArray();
        json.getAsJsonArray("buildings").forEach(b -> {
            if (!b.getAsJsonObject().get("type").getAsString().equals(ConstructionBuildingTypes.RESIDENCE.id())) {
                kept.add(b);
            }
        });
        json.add("buildings", kept); // the residence is missing from the save

        Colony reloaded = ColonySerializer.read(json, t.context(), new TerritoryIndex());

        assertEquals(1, reloaded.requests().all().size()); // the town hall's is kept
        assertEquals(
                2,
                reloaded.requests()
                        .all()
                        .iterator()
                        .next()
                        .deliverable()
                        .orElseThrow()
                        .count());
    }
}
