package dev.hycolony.core.colony.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC Permissions: hasPermission's neutral rule, canAlterPermission, ranks and members as the window edits them. */
class PermissionRulesTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Permissions p = Permissions.createDefault(owner, "Owner");

    private Rank rank(int id) {
        return p.ranks().get(id);
    }

    @Test
    void theNeutralRankNeverEditsPermissionsNorTeleportsWhateverItsFlags() {
        rank(Permissions.NEUTRAL).add(Action.EDIT_PERMISSIONS);
        rank(Permissions.NEUTRAL).add(Action.TELEPORT_TO_COLONY);
        assertFalse(p.hasPermission(bob, Action.EDIT_PERMISSIONS));
        assertFalse(p.hasPermission(bob, Action.TELEPORT_TO_COLONY));
        assertTrue(p.hasPermission(bob, Action.ACCESS_TOGGLEABLES));
    }

    @Test
    void onlyTheOwnerAltersTheOwnerRank() {
        assertFalse(p.canAlterPermission(rank(Permissions.OFFICER), rank(Permissions.OWNER), Action.PLACE_BLOCKS));
        assertTrue(p.canAlterPermission(rank(Permissions.OWNER), rank(Permissions.OWNER), Action.PLACE_BLOCKS));
    }

    @Test
    void alteringNeedsEditPermissions() {
        assertFalse(p.canAlterPermission(rank(Permissions.FRIEND), rank(Permissions.NEUTRAL), Action.PLACE_BLOCKS));
        assertTrue(p.canAlterPermission(rank(Permissions.OWNER), rank(Permissions.NEUTRAL), Action.PLACE_BLOCKS));
    }

    @Test
    void noRankTakesFromItselfTheRightsToEditManageOrAccess() {
        Rank own = rank(Permissions.OWNER);
        assertFalse(p.canAlterPermission(own, own, Action.EDIT_PERMISSIONS));
        assertFalse(p.canAlterPermission(own, own, Action.MANAGE_HUTS));
        assertFalse(p.canAlterPermission(own, own, Action.ACCESS_HUTS));
        assertTrue(p.canAlterPermission(own, own, Action.BREAK_BLOCKS));
    }

    @Test
    void alterPermissionSetsOrClearsTheFlagWhenAllowed() {
        assertTrue(p.alterPermission(rank(Permissions.OWNER), rank(Permissions.FRIEND), Action.PLACE_BLOCKS, true));
        assertTrue(rank(Permissions.FRIEND).has(Action.PLACE_BLOCKS));
        assertTrue(p.alterPermission(rank(Permissions.OWNER), rank(Permissions.FRIEND), Action.PLACE_BLOCKS, false));
        assertFalse(rank(Permissions.FRIEND).has(Action.PLACE_BLOCKS));
        assertFalse(p.alterPermission(rank(Permissions.FRIEND), rank(Permissions.FRIEND), Action.PLACE_BLOCKS, true));
        assertFalse(rank(Permissions.FRIEND).has(Action.PLACE_BLOCKS));
    }

    @Test
    void aNewRankTakesTheFirstFreeIdAfterTheInitialOnes() {
        Rank guards = p.addRank("Guards");
        Rank traders = p.addRank("Traders");
        assertEquals(Permissions.HOSTILE + 1, guards.id());
        assertEquals(Permissions.HOSTILE + 2, traders.id());
        assertEquals(0L, guards.permissions());
        assertFalse(guards.isInitial());
        assertTrue(p.removeRank(guards.id()));
        assertEquals(Permissions.HOSTILE + 1, p.addRank("Again").id());
    }

    @Test
    void removingARankMovesItsPlayersToNeutralAndKeepsInitialRanks() {
        Rank guards = p.addRank("Guards");
        assertTrue(p.addPlayer(bob, "Bob", guards.id()));
        assertTrue(p.removeRank(guards.id()));
        assertEquals(Permissions.NEUTRAL, p.rankOf(bob).id());
        assertFalse(p.removeRank(Permissions.FRIEND));
        assertTrue(p.ranks().containsKey(Permissions.FRIEND));
    }

    @Test
    void aRankTypeIsManagerHostileOrNone() {
        Rank guards = p.addRank("Guards");
        assertTrue(p.setRankType(guards.id(), RankType.COLONY_MANAGER));
        assertTrue(guards.isColonyManager());
        assertFalse(guards.isHostile());
        assertTrue(p.setRankType(guards.id(), RankType.HOSTILE));
        assertTrue(guards.isHostile());
        assertFalse(guards.isColonyManager());
        assertTrue(p.setRankType(guards.id(), RankType.NONE));
        assertEquals(RankType.NONE, guards.type());
        assertFalse(p.setRankType(99, RankType.NONE));
    }

    @Test
    void addPlayerReplacesARankButNeverTouchesTheOwner() {
        assertTrue(p.addPlayer(bob, "Bob", Permissions.NEUTRAL));
        assertTrue(p.addPlayer(bob, "Bob", Permissions.FRIEND));
        assertEquals(Permissions.FRIEND, p.rankOf(bob).id());
        assertFalse(p.addPlayer(owner, "Owner", Permissions.NEUTRAL));
        assertEquals(Permissions.OWNER, p.rankOf(owner).id());
    }

    @Test
    void removePlayerNeverRemovesTheOwner() {
        p.addPlayer(bob, "Bob", Permissions.FRIEND);
        assertTrue(p.removePlayer(bob));
        assertFalse(p.members().containsKey(bob));
        assertFalse(p.removePlayer(owner));
        assertFalse(p.removePlayer(bob));
    }
}
