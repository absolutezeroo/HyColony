package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * A container window a custom page opens beside itself (PageManager.openCustomPageWithWindows) whose items belong to
 * the player: closing it, however it closes (the page dismissed, the player leaving: WindowManager.closeAllWindows),
 * gives them back, dropped at the player's feet when their inventory is full, as StructuralCraftingWindow.onClose0
 * does with its input slot.
 */
public final class ReturningContainerWindow extends ContainerWindow {
    private final SimpleItemContainer container;
    private boolean closed;

    /** A window on container, whose content goes back to the player on close. */
    public ReturningContainerWindow(SimpleItemContainer container) {
        super(container);
        this.container = container;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        super.onClose0(ref, accessor);
        closed = true;
        SimpleItemContainer.addOrDropItemStacks(
                accessor,
                ref,
                InventoryComponent.getCombined(accessor, ref, InventoryComponent.HOTBAR_FIRST),
                container.dropAllItemStacks(false));
    }

    /** Whether this window was closed (its content already went back to the player). */
    public boolean isClosed() {
        return closed;
    }

    /** Closes this window at the world's next task if player still holds it then (HeldWindows.closeLater). */
    public void closeLater(Ref<EntityStore> player) {
        HeldWindows.closeLater(player, this);
    }
}
