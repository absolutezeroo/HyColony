package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CreateColonyMessage: a colony is founded between Min- and MaxDistanceFromWorldSpawn blocks (2D) of spawn. */
class SpawnDistanceTest {
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();

    private ColonyManager start(int maxDistance, int minDistance) {
        ColonyConfig d = ColonyConfig.defaults();
        ColonyConfig.Claims c = d.claims();
        t.config = new ColonyConfig(
                d.gameplay(),
                new ColonyConfig.Claims(
                        c.maxColonySize(), c.minColonyDistance(), c.initialColonySize(), maxDistance, minDistance),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony(),
                d.structurize());
        t.world.spawn = new BlockPos(0, 100, 0);
        return t.manager();
    }

    private Msg refusal(ColonyManager manager, BlockPos pos) {
        return assertInstanceOf(HutPlacement.Denied.class, manager.huts().checkPlacement(alice, pos, TOWN_HALL))
                .reason();
    }

    @Test
    void townHallCloserToSpawnThanTheMinimumIsRefusedWithTheMissingDistance() {
        ColonyManager manager = start(30000, 100);

        Msg msg = refusal(manager, new BlockPos(30, 64, 40)); // 50 blocks away, height ignored
        assertEquals("hycolony.colony.tooCloseToSpawn", msg.key());
        assertEquals(List.of("50"), msg.params());

        assertInstanceOf(
                HutPlacement.FoundNewColony.class,
                manager.huts().checkPlacement(alice, new BlockPos(100, 64, 0), TOWN_HALL));
    }

    @Test
    void townHallFartherFromSpawnThanTheMaximumIsRefusedWithTheExcessDistance() {
        ColonyManager manager = start(1000, 0);

        Msg msg = refusal(manager, new BlockPos(0, 64, 1500));
        assertEquals("hycolony.colony.tooFarFromSpawn", msg.key());
        assertEquals(List.of("500"), msg.params());

        assertInstanceOf(
                HutPlacement.FoundNewColony.class,
                manager.huts().checkPlacement(alice, new BlockPos(0, 64, 1000), TOWN_HALL));
    }

    @Test
    void unknownSpawnDoesNotBlockFounding() {
        ColonyManager manager = start(1000, 1000);
        t.world.spawn = null;

        assertInstanceOf(
                HutPlacement.FoundNewColony.class,
                manager.huts().checkPlacement(alice, new BlockPos(5, 64, 5), TOWN_HALL));
    }
}
