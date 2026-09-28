package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.ui.PageEvents;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a cutter page in step with its block's slots while the player looks at it. The listener runs inside the
 * container's own changes (a craft's removal, another player's move), so it never throws, and it stops itself once
 * the page is no longer the player's in the block's world (replaced, player gone or moved to another world).
 */
final class CutterFollower {
    private final PlayerRef player;
    private final World world;
    private final ItemContainer slots;
    private final CustomUIPage page;
    private final Runnable redraw;
    private @Nullable EventRegistration<?, ?> registration;

    CutterFollower(PlayerRef player, World world, ItemContainer slots, CustomUIPage page, Runnable redraw) {
        this.player = player;
        this.world = world;
        this.slots = slots;
        this.page = page;
        this.redraw = redraw;
    }

    /** Starts following the slots (once the page and its window are open). */
    void start() {
        registration = slots.registerChangeEvent(e -> PageEvents.guard(page.getClass(), this::follow));
    }

    /** Stops following; safe to call more than once. */
    void stop() {
        EventRegistration<?, ?> current = registration;
        registration = null;
        if (current != null) {
            current.unregister();
        }
    }

    /** Redraws while the player still looks at the page in this world, else stops. */
    private void follow() {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null
                || !ref.isValid()
                || !world.equals(ref.getStore().getExternalData().getWorld())) {
            stop();
            return;
        }
        Player shown = ref.getStore().getComponent(ref, Player.getComponentType());
        if (shown == null || !page.equals(shown.getPageManager().getCustomPage())) {
            stop();
            return;
        }
        redraw.run();
    }
}
