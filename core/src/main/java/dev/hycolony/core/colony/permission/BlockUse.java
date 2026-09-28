package dev.hycolony.core.colony.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A player using (right-clicking) a block inside a colony, as MC ColonyPermissionEventHandler.on(PlayerInteractEvent)
 * sees it. Hut blocks are not handled here (their window checks ACCESS_HUTS).
 *
 * <p>Deviation from MC: no free blocks or free positions (MC Colony.getFreeBlocks/getFreePositions and the
 * colonyProtectionException tag) since HyColony has no way yet to declare them; no scan tool check, HyColony has no
 * scan tool; no PvP-mode exception.
 *
 * @param toggleable a door or a gate (Minecraft BlockTags.DOORS, FENCE_GATES)
 * @param container a block holding items (Minecraft BaseEntityBlock with a container)
 * @param blockEntity a block with a block entity (Minecraft Level.getBlockEntity != null)
 * @param held what the player holds
 */
public record BlockUse(boolean toggleable, boolean container, boolean blockEntity, Held held) {
    /**
     * The held item, as far as the rule cares. MC's {@code stack.isEdible()} return only skips the potion and scan
     * tool checks, which food never matches, so food is {@link #OTHER}.
     */
    public enum Held {
        NOTHING,
        /** Minecraft PotionItem. */
        POTION,
        OTHER
    }

    /**
     * The action the player lacks for this use, or empty when it is allowed. {@code allowed}: what the player's rank
     * may do in this colony; {@code protection}: MC enableColonyProtection. Same order of checks as MC.
     */
    public Optional<Action> refused(Predicate<Action> allowed, boolean protection) {
        if ((toggleable && allowed.test(Action.ACCESS_TOGGLEABLES)) || !protection) {
            return Optional.empty();
        }
        return needed().stream().filter(allowed.negate()).findFirst();
    }

    /** The actions this use needs, in MC's order of checks. */
    private List<Action> needed() {
        List<Action> needed = new ArrayList<>(List.of(Action.RIGHTCLICK_BLOCK));
        if (container) {
            needed.add(Action.OPEN_CONTAINER);
        }
        if (blockEntity) {
            needed.add(Action.RIGHTCLICK_ENTITY);
        }
        if (held == Held.POTION) {
            needed.add(Action.THROW_POTION);
        }
        return needed;
    }
}
