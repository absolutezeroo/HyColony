package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.Rank;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the town hall's Permissions tab (MC WindowPermissionsPage): members, ranks, refusals, online players. */
final class PermissionViews {
    private PermissionViews() {}

    static TownHallView.Permissions of(Colony c, ColonyContext ctx, UUID viewer) {
        Permissions p = c.permissions();
        Rank viewerRank = p.rankOf(viewer);
        List<TownHallView.RankRow> ranks = p.ranks().values().stream()
                .sorted(Comparator.comparingInt(Rank::id))
                .map(r -> rank(p, viewerRank, r))
                .toList();
        List<PermissionEvents.Event> refusals = new ArrayList<>(p.events().entries());
        Collections.reverse(refusals); // MC fillEventsList: newest first
        return new TownHallView.Permissions(
                ColonyAccess.allows(c, viewer, Action.EDIT_PERMISSIONS), members(p), ranks, refusals, online(p, ctx));
    }

    /** MC updateUsers: the members by rank id (Rank.compareTo), in the order they joined within a rank. */
    private static List<TownHallView.MemberRow> members(Permissions p) {
        return p.members().entrySet().stream()
                .map(e -> member(p, e))
                .sorted(Comparator.comparingInt(TownHallView.MemberRow::rankId))
                .toList();
    }

    private static TownHallView.MemberRow member(Permissions p, Map.Entry<UUID, Permissions.Member> e) {
        Rank rank = p.rankOf(e.getKey());
        return new TownHallView.MemberRow(e.getKey(), e.getValue().name(), rank.id(), rank.name());
    }

    /** MC fillPermissionList: every action, set or not, and whether the viewer's rank may alter it. */
    private static TownHallView.RankRow rank(Permissions p, Rank viewerRank, Rank r) {
        List<TownHallView.ActionState> actions = Arrays.stream(Action.values())
                .map(a ->
                        new TownHallView.ActionState(a, p.hasPermission(r, a), p.canAlterPermission(viewerRank, r, a)))
                .toList();
        return new TownHallView.RankRow(r.id(), r.name(), r.isInitial(), r.type(), actions);
    }

    /** MC playerPicker: the players online in the colony's world who are not members yet. */
    private static List<PlayerDirectory.Profile> online(Permissions p, ColonyContext ctx) {
        return ctx.players().onlineIn(ctx.world()).stream()
                .filter(id -> !p.members().containsKey(id))
                .flatMap(id -> ctx.players().name(id).map(n -> new PlayerDirectory.Profile(id, n)).stream())
                .toList();
    }
}
