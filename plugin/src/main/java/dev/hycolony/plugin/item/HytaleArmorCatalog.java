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
 * Item.getMaxDurability()} (80 to 180 points for vanilla armour, 0 unbreakable). Read at each
 * call (one asset lookup): a piece is asked about only when a player moves it. Never throws (CLAUDE.md § 4).
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
            ArmorInfo.Slot slot = ArmorInfo.Slot.values()[armor.getArmorSlot().ordinal()];
            int points = (int) Math.max(0, asset.getMaxDurability());
            return Optional.of(new ArmorInfo(slot, Math.max(0, asset.getItemLevel()), points));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony armour of %s unread", item.id());
            warned = true;
            return Optional.empty();
        }
    }
}
