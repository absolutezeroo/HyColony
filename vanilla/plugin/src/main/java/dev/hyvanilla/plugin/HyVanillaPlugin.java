package dev.hyvanilla.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hyvanilla.plugin.block.FlowerPotSystem;
import javax.annotation.Nonnull;

/** HyVanilla's entry point: its asset pack (carpets, flower pots) and the flower pot's use. */
public final class HyVanillaPlugin extends JavaPlugin {
    public HyVanillaPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Unconditionally: HyColony orders its protection before this system (HyVanillaSystems), and a
        // SystemDependency on an unregistered class throws (ComponentRegistry.java:657-659).
        getEntityStoreRegistry().registerSystem(new FlowerPotSystem(VanillaIds.flowerPots()));
    }
}
