package dev.hycolony.core.testing.tape;

import dev.hycolony.core.construction.tape.TapeBlocks;
import dev.hycolony.core.construction.tape.TapeShape;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.Optional;

/** Tape blocks named {@code Tape_<shape>}. */
public final class FakeTapeBlocks implements TapeBlocks {
    private static final String PREFIX = "Tape_";

    /** The key of a tape of {@code shape}. */
    public static BlockKey key(TapeShape shape) {
        return new BlockKey(PREFIX + shape.name());
    }

    /** {@code catalog} with every tape walked through, as the game's tape (no collision, MC noCollission). */
    public static FakeCatalog walkThrough(FakeCatalog catalog) {
        for (TapeShape shape : TapeShape.values()) {
            catalog.kinds.put(key(shape), BlockKind.NON_SOLID);
        }
        return catalog;
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
