package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.Map;
import java.util.UUID;

/** A hut's own container, opened from its window's Storage button. World thread only. */
final class HutStorage {
    private final PlayerRef playerRef;
    private final ColonyManager manager;
    private final BlockPos pos;

    HutStorage(PlayerRef playerRef, ColonyManager manager, BlockPos pos) {
        this.playerRef = playerRef;
        this.manager = manager;
        this.pos = pos;
    }

    /**
     * Opens the container as vanilla OpenContainerInteraction does, if the core lets the player (MC
     * OpenInventoryMessage: MANAGE_HUTS, a refusal told). On close the window leaves the block's window table (or it
     * never opens again) and the core re-checks the building's stuck requests.
     */
    void open(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!manager.hutWindows().mayOpenInventory(playerRef.getUuid(), pos)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        ItemContainerBlock container =
                BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, pos.x(), pos.y(), pos.z());
        if (section == null || container == null) {
            tell(Msg.of("hycolony.ui.building.noStorage"));
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type =
                blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        if (type == null || type.getItem() == null) {
            return; // ContainerBlockWindow reads the block's item id
        }
        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x(),
                pos.y(),
                pos.z(),
                blocks.getRotationIndex(pos.x(), pos.y(), pos.z()),
                type,
                container.getItemContainer());
        show(ref, store, world, window, container.getWindows());
    }

    /**
     * Opens {@code window} for this player, unless they have it open already or have no Player component; its close
     * re-checks the hut's requests on the world thread.
     */
    private void show(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            World world,
            ContainerBlockWindow window,
            Map<UUID, ContainerBlockWindow> windows) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        UUID player = playerRef.getUuid();
        if (windows.putIfAbsent(player, window) != null) {
            return; // already open
        }
        if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            window.registerCloseEvent(e -> {
                windows.remove(player, window);
                world.execute(() -> manager.requestActions().onContainerChanged(pos));
            });
        } else {
            windows.remove(player, window);
        }
    }

    private void tell(Msg msg) {
        playerRef.sendMessage(HytaleNotifier.toMessage(msg));
    }
}
