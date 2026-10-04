package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.plugin.registry.AssetRegistry;
import dev.hycolony.plugin.crafting.JobTagAsset;
import dev.hycolony.plugin.food.FoodValueAsset;

/**
 * HyColony's asset types open to other mods (spec 2026-10-04): their files are read in every pack once the assets
 * load, so they are registered at setup, as Hytale's own plugins do (ShopPlugin.setup).
 */
final class OpenAssetTypes {
    private OpenAssetTypes() {}

    /** Registers each type with {@code registry}; call once, in setup(). */
    static void register(AssetRegistry registry) {
        FoodValueAsset.register(registry);
        JobTagAsset.register(registry);
    }
}
