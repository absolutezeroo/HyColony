package dev.hycolony.core.decoration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every flower pot block of the Decorations pack: one empty pot per colour, and for each the block holding each plant
 * (Minecraft's potted_&lt;plant&gt; blocks, one set per colour). Deviation from MC: coloured pots are a requested
 * addition; Minecraft has a single flower pot.
 */
public final class FlowerPotBlocks {
    /** A pot block: its empty pot (its colour) and the plant it holds, if any. */
    public record Pot(String pot, Optional<String> plant) {}

    private final Map<String, Map<String, String>> pots;
    private final Map<String, Pot> byBlock = new HashMap<>();
    private final Set<String> plants = new HashSet<>();

    /** {@code pots}: empty pot block -> plant item -> that pot's block holding the plant. */
    public FlowerPotBlocks(Map<String, Map<String, String>> pots) {
        this.pots = Map.copyOf(pots);
        pots.forEach((pot, potted) -> {
            byBlock.put(pot, new Pot(pot, Optional.empty()));
            potted.forEach((plant, block) -> byBlock.put(block, new Pot(pot, Optional.of(plant))));
            plants.addAll(potted.keySet());
        });
    }

    /** The pot that {@code block} is, or empty for any other block. */
    public Optional<Pot> find(String block) {
        return Optional.ofNullable(byBlock.get(block));
    }

    /**
     * The block of {@code pot} holding {@code plant} (the empty pot for none); empty when that pot has no such block.
     */
    public Optional<String> block(String pot, Optional<String> plant) {
        Map<String, String> potted = pots.get(pot);
        if (potted == null) {
            return Optional.empty();
        }
        return plant.isEmpty() ? Optional.of(pot) : Optional.ofNullable(potted.get(plant.get()));
    }

    /** The plants some pot can hold. */
    public Set<String> plants() {
        return Set.copyOf(plants);
    }
}
