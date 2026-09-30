package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(new MenuState(Optional.empty(), Optional.empty(), 1, Layers.ALL, false), MenuState.INITIAL);
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

    @Test
    void stepIsKeptBetweenOneAndTen() {
        assertEquals(1, MenuState.INITIAL.withStep(0).step());
        assertEquals(7, MenuState.INITIAL.withStep(7).step());
        assertEquals(MenuState.MAX_STEP, MenuState.INITIAL.withStep(99).step());
    }

    @Test
    void stepIsKeptAcrossOtherChoices() {
        MenuState s =
                MenuState.INITIAL.withStep(4).withColony(A).withCitizen(ANN).toggle(Layers.Layer.ZONE);

        assertEquals(4, s.step());
    }

    @Test
    void autoCheckIsOffAtFirstAndTogglesKeepingTheRest() {
        MenuState on = MenuState.INITIAL.withColony(A).withStep(3).toggleAutoCheck();

        assertTrue(on.autoCheck());
        assertEquals(Optional.of(A), on.colony());
        assertEquals(3, on.step());
        assertFalse(on.toggleAutoCheck().autoCheck());
        assertTrue(
                on.withColony(B)
                        .withCitizen(ANN)
                        .withStep(5)
                        .toggle(Layers.Layer.STOP)
                        .autoCheck(),
                "kept");
    }
}
