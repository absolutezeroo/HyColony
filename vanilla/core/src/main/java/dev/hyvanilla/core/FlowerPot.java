package dev.hyvanilla.core;

import java.util.Optional;
import java.util.Set;

/**
 * What using a flower pot does, like Minecraft FlowerPotBlock.useItemOn then useWithoutItem: an empty pot takes a
 * pottable plant (one consumed unless creative, Minecraft ItemStack.consume); a filled pot gives its plant back unless
 * the player holds a pottable plant. Deviation from MC: a requested addition, the flower pot being a vanilla
 * Minecraft block that MineColonies only uses in its schematics.
 *
 * @param pottable the plant items a pot accepts (HyVanilla's id-map table)
 */
public record FlowerPot(Set<String> pottable) {
    /** The outcome of one use. */
    public sealed interface Outcome permits Plant, GiveBack, Nothing {}

    /** Puts {@code plant} in the pot; {@code consume}: take one from the player's hand. */
    public record Plant(String plant, boolean consume) implements Outcome {}

    /** Empties the pot and gives {@code plant} to the player (dropped at his feet when his inventory is full). */
    public record GiveBack(String plant) implements Outcome {}

    /** Leaves pot and player as they are. */
    public record Nothing() implements Outcome {}

    /** The outcome when nothing changes. */
    public static final Outcome NOTHING = new Nothing();

    public FlowerPot {
        pottable = Set.copyOf(pottable);
    }

    /**
     * The outcome of using the pot holding {@code potted} (empty for an empty pot) with {@code held} in hand (empty for
     * an empty hand).
     */
    public Outcome use(Optional<String> potted, Optional<String> held, boolean creative) {
        boolean holdsPlant = held.filter(pottable::contains).isPresent();
        if (potted.isPresent()) {
            return holdsPlant ? NOTHING : new GiveBack(potted.get());
        }
        return holdsPlant ? new Plant(held.get(), !creative) : NOTHING;
    }
}
