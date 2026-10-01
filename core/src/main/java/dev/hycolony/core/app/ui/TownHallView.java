package dev.hycolony.core.app.ui;

import dev.hycolony.core.citizen.home.HousingCapacity;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;

/** MC WindowTownHall, one field group per tab: Home (name, rename), Information, Citizens, Statistics. */
public record TownHallView(
        int colonyId,
        String colonyName,
        List<CitizenRow> citizens,
        WorkOrdersView workOrders,
        Stats stats,
        Home home,
        Info info,
        Settings settings) {
    public TownHallView {
        citizens = List.copyOf(citizens);
    }

    /** MC WindowSettings: the town hall's switches, in MC's order. */
    public record Settings(boolean moveIn, boolean autoHiring, boolean autoHousing) {}

    /** MC WindowInfoPage: the colony's day (for the interval filter) and its events of MC's kinds, oldest first. */
    public record Info(int day, List<EventRow> events) {
        public Info {
            events = List.copyOf(events);
        }

        /** MC WindowInfoPage.fillEventsList: the events of the last {@code days} days; every one for a negative. */
        public List<EventRow> within(int days) {
            return days < 0
                    ? events
                    : events.stream().filter(e -> e.day() >= day - days).toList();
        }
    }

    /**
     * An event (MC IColonyEventDescription): its type ("citizenSpawned", "buildingBuilt"...), day, parameters (a
     * citizen's name, or a hut type and level) and position; no position for one saved before schema 7.
     */
    public record EventRow(String type, int day, List<String> params, Optional<BlockPos> pos) {
        public EventRow {
            params = List.copyOf(params);
        }
    }

    /**
     * MC WindowMainPage: the town hall's position and level (its ribbon), the type of the order running on it (the
     * build button then cancels it) and the colony's pack among the blueprint styles.
     */
    public record Home(
            BlockPos townHallPos, int townHallLevel, Optional<WorkOrderType> order, String style, List<String> styles) {
        public Home {
            styles = List.copyOf(styles);
        }
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
