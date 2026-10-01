package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WindowKey;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.Rank;
import dev.hycolony.core.colony.permission.RankType;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The town hall's Permissions tab buttons (MC PermissionsMessage): members and ranks, EDIT_PERMISSIONS unless MC
 * says otherwise. Each change shows the town hall again; a missing right is refused silently, as MC's
 * PermissionsMessage (not an AbstractColonyServerMessage) does.
 */
public final class PermissionActions {
    /** MC layoutpermissions.xml: the player and rank name fields take 32 characters (maxlength). */
    static final int MAX_NAME_LENGTH = 32;

    private final ColonyManager manager;

    public PermissionActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * MC AddPlayer: the player named {@code name}, once found (online or in the game's profiles), joins as neutral;
     * false without the right or the colony, or for an empty name or one over {@link #MAX_NAME_LENGTH}; true once
     * the lookup is asked.
     *
     * <p>Deviation from MC: MC reads the server's local profile cache; Hytale has none, so the lookup asks Hytale's
     * profile service and answers later (PlayerDirectory.findByName).
     */
    public boolean addPlayer(UUID actor, int colonyId, String name) {
        Optional<Colony> c = editable(actor, colonyId);
        if (c.isEmpty() || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        manager.context().players().findByName(name, found -> found.ifPresent(p -> added(actor, colonyId, p)));
        return true;
    }

    /**
     * The late answer of {@link #addPlayer}: the colony and the actor's right are checked again, and the town hall
     * shows again only if the actor still has it open.
     */
    private void added(UUID actor, int colonyId, PlayerDirectory.Profile p) {
        manager.byId(colonyId)
                .filter(c -> ColonyAccess.allows(c, actor, Action.EDIT_PERMISSIONS))
                .filter(c -> c.permissions().addPlayer(p.id(), p.name(), Permissions.NEUTRAL))
                .ifPresent(c -> {
                    c.markDirty();
                    if (manager.windows().ui().isShowing(actor, new WindowKey.TownHall(colonyId))) {
                        manager.windows().showTownHall(c, actor);
                    }
                });
    }

    /**
     * MC AddPlayerOrFakePlayer: a refused player of the events list joins as neutral, their events dropped.
     *
     * <p>Deviation from MC: never the owner (Permissions.addPlayer), whom MC would make neutral.
     */
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
            return false;
        }
        return changed(c, actor, p.removePlayer(target));
    }

    /**
     * MC ChangePlayerRank: a member's rank, never to owner.
     *
     * <p>Deviation from MC: the owner's own rank never changes (Permissions.setRank); only MC's window hides it.
     */
    public boolean setRank(UUID actor, int colonyId, UUID target, int rankId) {
        return edit(actor, colonyId, p -> {
            Permissions.Member m = p.members().get(target);
            return m != null && p.setRank(target, m.name(), rankId);
        });
    }

    /**
     * MC AddRank: the window sends only a non-empty name no rank but the owner's has yet
     * (WindowPermissionsPage.isValidRankname); false otherwise or over {@link #MAX_NAME_LENGTH}.
     */
    public boolean addRank(UUID actor, int colonyId, String name) {
        return edit(actor, colonyId, p -> {
            if (name.isEmpty()
                    || name.length() > MAX_NAME_LENGTH
                    || p.ranks().values().stream()
                            .filter(r -> r.id() != Permissions.OWNER)
                            .map(Rank::name)
                            .anyMatch(name::equals)) {
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

    /** The colony if {@code actor} may edit its permissions; empty otherwise. */
    private Optional<Colony> editable(UUID actor, int colonyId) {
        Optional<Colony> c = manager.byId(colonyId);
        if (c.isPresent() && !ColonyAccess.allows(c.get(), actor, Action.EDIT_PERMISSIONS)) {
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
