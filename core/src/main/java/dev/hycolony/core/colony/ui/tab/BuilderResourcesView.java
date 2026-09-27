package dev.hycolony.core.colony.ui.tab;

import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/**
 * The builder hut's Resources tab (MC WindowBuilderResModule): rows in ResourceComparator order, and a header while
 * the hut holds an order.
 */
public record BuilderResourcesView(List<ResourceRow> rows, Optional<Header> header) implements ModuleTab {
    /** MC RessourceAvailability, in its order; the rows are black, red, orange and dark green. */
    public enum Status {
        NOT_NEEDED,
        DONT_HAVE,
        NEED_MORE,
        HAVE_ENOUGH;

        /** BuildingBuilderResource.getAvailabilityStatus (without IN_DELIVERY: no couriers yet). */
        public static Status of(int needed, int available, int playerHas) {
            if (needed <= available) {
                return NOT_NEEDED;
            }
            if (playerHas == 0) {
                return DONT_HAVE;
            }
            return playerHas < needed - available ? NEED_MORE : HAVE_ENOUGH;
        }
    }

    public record ResourceRow(ItemKey item, int needed, int available, int playerHas, Status status) {
        /** BuildingBuilderResource.getMissingFromPlayer: negative when the player's items would not be enough. */
        public int missingFromPlayer() {
            return playerHas + available - needed;
        }
    }

    /**
     * The order's name ({@code type}, building, target level), "step {@code step}/{@code totalSteps}" (finished
     * stages) and "supplied {@code suppliedPercent}% / progress {@code percent}%" (MC progress.step, progress.res).
     */
    public record Header(
            WorkOrderType type,
            String buildingName,
            int targetLevel,
            int step,
            int totalSteps,
            int suppliedPercent,
            int percent) {}

    public BuilderResourcesView {
        rows = List.copyOf(rows);
    }
}
