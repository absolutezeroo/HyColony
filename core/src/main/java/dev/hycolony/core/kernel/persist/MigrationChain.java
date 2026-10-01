package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MigrationChain {
    public static final String VERSION_KEY = "schemaVersion";

    private final int current;
    private final Map<Integer, Migration> byFrom;

    public MigrationChain(int current, List<Migration> migrations) {
        this.current = current;
        this.byFrom = migrations.stream().collect(Collectors.toMap(Migration::from, Function.identity()));
    }

    /** SP0: schema 1, no migrations yet. Add one Migration per future schema bump. */
    public static MigrationChain sp0() {
        return new MigrationChain(1, List.of());
    }

    /**
     * SP4: schema 8. Schema 2 added citizen inventory/job, colony requests/workOrders/settings, building containers;
     * schema 3 moved a tool's wear from the job's per-item counter onto the stack; schema 4 added the huts' plan
     * benches and the colony's recipe registry; schema 5 the colony's fields; schema 6 the residences' residents and
     * beds, the citizens' sleep and the colony's auto-housing setting; schema 7 the colony's style, its move-in
     * setting and the event log's positions; schema 8 the citizens' last meals, "just ate", job status and happiness.
     */
    public static MigrationChain sp4() {
        return new MigrationChain(
                8,
                List.of(
                        new Migration(1, MigrationChain::v1ToV2),
                        new Migration(2, MigrationChain::v2ToV3),
                        new Migration(3, MigrationChain::v3ToV4),
                        new Migration(4, MigrationChain::v4ToV5),
                        new Migration(5, MigrationChain::v5ToV6),
                        new Migration(6, MigrationV6ToV7::apply),
                        new Migration(7, MigrationV7ToV8::apply)));
    }

    private static JsonObject v1ToV2(JsonObject doc) {
        for (JsonElement el : doc.getAsJsonArray("citizens")) {
            JsonObject citizen = el.getAsJsonObject();
            citizen.add("inventory", new JsonArray());
            citizen.add("job", JsonNull.INSTANCE);
        }
        doc.add("requests", new JsonObject());
        doc.add("workOrders", new JsonArray());
        JsonObject settings = new JsonObject();
        settings.addProperty("autoHiring", true);
        doc.add("settings", settings);
        for (JsonElement el : doc.getAsJsonArray("buildings")) {
            JsonObject building = el.getAsJsonObject();
            building.add("containers", new JsonArray());
            building.addProperty("deconstructed", false);
        }
        return doc;
    }

    /**
     * The builder's {@code toolUses} counter (blocks mined per tool id) becomes the {@code damage} of the last slot
     * holding that tool, the stack its break would have removed; a counter for a tool no longer held is dropped.
     */
    private static JsonObject v2ToV3(JsonObject doc) {
        for (JsonElement el : doc.getAsJsonArray("citizens")) {
            JsonObject citizen = el.getAsJsonObject();
            JsonElement job = citizen.get("job");
            if (job == null || !job.isJsonObject() || !job.getAsJsonObject().has("toolUses")) {
                continue;
            }
            JsonObject uses = job.getAsJsonObject().remove("toolUses").getAsJsonObject();
            JsonArray inventory = citizen.has("inventory") ? citizen.getAsJsonArray("inventory") : new JsonArray();
            uses.entrySet()
                    .forEach(e -> lastSlotOf(inventory, e.getKey())
                            .ifPresent(slot ->
                                    slot.addProperty("damage", e.getValue().getAsInt())));
        }
        return doc;
    }

    /** No hut has registered a bench of its plan yet, and the colony has learnt no recipe. */
    private static JsonObject v3ToV4(JsonObject doc) {
        for (JsonElement el : doc.getAsJsonArray("buildings")) {
            if (el.isJsonObject()) {
                el.getAsJsonObject().add("workstations", new JsonArray());
            }
        }
        doc.add("recipes", new JsonObject());
        return doc;
    }

    /** Schema 5 (SP3b-2): the colony keeps its fields, none in an older save. */
    private static JsonObject v4ToV5(JsonObject doc) {
        doc.add("fields", new JsonArray());
        return doc;
    }

    /**
     * Schema 6 (SP4): each residence lists the citizens whose saved home it is, in citizen order (the load assigns
     * them again, as MC), and no bed yet (the load finds them in its plan); citizens are awake; the colony houses its
     * homeless automatically (MC AUTO_HOUSING_MODE).
     */
    private static JsonObject v5ToV6(JsonObject doc) {
        for (JsonElement el : doc.getAsJsonArray("buildings")) {
            if (el instanceof JsonObject b
                    && b.get("type") instanceof JsonPrimitive type
                    && "hycolony:residence".equals(type.getAsString())) {
                residenceModules(doc, b);
            }
        }
        for (JsonElement el : doc.getAsJsonArray("citizens")) {
            if (el instanceof JsonObject citizen) {
                citizen.addProperty("asleep", false);
                citizen.add("bedPos", JsonNull.INSTANCE);
            }
        }
        JsonObject settings = doc.get("settings") instanceof JsonObject s ? s : new JsonObject();
        settings.addProperty("autoHousing", true);
        doc.add("settings", settings);
        return doc;
    }

    /** The {@code living} module (residents whose saved home is this residence) and an empty {@code bed} module. */
    private static void residenceModules(JsonObject doc, JsonObject residence) {
        JsonObject modules = residence.get("modules") instanceof JsonObject m ? m : new JsonObject();
        residence.add("modules", modules);
        JsonElement pos = residence.get("pos");
        if (!modules.has("living")) {
            JsonArray residents = new JsonArray();
            for (JsonElement el : doc.getAsJsonArray("citizens")) {
                if (el instanceof JsonObject c && pos != null && pos.equals(c.get("home")) && c.has("id")) {
                    residents.add(c.get("id"));
                }
            }
            JsonObject living = new JsonObject();
            living.add("residents", residents);
            living.addProperty("hiringMode", "DEFAULT");
            modules.add("living", living);
        }
        if (!modules.has("bed")) {
            JsonObject bed = new JsonObject();
            bed.add("beds", new JsonArray());
            modules.add("bed", bed);
        }
    }

    private static Optional<JsonObject> lastSlotOf(JsonArray inventory, String item) {
        for (int i = inventory.size() - 1; i >= 0; i--) {
            JsonElement slot = inventory.get(i);
            if (slot.isJsonObject()
                    && item.equals(slot.getAsJsonObject().get("item").getAsString())) {
                return Optional.of(slot.getAsJsonObject());
            }
        }
        return Optional.empty();
    }

    public int current() {
        return current;
    }

    public int versionOf(JsonObject doc) {
        return doc.has(VERSION_KEY) ? doc.get(VERSION_KEY).getAsInt() : 1;
    }

    public JsonObject migrate(JsonObject doc) {
        int version = versionOf(doc);
        if (version > current) {
            throw new SchemaTooNewException(version, current);
        }
        while (version < current) {
            Migration m = byFrom.get(version);
            if (m == null) {
                throw new IllegalStateException("No migration from schema " + version);
            }
            doc = m.apply().apply(doc);
            version++;
            doc.addProperty(VERSION_KEY, version);
        }
        return doc;
    }
}
