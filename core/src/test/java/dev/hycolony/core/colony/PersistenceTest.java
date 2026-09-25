package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    @TempDir Path dir;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private ColonyManager manager(TestContexts t) {
        ColonyManager m = new ColonyManager(t.context());
        m.setStorage(new FileColonyStorage(dir), MigrationChain.sp1());
        return m;
    }

    @Test
    void fullRoundTrip() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 1);
        Colony c = m.confirmFoundation(alice, "Rivendell").orElseThrow();
        m.setRank(alice, c.id(), bob, "Bob", Permissions.FRIEND);
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        c.setDay(7);
        m.saveAll();

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.loadAll();
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
        assertEquals(c.log().entries(), r.log().entries());
        assertTrue(reloaded.colonyAt(new BlockPos(10, 64, 10)).isPresent()); // territory rebuilt
    }

    @Test
    void unknownBuildingTypeIsPreservedVerbatim() throws Exception {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "A").orElseThrow();
        m.saveAll();
        Path file = dir.resolve("colony-" + c.id() + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject alien = new JsonObject();
        alien.addProperty("type", "future:windmill");
        alien.addProperty("secret", 42);
        json.getAsJsonArray("buildings").add(alien);
        Files.writeString(file, json.toString());

        ColonyManager reloaded = manager(new TestContexts());
        reloaded.loadAll();
        reloaded.byId(c.id()).orElseThrow().markDirty();
        reloaded.saveAll();
        String saved = Files.readString(file);
        assertTrue(saved.contains("future:windmill") && saved.contains("\"secret\":42"), saved);
    }

    @Test
    void newerSchemaIsSkippedAndNeverOverwritten() throws Exception {
        Files.writeString(dir.resolve("colony-9.json"), "{\"schemaVersion\":99,\"id\":9}");
        ColonyManager m = manager(new TestContexts());
        m.loadAll();
        assertTrue(m.byId(9).isEmpty());
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "New").orElseThrow();
        assertTrue(c.id() > 9);
        m.saveAll();
        assertEquals("{\"schemaVersion\":99,\"id\":9}", Files.readString(dir.resolve("colony-9.json")));
    }

    @Test
    void bodiesOfUnloadedColonyAreLeftAlone() throws Exception {
        Files.writeString(dir.resolve("colony-9.json"), "{\"schemaVersion\":99,\"id\":9}");
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.loadAll();
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
        m.loadAll();
        Colony c = m.byId(1).orElseThrow();
        assertEquals("Fixture", c.name());
        assertEquals(1, c.citizens().all().size());
    }

    @Test
    void deleteArchivesFile() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "A").orElseThrow();
        m.saveAll();
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
        public List<Integer> colonyIds() throws IOException { return delegate.colonyIds(); }

        @Override
        public int highestIdEverUsed() throws IOException { return delegate.highestIdEverUsed(); }

        @Override
        public Optional<JsonObject> load(int id) throws IOException {
            if (id == failingId) {
                throw new IOException("simulated read failure for colony " + id);
            }
            return delegate.load(id);
        }

        @Override
        public void save(int id, String json) throws IOException { delegate.save(id, json); }

        @Override
        public void backupVersion(int id, int schemaVersion, String json) throws IOException {
            delegate.backupVersion(id, schemaVersion, json);
        }

        @Override
        public void archive(int id) throws IOException { delegate.archive(id); }
    }

    @Test
    void loadAllContinuesAfterOneColonyIOException() {
        TestContexts t = new TestContexts();
        ColonyManager m = manager(t);
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony a = m.confirmFoundation(alice, "A").orElseThrow();
        m.beginFoundation(bob, "Bob", new BlockPos(2000, 64, 0), 0);
        Colony b = m.confirmFoundation(bob, "B").orElseThrow();
        m.saveAll();

        ColonyManager reloaded = new ColonyManager(new TestContexts().context());
        reloaded.setStorage(new FlakyStorage(dir, a.id()), MigrationChain.sp1());
        reloaded.loadAll();

        assertTrue(reloaded.byId(a.id()).isEmpty());
        assertTrue(reloaded.byId(b.id()).isPresent());
    }
}
