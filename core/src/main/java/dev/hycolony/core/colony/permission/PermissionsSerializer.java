package dev.hycolony.core.colony.permission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    /**
     * The saved permissions; throws only for a missing or malformed {@code owner}, the colony's identity (see
     * ColonySerializer.read). Anything else missing or malformed falls back (CLAUDE.md § 5).
     */
    public static Permissions read(JsonObject o) {
        UUID owner = UUID.fromString(o.get("owner").getAsString());
        String ownerName = SavedJson.stringOr(o.get("ownerName"), "");
        Permissions defaults = Permissions.createDefault(owner, ownerName);
        Map<Integer, Rank> ranks = new LinkedHashMap<>(defaults.ranks());
        for (JsonElement el : SavedJson.arrayOr(o.get("ranks"))) {
            readRank(SavedJson.objectOr(el), ranks);
        }
        Map<UUID, Permissions.Member> members = new LinkedHashMap<>(defaults.members());
        for (JsonElement el : SavedJson.arrayOr(o.get("members"))) {
            JsonObject m = SavedJson.objectOr(el);
            uuid(m.get("uuid"))
                    .ifPresent(id -> members.put(
                            id,
                            new Permissions.Member(
                                    SavedJson.stringOr(m.get("name"), ""),
                                    SavedJson.intOr(m.get("rank"), Permissions.NEUTRAL))));
        }
        return Permissions.restore(owner, ownerName, ranks, members);
    }

    /**
     * Tolerant (CLAUDE.md § 5): a key the saved rank lacks keeps the value of the rank it replaces (the default rank
     * of that id, or a blank one); a rank without an id is dropped.
     */
    private static void readRank(JsonObject r, Map<Integer, Rank> ranks) {
        int id = SavedJson.intOr(r.get("id"), -1);
        if (id < 0) {
            return;
        }
        Rank base = ranks.getOrDefault(id, new Rank(id, "", 0L, false));
        Rank rank = new Rank(
                id,
                SavedJson.stringOr(r.get("name"), base.name()),
                SavedJson.longOr(r.get("permissions"), base.permissions()),
                SavedJson.boolOr(r.get("initial"), base.isInitial()));
        rank.setColonyManager(SavedJson.boolOr(r.get("colonyManager"), base.isColonyManager()));
        rank.setHostile(SavedJson.boolOr(r.get("hostile"), base.isHostile()));
        ranks.put(id, rank);
    }

    /** The UUID the string {@code e} names; empty for anything else. */
    private static Optional<UUID> uuid(@Nullable JsonElement e) {
        try {
            return Optional.of(UUID.fromString(SavedJson.stringOr(e, "")));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
