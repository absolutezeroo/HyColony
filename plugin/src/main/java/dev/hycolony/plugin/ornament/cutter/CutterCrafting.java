package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.cutter.CutterCraft;
import dev.hycolony.core.ornament.cutter.SlotContent;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting at the architect's cutter (MC DO ArchitectsCutterRecipe.assemble and the ArchitectsCutterContainer output
 * slot's onTake): the recipe checked on the world thread, the variant requested off it, the player's materials checked
 * again once it exists, then 1 of each material used taken per craft from their inventory (nothing from a creative
 * player) and the DO quantity given per craft. Nothing is taken or given when anything changed or failed.
 */
final class CutterCrafting {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterCrafting() {}

    /**
     * One click: where, who, the shape, the chosen material of each slot, the tags, the registry, how many crafts
     * were asked (capped by what the materials allow), and the page redraw to run afterwards if it is still shown.
     */
    record Request(
            World world,
            Ref<EntityStore> player,
            OrnamentShape shape,
            List<String> materials,
            MaterialTags tags,
            OrnamentVariantRegistry registry,
            int crafts,
            Runnable redraw) {
        Request {
            materials = List.copyOf(materials);
        }
    }

    /** Starts crafting; call it on the world thread. The variant is never awaited here. */
    static void craft(Request request) {
        Map<String, Integer> inventory = CutterInventory.counts(request.player().getStore(), request.player());
        CutterCraft.Result first = CutterCraft.check(request.shape(), slots(request, inventory), request.tags());
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

    /** On the world thread, once the variant exists (or failed): check again, take, give. */
    private static void finish(Request request, CutterCraft.Ready asked, @Nullable Throwable error) {
        if (!request.player().isValid()) {
            return; // the player left: nothing taken, the variant stays for next time
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
        Map<String, Integer> inventory = CutterInventory.counts(store, request.player());
        CutterCraft.Result now =
                CutterCraft.check(request.shape(), slots(request, inventory), request.tags(), creative(store, request));
        if (!(now instanceof CutterCraft.Ready ready)
                || !ready.key().blockTypeKey().equals(asked.key().blockTypeKey())) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        int crafts = Math.min(request.crafts(), CutterCraft.maxCrafts(ready, inventory));
        if (crafts <= 0 || !CutterInventory.take(store, request.player(), materials(ready, crafts))) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        String item = ready.key().blockTypeKey();
        CutterInventory.give(store, request.player(), item, ready.quantity() * crafts);
        player.sendMessage(Message.translation("hycolony.ornament.cutter.crafted")
                .param("p0", String.valueOf(ready.quantity() * crafts))
                .param("p1", CutterDrawing.itemName(item)));
        request.redraw().run();
    }

    /** The recipe slots: each chosen material with what the player holds of it. */
    private static List<SlotContent> slots(Request request, Map<String, Integer> inventory) {
        return request.materials().stream()
                .map(id -> id.isEmpty() ? SlotContent.EMPTY : new SlotContent(id, inventory.getOrDefault(id, 0)))
                .toList();
    }

    /** What crafts crafts of ready take: 1 of each consumed slot's material per craft. */
    private static Map<String, Integer> materials(CutterCraft.Ready ready, int crafts) {
        Map<String, Integer> materials = new HashMap<>();
        ready.consumed().forEach(slot -> materials.merge(ready.key().materials().get(slot), crafts, Integer::sum));
        return materials;
    }

    /** Whether the player is in creative mode (as adapter/HytalePlayerDirectory reads it). */
    private static boolean creative(Store<EntityStore> store, Request request) {
        Player player = store.getComponent(request.player(), Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    /** Sends a translated message to player. */
    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }
}
