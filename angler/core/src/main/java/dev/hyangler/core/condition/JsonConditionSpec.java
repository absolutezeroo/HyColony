package dev.hyangler.core.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hyangler.api.condition.ConditionSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** A condition object of a data file, read with Gson; a mistyped field reads as absent. */
record JsonConditionSpec(String type, JsonObject json) implements ConditionSpec {

    @Override
    public Optional<String> string(String key) {
        return primitive(key).filter(JsonPrimitive::isString).map(JsonPrimitive::getAsString);
    }

    @Override
    public List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : array(key)) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                out.add(e.getAsString());
            }
        }
        return List.copyOf(out);
    }

    @Override
    public OptionalInt integer(String key) {
        Optional<JsonPrimitive> p = primitive(key).filter(JsonPrimitive::isNumber);
        return p.isPresent() ? OptionalInt.of(p.get().getAsInt()) : OptionalInt.empty();
    }

    @Override
    public OptionalDouble number(String key) {
        Optional<JsonPrimitive> p = primitive(key).filter(JsonPrimitive::isNumber);
        return p.isPresent() ? OptionalDouble.of(p.get().getAsDouble()) : OptionalDouble.empty();
    }

    @Override
    public Optional<Boolean> bool(String key) {
        return primitive(key).filter(JsonPrimitive::isBoolean).map(JsonPrimitive::getAsBoolean);
    }

    @Override
    public List<Integer> integers(String key) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : array(key)) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                out.add(e.getAsInt());
            }
        }
        return List.copyOf(out);
    }

    private Optional<JsonPrimitive> primitive(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? Optional.of(e.getAsJsonPrimitive()) : Optional.empty();
    }

    private JsonArray array(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
}
