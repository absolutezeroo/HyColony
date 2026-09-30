package dev.hycolony.core.app.api;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.CitizenStateChanged;
import dev.hycolony.api.debug.JobStateChanged;
import dev.hycolony.api.debug.RequestStateChanged;
import dev.hycolony.api.debug.StuckAction;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.CitizenDebugEvents;
import dev.hycolony.core.colony.Colony;

/** The api's experimental debug events: the core's, in references and enum names (spec 2026-09-30, § 4.2). */
final class DebugRoutes {
    private final String world;

    private DebugRoutes(String world) {
        this.world = world;
    }

    /** Adds the debug events' routes to {@code events}, whose world is {@code world}. */
    static void addTo(ApiEvents events, String world) {
        DebugRoutes r = new DebugRoutes(world);
        events.route(
                CitizenStateChanged.class,
                CitizenDebugEvents.AiStateChanged.class,
                e -> new CitizenStateChanged(
                        r.ref(e.colony(), e.citizen()), e.from().name(), e.to().name()));
        events.route(
                JobStateChanged.class,
                CitizenDebugEvents.JobStepChanged.class,
                e -> new JobStateChanged(r.ref(e.colony(), e.citizen()), e.from(), e.to()));
        events.route(
                WalkEnded.class,
                CitizenDebugEvents.WalkEnded.class,
                e -> ApiDebugSnapshots.walkEnded(r.ref(e.colony(), e.citizen()), e.end()));
        events.route(
                StuckAction.class,
                CitizenDebugEvents.StuckActed.class,
                e -> new StuckAction(
                        r.ref(e.colony(), e.citizen()),
                        ApiSnapshots.pos(e.target()),
                        ApiSnapshots.vec(e.at()),
                        e.action().name()));
        events.route(
                RequestStateChanged.class,
                dev.hycolony.core.request.model.RequestStateChanged.class,
                e -> new RequestStateChanged(
                        new ColonyRef(world, e.colonyId()),
                        e.token().id().toString(),
                        e.from().name(),
                        e.to().name()));
    }

    private CitizenRef ref(Colony colony, CitizenData citizen) {
        return new CitizenRef(new ColonyRef(world, colony.id()), citizen.id());
    }
}
