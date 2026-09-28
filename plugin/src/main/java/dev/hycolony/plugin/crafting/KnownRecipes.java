package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * The recipes a player learnt, for Hytale's {@code KnowledgeRequired}: Hytale keeps them by primary output item id in
 * {@code PlayerConfigData.getKnownRecipes} (CraftingManager.isValidBenchForRecipe). World thread: reads the player's
 * store.
 */
final class KnownRecipes {
    private KnownRecipes() {}

    /** Whether the online {@code player} knows the recipe of {@code outputItemId}; false when offline. */
    static boolean knows(UUID player, String outputItemId) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Player p = ref.getStore().getComponent(ref, Player.getComponentType());
        return p != null && p.getPlayerConfigData().getKnownRecipes().contains(outputItemId);
    }
}
