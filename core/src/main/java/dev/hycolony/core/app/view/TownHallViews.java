package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Builds the town hall window's view (MC WindowTownHall): the colony, its work orders, citizens and statistics. */
final class TownHallViews {
    /**
     * MC EventDescriptionManager's kinds: a citizen moving in, a hut built, upgraded, repaired or deconstructed.
     * Births, coming of age and deaths need systems HyColony lacks.
     */
    private static final Set<String> MC_EVENTS =
            Set.of("citizenSpawned", "buildingBuilt", "buildingUpgraded", "buildingRepaired", "buildingDeconstructed");

    private final ColonyContext ctx;

    TownHallViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    TownHallView of(Colony c, UUID viewer) {
        // MC WindowCitizenPage.COMPARE_BY_NAME.
        List<CitizenRow> rows = c.citizens().all().stream()
                .sorted(Comparator.comparing(CitizenData::name))
                .map(d -> row(c, d))
                .toList();
        return new TownHallView(
                c.id(),
                c.name(),
                rows,
                WorkOrderViews.of(c, viewer),
                TownHallStats.of(c),
                home(c),
                info(c),
                new TownHallView.Settings(
                        c.settings().moveIn(),
                        c.settings().autoHiring(),
                        c.settings().autoHousing()));
    }

    /** The log's events of MC's kinds, in log order (MC WindowInfoPage.fillEventsList). */
    private static TownHallView.Info info(Colony c) {
        return new TownHallView.Info(
                c.day(),
                c.log().entries().stream()
                        .filter(e -> MC_EVENTS.contains(e.type()))
                        .map(e -> new TownHallView.EventRow(e.type(), e.day(), e.params(), e.pos()))
                        .toList());
    }

    private TownHallView.Home home(Colony c) {
        Optional<Building> hall = c.buildings().townHall();
        List<String> styles = ctx.ports().blueprints().styles();
        return new TownHallView.Home(
                hall.map(Building::position).orElse(c.center()),
                hall.map(Building::level).orElse(0),
                hall.flatMap(b -> c.work().byBuilding(b.position())).map(WorkOrder::type),
                // Deviation from MC: a colony without a pack shows the first style, MC's DEFAULT_STYLE (Colonial).
                c.settings().style().isEmpty() && !styles.isEmpty()
                        ? styles.getFirst()
                        : c.settings().style(),
                styles);
    }

    private CitizenRow row(Colony c, CitizenData d) {
        List<CitizenRow.SkillLevel> skills = Arrays.stream(Skill.values())
                .map(s -> new CitizenRow.SkillLevel(s, d.skills().level(s)))
                .toList();
        return new CitizenRow(
                d.id(), d.name(), d.gender(), d.job().map(j -> j.type().id()), status(c, d), skills);
    }

    /**
     * "absent" without a live body, else the AI state, "idle" or "working"; an idle citizen walking about (MC keeps a
     * wandering citizen IDLE) shows as "wandering".
     */
    String status(Colony c, CitizenData d) {
        Optional<BodyId> body = c.citizens().bodyOf(d.id()).filter(ctx.bodies()::isAlive);
        if (body.isEmpty()) {
            return "absent";
        }
        CitizenState state = c.citizens().aiState(d.id()).orElse(CitizenState.IDLE);
        if (state == CitizenState.IDLE && ctx.bodies().navStatus(body.get()) == NavStatus.MOVING) {
            return "wandering";
        }
        return state.name().toLowerCase(Locale.ROOT);
    }
}
