package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Tolerant reading of saved JSON values (CLAUDE.md § 5): a missing value, or one of another type, gives the default
 * instead of throwing. Positions are {@code {x, y, z}} objects, a null position JSON null.
 */
public final class SavedJson {
    private static final List<String> AXES = List.of("x", "y", "z");

    private SavedJson() {}

    /** The integer {@code e}, else {@code fallback}. */
    public static int intOr(@Nullable JsonElement e, int fallback) {
        return (e instanceof JsonPrimitive p && p.isNumber()) ? p.getAsInt() : fallback;
    }

    /** The long {@code e}, else {@code fallback}. */
    public static long longOr(@Nullable JsonElement e, long fallback) {
        return (e instanceof JsonPrimitive p && p.isNumber()) ? p.getAsLong() : fallback;
    }

    /** The integers of the array {@code e}, in order; other entries, or anything but an array, give none. */
    public static List<Integer> ints(@Nullable JsonElement e) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement v : arrayOr(e)) {
            if (v instanceof JsonPrimitive p && p.isNumber()) {
                out.add(p.getAsInt());
            }
        }
        return out;
    }

    /** The number {@code e}, else {@code fallback}. */
    public static double doubleOr(@Nullable JsonElement e, double fallback) {
        return (e instanceof JsonPrimitive p && p.isNumber()) ? p.getAsDouble() : fallback;
    }

    /** The boolean {@code e}, else {@code fallback}. */
    public static boolean boolOr(@Nullable JsonElement e, boolean fallback) {
        return (e instanceof JsonPrimitive p && p.isBoolean()) ? p.getAsBoolean() : fallback;
    }

    /** The string {@code e}, else {@code fallback}. */
    public static String stringOr(@Nullable JsonElement e, String fallback) {
        return (e instanceof JsonPrimitive p && p.isString()) ? p.getAsString() : fallback;
    }

    /** The array {@code e}, else an empty one. */
    public static JsonArray arrayOr(@Nullable JsonElement e) {
        return e instanceof JsonArray a ? a : new JsonArray();
    }

    /** The object {@code e}, else an empty one. */
    public static JsonObject objectOr(@Nullable JsonElement e) {
        return e instanceof JsonObject o ? o : new JsonObject();
    }

    /** The constant of {@code type} named by the string {@code e}; empty for a name this build does not know. */
    public static <E extends Enum<E>> Optional<E> enumOf(Class<E> type, @Nullable JsonElement e) {
        String name = stringOr(e, "");
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** {@code p} as an {@code {x, y, z}} object; JSON null for a null position. */
    public static JsonElement pos(@Nullable BlockPos p) {
        if (p == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    /** A position from any JSON: empty unless {@code e} is an object with numeric x, y and z. */
    public static Optional<BlockPos> tryPos(@Nullable JsonElement e) {
        if (!(e instanceof JsonObject o) || !hasAxes(o)) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
    }

    /** An optional saved position: null when absent, JSON null or malformed. */
    public static @Nullable BlockPos readPos(@Nullable JsonElement e) {
        return tryPos(e).orElse(null);
    }

    /** A position the save cannot do without (an identity); throws when absent or malformed. */
    public static BlockPos requirePos(@Nullable JsonElement e) {
        return tryPos(e).orElseThrow(() -> new IllegalStateException("missing position"));
    }

    /** {@code v} as an {@code {x, y, z}} object; JSON null for a null vector. */
    public static JsonElement vec(@Nullable Vec3 v) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x());
        o.addProperty("y", v.y());
        o.addProperty("z", v.z());
        return o;
    }

    /** An optional saved vector: null when absent, JSON null or malformed. */
    public static @Nullable Vec3 readVec(@Nullable JsonElement e) {
        if (!(e instanceof JsonObject o) || !hasAxes(o)) {
            return null;
        }
        return new Vec3(
                o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }

    private static boolean hasAxes(JsonObject o) {
        for (String axis : AXES) {
            if (!(o.get(axis) instanceof JsonPrimitive p && p.isNumber())) {
                return false;
            }
        }
        return true;
    }
}
