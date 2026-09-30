package dev.hycolony.core.construction.shared;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import java.util.Optional;
import java.util.UUID;

/**
 * A building reaching a level, shared by a finished work order and a creative paste (MC
 * AbstractBuilding.onUpgradeComplete, plus the fireworks of AbstractSchematicProvider.upgradeBuildingLevelToSchematicData).
 */
public final class UpgradeCompletion {
    private UpgradeCompletion() {}

    /**
     * Sets the level, marks the building built and not deconstructed, claims around it, celebrates when the level
     * rose, tells the building's event modules when it rose or the building was deconstructed, posts
     * {@link ColonyEvents.BuildingLevelChanged} caused by {@code player} (empty for a builder) and marks the colony
     * dirty.
     */
    public static void reach(Colony colony, Building b, int level, Optional<UUID> player) {
        int oldLevel = b.level();
        // MC upgradeBuildingLevelToSchematicData: modules hear only of a rise or a rebuild, never of a repair.
        boolean upgraded = level > oldLevel || b.isDeconstructed();
        b.setLevel(level);
        b.setBuilt(true);
        b.setDeconstructed(false);
        colony.claimAround(b.position(), ClaimRadius.of(b.type().id(), level));
        if (level > oldLevel) {
            colony.context().ports().effects().celebrate(b.position());
        }
        if (upgraded) {
            for (BuildingModule module : b.modules().values()) {
                if (module instanceof BuildingEventsModule events) {
                    events.onUpgradeComplete(colony, b, level);
                }
            }
        }
        colony.context().bus().post(new ColonyEvents.BuildingLevelChanged(colony, b, oldLevel, level, player));
        colony.markDirty();
    }
}
