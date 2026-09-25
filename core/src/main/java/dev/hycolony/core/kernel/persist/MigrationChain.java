package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
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

    /** SP1: schema 2. Adds citizen inventory/job, colony requests/workOrders/settings, building containers. */
    public static MigrationChain sp1() {
        return new MigrationChain(2, List.of(new Migration(1, MigrationChain::v1ToV2)));
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
