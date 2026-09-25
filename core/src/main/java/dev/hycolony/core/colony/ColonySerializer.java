package dev.hycolony.core.colony;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.Skills;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.RequestSerializer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Colony <-> JSON (schema 2). Unknown buildings/modules are kept verbatim. */
public final class ColonySerializer {
    public static final int SCHEMA_VERSION = 2;

    private ColonySerializer() {}

    public static JsonObject write(Colony c) {
        JsonObject o = new JsonObject();
        o.addProperty(MigrationChain.VERSION_KEY, SCHEMA_VERSION);
        o.addProperty("id", c.id());
        o.addProperty("name", c.name());
        o.add("center", pos(c.center()));
        o.addProperty("day", c.day());
        o.add("permissions", permissions(c.permissions()));

        o.add("requests", RequestSerializer.write(c.requests()));
        // Placeholder for a schema v2 field not yet backed by a model; a future task extends this serializer.
        o.add("workOrders", new JsonArray());
        JsonObject settings = new JsonObject();
        settings.addProperty("autoHiring", c.settings().autoHiring());
        o.add("settings", settings);

        JsonArray buildings = new JsonArray();
        for (Building b : c.buildings().all()) {
            buildings.add(building(b));
        }
        c.buildings().unknown().forEach(buildings::add);
        o.add("buildings", buildings);

        JsonArray citizens = new JsonArray();
        for (CitizenData d : c.citizens().all()) {
            citizens.add(citizen(d));
        }
        o.add("citizens", citizens);

        JsonArray log = new JsonArray();
        for (EventLog.Entry e : c.log().entries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", e.type());
            entry.addProperty("day", e.day());
            JsonArray params = new JsonArray();
            e.params().forEach(params::add);
            entry.add("params", params);
            log.add(entry);
        }
        o.add("eventLog", log);
        return o;
    }

    public static Colony read(JsonObject o, ColonyContext ctx, TerritoryIndex territory) {
        Colony c = new Colony(ctx, territory, o.get("id").getAsInt(), o.get("name").getAsString(),
                readPos(o.getAsJsonObject("center")), readPermissions(o.getAsJsonObject("permissions")));
        c.setDay(o.get("day").getAsInt());
        if (o.has("settings")) {
            JsonObject settings = o.getAsJsonObject("settings");
            if (settings.has("autoHiring")) {
                c.settings().setAutoHiring(settings.get("autoHiring").getAsBoolean());
            }
        }
        for (JsonElement el : o.getAsJsonArray("buildings")) {
            JsonObject b = el.getAsJsonObject();
            Optional<BuildingType> type = ctx.buildingTypes().byId(b.get("type").getAsString());
            if (type.isEmpty()) {
                c.buildings().keepUnknown(b);
                continue;
            }
            c.buildings().add(readBuilding(b, type.get()));
        }
        for (JsonElement el : o.getAsJsonArray("citizens")) {
            c.citizens().restore(readCitizen(el.getAsJsonObject(), ctx));
        }
        // After the buildings: they re-registered as resolver providers.
        if (o.has("requests")) {
            RequestSerializer.read(o.getAsJsonObject("requests"), c.requests());
        }
        for (JsonElement el : o.getAsJsonArray("eventLog")) {
            JsonObject e = el.getAsJsonObject();
            // Manual loop: JsonArray.asList() needs Gson 2.10+, and the server's Gson version is not guaranteed.
            java.util.List<String> params = new java.util.ArrayList<>();
            for (JsonElement p : e.getAsJsonArray("params")) {
                params.add(p.getAsString());
            }
            c.log().restore(new EventLog.Entry(e.get("type").getAsString(), e.get("day").getAsInt(), params));
        }
        c.clearDirty();
        return c;
    }

    // ---- positions ----

    private static JsonElement pos(BlockPos p) {
        if (p == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    private static BlockPos readPos(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    private static JsonElement vec(Vec3 v) {
        if (v == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x());
        o.addProperty("y", v.y());
        o.addProperty("z", v.z());
        return o;
    }

    private static Vec3 readVec(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        return new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }

    // ---- permissions ----

    private static JsonObject permissions(Permissions p) {
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

    private static Permissions readPermissions(JsonObject o) {
        UUID owner = UUID.fromString(o.get("owner").getAsString());
        String ownerName = o.get("ownerName").getAsString();
        Permissions defaults = Permissions.createDefault(owner, ownerName);
        Map<Integer, Rank> ranks = new LinkedHashMap<>(defaults.ranks());
        for (JsonElement el : o.getAsJsonArray("ranks")) {
            JsonObject r = el.getAsJsonObject();
            ranks.put(r.get("id").getAsInt(), new Rank(r.get("id").getAsInt(), r.get("name").getAsString(),
                    r.get("permissions").getAsLong(), r.get("initial").getAsBoolean(),
                    r.get("colonyManager").getAsBoolean(), r.get("hostile").getAsBoolean()));
        }
        Map<UUID, Permissions.Member> members = new LinkedHashMap<>();
        for (JsonElement el : o.getAsJsonArray("members")) {
            JsonObject m = el.getAsJsonObject();
            members.put(UUID.fromString(m.get("uuid").getAsString()),
                    new Permissions.Member(m.get("name").getAsString(), m.get("rank").getAsInt()));
        }
        return Permissions.restore(owner, ownerName, ranks, members);
    }

    // ---- buildings ----

    private static JsonObject building(Building b) {
        JsonObject o = new JsonObject();
        o.addProperty("type", b.type().id());
        o.add("pos", pos(b.position()));
        o.addProperty("rotation", b.rotation());
        o.addProperty("level", b.level());
        o.addProperty("built", b.isBuilt());
        o.addProperty("customName", b.customName());
        o.addProperty("style", b.style());
        JsonObject modules = new JsonObject();
        b.modules().forEach((key, module) -> {
            if (module instanceof PersistentModule pm) {
                JsonObject m = new JsonObject();
                pm.write(m);
                modules.add(key, m);
            }
        });
        b.unknownModules().forEach(modules::add);
        o.add("modules", modules);
        JsonArray containers = new JsonArray();
        b.registeredContainers().forEach(p -> containers.add(pos(p)));
        o.add("containers", containers);
        o.addProperty("deconstructed", false);
        return o;
    }

    private static Building readBuilding(JsonObject o, BuildingType type) {
        Building b = Building.create(type, readPos(o.get("pos")), o.get("rotation").getAsInt());
        b.setLevel(o.get("level").getAsInt());
        b.setBuilt(o.get("built").getAsBoolean());
        b.setCustomName(o.get("customName").getAsString());
        b.setStyle(o.get("style").getAsString());
        if (o.has("containers")) {
            for (JsonElement el : o.getAsJsonArray("containers")) {
                b.addContainer(readPos(el));
            }
        }
        JsonObject modules = o.getAsJsonObject("modules");
        for (String key : modules.keySet()) {
            BuildingModule module = b.modules().get(key);
            if (module instanceof PersistentModule pm) {
                pm.read(modules.getAsJsonObject(key));
            } else if (module == null) {
                b.unknownModules().put(key, modules.getAsJsonObject(key));
            }
        }
        return b;
    }

    // ---- citizens ----

    private static JsonObject citizen(CitizenData d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id());
        o.addProperty("name", d.name());
        o.addProperty("gender", d.gender().name());
        o.addProperty("child", d.isChild());
        JsonObject skills = new JsonObject();
        for (Skill s : Skill.values()) {
            JsonObject so = new JsonObject();
            so.addProperty("level", d.skills().level(s));
            so.addProperty("xp", d.skills().experience(s));
            skills.add(s.name(), so);
        }
        o.add("skills", skills);
        o.add("lastPosition", vec(d.lastPosition()));
        o.add("respawnPosition", pos(d.respawnPosition()));
        o.add("home", pos(d.homeBuilding()));
        o.add("work", pos(d.workBuilding()));
        o.addProperty("saturation", d.saturation());
        o.add("inventory", d.inventory().write());
        o.add("job", d.job().<JsonElement>map(Job::write).orElse(JsonNull.INSTANCE));
        return o;
    }

    private static CitizenData readCitizen(JsonObject o, ColonyContext ctx) {
        CitizenData d = new CitizenData(o.get("id").getAsInt());
        d.setName(o.get("name").getAsString());
        d.setGender(Gender.valueOf(o.get("gender").getAsString()));
        d.setChild(o.get("child").getAsBoolean());
        Skills skills = Skills.empty();
        JsonObject so = o.getAsJsonObject("skills");
        for (Skill s : Skill.values()) {
            if (so.has(s.name())) {
                JsonObject e = so.getAsJsonObject(s.name());
                skills.set(s, e.get("level").getAsInt(), e.get("xp").getAsDouble());
            }
        }
        d.setSkills(skills);
        d.setLastPosition(readVec(o.get("lastPosition")));
        d.setRespawnPosition(readPos(o.get("respawnPosition")));
        d.setHomeBuilding(readPos(o.get("home")));
        d.setWorkBuilding(readPos(o.get("work")));
        d.setSaturation(o.get("saturation").getAsDouble());
        d.setInventory(Inventory.read(o.getAsJsonArray("inventory"), CitizenData.INVENTORY_SLOTS));
        if (o.has("job") && !o.get("job").isJsonNull()) {
            JsonObject jobJson = o.getAsJsonObject("job");
            String typeId = jobJson.get("type").getAsString();
            ctx.jobs().byId(typeId).ifPresent(type -> {
                Job job = type.factory().apply(d);
                job.read(jobJson);
                d.setJob(job);
            });
        }
        return d;
    }
}
