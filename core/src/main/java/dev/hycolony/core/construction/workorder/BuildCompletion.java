package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.construction.tape.ConstructionTape;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * The building side of a finished order (MC executeSpecificCompleteActions + sendCompletionMessage). Fireworks only
 * when the level rises, as MC AbstractSchematicProvider.upgradeBuildingLevelToSchematicData: never on REPAIR nor
 * REMOVE.
 */
final class BuildCompletion {
    private BuildCompletion() {}

    /**
     * BUILD/UPGRADE/REPAIR: the building reaches the target level ({@link UpgradeCompletion#reach}). REMOVE:
     * deconstructed, level kept, {@link ColonyEvents.BuildingLevelChanged}. Then log, members' message, and the order
     * leaves the work manager.
     */
    static void apply(Colony colony, WorkOrder o, Building b) {
        // MC AbstractBuilding.onUpgradeComplete: the tape of the plan built over comes down before the level changes;
        // the order's own removal then takes the tape around the new level's plan.
        ConstructionTape.remove(colony, b);
        String logType;
        if (o.type() == WorkOrderType.REMOVE) {
            b.setDeconstructed(true);
            colony.context()
                    .bus()
                    .post(new ColonyEvents.BuildingLevelChanged(colony, b, b.level(), b.level(), Optional.empty()));
            logType = "buildingDeconstructed";
        } else {
            UpgradeCompletion.reach(colony, b, o.targetLevel(), Optional.empty());
            logType = switch (o.type()) {
                case BUILD -> "buildingBuilt";
                case UPGRADE -> "buildingUpgraded";
                default -> "buildingRepaired";
            };
        }
        colony.log().addAt(b.position(), logType, colony.day(), b.type().id(), String.valueOf(b.level()));
        Msg done = completionMessage(o.type(), b);
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, done);
            }
        }
        colony.work().complete(o);
        colony.markDirty();
    }

    /**
     * MC EntityAIStructureBuilder.sendCompletionMessage: one key per {@code WorkOrderType.getCompletionMessageID}, the
     * building's translated name (or its custom name). Deviation from MC: the new level replaces the direction from
     * the colony centre.
     */
    private static Msg completionMessage(WorkOrderType type, Building b) {
        String name = nameParam(b);
        String level = String.valueOf(b.level());
        return switch (type) {
            case REMOVE -> Msg.of("hycolony.build.removeComplete", name);
            case REPAIR -> Msg.of("hycolony.build.repairComplete", name, level);
            case BUILD, UPGRADE -> Msg.of("hycolony.build.complete", name, level);
        };
    }

    /** The building's name as a message parameter: its translated type key, or its custom name as is. */
    static String nameParam(Building b) {
        return b.displayName().startsWith("hycolony:")
                ? "%hycolony.ui.building.type." + b.displayName().substring("hycolony:".length())
                : b.displayName();
    }
}
