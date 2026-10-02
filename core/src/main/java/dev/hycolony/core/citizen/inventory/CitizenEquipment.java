package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.item.Inventory;

/**
 * A citizen's armour and hands (MC InventoryCitizen's armorInventory, mainItem and offhandItem): four armour slots in
 * {@link dev.hycolony.core.kernel.item.ArmorInfo.Slot} order, and the slot of its inventory each hand holds. A hand
 * pointing at a slot since emptied stays, as MC: it holds nothing while the slot is empty.
 */
public final class CitizenEquipment {
    /** MC InventoryCitizen.NO_SLOT: a hand holding nothing. */
    public static final int NO_SLOT = -1;
    /** MC: helmet, chestplate, leggings, boots; Hytale: head, chest, hands, legs. */
    public static final int ARMOR_SLOTS = 4;

    /** MC InteractionHand. */
    public enum Hand {
        MAIN,
        OFF
    }

    private final Inventory armor;
    private int main = NO_SLOT;
    private int off = NO_SLOT;

    public CitizenEquipment() {
        this(new Inventory(ARMOR_SLOTS));
    }

    /** With saved {@code armor} of {@link #ARMOR_SLOTS} slots. */
    public CitizenEquipment(Inventory armor) {
        if (armor.size() != ARMOR_SLOTS) {
            throw new IllegalArgumentException("armour has " + ARMOR_SLOTS + " slots: " + armor.size());
        }
        this.armor = armor;
    }

    public Inventory armor() {
        return armor;
    }

    /** The inventory slot {@code hand} holds; {@link #NO_SLOT} for none (MC getHeldItemSlot). */
    public int held(Hand hand) {
        return hand == Hand.MAIN ? main : off;
    }

    /**
     * MC setHeldItem(hand, slot): {@code hand} holds inventory {@code slot}, or nothing with {@link #NO_SLOT}; a slot
     * outside the citizen's inventory is refused.
     *
     * <p>Deviation from MC: MC's setHeldItem has no {@code else}, so setting the main hand also sets the off hand (a
     * bug); here each hand is set alone.
     */
    public void hold(Hand hand, int slot) {
        if (slot < NO_SLOT || slot >= CitizenData.INVENTORY_SLOTS) {
            throw new IllegalArgumentException("no inventory slot " + slot);
        }
        if (hand == Hand.MAIN) {
            main = slot;
        } else {
            off = slot;
        }
    }

    /** Both hands empty. */
    public void clearHands() {
        main = NO_SLOT;
        off = NO_SLOT;
    }
}
