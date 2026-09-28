package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
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

/** Schema 4 (SP3b-1 crafting): huts register the benches of their plan, the colony keeps a recipe registry. */
class MigrationV3ToV4Test {
    private static final String FIXTURE = "colony-v3-crafting.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV3ToV4Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v3ColonyLoadsWithNoWorkstations() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = new ColonyManager(new TestContexts().context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());

        m.persistence().loadAll();

        Colony c = m.byId(1).orElseThrow();
        assertEquals(2, c.buildings().all().size());
        for (Building b : c.buildings().all()) {
            assertTrue(b.registeredBlocks().workstations().isEmpty(), b.type().id());
        }
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);
    }

    @Test
    void v3ToV4GivesEveryBuildingNoWorkstationsAndTheColonyNoRecipes() throws IOException {
        JsonObject doc = JsonParser.parseString(fixture()).getAsJsonObject();

        JsonObject migrated = MigrationChain.sp3b().migrate(doc);

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        for (JsonElement b : migrated.getAsJsonArray("buildings")) {
            assertTrue(b.getAsJsonObject().getAsJsonArray("workstations").isEmpty());
        }
        assertTrue(migrated.getAsJsonObject("recipes").isEmpty());
    }
}
