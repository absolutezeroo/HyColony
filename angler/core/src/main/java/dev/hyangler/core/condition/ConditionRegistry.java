package dev.hyangler.core.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.api.condition.ConditionFactory;
import dev.hyangler.api.condition.ConditionTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * The condition types the data files name (spec § 6.4) and their parsing: All, Any and Not combine others, every other
 * type is a factory, HyAngler's own (BuiltinConditions) or another mod's (Tide FishingConditionType). Mods register
 * during their setup; the files are parsed after, once the assets have loaded.
 */
public final class ConditionRegistry implements ConditionTypes {
    private static final Set<String> COMBINERS = Set.of("All", "Any", "Not");
    private final Map<String, ConditionFactory> factories = new ConcurrentHashMap<>();

    /** A registry holding HyAngler's own types. */
    public ConditionRegistry() {
        BuiltinConditions.registerAll(this);
    }

    @Override
    public void register(String type, ConditionFactory factory) {
        if (COMBINERS.contains(type) || factories.putIfAbsent(type, factory) != null) {
            throw new IllegalArgumentException("condition type " + type + " is already registered");
        }
    }

    @Override
    public Set<String> types() {
        return Set.copyOf(factories.keySet());
    }

    /**
     * A file's Conditions value: absent is always true, an object with a Type is parsed, an object holding All or Any
     * reads its list; throws {@link IllegalArgumentException} with the reason when it is invalid.
     */
    public Condition parseAll(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ctx -> true;
        }
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Conditions is an object");
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("Type")) {
            return parse(o);
        }
        for (String combiner : List.of("All", "Any")) {
            if (o.has(combiner)) {
                JsonObject wrapped = new JsonObject();
                wrapped.addProperty("Type", combiner);
                wrapped.add("Conditions", o.get(combiner));
                return parse(wrapped);
            }
        }
        throw new IllegalArgumentException("Conditions needs a Type, All or Any");
    }

    /** One condition object; throws {@link IllegalArgumentException} with the reason when it is invalid. */
    public Condition parse(JsonObject json) {
        JsonElement typeField = json.get("Type");
        if (typeField == null || !typeField.isJsonPrimitive()) {
            throw new IllegalArgumentException("a condition needs a Type");
        }
        String type = typeField.getAsString();
        return switch (type) {
            case "All" -> all(list(json, type));
            case "Any" -> any(list(json, type));
            case "Not" -> not(json);
            default -> {
                ConditionFactory factory = factories.get(type);
                if (factory == null) {
                    throw new IllegalArgumentException("unknown condition type " + type);
                }
                yield factory.create(new JsonConditionSpec(type, json));
            }
        };
    }

    private Condition not(JsonObject json) {
        JsonElement inner = json.get("Condition");
        if (inner == null || !inner.isJsonObject()) {
            throw new IllegalArgumentException("Not needs a Condition");
        }
        Condition c = parse(inner.getAsJsonObject());
        return ctx -> !c.test(ctx);
    }

    private static Condition all(List<Condition> all) {
        return ctx -> {
            for (Condition c : all) {
                if (!c.test(ctx)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static Condition any(List<Condition> any) {
        return ctx -> {
            for (Condition c : any) {
                if (c.test(ctx)) {
                    return true;
                }
            }
            return false;
        };
    }

    private List<Condition> list(JsonObject json, String type) {
        JsonElement conditions = json.get("Conditions");
        if (conditions == null
                || !conditions.isJsonArray()
                || conditions.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException(type + " needs Conditions");
        }
        List<Condition> out = new ArrayList<>();
        for (JsonElement e : conditions.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("a condition is an object");
            }
            out.add(parse(e.getAsJsonObject()));
        }
        return List.copyOf(out);
    }
}
