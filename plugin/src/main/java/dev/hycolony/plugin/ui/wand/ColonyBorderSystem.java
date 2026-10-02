package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.wand.ColonyBorder;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * MC ColonyBorderRenderer, server side: every {@link #CHECK_SECONDS} on each world's thread, the borders the core
 * draws ({@link ColonyBorder}) are sent to each player holding the build tool ({@link BorderShapes}). Redrawn when the
 * nearest colony or the player's cell changes (MC), or the view; renewed before they expire; cleared when the tool is
 * put away.
 */
final class ColonyBorderSystem extends TickingSystem<EntityStore> {
    /** Seconds between two looks at the players: a held tool shows its borders within that. */
    static final float CHECK_SECONDS = 0.25f;
    /** Unchanged borders are sent again that often, before they expire. */
    private static final long RENEW_NANOS = TimeUnit.SECONDS.toNanos(10);
    /** A shape's life in seconds: the renewal plus two looks, so that no gap shows. */
    private static final float LIFETIME_SECONDS = 10 + 2 * CHECK_SECONDS;
    /** Hytale's chunks, in which the view radius counts, are 32 blocks: two claim cells. */
    private static final int CELLS_PER_CHUNK = 2;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** What a player's borders were drawn for: the player's entity (a new one after a world change), and the rest. */
    private record Key(Ref<EntityStore> entity, int colonyId, ClaimCell cell, int viewCells) {}

    /** The borders a player was last sent, and when (System.nanoTime). */
    private record Sent(Key key, long nanos) {}

    private final WorldRuntimes runtimes;
    private final String buildTool;
    /** Seconds since each world's last look, one box per world: nothing is allocated per tick. */
    private final Map<String, float[]> sinceCheck = new ConcurrentHashMap<>();
    /** By player, across worlds (each on its own thread). ponytail: a player leaving while drawn stays here. */
    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();
    /** The first failure is logged SEVERE, the next ones FINE. */
    private volatile boolean failed;

    ColonyBorderSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
        this.buildTool = runtimes.setup().ids().itemId("build_tool");
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        float[] elapsed = sinceCheck.computeIfAbsent(world.getName(), k -> new float[1]);
        elapsed[0] += dt;
        if (elapsed[0] < CHECK_SECONDS) {
            return;
        }
        elapsed[0] = 0f;
        WorldRuntime rt = runtimes.of(world);
        if (rt == null || !rt.enabled()) {
            return;
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            // Out of a TickingSystem, an exception would stop the world's thread; one player's never stops another's.
            try {
                refresh(rt, store, player);
            } catch (RuntimeException e) {
                LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony colony border failed");
                failed = true;
            }
        }
    }

    /** Sends, renews or clears {@code player}'s borders. */
    private void refresh(WorldRuntime rt, Store<EntityStore> store, PlayerRef player) {
        Ref<EntityStore> entity = player.getReference();
        Optional<Key> key = entity == null || !entity.isValid() ? Optional.empty() : key(rt, store, entity);
        Sent last = sent.get(player.getUuid());
        PacketHandler out = player.getPacketHandler();
        if (key.isEmpty()) {
            if (last != null) {
                sent.remove(player.getUuid());
                BorderShapes.clear(out);
            }
            return;
        }
        long now = System.nanoTime();
        if (last != null) {
            if (last.key().equals(key.get())) {
                if (now - last.nanos() < RENEW_NANOS) {
                    return;
                }
            } else {
                BorderShapes.clear(out); // the old borders would stay until they expire
            }
        }
        Key k = key.get();
        BorderShapes.send(
                out, ColonyBorder.lines(rt.manager(), k.colonyId(), k.cell(), k.viewCells()), LIFETIME_SECONDS);
        sent.put(player.getUuid(), new Sent(k, now));
    }

    /** What to draw for a player holding the build tool near a colony; empty otherwise. */
    private Optional<Key> key(WorldRuntime rt, Store<EntityStore> store, Ref<EntityStore> entity) {
        ItemStack held = InventoryComponent.getItemInHand(store, entity);
        if (held == null || held.isEmpty() || !buildTool.equals(held.getItemId())) {
            return Optional.empty();
        }
        TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
        Player component = store.getComponent(entity, Player.getComponentType());
        if (transform == null || component == null) {
            return Optional.empty();
        }
        Vector3d at = transform.getPosition();
        BlockPos pos = new BlockPos((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        int viewCells = component.getViewRadius() * CELLS_PER_CHUNK;
        return ColonyBorder.nearest(rt.manager(), pos).map(c -> new Key(entity, c.id(), ClaimCell.of(pos), viewCells));
    }
}
