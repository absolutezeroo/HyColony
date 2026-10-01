package dev.hycolony.plugin.food;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.crafting.furnace.CookingStations;
import dev.hycolony.core.crafting.furnace.StationContents;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * CookingStations over Hytale's processing benches (ProcessingBenchBlock, sp4b-hytale-food § 3.3): the campfire's
 * input, fuel and output containers, setActive to light it (it refuses without fuel), and its input progress to hurry
 * it.
 * Deviation from MC: MC's accelerateFurnaces runs whole furnace ticks (burning fuel too); here only the cooking
 * progress moves on [in-game]. World thread only; never throws (CLAUDE.md § 4).
 */
public final class HytaleCookingStations implements CookingStations {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Game ticks per second: a MC tick is 1/20 s of the bench's progress. */
    private static final float TICKS_PER_SECOND = 20f;

    private final World world;
    private final HytaleStacks stacks;
    private boolean warned;

    public HytaleCookingStations(World world, HytaleStacks stacks) {
        this.world = world;
        this.stacks = stacks;
    }

    /** The bench at a position with its bench block and block state, to turn it on. */
    private record Station(ProcessingBenchBlock bench, BenchBlock block, BlockModule.@Nullable BlockStateInfo info) {}

    private Optional<Station> at(BlockPos p) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, p.x(), p.y(), p.z());
        if (ref == null || !ref.isValid()) {
            return Optional.empty();
        }
        Store<ChunkStore> st = world.getChunkStore().getStore();
        ProcessingBenchBlock bench = st.getComponent(ref, ProcessingBenchBlock.getComponentType());
        BenchBlock block = st.getComponent(ref, BenchBlock.getComponentType());
        if (bench == null || block == null) {
            return Optional.empty();
        }
        return Optional.of(
                new Station(bench, block, st.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())));
    }

    @Override
    public Optional<StationContents> contents(BlockPos pos) {
        return guard(
                () -> at(pos).map(s -> new StationContents(
                        list(s.bench().getInputContainer()),
                        list(s.bench().getFuelContainer()),
                        list(s.bench().getOutputContainer()),
                        s.bench().isActive())),
                Optional.empty());
    }

    @Override
    public int insertInput(BlockPos pos, ItemAmount stack) {
        return guard(
                () -> at(pos).map(s -> insert(s.bench().getInputContainer(), stack))
                        .orElse(0),
                0);
    }

    @Override
    public int insertFuel(BlockPos pos, ItemAmount stack) {
        return guard(
                () -> at(pos).map(s -> insert(s.bench().getFuelContainer(), stack))
                        .orElse(0),
                0);
    }

    @Override
    public List<ItemAmount> extractOutput(BlockPos pos) {
        return guard(
                () -> at(pos).map(s -> takeAll(s.bench().getOutputContainer())).orElse(List.of()), List.of());
    }

    @Override
    public List<ItemAmount> extractFuel(BlockPos pos) {
        return guard(
                () -> at(pos).map(s -> takeAll(s.bench().getFuelContainer())).orElse(List.of()), List.of());
    }

    /** setActive(true), which Hytale refuses with no fuel; true once it burns. */
    @Override
    public boolean light(BlockPos pos) {
        return guard(
                () -> at(pos).map(s -> s.bench().isActive()
                                || (!s.bench().getInputContainer().isEmpty()
                                        && s.bench().setActive(true, s.block(), s.info())))
                        .orElse(false),
                false);
    }

    /** Moves the cooking progress of a burning bench on by {@code ticks} / 20 seconds. */
    @Override
    public void accelerate(BlockPos pos, int ticks) {
        guard(
                () -> at(pos).filter(s -> s.bench().isActive() && s.bench().getRecipe() != null)
                        .map(s -> {
                            s.bench().setInputProgress(s.bench().getInputProgress() + ticks / TICKS_PER_SECOND);
                            return true;
                        })
                        .orElse(false),
                false);
    }

    private List<ItemAmount> list(ItemContainer c) {
        List<ItemAmount> out = new ArrayList<>();
        c.forEach((slot, s) -> out.add(stacks.toAmount(s)));
        return out;
    }

    /** Adds what fits of {@code a}; returns how many went in. */
    private int insert(ItemContainer c, ItemAmount a) {
        ItemStack rem = c.addItemStack(stacks.toStack(a)).getRemainder();
        return a.count() - (rem == null || ItemStack.isEmpty(rem) ? 0 : rem.getQuantity());
    }

    /** Empties {@code c}, slot by slot. */
    private List<ItemAmount> takeAll(ItemContainer c) {
        List<ItemAmount> out = new ArrayList<>();
        for (short s = 0; s < c.getCapacity(); s++) {
            ItemStack st = c.getItemStack(s);
            if (st != null
                    && !ItemStack.isEmpty(st)
                    && c.removeItemStackFromSlot(s, st.getQuantity()).succeeded()) {
                out.add(stacks.toAmount(st));
            }
        }
        return out;
    }

    private <T> T guard(Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("CookingStations failed");
            warned = true;
            return fallback;
        }
    }
}
