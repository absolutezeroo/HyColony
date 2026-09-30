package dev.hycolony.core.app.diagnostics;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a healthy colony never shows (spec 2026-09-30, § 5; docs/research/debug-mod.md § 5). No MC source: MC checks
 * only its inventories ({@code debuginventories}). A check is a snapshot, taken between ticks (never from a request
 * system callback, where a reassigned request is briefly without a resolver); {@link ViolationWatch} keeps the lasting
 * ones.
 */
public final class Invariants {
    /** Ticks a job's step may last outside a legitimate wait (5 minutes); HyColony's own constant. */
    public static final int JOB_STEP_STALE_TICKS = 6000;
    /** Exceptions caught by a citizen's AIs from which they count as repeated. */
    public static final int REPEATED_FAILURES = 2;

    private Invariants() {}

    /** The invariants {@code colony} breaks now: its citizens' in id order, then its requests'; empty when healthy. */
    public static List<Violation> check(Colony colony) {
        List<Violation> out = new ArrayList<>();
        for (CitizenData citizen : colony.citizens().all()) {
            new CitizenInvariants(colony, citizen, out).check();
        }
        for (Request request : colony.requests().all()) {
            if (resolving(request.state())
                    && colony.requests().resolverOf(request.token()).isEmpty()) {
                out.add(new Violation(
                        Violation.Code.REQUEST_UNRESOLVED,
                        Msg.of(
                                "hycolony.debug.violation.requestUnresolved",
                                CitizenInvariants.shortId(request.token()),
                                request.state().name()),
                        request.citizenId() == Request.NO_CITIZEN
                                ? OptionalInt.empty()
                                : OptionalInt.of(request.citizenId()),
                        Optional.empty()));
            }
        }
        return out;
    }

    /** Whether a request in {@code state} is in a resolver's hands. */
    private static boolean resolving(RequestState state) {
        return state == RequestState.ASSIGNED || state == RequestState.IN_PROGRESS;
    }
}
