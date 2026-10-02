package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * MC AbstractJob.onRemoval: a citizen losing its job puts its armour back in its inventory
 * (InventoryCitizen.moveArmorToInventory, a piece that does not fit staying worn) and its body's hands go empty
 * (setItemSlot MAINHAND and OFFHAND to nothing). MC's moveArmorToInventory does nothing for a hand, so the slots its
 * hands hold stay, as MC. MC's entity draws its armour from the InventoryCitizen (EntityCitizen.getItemBySlot), so the
 * body here is shown what stays worn ({@link HeldItems#showArmor}).
 */
public final class EquipmentReturn {
    private EquipmentReturn() {}

    /** After {@code d}'s job is removed: its armour put away, its body showing empty hands and what stays worn. */
    public static void onJobRemoved(Colony c, CitizenData d) {
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
        c.citizens().bodyOf(d.id()).ifPresent(b -> {
            c.context().bodies().setHeldItem(b, Optional.empty());
            HeldItems.showArmor(d, c.context().bodies(), b);
        });
    }
}
