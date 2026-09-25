package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaV2MigrationTest {
    @TempDir Path dir;

    private ColonyManager manager() {
        ColonyManager m = new ColonyManager(new TestContexts().context());
        m.setStorage(new FileColonyStorage(dir), MigrationChain.sp1());
        return m;
    }

    @Test
    void v1FixtureMigratesToV2() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v1.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        ColonyManager m = manager();
        m.loadAll();
        Colony c = m.byId(1).orElseThrow();
        assertEquals("Fixture", c.name());
        m.saveAll();

        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":2"), saved);
        assertTrue(Files.exists(dir.resolve("colony-1.v1.json")));
        String backup = Files.readString(dir.resolve("colony-1.v1.json"));
        assertTrue(backup.contains("\"schemaVersion\":1"), backup);
    }

    @Test
    void v2FixtureLoads() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v2.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        ColonyManager m = manager();
        m.loadAll();
        Colony c = m.byId(1).orElseThrow();
        assertEquals("Fixture", c.name());
        assertEquals(1, c.citizens().all().size());
    }

    @Test
    void citizenInventoryRoundTrip() {
        UUID alice = UUID.randomUUID();
        ColonyManager m = manager();
        m.beginFoundation(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = m.confirmFoundation(alice, "Rivendell").orElseThrow();
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        CitizenData original = c.citizens().all().iterator().next();
        ItemKey pick = new ItemKey("hycolony:test_pickaxe");
        original.inventory().insert(new ItemAmount(pick, 5), item -> 64);
        m.saveAll();

        ColonyManager reloaded = manager();
        reloaded.loadAll();
        Colony r = reloaded.byId(c.id()).orElseThrow();
        CitizenData restored = r.citizens().get(original.id()).orElseThrow();
        assertEquals(5, restored.inventory().count(pick));
    }
}
