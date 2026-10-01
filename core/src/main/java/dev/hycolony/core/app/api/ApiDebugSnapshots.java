package dev.hycolony.core.app.api;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.CitizenVitals;
import dev.hycolony.core.citizen.vitals.EndedWalk;
import dev.hycolony.core.citizen.vitals.WalkVitals;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The api's debug snapshots of the core's vital signs, histories and invariants: states by their enum names. */
final class ApiDebugSnapshots {
    private ApiDebugSnapshots() {}

    /**
     * What citizen {@code d} of {@code c}, known to the api as {@code ref}, is doing now; no AI state nor job step
     * while its body is unloaded, as no AI runs then.
     */
    static CitizenDebugSnapshot citizen(CitizenRef ref, Colony c, CitizenData d) {
        CitizenVitals v = d.vitals();
        WalkVitals walks = v.walks();
        boolean running = c.citizens().ai(d.id()).isPresent();
        return new CitizenDebugSnapshot(
                ref,
                c.context().clock().currentTick(),
                running ? v.aiState().map(Enum::name).orElse("") : "",
                v.aiStateSince(),
                running ? v.jobStep().orElse("") : "",
                v.jobStepSince(),
                c.citizens().jobActivity(d.id()).map(ApiDebugSnapshots::text),
                walks.target().map(ApiSnapshots::pos),
                path(c, d),
                walks.lastEnd().map(e -> walkEnded(ref, e)),
                walks.lastStuck().map(Enum::name).orElse(""),
                walks.lastStuckTick(),
                queue(c, d),
                d.leisureTime(),
                v.history().stream().map(ApiDebugSnapshots::entry).toList());
    }

    /** The path the citizen's loaded body still plans to walk; empty without one. */
    private static List<Vec> path(Colony c, CitizenData d) {
        return c.citizens().bodyOf(d.id()).map(b -> c.context().bodies().path(b)).orElse(List.of()).stream()
                .map(ApiSnapshots::vec)
                .toList();
    }

    /** How a walk of {@code citizen} ended. */
    static WalkEnded walkEnded(CitizenRef citizen, EndedWalk e) {
        return new WalkEnded(
                citizen,
                ApiSnapshots.pos(e.target()),
                ApiSnapshots.vec(e.at()),
                e.how().name(),
                e.distance(),
                e.nav().name());
    }

    /** A history entry, its kind by name and its detail as a text. */
    static HistoryEntry entry(dev.hycolony.core.citizen.vitals.HistoryEntry e) {
        return new HistoryEntry(e.tick(), e.kind().name(), e.from(), e.to(), text(e.detail()));
    }

    /** A broken invariant of the colony {@code colony}. */
    static Violation violation(ColonyRef colony, dev.hycolony.core.app.diagnostics.Violation v) {
        return new Violation(
                v.code().name(),
                text(v.detail()),
                v.citizen().isPresent()
                        ? Optional.of(new CitizenRef(colony, v.citizen().getAsInt()))
                        : Optional.empty(),
                v.pos().map(ApiSnapshots::pos));
    }

    /** A core message as an api text: a parameter starting with {@code %} names a key, translated on its own. */
    static ApiText text(Msg m) {
        List<Object> params = new ArrayList<>(m.params().size());
        for (String p : m.params()) {
            params.add(p.startsWith("%") ? ApiText.of(p.substring(1)) : p);
        }
        return new ApiText(m.key(), params);
    }

    /** The ids of its job's own queue, head first; empty without a job AI. */
    private static List<String> queue(Colony c, CitizenData d) {
        return c.citizens().ai(d.id()).flatMap(CitizenAI::jobAi).map(JobAI::queue).orElse(List.of()).stream()
                .map(RequestToken::id)
                .map(Object::toString)
                .toList();
    }
}
