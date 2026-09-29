package dev.hycolony.core.app.persistence;

import static dev.hycolony.core.kernel.persist.SavedJson.arrayOr;
import static dev.hycolony.core.kernel.persist.SavedJson.boolOr;
import static dev.hycolony.core.kernel.persist.SavedJson.intOr;
import static dev.hycolony.core.kernel.persist.SavedJson.objectOr;
import static dev.hycolony.core.kernel.persist.SavedJson.pos;
import static dev.hycolony.core.kernel.persist.SavedJson.requirePos;
import static dev.hycolony.core.kernel.persist.SavedJson.stringOr;
import static dev.hycolony.core.kernel.persist.SavedJson.tryPos;

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
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.RequestSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Colony <-> JSON (schema {@value #SCHEMA_VERSION}). Unknown buildings/modules are kept verbatim. */
public final class ColonySerializer {
    public static final int SCHEMA_VERSION = 5;

    private static final System.Logger LOG = System.getLogger(ColonySerializer.class.getName());

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
        o.add("recipes", c.registries().recipes().write());
        o.add("fields", c.registries().fields().write());
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

    /**
     * The saved colony; a missing optional key takes its default (§ 5). Throws when its identity (id, centre,
     * permissions) is missing: such a file is left untouched by {@code ColonyPersistence}.
     */
    public static Colony read(JsonObject o, ColonyContext ctx, TerritoryIndex territory) {
        Colony c = new Colony(
                ctx,
                territory,
                new Colony.Founding(
                        o.get("id").getAsInt(),
                        stringOr(o.get("name"), ""),
                        requirePos(o.get("center")),
                        PermissionsSerializer.read(o.getAsJsonObject("permissions"))));
        c.setDay(intOr(o.get("day"), 0));
        readSettings(o, c);
        // Before the buildings: their crafting modules name recipes by their id in the registry.
        if (o.get("recipes") instanceof JsonObject recipes) {
            c.registries()
                    .recipes()
                    .read(recipes, ctx.ports().crafting().catalog(), w -> LOG.log(System.Logger.Level.WARNING, w));
        }
        readBuildings(arrayOr(o.get("buildings")), c, ctx);
        if (o.get("fields") instanceof JsonArray fields) {
            c.registries().fields().load(fields);
        }
        boolean repaired = readCitizens(arrayOr(o.get("citizens")), c, ctx);
        // After the buildings: they re-registered as resolver providers.
        repaired |= o.get("requests") instanceof JsonObject requests && RequestSerializer.read(requests, c.requests());
        repaired |= readWorkOrders(o, c);
        readEventLog(arrayOr(o.get("eventLog")), c.log());
        boolean healed = heal(c) || repaired;
        c.clearDirty();
        if (healed) {
            c.markDirty(); // write the healed state at the next save instead of healing on every load
        }
        return c;
    }

    private static void readSettings(JsonObject o, Colony c) {
        JsonObject settings = objectOr(o.get("settings"));
        c.settings()
                .setAutoHiring(boolOr(settings.get("autoHiring"), c.settings().autoHiring()));
    }

    /** The saved citizens; true when one without an id was left out, so that the next save drops it. */
    private static boolean readCitizens(JsonArray citizens, Colony c, ColonyContext ctx) {
        boolean skipped = false;
        for (JsonElement el : citizens) {
            Optional<CitizenData> citizen =
                    el instanceof JsonObject saved ? CitizenSerializer.read(saved, ctx) : Optional.empty();
            citizen.ifPresent(c.citizens()::restore);
            if (citizen.isEmpty()) {
                LOG.log(System.Logger.Level.WARNING, "Saved citizen skipped, no id: {0}", el);
                skipped = true;
            }
        }
        return skipped;
    }

    /** The saved buildings; one of a type this build does not know, or without a position, is kept verbatim. */
    private static void readBuildings(JsonArray buildings, Colony c, ColonyContext ctx) {
        for (JsonElement el : buildings) {
            if (!(el instanceof JsonObject b)) {
                continue;
            }
            Optional<BuildingType> type = ctx.buildingTypes().byId(stringOr(b.get("type"), ""));
            if (type.isEmpty() || tryPos(b.get("pos")).isEmpty()) {
                c.buildings().keepUnknown(b);
                continue;
            }
            c.buildings().add(BuildingSerializer.read(b, type.get()));
        }
    }

    /** The saved work orders; true when an unreadable one was left out, so that the next save drops it. */
    private static boolean readWorkOrders(JsonObject o, Colony c) {
        boolean skipped = WorkOrderSerializer.read(arrayOr(o.get("workOrders")), c.work());
        if (o.has("workOrderTopId")) {
            c.work().restoreTopId(intOr(o.get("workOrderTopId"), 0));
        }
        return skipped;
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

    /** The saved event log; an entry that is not an object is skipped, a missing field takes its default. */
    private static void readEventLog(JsonArray entries, EventLog log) {
        for (JsonElement el : entries) {
            if (!(el instanceof JsonObject e)) {
                continue;
            }
            // Manual loop: JsonArray.asList() needs Gson 2.10+, and the server's Gson version is not guaranteed.
            List<String> params = new ArrayList<>();
            for (JsonElement p : arrayOr(e.get("params"))) {
                params.add(stringOr(p, ""));
            }
            log.restore(new EventLog.Entry(stringOr(e.get("type"), ""), intOr(e.get("day"), 0), params));
        }
    }

    /**
     * A save can reference what is gone (a building removed, or of a type no longer registered): its citizens are
     * freed (job dropped, rehireable) and its requests cancelled, so nothing waits forever. A hut's worker list
     * keeps only citizens that exist and work there, so a hut never looks employed by nobody. Last, the crafting
     * state is repaired ({@link CraftingHeal}), once the orphan requests are gone.
     */
    private static boolean heal(Colony c) {
        boolean changed = false;
        for (CitizenData d : c.citizens().all()) {
            if (isStale(c, d)) {
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
        changed |= c.requests().cancelOrphans();
        changed |= c.registries()
                .fields()
                .freeOwnersNotIn(c.buildings().all().stream()
                        .filter(b -> b.module(FarmerFieldsModule.class).isPresent())
                        .map(Building::position)
                        .collect(Collectors.toSet()));
        return CraftingHeal.heal(c) || changed;
    }

    /**
     * Whether the citizen's assignment is broken, which would keep it idle forever: a job and a work building always
     * come together ({@code WorkerModule.hire}), so one without the other frees it (a malformed save, § 5), as does a
     * work building that is gone. An unknown job stays only with its hut kept unknown too, so both come back with
     * their pack.
     */
    private static boolean isStale(Colony c, CitizenData d) {
        BlockPos work = d.workBuilding();
        if (d.unknownJob().isPresent()) {
            return work == null || !keptUnknownAt(c, work);
        }
        if (d.job().isPresent() != (work != null)) {
            return true;
        }
        return work != null && c.buildings().at(work).isEmpty();
    }

    /** Whether a kept unknown building sits at {@code pos}; a malformed saved position never matches (§ 5). */
    private static boolean keptUnknownAt(Colony c, BlockPos pos) {
        return c.buildings().unknown().stream()
                .anyMatch(raw -> tryPos(raw.get("pos")).filter(pos::equals).isPresent());
    }
}
