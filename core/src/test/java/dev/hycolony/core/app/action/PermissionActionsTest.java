package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.RankType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC PermissionsMessage: the town hall's Permissions tab buttons, EDIT_PERMISSIONS. */
class PermissionActionsTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final PermissionActions actions = new PermissionActions(manager);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final Colony colony;

    PermissionActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        t.players.names.put(bob, "Bob");
        t.notifier.sent.clear();
    }

    private Permissions permissions() {
        return colony.permissions();
    }

    @Test
    void addingAKnownNameMakesTheMemberNeutralAndShowsTheTownHall() {
        manager.windows().openTownHall(alice, new BlockPos(0, 64, 0));
        assertTrue(actions.addPlayer(alice, colony.id(), "Bob"));
        assertEquals(Permissions.NEUTRAL, permissions().rankOf(bob).id());
        assertEquals("Bob", permissions().members().get(bob).name());
        TownHallView shown = (TownHallView) t.ui.shown.get(alice);
        assertTrue(shown.permissions().members().stream().anyMatch(m -> m.id().equals(bob)), "shown again");
    }

    @Test
    void anUnknownNameAddsNoOne() {
        actions.addPlayer(alice, colony.id(), "Nobody");
        assertEquals(1, permissions().members().size());
    }

    @Test
    void addingNeedsEditPermissionsAndRefusesSilentlyAsMc() {
        assertFalse(actions.addPlayer(carol, colony.id(), "Bob"));
        assertFalse(permissions().members().containsKey(bob));
        assertTrue(t.notifier.sent.isEmpty());
    }

    @Test
    void addingARefusedPlayerDropsTheirEventsAsMc() {
        permissions()
                .events()
                .add(new PermissionEvents.Event(Optional.of(bob), "Bob", Action.BREAK_BLOCKS, new BlockPos(1, 64, 1)));
        assertTrue(actions.addKnownPlayer(alice, colony.id(), bob, "Bob"));
        assertEquals(Permissions.NEUTRAL, permissions().rankOf(bob).id());
        assertTrue(permissions().events().entries().isEmpty());
    }

    @Test
    void anEditorRemovesAFriendButAFriendRemovesNoOneElse() {
        permissions().addPlayer(bob, "Bob", Permissions.FRIEND);
        permissions().addPlayer(carol, "Carol", Permissions.FRIEND);
        assertFalse(actions.removePlayer(carol, colony.id(), bob), "a friend has no EDIT_PERMISSIONS");
        assertTrue(actions.removePlayer(alice, colony.id(), bob));
        assertFalse(permissions().members().containsKey(bob));
    }

    @Test
    void anyoneMayLeaveTheColony() {
        permissions().addPlayer(carol, "Carol", Permissions.FRIEND);
        assertTrue(actions.removePlayer(carol, colony.id(), carol));
        assertFalse(permissions().members().containsKey(carol));
    }

    @Test
    void theOwnerIsNeverRemoved() {
        assertFalse(actions.removePlayer(alice, colony.id(), alice));
        assertEquals(Permissions.OWNER, permissions().rankOf(alice).id());
    }

    @Test
    void aRankIsAddedOnlyWithAFreshNonEmptyName() {
        assertTrue(actions.addRank(alice, colony.id(), "Guards"));
        assertFalse(actions.addRank(alice, colony.id(), "Guards"));
        assertFalse(actions.addRank(alice, colony.id(), ""));
        assertEquals(6, permissions().ranks().size());
    }

    @Test
    void rankTypeAndRemovalNeedEditPermissions() {
        assertTrue(actions.addRank(alice, colony.id(), "Guards"));
        int guards = Permissions.HOSTILE + 1;
        assertFalse(actions.setRankType(carol, colony.id(), guards, RankType.HOSTILE));
        assertTrue(actions.setRankType(alice, colony.id(), guards, RankType.HOSTILE));
        assertTrue(permissions().ranks().get(guards).isHostile());
        assertFalse(actions.removeRank(carol, colony.id(), guards));
        assertTrue(actions.removeRank(alice, colony.id(), guards));
        assertFalse(permissions().ranks().containsKey(guards));
    }

    @Test
    void alteringAnActionFollowsCanAlterPermission() {
        assertTrue(actions.alterPermission(alice, colony.id(), Permissions.FRIEND, Action.PLACE_BLOCKS, true));
        assertTrue(permissions().ranks().get(Permissions.FRIEND).has(Action.PLACE_BLOCKS));
        assertFalse(actions.alterPermission(alice, colony.id(), Permissions.OWNER, Action.EDIT_PERMISSIONS, false));
        assertTrue(permissions().ranks().get(Permissions.OWNER).has(Action.EDIT_PERMISSIONS));
    }

    @Test
    void aMembersRankChangesButNeverToOwnerAsMc() {
        permissions().addPlayer(bob, "Bob", Permissions.NEUTRAL);
        assertTrue(actions.setRank(alice, colony.id(), bob, Permissions.FRIEND));
        assertEquals(Permissions.FRIEND, permissions().rankOf(bob).id());
        assertFalse(actions.setRank(alice, colony.id(), bob, Permissions.OWNER));
        assertFalse(actions.setRank(carol, colony.id(), bob, Permissions.OFFICER));
        assertEquals(Permissions.FRIEND, permissions().rankOf(bob).id());
    }

    @Test
    void anEditorWhoIsNoManagerRemovesOnlyHostilePlayers() {
        permissions().addPlayer(carol, "Carol", Permissions.FRIEND);
        permissions().ranks().get(Permissions.FRIEND).add(Action.EDIT_PERMISSIONS);
        permissions().addPlayer(bob, "Bob", Permissions.HOSTILE);
        UUID dave = UUID.randomUUID();
        permissions().addPlayer(dave, "Dave", Permissions.NEUTRAL);
        assertFalse(actions.removePlayer(carol, colony.id(), dave));
        assertTrue(actions.removePlayer(carol, colony.id(), bob));
    }

    @Test
    void aRankMayBeNamedAfterTheOwnerRankAsMcListsAllButIt() {
        assertTrue(actions.addRank(alice, colony.id(), "Owner"));
    }

    @Test
    void aLateAnswerForAGoneColonyChangesNothing() {
        t.players.answerLater = true;
        assertTrue(actions.addPlayer(alice, colony.id(), "Bob"));
        manager.deleteColony(colony.id(), alice);
        t.players.answerNow();
        assertFalse(permissions().members().containsKey(bob));
    }

    @Test
    void aLateAnswerAfterTheActorLostTheRightAddsNoOne() {
        permissions().addPlayer(carol, "Carol", Permissions.FRIEND);
        permissions().ranks().get(Permissions.FRIEND).add(Action.EDIT_PERMISSIONS);
        t.players.answerLater = true;
        assertTrue(actions.addPlayer(carol, colony.id(), "Bob"));
        permissions().ranks().get(Permissions.FRIEND).remove(Action.EDIT_PERMISSIONS);
        t.players.answerNow();
        assertFalse(permissions().members().containsKey(bob));
    }

    @Test
    void aRefusedRemovalSaysNothingAsMc() {
        permissions().addPlayer(bob, "Bob", Permissions.FRIEND);
        permissions().addPlayer(carol, "Carol", Permissions.FRIEND);
        assertFalse(actions.removePlayer(carol, colony.id(), bob));
        assertTrue(t.notifier.sent.isEmpty());
    }

    @Test
    void aLateAnswerDoesNotReopenAClosedTownHall() {
        t.players.answerLater = true;
        assertTrue(actions.addPlayer(alice, colony.id(), "Bob"));
        t.ui.shown.clear();
        t.players.answerNow();
        assertEquals(Permissions.NEUTRAL, permissions().rankOf(bob).id());
        assertFalse(t.ui.shown.containsKey(alice), "the player had left the town hall");
    }

    @Test
    void aNameLongerThanTheFieldIsRefused() {
        assertFalse(actions.addRank(alice, colony.id(), "x".repeat(33)));
        assertFalse(actions.addPlayer(alice, colony.id(), "x".repeat(33)));
    }
}
