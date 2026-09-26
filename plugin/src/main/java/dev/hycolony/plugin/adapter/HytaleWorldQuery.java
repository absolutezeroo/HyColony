package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.joml.Vector3d;

public final class HytaleWorldQuery implements WorldQuery {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private boolean warned;

    public HytaleWorldQuery(World world) {
        this.world = world;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x(), pos.z())) != null;
    }

    /** The world config's spawn provider, which may give each player their own spawn (IndividualSpawnProvider). */
    @Override
    public Optional<BlockPos> spawnPoint(UUID player) {
        try {
            ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
            if (provider == null) {
                return Optional.empty();
            }
            Vector3d p = provider.getSpawnPoint(world, player).getPosition();
            return Optional.of(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("WorldQuery.spawnPoint failed");
            warned = true;
            return Optional.empty();
        }
    }
}
