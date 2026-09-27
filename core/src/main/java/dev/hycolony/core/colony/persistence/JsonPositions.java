package dev.hycolony.core.colony.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Positions as JSON {@code {x, y, z}} objects; a null position is JSON null. */
final class JsonPositions {
    private static final List<String> AXES = List.of("x", "y", "z");

    private JsonPositions() {}

    static JsonElement pos(@Nullable BlockPos p) {
        if (p == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    static @Nullable BlockPos readPos(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new BlockPos(
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    /** A position from any JSON, never throwing: empty unless {@code e} is an object with numeric x, y and z. */
    static Optional<BlockPos> tryPos(@Nullable JsonElement e) {
        if (!(e instanceof JsonObject o)) {
            return Optional.empty();
        }
        for (String axis : AXES) {
            if (!(o.get(axis) instanceof JsonPrimitive p && p.isNumber())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(readPos(o));
    }

    /** A position the save cannot do without; throws on JSON null or a missing key, like any missing required field. */
    static BlockPos requirePos(@Nullable JsonElement e) {
        BlockPos p = readPos(e);
        if (p == null) {
            throw new IllegalStateException("missing position");
        }
        return p;
    }

    static JsonElement vec(@Nullable Vec3 v) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x());
        o.addProperty("y", v.y());
        o.addProperty("z", v.z());
        return o;
    }

    static @Nullable Vec3 readVec(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new Vec3(
                o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }
}
