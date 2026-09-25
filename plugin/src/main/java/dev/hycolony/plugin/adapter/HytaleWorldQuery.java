package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;

public final class HytaleWorldQuery implements WorldQuery {
    private final World world;

    public HytaleWorldQuery(World world) {
        this.world = world;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x(), pos.z())) != null;
    }
}
