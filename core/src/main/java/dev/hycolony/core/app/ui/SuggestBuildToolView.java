package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;

/**
 * MC WindowSuggestBuildTool: a hut block placed by hand, refused; {@code pos} is where it was aimed (the cell against
 * the clicked face), {@code hut} the hut item the player holds.
 */
public record SuggestBuildToolView(BlockPos pos, ItemKey hut) {}
