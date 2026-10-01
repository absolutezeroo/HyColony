package dev.hycolony.core.app.api;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.perf.PartTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The api's view of one world's colonies (spec 2026-09-30, § 4). Every call checks it runs on the world's thread, where
 * the core lives without locks: elsewhere it throws, as Hytale's Store.assertThread does. Deviation from CLAUDE.md
 * § 4 by design: this is no port, and a call from another thread is an addon's programming error.
 */
public final class CoreColonyWorld implements ColonyWorld {
    private final ColonyManager manager;
    private final BooleanSupplier onWorldThread;
    private final String world;
    private final ApiEvents events;
    private final CoreDebugAccess debug = new CoreDebugAccess(this);

    /** The api over {@code manager}'s colonies; {@code onWorldThread} tells whether the caller runs on its thread. */
    public CoreColonyWorld(ColonyManager manager, BooleanSupplier onWorldThread) {
        this.manager = manager;
        this.onWorldThread = onWorldThread;
        this.world = manager.context().world().name();
        this.events = new ApiEvents(manager.context().bus(), world);
    }

    @Override
    public List<ColonySummary> colonies() {
        checkThread();
        return manager.all().stream().map(c -> ApiSnapshots.colony(ref(c), c)).toList();
    }

    @Override
    public Optional<ColonySummary> colony(ColonyRef ref) {
        checkThread();
        return find(ref).map(c -> ApiSnapshots.colony(ref, c));
    }

    @Override
    public List<CitizenSnapshot> citizens(ColonyRef colony) {
        checkThread();
        return find(colony)
                .map(c -> c.citizens().all().stream()
                        .map(d -> ApiSnapshots.citizen(colony, c, d))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public Optional<CitizenSnapshot> citizen(CitizenRef ref) {
        checkThread();
        return find(ref.colony())
                .flatMap(c -> c.citizens().get(ref.citizenId()).map(d -> ApiSnapshots.citizen(ref.colony(), c, d)));
    }

    @Override
    public List<BuildingSnapshot> buildings(ColonyRef colony) {
        checkThread();
        return find(colony)
                .map(c -> c.buildings().all().stream()
                        .map(b -> ApiSnapshots.building(colony, b))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public List<RequestSnapshot> requests(ColonyRef colony) {
        checkThread();
        return find(colony)
                .map(c -> c.requests().all().stream()
                        .map(r -> ApiSnapshots.request(colony, c, r))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        checkThread();
        return events.subscribe(type, listener);
    }

    @Override
    public DebugAccess debug() {
        checkThread();
        return debug;
    }

    /** The colony {@code ref} names, if it is of this world. */
    Optional<Colony> find(ColonyRef ref) {
        return ref.world().equals(world) ? manager.byId(ref.colonyId()) : Optional.empty();
    }

    private ColonyRef ref(Colony c) {
        return new ColonyRef(world, c.id());
    }

    /** How long each part of this world's core took over the last minute, the heaviest first. */
    List<PartTime> timings() {
        return manager.context().timings().lastMinute();
    }

    /** Throws {@link IllegalStateException} off the world's thread. */
    void checkThread() {
        if (!onWorldThread.getAsBoolean()) {
            throw new IllegalStateException("HyColony's api was called outside the thread of world " + world);
        }
    }
}
