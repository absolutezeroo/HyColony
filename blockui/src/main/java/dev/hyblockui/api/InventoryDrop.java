package dev.hyblockui.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.function.Function;

/**
 * What the client sends when an item is dropped on an inventory grid of a custom page (the Dropped event of an
 * ItemGrid filled with ItemStacks): the grid dropped on, the target slot, and where the item came from. Only positions
 * are used: the stack and its quantity are read again on the server (InventoryMoves). Codec target, so mutable.
 */
public final class InventoryDrop {
    /** The event data key naming the grid dropped on (see InventoryGrids.bindDrop). */
    static final String GRID_KEY = "Grid";

    String grid = "";
    Integer toSlot = -1;
    Integer fromSection = 0;
    Integer fromSlot = -1;
    Integer quantity = 0;

    /**
     * Adds the drop's keys to a page's event codec, whose events hold an InventoryDrop reached through drop: Grid,
     * SlotIndex, SourceInventorySectionId, SourceSlotId and ItemStackQuantity (the keys Hytale sends, unchanged in
     * 0.7.0).
     */
    public static <T> BuilderCodec.Builder<T> appendTo(BuilderCodec.Builder<T> codec, Function<T, InventoryDrop> drop) {
        return codec.append(
                        new KeyedCodec<>(GRID_KEY, Codec.STRING),
                        (d, v) -> drop.apply(d).grid = v,
                        d -> drop.apply(d).grid)
                .add()
                .append(
                        new KeyedCodec<>("SlotIndex", Codec.INTEGER),
                        (d, v) -> drop.apply(d).toSlot = v,
                        d -> drop.apply(d).toSlot)
                .add()
                .append(
                        new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER),
                        (d, v) -> drop.apply(d).fromSection = v,
                        d -> drop.apply(d).fromSection)
                .add()
                .append(
                        new KeyedCodec<>("SourceSlotId", Codec.INTEGER),
                        (d, v) -> drop.apply(d).fromSlot = v,
                        d -> drop.apply(d).fromSlot)
                .add()
                .append(
                        new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER),
                        (d, v) -> drop.apply(d).quantity = v,
                        d -> drop.apply(d).quantity)
                .add();
    }

    /** The grid dropped on, as named when its drop was bound; "" when the event is no drop. */
    public String grid() {
        return grid == null ? "" : grid;
    }

    /** Whether every position the move needs was sent (a key the client left out decodes as null). */
    boolean complete() {
        return toSlot != null && fromSection != null && fromSlot != null && quantity != null;
    }
}
