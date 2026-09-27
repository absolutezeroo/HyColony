package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A save written with content this build does not know (a disabled pack, an older version) loads without loss. */
class TolerantLoadTest {
    private static final BlockPos TOWN_HALL = new BlockPos(0, 64, 0);

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
        assertEquals(TOWN_HALL, d.workBuilding());
        c.markDirty();
        m.persistence().saveAll();

        JsonObject after = savedCitizen();
        assertEquals(before.get("job"), after.get("job"));
        assertEquals(before.get("work"), after.get("work"));
    }

    @Test
    void reEnablingTheJobRestoresTheAssignment() throws IOException {
        install("colony-v3-unknown-job.json");
        ColonyManager disabled = load(new TestContexts());
        disabled.byId(1).orElseThrow().markDirty();
        disabled.persistence().saveAll();

        TestContexts enabled = new TestContexts();
        enabled.jobs.register(TestJobs.TYPE);
        CitizenData d = load(enabled).byId(1).orElseThrow().citizens().get(1).orElseThrow();

        assertEquals(TestJobs.TYPE, d.job().orElseThrow().type());
        assertEquals(7, d.job().orElseThrow().write().get("actionsDone").getAsInt());
        assertEquals(TOWN_HALL, d.workBuilding());
    }
}
