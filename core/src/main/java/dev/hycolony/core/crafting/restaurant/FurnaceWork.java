package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.crafting.furnace.FuelRequests;
import dev.hycolony.core.crafting.furnace.StationContents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;
import org.jspecify.annotations.Nullable;

/**
 * The waiter's work at the campfires (MC AbstractEntityAIUsesFurnace, with EntityAIWorkCook's isSmeltable and
 * extractFromFurnace): empties a station of a fuel no longer allowed or of what it cooked, asks for fuel, gathers raw
 * food and fuel from the hall, fills a station and lights it. Deviation from MC: the complaints (no fuel listed, no
 * station, empty menu) are interactions, which HyColony lacks: the waiter just waits. MC's requestSmeltable only
 * complains for the waiter, so neither it nor reachedMaxToKeep, which only gates it, is ported.
 */
final class FurnaceWork {
    /** MC BASE_XP_GAIN: experience for each cooked load taken out. */
    static final double BASE_XP_GAIN = 2;
    /** MC RETRIEVE_SMELTABLE_IF_MORE_THAN: a station is emptied past this many cooked items. */
    static final int RETRIEVE_IF_MORE_THAN = 10;
    /** MC STACKSIZE: the most moved at once. */
    static final int STACKSIZE = 64;

    private final CookWorkContext ctx;
    private final CookService service;
    private @Nullable BlockPos walkTo;
    /** MC needsCurrently: what GATHERING_REQUIRED_MATERIALS fetches from the hall. */
    private @Nullable Predicate<ItemKey> needs;

    FurnaceWork(CookWorkContext ctx, CookService service) {
        this.ctx = ctx;
        this.service = service;
    }

    /** MC EntityAIWorkCook.isSmeltable: cooks into a dish on the menu. */
    boolean isSmeltable(ItemKey item) {
        return ctx.items().cooked(item).map(ctx.menu().menu()::contains).orElse(false);
    }

    /** MC needsCurrently: {@link #gather} fetches what {@code wanted} accepts, a stack at most. */
    void need(Predicate<ItemKey> wanted) {
        needs = wanted;
    }

    /**
     * MC startWorking, every 60 ticks: at the hall, the waiter's serving first ({@link CookService#importantJobs}),
     * then a station to clear of forbidden fuel, a station to empty, a fuel request, food or fuel to fetch, a station
     * to fill; else it starts again.
     */
    CookState startWorking() {
        if (!ctx.walkToHall() || ctx.furnaces().stations().isEmpty()) {
            return CookState.START_WORKING; // MC: no station, it waits (the complaint is an interaction)
        }
        CookState next = service.importantJobs(this::need);
        if (next != CookState.START_WORKING) {
            return next;
        }
        Optional<BlockPos> station = firstStation(this::hasForbiddenFuel);
        if (station.isPresent()) {
            walkTo = station.get();
            return CookState.RETRIEVING_USED_FUEL_FROM_FURNACE;
        }
        station = firstStation(FurnaceWork::toEmpty);
        if (station.isPresent()) {
            walkTo = station.get();
            return CookState.RETRIEVING_END_PRODUCT_FROM_FURNACE;
        }
        return supply();
    }

    /** MC startWorking's second half: counts, the fuel request, gathering, then a station to fill. */
    private CookState supply() {
        Predicate<ItemKey> fuel = item -> ctx.fuel().allows(ctx.colony(), item);
        int smeltableInHall = inHall(this::isSmeltable);
        int smeltableCarried = carried(this::isSmeltable);
        int fuelInHall = inHall(fuel);
        int fuelCarried = carried(fuel);
        if (fuelInHall + fuelCarried <= 0 && !FuelRequests.open(ctx.colony(), ctx.hall())) {
            FuelRequests.ask(
                    ctx.colony(),
                    ctx.hall(),
                    new ArrayList<>(ctx.fuel().fuels(ctx.colony())),
                    ctx.furnaces().stations().size());
        }
        if (smeltableInHall > 0 && smeltableCarried == 0) {
            need(this::isSmeltable);
            return CookState.GATHERING_REQUIRED_MATERIALS;
        }
        if (fuelInHall > 0 && fuelCarried == 0) {
            need(fuel);
            return CookState.GATHERING_REQUIRED_MATERIALS;
        }
        return stationToFill(fuelInHall + fuelCarried, smeltableInHall + smeltableCarried);
    }

    /**
     * MC checkIfAbleToSmelt: the first station with input but no fuel (fuel at hand), fuel but no input (food at
     * hand), or neither (both at hand); a position whose loaded block is no station any more is dropped (MC
     * removeFromFurnaces). Deviation from MC: a MC furnace lights itself, a Hytale bench must be turned on, and a
     * player or a full output puts it out; a station passed over with fuel and something that cooks, but out, is lit
     * again (an input that cooks into nothing would only go out again).
     */
    private CookState stationToFill(int fuel, int smeltable) {
        for (BlockPos pos : List.copyOf(ctx.furnaces().stations())) {
            Optional<StationContents> c = ctx.stations().contents(pos);
            if (c.isEmpty()) {
                forgetIfNoStation(pos);
                continue;
            }
            StationContents s = c.get();
            if (canFill(s, fuel > 0, smeltable > 0)) {
                walkTo = pos;
                return CookState.FILL_UP_FURNACES;
            }
            if (!s.lit()
                    && s.fuelCount() > 0
                    && s.input().stream()
                            .anyMatch(a -> ctx.items().cooked(a.item()).isPresent())) {
                ctx.stations().light(pos);
            }
        }
        return CookState.START_WORKING;
    }

    /** MC removeFromFurnaces: forgets {@code pos} when its loaded block is no cooking station; marks the colony. */
    private void forgetIfNoStation(BlockPos pos) {
        boolean gone = ctx.colony()
                .context()
                .ports()
                .blocks()
                .get(pos)
                .filter(b -> !ctx.colony().context().ports().cooking().catalog().isStation(b.key()))
                .isPresent();
        if (gone) {
            ctx.furnaces().removeStation(pos);
            ctx.colony().markDirty();
        }
    }

    /** MC checkIfAbleToSmelt's test for one station, given whether fuel and raw food are at hand. */
    private static boolean canFill(StationContents s, boolean fuel, boolean smeltable) {
        boolean input = s.inputCount() > 0;
        boolean burning = s.fuelCount() > 0;
        return (fuel && input && !burning)
                || (smeltable && burning && !input)
                || (fuel && smeltable && !input && !burning);
    }

    /**
     * MC fillUpFurnace: at the station, raw food into an empty input and fuel into an empty fuel slot, a stack each,
     * then lights it (Hytale's bench must be turned on, a MC furnace lights itself).
     */
    CookState fillUp() {
        BlockPos pos = walkTo;
        if (pos == null || ctx.stations().contents(pos).isEmpty()) {
            walkTo = null;
            return CookState.START_WORKING;
        }
        if (!ctx.walkToWorkPos(pos)) {
            return CookState.FILL_UP_FURNACES;
        }
        StationContents s = ctx.stations().contents(pos).orElseThrow();
        if (s.inputCount() == 0) {
            moveFirst(this::isSmeltable, ctx.stations()::insertInput, pos);
        }
        StationContents after = ctx.stations().contents(pos).orElse(s);
        if (after.fuelCount() == 0) {
            moveFirst(item -> ctx.fuel().allows(ctx.colony(), item), ctx.stations()::insertFuel, pos);
        }
        ctx.stations().light(pos);
        walkTo = null;
        return CookState.START_WORKING;
    }

    /**
     * MC retrieveSmeltableFromFurnace with the waiter's extractFromFurnace: at the station, everything it cooked goes
     * to the waiter if it all fits (else it stays), for {@link #BASE_XP_GAIN} and two actions either way (MC counts
     * one in each).
     */
    CookState retrieveProduct() {
        BlockPos pos = walkTo;
        if (pos == null) {
            return CookState.START_WORKING;
        }
        if (!ctx.walkToWorkPos(pos)) {
            return CookState.RETRIEVING_END_PRODUCT_FROM_FURNACE;
        }
        walkTo = null;
        Optional<StationContents> s = ctx.stations().contents(pos);
        if (s.map(StationContents::outputCount).orElse(0) == 0) {
            return CookState.START_WORKING;
        }
        takeIfAllFit(s.get().output(), () -> ctx.stations().extractOutput(pos));
        ctx.award(BASE_XP_GAIN);
        ctx.job().incrementActionsAndDecSaturation();
        ctx.job().incrementActionsAndDecSaturation();
        return CookState.START_WORKING;
    }

    /** MC retrieveUsedFuel: at the station, the fuel no longer allowed goes back to the waiter if it all fits. */
    CookState retrieveFuel() {
        BlockPos pos = walkTo;
        if (pos == null) {
            return CookState.START_WORKING;
        }
        if (!ctx.walkToWorkPos(pos)) {
            return CookState.RETRIEVING_USED_FUEL_FROM_FURNACE;
        }
        walkTo = null;
        ctx.stations()
                .contents(pos)
                .ifPresent(s -> takeIfAllFit(s.fuel(), () -> ctx.stations().extractFuel(pos)));
        return CookState.START_WORKING;
    }

    /**
     * MC transferItemStackIntoNextFreeSlotInItemHandler: {@code extract}s the station's {@code items} into the
     * waiter's inventory only when all of them fit; else they stay in the station.
     */
    private void takeIfAllFit(List<ItemAmount> items, Supplier<List<ItemAmount>> extract) {
        Inventory probe = ctx.stock().inventory().copy();
        for (ItemAmount a : items) {
            if (probe.insert(a, ctx.items()::maxStack) != null) {
                return;
            }
        }
        ctx.stock().storeDrops(extract.get());
    }

    /**
     * MC getNeededItem for GATHERING_REQUIRED_MATERIALS: at the hall, takes a stack of what {@link #need} asked for
     * from its containers (the waiter carries none of it when it asks). Deviation from MC, as CraftingWork's gather:
     * the hall's containers are one stock, where MC walks to the chest holding the item.
     */
    CookState gather() {
        Predicate<ItemKey> wanted = needs;
        if (wanted == null) {
            return CookState.START_WORKING;
        }
        if (!ctx.walkToHall()) {
            return CookState.GATHERING_REQUIRED_MATERIALS;
        }
        int left = STACKSIZE;
        for (ItemKey item : hallContents().keySet()) {
            if (left > 0 && wanted.test(item)) {
                left -= ctx.stock().take(item, left);
            }
        }
        needs = null;
        return CookState.START_WORKING;
    }

    /**
     * MC accelerateFurnaces, every second in every state: each burning station works (primary skill / 10) x 2 game
     * ticks more. Returns false: it changes no state.
     */
    boolean accelerate() {
        int ticks = (ctx.citizen().skills().level(ctx.workers().primary()) / 10) * 2;
        if (ticks > 0) {
            for (BlockPos pos : ctx.furnaces().stations()) {
                if (ctx.stations().contents(pos).map(StationContents::lit).orElse(false)) {
                    ctx.stations().accelerate(pos, ticks);
                }
            }
        }
        return false;
    }

    /** MC getPositionOfOvenToRetrieveFrom's test: out and done, or more than ten cooked, or cooked with no input. */
    private static boolean toEmpty(StationContents s) {
        int out = s.outputCount();
        return (!s.lit() && out > 0) || out > RETRIEVE_IF_MORE_THAN || (out > 0 && s.inputCount() == 0);
    }

    /** MC getPositionOfOvenToRetrieveFuelFrom's test: a fuel no longer allowed. */
    private boolean hasForbiddenFuel(StationContents s) {
        return s.fuel().stream().anyMatch(f -> !ctx.fuel().allows(ctx.colony(), f.item()));
    }

    private Optional<BlockPos> firstStation(Predicate<StationContents> test) {
        for (BlockPos pos : ctx.furnaces().stations()) {
            if (ctx.stations().contents(pos).filter(test).isPresent()) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /**
     * MC transferXOfFirstSlotInItemHandlerWithIntoInItemHandler: up to a stack from the waiter's first slot that
     * {@code wanted} accepts into the station; what does not go in stays.
     */
    private void moveFirst(Predicate<ItemKey> wanted, ToIntBiFunction<BlockPos, ItemAmount> insert, BlockPos pos) {
        Inventory inv = ctx.stock().inventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemAmount a = inv.slot(i).orElse(null);
            if (a != null && wanted.test(a.item())) {
                int moved = insert.applyAsInt(pos, a.withCount(Math.min(STACKSIZE, a.count())));
                inv.set(i, moved >= a.count() ? Optional.empty() : Optional.of(a.withCount(a.count() - moved)));
                return;
            }
        }
    }

    private Map<ItemKey, Integer> hallContents() {
        return ctx.colony().context().ports().containers().contents(ctx.hall().containers());
    }

    private int inHall(Predicate<ItemKey> wanted) {
        int n = 0;
        for (Map.Entry<ItemKey, Integer> e : hallContents().entrySet()) {
            if (wanted.test(e.getKey())) {
                n += e.getValue();
            }
        }
        return n;
    }

    private int carried(Predicate<ItemKey> wanted) {
        return ctx.stock().inventory().contents().stream()
                .filter(a -> wanted.test(a.item()))
                .mapToInt(ItemAmount::count)
                .sum();
    }
}
