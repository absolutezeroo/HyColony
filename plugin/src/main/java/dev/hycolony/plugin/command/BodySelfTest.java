package dev.hycolony.plugin.command;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.command.LogisticsSelfTest.SelfTestReport;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Selftest steps of a citizen body: spawn one next to the player, walk it 3 blocks, despawn it. */
final class BodySelfTest {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private BodySelfTest() {}

    /** Reports "spawn" at once and "move" once the body arrives, stops, or has walked for 15 s. */
    static void run(SelfTestReport report, WorldRuntime rt, World world, BlockPos at) {
        // Tag (-1, -1): if this body survives a crash, onBodyLoaded finds no colony -1 and despawns it.
        Optional<BodyId> body =
                rt.bodies().spawn(rt.manager().context().world(), at.offset(2, 0, 0), -1, -1, "SelfTest");
        report.line("spawn", body.isPresent(), "spawnNPCWithColumnProbe");
        body.ifPresent(b -> {
            Vec3 start = rt.bodies().position(b).orElseThrow();
            rt.bodies().moveTo(b, new Vec3(start.x() + 3, start.y(), start.z()));
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
                report.line("move", s == NavStatus.ARRIVED, s.name());
                rt.bodies().despawn(b);
            };
            scheduleLogged(world, poll[0], scheduleWarned);
        });
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
