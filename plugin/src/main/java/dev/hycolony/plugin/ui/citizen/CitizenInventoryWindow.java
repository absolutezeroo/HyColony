package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ValidatedWindow;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * The container window on a citizen's inventory. Valid while the citizen exists, at any distance (MC
 * ContainerCitizenInventory.stillValid is always true). World thread only.
 */
final class CitizenInventoryWindow extends ContainerWindow implements ValidatedWindow {
    private final Supplier<Optional<Inventory>> core;
    private Inventory seen;
    private long seenChanges;

    CitizenInventoryWindow(CitizenItemContainer container, Supplier<Optional<Inventory>> core) {
        super(container);
        this.core = core;
    }

    /** Hytale checks it before each move in the window and closes the window when it fails. */
    @Override
    public boolean validate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return core.get().isPresent();
    }

    /**
     * Hytale asks every tick (PlayerSendInventorySystem, WindowManager.updateWindows) whether to re-send the window: yes
     * also when the citizen's AI changed the inventory, which no container transaction reports.
     */
    @Override
    protected boolean consumeIsDirty() {
        return super.consumeIsDirty() | coreChanged();
    }

    /** Whether the citizen's inventory changed since the last call (the AI took or stored items, or was replaced). */
    boolean coreChanged() {
        Inventory inv = core.get().orElse(null);
        if (inv == null || inv.equals(seen) && inv.changes() == seenChanges) {
            return false;
        }
        seen = inv;
        seenChanges = inv.changes();
        return true;
    }
}
