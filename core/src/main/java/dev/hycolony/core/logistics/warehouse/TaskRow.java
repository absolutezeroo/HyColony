package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import java.util.Optional;

/**
 * A courier task row (MC WindowHutRequestTaskModule): who asks and, when the request serves a parent elsewhere, for
 * whom ("Warehouse -> Builder's Hut"); its priority; {@code inProgress} draws it as the one under way.
 */
public record TaskRow(
        RequestToken token,
        Requestable requestable,
        String requester,
        Optional<String> forRequester,
        int priority,
        boolean inProgress) {}
