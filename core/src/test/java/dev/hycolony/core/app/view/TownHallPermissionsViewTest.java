package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC WindowPermissionsPage: members by rank, ranks and their actions, refused actions, online non-members. */
class TownHallPermissionsViewTest {
    private final TownHallFixture f = new TownHallFixture();

    private TownHallView.Permissions view(UUID player) {
        return f.townHallView(player).permissions();
    }

    @Test
    void membersAreSortedByRank() {
        UUID dave = UUID.randomUUID();
        UUID zed = UUID.randomUUID();
        f.colony.permissions().addPlayer(dave, "Dave", Permissions.HOSTILE);
        f.colony.permissions().addPlayer(zed, "Zed", Permissions.OFFICER); // joined last, ranks before Carol
        assertEquals(
                List.of("Alice", "Zed", "Carol", "Dave"),
                view(f.alice).members().stream()
                        .map(TownHallView.MemberRow::name)
                        .toList());
        assertEquals(Permissions.FRIEND, view(f.alice).members().get(2).rankId());
    }

    @Test
    void ranksCarryTheirActionsAndWhatTheViewerMayAlter() {
        TownHallView.RankRow owner = view(f.alice).ranks().getFirst();
        TownHallView.RankRow friend = view(f.alice).ranks().get(Permissions.FRIEND);
        assertEquals(Action.values().length, friend.actions().size());
        TownHallView.ActionState place = friend.actions().get(Action.PLACE_BLOCKS.ordinal());
        assertFalse(place.on());
        assertTrue(place.alterable());
        TownHallView.ActionState ownEdit = owner.actions().get(Action.EDIT_PERMISSIONS.ordinal());
        assertTrue(ownEdit.on());
        assertFalse(ownEdit.alterable(), "no rank takes EDIT_PERMISSIONS from itself");
        assertFalse(view(f.carol)
                .ranks()
                .get(Permissions.FRIEND)
                .actions()
                .getFirst()
                .alterable());
    }

    @Test
    void onlyAnEditorMayEdit() {
        assertTrue(view(f.alice).canEdit());
        assertFalse(view(f.carol).canEdit());
    }

    @Test
    void refusedActionsAreListedNewestFirst() {
        PermissionEvents events = f.colony.permissions().events();
        events.add(new PermissionEvents.Event(Optional.empty(), "Old", Action.BREAK_BLOCKS, new BlockPos(1, 2, 3)));
        events.add(new PermissionEvents.Event(Optional.empty(), "New", Action.PLACE_BLOCKS, new BlockPos(4, 5, 6)));
        assertEquals(
                List.of("New", "Old"),
                view(f.alice).refusals().stream()
                        .map(PermissionEvents.Event::name)
                        .toList());
    }

    @Test
    void onlinePlayersWhoAreNotMembersAreOffered() {
        UUID ed = UUID.randomUUID();
        f.t.players.online.put(ed, new BlockPos(0, 64, 0));
        f.t.players.online.put(f.carol, new BlockPos(0, 64, 0));
        f.t.players.names.put(ed, "Ed");
        f.t.players.names.put(f.carol, "Carol");
        assertEquals(
                List.of("Ed"),
                view(f.alice).online().stream().map(p -> p.name()).toList());
    }

    @Test
    void theNeutralRankShowsEditPermissionsOffWhateverItsFlag() {
        f.colony.permissions().ranks().get(Permissions.NEUTRAL).add(Action.EDIT_PERMISSIONS);
        TownHallView.RankRow neutral = view(f.alice).ranks().stream()
                .filter(r -> r.id() == Permissions.NEUTRAL)
                .findFirst()
                .orElseThrow();
        assertFalse(neutral.actions().get(Action.EDIT_PERMISSIONS.ordinal()).on());
    }

    @Test
    void ranksKeepTheirInsertionOrderWhenAnIdIsReusedAsMc() {
        Permissions p = f.colony.permissions();
        int a = p.addRank("A").id();
        p.addRank("B");
        p.removeRank(a);
        assertEquals(a, p.addRank("C").id(), "MC reuses the lowest free id");
        List<String> names =
                view(f.alice).ranks().stream().map(TownHallView.RankRow::name).toList();
        assertEquals(List.of("B", "C"), names.subList(names.size() - 2, names.size()));
    }
}
