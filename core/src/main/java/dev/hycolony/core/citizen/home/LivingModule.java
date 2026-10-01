package dev.hycolony.core.citizen.home;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.building.module.TickingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The residents of a residence: one per hut level, assigned by hand or taken among the homeless. Port of MC
 * LivingBuildingModule and its AbstractAssignedCitizenModule.
 */
public final class LivingModule implements PersistentModule, TickingModule, BuildingEventsModule, ProvidesTab {
    private final List<Integer> residents = new ArrayList<>();
    private HiringMode hiringMode = HiringMode.DEFAULT;

    /** MC LivingBuildingModule.getModuleMax: the hut's level, so none at level 0. */
    public int max(Building b) {
        return b.level();
    }

    public boolean isFull(Building b) {
        return residents.size() >= max(b);
    }

    public List<Integer> residents() {
        return Collections.unmodifiableList(residents);
    }

    public HiringMode hiringMode() {
        return hiringMode;
    }

    public void setHiringMode(HiringMode hiringMode) {
        this.hiringMode = hiringMode;
    }

    /** MC onColonyTick: AUTO, or DEFAULT while the colony's {@code autoHousing} setting is on. */
    public boolean autoHousing(Colony c) {
        return hiringMode == HiringMode.AUTO
                || (hiringMode == HiringMode.DEFAULT && c.settings().autoHousing());
    }

    /** MC WindowAssignCitizen: Assign and Unassign only work in MANUAL, or DEFAULT while autoHousing is off. */
    public boolean manual(Colony c) {
        return hiringMode == HiringMode.MANUAL
                || (hiringMode == HiringMode.DEFAULT && !c.settings().autoHousing());
    }

    /**
     * MC assignCitizen: makes {@code b} the citizen's home, after it left its old one (MC setHomeBuilding); false for
     * a resident already here or a full hut.
     */
    public boolean assign(Colony c, Building b, CitizenData citizen) {
        if (residents.contains(citizen.id()) || isFull(b)) {
            return false;
        }
        residents.add(citizen.id());
        moveHome(c, citizen, b.position());
        c.markDirty();
        return true;
    }

    /** MC removeCitizen: the resident leaves, homeless and bedless; false for a citizen who does not live here. */
    public boolean remove(Colony c, Building b, int citizenId) {
        if (!residents.remove(Integer.valueOf(citizenId))) {
            return false;
        }
        c.citizens().get(citizenId).ifPresent(d -> moveHome(c, d, null));
        c.markDirty();
        return true;
    }

    /**
     * MC CitizenData.setHomeBuilding: a citizen moving house leaves its old one first; any change of an existing home
     * forgets its bed.
     */
    private static void moveHome(Colony c, CitizenData citizen, @Nullable BlockPos home) {
        BlockPos old = citizen.homeBuilding();
        if (old != null && home != null && !old.equals(home)) {
            c.buildings()
                    .at(old)
                    .ifPresent(o -> o.module(LivingModule.class).ifPresent(m -> m.remove(c, o, citizen.id())));
        }
        if (citizen.homeBuilding() != null) {
            citizen.setBedPos(null);
        }
        citizen.setHomeBuilding(home);
    }

    /** MC AbstractAssignedCitizenModule.onDestroyed: every resident leaves. */
    @Override
    public void onRemoved(Colony colony, Building building) {
        for (int id : List.copyOf(residents)) {
            remove(colony, building, id);
        }
    }

    /** MC LivingBuildingModule.onColonyTick: takes the homeless, in citizen order, as many as fit. */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (isFull(building) || !autoHousing(colony)) {
            return;
        }
        for (CitizenData citizen : colony.citizens().all()) {
            if (isFull(building)) {
                return;
            }
            if (citizen.homeBuilding() == null) {
                assign(colony, building, citizen);
            }
        }
    }

    /** MC LivingBuildingModuleView: the Residents tab, see {@link ResidentsViews}. */
    @Override
    public ResidentsView tab(Colony colony, Building building, UUID viewer) {
        return ResidentsViews.of(colony, building, this, viewer);
    }

    /**
     * The residents read from a save, removed from this module: the load assigns each again in order (MC
     * LivingBuildingModule.deserializeNBT), see {@code HousingHeal}.
     */
    public List<Integer> takeSavedResidents() {
        List<Integer> saved = List.copyOf(residents);
        residents.clear();
        return saved;
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        residents.forEach(arr::add);
        out.add("residents", arr);
        out.addProperty("hiringMode", hiringMode.name());
    }

    /** Tolerant (CLAUDE.md § 5): a non-number resident is skipped, an unknown or missing mode reads as DEFAULT. */
    @Override
    public void read(JsonObject in) {
        residents.clear();
        residents.addAll(SavedJson.ints(in.get("residents")));
        hiringMode = SavedJson.enumOf(HiringMode.class, in.get("hiringMode")).orElse(HiringMode.DEFAULT);
    }
}
