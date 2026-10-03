package dev.hycolony.core.app.view;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;

/**
 * The side panel of a citizen's inventory page, laid out as Hytale's character panel at the player's request: its name,
 * its health and maximum (MC CitizenDataView.getHealth, getMaxHealth: the entity's own, as the town hall shows them),
 * the defense its armour gives, its saturation (MC ICitizenData.MAX_SATURATION) and the items of the slots its hands
 * hold (MC InventoryCitizen.getHeldItem).
 */
public record CitizenInventoryView(
        String name,
        int health,
        int maxHealth,
        int defensePercent,
        int saturation,
        int maxSaturation,
        Optional<ItemAmount> mainHand,
        Optional<ItemAmount> offHand) {

    /**
     * The panel of {@code d}: without a living body, MC's full 20/20 (MAX_HEALTH without its entity) and no defense;
     * health truncated as MC casts its float health.
     */
    public static CitizenInventoryView of(Colony c, CitizenData d) {
        Optional<BodyId> body = c.citizens().bodyOf(d.id()).filter(c.context().bodies()::isAlive);
        return new CitizenInventoryView(
                d.name(),
                body.map(b -> (int) c.context().health().health(b)).orElse(CitizenData.MC_MAX_HEALTH),
                body.map(b -> (int) c.context().health().maxHealth(b)).orElse(CitizenData.MC_MAX_HEALTH),
                body.map(c.context().bodies()::defensePercent).orElse(0),
                (int) d.saturation(),
                (int) CitizenData.MAX_SATURATION,
                held(d, Hand.MAIN),
                held(d, Hand.OFF));
    }

    /** The stack in the slot {@code hand} holds; empty for no slot or an emptied one. */
    private static Optional<ItemAmount> held(CitizenData d, Hand hand) {
        int slot = d.equipment().held(hand);
        return slot == CitizenEquipment.NO_SLOT
                ? Optional.empty()
                : d.inventory().slot(slot);
    }
}
