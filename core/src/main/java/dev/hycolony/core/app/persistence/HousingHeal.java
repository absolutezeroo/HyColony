package dev.hycolony.core.app.persistence;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.kernel.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilds the colony's housing after a load. MC saves no citizen's home: each residence assigns its saved residents
 * again, in order (LivingBuildingModule.deserializeNBT), so one past the hut's level becomes homeless. A residence
 * built before SP4 finds the beds of its plan once.
 */
final class HousingHeal {
    private HousingHeal() {}

    /** Reassigns the residents and fills empty bed lists from the plans; true when a home or a bed changed. */
    static boolean heal(Colony c) {
        Map<Integer, @Nullable BlockPos> saved = new HashMap<>();
        for (CitizenData d : c.citizens().all()) {
            saved.put(d.id(), d.homeBuilding());
            d.setHomeBuilding(null); // MC keeps no home: the residences give it back below
        }
        Map<LivingModule, List<Integer>> savedResidents = new HashMap<>();
        for (Building b : c.buildings().all()) {
            b.module(LivingModule.class).ifPresent(living -> savedResidents.put(living, reassign(c, b, living)));
        }
        boolean changed = savedResidents.entrySet().stream()
                .anyMatch(e -> !e.getKey().residents().equals(e.getValue()));
        for (CitizenData d : c.citizens().all()) {
            if (d.homeBuilding() == null && d.bedPos() != null) {
                d.setBedPos(null);
                changed = true;
            }
            changed |= !Objects.equals(saved.get(d.id()), d.homeBuilding());
        }
        for (Building b : c.buildings().all()) {
            changed |= findPlannedBeds(c, b);
        }
        return changed;
    }

    /**
     * MC deserializeNBT: each saved resident, in order, through assignCitizen (refused once the hut is full); returns
     * the saved list.
     */
    private static List<Integer> reassign(Colony c, Building b, LivingModule living) {
        List<Integer> saved = living.takeSavedResidents();
        for (int id : saved) {
            c.citizens().get(id).ifPresent(d -> living.assign(c, b, d));
        }
        return saved;
    }

    /** A built residence without beds registers those of its plan (MC registerBlockPosition never ran for it). */
    private static boolean findPlannedBeds(Colony c, Building b) {
        BedModule beds = b.module(BedModule.class).orElse(null);
        if (beds == null
                || !beds.beds().isEmpty()
                || b.level() <= 0
                || b.style().isEmpty()) {
            return false;
        }
        GamePorts ports = c.context().ports();
        ports.blueprints()
                .load(b.style(), b.type().id(), b.level(), b.rotation())
                .ifPresent(bp -> bp.entries().stream()
                        .filter(e -> ports.blockCatalog().isBed(e.state().key()))
                        .forEach(e -> beds.addBed(b.position()
                                .offset(
                                        e.offset().x(),
                                        e.offset().y(),
                                        e.offset().z()))));
        return !beds.beds().isEmpty();
    }
}
