package dev.hyangler.plugin.api;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyangler.api.Fishing;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.Tackle;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * HyAngler for another Hytale mod (spec 2026-10-04-hyangler-design § 8.2): its fishing engine, and what needs Hytale's
 * types. {@link #contextAt} must be called on its world's thread; the rest is safe from any thread.
 *
 * <p>HyAngler implements it, addons do not: a minor version may add methods.
 *
 * @since 1.0
 */
public interface HyAnglerApi {
    /**
     * HyAngler's api; throws {@link IllegalStateException} before HyAngler's setup or after its shutdown. Any
     * thread.
     */
    static HyAnglerApi get() {
        return HyAnglerApiHolder.get();
    }

    /**
     * The fishing engine. Its catalog is empty until HyAngler's start, which reads the data files after the assets
     * and after every mod's setup, where condition types register. Any thread.
     */
    Fishing fishing();

    /**
     * The catch context at a block of world for this tackle: environment, zone, water, depth, open water, sky, hour,
     * weather, moon. On world's thread, else {@link IllegalStateException}.
     */
    FishingContext contextAt(World world, int x, int y, int z, Tackle tackle);

    /** The tackle of a rod stack; empty for a stack that is no rod, an empty stack, or none. Any thread. */
    Optional<Tackle> tackleOf(@Nullable ItemStack stack);
}
