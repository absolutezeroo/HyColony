package dev.hylens.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Each operator's menu choices, kept while HyLens runs (spec 2026-09-30, § 6.4). */
class MenusTest {
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final ColonyRef A = new ColonyRef("default", 1);

    private final Menus menus = new Menus();

    @Test
    void operatorWhoChoseNothingHasTheInitialState() {
        assertEquals(MenuState.INITIAL, menus.state(OPERATOR));
    }

    @Test
    void choicesAreKeptPerOperator() {
        MenuState chosen = menus.update(OPERATOR, s -> s.withColony(A).toggle(Layers.Layer.ZONE));

        assertEquals(chosen, menus.state(OPERATOR));
        assertEquals(MenuState.INITIAL, menus.state(UUID.randomUUID()));
    }

    @Test
    void forgottenOperatorStartsAfresh() {
        menus.update(OPERATOR, s -> s.withColony(A));

        menus.forget(OPERATOR);

        assertEquals(MenuState.INITIAL, menus.state(OPERATOR));
    }

    @Test
    void choicesAddUp() {
        menus.update(OPERATOR, s -> s.withColony(A));

        MenuState both = menus.update(OPERATOR, s -> s.toggle(Layers.Layer.ZONE));

        assertEquals(new MenuState(Optional.of(A), Optional.empty(), Layers.ALL.toggle(Layers.Layer.ZONE)), both);
    }
}
