package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.stats.ColonyStatistics;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 11 (citizens' death): the colony's statistics (MC StatisticsManager) and each citizen's mourning. */
class MigrationV10ToV11Test {
    private static final String FIXTURE = "colony-v10-mourning.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV10ToV11Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
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
    void v10ToV11GivesNoStatisticsAndNoMourning() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        assertEquals(0, migrated.getAsJsonObject("statistics").size());
        JsonObject mourning =
                migrated.getAsJsonArray("citizens").get(0).getAsJsonObject().getAsJsonObject("mourning");
        assertFalse(mourning.get("mourning").getAsBoolean());
        assertEquals(0, mourning.getAsJsonArray("deceased").size());
    }

    @Test
    void aSchema10SaveLoadsAndIsWrittenAsSchema11() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = load();
        Colony c = m.byId(1).orElseThrow();
        assertFalse(c.citizens().get(1).orElseThrow().mourning().isMourning());
        c.markDirty();
        m.persistence().saveAll();

        assertTrue(Files.readString(dir.resolve("colony-1.json")).contains("\"schemaVersion\":11"));
    }

    @Test
    void statisticsAndMourningSurviveASave() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = load();
        Colony c = m.byId(1).orElseThrow();
        c.registries().statistics().incrementBy(ColonyStatistics.DEATH, 2, 3);
        CitizenData aela = c.citizens().get(1).orElseThrow();
        aela.mourning().addDeceased("Bob");
        aela.mourning().onWakeUp();
        c.markDirty();
        m.persistence().saveAll();

        Colony again = load().byId(1).orElseThrow();

        assertEquals(Map.of(3, 2), again.registries().statistics().perDay(ColonyStatistics.DEATH));
        CitizenData back = again.citizens().get(1).orElseThrow();
        assertTrue(back.mourning().isMourning());
        assertEquals(Set.of("Bob"), back.mourning().deceased());
    }

    @Test
    void aBrokenSaveLoadsWhatItCan() throws IOException {
        JsonObject doc = JsonParser.parseString(fixture()).getAsJsonObject();
        doc.addProperty(MigrationChain.VERSION_KEY, ColonySerializer.SCHEMA_VERSION);
        doc.add("statistics", JsonParser.parseString("{\"death\":{\"x\":1,\"4\":\"y\",\"5\":2},\"bad\":3}"));
        JsonObject citizen = doc.getAsJsonArray("citizens").get(0).getAsJsonObject();
        citizen.add("mourning", JsonParser.parseString("{\"mourning\":true,\"deceased\":[7]}"));
        Files.writeString(dir.resolve("colony-1.json"), doc.toString());

        Colony c = load().byId(1).orElseThrow();

        assertEquals(Map.of(5, 2), c.registries().statistics().perDay(ColonyStatistics.DEATH));
        assertFalse(c.citizens().get(1).orElseThrow().mourning().isMourning(), "nobody to mourn: not mourning");
    }
}
