package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What an operator chose in the HyLens menu (spec 2026-09-30, § 6.4). */
class MenuStateTest {
    private static final ColonyRef A = new ColonyRef("default", 1);
    private static final ColonyRef B = new ColonyRef("default", 2);
    private static final CitizenRef ANN = new CitizenRef(A, 4);

    @Test
    void nothingIsChosenAtFirstAndEveryLayerShows() {
        assertEquals(new MenuState(Optional.empty(), Optional.empty(), Layers.ALL), MenuState.INITIAL);
    }

    @Test
    void choosingAnotherColonyForgetsTheCitizen() {
        MenuState s = MenuState.INITIAL.withColony(A).withCitizen(ANN);

        assertEquals(Optional.of(ANN), s.withColony(A).citizen(), "the same colony keeps it");
        assertEquals(Optional.empty(), s.withColony(B).citizen());
        assertEquals(Optional.of(B), s.withColony(B).colony());
    }

    @Test
    void choosingACitizenChoosesItsColony() {
        assertEquals(
                Optional.of(A), MenuState.INITIAL.withColony(B).withCitizen(ANN).colony());
    }

    @Test
    void togglingALayerKeepsTheRest() {
        MenuState s = MenuState.INITIAL.withColony(A).toggle(Layers.Layer.ZONE);

        assertEquals(Layers.ALL.toggle(Layers.Layer.ZONE), s.layers());
        assertEquals(Optional.of(A), s.colony());
    }
}
