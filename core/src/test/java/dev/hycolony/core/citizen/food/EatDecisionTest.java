package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.shouldEat. */
class EatDecisionTest {
    private final CitizenData citizen = new CitizenData(1);
    private final Random random = new Random(1);

    private boolean decide(boolean eating, boolean interruptible, boolean waiter, double health) {
        return EatDecision.shouldEat(
                citizen, new EatDecision.Situation(eating, interruptible, waiter, () -> health), random);
    }

    @Test
    void aFedCitizenDoesNotEat() {
        citizen.setSaturation(11);
        assertFalse(decide(false, true, false, 20));
        citizen.setSaturation(60);
        assertFalse(decide(true, true, false, 20));
    }

    @Test
    void itEatsAtTwoAndAHalf() {
        citizen.setSaturation(2.6);
        assertFalse(decide(false, true, false, 20));
        citizen.setSaturation(2.5);
        assertTrue(decide(false, true, false, 20));
    }

    @Test
    void aHurtCitizenEatsBelowSix() {
        citizen.setSaturation(5.9);
        assertFalse(decide(false, true, false, 6));
        assertTrue(decide(false, true, false, 5.9));
        citizen.setSaturation(6);
        assertFalse(decide(false, true, false, 1));
    }

    @Test
    void anEatingCitizenGoesOnUntilFullOrJustAte() {
        citizen.setSaturation(30);
        assertTrue(decide(true, true, false, 20));
        citizen.hunger().setJustAte(true);
        assertFalse(decide(true, true, false, 20));
    }

    @Test
    void aJobThatCannotBeInterruptedKeepsItFromEating() {
        citizen.setSaturation(0);
        assertFalse(decide(false, false, false, 20));
        assertFalse(decide(true, false, false, 20));
    }

    @Test
    void aWaiterRarelyGoesToEat() {
        citizen.setSaturation(0);
        int went = 0;
        for (int i = 0; i < 2000; i++) {
            went += decide(false, true, true, 20) ? 1 : 0;
        }
        assertTrue(went > 0 && went < 40, "went " + went);
    }
}
