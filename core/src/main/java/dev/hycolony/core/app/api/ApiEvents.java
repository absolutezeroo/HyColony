package dev.hycolony.core.app.api;

import dev.hycolony.api.Actor;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.event.BuildingLevelChanged;
import dev.hycolony.api.event.BuildingPlaced;
import dev.hycolony.api.event.BuildingRemoved;
import dev.hycolony.api.event.CitizenSpawned;
import dev.hycolony.api.event.ColonyCreated;
import dev.hycolony.api.event.ColonyDeleted;
import dev.hycolony.api.event.DayStarted;
import dev.hycolony.api.event.NightFell;
import dev.hycolony.api.event.WorkOrderCreated;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.event.EventBus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The api's stable events: each is heard by listening to the core event it comes from, translated into references
 * with its cause (spec 2026-09-30, § 4.2). Nothing listens to the core until an addon subscribes.
 */
final class ApiEvents {
    /** One api event type: the core event it comes from, and how it is translated. */
    private record Route<C>(Class<C> core, Function<C, Object> translate) {}

    private final EventBus bus;
    private final String world;
    private final Map<Class<?>, Route<?>> routes = new HashMap<>();

    ApiEvents(EventBus bus, String world) {
        this.bus = bus;
        this.world = world;
        routeColonies();
        routeBuildings();
        routeCitizensAndDays();
    }

    private void routeColonies() {
        route(
                ColonyCreated.class,
                ColonyEvents.ColonyCreated.class,
                e -> new ColonyCreated(ref(e.colony()), cause(e.player())));
        route(
                ColonyDeleted.class,
                ColonyEvents.ColonyDeleted.class,
                e -> new ColonyDeleted(new ColonyRef(world, e.colonyId()), cause(e.player())));
        route(
                WorkOrderCreated.class,
                ColonyEvents.WorkOrderCreated.class,
                e -> new WorkOrderCreated(
                        ref(e.colony()),
                        e.order().id(),
                        name(e.order().type()),
                        ApiSnapshots.pos(e.order().buildingPos()),
                        cause(e.player())));
    }

    private void routeBuildings() {
        route(
                BuildingPlaced.class,
                ColonyEvents.BuildingPlaced.class,
                e -> new BuildingPlaced(
                        ref(e.colony()),
                        type(e.building()),
                        ApiSnapshots.pos(e.building().position()),
                        cause(e.player())));
        route(
                BuildingRemoved.class,
                ColonyEvents.BuildingRemoved.class,
                e -> new BuildingRemoved(
                        ref(e.colony()),
                        type(e.building()),
                        ApiSnapshots.pos(e.building().position()),
                        cause(e.player())));
        route(
                BuildingLevelChanged.class,
                ColonyEvents.BuildingLevelChanged.class,
                e -> new BuildingLevelChanged(
                        ref(e.colony()),
                        type(e.building()),
                        ApiSnapshots.pos(e.building().position()),
                        e.oldLevel(),
                        e.newLevel(),
                        cause(e.player())));
    }

    private void routeCitizensAndDays() {
        route(
                CitizenSpawned.class,
                dev.hycolony.core.citizen.CitizenSpawned.class,
                e -> new CitizenSpawned(
                        new CitizenRef(ref(e.colony()), e.citizen().id())));
        route(
                DayStarted.class,
                ColonyEvents.DayStarted.class,
                e -> new DayStarted(ref(e.colony()), e.colony().day()));
        route(NightFell.class, ColonyEvents.NightFell.class, e -> new NightFell(ref(e.colony())));
    }

    /**
     * Delivers every later api event of {@code type} to {@code listener}, translated from its core event; throws
     * {@link IllegalArgumentException} when {@code type} is not an api event.
     */
    <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        Route<?> route = routes.get(type);
        if (route == null) {
            throw new IllegalArgumentException(type.getName() + " is not an event of HyColony's api");
        }
        return listen(route, type, listener);
    }

    /** Subscribes to {@code route}'s core event, translating each for {@code listener}. */
    private <C, E> Subscription listen(Route<C> route, Class<E> type, Consumer<? super E> listener) {
        EventBus.Registration r = bus.subscribe(
                route.core(), e -> listener.accept(type.cast(route.translate().apply(e))));
        return r::close;
    }

    /** Hears the api event {@code api} through the core event {@code core}, translated by {@code translate}. */
    private <A, C> void route(Class<A> api, Class<C> core, Function<C, A> translate) {
        routes.put(api, new Route<>(core, translate::apply));
    }

    private static String type(Building b) {
        return b.type().id();
    }

    /** The api's stable name of an order type: a switch, so a new core type breaks the build, not addons. */
    private static String name(WorkOrderType type) {
        return switch (type) {
            case BUILD -> "BUILD";
            case UPGRADE -> "UPGRADE";
            case REPAIR -> "REPAIR";
            case REMOVE -> "REMOVE";
        };
    }

    private ColonyRef ref(Colony c) {
        return new ColonyRef(world, c.id());
    }

    /** The player who caused it, else the colony itself. */
    private static Actor cause(Optional<UUID> player) {
        return player.<Actor>map(Actor.Player::new).orElseGet(Actor.Colony::new);
    }
}
