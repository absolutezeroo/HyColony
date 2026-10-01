package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import java.util.UUID;

/** The citizen window's + and - skill buttons (MC MainWindowCitizen and AdjustSkillCitizenMessage). */
public final class CitizenSkillActions {
    private final ColonyManager manager;

    public CitizenSkillActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * MC AdjustSkillCitizenMessage: a player in creative mode with MANAGE_HUTS (MC AbstractColonyServerMessage's
     * default) moves the citizen's {@code skill} by {@code delta} levels, kept between 1 and the maximum (MC
     * CitizenSkillHandler.incrementLevel); the colony is saved and the window shown again. False, changing nothing,
     * for an unknown colony or citizen, outside creative mode or without the right.
     *
     * <p>Deviation from MC: the citizen's body need not be loaded; MC returns without its entity, here the skills live
     * in the core.
     */
    public boolean adjust(UUID player, int colonyId, int citizenId, Skill skill, int delta) {
        Colony c = manager.byId(colonyId).orElse(null);
        if (c == null) {
            return false;
        }
        CitizenData d = c.citizens().get(citizenId).orElse(null);
        if (d == null
                || !manager.context().players().isCreative(player)
                || !ColonyAccess.allows(c, player, Action.MANAGE_HUTS)) {
            return false;
        }
        d.skills().set(skill, d.skills().level(skill) + delta, d.skills().experience(skill));
        c.markDirty();
        manager.windows().openCitizen(player, colonyId, citizenId);
        return true;
    }
}
