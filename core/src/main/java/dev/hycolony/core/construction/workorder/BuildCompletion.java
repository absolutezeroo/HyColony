package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.shared.ClaimRadius;
import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/**
 * The building side of a finished order (MC executeSpecificCompleteActions + sendCompletionMessage). Fireworks only when
 * the level rises, as MC AbstractSchematicProvider.upgradeBuildingLevelToSchematicData: never on REPAIR nor REMOVE.
 */
final class BuildCompletion {
    private BuildCompletion() {}

    /**
     * BUILD/UPGRADE/REPAIR: target level, built, claims. REMOVE: deconstructed, level kept. Then log, fireworks if the
     * level rose, members' message, {@link ColonyEvents.BuildingLevelChanged}, and the order leaves the work manager.
     */
    static void apply(Colony colony, WorkOrder o, Building b) {
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
        if (b.level() > oldLevel) {
            colony.context().ports().effects().celebrate(b.position());
        }
        Msg done = completionMessage(o.type(), b);
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, done);
            }
        }
        colony.context().bus().post(new ColonyEvents.BuildingLevelChanged(colony, b, oldLevel, b.level()));
        colony.work().complete(o);
        colony.markDirty();
    }

    /**
     * MC EntityAIStructureBuilder.sendCompletionMessage: one key per {@code WorkOrderType.getCompletionMessageID}, the
     * building's translated name (or its custom name). Deviation from MC: the new level replaces the direction from
     * the colony centre, and no REMOVE hint about the "Pick Up" button (HyColony has none).
     */
    private static Msg completionMessage(WorkOrderType type, Building b) {
        String name = b.displayName().startsWith("hycolony:")
                ? "%hycolony.ui.building.type." + b.displayName().substring("hycolony:".length())
                : b.displayName();
        String level = String.valueOf(b.level());
        return switch (type) {
            case REMOVE -> Msg.of("hycolony.build.removeComplete", name);
            case REPAIR -> Msg.of("hycolony.build.repairComplete", name, level);
            default -> Msg.of("hycolony.build.complete", name, level);
        };
    }
}
