package dev.hycolony.core.app.ui;

import dev.hycolony.core.citizen.home.HousingCapacity;
import dev.hycolony.core.colony.ColonySettings;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.colony.permission.RankType;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MC WindowTownHall, one field group per tab: Home, Information, Permissions, Citizens, Statistics, Settings. */
public record TownHallView(
        int colonyId,
        String colonyName,
        List<CitizenRow> citizens,
        WorkOrdersView workOrders,
        Stats stats,
        Home home,
        Info info,
        Settings settings,
        Permissions permissions) {
    public TownHallView {
        citizens = List.copyOf(citizens);
    }

    /**
     * MC WindowPermissionsPage: whether the viewer may edit (EDIT_PERMISSIONS), the members by rank, the ranks as made,
     * the refused actions newest first and the online players who are not members.
     */
    public record Permissions(
            boolean canEdit,
            List<MemberRow> members,
            List<RankRow> ranks,
            List<PermissionEvents.Event> refusals,
            List<PlayerDirectory.Profile> online) {
        public Permissions {
            members = List.copyOf(members);
            ranks = List.copyOf(ranks);
            refusals = List.copyOf(refusals);
            online = List.copyOf(online);
        }
    }

    /** A colony member: its id, name and rank. */
    public record MemberRow(UUID id, String name, int rankId, String rankName) {}

    /** A rank: its id, name, whether it is initial (never removed), its type and every action's state. */
    public record RankRow(int id, String name, boolean initial, RankType type, List<ActionState> actions) {
        public RankRow {
            actions = List.copyOf(actions);
        }
    }

    /** An action of a rank: set or not, and whether the viewer's rank may alter it (MC canAlterPermission). */
    public record ActionState(Action action, boolean on, boolean alterable) {}

    /** MC WindowSettings: the town hall's switches, in MC's order. */
    public record Settings(boolean moveIn, boolean autoHiring, boolean autoHousing) {
        /** The value of {@code toggle}. */
        public boolean get(ColonySettings.Toggle toggle) {
            return switch (toggle) {
                case MOVE_IN -> moveIn;
                case AUTO_HIRING -> autoHiring;
                case AUTO_HOUSING -> autoHousing;
            };
        }
    }

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
