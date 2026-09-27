package dev.hycolony.core.colony.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.permission.BlockUse.Held;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockUseTest {
    private static final BlockUse PLAIN = new BlockUse(false, false, false, Held.OTHER);

    private static Optional<Action> refused(BlockUse use, Set<Action> allowed, boolean protection) {
        return use.refused(allowed::contains, protection);
    }

    @Test
    void aStrangerCannotUseAnyBlock() {
        assertEquals(Optional.of(Action.RIGHTCLICK_BLOCK), refused(PLAIN, Set.of(), true));
    }

    @Test
    void rightClickBlockAllowsAPlainBlock() {
        assertEquals(Optional.empty(), refused(PLAIN, Set.of(Action.RIGHTCLICK_BLOCK), true));
    }

    @Test
    void withoutColonyProtectionEveryUseIsAllowed() {
        assertEquals(Optional.empty(), refused(PLAIN, Set.of(), false));
    }

    @Test
    void doorsAndGatesOnlyNeedAccessToToggleables() {
        BlockUse door = new BlockUse(true, false, false, Held.OTHER);
        assertEquals(Optional.empty(), refused(door, Set.of(Action.ACCESS_TOGGLEABLES), true));
        assertEquals(Optional.of(Action.RIGHTCLICK_BLOCK), refused(door, Set.of(), true));
    }

    @Test
    void aContainerAlsoNeedsOpenContainer() {
        BlockUse chest = new BlockUse(false, true, true, Held.NOTHING);
        assertEquals(Optional.of(Action.OPEN_CONTAINER), refused(chest, Set.of(Action.RIGHTCLICK_BLOCK), true));
        assertEquals(
                Optional.of(Action.RIGHTCLICK_ENTITY),
                refused(chest, EnumSet.of(Action.RIGHTCLICK_BLOCK, Action.OPEN_CONTAINER), true));
    }

    @Test
    void aBlockWithABlockEntityAlsoNeedsRightClickEntity() {
        BlockUse bench = new BlockUse(false, false, true, Held.NOTHING);
        assertEquals(Optional.of(Action.RIGHTCLICK_ENTITY), refused(bench, Set.of(Action.RIGHTCLICK_BLOCK), true));
    }

    @Test
    void aPotionInHandNeedsThrowPotion() {
        BlockUse potion = new BlockUse(false, false, false, Held.POTION);
        assertEquals(Optional.of(Action.THROW_POTION), refused(potion, Set.of(Action.RIGHTCLICK_BLOCK), true));
        assertEquals(Optional.empty(), refused(potion, EnumSet.of(Action.RIGHTCLICK_BLOCK, Action.THROW_POTION), true));
    }

    @Test
    void foodOrAnEmptyHandNeedNothingMore() {
        assertEquals(
                Optional.empty(),
                refused(new BlockUse(false, false, false, Held.FOOD), Set.of(Action.RIGHTCLICK_BLOCK), true));
        assertEquals(
                Optional.empty(),
                refused(new BlockUse(false, false, false, Held.NOTHING), Set.of(Action.RIGHTCLICK_BLOCK), true));
    }
}
