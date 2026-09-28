package dev.hyblockui.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a callback whenever the player's inventory or one of the given containers changes, so that a page showing
 * them stays current (as StructuralCraftingWindow follows the combined inventory). World thread.
 */
public final class InventoryWatch {
    private final List<EventRegistration<?, ?>> registrations = new ArrayList<>();

    private InventoryWatch() {}

    /**
     * Starts following player's inventory (hotbar, storage, armor) and others; onChange runs on the thread
     * that changed them.
     */
    public static InventoryWatch start(
            Store<EntityStore> store, Ref<EntityStore> player, Runnable onChange, ItemContainer... others) {
        InventoryWatch watch = new InventoryWatch();
        watch.follow(InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_FIRST), onChange);
        watch.follow(PlayerSection.ARMOR.container(store, player), onChange);
        for (ItemContainer other : others) {
            watch.follow(other, onChange);
        }
        return watch;
    }

    /** Stops following; safe to call more than once. */
    public void stop() {
        registrations.forEach(EventRegistration::unregister);
        registrations.clear();
    }

    private void follow(ItemContainer container, Runnable onChange) {
        registrations.add(container.registerChangeEvent(e -> onChange.run()));
    }
}
