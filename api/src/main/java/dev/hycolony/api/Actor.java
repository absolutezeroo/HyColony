package dev.hycolony.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Who acts, or who caused a change. Its three cases are fixed: adding one would break an addon's exhaustive
 * {@code switch}, so it would be a major version.
 *
 * @since 1.0
 */
public sealed interface Actor {
    /** A player, whose rights in the colony are checked. */
    record Player(UUID id) implements Actor {
        /** Refuses a missing id. */
        public Player {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * A plugin acting on its own, named by its identifier ({@code Group:Name}, as in its manifest); it answers for what
     * it does, the colony's rights are not checked.
     */
    record Plugin(String name) implements Actor {
        /** Refuses a missing or blank name. */
        public Plugin {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("a plugin is named by its identifier");
            }
        }
    }

    /** The colony itself (its AI, its own upkeep): only ever a cause, an action asked in its name is refused. */
    record Colony() implements Actor {}
}
