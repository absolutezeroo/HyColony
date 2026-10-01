package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC RecallSingleCitizenMessage: the town hall's "Recall Citizen" brings one citizen to the town hall. */
class CitizenRecallTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final CitizenRecall recall = new CitizenRecall(manager);
    private final Colony colony;

    CitizenRecallTest() {
        manager.foundation().begin(alice, "Alice", HALL, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    private CitizenData citizen(int id) {
        CitizenData d = new CitizenData(id);
        colony.citizens().restore(d);
        return d;
    }

    @Test
    void recallTeleportsALivingCitizenToTheTownHall() {
        citizen(1);
        colony.citizens().respawnBody(1);

        assertTrue(recall.recall(alice, colony.id(), 1));

        assertEquals(List.of(Vec3.center(HALL)), t.bodies.teleports);
        assertTrue(t.ui.shown.get(alice) instanceof TownHallView, "the town hall is shown again");
    }

    @Test
    void recallGivesABodilessCitizenABodyAtTheTownHall() {
        CitizenData d = citizen(1);
        d.setLastPosition(Vec3.center(new BlockPos(60, 64, 0)));

        assertTrue(recall.recall(alice, colony.id(), 1));

        BodyId body = colony.citizens().bodyOf(1).orElseThrow();
        // Beside the hut block, north first (MC getSpawnPoint refuses the hut's own cell).
        assertEquals(HALL.offset(0, 0, -1), t.bodies.bodies.get(body).position.toBlockPos());
    }

    @Test
    void recallSaysWhenTheBodyCannotAppear() {
        citizen(1);
        t.bodies.refuseSpawn = true;

        recall.recall(alice, colony.id(), 1);

        assertEquals(
                Msg.of("hycolony.hut.recallFail"), t.notifier.sent.getLast().msg());
    }

    @Test
    void recallNeedsManageHuts() {
        citizen(1);
        assertFalse(recall.recall(UUID.randomUUID(), colony.id(), 1));
        assertTrue(colony.citizens().bodyOf(1).isEmpty());
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key());
    }

    @Test
    void recallOfAnUnknownCitizenDoesNothing() {
        assertFalse(recall.recall(alice, colony.id(), 42));
    }
}
