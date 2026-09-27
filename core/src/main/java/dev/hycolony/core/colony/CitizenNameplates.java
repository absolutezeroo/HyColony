package dev.hycolony.core.colony;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.resolver.PlayerResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MineColonies' "interaction pending" marker (the "!" over a citizen with a blocking RequestBasedInteraction): while
 * a citizen has an open request that only a player can provide (held by the {@link PlayerResolver}, the same
 * condition as the chat notice), its nameplate reads "! name". Recomputed every {@link #INTERVAL} ticks; a body is
 * renamed only when its name changes, or once when first seen (a body loaded after a restart may carry a stale name).
 *
 * <p>Deviation from MC: MC renders an icon over the head; Hytale NPCs have no such overlay, so the name carries it.
 */
public final class CitizenNameplates {
    public static final int INTERVAL = 20;
    static final String MARKER = "! ";

    private final Colony colony;
    /** The name last given to each body. */
    private final Map<BodyId, String> shown = new HashMap<>();

    CitizenNameplates(Colony colony) {
        this.colony = colony;
    }

    /** The name a body of {@code citizen} should show now (also for a body spawned now). */
    public String nameFor(CitizenData citizen) {
        return waitingCitizens().contains(citizen.id()) ? MARKER + citizen.name() : citizen.name();
    }

    public void refresh() {
        Set<Integer> waiting = waitingCitizens();
        Map<BodyId, String> seen = new HashMap<>();
        for (CitizenData d : colony.citizens().all()) {
            BodyId body = colony.citizens().bodyOf(d.id()).orElse(null);
            if (body == null || !colony.context().bodies().isAlive(body)) {
                continue;
            }
            String name = waiting.contains(d.id()) ? MARKER + d.name() : d.name();
            if (!name.equals(shown.get(body))) {
                colony.context().bodies().setDisplayName(body, name);
            }
            seen.put(body, name);
        }
        shown.clear(); // gone bodies are forgotten
        shown.putAll(seen);
    }

    // ponytail: building-level requests (citizen -1) mark nobody; MC shows those on the hut, not on a citizen.
    private Set<Integer> waitingCitizens() {
        Set<Integer> out = new HashSet<>();
        for (Request r : colony.requests().assignedTo(PlayerResolver.ID)) {
            if (r.citizenId() != -1 && r.state().isBefore(RequestState.COMPLETED)) {
                out.add(r.citizenId());
            }
        }
        return out;
    }
}
