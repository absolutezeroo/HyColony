package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.decoration.FlowerPot;
import dev.hycolony.core.decoration.FlowerPotBlocks;
import dev.hycolony.plugin.IdMap;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A player using a flower pot of the Decorations pack: asks the core {@link FlowerPot} rule what happens, then
 * {@link FlowerPotUse} does it. The event is not cancelled on success: a cancelled UseBlock fails, and the held item's
 * fallback would then place the plant beside the pot or harvest it (docs/research/carpets-flower-pots.md § 6); the
 * pot's own interaction is a no-op.
 */
public final class FlowerPotSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final FlowerPotBlocks blocks;
    private final FlowerPot rule;

    public FlowerPotSystem(IdMap ids) {
        super(UseBlockEvent.Pre.class);
        this.blocks = new FlowerPotBlocks(ids.flowerPots());
        this.rule = new FlowerPot(blocks.plants());
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
            @Nonnull UseBlockEvent.Pre event) {
        try {
            FlowerPotBlocks.Pot pot = blocks.find(event.getBlockType().getId()).orElse(null);
            if (pot == null) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            ItemStack held = event.getContext().getHeldItem();
            Optional<String> heldId = held == null || held.isEmpty() ? Optional.empty() : Optional.of(held.getItemId());
            Player player = buffer.getComponent(ref, Player.getComponentType());
            boolean creative = player != null && player.getGameMode() == GameMode.Creative;
            FlowerPot.Outcome outcome = rule.use(pot.plant(), heldId, creative);
            apply(
                    outcome,
                    pot.pot(),
                    new FlowerPotUse(
                            store.getExternalData().getWorld(),
                            event.getTargetBlock(),
                            event.getContext(),
                            ref,
                            buffer));
        } catch (RuntimeException e) {
            event.setCancelled(true);
            LOG.at(Level.SEVERE).withCause(e).log("HyColony flower pot use failed at %s", event.getTargetBlock());
        }
    }

    /** Carries out {@code outcome} on a pot whose empty block (its colour) is {@code pot}. */
    private void apply(FlowerPot.Outcome outcome, String pot, FlowerPotUse use) {
        switch (outcome) {
            case FlowerPot.Plant plant -> {
                // Pot first: a hand that changed meanwhile then undoes it, and nothing is lost.
                String potted = blocks.block(pot, Optional.of(plant.plant())).orElse(null);
                if (use.swap(potted) && plant.consume() && !use.takeOneHeld()) {
                    use.swap(pot);
                }
            }
            case FlowerPot.GiveBack back -> {
                if (use.swap(pot)) {
                    use.give(back.plant());
                }
            }
            case FlowerPot.Nothing _ -> {
                // MC returns CONSUME: the use ends here, nothing changes.
            }
        }
    }
}
