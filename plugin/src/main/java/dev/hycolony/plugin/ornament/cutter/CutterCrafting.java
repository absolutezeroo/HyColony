package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.cutter.CutterCraft;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.inventory.PlayerItems;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.List;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting at the architect's cutter (MC DO ArchitectsCutterRecipe.assemble and the ArchitectsCutterContainer output
 * slot's onTake): the recipe checked on the world thread, the variant requested off it, the slots checked again once
 * it exists, then 1 taken from each slot used per craft (nothing from a creative player) and the DO quantity given
 * per craft. Nothing is taken or given when anything changed or failed.
 */
final class CutterCrafting {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterCrafting() {}

    /**
     * One click: where, who, the shape, the player's cutter slots, the tags, the registry, how many crafts were asked
     * (capped by what the slots allow), and the page redraw to run afterwards if it is still shown.
     */
    record Request(
            World world,
            Ref<EntityStore> player,
            OrnamentShape shape,
            CutterSlots slots,
            MaterialTags tags,
            OrnamentVariantRegistry registry,
            int crafts,
            Runnable redraw) {}

    /** Starts crafting; call it on the world thread. The variant is never awaited here. */
    static void craft(Request request) {
        CutterCraft.Result first =
                CutterCraft.check(request.shape(), request.slots().contents(), request.tags());
        if (!(first instanceof CutterCraft.Ready ready)) {
            return; // the craft buttons are disabled while the preview is not ready
        }
        var _ = request.registry().request(List.of(ready.key())).whenComplete((variants, error) -> {
            try {
                request.world().execute(() -> finish(request, ready, error));
            } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing was taken
                LOG.at(Level.SEVERE).withCause(e).log(
                        "hyornament: cutter craft of %s dropped", ready.key().id());
            }
        });
    }

    /** Whether player is in creative mode (as adapter/HytalePlayerDirectory reads it); false when unknown. */
    static boolean creative(Store<EntityStore> store, Ref<EntityStore> player) {
        Player component = store.getComponent(player, Player.getComponentType());
        return component != null && component.getGameMode() == GameMode.Creative;
    }

    /** On the world thread, once the variant exists (or failed): check again, take, give. */
    private static void finish(Request request, CutterCraft.Ready asked, @Nullable Throwable error) {
        if (!request.player().isValid()) {
            return; // the player left: their slots went back to them with the window, the variant stays
        }
        Store<EntityStore> store = request.player().getStore();
        PlayerRef player = store.getComponent(request.player(), PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        if (error != null) {
            LOG.at(Level.SEVERE).withCause(error).log(
                    "hyornament: cutter could not create %s", asked.key().id());
            say(player, "hycolony.ornament.failed", "create");
            return;
        }
        var slots = request.slots().contents();
        CutterCraft.Result now =
                CutterCraft.check(request.shape(), slots, request.tags(), creative(store, request.player()));
        if (!(now instanceof CutterCraft.Ready ready)
                || !ready.key().blockTypeKey().equals(asked.key().blockTypeKey())) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        String item = ready.key().blockTypeKey();
        if (Item.getAssetMap().getAsset(item) == null) { // created but not loaded: take nothing rather than lose it
            say(player, "hycolony.ornament.failed", "create");
            return;
        }
        int crafts = Math.min(request.crafts(), CutterCraft.maxCrafts(ready, slots));
        if (crafts <= 0 || !request.slots().take(ready.consumed(), crafts)) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        PlayerItems.give(store, request.player(), item, ready.quantity() * crafts);
        player.sendMessage(Message.translation("hycolony.ornament.cutter.crafted")
                .param("p0", String.valueOf(ready.quantity() * crafts))
                .param("p1", CutterDrawing.itemName(item)));
        request.redraw().run();
    }

    /** Sends a translated message to player. */
    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }
}
