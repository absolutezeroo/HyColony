package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC Permissions.hasPermission(Player, Action): the creative operator bypass of colony protection. */
class ColonyProtectionTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final BlockPos inside = hall.offset(3, 0, 3);

    private ColonyManager start(int bypassLevel) {
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                new ColonyConfig.Permissions(true, d.permissions().turnOffExplosionsInColonies(), bypassLevel),
                d.commands(),
                d.client(),
                d.hycolony());
        ColonyManager manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", hall, 0);
        manager.foundation().confirm(alice, "A").orElseThrow();
        return manager;
    }

    @Test
    void creativeOperatorBypassesProtectionWithTheOperatorRankActions() {
        ColonyManager manager = start(2);
        t.players.operators.add(bob);
        t.players.creative.add(bob);

        assertTrue(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.isAllowed(bob, inside, Action.OPEN_CONTAINER));
        assertFalse(manager.isAllowed(bob, inside, Action.EDIT_PERMISSIONS)); // not in MC's OP_RANK
    }

    @Test
    void operatorOutOfCreativeAndCreativeNonOperatorDoNotBypass() {
        ColonyManager manager = start(2);
        t.players.operators.add(bob);
        assertFalse(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));

        t.players.operators.clear();
        t.players.creative.add(bob);
        assertFalse(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
    }

    @Test
    void bypassLevelZeroLetsAnyCreativePlayerThrough() {
        ColonyManager manager = start(0);
        t.players.creative.add(bob);

        assertTrue(manager.isAllowed(bob, inside, Action.PLACE_BLOCKS));
    }
}
