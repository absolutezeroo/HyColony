package dev.hyangler.plugin.bridge;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyangler.api.Fishing;
import dev.hyangler.api.FishingContext;
import dev.hyangler.api.RodStats;
import dev.hyangler.api.Tackle;
import dev.hyangler.core.FishingService;
import dev.hyangler.plugin.AnglerIds;
import dev.hyangler.plugin.api.HyAnglerApi;
import dev.hyangler.plugin.api.HyAnglerApiHolder;
import dev.hyangler.plugin.world.WorldContexts;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * HyAngler's api behind its holder (spec § 8): the core's service, the context at a block, a rod's tackle. It never
 * loads the data files: HyAngler does at its start, after the assets and every mod's setup (AnglerData).
 */
public final class ApiBridge implements HyAnglerApi {
    private final FishingService service;
    private final AnglerIds ids;

    private ApiBridge(FishingService service, AnglerIds ids) {
        this.service = service;
        this.ids = ids;
    }

    /** HyAngler's setup: makes a bridge over these the api, and returns it. */
    public static ApiBridge install(FishingService service, AnglerIds ids) {
        ApiBridge bridge = new ApiBridge(service, ids);
        HyAnglerApiHolder.install(bridge);
        return bridge;
    }

    /** HyAngler's shutdown: no api any more. */
    public static void uninstall() {
        HyAnglerApiHolder.clear();
    }

    @Override
    public Fishing fishing() {
        return service;
    }

    @Override
    public FishingContext contextAt(World world, int x, int y, int z, Tackle tackle) {
        ApiThreads.check(world);
        return WorldContexts.of(world, ids).at(x, y, z, tackle);
    }

    @Override
    public Optional<Tackle> tackleOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return service.rod(stack.getItemId()).map(RodStats::tackle);
    }
}
