package dev.hycolony.core.app.view;

import dev.hycolony.core.app.citizen.HappinessRows;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the citizen window's view (MC MainWindowCitizen, RequestWindowCitizen, JobWindowCitizen,
 * HappinessWindowCitizen): name, health, food and happiness, skills, gender, requests and the job's skills.
 */
final class CitizenViews {
    private static final int MC_MAX_HEALTH = CitizenData.MC_MAX_HEALTH;

    private final ColonyContext ctx;
    private final TownHallViews townHall;
    private final RequestViews requests;

    CitizenViews(ColonyContext ctx, TownHallViews townHall, RequestViews requests) {
        this.ctx = ctx;
        this.townHall = townHall;
        this.requests = requests;
    }

    CitizenView of(Colony c, CitizenData d, UUID player) {
        Optional<Building> work = Optional.ofNullable(d.workBuilding()).flatMap(c.buildings()::at);
        List<RequestsView.RequestRow> open =
                work.map(b -> requests(c, b, d, player)).orElse(List.of());
        Optional<Requestable> waitingFor =
                work.flatMap(b -> openOf(c, b, d.id()).stream().findFirst().map(Request::requestable));
        Optional<WorkerModule> worker = work.flatMap(b -> b.module(WorkerModule.class));
        List<Skill> jobSkills =
                worker.map(w -> List.of(w.primary(), w.secondary())).orElse(List.of());
        return new CitizenView(
                c.id(),
                d.id(),
                d.name(),
                d.job().map(j -> j.type().id()),
                work.map(Building::displayName),
                waitingFor.isPresent() ? "waitingFor" : townHall.status(c, d),
                waitingFor,
                c.citizens().jobActivity(d.id()),
                health(c, d),
                d.saturation(),
                d.happiness().happiness(c, d),
                HappinessRows.of(d.happiness()),
                d.gender(),
                ctx.players().isCreative(player),
                SkillRows.of(d.skills(), jobSkills),
                open,
                worker.map(w -> JobSkillShares.of(w.primary(), w.secondary())));
    }

    /**
     * MC RequestWindowCitizen.getOpenRequests: the citizen's open requests in its workplace, then the workplace's own
     * (citizen -1), each followed by its children, with Fulfill as MC isFulfillable.
     */
    private List<RequestsView.RequestRow> requests(Colony c, Building work, CitizenData d, UUID player) {
        List<ItemAmount> owned = requests.owned(player);
        List<RequestsView.RequestRow> rows = new ArrayList<>();
        for (int citizen : List.of(d.id(), Request.NO_CITIZEN)) {
            openOf(c, work, citizen).forEach(r -> requests.tree(c, r, 0, owned, rows));
        }
        boolean creative = ctx.players().isCreative(player);
        return rows.stream()
                .map(row -> row.withFulfillable(fulfillable(row, work, creative)))
                .toList();
    }

    /** The workplace's open requests filed for {@code citizenId} (-1: its own). */
    private static List<Request> openOf(Colony c, Building work, int citizenId) {
        return c.requests().byRequester(work.requesterId()).stream()
                .filter(r -> r.citizenId() == citizenId && r.state().isBefore(RequestState.COMPLETED))
                .toList();
    }

    /**
     * MC CitizenRequestTreeWindowModule.isFulfillable: a request for items, the player in creative mode or holding
     * some, on a root or on a request whose requester stands at the workplace.
     */
    private static boolean fulfillable(RequestsView.RequestRow row, Building work, boolean creative) {
        boolean canGive = creative ? row.requestable() instanceof Deliverable : row.playerHas() > 0;
        return canGive && (row.depth() == 0 || row.requesterPos().equals(Optional.of(work.position())));
    }

    /**
     * The body's health in MC points, truncated as MC casts its float health; full without a living body, as MC
     * CitizenDataView.getHealth gives MAX_HEALTH without its entity.
     */
    private int health(Colony c, CitizenData d) {
        return c.citizens()
                .bodyOf(d.id())
                .filter(ctx.bodies()::isAlive)
                .map(b -> ctx.bodies().healthPercent(b) * MC_MAX_HEALTH / 100)
                .orElse(MC_MAX_HEALTH);
    }
}
