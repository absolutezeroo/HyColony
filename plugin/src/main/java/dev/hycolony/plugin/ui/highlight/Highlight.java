package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.server.core.Message;
import dev.hycolony.core.kernel.BlockPos;

/**
 * A block shown to one player so they find it ({@link Highlights}): the block at {@code anchor} glows and a map marker
 * named {@code markerName} points to it. The anchor also identifies it: asking again for it turns it off.
 */
public record Highlight(BlockPos anchor, Message markerName) {}
