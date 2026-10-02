package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;

/**
 * MC CitizenItemUtils.damageArmor: a hurt citizen's armour wears, each piece by a quarter of the damage, at least one
 * point; a piece worn to its durability breaks and is gone.
 *
 * <p>Deviation from MC: the points come off the piece's Hytale durability (80 to 180 for vanilla armour, MC's 80 to
 * 528), so a piece breaks in fewer hits than in MC. Hytale itself never wears a non-player's armour.
 */
public final class ArmorWear {
    /** MC: the damage shared by the armour pieces, a quarter each. */
    private static final int DAMAGE_PER_POINT = 4;

    private ArmorWear() {}

    /**
     * {@code d} lost {@code damagePercent} of its body's health: on MC's scale ({@link CitizenData#MC_MAX_HEALTH}),
     * each armour piece the catalog knows loses {@code max(1, damage / 4)} points; the colony is saved and the body
     * shows what is left. Nothing without armour.
     */
    public static void onHurt(Colony c, CitizenData d, double damagePercent) {
        double damage = damagePercent * CitizenData.MC_MAX_HEALTH / 100;
        int wear = Math.max(1, (int) (damage / DAMAGE_PER_POINT));
        Inventory armor = d.equipment().armor();
        boolean worn = false;
        for (int slot = 0; slot < armor.size(); slot++) {
            Optional<ArmorInfo> piece =
                    armor.slot(slot).flatMap(a -> c.context().ports().armors().armor(a.item()));
            if (piece.isPresent()) {
                armor.damage(slot, wear, piece.get().maxDurability());
                worn = true;
            }
        }
        if (worn) {
            c.markDirty();
            c.citizens()
                    .bodyOf(d.id())
                    .ifPresent(b -> HeldItems.showArmor(d, c.context().bodies(), b));
        }
    }
}
