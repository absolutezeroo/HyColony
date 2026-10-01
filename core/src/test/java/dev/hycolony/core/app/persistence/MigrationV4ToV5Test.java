package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 5 (SP3b-2 farmer): the colony keeps its fields. */
class MigrationV4ToV5Test {
    private static final String FIXTURE = "colony-v4-fields.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV4ToV5Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v4ColonyLoadsWithNoField() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());

        m.persistence().loadAll();

        Colony c = m.byId(1).orElseThrow();
        assertTrue(c.registries().fields().all().isEmpty());
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
        assertTrue(saved.contains("\"fields\":[]"), saved);
    }

    @Test
    void v4ToV5GivesTheColonyNoField() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        assertTrue(migrated.getAsJsonArray("fields").isEmpty());
    }
}
