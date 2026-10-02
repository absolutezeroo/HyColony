package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.requests.RequestSystemReset;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A request system reset empties a courier's own queue, which MC keeps in the stores it renews. */
class CourierResetTest {
    @Test
    void aResetEmptiesACouriersQueue() {
        TestContexts t = new TestContexts();
        ColonyManager manager = t.manager();
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "A").orElseThrow();
        CitizenData courier = new CitizenData(1);
        DeliverymanJob job = new DeliverymanJob(courier);
        courier.setJob(job);
        colony.citizens().restore(courier);
        job.mutableQueue().add(RequestToken.random());

        RequestSystemReset.reset(colony);

        assertTrue(job.taskQueue().isEmpty());
    }
}
