package dev.hycolony.core.app.api;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.PartTiming;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.core.app.diagnostics.Invariants;
import dev.hycolony.core.app.diagnostics.ViolationWatch;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.CitizenHistory;
import dev.hycolony.core.colony.Colony;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The api's debug reads of one world's colonies, and its actions ({@link CoreDebugActions}, {@link CoreDebugEdits})
 * (spec 2026-09-30, § 4.2): each call checks the thread as {@link CoreColonyWorld} does. {@link #check} keeps a
 * {@link ViolationWatch} per colony, so it confirms the lasting states across its calls.
 */
final class CoreDebugAccess implements DebugAccess {
    private final CoreColonyWorld world;
    private final Map<Integer, ViolationWatch> watches = new HashMap<>();
    private final CoreDebugActions actions;
    private final CoreDebugEdits edits;

    CoreDebugAccess(CoreColonyWorld world) {
        this.world = world;
        this.actions = new CoreDebugActions(world);
        this.edits = new CoreDebugEdits(world);
    }

    @Override
    public Optional<CitizenDebugSnapshot> inspect(CitizenRef ref) {
        world.checkThread();
        return world.find(ref.colony())
                .flatMap(c -> c.citizens().get(ref.citizenId()).map(d -> ApiDebugSnapshots.citizen(ref, c, d)));
    }

    @Override
    public List<HistoryEntry> history(CitizenRef ref) {
        world.checkThread();
        return citizen(ref)
                .map(d -> d.vitals().history().stream()
                        .map(ApiDebugSnapshots::entry)
                        .toList())
                .orElse(List.of());
    }

    @Override
    public List<Violation> check(ColonyRef ref) {
        world.checkThread();
        Colony c = world.find(ref).orElse(null);
        if (c == null) {
            return List.of();
        }
        long now = c.context().clock().currentTick();
        return watches.computeIfAbsent(c.id(), _ -> new ViolationWatch()).confirmed(Invariants.check(c), now).stream()
                .map(v -> ApiDebugSnapshots.violation(ref, v))
                .toList();
    }

    @Override
    public Optional<Subscription> track(CitizenRef ref) {
        world.checkThread();
        return citizen(ref).map(d -> {
            CitizenHistory.Tracking tracking = d.vitals().track();
            return tracking::close;
        });
    }

    @Override
    public ActionResult walkTo(Actor actor, CitizenRef ref, Pos target) {
        return actions.walkTo(actor, ref, target);
    }

    @Override
    public ActionResult forceLeisure(Actor actor, CitizenRef ref) {
        return actions.forceLeisure(actor, ref);
    }

    @Override
    public ActionResult teleport(Actor actor, CitizenRef ref, Pos target) {
        return actions.teleport(actor, ref, target);
    }

    @Override
    public ActionResult respawnBody(Actor actor, CitizenRef ref) {
        return actions.respawnBody(actor, ref);
    }

    @Override
    public ActionResult spawnCitizen(Actor actor, ColonyRef colony) {
        return edits.spawnCitizen(actor, colony);
    }

    @Override
    public ActionResult setSaturation(Actor actor, CitizenRef ref, double value) {
        return edits.setSaturation(actor, ref, value);
    }

    @Override
    public List<PartTiming> timings() {
        world.checkThread();
        return world.timings().stream()
                .map(p -> new PartTiming(p.part(), p.calls(), p.totalNanos(), p.maxNanos()))
                .toList();
    }

    private Optional<CitizenData> citizen(CitizenRef ref) {
        return world.find(ref.colony()).flatMap(c -> c.citizens().get(ref.citizenId()));
    }
}
