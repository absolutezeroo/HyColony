package dev.hycolony.core.citizen.food;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One citizen's meals, as its AI sees them: the decision (MC CitizenAI.shouldEat, see {@link EatDecision}) and the
 * meal itself while in EATING ({@link EatAI}).
 */
public final class CitizenEating {
    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private @Nullable EatAI ai;

    public CitizenEating(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
    }

    /**
     * MC shouldEat for a citizen {@code eating} or not, whose job AI can be interrupted right now if
     * {@code interruptible}; a citizen starting a meal gets a fresh eat AI.
     */
    public boolean shouldEat(boolean eating, boolean interruptible) {
        EatDecision.Situation s = new EatDecision.Situation(
                eating,
                interruptible,
                data.job().map(Job::servesFood).orElse(false),
                () -> colony.context().health().health(body));
        boolean eat = EatDecision.shouldEat(data, s, colony.context().random());
        if (eat && !eating) {
            ai = new EatAI(colony, data, body);
        }
        return eat;
    }

    /** One tick of the meal; true once it is over (MC: the eat task returns IDLE). */
    public boolean tick() {
        EatAI current = ai;
        if (current == null) {
            current = new EatAI(colony, data, body);
            ai = current;
        }
        current.tick();
        return current.finished();
    }

    /** Ends the meal under way (MC reset on leaving EATING): empty hand, up from its seat. */
    public void stop() {
        EatAI current = ai;
        if (current != null) {
            current.stop();
            ai = null;
        }
    }

    /** The meal's step, for diagnostics; empty outside a meal. */
    public Optional<EatAI.State> step() {
        return Optional.ofNullable(ai).map(EatAI::state);
    }
}
