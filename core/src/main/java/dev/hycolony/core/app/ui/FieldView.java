package dev.hycolony.core.app.ui;

import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/**
 * A field block's window (MC WindowField): the farmer working it, its seed, its four radii, and the seeds it may be
 * set to. {@code canManage}: the viewer may change the seed and the radii (MANAGE_HUTS).
 */
public record FieldView(
        BlockPos pos,
        Optional<String> farmer,
        Optional<ItemKey> seed,
        FieldRadii radii,
        List<ItemKey> seeds,
        boolean canManage) {
    public FieldView {
        seeds = List.copyOf(seeds);
    }
}
