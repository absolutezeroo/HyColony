package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 6 (SP4): residences keep their residents and beds, citizens their sleep. */
class MigrationV5ToV6Test {
    private static final String FIXTURE = "colony-v5-homes.json";
    private static final BlockPos RESIDENCE = new BlockPos(8, 64, 0);

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV5ToV6Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v5ToV6GivesResidencesTheirSavedResidents() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        JsonObject modules =
                migrated.getAsJsonArray("buildings").get(1).getAsJsonObject().getAsJsonObject("modules");
        assertEquals(
                "[1,2]",
                modules.getAsJsonObject("living").getAsJsonArray("residents").toString());
        assertTrue(modules.getAsJsonObject("bed").getAsJsonArray("beds").isEmpty());
        assertTrue(migrated.getAsJsonObject("settings").get("autoHousing").getAsBoolean());
        JsonObject citizen = migrated.getAsJsonArray("citizens").get(0).getAsJsonObject();
        assertFalse(citizen.get("asleep").getAsBoolean());
        assertTrue(citizen.get("bedPos").isJsonNull());
        assertTrue(migrated.getAsJsonArray("buildings")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("modules")
                .keySet()
                .isEmpty()); // the town hall is no residence
    }

    @Test
    void v5ColonyLoadsWithResidentsUpToTheLevelAndTheRestHomeless() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());

        m.persistence().loadAll();

        Colony c = m.byId(1).orElseThrow();
        LivingModule living = c.buildings()
                .at(RESIDENCE)
                .orElseThrow()
                .module(LivingModule.class)
                .orElseThrow();
        assertEquals(List.of(1), living.residents()); // level 1: one place, MC assignCitizen refuses the second
        assertEquals(RESIDENCE, c.citizens().get(1).orElseThrow().homeBuilding());
        assertNull(c.citizens().get(2).orElseThrow().homeBuilding());
        assertFalse(c.citizens().get(1).orElseThrow().asleep());
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
        assertTrue(saved.contains("\"autoHousing\":true"), saved);
    }
}
