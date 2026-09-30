package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ValidatedWindow;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The container window on a citizen's inventory. Valid while the citizen exists, at any distance (MC
 * ContainerCitizenInventory.stillValid is always true). World thread only.
 */
final class CitizenInventoryWindow extends ContainerWindow implements ValidatedWindow {
    private final CitizenData citizen;
    private final BooleanSupplier alive;
    private @Nullable Inventory seen;
    private long seenChanges;
    private Runnable onChange = () -> {};

    CitizenInventoryWindow(CitizenItemContainer container, CitizenData citizen, BooleanSupplier alive) {
        super(container);
        this.citizen = citizen;
        this.alive = alive;
    }

    /** Hytale checks it before each move in the window and closes the window when it fails. */
    @Override
    public boolean validate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return alive.getAsBoolean();
    }

    /**
     * Hytale asks every tick (PlayerSendInventorySystem, WindowManager.updateWindows) whether to re-send the window:
     * yes also when the citizen's AI changed the inventory, which no container transaction reports; then tells the page
     * showing it. Allocates nothing.
     */
    @Override
    protected boolean consumeIsDirty() {
        boolean dirty = super.consumeIsDirty() | coreChanged();
        if (dirty) {
            onChange.run();
        }
        return dirty;
    }

    /** Runs listener whenever the window is re-sent (a move in it, or the AI changing the inventory). */
    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    /** Whether the citizen's inventory changed since the last call (the AI took or stored items, or was replaced). */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // the same Inventory object, not an equal one
    boolean coreChanged() {
        Inventory inv = citizen.inventory();
        if (inv == seen && inv.changes() == seenChanges) {
            return false;
        }
        seen = inv;
        seenChanges = inv.changes();
        return true;
    }
}
