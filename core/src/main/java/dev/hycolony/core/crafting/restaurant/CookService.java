package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.EatDecision;
import dev.hycolony.core.citizen.food.FoodChoice;
import dev.hycolony.core.citizen.food.FoodRules;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The waiter's service (MC EntityAIWorkCook: checkForImportantJobs, serveFoodToCitizen, serveFoodToPlayer): the
 * hungry citizens in the hall get the dish that suits them best, the hurt managers in it a meal. Deviation from MC:
 * Hytale has no hunger, so a player is served below {@link #PLAYER_HEALTH_PERCENT} of its health instead of MC's
 * food level 10 [in-game]; a player already waiting is not queued twice.
 */
final class CookService {
    /** MC SATURATION_TO_SERVE: food value a player gets per hall level. */
    static final int SATURATION_TO_SERVE = 16;
    /** Deviation from MC (see the class): the health under which a player is served. */
    static final int PLAYER_HEALTH_PERCENT = 50;
    /** MC serveFoodToCitizen: bites fed straight to a citizen whose inventory is full. */
    static final int FEEDING_ATTEMPTS = 10;
    /** MC: a dish handed over is half again as much as the citizen needs. */
    private static final double EXTRA_FOOD = 1.5;

    private final CookWorkContext ctx;
    private final Deque<Integer> citizensToServe = new ArrayDeque<>();
    private final Deque<UUID> playersToServe = new ArrayDeque<>();

    CookService(CookWorkContext ctx) {
        this.ctx = ctx;
    }

    /**
     * MC checkForImportantJobs: queues the hurt managers and the hungry citizens in the hall; a citizen whose best dish
     * is in the hall, not on the waiter, has it fetched first ({@code gather}). Returns the service to do, else
     * START_WORKING.
     */
    CookState importantJobs(Consumer<Predicate<ItemKey>> gather) {
        for (UUID player :
                ctx.colony().context().players().onlineIn(ctx.colony().context().world())) {
            if (wantsServing(player)) {
                playersToServe.add(player);
            }
        }
        for (CitizenData c : ctx.colony().citizens().all()) {
            if (!wantsServing(c)) {
                continue;
            }
            FoodChoice choice = new FoodChoice(ctx.colony(), c);
            if (choice.bestIsInInventory(ctx.stock().inventory(), menu(), ctx.hall())) {
                citizensToServe.add(c.id());
            } else {
                Optional<ItemKey> inHall = choice.bestInBuilding(ctx.hall(), menu());
                if (inHall.isPresent()) {
                    gather.accept(inHall.get()::equals);
                    return CookState.GATHERING_REQUIRED_MATERIALS;
                }
            }
        }
        return next(gather);
    }

    /** MC checkForImportantJobs: a hurt manager in the hall, not queued yet. */
    private boolean wantsServing(UUID player) {
        return inHall(ctx.colony().context().players().position(player))
                && ctx.colony().context().players().healthPercent(player) < PLAYER_HEALTH_PERCENT
                && ctx.colony().permissions().hasPermission(player, Action.MANAGE_HUTS)
                && !playersToServe.contains(player);
    }

    /** MC checkForImportantJobs: a citizen in the hall, no waiter, due a meal and carrying no menu food. */
    private boolean wantsServing(CitizenData c) {
        return inHall(here(c)) && !c.job().map(Job::servesFood).orElse(false) && shouldBeFed(c) && !carriesMenuFood(c);
    }

    /**
     * MC checkForImportantJobs' end: citizens first, then players, the menu fetched for them if the waiter has none.
     */
    private CookState next(Consumer<Predicate<ItemKey>> gather) {
        if (!citizensToServe.isEmpty()) {
            return CookState.COOK_SERVE_FOOD_TO_CITIZEN;
        }
        if (playersToServe.isEmpty()) {
            return CookState.START_WORKING;
        }
        Predicate<ItemKey> onMenu = menu()::contains;
        if (carried(onMenu) == 0 && hallHas(onMenu)) {
            gather.accept(onMenu);
            return CookState.GATHERING_REQUIRED_MATERIALS;
        }
        return CookState.COOK_SERVE_FOOD_TO_PLAYER;
    }

    /**
     * MC serveFoodToCitizen, every 30 ticks: walks to the next citizen still in the hall; one with a full inventory is
     * fed in place, up to {@link #FEEDING_ATTEMPTS} bites without history nor bonus; else it gets 1.5 times the dish it
     * needs, for 2 XP and a little hunger. Back to START_WORKING once nobody waits or the menu food runs out.
     */
    CookState serveCitizen() {
        Integer id = citizensToServe.peek();
        if (id == null) {
            return CookState.START_WORKING;
        }
        CitizenData c = ctx.colony().citizens().get(id).orElse(null);
        Optional<BlockPos> at = c == null ? Optional.empty() : here(c);
        if (c == null || !inHall(at)) {
            citizensToServe.poll();
            return CookState.COOK_SERVE_FOOD_TO_CITIZEN;
        }
        if (!ctx.walkToWorkPos(at.orElseThrow())) {
            return CookState.COOK_SERVE_FOOD_TO_CITIZEN;
        }
        citizensToServe.poll();
        if (c.inventory().isFull()) {
            feedInPlace(c);
        } else if (!carriesMenuFood(c) && !handOver(c)) {
            citizensToServe.clear();
            return CookState.START_WORKING;
        }
        return CookState.COOK_SERVE_FOOD_TO_CITIZEN;
    }

    /** MC: a bite at a time from the waiter's best menu dish, until the citizen is full or nothing suits. */
    private void feedInPlace(CitizenData c) {
        Inventory cook = ctx.stock().inventory();
        for (int i = 0; i < FEEDING_ATTEMPTS; i++) {
            int slot = new FoodChoice(ctx.colony(), c).bestSlot(cook, menu());
            if (slot == -1) {
                return;
            }
            ItemAmount stack = cook.slot(slot).orElseThrow();
            cook.set(slot, stack.count() > 1 ? Optional.of(stack.withCount(stack.count() - 1)) : Optional.empty());
            c.hunger().increase(FoodRules.foodValue(ctx.items(), stack.item()));
            if (c.saturation() >= CitizenData.MAX_SATURATION) {
                return;
            }
        }
    }

    /**
     * MC: the dish that suits {@code c} best, {@code ceil(floor(max(1, (60 - s) / value)) x 1.5)} of it, into its
     * inventory if it all fits (else nothing, as MC); false when the waiter has no menu food left (true while one
     * exists but suits no one).
     */
    private boolean handOver(CitizenData c) {
        Inventory cook = ctx.stock().inventory();
        int slot = new FoodChoice(ctx.colony(), c).bestSlot(cook, menu());
        if (slot == -1) {
            return carried(menu()::contains) > 0;
        }
        ItemAmount stack = cook.slot(slot).orElseThrow();
        int qty = (int) Math.max(
                1.0, (CitizenData.MAX_SATURATION - c.saturation()) / FoodRules.foodValue(ctx.items(), stack.item()));
        qty = Math.min(stack.count(), (int) Math.ceil(qty * EXTRA_FOOD));
        if (c.inventory().copy().insert(stack.withCount(qty), ctx.items()::maxStack) == null) {
            c.inventory().insert(stack.withCount(qty), ctx.items()::maxStack);
            int left = stack.count() - qty;
            cook.set(slot, left > 0 ? Optional.of(stack.withCount(left)) : Optional.empty());
            ctx.award(FurnaceWork.BASE_XP_GAIN);
            ctx.job().decreaseSaturationForContinuousAction();
        }
        return true;
    }

    /**
     * MC serveFoodToPlayer, every 30 ticks: walks to the next player still in the hall and gives it menu food up to
     * {@link #SATURATION_TO_SERVE} food value a hall level, with MC's "take this food" message; nothing when it already
     * carries menu food or takes none (a full inventory), and on to the next. Back to START_WORKING after a meal.
     */
    CookState servePlayer() {
        UUID player = playersToServe.peek();
        if (player == null) {
            return CookState.START_WORKING;
        }
        Optional<BlockPos> at = ctx.colony().context().players().position(player);
        if (!inHall(at)) {
            playersToServe.poll();
            return CookState.START_WORKING;
        }
        if (!ctx.walkToWorkPos(at.orElseThrow())) {
            return CookState.COOK_SERVE_FOOD_TO_PLAYER;
        }
        playersToServe.poll();
        Set<ItemKey> menu = menu();
        if (ctx.colony().context().ports().playerInventory().contents(player).keySet().stream()
                .anyMatch(menu::contains)) {
            return CookState.COOK_SERVE_FOOD_TO_PLAYER;
        }
        boolean hasFood = carried(menu::contains) > 0;
        if (PlayerMeal.give(ctx, player, ctx.hall().level() * SATURATION_TO_SERVE) <= 0) {
            if (hasFood) {
                return CookState.COOK_SERVE_FOOD_TO_PLAYER; // MC isItemHandlerFull: on to the next player
            }
            playersToServe.clear();
            return CookState.START_WORKING;
        }
        ctx.colony()
                .context()
                .notifier()
                .send(player, Msg.of("hycolony.cook.servePlayer", ctx.citizen().name()));
        ctx.award(FurnaceWork.BASE_XP_GAIN);
        ctx.job().decreaseSaturationForContinuousAction();
        return CookState.START_WORKING;
    }

    /** MC shouldBeFed: not working, at most average saturation, not just fed. */
    private static boolean shouldBeFed(CitizenData c) {
        return !c.job().map(Job::isWorking).orElse(false)
                && c.saturation() <= EatDecision.AVERAGE_SATURATION
                && !c.hunger().justAte();
    }

    /** MC: the citizen carries a menu dish (MC also asks its home's canEat, true for every home). */
    private boolean carriesMenuFood(CitizenData c) {
        return c.inventory().contents().stream().anyMatch(a -> menu().contains(a.item()));
    }

    private Set<ItemKey> menu() {
        return ctx.menu().menu();
    }

    private int carried(Predicate<ItemKey> wanted) {
        return ctx.stock().inventory().contents().stream()
                .filter(a -> wanted.test(a.item()))
                .mapToInt(ItemAmount::count)
                .sum();
    }

    private boolean hallHas(Predicate<ItemKey> wanted) {
        return ctx.colony().context().ports().containers().contents(ctx.hall().containers()).keySet().stream()
                .anyMatch(wanted);
    }

    private Optional<BlockPos> here(CitizenData c) {
        return ctx.colony()
                .citizens()
                .bodyOf(c.id())
                .flatMap(ctx.colony().context().bodies()::position)
                .map(Vec3::toBlockPos);
    }

    /** MC building.isInBuilding. */
    private boolean inHall(Optional<BlockPos> at) {
        return at.map(p -> HutFootprint.isInBuilding(ctx.colony().context().ports(), ctx.hall(), p))
                .orElse(false);
    }
}
