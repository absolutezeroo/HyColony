package dev.hycolony.core.app.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.HandFeeding;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen.mobInteract with food, from the player's side. */
class CitizenFeedingTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final ItemKey bread = t.catalog.food("bread", 5, 0);
    private final Colony colony;

    CitizenFeedingTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        CitizenData citizen = new CitizenData(1);
        citizen.setName("Jean");
        colony.citizens().restore(citizen);
    }

    @Test
    void aSecondOfferTooSoonIsRefusedWithMcsMessage() {
        CitizenFeeding feeding = new CitizenFeeding(manager);
        t.notifier.sent.clear();
        assertEquals(HandFeeding.Outcome.FED, feeding.offer(alice, colony.id(), 1, bread));
        assertTrue(t.notifier.sent.isEmpty());

        assertEquals(HandFeeding.Outcome.NOT_NOW, feeding.offer(alice, colony.id(), 1, bread));

        assertEquals(
                List.of(new Msg("hycolony.citizen.notNow", List.of("Jean"))),
                t.notifier.sent.stream().map(s -> s.msg()).toList());
    }

    @Test
    void anUnknownCitizenOpensNoFeeding() {
        assertEquals(HandFeeding.Outcome.NOT_FOOD, new CitizenFeeding(manager).offer(alice, colony.id(), 42, bread));
    }
}
