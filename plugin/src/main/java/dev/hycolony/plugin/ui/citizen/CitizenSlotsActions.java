package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The take all, put all and quick stack buttons over a citizen's 27 slots (CitizenInventory.ui), the moves the
 * client's own container buttons ask for (InventoryPacketHandler InventoryAction: InventoryUtils.takeAll, putAll,
 * quickStack). Each item that lands in or leaves the citizen's slots goes through its container, which reports it to
 * the core.
 */
final class CitizenSlotsActions {
    private static final String TAKE_ALL = "takeAll";
    private static final String PUT_ALL = "putAll";
    private static final String QUICK_STACK = "quickStack";

    private CitizenSlotsActions() {}

    /** Binds the three buttons to their actions. */
    static void bind(UIEventBuilder events) {
        button(events, "#TakeAllButton", TAKE_ALL);
        button(events, "#PutAllButton", PUT_ALL);
        button(events, "#QuickStackButton", QUICK_STACK);
    }

    private static void button(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    /** Hytale's move for {@code action} on the window {@code section}; an unknown action does nothing. */
    static void apply(String action, Ref<EntityStore> ref, Store<EntityStore> store, int section) {
        switch (action) {
            case TAKE_ALL -> InventoryUtils.takeAll(ref, section, settings(store, ref), store);
            case PUT_ALL -> InventoryUtils.putAll(ref, section, store);
            case QUICK_STACK -> InventoryUtils.quickStack(ref, section, store);
            default -> {}
        }
    }

    /** The player's settings, which tell where a taken item goes; Hytale's defaults without them. */
    private static PlayerSettings settings(Store<EntityStore> store, Ref<EntityStore> ref) {
        PlayerSettings settings = store.getComponent(ref, PlayerSettings.getComponentType());
        return settings == null ? PlayerSettings.defaults() : settings;
    }
}
