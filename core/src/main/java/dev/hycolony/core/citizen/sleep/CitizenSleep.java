package dev.hycolony.core.citizen.sleep;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.HomePosition;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import org.jspecify.annotations.Nullable;

/**
 * One citizen's sleep, as its AI sees it: the decision (MC calculateNextState, see {@link SleepDecision}), the walk to
 * bed while in SLEEP ({@link SleepAI}) and getting up ({@link SleepHandler}).
 */
public final class CitizenSleep {
    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final SleepHandler handler;
    private @Nullable SleepAI ai;

    public CitizenSleep(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.handler = new SleepHandler(colony, data, body);
    }

    /**
     * MC calculateNextState, sleep part, for a citizen in SLEEP or not: a citizen going to bed gets a fresh sleep AI;
     * one waking up gets out of bed. NONE without a body position.
     */
    public SleepDecision.Verdict decide(boolean inSleepState) {
        BlockPos at =
                colony.context().bodies().position(body).map(Vec3::toBlockPos).orElse(null);
        if (at == null) {
            return SleepDecision.Verdict.NONE;
        }
        SleepDecision.Verdict verdict = SleepDecision.decide(
                colony.context().clock(), inSleepState, data.asleep(), HomePosition.of(colony, data), at);
        switch (verdict) {
            case GO_TO_SLEEP -> ai = new SleepAI(colony, data, body, handler);
            case WAKE_UP -> {
                handler.wakeUp();
                ai = null;
            }
            case STAY_ASLEEP, NONE -> {}
        }
        return verdict;
    }

    /** One tick of the walk to bed and of the sleep (MC EntityAISleep's transitions). */
    public void tick() {
        SleepAI current = ai;
        if (current == null) {
            current = new SleepAI(colony, data, body, handler);
            ai = current;
        }
        current.tick();
    }

    /** MC CitizenSleepHandler.onWakeUp for an asleep citizen (a teleport, MC TeleportHelper); else nothing. */
    public void wakeUp() {
        handler.wakeUp();
    }

    /**
     * A hurt sleeper gets out of bed (Minecraft LivingEntity.hurt, stopSleeping), without MC's wake-up hooks (its hut,
     * job and home learn of a wake-up only in the morning). Deviation from MC: MC keeps isAsleep until its morning
     * onWakeUp; ours reads asleep as lying in bed, so the citizen is bedless and awake until it goes to bed again,
     * and its morning hooks run only if it lies down again before day. Nothing for a citizen awake.
     */
    public void hurtAwake() {
        if (data.asleep()) {
            colony.context().bodies().wakeUp(body);
            handler.leftBed();
        }
    }

    /**
     * MC CitizenData.initEntityValues: a body appearing wakes its citizen up. Deviation from MC: always, not only
     * without a bed position, as Hytale saves no NPC lying in a bed.
     */
    public void onBodyAppeared() {
        handler.onWakeUp();
    }
}
