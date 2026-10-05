package dev.hyangler.plugin.world;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.WeatherParticle;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hyangler.core.port.WeatherProbe;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The weather on a block (fishing-hytale.md § 5.6, as HyColony's HytaleWorldQuery.isRainingAt): the forced weather
 * ({@code /weather set}) wins, else the one WeatherSystem drew for the block's environment. It is rain or snow when its
 * particle system is in the id-map's precipitation list. Never loads a chunk: an unloaded block has no weather.
 */
final class HytaleWeather implements WeatherProbe {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final Set<String> precipitation;
    private boolean warned;

    HytaleWeather(World world, Set<String> precipitation) {
        this.world = world;
        this.precipitation = precipitation;
    }

    @Override
    public String weather(int x, int y, int z) {
        Weather weather = at(x, y, z);
        return weather == null ? "" : weather.getId();
    }

    @Override
    public boolean raining(int x, int y, int z) {
        Weather weather = at(x, y, z);
        WeatherParticle particle = weather == null ? null : weather.getParticle();
        return particle != null && particle.systemId != null && precipitation.contains(particle.systemId);
    }

    private @Nullable Weather at(int x, int y, int z) {
        try {
            WeatherResource weather = world.getEntityStore().getStore().getResource(WeatherResource.getResourceType());
            int index = weather.getForcedWeatherIndex();
            if (index == 0) {
                ChunkStore chunks = world.getChunkStore();
                Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
                EnvironmentSection env = sec == null || !sec.isValid()
                        ? null
                        : chunks.getStore().getComponent(sec, EnvironmentSection.getComponentType());
                if (env == null) {
                    return null;
                }
                index = weather.getWeatherIndexForEnvironment(env.get(x, y, z));
            }
            return Weather.getAssetMap().getAsset(index);
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: weather read failed");
            warned = true;
            return null;
        }
    }
}
