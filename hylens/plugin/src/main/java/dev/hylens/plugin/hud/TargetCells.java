package dev.hylens.plugin.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.api.Pos;
import dev.hylens.core.hud.TargetCell;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the world holds at a walk target and below it, read on the world's thread from the chunk sections as
 * HytaleWorldBlocks.get reads them: the raw id of the block (a variant's state id included), else of the fluid, else
 * {@code ""}.
 */
final class TargetCells {
    private TargetCells() {}

    /** The cell at {@code target} and the one below; empty when either chunk is not loaded. */
    static Optional<TargetCell> at(World world, Pos target) {
        Optional<String> block = id(world, target.x(), target.y(), target.z());
        Optional<String> below = id(world, target.x(), target.y() - 1, target.z());
        return block.isPresent() && below.isPresent()
                ? Optional.of(new TargetCell(block.get(), below.get()))
                : Optional.empty();
    }

    /** The id of what fills the cell; empty when its section is not loaded. */
    private static Optional<String> id(World world, int x, int y, int z) {
        @Nullable Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return Optional.empty();
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        @Nullable BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
        if (blocks == null) {
            return Optional.empty();
        }
        int id = blocks.get(x, y, z);
        if (id != BlockType.EMPTY_ID) {
            @Nullable BlockType type = BlockType.getAssetMap().getAsset(id);
            return Optional.of(type == null ? "#" + id : type.getId());
        }
        @Nullable FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
        int fluid = fluids == null ? Fluid.EMPTY_ID : fluids.getFluidId(x, y, z);
        @Nullable Fluid f = fluid == Fluid.EMPTY_ID ? null : Fluid.getAssetMap().getAsset(fluid);
        return Optional.of(f == null ? "" : f.getId());
    }
}
