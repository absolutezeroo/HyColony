package dev.hycolony.core.decoration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.decoration.FlowerPot.GiveBack;
import dev.hycolony.core.decoration.FlowerPot.Plant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowerPotTest {
    private static final String ROSE = "Plant_Flower_Common_Red";
    private static final String OAK = "Plant_Sapling_Oak";
    private static final FlowerPot POT = new FlowerPot(Set.of(ROSE, OAK));

    @Test
    void emptyPotTakesTheHeldPlantAndConsumesOne() {
        assertEquals(new Plant(ROSE, true), POT.use(Optional.empty(), Optional.of(ROSE), false));
    }

    @Test
    void creativePlayerKeepsThePlantHePots() {
        assertEquals(new Plant(OAK, false), POT.use(Optional.empty(), Optional.of(OAK), true));
    }

    @Test
    void emptyPotIgnoresAnItemThatIsNotPottable() {
        assertEquals(FlowerPot.NOTHING, POT.use(Optional.empty(), Optional.of("Rock_Stone"), false));
    }

    @Test
    void emptyPotUsedWithAnEmptyHandDoesNothing() {
        assertEquals(FlowerPot.NOTHING, POT.use(Optional.empty(), Optional.empty(), false));
    }

    @Test
    void filledPotGivesItsPlantBackToAnEmptyHand() {
        assertEquals(new GiveBack(ROSE), POT.use(Optional.of(ROSE), Optional.empty(), false));
    }

    @Test
    void filledPotGivesItsPlantBackWhenTheHeldItemIsNotPottable() {
        assertEquals(new GiveBack(ROSE), POT.use(Optional.of(ROSE), Optional.of("Rock_Stone"), false));
    }

    @Test
    void filledPotGivesItsPlantBackToACreativePlayerToo() {
        assertEquals(new GiveBack(OAK), POT.use(Optional.of(OAK), Optional.empty(), true));
    }

    @Test
    void filledPotUsedWithAPottablePlantDoesNothing() {
        assertEquals(FlowerPot.NOTHING, POT.use(Optional.of(ROSE), Optional.of(OAK), false));
        assertEquals(FlowerPot.NOTHING, POT.use(Optional.of(ROSE), Optional.of(ROSE), false));
    }
}
