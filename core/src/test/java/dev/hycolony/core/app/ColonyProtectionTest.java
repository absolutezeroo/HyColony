package dev.hycolony.core.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.config.Explosions;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The creative operator bypass (MC Permissions.hasPermission), breaking a hut (MC ColonyPermissionEventHandler) and
 * explosions (MC TurnOffExplosionsInColonies).
 */
class ColonyProtectionTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final BlockPos inside = hall.offset(3, 0, 3);

    private ColonyManager start(int bypassLevel) {
        return start(new ColonyConfig.Permissions(true, Explosions.DAMAGE_ENTITIES, bypassLevel));
    }

    private ColonyManager start(ColonyConfig.Permissions permissions) {
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                permissions,
                d.commands(),
                d.client(),
                d.hycolony(),
                d.structurize(),
                d.combat());
        ColonyManager manager = t.manager();
        manager.foundation().begin(alice, "Alice", hall, 0);
        manager.foundation().confirm(alice, "A").orElseThrow();
        return manager;
    }

    /** Asked for: no hostile spawn anywhere in a colony's territory, and outside it as usual. */
    @Test
    void hostileSpawnsAreRefusedInTheTerritoryOnly() {
        ColonyManager manager = start(2);

        assertFalse(manager.protection().allowsHostileSpawn(inside));
        assertFalse(manager.protection().allowsHostileSpawn(new BlockPos(79, 20, -64)), "a far claimed cell");
        assertTrue(manager.protection().allowsHostileSpawn(new BlockPos(80, 64, 0)), "the first unclaimed cell");
    }

    /** Asked for: an area touching the territory (cells -4..4, blocks -64..79) is not wilderness. */
    @Test
    void anAreaTouchingTheTerritoryIsNotWilderness() {
        ColonyManager manager = start(2);

        assertFalse(manager.protection().isWilderness(64, 0, 32), "its first cell is claimed");
        assertFalse(manager.protection().isWilderness(-80, -80, 32), "its last cell is claimed");
        assertTrue(manager.protection().isWilderness(80, 0, 32), "beside the border");
        assertTrue(manager.protection().isWilderness(-96, 0, 32), "beside the border, negative side");
    }

    @Test
    void creativeOperatorBypassesProtectionWithTheOperatorRankActions() {
        ColonyManager manager = start(2);
        t.players.operators.add(bob);
        t.players.creative.add(bob);

        assertTrue(manager.protection().isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.protection().isAllowed(bob, inside, Action.OPEN_CONTAINER));
        assertFalse(manager.protection().isAllowed(bob, inside, Action.EDIT_PERMISSIONS)); // not in MC's OP_RANK
    }

    @Test
    void operatorOutOfCreativeAndCreativeNonOperatorDoNotBypass() {
        ColonyManager manager = start(2);
        t.players.operators.add(bob);
        assertFalse(manager.protection().isAllowed(bob, inside, Action.BREAK_BLOCKS));

        t.players.operators.clear();
        t.players.creative.add(bob);
        assertFalse(manager.protection().isAllowed(bob, inside, Action.BREAK_BLOCKS));
    }

    @Test
    void bypassLevelZeroLetsAnyCreativePlayerThrough() {
        ColonyManager manager = start(0);
        t.players.creative.add(bob);

        assertTrue(manager.protection().isAllowed(bob, inside, Action.PLACE_BLOCKS));
    }

    @Test
    void protectionRefusesAStrangersActionAndTellsHim() {
        ColonyManager manager = start(2);

        assertTrue(manager.protection().refuses(bob, inside, Action.BREAK_BLOCKS));

        assertEquals(
                "hycolony.permission.denied", t.notifier.sent.getLast().msg().key());
        assertFalse(manager.protection().refuses(alice, inside, Action.BREAK_BLOCKS));
    }

    @Test
    void withoutColonyProtectionNoActionIsRefused() {
        ColonyManager manager = start(new ColonyConfig.Permissions(false, Explosions.DAMAGE_ENTITIES, 2));

        assertFalse(manager.protection().refuses(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.protection().allows(bob, inside, Action.OPEN_CONTAINER));
    }

    @Test
    void strangerOpeningAChestInsideTheColonyIsRefused() {
        ColonyManager manager = start(2);
        BlockUse chest = new BlockUse(false, true, false, BlockUse.Held.NOTHING);

        assertTrue(manager.protection().refuses(bob, inside, chest));
        assertFalse(manager.protection().refuses(bob, new BlockPos(9000, 64, 0), chest), "outside any colony");
    }

    @Test
    void strangerCannotBreakAHutAndIsTold() {
        ColonyManager manager = start(2);

        assertFalse(manager.huts().breakBy(bob, hall));

        assertTrue(manager.colonyAt(hall).orElseThrow().buildings().townHall().isPresent());
        assertEquals(
                "hycolony.permission.denied", t.notifier.sent.getLast().msg().key());
    }

    @Test
    void memberWithBreakHutsBreaksAHut() {
        ColonyManager manager = start(2);

        assertTrue(manager.huts().breakBy(alice, hall));

        assertTrue(manager.colonyAt(hall).orElseThrow().buildings().townHall().isEmpty());
    }

    @Test
    void breakingAnUnconfirmedTownHallCancelsItsFoundation() {
        ColonyManager manager = start(2);
        BlockPos far = new BlockPos(5000, 64, 0);
        manager.foundation().begin(bob, "Bob", far, 0);

        assertTrue(manager.huts().breakBy(alice, far));

        assertTrue(manager.foundation().pendingPositionOf(bob).isEmpty());
    }

    @Test
    void hutBlockWithoutItsBuildingBreaksUnchecked() {
        ColonyManager manager = start(2);

        assertTrue(manager.huts().breakBy(bob, inside), "MC: no building there, the event goes through");

        assertTrue(t.notifier.sent.stream().noneMatch(s -> s.player().equals(bob)));
    }

    @Test
    void withoutColonyProtectionAnyPlayerBreaksAHut() {
        ColonyManager manager = start(new ColonyConfig.Permissions(false, Explosions.DAMAGE_ENTITIES, 2));

        assertTrue(manager.huts().breakBy(bob, hall));

        assertTrue(manager.colonyAt(hall).orElseThrow().buildings().townHall().isEmpty(), "MC: building.destroy()");
    }

    @Test
    void explosionsSpareColonyBlocksUnlessDamageEverything() {
        ColonyManager manager = start(new ColonyConfig.Permissions(false, Explosions.DAMAGE_ENTITIES, 2));
        assertTrue(manager.protection().explosionSparesBlock(inside)); // regardless of EnableColonyProtection, like MC
        assertFalse(manager.protection().explosionSparesBlock(new BlockPos(9000, 64, 0)));

        manager = start(new ColonyConfig.Permissions(true, Explosions.DAMAGE_EVERYTHING, 2));
        assertFalse(manager.protection().explosionSparesBlock(inside));
    }
}
