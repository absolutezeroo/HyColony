package dev.hycolony.api;

import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.CitizenWellbeing;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * HyColony in one world. Every method must be called on that world's thread, else it throws
 * {@link IllegalStateException}. Reads return snapshots: immutable, they do not follow the colony afterwards. A list
 * for an unknown colony, or a colony of another world, is empty.
 *
 * <p>HyColony implements it, addons do not: a minor version may add methods.
 *
 * @since 1.0
 */
public interface ColonyWorld {
    /** The colonies of this world, by id. */
    List<ColonySummary> colonies();

    /** The colony {@code ref}; empty if there is none. */
    Optional<ColonySummary> colony(ColonyRef ref);

    /** The citizens of {@code colony}, by id. */
    List<CitizenSnapshot> citizens(ColonyRef colony);

    /** The citizen {@code ref}; empty if there is none. */
    Optional<CitizenSnapshot> citizen(CitizenRef ref);

    /**
     * How the citizen {@code ref} fares: its saturation and happiness; empty if there is none.
     *
     * @since 1.1
     */
    @Experimental
    Optional<CitizenWellbeing> wellbeing(CitizenRef ref);

    /** The buildings of {@code colony}, in the order they were placed. */
    List<BuildingSnapshot> buildings(ColonyRef colony);

    /** The requests of {@code colony} the request system still knows, in creation order. */
    @Experimental
    List<RequestSnapshot> requests(ColonyRef colony);

    /**
     * Hears every later event of exactly {@code type}, one of the records of {@code dev.hycolony.api.event} or an
     * experimental event of {@code dev.hycolony.api.debug} ({@code CitizenStateChanged}, {@code JobStateChanged},
     * {@code WalkEnded}, {@code StuckAction}, {@code RequestStateChanged}): each is delivered on this world's thread,
     * after the change. Throws {@link IllegalArgumentException} for another type. Close the subscription to stop; a
     * plugin should rather subscribe through HyColony's Hytale entry point, which closes it when the plugin stops.
     */
    <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener);

    /** What a debugging tool reads of this world's colonies. */
    @Experimental
    DebugAccess debug();
}
