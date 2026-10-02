package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.app.wand.HutHandPlacement;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * A player placing a hut block by hand: the core decides (MC EventHandler.onPlayerInteract: the hut's placing rules,
 * then the build tool suggested unless a creative player crouches); an allowed placement registers the hut or begins
 * a foundation. Queries PlayerRef so only players trigger it.
 */
public final class HutPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private final WorldRuntimes runtimes;
    private final Map<String, BuildingType> huts;
    private final ItemKey buildTool;

    public HutPlaceSystem(WorldRuntimes runtimes) {
        super(PlaceBlockEvent.class);
        this.runtimes = runtimes;
        this.huts = HutBlockSystems.byItemId(runtimes.setup());
        this.buildTool = new ItemKey(runtimes.setup().ids().itemId("build_tool"));
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    /** Cancels the placement on a refusal; a failure cancels it too and is logged, never thrown. */
    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull PlaceBlockEvent event) {
        try {
            ItemStack held = event.getItemInHand();
            BuildingType type = held == null ? null : huts.get(held.getItemId());
            if (held == null || type == null) {
                return;
            }
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = HutBlockSystems.player(index, chunk, store);
            if (rt == null || !rt.enabled() || player == null) {
                event.setCancelled(true);
                return;
            }
            ColonyManager m = rt.manager();
            BlockPos pos = HutBlockSystems.pos(event.getTargetBlock());
            HutHandPlacement hand = new HutHandPlacement(m, rt.wand(), buildTool);
            ItemKey hut = new ItemKey(held.getItemId());
            boolean crouching = crouching(chunk.getReferenceTo(index), store);
            Optional<HutPlacement> placement = hand.handPlaced(player.getUuid(), pos, type.id(), hut, crouching);
            if (placement.isEmpty()) {
                event.setCancelled(true); // the build tool suggested, or no access to the colony's huts
                return;
            }
            int rotation = event.getRotation().yaw().getDegrees() / 90; // declared degrees, not the enum position
            switch (placement.get()) {
                case HutPlacement.Denied denied -> {
                    event.setCancelled(true);
                    player.sendMessage(HytaleNotifier.toMessage(denied.reason()));
                }
                // The core only returns this for a town hall.
                case HutPlacement.FoundNewColony _ ->
                    m.foundation().begin(player.getUuid(), player.getUsername(), pos, rotation);
                case HutPlacement.Allowed allowed ->
                    m.huts().place(allowed.colony(), type.id(), pos, rotation, player.getUuid());
            }
        } catch (RuntimeException e) {
            event.setCancelled(true);
            HutBlockSystems.failed("place", event.getTargetBlock(), e);
        }
    }

    /** Whether the player is crouching (MovementStates.crouching); false without movement states. */
    private static boolean crouching(Ref<EntityStore> ref, Store<EntityStore> store) {
        MovementStatesComponent states = store.getComponent(ref, MovementStatesComponent.getComponentType());
        return states != null && states.getMovementStates().crouching;
    }
}
