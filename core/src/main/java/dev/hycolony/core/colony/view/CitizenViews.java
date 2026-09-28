package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds the citizen window's view (MC WindowCitizen): job, activity, skills, inventory, requests, job skills. */
final class CitizenViews {
    private final ColonyContext ctx;
    private final TownHallViews townHall;
    private final RequestViews requests;

    CitizenViews(ColonyContext ctx, TownHallViews townHall, RequestViews requests) {
        this.ctx = ctx;
        this.townHall = townHall;
        this.requests = requests;
    }

    CitizenView of(Colony c, CitizenData d, UUID player) {
        Map<ItemKey, Integer> owned = ctx.ports().playerInventory().contents(player);
        List<RequestsView.RequestRow> open = new ArrayList<>();
        for (Request r : c.requests().all()) {
            if (r.citizenId() == d.id() && r.state().isBefore(RequestState.COMPLETED)) {
                requests.tree(c, r, 0, owned, open);
            }
        }
        Optional<Requestable> waitingFor = open.stream().findFirst().map(RequestsView.RequestRow::requestable);
        Optional<Building> work = Optional.ofNullable(d.workBuilding()).flatMap(c.buildings()::at);
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
                SkillRows.of(d.skills(), jobSkills),
                open,
                worker.map(w -> JobSkillShares.of(w.primary(), w.secondary())));
    }
}
