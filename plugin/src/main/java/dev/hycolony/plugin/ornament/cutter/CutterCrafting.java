package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.MaterialTags;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.cutter.CutterCraft;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting at the architect's cutter (MC DO ArchitectsCutterRecipe.assemble and the ArchitectsCutterContainer output
 * slot's onTake): the recipe checked on the world thread, the variant requested off it, the slots checked again once
 * it exists, then 1 taken from each slot used (nothing from a creative player) and the DO quantity given. Nothing is
 * taken or given when anything changed or failed.
 */
final class CutterCrafting {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterCrafting() {}

    /** One craft: where, who, the block's slots, the shape, the tags, the registry, and the page to redraw. */
    record Request(
            World world,
            Ref<EntityStore> player,
            ItemContainer slots,
            OrnamentShape shape,
            MaterialTags tags,
            OrnamentVariantRegistry registry,
            Runnable redraw) {}

    /** Starts crafting; call it on the world thread. The variant is never awaited here. */
    static void craft(Request request) {
        CutterCraft.Result first =
                CutterCraft.check(request.shape(), CutterSlots.read(request.slots()), request.tags());
        if (!(first instanceof CutterCraft.Ready ready)) {
            return; // the craft button is disabled while the preview is not ready
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
        CutterCraft.Result now = CutterCraft.check(
                request.shape(), CutterSlots.read(request.slots()), request.tags(), creative(store, request.player()));
        if (!(now instanceof CutterCraft.Ready ready)
                || !ready.key().blockTypeKey().equals(asked.key().blockTypeKey())
                || !take(request.slots(), ready.consumed())) {
            say(player, "hycolony.ornament.cutter.changed");
            return;
        }
        String item = ready.key().blockTypeKey();
        SimpleItemContainer.addOrDropItemStack(
                store,
                request.player(),
                InventoryComponent.getCombined(store, request.player(), InventoryComponent.HOTBAR_FIRST),
                new ItemStack(item, ready.quantity()));
        player.sendMessage(Message.translation("hycolony.ornament.cutter.crafted")
                .param("p0", String.valueOf(ready.quantity()))
                .param("p1", CutterPage.itemName(item)));
        request.redraw().run();
    }

    /** Takes 1 from each slot; if one fails, gives back what was taken and returns false. */
    private static boolean take(ItemContainer slots, List<Integer> consumed) {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot : consumed) {
            ItemStack before = slots.getItemStack((short) slot);
            if (before == null || before.isEmpty()) {
                giveBack(slots, consumed, taken);
                return false;
            }
            ItemStackSlotTransaction removal = slots.removeItemStackFromSlot((short) slot, 1);
            ItemStack output = removal.getOutput();
            if (!removal.succeeded()) {
                giveBack(slots, consumed, taken);
                return false;
            }
            taken.add(output == null || output.isEmpty() ? new ItemStack(before.getItemId(), 1) : output);
        }
        return true;
    }

    /** Puts each taken item back in the slot it came from; one that does not fit is logged (lost). */
    private static void giveBack(ItemContainer slots, List<Integer> consumed, List<ItemStack> taken) {
        for (int i = 0; i < taken.size(); i++) {
            if (!slots.addItemStackToSlot(consumed.get(i).shortValue(), taken.get(i))
                    .succeeded()) {
                LOG.at(Level.SEVERE).log(
                        "hyornament: cutter could not give back %s",
                        taken.get(i).getItemId());
            }
        }
    }

    /** Whether the player is in creative mode (as adapter/HytalePlayerDirectory reads it). */
    private static boolean creative(Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    /** Sends a translated message to player. */
    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }
}
