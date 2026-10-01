package dev.hycolony.core.logistics.warehouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.building.module.TickingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Links couriers, already hired by their own hut, to this warehouse (MC {@code CourierAssignmentModule}). It gives no
 * job: it only records which citizens may use the warehouse ({@code canAccessWareHouse}).
 */
public final class CourierAssignmentModule implements TickingModule, PersistentModule, ProvidesTab {
    /** The courier job id (MC {@code ModJobs.delivery}); the courier {@code JobType} uses this id. */
    public static final String COURIER_JOB_ID = "hycolony:deliveryman";

    private final List<Integer> couriers = new ArrayList<>();
    private HiringMode hiringMode = HiringMode.DEFAULT;

    /** The attached couriers' citizen ids, in attachment order. */
    public List<Integer> couriers() {
        return Collections.unmodifiableList(couriers);
    }

    /** MC CourierAssignmentModule.assignCitizen: attaches the courier {@code citizenId}; false if already attached. */
    public boolean attach(int citizenId) {
        if (couriers.contains(citizenId)) {
            return false;
        }
        couriers.add(citizenId);
        return true;
    }

    /** MC removeCitizen: detaches {@code citizenId}, who keeps its courier job; false if it was not attached. */
    public boolean detach(int citizenId) {
        return couriers.remove(Integer.valueOf(citizenId));
    }

    /** MC getModuleMax: two couriers per warehouse level, so none at level 0. */
    public static int maxCouriers(Building warehouse) {
        return warehouse.level() * 2;
    }

    public HiringMode hiringMode() {
        return hiringMode;
    }

    public void setHiringMode(HiringMode hiringMode) {
        this.hiringMode = hiringMode;
    }

    /** MC JobDeliveryman.findWareHouse: the first warehouse that has {@code citizenId} attached; empty if none. */
    public static Optional<Building> warehouseOf(Colony colony, int citizenId) {
        return colony.buildings().all().stream()
                .filter(b -> b.module(CourierAssignmentModule.class)
                        .map(m -> m.couriers.contains(citizenId))
                        .orElse(false))
                .findFirst();
    }

    /**
     * MC onColonyTick: attaches every courier without a warehouse while there is room and auto-hiring applies, then
     * detaches the citizens who are gone or no longer couriers. Marks the colony dirty on a change.
     */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (couriers.size() < maxCouriers(building) && hiringMode.canAutoHire(colony, building, false)) {
            for (CitizenData citizen : colony.citizens().all()) {
                if (isCourier(citizen)
                        && couriers.size() < maxCouriers(building)
                        && warehouseOf(colony, citizen.id()).isEmpty()) {
                    couriers.add(citizen.id());
                    colony.markDirty();
                }
            }
        }
        if (couriers.removeIf(id -> !colony.citizens()
                .get(id)
                .map(CourierAssignmentModule::isCourier)
                .orElse(false))) {
            colony.markDirty();
        }
    }

    /** Whether {@code citizen} has the courier job. */
    public static boolean isCourier(CitizenData citizen) {
        return citizen.job().map(j -> j.type().id().equals(COURIER_JOB_ID)).orElse(false);
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        couriers.forEach(arr::add);
        out.add("couriers", arr);
        out.addProperty("hiringMode", hiringMode.name());
    }

    /** Unknown hiring modes fall back to DEFAULT; missing citizens are dropped on the next colony tick. */
    @Override
    public void read(JsonObject in) {
        couriers.clear();
        couriers.addAll(SavedJson.ints(in.get("couriers")));
        hiringMode = SavedJson.enumOf(HiringMode.class, in.get("hiringMode")).orElse(HiringMode.DEFAULT);
    }

    /** The warehouse's Couriers tab (MC CourierAssignmentModuleView), by id as MC's HashSet of ints lists them. */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return new CourierAssignmentView(
                couriers.stream()
                        .sorted()
                        .flatMap(id -> colony.citizens().get(id).stream())
                        .map(CitizenData::name)
                        .toList(),
                maxCouriers(building));
    }
}
