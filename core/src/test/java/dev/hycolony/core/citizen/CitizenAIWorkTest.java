package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAIWorkTest {
    private final TestContexts t = new TestContexts();

    @Test
    void fireReturnsCitizenToWander() {
        BlockPos hall = new BlockPos(0, 64, 0);
        Colony c = new Colony(t.context(), new TerritoryIndex(), 1, "T", hall,
                Permissions.createDefault(UUID.randomUUID(), "A"));
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
        d.setJob(TestJobs.TYPE.factory().apply(d));

        CitizenAI ai = new CitizenAI(c, d, body);
        for (int i = 0; i < 30 && ai.state() != CitizenState.WORKING; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.WORKING, ai.state());

        d.setJob(null); // fire(): the job is removed
        for (int i = 0; i < 5 && ai.state() != CitizenState.IDLE; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.IDLE, ai.state()); // back to its errand, no longer working
    }
}
