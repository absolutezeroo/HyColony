package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A save written with content this build does not know (a disabled pack, an older version) loads without loss. */
class TolerantLoadTest {
    private static final BlockPos HUT = new BlockPos(8, 64, 0);
    private static final BuildingType TEST_HUT = new BuildingType("test:hut", "hut.test", 5, List.of());

    @TempDir
    Path dir;

    private void install(String fixture) throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
    }

    private ColonyManager load(TestContexts t) {
        ColonyManager m = new ColonyManager(t.context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp2());
        m.persistence().loadAll();
        return m;
    }

    private JsonObject savedCitizen() throws IOException {
        JsonObject saved = JsonParser.parseString(Files.readString(dir.resolve("colony-1.json")))
                .getAsJsonObject();
        return saved.getAsJsonArray("citizens").get(0).getAsJsonObject();
    }

    private static RequestToken token(int n) {
        return new RequestToken(new UUID(0, n));
    }

    @Test
    void aRequestOfAnUnknownTypeIsSkippedAndTheColonyLoads() throws IOException {
        install("colony-v3-unknown-request.json");

        ColonyManager m = load(new TestContexts());

        Colony c = m.byId(1).orElseThrow();
        assertTrue(c.requests().get(token(1)).isPresent(), "a known request still loads");
        assertTrue(c.requests().get(token(2)).isEmpty(), "unknown type");
        assertTrue(c.requests().get(token(3)).isEmpty(), "child of a skipped request");
        assertTrue(c.requests().get(token(4)).isEmpty(), "unknown state");
        assertTrue(c.requests().get(token(5)).isEmpty(), "readable parent of a skipped child");
        assertTrue(c.requests().get(token(6)).isEmpty(), "unknown type, under a readable parent");
        assertTrue(c.requests().get(token(7)).isEmpty(), "unknown tool type");
        assertEquals(1, c.requests().all().size());
    }

    @Test
    void anUnknownJobIsKeptAndWrittenBackUnchanged() throws IOException {
        install("colony-v3-unknown-job.json");
        JsonObject before = savedCitizen();

        ColonyManager m = load(new TestContexts());
        Colony c = m.byId(1).orElseThrow();
        CitizenData d = c.citizens().get(1).orElseThrow();
        assertTrue(d.job().isEmpty(), "the job stays inactive while its type is unknown");
        c.markDirty();
        m.persistence().saveAll();

        JsonObject after = savedCitizen();
        assertEquals(before.get("job"), after.get("job"));
        assertEquals(before.get("work"), after.get("work"));
    }

    @Test
    void anUnknownJobAtItsKeptUnknownHutKeepsTheAssignmentAndTheRawJob() throws IOException {
        install("colony-v3-unknown-job.json");

        CitizenData d =
                load(new TestContexts()).byId(1).orElseThrow().citizens().get(1).orElseThrow();

        assertEquals(HUT, d.workBuilding());
        assertEquals(7, d.unknownJob().orElseThrow().get("actionsDone").getAsInt());
    }

    @Test
    void anUnknownHutWithAMalformedPositionDoesNotBreakTheLoad() throws IOException {
        install("colony-v3-unknown-job.json");
        Path file = dir.resolve("colony-1.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var buildings = json.getAsJsonArray("buildings");
        for (String pos : List.of("\"garbage\"", "[1, 2, 3]", "{\"x\": \"a\", \"y\": 64, \"z\": 0}", "{\"x\": 8}")) {
            JsonObject broken = new JsonObject();
            broken.addProperty("type", "removed:hut");
            broken.add("pos", JsonParser.parseString(pos));
            buildings.add(broken);
        }
        buildings.add(buildings.remove(1)); // the valid unknown hut is searched last
        Files.writeString(file, json.toString());

        CitizenData d =
                load(new TestContexts()).byId(1).orElseThrow().citizens().get(1).orElseThrow();

        assertEquals(HUT, d.workBuilding());
        assertTrue(d.unknownJob().isPresent());
    }

    @Test
    void anUnknownJobAtAKnownBuildingIsFreed() throws IOException {
        install("colony-v3-unknown-job.json");
        Path file = dir.resolve("colony-1.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject townHall = new JsonObject();
        townHall.addProperty("x", 0);
        townHall.addProperty("y", 64);
        townHall.addProperty("z", 0);
        json.getAsJsonArray("citizens").get(0).getAsJsonObject().add("work", townHall);
        Files.writeString(file, json.toString());

        CitizenData d =
                load(new TestContexts()).byId(1).orElseThrow().citizens().get(1).orElseThrow();

        assertNull(d.workBuilding(), "no unknown hut holds its job: the citizen is rehireable");
        assertTrue(d.unknownJob().isEmpty());
    }

    @Test
    void reEnablingTheJobRestoresTheAssignment() throws IOException {
        install("colony-v3-unknown-job.json");
        ColonyManager disabled = load(new TestContexts());
        disabled.byId(1).orElseThrow().markDirty();
        disabled.persistence().saveAll();

        TestContexts enabled = new TestContexts();
        enabled.jobs.register(TestJobs.TYPE);
        enabled.extraBuildingTypes.add(TEST_HUT);
        Colony c = load(enabled).byId(1).orElseThrow();
        CitizenData d = c.citizens().get(1).orElseThrow();

        assertEquals(TestJobs.TYPE, d.job().orElseThrow().type());
        assertEquals(7, d.job().orElseThrow().write().get("actionsDone").getAsInt());
        assertEquals(HUT, d.workBuilding());
        assertTrue(c.buildings().at(HUT).isPresent(), "the hut came back with the job");
    }
}
