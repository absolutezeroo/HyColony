package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * MC AbstractJob.onRemoval: a citizen losing its job empties its hands and puts its armour back in its inventory
 * (InventoryCitizen.moveArmorToInventory); a piece that does not fit stays worn, as MC.
 */
public final class EquipmentReturn {
    private EquipmentReturn() {}

    /** After {@code d}'s job is removed: its hands empty, its armour in its inventory, its body showing both. */
    public static void onJobRemoved(Colony c, CitizenData d) {
        d.equipment().clearHands();
        Inventory armor = d.equipment().armor();
        for (int slot = 0; slot < armor.size(); slot++) {
            ItemAmount piece = armor.slot(slot).orElse(null);
            if (piece != null) {
                @Nullable
                ItemAmount rest =
                        d.inventory().insert(piece, c.context().ports().catalog()::maxStack);
                armor.set(slot, Optional.ofNullable(rest));
            }
        }
        c.markDirty();
        c.citizens().bodyOf(d.id()).ifPresent(b -> HeldItems.show(d, c.context().bodies(), b));
    }
}
