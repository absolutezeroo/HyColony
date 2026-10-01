package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.EatAI.State;
import dev.hycolony.core.citizen.food.hall.DiningHall;
import dev.hycolony.core.citizen.food.hall.DiningHalls;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where a citizen of {@link EatAI} eats: the dining hall it finds, walks to, sits in, waits to be served at and in
 * the end serves itself from. Port of MC EntityAIEatTask's searchRestaurant, goToRestaurant, waitForFood,
 * goToEatingPlace and getFoodYourself, with its {@code restaurant}, {@code eatPos} and reset.
 */
final class DiningVisit {
    /** MC MINUTES_WAITING_TIME x SECONDS_A_MINUTE: waits (transitions) before serving itself. */
    static final int WAITING_TRANSITIONS = 2 * 60;
    /** MC GO_TO_EAT_POS: walking transitions before giving up the seat. */
    static final int WALK_TIMEOUT = 400;
    /** MC: being seated adds this to the walking count (a delay before eating). */
    static final int SEATED_DELAY = 10;
    /** MC walkToPos(citizen, eatPos, 2, true). */
    private static final int SEAT_REACH = 2;
    /** MC getFoodYourself: half again as much food as it needs. */
    private static final double EXTRA_FOOD = 1.5;

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final BlockApproach walker;
    private final EatingTable table;
    private @Nullable BlockPos restaurantPos;
    /** The dining hall it eats at; null when eating elsewhere (MC {@code restaurant}). */
    private @Nullable Building restaurant;

    private @Nullable BlockPos eatPos;
    /**
     * MC timeOutWalking. Deviation from MC: it starts at 0 each meal, where MC's lives as long as the citizen, so a
     * seat given up once would make every later meal give its seat up at once.
     */
    private int walkTimeout;

    DiningVisit(Colony colony, CitizenData data, BodyId body, BlockApproach walker, EatingTable table) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.walker = walker;
        this.table = table;
    }

    /** The dining hall it eats at from now on; null when it eats elsewhere (its work hut). */
    void eatAt(@Nullable Building hall) {
        restaurant = hall;
    }

    /** Whether it eats at a dining hall. */
    boolean atRestaurant() {
        return restaurant != null;
    }

    /** MC reset: an empty hand, up from its seat, everything forgotten. */
    void reset() {
        table.clear();
        colony.context().seats().standUp(body);
        restaurantPos = null;
        restaurant = null;
        eatPos = null;
    }

    /**
     * MC searchRestaurant: the closest dining hall with a waiter to its work hut (else home, else itself), else the
     * closest at all; it stops working. Without one it gives up fed enough, else checks again.
     */
    State search() {
        BlockPos from = data.workBuilding() != null
                ? data.workBuilding()
                : data.homeBuilding() != null ? data.homeBuilding() : here().orElse(null);
        if (from == null) {
            return State.CHECK_FOR_FOOD;
        }
        restaurantPos = DiningHalls.closest(colony, from, true)
                .or(() -> DiningHalls.closest(colony, from, false))
                .map(Building::position)
                .orElse(null);
        data.job().filter(Job::isWorking).ifPresent(j -> j.setWorking(colony, false));
        if (restaurantPos == null) {
            return fedEnough() ? done() : State.CHECK_FOR_FOOD; // MC: NO_RESTAURANT, once interactions exist
        }
        return State.GO_TO_RESTAURANT;
    }

    /** MC goToRestaurant: inside the hall it waits for food, else walks there; a hall gone sends it searching. */
    @Nullable
    State goTo() {
        Building hall = hallAt(restaurantPos);
        if (hall == null) {
            return State.SEARCH_RESTAURANT;
        }
        if (inside(hall)) {
            return State.WAIT_FOR_FOOD;
        }
        return walker.walkToBuilding(hall) ? State.SEARCH_RESTAURANT : null;
    }

    /**
     * MC waitForFood: becomes a customer of the hall closest to its work hut (else to itself); outside it walks back;
     * a free seat to go to; food to eat; fed enough to leave; else waits.
     */
    @Nullable
    State waitForFood() {
        BlockPos from = data.workBuilding() != null ? data.workBuilding() : here().orElse(null);
        Building hall =
                from == null ? null : DiningHalls.closest(colony, from, false).orElse(null);
        if (hall == null) {
            return State.SEARCH_RESTAURANT;
        }
        restaurantPos = hall.position();
        DiningHall module = DiningHalls.of(hall).orElseThrow();
        module.storeCustomer(colony, hall, data.id());
        restaurant = hall;
        if (!inside(hall)) {
            return State.GO_TO_RESTAURANT;
        }
        eatPos = module.nextSeat(colony, hall).orElse(null);
        if (eatPos != null) {
            return State.GO_TO_EAT_POS;
        }
        if (hasFood()) {
            return State.EAT;
        }
        return fedEnough() ? done() : null;
    }

    /**
     * MC goToEatingPlace: walks to its seat and sits; after a while seated (or without a seat) it eats what it was
     * served, or after two minutes of waiting serves itself.
     */
    @Nullable
    State goToEatPos() {
        if (eatPos == null || walkTimeout++ > WALK_TIMEOUT) {
            State next = afterWaiting();
            if (next != null) {
                return next;
            }
        }
        BlockPos seat = eatPos;
        Building hall = restaurant;
        // MC walkToPos(eatPos, 2, true): beside the seat, here from the hall's side
        if (seat != null && hall != null && walker.walkToPosInBuilding(seat, hall, SEAT_REACH)) {
            colony.context().seats().sitOn(body, seat);
            walkTimeout += SEATED_DELAY;
            if (!hasFood() && table.waitedTooLong()) {
                return State.GET_FOOD_YOURSELF;
            }
        }
        return null;
    }

    /** MC goToEatingPlace once seated long enough or seatless: served food to eat, else one more wait; null goes on. */
    private @Nullable State afterWaiting() {
        if (hasFood()) {
            walkTimeout = 0;
            return State.EAT;
        }
        return table.waitedTooLong() ? State.GET_FOOD_YOURSELF : null;
    }

    /**
     * MC getFoodYourself: at the hall, takes from its containers half again as much of the best menu food as it needs
     * to be full, then eats; without any it waits again.
     */
    @Nullable
    State getFoodYourself() {
        Building hall = hallAt(restaurantPos);
        Optional<DiningHall> module = hall == null ? Optional.empty() : DiningHalls.of(hall);
        if (hall == null || module.isEmpty()) {
            return State.WAIT_FOR_FOOD; // MC: no dining hall there any more
        }
        colony.context().seats().standUp(body);
        if (!walker.walkToBuilding(hall)) {
            return null;
        }
        Optional<ItemKey> food =
                new FoodChoice(colony, data).bestInBuilding(hall, module.get().menu());
        if (food.isEmpty()) {
            return State.WAIT_FOR_FOOD;
        }
        double value = FoodRules.foodValue(colony.context().ports().catalog(), food.get());
        int qty = (int) Math.max(1.0, (CitizenData.MAX_SATURATION - data.saturation()) / value);
        FoodTransfer.take(colony, hall, data.inventory(), food.get(), (int) Math.ceil(qty * EXTRA_FOOD));
        return State.EAT;
    }

    /** MC hasFood: the best food it carries, on the dining hall's menu while it eats at one. */
    boolean hasFood() {
        Set<ItemKey> menu = restaurant == null
                ? null
                : DiningHalls.of(restaurant).map(DiningHall::menu).orElse(null);
        return table.choose(new FoodChoice(colony, data).bestSlot(data.inventory(), menu));
    }

    /** MC: fed enough to leave without eating (saturation at least average). */
    private boolean fedEnough() {
        return data.saturation() >= EatDecision.AVERAGE_SATURATION;
    }

    /** MC: {@code reset(); setJustAte(true); return DONE}. */
    private State done() {
        reset();
        data.hunger().setJustAte(true);
        return State.DONE;
    }

    private @Nullable Building hallAt(@Nullable BlockPos pos) {
        return pos == null ? null : colony.buildings().at(pos).orElse(null);
    }

    private boolean inside(Building hall) {
        return here().map(at -> HutFootprint.isInBuilding(colony.context().ports(), hall, at))
                .orElse(false);
    }

    private Optional<BlockPos> here() {
        return colony.context().bodies().position(body).map(Vec3::toBlockPos);
    }
}
