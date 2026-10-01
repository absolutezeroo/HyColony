package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import java.util.Optional;

/**
 * A task row (MC WindowHutRequestTaskModule): who asks and, when the request serves a parent elsewhere, for whom
 * ("Warehouse -> Builder's Hut") with both places for the tooltip; its priority; {@code inProgress} draws it as the
 * one under way.
 */
public record TaskRow(
        RequestToken token,
        Requestable requestable,
        String requester,
        Optional<String> forRequester,
        Optional<BlockPos> requesterPos,
        Optional<BlockPos> forPos,
        int priority,
        boolean inProgress) {}
