package dev.hycolony.core.kernel.persist;

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
