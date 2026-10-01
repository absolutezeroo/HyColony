package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.citizen.CitizenFeeding;
import dev.hycolony.core.citizen.food.HandFeeding;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * "Use" on a citizen opens its window (MC WindowCitizen), or feeds it when the player holds food (MC
 * EntityCitizen.mobInteract). Every NPC role gets Interactions.Use = "*UseNPC" (RoleBuilderSystem), so
 * UseEntityInteraction fires UseEntityEvent.Pre on the player before running it; cancelling it keeps the vanilla NPC
 * interaction from starting, as HutBlockSystems.Use does for huts.
 */
public final class CitizenUseSystem extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final WorldRuntimes runtimes;

    public CitizenUseSystem(WorldRuntimes runtimes) {
        super(UseEntityEvent.Pre.class);
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull UseEntityEvent.Pre event) {
        try {
            Ref<EntityStore> target = event.getTargetEntity();
            if (event.getInteractionType() != InteractionType.Use || !target.isValid()) {
                return;
            }
            CitizenTag tag = store.getComponent(target, HyColonyComponents.citizenTag());
            if (tag == null) {
                return;
            }
            event.setCancelled(true);
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
            if (rt == null || !rt.enabled() || player == null) {
                return;
            }
            if (!fed(rt, player, tag, event.getContext())) {
                rt.manager().windows().openCitizen(player.getUuid(), tag.colonyId(), tag.citizenId());
            }
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony citizen use failed");
        }
    }

    /**
     * MC EntityCitizen.mobInteract with food in hand: the core feeds the citizen and one food leaves the player's hand
     * when it was eaten (or poisonous). False when the held item is no food: the window opens instead. [in-game]: the
     * Use context of an entity holds the item in hand (sp4b-hytale-food § 7).
     */
    private static boolean fed(WorldRuntime rt, PlayerRef player, CitizenTag tag, InteractionContext ctx) {
        ItemStack held = ctx.getHeldItem();
        if (held == null || held.isEmpty()) {
            return false;
        }
        HandFeeding.Outcome outcome = new CitizenFeeding(rt.manager())
                .offer(player.getUuid(), tag.colonyId(), tag.citizenId(), new ItemKey(held.getItemId()));
        if (outcome == HandFeeding.Outcome.FED || outcome == HandFeeding.Outcome.POISONED) {
            ItemContainer hand = ctx.getHeldItemContainer();
            if (hand != null) {
                hand.removeItemStackFromSlot(ctx.getHeldItemSlot(), 1);
            }
        }
        return outcome != HandFeeding.Outcome.NOT_FOOD;
    }
}
