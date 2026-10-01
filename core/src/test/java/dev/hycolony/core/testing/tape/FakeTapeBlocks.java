package dev.hycolony.core.testing.tape;

import dev.hycolony.core.construction.tape.TapeBlocks;
import dev.hycolony.core.construction.tape.TapeShape;
import dev.hycolony.core.kernel.item.BlockKey;
import java.util.Optional;

/** Tape blocks named {@code Tape_<shape>}. */
public final class FakeTapeBlocks implements TapeBlocks {
    private static final String PREFIX = "Tape_";

    /** The key of a tape of {@code shape}. */
    public static BlockKey key(TapeShape shape) {
        return new BlockKey(PREFIX + shape.name());
    }

    @Override
    public Optional<BlockKey> block(TapeShape shape) {
        return Optional.of(key(shape));
    }

    @Override
    public boolean isTape(BlockKey block) {
        return block.id().startsWith(PREFIX);
    }
}
