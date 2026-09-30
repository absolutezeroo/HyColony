package dev.hycolony.api.debug;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Subscription;
import java.util.List;
import java.util.Optional;

/**
 * What a debugging tool reads of one world's colonies. Every method must be called on that world's thread, else it
 * throws {@link IllegalStateException}. HyColony implements it, addons do not.
 *
 * @since 1.0
 */
@Experimental
public interface DebugAccess {
    /** What the citizen {@code ref}'s AI is doing; empty if there is no such citizen. */
    Optional<CitizenDebugSnapshot> inspect(CitizenRef ref);

    /** The last things the citizen {@code ref} did, oldest first, while tracked ({@link #track}); else empty. */
    List<HistoryEntry> history(CitizenRef ref);

    /**
     * The invariants {@code colony} breaks, lasting ones only: meant to be called every few ticks. A state (a stale
     * step, an incoherent queue, a request without resolver) counts once seen at every call for 5 seconds, as a worker
     * may pass through one; a trace (a walk's end, a stuck action, a failed respawn, repeated failures) at once. A lone
     * call returns only traces. Empty for an unknown colony.
     */
    List<Violation> check(ColonyRef colony);

    /**
     * Keeps the citizen {@code ref}'s {@link #history} until the subscription closes; several trackings may hold one
     * citizen. Empty if there is no such citizen. A plugin should rather track through HyColony's Hytale entry point,
     * which stops tracking when the plugin stops.
     */
    Optional<Subscription> track(CitizenRef ref);
}
