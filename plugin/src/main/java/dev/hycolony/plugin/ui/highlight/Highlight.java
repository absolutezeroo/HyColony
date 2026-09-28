package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.server.core.Message;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;

/**
 * Something shown to one player so they find it: boxes drawn around it and a map marker named {@code markerName} at
 * {@code anchor}. The anchor also identifies it: asking again for the same anchor turns it off ({@link Highlights}).
 */
public record Highlight(BlockPos anchor, List<Box> boxes, Message markerName) {
    public Highlight {
        boxes = List.copyOf(boxes);
    }

    /** How a box is drawn: a translucent block, its edges only, or a thin upright cylinder. */
    public enum Style {
        SOLID,
        OUTLINE,
        BEAM
    }

    /** A box centred on (x, y, z), sized (sx, sy, sz) in blocks. */
    public record Box(double x, double y, double z, double sx, double sy, double sz, Style style) {}

    /** The box of {@code height} blocks standing on {@code pos}, a little wider than the block. */
    public static Box around(BlockPos pos, double height) {
        return new Box(pos.x() + 0.5, pos.y() + height / 2, pos.z() + 0.5, 1.1, height + 0.1, 1.1, Style.SOLID);
    }

    /** A thin beam of {@code length} blocks rising from {@code topY} above {@code pos}, to spot it from afar. */
    public static Box beam(BlockPos pos, double topY, double length) {
        return new Box(pos.x() + 0.5, topY + length / 2, pos.z() + 0.5, 0.25, length, 0.25, Style.BEAM);
    }
}
