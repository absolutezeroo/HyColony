package dev.hycolony.plugin.ui.hut.restaurant;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Locale;
import java.util.UUID;

/**
 * The text filter of MC's item lists (RestaurantMenuModuleWindow and ItemListModuleWindow.updateResources): an item
 * shows when the filter is empty, or its id or its name in the player's language contains it, ignoring case.
 */
final class ItemNames {
    private ItemNames() {}

    /** Whether {@code item} passes {@code filter} for {@code player}. */
    static boolean matches(UUID player, ItemKey item, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        String wanted = filter.toLowerCase(Locale.ROOT);
        return item.id().toLowerCase(Locale.ROOT).contains(wanted)
                || name(player, item).toLowerCase(Locale.ROOT).contains(wanted);
    }

    /** The item's name in the player's language; its id when the game has no translation or the player left. */
    private static String name(UUID player, ItemKey item) {
        Item asset = Item.getAssetMap().getAsset(item.id());
        PlayerRef ref = Universe.get().getPlayer(player);
        String name = asset == null || ref == null
                ? null
                : I18nModule.get().getMessage(ref.getLanguage(), asset.getTranslationKey());
        return name == null ? item.id() : name;
    }
}
