package dev.hycolony.core.colony.persistence;

import static dev.hycolony.core.colony.persistence.JsonPositions.pos;
import static dev.hycolony.core.colony.persistence.JsonPositions.requirePos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.colony.permission.PermissionsSerializer;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.workorder.WorkOrderSerializer;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.RequestSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Colony <-> JSON (schema 3). Unknown buildings/modules are kept verbatim. */
public final class ColonySerializer {
    public static final int SCHEMA_VERSION = 3;

    private ColonySerializer() {}

    public static JsonObject write(Colony c) {
        JsonObject o = new JsonObject();
        o.addProperty(MigrationChain.VERSION_KEY, SCHEMA_VERSION);
        o.addProperty("id", c.id());
        o.addProperty("name", c.name());
        o.add("center", pos(c.center()));
        o.addProperty("day", c.day());
        o.add("permissions", PermissionsSerializer.write(c.permissions()));

        o.add("requests", RequestSerializer.write(c.requests()));
        o.add("workOrders", WorkOrderSerializer.write(c.work()));
        o.addProperty("workOrderTopId", c.work().topId());
        JsonObject settings = new JsonObject();
        settings.addProperty("autoHiring", c.settings().autoHiring());
        o.add("settings", settings);

        JsonArray buildings = new JsonArray();
        for (Building b : c.buildings().all()) {
            buildings.add(BuildingSerializer.write(b));
        }
        c.buildings().unknown().forEach(buildings::add);
        o.add("buildings", buildings);

        JsonArray citizens = new JsonArray();
        for (CitizenData d : c.citizens().all()) {
            citizens.add(CitizenSerializer.write(d));
        }
        o.add("citizens", citizens);
        o.add("eventLog", eventLog(c.log()));
        return o;
    }

    public static Colony read(JsonObject o, ColonyContext ctx, TerritoryIndex territory) {
        Colony c = new Colony(
                ctx,
                territory,
                new Colony.Founding(
                        o.get("id").getAsInt(),
                        o.get("name").getAsString(),
                        requirePos(o.getAsJsonObject("center")),
                        PermissionsSerializer.read(o.getAsJsonObject("permissions"))));
        c.setDay(o.get("day").getAsInt());
        readSettings(o, c);
        readBuildings(o.getAsJsonArray("buildings"), c, ctx);
        for (JsonElement el : o.getAsJsonArray("citizens")) {
            c.citizens().restore(CitizenSerializer.read(el.getAsJsonObject(), ctx));
        }
        // After the buildings: they re-registered as resolver providers.
        if (o.has("requests")) {
            RequestSerializer.read(o.getAsJsonObject("requests"), c.requests());
        }
        readWorkOrders(o, c);
        readEventLog(o.getAsJsonArray("eventLog"), c.log());
        boolean healed = heal(c);
        c.clearDirty();
        if (healed) {
            c.markDirty(); // write the healed state at the next save instead of healing on every load
        }
        return c;
    }

    private static void readSettings(JsonObject o, Colony c) {
        if (o.has("settings")) {
            JsonObject settings = o.getAsJsonObject("settings");
            if (settings.has("autoHiring")) {
                c.settings().setAutoHiring(settings.get("autoHiring").getAsBoolean());
            }
        }
    }

    private static void readBuildings(JsonArray buildings, Colony c, ColonyContext ctx) {
        for (JsonElement el : buildings) {
            JsonObject b = el.getAsJsonObject();
            Optional<BuildingType> type = ctx.buildingTypes().byId(b.get("type").getAsString());
            if (type.isEmpty()) {
                c.buildings().keepUnknown(b);
                continue;
            }
            c.buildings().add(BuildingSerializer.read(b, type.get()));
        }
    }

    private static void readWorkOrders(JsonObject o, Colony c) {
        if (o.has("workOrders")) {
            WorkOrderSerializer.read(o.getAsJsonArray("workOrders"), c.work());
        }
        if (o.has("workOrderTopId")) {
            c.work().restoreTopId(o.get("workOrderTopId").getAsInt());
        }
    }

    private static JsonArray eventLog(EventLog eventLog) {
        JsonArray log = new JsonArray();
        for (EventLog.Entry e : eventLog.entries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", e.type());
            entry.addProperty("day", e.day());
            JsonArray params = new JsonArray();
            e.params().forEach(params::add);
            entry.add("params", params);
            log.add(entry);
        }
        return log;
    }

    private static void readEventLog(JsonArray entries, EventLog log) {
        for (JsonElement el : entries) {
            JsonObject e = el.getAsJsonObject();
            // Manual loop: JsonArray.asList() needs Gson 2.10+, and the server's Gson version is not guaranteed.
            List<String> params = new ArrayList<>();
            for (JsonElement p : e.getAsJsonArray("params")) {
                params.add(p.getAsString());
            }
            log.restore(
                    new EventLog.Entry(e.get("type").getAsString(), e.get("day").getAsInt(), params));
        }
    }

    /**
     * A save can reference what is gone (a building removed, or of a type no longer registered): its citizens are
     * freed (job dropped, rehireable) and its requests cancelled, so nothing waits forever. A hut's worker list
     * keeps only citizens that exist and work there, so a hut never looks employed by nobody. A citizen whose job is
     * unknown keeps its assignment: its hut is likely kept unknown too, and both come back with their pack.
     */
    private static boolean heal(Colony c) {
        boolean changed = false;
        for (CitizenData d : c.citizens().all()) {
            if (d.workBuilding() != null
                    && d.unknownJob().isEmpty()
                    && c.buildings().at(d.workBuilding()).isEmpty()) {
                d.job().ifPresent(job -> job.onRemoval(c));
                d.setJob(null);
                d.setWorkBuilding(null);
                changed = true;
            }
        }
        for (Building b : c.buildings().all()) {
            Optional<WorkerModule> workers = b.module(WorkerModule.class);
            if (workers.isPresent()) {
                changed |= workers.get()
                        .retainWorkers(id -> c.citizens()
                                .get(id)
                                .map(d -> b.position().equals(d.workBuilding()))
                                .orElse(false));
            }
        }
        return c.requests().cancelOrphans() | changed;
    }
}
