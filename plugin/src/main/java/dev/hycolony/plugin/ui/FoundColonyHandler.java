package dev.hycolony.plugin.ui;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The founding window's answers for one player (spec SP0 § 4.2): confirming founds the colony, cancelling or closing
 * drops the foundation; a town hall whose foundation was dropped is removed and dropped as an item.
 */
public final class FoundColonyHandler implements FoundColonyPage.Handler {
    private final Supplier<ColonyManager> manager;
    private final UUID player;
    private final HytaleBlocks blocks;
    private final String townHallBlockId;
    private final String townHallItemId;
    /** The UI port's players whose page Hytale is closing now, so that its close() leaves the page alone. */
    private final Set<UUID> closing;

    public FoundColonyHandler(
            Supplier<ColonyManager> manager, UUID player, HytaleBlocks blocks, IdMap ids, Set<UUID> closing) {
        this.manager = manager;
        this.player = player;
        this.blocks = blocks;
        this.townHallBlockId = ids.blockId("hut.townhall");
        this.townHallItemId = ids.itemId("hut.townhall");
        this.closing = closing;
    }

    /** True once the colony exists; a spot that became invalid drops the foundation and its town hall. */
    @Override
    public boolean confirm(String name) {
        ColonyManager m = manager.get();
        Optional<BlockPos> pos = m.foundation().pendingPositionOf(player);
        boolean created = m.foundation().confirm(player, name).isPresent();
        if (!created && m.foundation().pendingPositionOf(player).isEmpty()) {
            pos.ifPresent(this::removeTownHall);
        }
        return created;
    }

    /** Drops the foundation and its town hall; {@code windowClosing} while Hytale is already closing the page. */
    @Override
    public void cancel(boolean windowClosing) {
        if (windowClosing) {
            closing.add(player);
        }
        try {
            manager.get().foundation().cancel(player).ifPresent(this::removeTownHall);
        } finally {
            closing.remove(player);
        }
    }

    private void removeTownHall(BlockPos pos) {
        blocks.removeWithDrop(pos, townHallBlockId, townHallItemId);
    }
}
