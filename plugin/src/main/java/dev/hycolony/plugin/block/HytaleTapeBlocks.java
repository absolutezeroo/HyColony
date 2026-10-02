package dev.hycolony.plugin.block;

import dev.hycolony.core.construction.tape.TapeBlocks;
import dev.hycolony.core.construction.tape.TapeShape;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.plugin.IdMap;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The construction tape's block ids, one per shape, from the id-map ({@code block.tape.<shape>}): the base block for
 * a straight tape, its connected states for the others ({@code tools/tape/generate.py}). Read once at setup, like the
 * other blocks of the id-map; its calls never throw.
 */
public final class HytaleTapeBlocks implements TapeBlocks {
    private final Map<TapeShape, BlockKey> blocks = new EnumMap<>(TapeShape.class);
    private final Set<BlockKey> tapes;

    public HytaleTapeBlocks(IdMap ids) {
        for (TapeShape shape : TapeShape.values()) {
            blocks.put(
                    shape, new BlockKey(ids.blockId("block.tape." + shape.name().toLowerCase(Locale.ROOT))));
        }
        tapes = Set.copyOf(blocks.values());
    }

    @Override
    public Optional<BlockKey> block(TapeShape shape) {
        return Optional.ofNullable(blocks.get(shape));
    }

    @Override
    public boolean isTape(BlockKey block) {
        return tapes.contains(block);
    }
}
