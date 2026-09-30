package dev.hycolony.api;

import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import java.util.List;
import java.util.Optional;

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

    /** The buildings of {@code colony}, in the order they were placed. */
    List<BuildingSnapshot> buildings(ColonyRef colony);

    /** The requests of {@code colony} the request system still knows, in creation order. */
    @Experimental
    List<RequestSnapshot> requests(ColonyRef colony);
}
