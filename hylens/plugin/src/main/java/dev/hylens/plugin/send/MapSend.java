package dev.hylens.plugin.send;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.worldmap.TeleportToWorldMapPosition;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ApiText;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.send.ArmedMaps;
import dev.hylens.core.send.SendTarget;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.UUID;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * "Send here" by the map (spec 2026-09-30, § 6.6): once an operator armed it, their next map "Teleport" sends the
 * citizen they watch or chose instead of them. The packet only gives X and Z, so the ground is found as vanilla's
 * GamePacketHandler.handleTeleportToWorldMapPosition finds it: the chunk loaded, then its heightmap. Out of the armed
 * mode, the packet goes on untouched.
 */
public final class MapSend {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The chunk loading flags vanilla's map teleport uses. */
    private static final int CHUNK_FLAGS = 32;

    private final ArmedMaps armed = new ArmedMaps();
    private final Watches watches;
    private final Menus menus;
    private volatile @Nullable PacketFilter filter;

    public MapSend(Watches watches, Menus menus) {
        this.watches = watches;
        this.menus = menus;
    }

    /** Starts reading the players' map packets; PacketAdapters is static, so {@link #stop} must follow. */
    public void start() {
        filter = PacketAdapters.registerInbound((PlayerPacketFilter) this::intercept);
    }

    /** Stops reading the map packets, as HyLens stops: its filter would outlive it in the server. */
    public void stop() {
        PacketFilter f = filter;
        filter = null;
        if (f != null) {
            PacketAdapters.deregisterInbound(f);
        }
    }

    /** {@code operator}'s next map "Teleport" sends a citizen. */
    public void arm(UUID operator) {
        armed.arm(operator);
    }

    /** {@code operator}'s map teleports them again, as they leave. */
    public void disarm(UUID operator) {
        armed.disarm(operator);
    }

    /**
     * Network thread: takes an armed operator's map "Teleport" and hands it to their world's thread; true consumes
     * it, false lets vanilla teleport them.
     */
    private boolean intercept(PlayerRef player, Packet packet) {
        if (!(packet instanceof TeleportToWorldMapPosition p) || !armed.use(player.getUuid())) {
            return false;
        }
        try {
            @Nullable Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                World world = ref.getStore().getExternalData().getWorld();
                world.execute(() -> ground(player, world, p.x, p.y));
            }
        } catch (RuntimeException e) { // a world stopping refuses tasks: the click is lost
            LOG.at(Level.FINE).withCause(e).log("HyLens: sending a citizen by the map failed");
        }
        return true;
    }

    /** World thread: loads the chunk of column {@code x z}, then sends the citizen onto its ground. */
    private void ground(PlayerRef player, World world, int x, int z) {
        world.getChunkStore()
                .getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock(x, z), CHUNK_FLAGS)
                .thenAcceptAsync(chunk -> send(player, world, chunk, x, z), world)
                .exceptionally(e -> {
                    LOG.at(Level.SEVERE).withCause(e).log("HyLens: sending a citizen by the map failed");
                    return null;
                });
    }

    /** World thread: sends the citizen onto the highest opaque block of column {@code x z}, or says there is none. */
    private void send(PlayerRef player, World world, @Nullable Ref<ChunkStore> chunk, int x, int z) {
        @Nullable Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        @Nullable
        HeightmapColumn column =
                chunk == null ? null : chunk.getStore().getComponent(chunk, HeightmapColumn.getComponentType());
        int height = column == null ? Integer.MIN_VALUE : column.getHeight(x, z);
        ApiText told = height == Integer.MIN_VALUE
                ? ApiText.of("hylens.send.noGround")
                : SendHere.watchedOrChosen(
                        world, watches, menus, player.getUuid(), SendTarget.standingOn(x, height, z));
        player.sendMessage(ApiMessages.of(told));
    }
}
