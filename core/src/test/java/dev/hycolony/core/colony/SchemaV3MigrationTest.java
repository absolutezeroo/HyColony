package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 3: a tool's wear moved from the builder job's per-item counter onto the stack itself. */
class SchemaV3MigrationTest {
    private static final ItemKey SHOVEL = new ItemKey("Tool_Shovel_Crude");

    @TempDir
    Path dir;

    @Test
    void theOldToolUsesCounterMovesOntoTheHeldTool() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v2-tooluses.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());

        m.persistence().loadAll();

        CitizenData d = m.byId(1).orElseThrow().citizens().get(1).orElseThrow();
        // The counter wore the stack a break removed: the last one holding that tool.
        assertEquals(Optional.of(new ItemAmount(SHOVEL, 1)), d.inventory().slot(0));
        assertEquals(Optional.of(new ItemAmount(SHOVEL, 1, 5)), d.inventory().slot(2));
        assertEquals(
                Optional.of(new ItemAmount(new ItemKey("hycolony:torch"), 4)),
                d.inventory().slot(1));
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
        assertFalse(saved.contains("toolUses"), saved);
    }
}
