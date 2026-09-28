package dev.hydomum.plugin.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hydomum.plugin.api.OrnamentVariant;
import java.util.logging.Level;

/** Who a /hydomum give hands its variant to: a stack of the variant's item, on the world thread. */
record VariantGift(PlayerRef player, Ref<EntityStore> ref) {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final int GIVEN = 16;

    /**
     * Gives {@link #GIVEN} of the variant's item (dropped at the feet when the inventory is full) and reports whether
     * the variant was created or reused; skipped when the player left meanwhile, a failure is logged and reported.
     */
    void give(OrnamentVariant variant, boolean created, long ms) {
        String key = variant.key().blockTypeKey();
        if (!ref.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = ref.getStore();
            SimpleItemContainer.addOrDropItemStack(
                    store,
                    ref,
                    InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST),
                    new ItemStack(key, GIVEN));
            LOG.at(Level.INFO).log(
                    "hydomum: gave %d %s (id %d), %s after %d ms",
                    GIVEN, key, variant.blockId(), created ? "created" : "reused", ms);
            if (created) {
                OrnamentCommand.say(player, "hydomum.ornament.created", key, String.valueOf(ms));
            } else {
                OrnamentCommand.say(player, "hydomum.ornament.reused", key);
            }
            OrnamentCommand.say(player, "hydomum.ornament.given", String.valueOf(GIVEN), key);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: giving %s failed", key);
            OrnamentCommand.say(player, "hydomum.ornament.failed", "give");
        }
    }
}
