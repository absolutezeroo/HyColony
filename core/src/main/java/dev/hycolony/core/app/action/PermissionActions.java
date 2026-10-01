package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.Rank;
import dev.hycolony.core.colony.permission.RankType;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The town hall's Permissions tab buttons (MC PermissionsMessage): members and ranks, EDIT_PERMISSIONS unless MC
 * says otherwise. Each change shows the town hall again; a missing right is told with MC's refusal message.
 */
public final class PermissionActions {
    private final ColonyManager manager;

    public PermissionActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * MC AddPlayer: the player named {@code name}, once found (online or in the game's profiles), joins as neutral;
     * false without the right or the colony, true once the lookup is asked.
     */
    public boolean addPlayer(UUID actor, int colonyId, String name) {
        Optional<Colony> c = editable(actor, colonyId);
        if (c.isEmpty() || name.isEmpty()) {
            return false;
        }
        manager.context()
                .players()
                .findByName(
                        name,
                        found -> found.ifPresent(p -> manager.byId(colonyId)
                                .ifPresent(col -> changed(
                                        col,
                                        actor,
                                        col.permissions().addPlayer(p.id(), p.name(), Permissions.NEUTRAL)))));
        return true;
    }

    /** MC AddPlayerOrFakePlayer: a refused player of the events list joins as neutral, their events dropped. */
    public boolean addKnownPlayer(UUID actor, int colonyId, UUID player, String name) {
        return edit(actor, colonyId, p -> {
            boolean added = p.addPlayer(player, name, Permissions.NEUTRAL);
            p.events().removeOf(player);
            return added;
        });
    }

    /**
     * MC RemovePlayer: a hostile member by anyone with EDIT_PERMISSIONS, another by a colony manager with it, or
     * oneself; never the owner.
     */
    public boolean removePlayer(UUID actor, int colonyId, UUID target) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null) {
            return false;
        }
        Permissions p = c.permissions();
        boolean edit = p.hasPermission(actor, Action.EDIT_PERMISSIONS);
        boolean hostile = p.rankOf(target).isHostile();
        boolean allowed =
                actor.equals(target) || (edit && (hostile || p.rankOf(actor).isColonyManager()));
        if (!allowed) {
            ColonyRefusal.tellNoPermission(c, actor);
            return false;
        }
        return changed(c, actor, p.removePlayer(target));
    }

    /** MC ChangePlayerRank: a member's rank, never to owner; the owner's own rank never changes (setRank). */
    public boolean setRank(UUID actor, int colonyId, UUID target, int rankId) {
        return edit(actor, colonyId, p -> {
            Permissions.Member m = p.members().get(target);
            return m != null && p.setRank(target, m.name(), rankId);
        });
    }

    /** MC AddRank: the window sends only a non-empty name no rank has yet (WindowPermissionsPage.isValidRankname). */
    public boolean addRank(UUID actor, int colonyId, String name) {
        return edit(actor, colonyId, p -> {
            if (name.isEmpty() || p.ranks().values().stream().map(Rank::name).anyMatch(name::equals)) {
                return false;
            }
            p.addRank(name);
            return true;
        });
    }

    /** MC RemoveRank: a rank that is not initial, its players becoming neutral. */
    public boolean removeRank(UUID actor, int colonyId, int rankId) {
        return edit(actor, colonyId, p -> p.removeRank(rankId));
    }

    /** MC EditRankType. */
    public boolean setRankType(UUID actor, int colonyId, int rankId, RankType type) {
        return edit(actor, colonyId, p -> p.setRankType(rankId, type));
    }

    /** MC PermissionsMessage.Permission: the actor's rank alters the rank's action (Permissions.alterPermission). */
    public boolean alterPermission(UUID actor, int colonyId, int rankId, Action action, boolean enable) {
        Colony c = manager.byId(colonyId).orElse(null);
        Rank rank = c == null ? null : c.permissions().ranks().get(rankId);
        if (c == null || rank == null) {
            return false;
        }
        return changed(c, actor, c.permissions().alterPermission(c.permissions().rankOf(actor), rank, action, enable));
    }

    /** Runs {@code change} on the colony's permissions if {@code actor} has EDIT_PERMISSIONS. */
    private boolean edit(UUID actor, int colonyId, Predicate<Permissions> change) {
        return editable(actor, colonyId)
                .map(c -> changed(c, actor, change.test(c.permissions())))
                .orElse(false);
    }

    /** The colony if {@code actor} may edit its permissions; empty otherwise, the actor told of a missing right. */
    private Optional<Colony> editable(UUID actor, int colonyId) {
        Optional<Colony> c = manager.byId(colonyId);
        if (c.isPresent() && !ColonyAccess.allows(c.get(), actor, Action.EDIT_PERMISSIONS)) {
            ColonyRefusal.tellNoPermission(c.get(), actor);
            return Optional.empty();
        }
        return c;
    }

    /** Marks a changed colony to save and shows the town hall again; returns {@code changed}. */
    private boolean changed(Colony c, UUID actor, boolean changed) {
        if (changed) {
            c.markDirty();
        }
        manager.windows().showTownHall(c, actor);
        return changed;
    }
}
