package dev.hyangler.plugin.world;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hyangler.core.port.EnvironmentProbe;
import java.util.List;
import java.util.logging.Level;

/**
 * Hytale's environment of a block, and its zone: the ZoneN tag each environment file declares, or takes from its
 * Parent (spec § 6.4, fishing-hytale.md § 5.6). Never loads a chunk: an unloaded block has no environment.
 */
final class HytaleEnvironments implements EnvironmentProbe {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final List<String> zones;
    private boolean warned;

    /** zones: the id-map's zone tags, tried in order. */
    HytaleEnvironments(World world, List<String> zones) {
        this.world = world;
        this.zones = zones;
    }

    @Override
    public String environment(int x, int y, int z) {
        try {
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
            if (sec == null || !sec.isValid()) {
                return "";
            }
            EnvironmentSection env = chunks.getStore().getComponent(sec, EnvironmentSection.getComponentType());
            Environment asset = env == null ? null : Environment.getAssetMap().getAsset(env.get(x, y, z));
            return asset == null ? "" : asset.getId();
        } catch (RuntimeException e) {
            failed(e, "environment");
            return "";
        }
    }

    @Override
    public String zone(String environment) {
        try {
            int index = Environment.getAssetMap().getIndex(environment);
            for (String zone : zones) {
                if (Environment.getAssetMap()
                        .getIndexesForTag(AssetRegistry.getOrCreateTagIndex(zone))
                        .contains(index)) {
                    return zone;
                }
            }
            return "";
        } catch (RuntimeException e) {
            failed(e, "zone");
            return "";
        }
    }

    private void failed(RuntimeException e, String what) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: %s read failed", what);
        warned = true;
    }
}
