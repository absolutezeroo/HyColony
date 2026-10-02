package dev.hycolony.plugin.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import dev.hycolony.core.citizen.inventory.ArmorCatalog;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Armour pieces from Hytale's item assets: the slot of {@code Item.getArmor().getArmorSlot()} (ItemArmorSlot order
 * Head, Chest, Hands, Legs), {@code Item.getItemLevel()}, which a child item inherits from its parent, and {@code
 * Item.getMaxDurability()}; its hits before it breaks come from the item catalog's durability. Read at each call (one
 * asset lookup, asked when a player moves an item into a citizen's armour, and at load for each worn armour piece of
 * a schema-9 citizen). Never throws (CLAUDE.md § 4).
 */
public final class HytaleArmorCatalog implements ArmorCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private boolean warned;

    @Override
    public Optional<ArmorInfo> armor(ItemKey item) {
        try {
            Item asset = Item.getAssetMap().getAsset(item.id());
            ItemArmor armor = asset == null ? null : asset.getArmor();
            if (asset == null || armor == null || armor.getArmorSlot() == null) {
                return Optional.empty();
            }
            int itemLevel = Math.max(0, asset.getItemLevel());
            double max = Math.max(0, asset.getMaxDurability());
            return ArmorInfo.Slot.at(armor.getArmorSlot().getValue()).map(slot -> new ArmorInfo(slot, itemLevel, max));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony armour of %s unread", item.id());
            warned = true;
            return Optional.empty();
        }
    }
}
