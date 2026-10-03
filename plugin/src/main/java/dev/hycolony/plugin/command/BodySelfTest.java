package dev.hycolony.plugin.command;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.WorldRuntime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Selftest steps of a citizen body: spawn one next to the player, walk it 3 blocks, climb it 3 blocks up then down in
 * place (the mechanics the ported pathfinding will use for ladders), despawn it.
 */
final class BodySelfTest {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks the body climbs up, then down. */
    private static final int CLIMB_BLOCKS = 3;

    private BodySelfTest() {}

    /**
     * Reports "spawn" at once, then "move", "climb up" and "climb down" as each ends: arrived, stopped, or after 15 s.
     */
    static void run(SelfTestReport report, WorldRuntime rt, World world, BlockPos at) {
        // Tag (-1, -1): if this body survives a crash, onBodyLoaded finds no colony -1 and despawns it.
        Optional<BodyId> body =
                rt.bodies().spawn(rt.manager().context().world(), at.offset(2, 0, 0), -1, -1, "SelfTest");
        report.line("spawn", body.isPresent(), "spawnNPCWithColumnProbe");
        body.ifPresent(b -> {
            Vec3 start = rt.bodies().position(b).orElseThrow();
            rt.bodies().moveTo(b, new Vec3(start.x() + 3, start.y(), start.z()));
            await(world, rt, b, moved -> {
                report.line("move", moved == NavStatus.ARRIVED, moved.name());
                Vec3 foot = rt.bodies().position(b).orElse(start);
                rt.bodies().climb(b, new Vec3(foot.x(), foot.y() + CLIMB_BLOCKS, foot.z()));
                await(world, rt, b, up -> {
                    report.line("climb up", up == NavStatus.ARRIVED, up.name());
                    rt.bodies().climb(b, foot);
                    await(world, rt, b, down -> {
                        report.line("climb down", down == NavStatus.ARRIVED, down.name());
                        rt.bodies().despawn(b);
                    });
                });
            });
        });
    }

    /** Polls the body's walk status every 500 ms, and gives it to {@code then} once not MOVING, or after 15 s. */
    private static void await(World world, WorldRuntime rt, BodyId b, Consumer<NavStatus> then) {
        long[] waited = {0};
        boolean[] scheduleWarned = {false};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            NavStatus s = rt.bodies().navStatus(b);
            waited[0] += 500;
            if (s == NavStatus.MOVING && waited[0] < 15_000) {
                scheduleLogged(world, poll[0], scheduleWarned);
                return;
            }
            then.accept(s);
        };
        scheduleLogged(world, poll[0], scheduleWarned);
    }

    /**
     * Reschedules {@code task} 500 ms out, like the poll loop above, and watches the dispatch off the world
     * thread so a failure is not silently dropped (CLAUDE.md sec 4): the first one logs WARNING, later calls
     * with the same {@code warnedOnce} flag log FINE.
     */
    private static void scheduleLogged(World world, Runnable task, boolean[] warnedOnce) {
        ScheduledFuture<?> future = world.scheduleAfter(task, 500, TimeUnit.MILLISECONDS);
        var _ = CompletableFuture.runAsync(() -> {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Level level = warnedOnce[0] ? Level.FINE : Level.WARNING;
                warnedOnce[0] = true;
                LOG.at(level).withCause(e.getCause()).log("HyColony selftest: scheduleAfter dispatch failed");
            }
        });
    }
}
