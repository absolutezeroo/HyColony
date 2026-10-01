package dev.hycolony.core.testing.food;

import dev.hycolony.core.crafting.furnace.CookingCatalog;
import dev.hycolony.core.crafting.furnace.CookingStations;
import dev.hycolony.core.crafting.furnace.StationContents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cooking as a test sets it up: station blocks, fuels, raw items and stations by position, each with one input, one
 * fuel and one output stack of at most 64; {@link #cook} turns a lit station's input into its output.
 */
public final class FakeCooking implements CookingCatalog, CookingStations {
    /** One station: its three slots and whether it burns; {@code accelerated} counts the extra ticks it got. */
    public static final class Station {
        public final List<ItemAmount> input = new ArrayList<>();
        public final List<ItemAmount> fuel = new ArrayList<>();
        public final List<ItemAmount> output = new ArrayList<>();
        public boolean lit;
        public int accelerated;
    }

    private static final int SLOT_SIZE = 64;

    public final List<BlockKey> stationBlocks = new ArrayList<>();
    public final List<ItemKey> fuels = new ArrayList<>();
    public final List<ItemKey> defaultFuels = new ArrayList<>();
    /** Dish -> the raw item that cooks into it. */
    public final Map<ItemKey, ItemKey> raws = new HashMap<>();

    public final Map<BlockPos, Station> stations = new LinkedHashMap<>();

    /** A station at {@code pos}; returns it. */
    public Station station(BlockPos pos) {
        return stations.computeIfAbsent(pos, p -> new Station());
    }

    /** The lit station at {@code pos} cooks all its input with {@code cooked} and burns one fuel. */
    public void cook(BlockPos pos, Function<ItemKey, ItemKey> cooked) {
        Station s = stations.get(pos);
        if (s == null || !s.lit || s.input.isEmpty()) {
            return;
        }
        for (ItemAmount in : s.input) {
            s.output.add(new ItemAmount(cooked.apply(in.item()), in.count()));
        }
        s.input.clear();
        s.lit = false;
    }

    @Override
    public boolean isStation(BlockKey block) {
        return stationBlocks.contains(block);
    }

    @Override
    public List<ItemKey> fuels() {
        return List.copyOf(fuels);
    }

    @Override
    public List<ItemKey> defaultFuels() {
        return List.copyOf(defaultFuels);
    }

    @Override
    public Optional<ItemKey> rawFor(ItemKey dish) {
        return Optional.ofNullable(raws.get(dish));
    }

    @Override
    public Optional<StationContents> contents(BlockPos pos) {
        Station s = stations.get(pos);
        return s == null ? Optional.empty() : Optional.of(new StationContents(s.input, s.fuel, s.output, s.lit));
    }

    @Override
    public int insertInput(BlockPos pos, ItemAmount stack) {
        Station s = stations.get(pos);
        return s == null ? 0 : insert(s.input, stack);
    }

    @Override
    public int insertFuel(BlockPos pos, ItemAmount stack) {
        Station s = stations.get(pos);
        return s == null ? 0 : insert(s.fuel, stack);
    }

    private static int insert(List<ItemAmount> slot, ItemAmount stack) {
        if (!slot.isEmpty() && !slot.get(0).item().equals(stack.item())) {
            return 0;
        }
        int there = slot.isEmpty() ? 0 : slot.get(0).count();
        int in = Math.min(stack.count(), SLOT_SIZE - there);
        if (in > 0) {
            slot.clear();
            slot.add(new ItemAmount(stack.item(), there + in));
        }
        return in;
    }

    @Override
    public List<ItemAmount> extractOutput(BlockPos pos) {
        Station s = stations.get(pos);
        return s == null ? List.of() : takeAll(s.output);
    }

    @Override
    public List<ItemAmount> extractFuel(BlockPos pos) {
        Station s = stations.get(pos);
        return s == null ? List.of() : takeAll(s.fuel);
    }

    private static List<ItemAmount> takeAll(List<ItemAmount> slot) {
        List<ItemAmount> out = List.copyOf(slot);
        slot.clear();
        return out;
    }

    @Override
    public boolean light(BlockPos pos) {
        Station s = stations.get(pos);
        if (s == null || s.input.isEmpty() || s.fuel.isEmpty()) {
            return false;
        }
        s.lit = true;
        return true;
    }

    @Override
    public void accelerate(BlockPos pos, int ticks) {
        Station s = stations.get(pos);
        if (s != null && s.lit) {
            s.accelerated += ticks;
        }
    }
}
