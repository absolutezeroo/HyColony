package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.construction.hut.WorkOrderListView;
import dev.hycolony.core.construction.resources.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.construction.resources.BuilderResourcesView.Status;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The rules the plugin's hut tabs follow, decided by the core (MC's module windows). */
class HutTabRulesTest {
    @Test
    void hiringButtonCyclesThroughEveryModeInOrder() {
        List<HiringMode> seen =
                Arrays.stream(HiringMode.values()).map(HiringMode::next).toList();

        assertEquals(List.of(HiringMode.AUTO, HiringMode.MANUAL, HiringMode.LOCKED, HiringMode.DEFAULT), seen);
    }

    @Test
    void builderModeButtonSwitchesAutomaticAndManual() {
        assertEquals(Mode.MANUAL, Mode.AUTO.next());
        assertEquals(Mode.AUTO, Mode.MANUAL.next());
    }

    @Test
    void resourceRowCanBeAddedOnlyWhenThePlayerHasSomeOfWhatIsMissing() {
        assertTrue(Status.NEED_MORE.canAdd());
        assertTrue(Status.HAVE_ENOUGH.canAdd());
        assertFalse(Status.DONT_HAVE.canAdd());
        assertFalse(Status.NOT_NEEDED.canAdd());
        assertEquals(7, new ResourceRow(new ItemKey("Rock_Stone"), 10, 3, 20, Status.HAVE_ENOUGH).missing());
    }

    @Test
    void automaticBuilderOnlyOffersToCancelItsOwnOrder() {
        WorkOrderListView.OrderLine own = line(true);
        WorkOrderListView.OrderLine other = line(false);
        WorkOrderListView auto = new WorkOrderListView(List.of(own, other), false);
        WorkOrderListView manual = new WorkOrderListView(List.of(own, other), true);

        assertTrue(auto.selectable(own));
        assertFalse(auto.selectable(other));
        assertTrue(manual.selectable(other));
    }

    private static WorkOrderListView.OrderLine line(boolean claimedHere) {
        return new WorkOrderListView.OrderLine(
                1, WorkOrderType.BUILD, "hut", 1, 0, false, claimedHere, Optional.empty());
    }
}
