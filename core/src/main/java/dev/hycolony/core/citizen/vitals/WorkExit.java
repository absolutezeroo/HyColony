package dev.hycolony.core.citizen.vitals;

/**
 * Why a citizen left WORKING (MC CitizenAI.calculateNextState's reasons), said in its history so HyLens shows it; the
 * name, lower-cased, ends its translation key {@code hycolony.debug.history.leftWork.*}.
 */
public enum WorkExit {
    /** It rains at its work hut and it may not work in the rain. */
    RAIN,
    /** Its job AI can go idle: nothing to do (a builder without a work order). */
    IDLE,
    /** It is on a leisure break. */
    BREAK,
    /** It goes to sleep. */
    SLEEP,
    /** It goes to eat. */
    MEAL,
    /** It mourns a deceased citizen. */
    MOURN,
    /** It flees from its attacker. */
    FLEE,
    /** It lost its job. */
    JOB_LOST;

    /** Its translation key. */
    String key() {
        return "hycolony.debug.history.leftWork."
                + switch (this) {
                    case RAIN -> "rain";
                    case IDLE -> "idle";
                    case BREAK -> "break";
                    case SLEEP -> "sleep";
                    case MEAL -> "meal";
                    case MOURN -> "mourn";
                    case FLEE -> "flee";
                    case JOB_LOST -> "jobLost";
                };
    }
}
