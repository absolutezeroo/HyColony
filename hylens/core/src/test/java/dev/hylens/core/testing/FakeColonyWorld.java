package dev.hylens.core.testing;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** A world of colonies held in maps, read through the api; its actions answer Done. */
public final class FakeColonyWorld implements ColonyWorld, DebugAccess {
    private final Map<ColonyRef, ColonySummary> colonies = new LinkedHashMap<>();
    private final Map<ColonyRef, List<CitizenSnapshot>> citizens = new LinkedHashMap<>();
    private final Map<CitizenRef, CitizenDebugSnapshot> debug = new LinkedHashMap<>();
    private final Map<ColonyRef, List<Violation>> alerts = new LinkedHashMap<>();

    /** Adds {@code colony} with its citizens {@code members}. */
    public FakeColonyWorld colony(ColonySummary colony, CitizenSnapshot... members) {
        colonies.put(colony.ref(), colony);
        citizens.put(colony.ref(), List.of(members));
        return this;
    }

    /** What {@code inspect} returns for its citizen. */
    public FakeColonyWorld debug(CitizenDebugSnapshot snapshot) {
        debug.put(snapshot.citizen(), snapshot);
        return this;
    }

    /** What {@code check} returns for {@code colony}. */
    public FakeColonyWorld alerts(ColonyRef colony, Violation... violations) {
        alerts.put(colony, List.of(violations));
        return this;
    }

    @Override
    public List<ColonySummary> colonies() {
        return List.copyOf(colonies.values());
    }

    @Override
    public Optional<ColonySummary> colony(ColonyRef ref) {
        return Optional.ofNullable(colonies.get(ref));
    }

    @Override
    public List<CitizenSnapshot> citizens(ColonyRef colony) {
        return citizens.getOrDefault(colony, List.of());
    }

    @Override
    public Optional<CitizenSnapshot> citizen(CitizenRef ref) {
        return citizens(ref.colony()).stream().filter(c -> c.ref().equals(ref)).findFirst();
    }

    @Override
    public List<BuildingSnapshot> buildings(ColonyRef colony) {
        return List.of();
    }

    @Override
    public List<RequestSnapshot> requests(ColonyRef colony) {
        return List.of();
    }

    @Override
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        return () -> {};
    }

    @Override
    public DebugAccess debug() {
        return this;
    }

    @Override
    public Optional<CitizenDebugSnapshot> inspect(CitizenRef ref) {
        return Optional.ofNullable(debug.get(ref));
    }

    @Override
    public List<HistoryEntry> history(CitizenRef ref) {
        return List.of();
    }

    @Override
    public List<Violation> check(ColonyRef colony) {
        return alerts.getOrDefault(colony, List.of());
    }

    @Override
    public Optional<Subscription> track(CitizenRef ref) {
        return Optional.of(() -> {});
    }

    @Override
    public ActionResult walkTo(Actor actor, CitizenRef ref, Pos target) {
        return new ActionResult.Done();
    }

    @Override
    public ActionResult forceLeisure(Actor actor, CitizenRef ref) {
        return new ActionResult.Done();
    }

    @Override
    public ActionResult teleport(Actor actor, CitizenRef ref, Pos target) {
        return new ActionResult.Done();
    }

    @Override
    public ActionResult respawnBody(Actor actor, CitizenRef ref) {
        return new ActionResult.Done();
    }
}
