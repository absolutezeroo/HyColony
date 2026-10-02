package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ValidatedWindow;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.item.Inventory;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The container window on one part of a citizen's inventory ({@link CitizenInventoryPart}), opened beside its inventory
 * page. Valid while the citizen exists, at any distance (MC ContainerCitizenInventory.stillValid is always true). World
 * thread only.
 */
final class CitizenInventoryWindow extends ContainerWindow implements ValidatedWindow {
    private final CitizenItemContainer.Owner owner;
    private final CitizenInventoryPart part;
    private @Nullable Inventory seen;
    private long seenChanges;

    CitizenInventoryWindow(
            CitizenItemContainer container, CitizenItemContainer.Owner owner, CitizenInventoryPart part) {
        super(container);
        this.owner = owner;
        this.part = part;
    }

    /** Hytale checks it before each move in the window and closes the window when it fails. */
    @Override
    public boolean validate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return owner.alive().getAsBoolean();
    }

    /**
     * Hytale asks every tick (PlayerSendInventorySystem, WindowManager.updateWindows) whether to re-send the window:
     * yes also when the citizen's AI changed that part, which no container transaction reports. Allocates nothing.
     */
    @Override
    @SuppressWarnings("ShortCircuitBoolean") // both must run: each consumes its own change
    protected boolean consumeIsDirty() {
        return super.consumeIsDirty() | coreChanged();
    }

    /** Whether that part changed since the last call (the AI took or stored items, a piece wore, or was replaced). */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // the same Inventory object, not an equal one
    boolean coreChanged() {
        Inventory inv = part.of(owner.citizen());
        if (inv == seen && inv.changes() == seenChanges) {
            return false;
        }
        seen = inv;
        seenChanges = inv.changes();
        return true;
    }
}
