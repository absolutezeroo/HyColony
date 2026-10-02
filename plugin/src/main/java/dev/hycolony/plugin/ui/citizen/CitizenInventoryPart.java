package dev.hycolony.plugin.ui.citizen;

import dev.hycolony.core.app.action.CitizenInventoryActions;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemKey;

/**
 * The two parts of a citizen's inventory its window shows (MC ContainerCitizenInventory): its 27 slots and its 4
 * armour slots; where each lives in the core, what a player may put there and whom a change is told to.
 */
enum CitizenInventoryPart {
    MAIN,
    ARMOR;

    /** This part of {@code citizen}'s inventory in the core. */
    Inventory of(CitizenData citizen) {
        return this == MAIN ? citizen.inventory() : citizen.equipment().armor();
    }

    /** Whether {@code item} may go in {@code slot}: anything in the 27 slots, the armour MC allows in the others. */
    boolean accepts(CitizenInventoryActions actions, int colonyId, CitizenData citizen, short slot, ItemKey item) {
        return this == MAIN || actions.mayWear(colonyId, citizen.id(), slot, item);
    }

    /** Tells the core a player's move changed this part from {@code before} (requests, body, save). */
    void report(CitizenInventoryActions actions, int colonyId, CitizenData citizen, Inventory before) {
        if (this == MAIN) {
            actions.onPlayerEdit(colonyId, citizen.id(), before);
        } else {
            actions.onArmorEdit(colonyId, citizen.id(), before);
        }
    }
}
