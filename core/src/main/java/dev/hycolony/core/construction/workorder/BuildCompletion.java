package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.ClaimRadius;
import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/** The building side of a finished order (MC executeSpecificCompleteActions + sendCompletionMessage). */
public final class BuildCompletion {
    private BuildCompletion() {}

    /**
     * BUILD/UPGRADE/REPAIR: target level, built, claims. REMOVE: deconstructed, level kept. Then log, members'
     * message, {@link ColonyEvents.BuildingLevelChanged}, and the order leaves the work manager.
     */
    public static void apply(Colony colony, WorkOrder o, Building b) {
        int oldLevel = b.level();
        String logType;
        if (o.type() == WorkOrderType.REMOVE) {
            b.setDeconstructed(true);
            logType = "buildingDeconstructed";
        } else {
            b.setLevel(o.targetLevel());
            b.setBuilt(true);
            b.setDeconstructed(false);
            colony.claimAround(b.position(), ClaimRadius.of(b.type().id(), b.level()));
            logType = switch (o.type()) {
                case BUILD -> "buildingBuilt";
                case UPGRADE -> "buildingUpgraded";
                default -> "buildingRepaired";
            };
        }
        colony.log().add(logType, colony.day(), b.type().id(), String.valueOf(b.level()));
        Msg done = Msg.of("hycolony.build.complete", b.displayName(), String.valueOf(b.level()));
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, done);
            }
        }
        colony.context().bus().post(new ColonyEvents.BuildingLevelChanged(colony, b, oldLevel, b.level()));
        colony.work().complete(o);
        colony.markDirty();
    }
}
