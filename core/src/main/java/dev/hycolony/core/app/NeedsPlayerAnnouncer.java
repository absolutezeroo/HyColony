package dev.hycolony.core.app;

import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.app.ui.UiPort;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.resolver.PlayerResolver;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MC StandardPlayerRequestResolver's "needs" message: a request only a player can provide is told to the online
 * owner and officers, naming the citizen (or the building) and its job.
 */
final class NeedsPlayerAnnouncer {
    private final Colony colony;
    private final UiPort ui;

    /** Makes {@code colony}'s player resolver tell its members, through {@code ui}, of a request needing a player. */
    static void install(Colony colony, UiPort ui) {
        NeedsPlayerAnnouncer announcer = new NeedsPlayerAnnouncer(colony, ui);
        colony.requests()
                .resolver(PlayerResolver.ID)
                .map(PlayerResolver.class::cast)
                .ifPresent(p -> p.setOnNeedsPlayer(announcer::announce));
    }

    private NeedsPlayerAnnouncer(Colony colony, UiPort ui) {
        this.colony = colony;
        this.ui = ui;
    }

    /** Tells the online owner and officers about {@code r}; a request that is not for items is not announced. */
    void announce(Request r) {
        Deliverable wanted = r.deliverable().orElse(null);
        if (wanted == null) {
            return;
        }
        Building b = colony.buildings().byRequester(r.requester()).orElse(null);
        Optional<CitizenData> citizen = r.citizenId() != Request.NO_CITIZEN
                ? colony.citizens().get(r.citizenId())
                : Optional.ofNullable(b)
                        .flatMap(hut -> hut.module(WorkerModule.class))
                        .flatMap(w -> w.workers().stream().findFirst())
                        .flatMap(colony.citizens()::get);
        String who = r.citizenId() != Request.NO_CITIZEN
                ? citizen.map(CitizenData::name).orElse("")
                : b != null ? b.displayName() : r.requester().value();
        String job = citizen.flatMap(CitizenData::job).map(j -> j.type().id()).orElse("");
        NeedsPlayerNotice notice = new NeedsPlayerNotice(who, job, wanted);
        for (UUID p : ownerAndOfficers()) {
            if (colony.context().players().isOnline(p)) {
                ui.notifyNeedsPlayer(p, notice);
            }
        }
    }

    private Set<UUID> ownerAndOfficers() {
        Permissions permissions = colony.permissions();
        Set<UUID> to = new LinkedHashSet<>();
        to.add(permissions.owner());
        permissions.members().forEach((p, m) -> {
            if (m.rankId() <= Permissions.OFFICER) {
                to.add(p);
            }
        });
        return to;
    }
}
