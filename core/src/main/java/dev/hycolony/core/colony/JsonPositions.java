package dev.hycolony.core.colony;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;

/** Positions as JSON {@code {x, y, z}} objects; a null position is JSON null. */
final class JsonPositions {
    private JsonPositions() {}

    static JsonElement pos(BlockPos p) {
        if (p == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    static BlockPos readPos(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new BlockPos(
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    static JsonElement vec(Vec3 v) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x());
        o.addProperty("y", v.y());
        o.addProperty("z", v.z());
        return o;
    }

    static Vec3 readVec(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new Vec3(
                o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }
}
