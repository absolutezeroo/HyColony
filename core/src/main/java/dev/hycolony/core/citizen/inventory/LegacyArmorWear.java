package dev.hycolony.core.citizen.inventory;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Optional;

/**
 * The wear of the armour pieces a schema-9 citizen carried, counted then in Hytale's durability points (the core did
 * not wear armour, DurabilityScale counting one use per point) and since schema 10 in hits (ItemCatalog.durability).
 * MigrationV9ToV10 marks each migrated citizen with {@link #KEY}, as only the catalog knows a piece's points per hit.
 */
final class LegacyArmorWear {
    /** Set by MigrationV9ToV10 (kernel/persist), which cannot see this package: the same key is written there. */
    static final String KEY = "armorWearInPoints";

    private LegacyArmorWear() {}

    /** Whether the saved citizen {@code o} counts its armour's wear in points, to be read in hits and written again. */
    static boolean marked(JsonObject o) {
        return o.get(KEY) instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    /**
     * Turns the points worn off each armour piece of {@code inventory} into the hits that wear as many points, rounded
     * up as DurabilityScale does, at most the piece's hits; a piece the catalog does not know as breakable armour
     * keeps its wear.
     */
    static void convert(Inventory inventory, ArmorCatalog armors, ItemCatalog items) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemAmount a = inventory.slot(slot).orElse(null);
            if (a == null || a.damage() == 0) {
                continue;
            }
            double points = armors.armor(a.item()).map(ArmorInfo::maxDurability).orElse(0.0);
            int hits = items.durability(a.item());
            if (points > 0 && hits > 0) {
                int worn = (int) Math.min(hits, Math.ceil(a.damage() * hits / points - 1e-9));
                inventory.set(slot, Optional.of(new ItemAmount(a.item(), a.count(), worn)));
            }
        }
    }
}
