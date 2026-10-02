package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The slots a citizen holds, kept in its {@link CitizenEquipment}, and the armour its body wears. As MC, the held slot
 * (InventoryCitizen.setHeldItem) and what the body shows in hand (the entity's own hand, setItemInHand) are apart: a
 * gesture that only shows an item, or empties the hand, calls {@link CitizenBodies#setHeldItem} and keeps the slot.
 */
public final class HeldItems {
    private HeldItems() {}

    /** MC CitizenItemUtils.setHeldItem(MAIN_HAND, slot): the main hand holds inventory {@code slot}, shown in hand. */
    public static void holdSlot(CitizenData d, CitizenBodies bodies, BodyId body, int slot) {
        d.equipment().hold(Hand.MAIN, slot);
        bodies.setHeldItem(body, d.inventory().slot(slot).map(ItemAmount::item));
    }

    /**
     * MC AbstractEntityAIBasic.dumpOneMoreSlot: a hand holding {@code slot}, whose stack was just stored, holds no
     * slot any more, and the body's main hand goes empty when it was that hand.
     */
    public static void release(CitizenData d, CitizenBodies bodies, Optional<BodyId> body, int slot) {
        for (Hand hand : Hand.values()) {
            if (d.equipment().held(hand) == slot) {
                d.equipment().hold(hand, CitizenEquipment.NO_SLOT);
                if (hand == Hand.MAIN) {
                    body.ifPresent(b -> bodies.setHeldItem(b, Optional.empty()));
                }
            }
        }
    }

    /**
     * The body wears the armour {@code d} wears, each piece with its wear (MC EntityCitizen.getItemBySlot draws the
     * armour from the InventoryCitizen).
     */
    public static void showArmor(CitizenData d, CitizenBodies bodies, BodyId body) {
        Inventory armor = d.equipment().armor();
        List<Optional<ItemAmount>> pieces = new ArrayList<>(armor.size());
        for (int i = 0; i < armor.size(); i++) {
            pieces.add(armor.slot(i));
        }
        bodies.setArmor(body, pieces);
    }
}
