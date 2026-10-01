package dev.hycolony.core.app.ui;

import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.field.FieldRadii.Direction;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A field block's window (MC WindowField): the farmer working it, its biome's name, its seed, its four radii, where
 * each side lies from the viewer's look, and the seeds it may be set to. {@code canManage}: the viewer may change the
 * seed and the radii (MANAGE_HUTS).
 */
public record FieldView(
        BlockPos pos,
        Optional<String> farmer,
        Optional<String> biome,
        Optional<ItemKey> seed,
        FieldRadii radii,
        Map<Direction, Relative> relative,
        List<ItemKey> seeds,
        boolean canManage) {
    /** MC WindowField.getDirectionalTranslationKey: where a side lies from the player's look. */
    public enum Relative {
        NEAREST,
        TO_RIGHT,
        OPPOSITE,
        TO_LEFT
    }

    public FieldView {
        relative = Map.copyOf(relative);
        seeds = List.copyOf(seeds);
    }

    /**
     * Each side seen from {@code facing} (a quarter-turn clockwise from north, PlayerDirectory.facing). MC compares
     * the 2D value of the side opposite the look, which is {@code facing} itself, with the side's (south 0, west 1,
     * north 2, east 3, the order of Direction).
     */
    public static Map<Direction, Relative> sides(int facing) {
        Map<Direction, Relative> sides = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            sides.put(d, Relative.values()[Math.floorMod(facing - d.ordinal(), 4)]);
        }
        return sides;
    }
}
