package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
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
     * SP3b: schema 5. Schema 2 added citizen inventory/job, colony requests/workOrders/settings, building containers;
     * schema 3 moved a tool's wear from the job's per-item counter onto the stack; schema 4 added the huts' plan
     * benches and the colony's recipe registry; schema 5 the colony's fields.
     */
    public static MigrationChain sp3b() {
        return new MigrationChain(
                5,
                List.of(
                        new Migration(1, MigrationChain::v1ToV2),
                        new Migration(2, MigrationChain::v2ToV3),
                        new Migration(3, MigrationChain::v3ToV4),
                        new Migration(4, MigrationChain::v4ToV5)));
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
