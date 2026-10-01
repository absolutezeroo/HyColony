package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.ColonySettings;
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
     * MC TownHallRenameMessage (permissionNeeded MANAGE_HUTS, {@link ColonyAccess}): renames the colony as typed, a
     * name over 25 characters cut to its first 24; the town hall window is shown again. False without the right (the
     * player is told) or for an empty name, which MC's WindowTownHallNameEntry never sends.
     */
    public boolean rename(UUID actor, int colonyId, String rawName) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !allowed(c, actor)) {
            return false;
        }
        String name = rawName == null ? "" : rawName;
        if (name.isEmpty()) {
            return false;
        }
        c.setName(name.length() <= RENAME_MAX_LENGTH ? name : name.substring(0, RENAME_CUT_LENGTH));
        windows.showTownHall(c, actor);
        return true;
    }

    /**
     * MC ColonyStructureStyleMessage (MANAGE_HUTS): sets the colony's style, the one new huts take; false without the
     * right (the player is told) or for a style the blueprints do not offer.
     *
     * <p>Deviation from MC: MC stores any name it is sent; a style no blueprint has would leave new huts planless.
     */
    public boolean setStyle(UUID actor, int colonyId, String style) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !allowed(c, actor)) {
            return false;
        }
        if (!manager.context().ports().blueprints().styles().contains(style)) {
            windows.showTownHall(c, actor); // the dropdown shows the colony's style again
            return false;
        }
        c.settings().setStyle(style);
        c.markDirty();
        windows.showTownHall(c, actor);
        return true;
    }

    /**
     * MC TriggerSettingMessage (MANAGE_HUTS): turns a town hall switch over, then shows the town hall again; false
     * without the right (the player is told).
     */
    public boolean toggle(UUID actor, int colonyId, ColonySettings.Toggle toggle) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null || !allowed(c, actor)) {
            return false;
        }
        c.settings().flip(toggle);
        c.markDirty();
        windows.showTownHall(c, actor);
        return true;
    }

    /** MANAGE_HUTS (MC AbstractColonyServerMessage), else the player is told, as MC does. */
    private static boolean allowed(Colony c, UUID actor) {
        if (ColonyAccess.allows(c, actor, Action.MANAGE_HUTS)) {
            return true;
        }
        ColonyRefusal.tellNoPermission(c, actor);
        return false;
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
