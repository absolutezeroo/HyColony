package dev.hyangler.api;

import java.util.UUID;

/**
 * Who fishes: a player, or an entity of another mod (HyColony's fisherman). Sealed: a case added later breaks the api.
 *
 * @since 1.0
 */
public sealed interface Angler {
    /** A player, by account. */
    record Player(UUID uuid) implements Angler {}

    /** An entity another mod drives, named by that mod and an id of its own. */
    record Plugin(String name, String id) implements Angler {}
}
