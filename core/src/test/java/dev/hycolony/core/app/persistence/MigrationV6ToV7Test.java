package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.EventLog;
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

/** Schema 7 (town hall): the colony style, the move-in setting and the event positions. */
class MigrationV6ToV7Test {
    private static final String FIXTURE = "colony-v6-townhall.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV6ToV7Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v6ToV7TakesTheTownHallStyleAndMcDefaults() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        assertEquals("Medieval Oak", migrated.get("style").getAsString());
        assertTrue(migrated.getAsJsonObject("settings").get("moveIn").getAsBoolean());
        assertTrue(migrated.getAsJsonArray("eventLog")
                .get(0)
                .getAsJsonObject()
                .get("pos")
                .isJsonNull());
    }

    @Test
    void v6ColonyLoadsWithItsTownHallStyleAndNoPositions() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());

        m.persistence().loadAll();

        Colony c = m.byId(1).orElseThrow();
        assertEquals("Medieval Oak", c.settings().style());
        assertTrue(c.settings().moveIn());
        assertEquals(2, c.log().entries().size());
        assertTrue(c.log().entries().stream().map(EventLog.Entry::pos).allMatch(p -> p.isEmpty()));
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
        assertEquals(
                "Medieval Oak",
                JsonParser.parseString(saved).getAsJsonObject().get("style").getAsString());
    }
}
