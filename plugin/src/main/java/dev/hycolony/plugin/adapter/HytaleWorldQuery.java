package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.WeatherParticle;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.joml.Vector3d;

public final class HytaleWorldQuery implements WorldQuery {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final Set<String> precipitation;
    private boolean warned;
    private boolean weatherWarned;

    /** {@code precipitation}: the weather particle systems that count as rain or snow (id-map). */
    public HytaleWorldQuery(World world, Set<String> precipitation) {
        this.world = world;
        this.precipitation = precipitation;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x(), pos.z())) != null;
    }

    /**
     * The world config's spawn provider, which may give each player their own spawn (IndividualSpawnProvider). Never
     * blocks the world thread: while the answer is pending (FitToHeightMap loading the column to fit Y), the provider's
     * single base point stands in, Y unfitted (the core reads X and Z only); empty if it has several.
     */
    @Override
    public Optional<BlockPos> spawnPoint(UUID player) {
        try {
            ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
            if (provider == null) {
                return Optional.empty();
            }
            Transform spawn = provider.getSpawnPointAsync(world, player).getNow(null);
            if (spawn == null) {
                Transform[] base = provider.getSpawnPoints();
                if (base.length != 1) {
                    return Optional.empty();
                }
                spawn = base[0];
            }
            Vector3d p = spawn.getPosition();
            return Optional.of(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("WorldQuery.spawnPoint failed");
            warned = true;
            return Optional.empty();
        }
    }

    /**
     * Rain or snow in the environment of {@code pos}: the forced weather ({@code /weather set}) wins, else the weather
     * WeatherSystem drew for the block's environment this hour, as WorldSupport.getCurrentWeatherIndex does for NPCs.
     * The weather counts as precipitation when its particle system is in the id-map list. False when the chunk is not
     * loaded, the weather is not computed yet, or anything fails (never throws).
     *
     * <p>Deviation from MC: MC Level.isRaining is one flag for the whole world; Hytale has weather per environment
     * only, so the core asks at the worker's hut. Snow counts as rain, as MC's global flag is also true in snowy
     * biomes.
     */
    @Override
    public boolean isRainingAt(BlockPos pos) {
        try {
            WeatherResource weather = world.getEntityStore().getStore().getResource(WeatherResource.getResourceType());
            int index = weather.getForcedWeatherIndex();
            if (index == 0) {
                Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
                EnvironmentSection env = sec == null
                        ? null
                        : world.getChunkStore().getStore().getComponent(sec, EnvironmentSection.getComponentType());
                if (env == null) {
                    return false;
                }
                index = weather.getWeatherIndexForEnvironment(env.get(pos.x(), pos.y(), pos.z()));
            }
            Weather asset = Weather.getAssetMap().getAsset(index);
            WeatherParticle particle = asset == null ? null : asset.getParticle();
            return particle != null && particle.systemId != null && precipitation.contains(particle.systemId);
        } catch (RuntimeException e) {
            LOG.at(weatherWarned ? Level.FINE : Level.WARNING).withCause(e).log("WorldQuery.isRainingAt failed");
            weatherWarned = true;
            return false;
        }
    }
}
