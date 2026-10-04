package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;

/** A module citizens are assigned to: workers, residents, couriers (MC AbstractAssignedCitizenModule). */
public interface AssignedCitizenModule extends BuildingModule {
    /**
     * MC removeCitizen: citizen {@code citizenId} leaves {@code building}, as its assignment ends; false, changing
     * nothing, when it was not assigned here.
     */
    boolean removeCitizen(Colony colony, Building building, int citizenId);

    /** MC CitizenManager.removeCivilian: every module of every hut of {@code colony} lets {@code citizenId} go. */
    static void removeEverywhere(Colony colony, int citizenId) {
        for (Building b : colony.buildings().all()) {
            for (BuildingModule m : b.modules().values()) {
                if (m instanceof AssignedCitizenModule assigned) {
                    assigned.removeCitizen(colony, b, citizenId);
                }
            }
        }
    }
}
