package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.List;

/**
 * Moves food from a hut's containers into a citizen's inventory (MC InventoryUtils.transfer…IntoNextBestSlot).
 * Deviation from MC: the amount may come from several containers and part of it may land, where MC takes one slot and
 * only when all of it fits; the containers port works on totals.
 */
final class FoodTransfer {
    private FoodTransfer() {}

    /**
     * Moves up to {@code max} of {@code food} from {@code hut} into {@code inventory}; what does not fit goes back.
     * Returns how many landed; 0 when the hut holds none or its chunk is not loaded.
     */
    static int take(Colony colony, Building hut, Inventory inventory, ItemKey food, int max) {
        ContainerAccess containers = colony.context().ports().containers();
        List<BlockPos> hc = hut.containers();
        int landed = 0;
        for (ItemAmount got : containers.extractStacks(hc, food, max)) {
            ItemAmount rest = inventory.insert(got, colony.context().ports().catalog()::maxStack);
            landed += got.count() - (rest == null ? 0 : rest.count());
            if (rest != null) {
                containers.insert(hc, rest); // back where it came from, which had room for it
            }
        }
        return landed;
    }

    /** MC transferItemStackIntoNextBestSlotInItemHandler(building, storage, inv): one full stack of {@code food}. */
    static boolean takeStack(Colony colony, Building hut, Inventory inventory, ItemKey food) {
        return take(
                        colony,
                        hut,
                        inventory,
                        food,
                        colony.context().ports().catalog().maxStack(food))
                > 0;
    }
}
