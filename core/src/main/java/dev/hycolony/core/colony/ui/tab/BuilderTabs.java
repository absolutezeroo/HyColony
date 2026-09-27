package dev.hycolony.core.colony.ui.tab;

import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.List;
import java.util.Optional;

/**
 * The builder hut's own tabs, in MC module order: Resources (BUILDING_RESOURCES), Settings (BUILDER_SETTINGS) and
 * Work orders (WORKORDER_VIEW).
 */
public record BuilderTabs(BuilderResourcesView resources, BuilderSettingsModule.Mode mode, List<OrderLine> orders)
        implements ModuleTab {
    /**
     * A WorkOrderModuleWindow row: {@code distance} in blocks (|dx| + |dz|), {@code current} the order the builder
     * works on (green frame), {@code claimedHere} shows Cancel; otherwise, in MANUAL mode, Select is enabled when
     * {@code selectRefusal} is empty.
     */
    public record OrderLine(
            int id,
            WorkOrderType type,
            String buildingName,
            int targetLevel,
            long distance,
            boolean current,
            boolean claimedHere,
            Optional<ManualSelection.Refusal> selectRefusal) {}

    public BuilderTabs {
        orders = List.copyOf(orders);
    }
}
