package dev.hylens.core.send;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Where "send here" sends a citizen, and which one (spec 2026-09-30, § 6.6). */
class SendTargetTest {
    private static final CitizenRef ANN = new CitizenRef(new ColonyRef("default", 1), 4);
    private static final CitizenRef BOB = new CitizenRef(new ColonyRef("default", 1), 7);

    @Test
    void citizenStandsOnTheBlockAimedOrTheGround() {
        assertEquals(new Pos(10, 65, -3), SendTarget.standingOn(10, 64, -3));
    }

    @Test
    void menuCoordinatesAreReadWholeNumbersSpacesIgnored() {
        assertEquals(Optional.of(new Pos(12, 64, -300)), SendTarget.parse(" 12", "64 ", "-300"));
    }

    @Test
    void anyMissingOrWrongCoordinateReadsNothing() {
        assertEquals(Optional.empty(), SendTarget.parse("12", "", "3"));
        assertEquals(Optional.empty(), SendTarget.parse("12", "6.5", "3"));
        assertEquals(Optional.empty(), SendTarget.parse("x", "64", "3"));
    }

    @Test
    void watchedCitizenGoesFirstThenTheChosenOne() {
        assertEquals(Optional.of(ANN), SendTarget.who("default", Optional.of(ANN), Optional.of(BOB)));
        assertEquals(Optional.of(BOB), SendTarget.who("default", Optional.empty(), Optional.of(BOB)));
        assertEquals(Optional.empty(), SendTarget.who("default", Optional.empty(), Optional.empty()));
    }

    @Test
    void watchKeptFromAnotherWorldGivesWayToTheChosenCitizen() {
        assertEquals(Optional.of(BOB), SendTarget.who("nether", Optional.of(ANN), Optional.of(BOB)));
        assertEquals(Optional.empty(), SendTarget.who("nether", Optional.of(ANN), Optional.empty()));
    }
}
