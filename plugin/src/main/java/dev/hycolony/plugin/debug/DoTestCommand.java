package dev.hycolony.plugin.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * /hycolony dotest [clear], operators only: creates a new texture and BlockType at runtime, places it 2 blocks in
 * front of the player, and clears the placed blocks on {@code clear}.
 *
 * <p>Temporary experiment for the Domum Ornamentum port (docs/research/domum-ornamentum.md B.6): it answers whether a
 * connected client renders a BlockType created at runtime without reconnecting. Remove it after the in-game test.
 */
public final class DoTestCommand extends AbstractPlayerCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final int DISTANCE = 2;

    private final RuntimeBlockFactory factory;
    // Commands from different worlds run on different threads.
    private final Queue<Placed> placed = new ConcurrentLinkedQueue<>();
    private final AtomicInteger next = new AtomicInteger(1);

    /** @param packKey the plugin's asset pack name ({@code getIdentifier().toString()}) */
    public DoTestCommand(String packKey) {
        super("dotest", "Runtime block type experiment (operators)");
        this.factory = new RuntimeBlockFactory(packKey);
        setPermissionGroups(new String[0]);
        addSubCommand(new Clear());
    }

    /**
     * Picks the cell on the world thread, then creates the texture and BlockType off it and places the block back on
     * it; a failure is logged SEVERE and reported with its step name.
     */
    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        int n = next.getAndIncrement();
        Placed at = inFront(store, ref, world);
        // World.tick holds AssetRegistry.ASSET_LOCK's read lock and loadAssets needs its write lock: loading assets on
        // the world thread deadlocks it (a ReentrantReadWriteLock cannot upgrade).
        CompletableFuture.runAsync(() -> create(n, at, player)).whenComplete((v, t) -> {
            if (t != null) {
                LOG.at(Level.SEVERE).withCause(t).log("dotest %d failed", n);
            }
        });
    }

    /** Off the world thread: registers texture and BlockType {@code HyColony_DoTest_<n>}, then queues the placing. */
    private void create(int n, Placed at, PlayerRef player) {
        String id = "HyColony_DoTest_" + n;
        String step = "compose";
        try {
            byte[] png = factory.composeTexture();
            long start = System.nanoTime();
            step = "texture";
            String texture = factory.registerTexture("Test_" + n, png);
            step = "blocktype";
            factory.registerBlockType(id, texture);
            at.world().execute(() -> place(id, at, player, start));
        } catch (RuntimeException e) {
            fail(e, step, id, player);
        }
    }

    /** On the world thread: places {@code id} at {@code at}, records it and reports the elapsed milliseconds. */
    private void place(String id, Placed at, PlayerRef player, long start) {
        try {
            at.world().setBlock(at.x(), at.y(), at.z(), id);
            placed.add(at);
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.at(Level.INFO).log("dotest: created %s at %d %d %d in %d ms", id, at.x(), at.y(), at.z(), ms);
            say(player, "hycolony.dotest.created", id, String.valueOf(ms));
        } catch (RuntimeException e) {
            fail(e, "place", id, player);
        }
    }

    /** Logs the failure SEVERE and tells the player (sendMessage is safe off the world thread). */
    private static void fail(RuntimeException e, String step, String id, PlayerRef player) {
        LOG.at(Level.SEVERE).withCause(e).log("dotest failed at step %s for %s", step, id);
        say(player, "hycolony.dotest.failed", step);
    }

    /** The feet-level cell {@link #DISTANCE} blocks along the view yaw (x = -sin, z = -cos, see HeadRotation). */
    private static Placed inFront(Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        Vector3d p =
                store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        float yaw = store.getComponent(ref, HeadRotation.getComponentType())
                .getRotation()
                .yaw();
        double x = p.x - Math.sin(yaw) * DISTANCE;
        double z = p.z - Math.cos(yaw) * DISTANCE;
        return new Placed(world, (int) Math.floor(x), (int) Math.floor(p.y), (int) Math.floor(z));
    }

    /** Sends the translated {@code key} with its parameters to the player. */
    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }

    /** A block this command placed, kept only in memory. */
    private record Placed(World world, int x, int y, int z) {}

    /**
     * Sets every recorded cell back to air on its own world thread. Positions live only in memory, so blocks placed
     * before a server restart are no longer cleared.
     */
    private final class Clear extends AbstractPlayerCommand {
        Clear() {
            super("clear", "Remove the blocks placed by dotest (operators)");
            setPermissionGroups(new String[0]);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            int count = 0;
            try {
                for (Placed at = placed.poll(); at != null; at = placed.poll()) {
                    Placed cell = at;
                    cell.world()
                            .execute(() -> cell.world().setBlock(cell.x(), cell.y(), cell.z(), BlockType.EMPTY_KEY));
                    count++;
                }
                say(player, "hycolony.dotest.cleared", String.valueOf(count));
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("dotest clear failed");
                say(player, "hycolony.dotest.failed", "clear");
            }
        }
    }
}
