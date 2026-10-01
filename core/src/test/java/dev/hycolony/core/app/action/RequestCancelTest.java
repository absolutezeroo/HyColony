package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC RequestTreeWindowModule.cancel and UpdateRequestStateMessage (CANCELLED), MANAGE_HUTS by default. */
class RequestCancelTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final RequestToken token;

    RequestCancelTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        BlockPos pos = new BlockPos(30, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0, alice);
        Building hut = colony.buildings().at(pos).orElseThrow();
        token = colony.requests()
                .createAndAssign(hut, new StackRequest(new ItemKey("plank"), 3, 3, true), Request.NO_CITIZEN);
    }

    private Optional<RequestState> state() {
        return colony.requests().get(token).map(Request::state);
    }

    @Test
    void aManagerCancelsARequest() {
        colony.clearDirty();
        assertTrue(manager.requestActions().cancel(alice, colony.id(), token));
        assertTrue(state().isEmpty() || state().get() == RequestState.CANCELLED, "cancelled, then dropped");
        assertTrue(colony.isDirty());
    }

    @Test
    void aFriendMayNotCancel() {
        Optional<RequestState> before = state();
        assertFalse(manager.requestActions().cancel(carol, colony.id(), token));
        assertEquals(before, state());
        assertTrue(before.isPresent() && before.get().isBefore(RequestState.COMPLETED));
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key(),
                "MC tells the refusal");
    }

    @Test
    void aCompletedRequestStillKnownIsLeftAlone() {
        colony.requests().updateState(token, RequestState.COMPLETED);
        Optional<RequestState> before = state();
        assertTrue(before.isPresent(), "the request waits to be received");
        assertFalse(manager.requestActions().cancel(alice, colony.id(), token));
        assertEquals(before, state());
    }

    @Test
    void anUnknownOrClosedRequestIsIgnored() {
        assertTrue(manager.requestActions().cancel(alice, colony.id(), token));
        assertFalse(manager.requestActions().cancel(alice, colony.id(), token), "already gone");
        assertFalse(manager.requestActions().cancel(alice, colony.id() + 1, token), "no such colony");
    }
}
