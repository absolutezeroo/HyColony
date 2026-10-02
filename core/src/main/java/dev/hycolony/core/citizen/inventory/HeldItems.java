package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a citizen holds and wears, kept in its {@link CitizenEquipment} and shown on its body (MC
 * CitizenItemUtils.setHeldItem(hand, slot), and the entity rendering its armour and held items).
 */
public final class HeldItems {
    private HeldItems() {}

    /** MC setHeldItem(MAIN_HAND, slot): the main hand holds inventory {@code slot}, the body shows its item. */
    public static void holdSlot(CitizenData d, CitizenBodies bodies, BodyId body, int slot) {
        d.equipment().hold(Hand.MAIN, slot);
        bodies.setHeldItem(body, d.inventory().slot(slot).map(ItemAmount::item));
    }

    /**
     * The main hand holds {@code item}: its first inventory slot, else no slot.
     *
     * <p>Deviation from MC: an item out of the inventory (a block the builder has just placed, a crafter's ingredient
     * in its hut) is still shown in hand, without a slot; MC only ever holds inventory slots.
     */
    public static void holdItem(CitizenData d, CitizenBodies bodies, BodyId body, Optional<ItemKey> item) {
        int slot = item.map(k -> firstSlot(d.inventory(), k)).orElse(CitizenEquipment.NO_SLOT);
        d.equipment().hold(Hand.MAIN, slot);
        bodies.setHeldItem(body, item);
    }

    /** Both hands empty, and the body's too (MC setHeldItem to an empty slot). */
    public static void clear(CitizenData d, CitizenBodies bodies, BodyId body) {
        d.equipment().clearHands();
        bodies.setHeldItem(body, Optional.empty());
    }

    /** A body bound to {@code d} shows its armour and the item of its main hand's slot; nothing for an empty slot. */
    public static void show(CitizenData d, CitizenBodies bodies, BodyId body) {
        int slot = d.equipment().held(Hand.MAIN);
        bodies.setHeldItem(
                body,
                slot == CitizenEquipment.NO_SLOT
                        ? Optional.empty()
                        : d.inventory().slot(slot).map(ItemAmount::item));
        showArmor(d, bodies, body);
    }

    /** The body wears the armour {@code d} wears, each piece with its wear. */
    public static void showArmor(CitizenData d, CitizenBodies bodies, BodyId body) {
        Inventory armor = d.equipment().armor();
        List<Optional<ItemAmount>> pieces = new ArrayList<>(armor.size());
        for (int i = 0; i < armor.size(); i++) {
            pieces.add(armor.slot(i));
        }
        bodies.setArmor(body, pieces);
    }

    private static int firstSlot(Inventory inventory, ItemKey item) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.slot(i).filter(a -> a.item().equals(item)).isPresent()) {
                return i;
            }
        }
        return CitizenEquipment.NO_SLOT;
    }
}
