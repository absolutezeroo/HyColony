package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/** What a colony's managers change about the colony itself: its name, its members' ranks, its existence. */
public final class ColonyAdministration {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public ColonyAdministration(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /** EDIT_PERMISSIONS; see {@link dev.hycolony.core.colony.permission.Permissions#setRank}. */
    public boolean setRank(UUID actor, int colonyId, UUID target, String targetName, int rankId) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !ColonyAccess.allows(c, actor, Action.EDIT_PERMISSIONS)) {
            return false;
        }
        boolean changed = c.permissions().setRank(target, targetName, rankId);
        if (changed) {
            c.markDirty();
        }
        return changed;
    }

    /**
     * Deletes the colony if {@code actor} is an operator or has a colony manager's rank; false otherwise or if the
     * colony is unknown or could not be archived. MC CommandDeleteColony, IMCColonyOfficerCommand.checkPreCondition.
     */
    public boolean delete(UUID actor, int colonyId) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !ColonyAccess.isOfficer(c, actor)) {
            return false;
        }
        return manager.deleteColony(colonyId, actor);
    }

    /** MC TownHallRenameMessage.MAX_NAME_LENGTH: a longer new name is cut to {@link #RENAME_CUT_LENGTH}. */
    static final int RENAME_MAX_LENGTH = 25;

    /** MC TownHallRenameMessage.SUBSTRING_LENGTH. */
    static final int RENAME_CUT_LENGTH = RENAME_MAX_LENGTH - 1;

    /**
     * MC TownHallRenameMessage (permissionNeeded MANAGE_HUTS, {@link ColonyAccess}): renames the colony, a name over
     * 25 characters cut to its first 24; the town hall window is shown again. False without the right or for a blank
     * name.
     *
     * <p>Deviation from MC: a blank name is refused (MC would accept it and show an empty title).
     */
    public boolean rename(UUID actor, int colonyId, String rawName) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !ColonyAccess.allows(c, actor, Action.MANAGE_HUTS)) {
            return false;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return false;
        }
        c.setName(name.length() <= RENAME_MAX_LENGTH ? name : name.substring(0, RENAME_CUT_LENGTH));
        windows.showTownHall(c, actor);
        return true;
    }

    /** Trims {@code raw}; empty if blank or over {@link ColonyManager#MAX_NAME_LENGTH}, after telling {@code actor}. */
    public static Optional<String> validName(ColonyContext ctx, UUID actor, String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > ColonyManager.MAX_NAME_LENGTH) {
            ctx.notifier()
                    .send(actor, Msg.of("hycolony.colony.invalidName", String.valueOf(ColonyManager.MAX_NAME_LENGTH)));
            return Optional.empty();
        }
        return Optional.of(name);
    }
}
