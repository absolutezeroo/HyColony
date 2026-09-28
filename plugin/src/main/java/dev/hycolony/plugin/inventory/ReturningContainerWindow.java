package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A container window a custom page opens beside itself (PageManager.openCustomPageWithWindows) whose items belong to
 * the player: closing it, however it closes (the page dismissed, the player leaving: WindowManager.closeAllWindows),
 * gives them back, dropped at the player's feet when their inventory is full, as StructuralCraftingWindow.onClose0
 * does with its input slot.
 */
public final class ReturningContainerWindow extends ContainerWindow {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

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

    /**
     * Closes this window if player still holds this very window: closing one the client already closed would throw
     * from WindowManager. A failure is logged, never thrown.
     */
    // Identity, not equals: a later window may reuse the id.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    public void closeIfOpen(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor) {
        try {
            Player holder = accessor.getComponent(player, Player.getComponentType());
            if (player.isValid() && holder != null && holder.getWindowManager().getWindow(getId()) == this) {
                close(player, accessor);
            }
        } catch (RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("hycolony: could not close a page's container window");
        }
    }
}
