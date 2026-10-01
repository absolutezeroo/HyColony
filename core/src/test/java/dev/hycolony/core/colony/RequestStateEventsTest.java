package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestStateChanged;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A colony posts each state change of its requests, from every transition of the request system, and only changes
 * (spec 2026-09-30, § 4.2, api.debug).
 */
class RequestStateEventsTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);

    private final TestContexts t = new TestContexts();
    private final Colony colony;
    private final List<RequestStateChanged> heard;
    private final RequestToken token;

    RequestStateEventsTest() {
        ColonyManager manager = t.manager();
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        heard = t.heard(RequestStateChanged.class);
        token = colony.requests()
                .createAndAssign(
                        colony.buildings().at(HALL).orElseThrow(),
                        new StackRequest(new ItemKey("Wood_Planks"), 1, 1, true),
                        1);
    }

    /** The changes heard since {@code from}, as "FROM->TO". */
    private List<String> path(int from) {
        return heard.subList(from, heard.size()).stream()
                .map(e -> e.from() + "->" + e.to())
                .toList();
    }

    @Test
    void newRequestIsAssignedThenInProgress() {
        assertEquals(List.of("CREATED->ASSIGNING", "ASSIGNING->ASSIGNED", "ASSIGNED->IN_PROGRESS"), path(0));
        assertEquals(
                List.of(colony.id()),
                heard.stream().map(RequestStateChanged::colonyId).distinct().toList());
        assertEquals(
                List.of(token),
                heard.stream().map(RequestStateChanged::token).distinct().toList());
    }

    @Test
    void cancellingPostsItOnce() {
        int from = heard.size();

        colony.requests().updateState(token, RequestState.CANCELLED);

        assertEquals(List.of("IN_PROGRESS->CANCELLED"), path(from), "the canceller sets it again: no change");
    }

    @Test
    void cancellingAllOfARequesterPostsEachCancellation() {
        int from = heard.size();

        colony.requests()
                .cancelAllFrom(colony.requests().get(token).orElseThrow().requester());

        assertEquals(List.of("IN_PROGRESS->CANCELLED"), path(from));
    }

    @Test
    void reassigningAwayFromEveryResolverLeavesItReported() {
        int from = heard.size();

        colony.requests().reassign(token, Set.of(PlayerResolver.ID, RetryingResolver.ID));

        assertEquals(List.of("IN_PROGRESS->REPORTED", "REPORTED->ASSIGNING", "ASSIGNING->REPORTED"), path(from));
    }

    @Test
    void resolvingPostsItsFollowupsThenCompletion() {
        int from = heard.size();

        colony.requests().updateState(token, RequestState.RESOLVED);

        assertEquals(
                List.of("IN_PROGRESS->RESOLVED", "RESOLVED->FOLLOWUP_IN_PROGRESS", "FOLLOWUP_IN_PROGRESS->COMPLETED"),
                path(from));
    }

    @Test
    void overrulingPostsItThenCompletion() {
        int from = heard.size();

        colony.requests().overrule(token, List.of(), false);

        assertEquals(List.of("IN_PROGRESS->OVERRULED", "OVERRULED->COMPLETED"), path(from));
    }
}
