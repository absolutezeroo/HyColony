package dev.hycolony.core.app.diagnostics;

import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.vitals.CitizenVitals;
import dev.hycolony.core.citizen.vitals.DebugText;
import dev.hycolony.core.citizen.vitals.EndedWalk;
import dev.hycolony.core.citizen.vitals.WalkVitals;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** One citizen's invariants (1, 2, 3, 7, 9 and 10), read from its vital signs and its job's AI. */
final class CitizenInvariants {
    /** The characters of a request's id shown in a message. */
    private static final int SHORT_ID = 8;
    /** The core ticks 20 times a second. */
    private static final int TICKS_PER_SECOND = 20;

    private final Colony colony;
    private final CitizenData citizen;
    private final CitizenVitals vitals;
    private final List<Violation> out;
    private final long now;

    CitizenInvariants(Colony colony, CitizenData citizen, List<Violation> out) {
        this.colony = colony;
        this.citizen = citizen;
        this.vitals = citizen.vitals();
        this.out = out;
        this.now = colony.context().clock().currentTick();
    }

    /** Adds the invariants the citizen breaks to {@code out}. */
    void check() {
        vitals.walks().lastEnd().ifPresent(this::walkEnd);
        stuck();
        respawnFailed();
        failing();
        if (vitals.aiState().orElse(null) == CitizenState.WORKING) {
            colony.citizens().ai(citizen.id()).flatMap(CitizenAI::jobAi).ifPresent(job -> {
                stale(job);
                queue(job);
            });
        }
    }

    /**
     * 1: a walk its nav ended on another floor than its target (the roof), or, for a plain walk, more than
     * {@link BodyWalker#ARRIVAL_RANGE} from it; a close walk's nav ends within its reach by construction.
     */
    private void walkEnd(EndedWalk end) {
        boolean navEnded = end.how() == WalkEnd.NAV_ENDED || end.how() == WalkEnd.IN_REACH;
        boolean otherFloor = Math.abs(end.at().y() - end.target().y()) >= 1;
        boolean far = end.how() == WalkEnd.NAV_ENDED && end.distance() > BodyWalker.ARRIVAL_RANGE;
        if (navEnded && (otherFloor || far)) {
            add(
                    Violation.Code.WALK_ENDED_AWAY,
                    Msg.of(
                            "hycolony.debug.violation.walkEndedAway",
                            DebugText.pos(end.target()),
                            DebugText.decimal(end.distance()),
                            end.nav().name()),
                    Optional.of(end.at().toBlockPos()));
        }
    }

    /**
     * 10: the stuck handler teleported or gave up after the current (or last) walk started; an action on the tick a
     * walk starts is the previous walk's, as the handler waits before acting.
     */
    private void stuck() {
        WalkVitals walks = vitals.walks();
        StuckHandler.Action action = walks.lastStuck().orElse(StuckHandler.Action.NONE);
        boolean escalated = action == StuckHandler.Action.TELEPORT || action == StuckHandler.Action.GIVE_UP;
        if (escalated && walks.lastStuckTick() > walks.startTick()) {
            BlockPos target = walks.target().orElse(null);
            add(
                    Violation.Code.STUCK_ESCALATED,
                    Msg.of(
                            "hycolony.debug.violation.stuckEscalated",
                            action.name(),
                            target == null ? "-" : DebugText.pos(target)),
                    Optional.ofNullable(target));
        }
    }

    /** 7: its colony's respawn checks found a loaded spot for it, yet spawned no body there. */
    private void respawnFailed() {
        colony.citizens()
                .failedRespawns()
                .since(citizen.id())
                .ifPresent(since -> add(
                        Violation.Code.RESPAWN_FAILED,
                        Msg.of("hycolony.debug.violation.respawnFailed", seconds(now - since))));
    }

    /** 9: its AIs caught {@link Invariants#REPEATED_FAILURES} exceptions or more since it was loaded: it stays. */
    private void failing() {
        int failures = vitals.aiFailures() + vitals.jobFailures();
        if (failures >= Invariants.REPEATED_FAILURES) {
            add(
                    Violation.Code.AI_FAILING,
                    Msg.of(
                            "hycolony.debug.violation.aiFailing",
                            String.valueOf(failures),
                            String.valueOf(vitals.jobFailures())));
        }
    }

    /** 2: no step change, action nor start of work for {@link Invariants#JOB_STEP_STALE_TICKS}, outside a wait. */
    private void stale(JobAI job) {
        long since = Math.max(Math.max(vitals.jobStepSince(), vitals.aiStateSince()), vitals.lastActionTick());
        if (!job.waiting() && now - since >= Invariants.JOB_STEP_STALE_TICKS) {
            add(
                    Violation.Code.JOB_STEP_STALE,
                    Msg.of("hycolony.debug.violation.jobStepStale", job.stateName(), seconds(now - since)));
        }
    }

    /** 3: a step serving the head of its queue finds it empty, gone, or not in progress. */
    private void queue(JobAI job) {
        if (!job.servesQueueHead()) {
            return;
        }
        List<RequestToken> queue = job.queue();
        if (queue.isEmpty()) {
            add(Violation.Code.QUEUE_INCOHERENT, Msg.of("hycolony.debug.violation.queueEmpty", job.stateName()));
            return;
        }
        RequestToken head = queue.getFirst();
        Request request = colony.requests().get(head).orElse(null);
        if (request == null) {
            add(
                    Violation.Code.QUEUE_INCOHERENT,
                    Msg.of("hycolony.debug.violation.queueHeadGone", job.stateName(), shortId(head)));
        } else if (request.state() != RequestState.IN_PROGRESS) {
            add(
                    Violation.Code.QUEUE_INCOHERENT,
                    Msg.of(
                            "hycolony.debug.violation.queueHeadState",
                            job.stateName(),
                            shortId(head),
                            request.state().name()));
        }
    }

    private void add(Violation.Code code, Msg detail) {
        add(code, detail, Optional.empty());
    }

    private void add(Violation.Code code, Msg detail, Optional<BlockPos> pos) {
        out.add(new Violation(code, detail, OptionalInt.of(citizen.id()), pos));
    }

    /** A request's id as shown in a message: its first characters. */
    static String shortId(RequestToken token) {
        return token.id().toString().substring(0, SHORT_ID);
    }

    private static String seconds(long ticks) {
        return String.valueOf(ticks / TICKS_PER_SECOND);
    }
}
