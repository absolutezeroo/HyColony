package dev.hycolony.core.colony.permission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A colony's {@link Permissions} (owner, ranks, members) to and from JSON. */
public final class PermissionsSerializer {
    private PermissionsSerializer() {}

    public static JsonObject write(Permissions p) {
        JsonObject o = new JsonObject();
        o.addProperty("owner", p.owner().toString());
        o.addProperty("ownerName", p.ownerName());
        JsonArray ranks = new JsonArray();
        for (Rank r : p.ranks().values()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id());
            ro.addProperty("name", r.name());
            ro.addProperty("permissions", r.permissions());
            ro.addProperty("initial", r.isInitial());
            ro.addProperty("colonyManager", r.isColonyManager());
            ro.addProperty("hostile", r.isHostile());
            ranks.add(ro);
        }
        o.add("ranks", ranks);
        JsonArray members = new JsonArray();
        p.members().forEach((uuid, m) -> {
            JsonObject mo = new JsonObject();
            mo.addProperty("uuid", uuid.toString());
            mo.addProperty("name", m.name());
            mo.addProperty("rank", m.rankId());
            members.add(mo);
        });
        o.add("members", members);
        return o;
    }

    public static Permissions read(JsonObject o) {
        UUID owner = UUID.fromString(o.get("owner").getAsString());
        String ownerName = o.get("ownerName").getAsString();
        Permissions defaults = Permissions.createDefault(owner, ownerName);
        Map<Integer, Rank> ranks = new LinkedHashMap<>(defaults.ranks());
        for (JsonElement el : o.getAsJsonArray("ranks")) {
            JsonObject r = el.getAsJsonObject();
            Rank rank = new Rank(
                    r.get("id").getAsInt(),
                    r.get("name").getAsString(),
                    r.get("permissions").getAsLong(),
                    r.get("initial").getAsBoolean());
            rank.setColonyManager(r.get("colonyManager").getAsBoolean());
            rank.setHostile(r.get("hostile").getAsBoolean());
            ranks.put(r.get("id").getAsInt(), rank);
        }
        Map<UUID, Permissions.Member> members = new LinkedHashMap<>();
        for (JsonElement el : o.getAsJsonArray("members")) {
            JsonObject m = el.getAsJsonObject();
            members.put(
                    UUID.fromString(m.get("uuid").getAsString()),
                    new Permissions.Member(
                            m.get("name").getAsString(), m.get("rank").getAsInt()));
        }
        return Permissions.restore(owner, ownerName, ranks, members);
    }
}
