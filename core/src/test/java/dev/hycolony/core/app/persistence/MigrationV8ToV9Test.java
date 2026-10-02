package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonySettings.Toggle;
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

/** Schema 9 (construction tape): the colony's construction tape setting, on as in MC (BuildingTownHall tape). */
class MigrationV8ToV9Test {
    private static final String FIXTURE = "colony-v8-tape.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV8ToV9Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ColonyManager load() {
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());
        m.persistence().loadAll();
        return m;
    }

    @Test
    void v8ToV9TurnsTheConstructionTapeOn() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        JsonObject settings = migrated.getAsJsonObject("settings");
        assertTrue(settings.get("constructionTape").getAsBoolean());
        assertFalse(settings.get("autoHousing").getAsBoolean(), "the other settings are kept");
    }

    @Test
    void theTapeSettingLoadsAndSurvivesASave() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = load();
        Colony c = m.byId(1).orElseThrow();
        assertTrue(c.settings().get(Toggle.CONSTRUCTION_TAPE));

        c.settings().set(Toggle.CONSTRUCTION_TAPE, false);
        c.markDirty();
        m.persistence().saveAll();

        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
        assertFalse(load().byId(1).orElseThrow().settings().get(Toggle.CONSTRUCTION_TAPE));
    }
}
