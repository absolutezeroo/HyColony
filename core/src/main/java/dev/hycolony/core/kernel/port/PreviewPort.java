package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import java.util.List;
import java.util.UUID;

/** Ghost block previews that only one player sees. Never throws; an absent player or unknown id is a no-op. */
public interface PreviewPort {
    /** One ghost block, relative to the preview's origin. */
    record Block(BlockPos offset, BlockState state) {}

    /** Shows {@code blocks} at {@code origin} to {@code player}, replacing any preview with the same id. */
    void show(UUID player, String id, BlockPos origin, List<Block> blocks);

    /** Removes one preview of {@code player}; nothing if absent. */
    void hide(UUID player, String id);

    /** Removes every preview of {@code player}. */
    void hideAll(UUID player);
}
