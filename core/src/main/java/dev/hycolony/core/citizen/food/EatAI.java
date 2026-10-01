package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.hall.DiningHall;
import dev.hycolony.core.citizen.food.hall.DiningHalls;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IState;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A citizen in EATING: eats what it carries, else fetches food at its work hut, else goes to a dining hall
 * ({@link DiningVisit}). Port of MC EntityAIEatTask; its interactions (NO_RESTAURANT, BETTER_FOOD, RAW_FOOD) wait for
 * the citizen interaction system, and a guard's night hurry has no guard to apply to. {@link #finished} tells the
 * citizen AI to idle.
 */
public final class EatAI {
    private static final System.Logger LOG = System.getLogger(EatAI.class.getName());
    /** MC EntityAIEatTask: every transition runs every 20 ticks. */
    static final int TICK_INTERVAL = 20;
    /** MC REQUIRED_TIME_TO_EAT: bites (transitions) per food. */
    static final int REQUIRED_TIME_TO_EAT = 5;

    /** MC EntityAIEatTask.EatingState, plus the EATING state's first transition (reset). */
    public enum State implements IState {
        INIT,
        CHECK_FOR_FOOD,
        GO_TO_HUT,
        SEARCH_RESTAURANT,
        GO_TO_RESTAURANT,
        WAIT_FOR_FOOD,
        GET_FOOD_YOURSELF,
        GO_TO_EAT_POS,
        EAT,
        DONE
    }

    private final Colony colony;
    private final CitizenData data;
    private final BlockApproach walker;
    private final TickRateStateMachine<State> machine;
    private final EatingTable table;
    private final DiningVisit visit;
    private boolean finished;
    private boolean failed;

    public EatAI(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.walker = new BlockApproach(
                colony.context().ports(),
                new BodyWalker(
                        colony.context().bodies(),
                        body,
                        colony.context().clock()::currentTick,
                        new CitizenWalkReports(colony, data)));
        this.table = new EatingTable(colony, data, body);
        this.visit = new DiningVisit(colony, data, body, walker, table);
        this.machine = new TickRateStateMachine<>(State.INIT, this::onException);
        add(State.INIT, this::init);
        add(State.CHECK_FOR_FOOD, () -> visit.hasFood() ? State.EAT : State.GO_TO_HUT); // MC getFood
        add(State.GO_TO_HUT, this::goToHut);
        add(State.SEARCH_RESTAURANT, visit::search);
        add(State.GO_TO_RESTAURANT, visit::goTo);
        add(State.WAIT_FOR_FOOD, visit::waitForFood);
        add(State.GET_FOOD_YOURSELF, visit::getFoodYourself);
        add(State.GO_TO_EAT_POS, visit::goToEatPos);
        add(State.EAT, this::eat);
        // MC: TickingTransition(DONE, () -> true, () -> IDLE, 1).
        machine.addTransition(new AITarget<>(State.DONE, (IStateSupplier<State>) this::finish, 1));
    }

    /** The meal is over: the citizen AI idles. */
    private @Nullable State finish() {
        finished = true;
        return null;
    }

    private void add(State state, IStateSupplier<State> action) {
        machine.addTransition(new AITarget<>(state, action, TICK_INTERVAL));
    }

    public void tick() {
        machine.tick();
    }

    public State state() {
        return machine.getState();
    }

    /** Whether the meal is over (MC: the transition to IDLE); the citizen AI then idles. */
    public boolean finished() {
        return finished;
    }

    /** MC reset, also on leaving EATING for anything: an empty hand, up from its seat, everything forgotten. */
    public void stop() {
        visit.reset();
    }

    private void onException(RuntimeException e) {
        LOG.log(
                failed ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                "Eat AI failed for " + data.name(),
                e);
        failed = true;
    }

    /** MC: entering EATING resets the task, then checks for food. */
    private State init() {
        stop();
        return State.CHECK_FOR_FOOD;
    }

    /**
     * MC goToHut: at its work hut, food it carries or takes from the hut ({@link #foodAtHut}); a waiter is done there;
     * else a dining hall.
     */
    private @Nullable State goToHut() {
        Building hut = Optional.ofNullable(data.workBuilding())
                .flatMap(colony.buildings()::at)
                .orElse(null);
        if (hut == null || !colony.context().worldQuery().isLoaded(hut.position())) {
            return State.SEARCH_RESTAURANT;
        }
        visit.eatAt(null);
        if (!walker.walkToBuilding(hut)) {
            return null;
        }
        if (foodAtHut(hut)) {
            return State.EAT;
        }
        if (data.job().map(Job::servesFood).orElse(false)) {
            stop();
            return State.DONE;
        }
        return State.SEARCH_RESTAURANT;
    }

    /**
     * MC goToHut at the hut: the best food it carries (a dining hall's menu first if it works at one), else a stack of
     * the best in the hut's containers; false for neither.
     */
    private boolean foodAtHut(Building hut) {
        FoodChoice choice = new FoodChoice(colony, data);
        Optional<DiningHall> hall = DiningHalls.of(hut);
        if (hall.isPresent()) {
            visit.eatAt(hut);
            if (choice.bestSlot(data.inventory(), hall.get().menu()) != -1) {
                return true;
            }
        }
        if (choice.bestSlot(data.inventory(), null) != -1) {
            return true;
        }
        Optional<ItemKey> inHut = choice.bestInBuilding(hut, null);
        return inHut.isPresent() && FoodTransfer.takeStack(colony, hut, data.inventory(), inHut.get());
    }

    /** MC eat: a bite of the best food it carries (see {@link EatingTable#bite}); the meal ends full. */
    private @Nullable State eat() {
        if (!visit.hasFood()) {
            return State.CHECK_FOR_FOOD;
        }
        return switch (table.bite(visit.atRestaurant())) {
            case CHEWING, AGAIN -> null;
            case NOT_EDIBLE -> State.CHECK_FOR_FOOD;
            case FULL -> finish(); // MC: straight to IDLE, without DONE
        };
    }
}
