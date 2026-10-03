package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A hurt citizen's armour wears (MC CitizenItemUtils.updateArmorDamage, called from EntityCitizen.hurt).
 *
 * <p>Deviation from MC (Hytale world): MC wears every piece by a quarter of the damage, a broken piece gone → Hytale's
 * own armour wear, which Hytale gives players only (DamageSystems.DamageArmor,
 * ItemUtils.canDecreaseItemStackDurability): a hit whose cause wears armour falls on one random piece that is not
 * broken, an unbreakable one included, and costs it one hit of its life (its DurabilityLossOnHit,
 * ItemCatalog.durability counting hits); an unbreakable piece loses nothing, a broken one stays worn.
 *
 * <p>Deviation from MC: no research yet, so no ARMOR_DURABILITY chance to spare the armour (updateArmorDamage).
 */
public final class ArmorWear {
    private ArmorWear() {}

    /**
     * {@code d} took a hit whose cause wears armour: one of its pieces that is not broken, chosen at random, takes one
     * hit; when it wore, the colony is saved and the body shows it. Nothing without such a piece.
     */
    public static void onHurt(Colony c, CitizenData d) {
        Inventory armor = d.equipment().armor();
        ItemCatalog catalog = c.context().ports().catalog();
        List<Integer> unbroken = new ArrayList<>(armor.size());
        for (int slot = 0; slot < armor.size(); slot++) {
            Optional<ItemAmount> piece = armor.slot(slot);
            if (piece.isPresent() && !catalog.wornOut(piece.get())) {
                unbroken.add(slot);
            }
        }
        if (unbroken.isEmpty()) {
            return;
        }
        int slot = unbroken.get(c.context().random().nextInt(unbroken.size()));
        ItemAmount piece = armor.slot(slot).orElseThrow();
        if (catalog.durability(piece.item()) <= 0) {
            return; // unbreakable: the hit wears nothing (Hytale's ItemStack.isUnbreakable)
        }
        armor.set(slot, Optional.of(new ItemAmount(piece.item(), piece.count(), piece.damage() + 1)));
        c.markDirty();
        c.citizens()
                .bodyOf(d.id())
                .ifPresent(b -> HeldItems.showArmor(d, c.context().bodies(), b));
    }
}
