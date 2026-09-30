package dev.hylens.core.watch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Pos;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hylens.core.testing.FakeColonyWorld;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** When a watch has lost its citizen for good (spec 2026-09-30, § 6.1). */
class WatchLossTest {
    private static final ColonyRef COLONY = new ColonyRef("default", 1);
    private static final CitizenRef ANN = new CitizenRef(COLONY, 4);

    private static ColonyWorld with(CitizenRef... members) {
        FakeColonyWorld world = new FakeColonyWorld();
        CitizenSnapshot[] citizens = new CitizenSnapshot[members.length];
        for (int i = 0; i < members.length; i++) {
            citizens[i] = new CitizenSnapshot(
                    members[i], "Ann", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
        return world.colony(new ColonySummary(COLONY, "Alpha", new Pos(0, 64, 0), UUID.randomUUID(), 1), citizens);
    }

    @Test
    void citizenHyColonyNoLongerKnowsIsLost() {
        assertTrue(WatchLoss.gone("default", Optional.of(new FakeColonyWorld()), ANN), "its colony deleted");
    }

    @Test
    void citizenStillKnownIsNotLost() {
        assertFalse(WatchLoss.gone("default", Optional.of(with(ANN)), ANN));
    }

    @Test
    void citizenOfAnotherWorldIsKeptForTheOperatorsReturn() {
        assertFalse(WatchLoss.gone("nether", Optional.of(new FakeColonyWorld()), ANN));
    }

    @Test
    void worldWhereHyColonyDoesNotRunLosesNothing() {
        assertFalse(WatchLoss.gone("default", Optional.empty(), ANN));
    }
}
