package dev.hycolony.plugin.ui.clipboard;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

/**
 * The clipboard a player used (MC ItemClipboard's NBT): the colony it notes and its "!" state live in the item's
 * metadata. The last clipboard each player used is kept for its window's "!" button.
 *
 * @param container the container holding it, as the interaction found it
 * @param slot its slot there
 * @param stack the item as read; a write replaces it in its slot only while the slot still holds it
 */
public record ClipboardItem(ItemContainer container, short slot, ItemStack stack) {
    /** MC TAG_COLONY. */
    private static final String COLONY = "HyColonyClipboardColony";
    /** MC TAG_HIDEUNIMPORTANT, which holds the window's showImportant flag. */
    private static final String SHOW_IMPORTANT = "HyColonyClipboardShowImportant";

    private static final Map<UUID, ClipboardItem> LAST_USED = new ConcurrentHashMap<>();

    /** The colony noted; empty for a clipboard never used on a hut. */
    public Optional<Integer> colony() {
        BsonDocument meta = stack.getMetadata();
        BsonValue v = meta == null ? null : meta.get(COLONY);
        return v != null && v.isInt32() ? Optional.of(v.asInt32().getValue()) : Optional.empty();
    }

    /** The "!" state; off for a new clipboard, as MC's missing tag. */
    public boolean showImportant() {
        BsonDocument meta = stack.getMetadata();
        BsonValue v = meta == null ? null : meta.get(SHOW_IMPORTANT);
        return v != null && v.isBoolean() && v.asBoolean().getValue();
    }

    /** This clipboard noting {@code colonyId}, written into its slot. */
    public ClipboardItem withColony(int colonyId) {
        return with(COLONY, new BsonInt32(colonyId));
    }

    /** This clipboard with the "!" state {@code on}, written into its slot. */
    public ClipboardItem withShowImportant(boolean on) {
        return with(SHOW_IMPORTANT, BsonBoolean.valueOf(on));
    }

    /** The item with {@code key} set; unchanged when its slot no longer holds it (moved or dropped meanwhile). */
    private ClipboardItem with(String key, BsonValue value) {
        ItemStack next = stack.withMetadata(key, value);
        return container.replaceItemStackInSlot(slot, stack, next).succeeded()
                ? new ClipboardItem(container, slot, next)
                : this;
    }

    /** Keeps {@code item} as the clipboard {@code player} last used. */
    public static void used(UUID player, ClipboardItem item) {
        LAST_USED.put(player, item);
    }

    /** The clipboard {@code player} last used, if any since the server started. */
    public static Optional<ClipboardItem> lastUsed(UUID player) {
        return Optional.ofNullable(LAST_USED.get(player));
    }
}
