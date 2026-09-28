package dev.hyvanilla.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hyvanilla.core.FlowerPotBlocks.Pot;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowerPotBlocksTest {
    private static final String RED = "Pot_Red";
    private static final String BLUE = "Pot_Blue";
    private static final String ROSE = "Plant_Rose";
    private static final String OAK = "Plant_Oak";
    private static final FlowerPotBlocks BLOCKS = new FlowerPotBlocks(Map.of(
            RED, Map.of(ROSE, "*Pot_Red_Rose", OAK, "*Pot_Red_Oak"),
            BLUE, Map.of(ROSE, "*Pot_Blue_Rose")));

    @Test
    void anEmptyPotBlockIsThatPotWithoutPlant() {
        assertEquals(Optional.of(new Pot(BLUE, Optional.empty())), BLOCKS.find(BLUE));
    }

    @Test
    void aPottedBlockIsItsOwnPotWithItsPlant() {
        assertEquals(Optional.of(new Pot(RED, Optional.of(OAK))), BLOCKS.find("*Pot_Red_Oak"));
        assertEquals(Optional.of(new Pot(BLUE, Optional.of(ROSE))), BLOCKS.find("*Pot_Blue_Rose"));
    }

    @Test
    void anyOtherBlockIsNoPot() {
        assertEquals(Optional.empty(), BLOCKS.find("Rock_Stone"));
    }

    @Test
    void plantingKeepsThePotsColour() {
        assertEquals(Optional.of("*Pot_Blue_Rose"), BLOCKS.block(BLUE, Optional.of(ROSE)));
        assertEquals(Optional.of("*Pot_Red_Rose"), BLOCKS.block(RED, Optional.of(ROSE)));
    }

    @Test
    void emptyingAPotGivesItsOwnEmptyBlock() {
        assertEquals(Optional.of(RED), BLOCKS.block(RED, Optional.empty()));
    }

    @Test
    void aPlantThatPotHasNoStateForHasNoBlock() {
        assertEquals(Optional.empty(), BLOCKS.block(BLUE, Optional.of(OAK)));
        assertEquals(Optional.empty(), BLOCKS.block("Pot_Green", Optional.empty()));
    }

    @Test
    void pottablePlantsAreThoseOfEveryPot() {
        assertEquals(Set.of(ROSE, OAK), BLOCKS.plants());
    }
}
