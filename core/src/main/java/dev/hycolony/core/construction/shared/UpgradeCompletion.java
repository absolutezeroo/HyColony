package dev.hycolony.core.construction.shared;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;

/**
 * A building reaching a level, shared by a finished work order and a creative paste (MC
 * AbstractBuilding.onUpgradeComplete, plus the fireworks of AbstractSchematicProvider.upgradeBuildingLevelToSchematicData).
 */
public final class UpgradeCompletion {
    private UpgradeCompletion() {}

    /**
     * Sets the level, marks the building built and not deconstructed, claims around it, celebrates when the level
     * rose, posts {@link ColonyEvents.BuildingLevelChanged} and marks the colony dirty.
     */
    public static void reach(Colony colony, Building b, int level) {
        int oldLevel = b.level();
        b.setLevel(level);
        b.setBuilt(true);
        b.setDeconstructed(false);
        colony.claimAround(b.position(), ClaimRadius.of(b.type().id(), level));
        if (level > oldLevel) {
            colony.context().ports().effects().celebrate(b.position());
        }
        colony.context().bus().post(new ColonyEvents.BuildingLevelChanged(colony, b, oldLevel, level));
        colony.markDirty();
    }
}
