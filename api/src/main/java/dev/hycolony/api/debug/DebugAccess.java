package dev.hycolony.api.debug;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Subscription;
import java.util.List;
import java.util.Optional;

/**
 * What a debugging tool reads of one world's colonies, and does to their citizens. Every method must be called on that
 * world's thread, else it throws {@link IllegalStateException}. HyColony implements it, addons do not.
 *
 * <p>An action is asked by an {@link Actor}: a player acts if a server operator or a manager of the colony (as MC's
 * officer commands), a plugin answers for itself, and the colony, only ever a cause, is refused.
 * {@link ActionResult.NotFound} for an unknown colony, or, once allowed, an unknown citizen.
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

    /**
     * Sends the citizen {@code ref} to {@code target}, a block of this world (MC {@code /mc citizens walk}): it walks
     * up to 4 blocks from it, for 3 minutes at most or until the stuck handler gives up, its AI waiting, which then
     * waits 5 seconds more and starts its job afresh. A new call replaces the walk under way.
     * {@link ActionResult.Unavailable} while its body is unloaded or dead.
     */
    ActionResult walkTo(Actor actor, CitizenRef ref, Pos target);

    /** Starts a leisure break for the citizen {@code ref} now, as long as one of MC's (3 minutes). */
    ActionResult forceLeisure(Actor actor, CitizenRef ref);

    /**
     * Teleports the citizen {@code ref}'s body onto {@code target}, its job then starting afresh;
     * {@link ActionResult.Unavailable} while unloaded or dead.
     */
    ActionResult teleport(Actor actor, CitizenRef ref, Pos target);

    /**
     * Replaces the citizen {@code ref}'s body with a new one where the colony's respawn check would put it (its respawn
     * point, else its last position...); {@link ActionResult.Unavailable} when none could appear, its old body kept.
     */
    ActionResult respawnBody(Actor actor, CitizenRef ref);

    /**
     * A new citizen arrives at the colony's town hall (MC {@code /mc citizens spawnNew}), even with "new citizens" off
     * and beyond the colony's room. Operators only, and plugins; {@link ActionResult.Unavailable} without a loaded town
     * hall or room for its body there, nothing created then.
     *
     * @since 1.2
     */
    ActionResult spawnCitizen(Actor actor, ColonyRef colony);

    /**
     * Changes the citizen {@code ref}'s saturation by {@code change} with {@code value}, which must be between 0 and
     * its maximum (MC {@code /mc citizens modify saturation}). A player must be in creative mode, and a colony manager
     * who is not an operator needs the server's Commands.CanPlayerUseModifyCitizensCommand.
     *
     * @since 1.2
     */
    ActionResult modifySaturation(Actor actor, CitizenRef ref, SaturationChange change, double value);

    /**
     * Hands the open item request {@code requestId} ({@code RequestSnapshot.id}) what it asks for, as MC's request
     * window "Fulfill": a player needs the colony's MANAGE_HUTS right and gives from their inventory, or for free in
     * creative mode; a plugin gives for free. {@link ActionResult.NotFound} for an unknown, malformed or closed
     * request, or one that asks no items (a delivery, a pickup); {@link ActionResult.Unavailable} when the player holds
     * none of it, or only wears it.
     *
     * @since 1.3
     */
    ActionResult fulfilRequest(Actor actor, ColonyRef colony, String requestId);

    /**
     * Restarts the colony's request system (MC {@code /mc colony requestsystem-reset}): every request forgotten at
     * once, its workers asking again at their next need. A player who is not an operator needs the server's
     * Commands.CanPlayerUseResetCommand; as MC, any player may then, member or not.
     *
     * @since 1.3
     */
    ActionResult resetRequests(Actor actor, ColonyRef colony);

    /**
     * How long each part of HyColony's core took in this world over the last minute, the heaviest first: Hytale
     * measures the whole core as one system.
     */
    List<PartTiming> timings();
}
