package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.ui.NeedsPlayerNotice;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.request.Request;
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

    NeedsPlayerAnnouncer(Colony colony) {
        this.colony = colony;
    }

    void announce(Request r) {
        Building b = colony.buildings().byRequester(r.requester()).orElse(null);
        Optional<CitizenData> citizen = r.citizenId() != -1
                ? colony.citizens().get(r.citizenId())
                : Optional.ofNullable(b)
                        .flatMap(hut -> hut.module(WorkerModule.class))
                        .flatMap(w -> w.workers().stream().findFirst())
                        .flatMap(colony.citizens()::get);
        String who = r.citizenId() != -1
                ? citizen.map(CitizenData::name).orElse("")
                : b != null ? b.displayName() : r.requester().value();
        String job = citizen.flatMap(CitizenData::job).map(j -> j.type().id()).orElse("");
        NeedsPlayerNotice notice = new NeedsPlayerNotice(who, job, r.requestable());
        for (UUID p : ownerAndOfficers()) {
            if (colony.context().players().isOnline(p)) {
                colony.context().ui().notifyNeedsPlayer(p, notice);
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
