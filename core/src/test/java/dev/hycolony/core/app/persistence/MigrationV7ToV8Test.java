package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.ExpirationModifier;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.happiness.HappinessIds;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.kernel.item.ItemKey;
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

/** Schema 8 (hunger and happiness): the citizens' meals, "just ate", job status and happiness. */
class MigrationV7ToV8Test {
    private static final String FIXTURE = "colony-v7-food.json";

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV7ToV8Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
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
    void v7ToV8GivesEveryCitizenMcDefaults() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(8, migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        JsonObject citizen = migrated.getAsJsonArray("citizens").get(0).getAsJsonObject();
        assertFalse(citizen.get("justAte").getAsBoolean());
        assertEquals(0, citizen.getAsJsonArray("foodHistory").size());
        assertEquals("IDLE", citizen.get("jobStatus").getAsString());
        assertEquals(0, citizen.getAsJsonArray("happiness").size());
    }

    @Test
    void v7ColonyLoadsWithItsSaturationAndFreshHappiness() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        Colony c = load().byId(1).orElseThrow();
        CitizenData d = c.citizens().get(1).orElseThrow();
        assertEquals(12.5, d.saturation());
        assertFalse(d.hunger().justAte());
        assertEquals(JobStatus.IDLE, d.jobStatus());
        assertEquals(10, d.happiness().modifiers().size());
    }

    @Test
    void mealsStatusAndModifiersSurviveASaveAndALoad() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = load();
        CitizenData d = m.byId(1).orElseThrow().citizens().get(1).orElseThrow();
        d.hunger().setJustAte(true);
        d.hunger().history().add(new ItemKey("apple"));
        d.hunger().history().add(new ItemKey("pie"));
        d.setJobStatus(JobStatus.STUCK);
        HappinessEvents.hurt(d);
        d.happiness()
                .get(HappinessIds.HOMELESSNESS)
                .orElseThrow()
                .dayEnd(m.byId(1).orElseThrow(), d);
        m.persistence().saveAll();
        String saved = Files.readString(dir.resolve("colony-1.json"));
        assertTrue(saved.contains("\"schemaVersion\":" + ColonySerializer.SCHEMA_VERSION), saved);

        CitizenData back = load().byId(1).orElseThrow().citizens().get(1).orElseThrow();
        assertTrue(back.hunger().justAte());
        assertEquals(
                List.of(new ItemKey("apple"), new ItemKey("pie")),
                back.hunger().history().foods());
        assertEquals(JobStatus.STUCK, back.jobStatus());
        assertEquals(
                1, back.happiness().get(HappinessIds.HOMELESSNESS).orElseThrow().days());
        ExpirationModifier damage =
                (ExpirationModifier) back.happiness().get(HappinessIds.DAMAGE).orElseThrow();
        assertEquals(1, damage.days());
        assertEquals(0, damage.value());
        assertEquals(11, back.happiness().modifiers().size());
    }
}
