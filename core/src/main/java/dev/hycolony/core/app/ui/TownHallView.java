package dev.hycolony.core.app.ui;

import dev.hycolony.core.citizen.home.HousingCapacity;
import java.util.List;

/** MC WindowTownHall, one field group per tab: Actions (name, rename), Information, Citizens, Statistics. */
public record TownHallView(
        int colonyId,
        String colonyName,
        String ownerName,
        int day,
        List<CitizenRow> citizens,
        boolean canRename,
        WorkOrdersView workOrders,
        Stats stats) {
    public TownHallView {
        citizens = List.copyOf(citizens);
    }

    /**
     * MC WindowStatsPage.createAndSetStatistics: the citizen count over {@code maxCitizens} with its colour
     * ({@code population}), workers over places per job (sorted by job id), the children and the unemployed adults.
     */
    public record Stats(
            int citizens,
            int maxCitizens,
            HousingCapacity.Population population,
            List<JobCount> jobs,
            int children,
            int unemployed) {
        public Stats {
            jobs = List.copyOf(jobs);
        }
    }

    /** {@code jobId} is a job type id ("hycolony:builder"); {@code max} sums the places of every hut of that job. */
    public record JobCount(String jobId, int workers, int max) {}
}
