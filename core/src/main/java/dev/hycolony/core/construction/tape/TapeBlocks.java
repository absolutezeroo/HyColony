package dev.hycolony.core.construction.tape;

import dev.hycolony.core.kernel.item.BlockKey;
import java.util.Optional;

/** Port: the construction tape's blocks (MC BlockConstructionTape), one per {@link TapeShape}; never throws. */
public interface TapeBlocks {
    /** A game without the tape block: nothing is placed, nothing is a tape. */
    TapeBlocks NONE = new TapeBlocks() {
        @Override
        public Optional<BlockKey> block(TapeShape shape) {
            return Optional.empty();
        }

        @Override
        public boolean isTape(BlockKey block) {
            return false;
        }
    };

    /** The block of a tape of {@code shape}; empty when the game has none. */
    Optional<BlockKey> block(TapeShape shape);

    /** Whether {@code block} is a construction tape, of any shape. */
    boolean isTape(BlockKey block);
}
