package dev.hycolony.core.crafting.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.crafting.recipe.CraftingRules.Allow;
import dev.hycolony.core.crafting.recipe.CraftingRules.CustomRecipe;
import dev.hycolony.core.crafting.recipe.CraftingRules.JobRules;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code crafting.json} into {@link CraftingRules}, tolerantly: a missing key reads as empty, an entry of the
 * wrong shape is skipped after one warning, and the rest of the file still loads.
 */
final class CraftingRulesJson {
    /** MC CustomRecipe.minBldgLevel default. */
    private static final int DEFAULT_MIN_LEVEL = 0;
    /** MC CustomRecipe.maxBldgLevel default. */
    private static final int DEFAULT_MAX_LEVEL = 5;

    private final Consumer<String> warn;

    CraftingRulesJson(Consumer<String> warn) {
        this.warn = warn;
    }

    /** The rules of the whole file; a missing {@code jobs} or {@code reduceable} section reads as empty. */
    CraftingRules read(JsonObject json) {
        Map<String, JobRules> jobs = new LinkedHashMap<>();
        JsonObject jobsJson = object(json, "jobs", "crafting.json");
        for (String jobId : jobsJson.keySet()) {
            job(jobsJson.get(jobId), "jobs." + jobId).ifPresent(j -> jobs.put(jobId, j));
        }
        JsonObject reduce = object(json, "reduceable", "crafting.json");
        return new CraftingRules(
                jobs, items(reduce, "ingredients", "reduceable"), items(reduce, "excludedProducts", "reduceable"));
    }

    /** One job's entry; empty (with a warning) unless it is an object. */
    private Optional<JobRules> job(JsonElement e, String where) {
        if (!(e instanceof JsonObject o)) {
            return skip(where, e);
        }
        List<Allow> allow = new ArrayList<>();
        for (JsonElement a : array(o, "allow", where)) {
            allow(a, where + ".allow").ifPresent(allow::add);
        }
        List<CustomRecipe> custom = new ArrayList<>();
        for (JsonElement c : array(o, "custom", where)) {
            custom(c, where + ".custom").ifPresent(custom::add);
        }
        return Optional.of(
                new JobRules(allow, items(o, "includeItems", where), items(o, "excludeItems", where), custom));
    }

    /** A bench is required; missing categories read as none. */
    private Optional<Allow> allow(JsonElement e, String where) {
        if (!(e instanceof JsonObject o) || !isString(o.get("bench"))) {
            return skip(where, e);
        }
        Optional<List<String>> categories = strings(o.get("categories"));
        if (categories.isEmpty()) {
            return skip(where, e);
        }
        return Optional.of(new Allow(o.get("bench").getAsString(), Set.copyOf(categories.get())));
    }

    /** An id and a Hytale recipe are required; the building levels default as in MC. */
    private Optional<CustomRecipe> custom(JsonElement e, String where) {
        if (!(e instanceof JsonObject o) || !isString(o.get("id")) || !isString(o.get("hytaleRecipe"))) {
            return skip(where, e);
        }
        OptionalInt min = intOr(o.get("minBuildingLevel"), DEFAULT_MIN_LEVEL);
        OptionalInt max = intOr(o.get("maxBuildingLevel"), DEFAULT_MAX_LEVEL);
        if (min.isEmpty() || max.isEmpty()) {
            return skip(where, e);
        }
        return Optional.of(new CustomRecipe(
                o.get("id").getAsString(), o.get("hytaleRecipe").getAsString(), min.getAsInt(), max.getAsInt()));
    }

    /** The item ids listed under {@code key}; each entry that is not a string is skipped with a warning. */
    private Set<ItemKey> items(JsonObject parent, String key, String where) {
        Set<ItemKey> out = new LinkedHashSet<>();
        for (JsonElement e : array(parent, key, where)) {
            if (isString(e)) {
                out.add(new ItemKey(e.getAsString()));
            } else {
                warnSkipped(where + "." + key, e);
            }
        }
        return out;
    }

    /** The object under {@code key}; an empty one when absent, or (with a warning) of another type. */
    private JsonObject object(JsonObject parent, String key, String where) {
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return new JsonObject();
        }
        if (e instanceof JsonObject o) {
            return o;
        }
        warnSkipped(where + "." + key, e);
        return new JsonObject();
    }

    /** The array under {@code key}; an empty one when absent, or (with a warning) of another type. */
    private JsonArray array(JsonObject parent, String key, String where) {
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return new JsonArray();
        }
        if (e instanceof JsonArray a) {
            return a;
        }
        warnSkipped(where + "." + key, e);
        return new JsonArray();
    }

    /** A list of strings; absent reads as empty, anything else (or any non-string entry) as invalid. */
    private static Optional<List<String>> strings(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return Optional.of(List.of());
        }
        if (!(e instanceof JsonArray a)) {
            return Optional.empty();
        }
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement s : a) {
            if (!isString(s)) {
                return Optional.empty();
            }
            out.add(s.getAsString());
        }
        return Optional.of(out);
    }

    /** An integer, {@code fallback} when absent, empty when of another type. */
    private static OptionalInt intOr(@Nullable JsonElement e, int fallback) {
        if (e == null || e.isJsonNull()) {
            return OptionalInt.of(fallback);
        }
        return e instanceof JsonPrimitive p && p.isNumber() ? OptionalInt.of(p.getAsInt()) : OptionalInt.empty();
    }

    private static boolean isString(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString();
    }

    /** Warns that the entry {@code e} at {@code where} is skipped; always empty. */
    private <T> Optional<T> skip(String where, @Nullable JsonElement e) {
        warnSkipped(where, e);
        return Optional.empty();
    }

    private void warnSkipped(String where, @Nullable JsonElement e) {
        warn.accept("crafting.json: invalid entry at " + where + " skipped: " + e);
    }
}
