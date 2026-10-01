package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A save missing an optional key, or holding a value of another type, loads with the default (CLAUDE.md § 5). */
class MissingKeysLoadTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    @TempDir
    Path dir;

    /** The manager {@link #saveEditAndLoad} loaded the colony with. */
    private ColonyManager reloaded;

    private ColonyManager manager() {
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());
        return m;
    }

    /** Saves a new colony with its first citizens, lets {@code edit} change the saved JSON, then loads it again. */
    private Colony saveEditAndLoad(Consumer<JsonObject> edit) throws IOException {
        ColonyManager m = manager();
        m.foundation().begin(OWNER, "Owner", CENTER, 0);
        Colony c = m.foundation().confirm(OWNER, "A").orElseThrow();
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        m.persistence().saveAll();
        Path file = dir.resolve("colony-" + c.id() + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        edit.accept(json);
        Files.writeString(file, json.toString());
        reloaded = manager();
        reloaded.persistence().loadAll();
        return reloaded.byId(c.id()).orElseThrow(() -> new AssertionError("the colony did not load"));
    }

    @Test
    void colonyWithoutDayCitizensOrEventLogLoadsEmpty() throws IOException {
        Colony c =
                saveEditAndLoad(json -> List.of("day", "citizens", "eventLog").forEach(json::remove));

        assertEquals(0, c.day());
        assertTrue(c.citizens().all().isEmpty());
        assertTrue(c.log().entries().isEmpty());
    }

    @Test
    void valuesOfAnotherTypeFallBackToTheirDefault() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            json.addProperty("day", "seven");
            json.add("citizens", new JsonPrimitive(4));
            json.getAsJsonArray("eventLog").add(new JsonPrimitive("garbage"));
        });

        assertEquals(0, c.day());
        assertTrue(c.citizens().all().isEmpty());
    }

    @Test
    void eventLogEntryWithoutDayOrParamsLoads() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            JsonObject entry = json.getAsJsonArray("eventLog").get(0).getAsJsonObject();
            entry.remove("day");
            entry.remove("params");
        });

        assertEquals(0, c.log().entries().getFirst().day());
        assertTrue(c.log().entries().getFirst().params().isEmpty());
    }

    @Test
    void buildingWithoutItsOptionalKeysLoadsWithDefaults() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            JsonObject hall = json.getAsJsonArray("buildings").get(0).getAsJsonObject();
            List.of("rotation", "level", "built", "customName", "style", "modules", "containers")
                    .forEach(hall::remove);
        });

        Building hall = c.buildings().townHall().orElseThrow();
        assertEquals(0, hall.rotation());
        assertEquals(0, hall.level());
        assertFalse(hall.isBuilt());
        assertEquals("", hall.style());
    }

    @Test
    void citizenWithoutItsOptionalKeysLoadsWithDefaults() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            for (JsonElement e : json.getAsJsonArray("citizens")) {
                JsonObject citizen = e.getAsJsonObject();
                List.of(
                                "name",
                                "gender",
                                "child",
                                "skills",
                                "saturation",
                                "leisureTime",
                                "inventory",
                                "home",
                                "work",
                                "job")
                        .forEach(citizen::remove);
                citizen.addProperty("lastPosition", "nowhere");
            }
        });

        assertEquals(4, c.citizens().all().size());
        CitizenData d = c.citizens().all().iterator().next();
        assertEquals("", d.name());
        assertEquals(0, d.leisureTime());
        assertTrue(d.inventory().contents().isEmpty());
    }

    @Test
    void leisureLongerThanOneBreakLoadsAsOneBreak() throws IOException {
        Colony c = saveEditAndLoad(json -> firstCitizen(json).addProperty("leisureTime", 1_000_000_000));

        int longest = c.citizens().all().stream()
                .mapToInt(CitizenData::leisureTime)
                .max()
                .orElseThrow();
        assertEquals(CitizenData.LEISURE_TICKS, longest, "an edited save cannot keep a worker on a break forever");
    }

    @Test
    void workerWithAnUnreadableWorkBuildingLosesItsJob() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            JsonObject citizen = firstCitizen(json);
            JsonObject job = new JsonObject();
            job.addProperty("type", "hycolony:builder");
            job.addProperty("actionsDone", "many");
            citizen.add("job", job);
            citizen.addProperty("work", "garbage");
        });

        CitizenData d = c.citizens().all().iterator().next();
        assertTrue(d.job().isEmpty(), "a job without its work building would keep the citizen idle forever");
        assertNull(d.workBuilding());
        assertTrue(c.isDirty());
    }

    @Test
    void citizenWithAWorkBuildingButNoJobIsFreed() throws IOException {
        Colony c = saveEditAndLoad(json -> firstCitizen(json).add("work", SavedJson.pos(CENTER)));

        assertNull(c.citizens().all().iterator().next().workBuilding());
    }

    @Test
    void citizenWithoutAnIdIsLeftOutAndTheColonyRewritten() throws IOException {
        Colony c = saveEditAndLoad(json -> firstCitizen(json).remove("id"));

        assertEquals(3, c.citizens().all().size());
        assertTrue(c.isDirty());
    }

    @Test
    void knownBuildingWithAnUnreadablePositionIsKeptAndWrittenBackUnchanged() throws IOException {
        JsonObject broken = new JsonObject();
        broken.addProperty("type", "hycolony:builder");
        broken.addProperty("pos", "garbage");
        Colony c = saveEditAndLoad(json -> json.getAsJsonArray("buildings").add(broken));

        assertEquals(1, c.buildings().all().size());
        c.markDirty();
        reloaded.persistence().saveAll();
        JsonObject saved = JsonParser.parseString(Files.readString(dir.resolve("colony-" + c.id() + ".json")))
                .getAsJsonObject();
        assertTrue(saved.getAsJsonArray("buildings").contains(broken));
    }

    @Test
    void settingsAndRequestsOfAnotherTypeLoad() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            json.addProperty("settings", 3);
            json.addProperty("requests", "none");
        });

        assertTrue(c.requests().all().isEmpty());
    }

    @Test
    void workOrderOfUnknownTypeOrStageIsSkippedAndTheColonyRewritten() throws IOException {
        Colony c = saveEditAndLoad(json -> {
            JsonArray orders = new JsonArray();
            orders.add(workOrder(1, "BUILD", "CLEAR"));
            orders.add(workOrder(2, "TERRAFORM", "CLEAR"));
            orders.add(workOrder(3, "BUILD", "PAINT"));
            JsonObject noPos = workOrder(4, "BUILD", "CLEAR");
            noPos.remove("pos");
            orders.add(noPos);
            orders.add(workOrder(0, "BUILD", "CLEAR")); // ids start at 1: 0 means no order
            json.add("workOrders", orders);
        });

        assertEquals(List.of(1), c.work().ordered().stream().map(WorkOrder::id).toList());
        assertTrue(c.isDirty(), "the skipped orders are left out of the next save");
    }

    private static JsonObject firstCitizen(JsonObject json) {
        return json.getAsJsonArray("citizens").get(0).getAsJsonObject();
    }

    private static JsonObject workOrder(int id, String type, String stage) {
        JsonObject pos = new JsonObject();
        pos.addProperty("x", CENTER.x());
        pos.addProperty("y", CENTER.y());
        pos.addProperty("z", CENTER.z());
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", type);
        o.add("pos", pos);
        o.addProperty("targetLevel", 1);
        o.addProperty("style", "Kweebec");
        o.addProperty("rotation", 0);
        o.addProperty("priority", 0);
        o.addProperty("stage", stage);
        o.addProperty("progressIndex", 0);
        return o;
    }
}
