package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.ornament.cutter.CutterActions;
import dev.hycolony.core.ornament.cutter.CutterView;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The cutter's craft buttons: crafts at once (as DO), or, when crafting takes time, one by one through the queue
 * while its bar fills; the buttons wait while the queue runs. World thread.
 */
final class CutterCraftClicks {
    private final CutterPage.Setup setup;
    private final CutterActions actions;
    private final CutterSlots slots;
    private final @Nullable CutterCraftQueue queue;
    private final Runnable redraw;

    /** Clicks crafting actions' shape from slots, through queue when crafting takes time (null: at once). */
    CutterCraftClicks(
            CutterPage.Setup setup,
            CutterActions actions,
            CutterSlots slots,
            @Nullable CutterCraftQueue queue,
            Runnable redraw) {
        this.setup = setup;
        this.actions = actions;
        this.slots = slots;
        this.queue = queue;
        this.redraw = redraw;
    }

    /** Whether crafts are queued: the craft buttons wait. */
    boolean busy() {
        return queue != null && queue.busy();
    }

    /** Crafts up to crafts times what the slots allow now (Integer.MAX_VALUE: All); the click's redraw shows it. */
    void craft(Ref<EntityStore> ref, Store<EntityStore> store, int crafts) {
        if (queue == null) {
            request(ref, crafts, ok -> redraw.run());
            return;
        }
        int allowed = actions.view(slots.contents(), CutterCrafting.creative(store, ref))
                                .preview()
                        instanceof CutterView.Ready ready
                ? Math.min(crafts, ready.maxCrafts())
                : 0;
        queue.start(
                allowed,
                done -> request(ref, 1, ok -> {
                    done.accept(ok);
                    redraw.run();
                }));
    }

    /** The queued craft's progress (0-1), 0 when crafting is instant. */
    double progress() {
        return queue == null ? 0 : queue.progress();
    }

    /** Asks CutterCrafting for crafts crafts of the chosen shape; done hears whether it crafted. */
    private void request(Ref<EntityStore> ref, int crafts, Consumer<Boolean> done) {
        actions.shape()
                .ifPresentOrElse(
                        shape -> CutterCrafting.craft(new CutterCrafting.Request(
                                setup.world(),
                                ref,
                                shape,
                                slots,
                                setup.catalogs().materials().tags(),
                                setup.settings().registry(),
                                crafts,
                                done)),
                        () -> done.accept(false));
    }
}
