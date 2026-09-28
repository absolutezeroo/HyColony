package dev.hycolony.core.kernel.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One data file ({@code id-map.json}, {@code styles.json}, {@code crafting.json}) merged from the core copy and each
 * enabled sub-plugin's fragment, in the order they are added. Objects merge key by key down to {@code depth} levels
 * (id-map: 1, its sections; styles: 2, style then building type; crafting: 2, job then key); below that, a key already
 * defined is a conflict that the first source keeps. Lists at a merged level are joined, each value kept once.
 */
public final class JsonFragments {
    /** {@code path} (keys joined by {@code /}) was defined by {@code first}, which keeps it, then by {@code second}. */
    public record Conflict(String path, String first, String second) {}

    private final JsonObject merged = new JsonObject();
    private final Map<List<String>, String> owners = new HashMap<>();
    private final int depth;

    public JsonFragments(int depth) {
        this.depth = depth;
    }

    /** Merges {@code fragment} from {@code source}; returns the keys it could not take (empty when none). */
    public List<Conflict> add(String source, JsonObject fragment) {
        List<Conflict> conflicts = new ArrayList<>();
        merge(merged, fragment, List.of(), source, conflicts);
        return conflicts;
    }

    /** The merged file so far; a live view, not a copy. */
    public JsonObject merged() {
        return merged;
    }

    private void merge(JsonObject into, JsonObject from, List<String> at, String source, List<Conflict> conflicts) {
        boolean mergeable = at.size() < depth;
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            List<String> path = append(at, e.getKey());
            JsonElement mine = into.get(e.getKey());
            JsonElement theirs = e.getValue();
            if (mine == null) {
                into.add(e.getKey(), theirs.deepCopy());
                owners.put(path, source);
            } else if (mergeable && mine.isJsonObject() && theirs.isJsonObject()) {
                merge(mine.getAsJsonObject(), theirs.getAsJsonObject(), path, source, conflicts);
            } else if (mergeable && mine.isJsonArray() && theirs.isJsonArray()) {
                join(mine.getAsJsonArray(), theirs.getAsJsonArray());
            } else {
                conflicts.add(new Conflict(String.join("/", path), owner(path), source));
            }
        }
    }

    private static void join(JsonArray into, JsonArray from) {
        from.forEach(v -> {
            if (!into.contains(v)) {
                into.add(v.deepCopy());
            }
        });
    }

    /** The source that added {@code path}, or the section holding it. */
    private String owner(List<String> path) {
        for (int n = path.size(); n > 0; n--) {
            String owner = owners.get(path.subList(0, n));
            if (owner != null) {
                return owner;
            }
        }
        return "?";
    }

    private static List<String> append(List<String> path, String key) {
        List<String> out = new ArrayList<>(path);
        out.add(key);
        return List.copyOf(out);
    }
}
