package dev.hycolony.core.app;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.Msg;

/** Outcome of placing a hut block. */
public sealed interface HutPlacement {
    /** Placement inside an existing colony is fine. */
    record Allowed(Colony colony) implements HutPlacement {}

    /** Town hall outside any colony: start the "found a colony" flow. */
    record FoundNewColony() implements HutPlacement {}

    record Denied(Msg reason) implements HutPlacement {}
}
