package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;

/** Closing a window a page opened beside itself, safely. World thread. */
public final class HeldWindows {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private HeldWindows() {}

    /**
     * Whether player's window manager still holds this very window: Window.equals only compares id, type and player,
     * which a later window may share.
     */
    // Identity, not equals: a later window may reuse the id.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    public static boolean holds(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor, Window window) {
        if (!player.isValid()) {
            return false;
        }
        Player holder = accessor.getComponent(player, Player.getComponentType());
        return holder != null && holder.getWindowManager().getWindow(window.getId()) == window;
    }

    /**
     * Closes window if player still holds it: closing one the client already closed would throw from WindowManager.
     * A failure is logged, never thrown.
     */
    public static void closeIfHeld(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor, Window window) {
        try {
            if (holds(player, accessor, window)) {
                window.close(player, accessor);
            }
        } catch (RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("hycolony: could not close a page's window");
        }
    }
}
