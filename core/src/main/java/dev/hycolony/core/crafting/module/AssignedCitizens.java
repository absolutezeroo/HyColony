package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import java.util.ArrayList;
import java.util.List;

/** MC AbstractBuilding.getAllAssignedCitizen: the citizens a hut employs, through every one of its worker modules. */
public final class AssignedCitizens {
    private AssignedCitizens() {}

    /** The hut's workers, in module then hiring order; a worker id the colony no longer knows is skipped. */
    public static List<CitizenData> of(Colony colony, Building hut) {
        List<CitizenData> out = new ArrayList<>();
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof WorkerModule workers) {
                for (int id : workers.workers()) {
                    colony.citizens().get(id).ifPresent(out::add);
                }
            }
        }
        return out;
    }
}
