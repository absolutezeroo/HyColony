package dev.hylens.plugin.watch;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.List;
import java.util.Optional;

/** What HyColony tells of a watched citizen at one refresh: the HUD and the drawings show the same read. */
record Watched(CitizenSnapshot citizen, CitizenDebugSnapshot debug, List<Violation> alerts) {
    /**
     * On the world's thread: {@code citizen} as HyColony knows it, with its confirmed alerts; empty when it no longer
     * knows it.
     */
    static Optional<Watched> read(ColonyWorld colonies, CitizenRef citizen) {
        DebugAccess debug = colonies.debug();
        Optional<CitizenDebugSnapshot> snapshot = debug.inspect(citizen);
        Optional<CitizenSnapshot> known = colonies.citizen(citizen);
        if (snapshot.isEmpty() || known.isEmpty()) {
            return Optional.empty();
        }
        List<Violation> alerts = debug.check(citizen.colony()).stream()
                .filter(v -> v.citizen().equals(Optional.of(citizen)))
                .toList();
        return Optional.of(new Watched(known.get(), snapshot.get(), alerts));
    }
}
