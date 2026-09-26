package dev.hycolony.core.colony.ui;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/** The builder hut's resources tab. {@code stage} is the active order's stage in lower case, "" without an order. */
public record BuilderResourcesView(int colonyId, BlockPos hut, List<ResourceRow> rows, int percent, String stage) {
    /** Row colours: red, orange, green, black. */
    public enum Status {
        DONT_HAVE,
        NEED_MORE,
        HAVE_ENOUGH,
        NOT_NEEDED;

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

    public record ResourceRow(ItemKey item, int needed, int available, int playerHas, Status status) {}

    public BuilderResourcesView {
        rows = List.copyOf(rows);
    }
}
