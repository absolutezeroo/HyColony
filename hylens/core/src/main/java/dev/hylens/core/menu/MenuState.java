package dev.hylens.core.menu;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import java.util.Optional;

/**
 * What an operator chose in the HyLens menu (spec 2026-09-30, § 6.4): a colony, one of its citizens, the core ticks a
 * clock step runs, the drawings shown, whether HyLens checks the colonies for them every few seconds, and the tab open
 * (spec 2026-10-02, § 3.2). Kept while HyLens runs, never saved.
 */
public record MenuState(
        Optional<ColonyRef> colony,
        Optional<CitizenRef> citizen,
        int step,
        Layers layers,
        boolean autoCheck,
        MenuTab tab) {
    /** Core ticks a step may ask at most: HyColony keeps no more pending (ColonyClock.step). */
    static final int MAX_STEP = 10;

    /** Nothing chosen, a step of one tick, every layer shown, no automatic check, the Colonies tab open. */
    static final MenuState INITIAL =
            new MenuState(Optional.empty(), Optional.empty(), 1, Layers.ALL, false, MenuTab.COLONIES);

    /** {@code chosen} as the colony, its citizens' tab open; the citizen chosen is kept only if it belongs to it. */
    public MenuState withColony(ColonyRef chosen) {
        Optional<CitizenRef> kept = citizen.filter(c -> c.colony().equals(chosen));
        return new MenuState(Optional.of(chosen), kept, step, layers, autoCheck, MenuTab.CITIZENS);
    }

    /** {@code chosen} and its colony chosen. */
    public MenuState withCitizen(CitizenRef chosen) {
        return new MenuState(Optional.of(chosen.colony()), Optional.of(chosen), step, layers, autoCheck, tab);
    }

    /** A step of {@code ticks}, kept between 1 and {@link #MAX_STEP}. */
    public MenuState withStep(int ticks) {
        return new MenuState(colony, citizen, Math.clamp(ticks, 1, MAX_STEP), layers, autoCheck, tab);
    }

    /** {@code layer} turned on if it was off, off if it was on. */
    public MenuState toggle(Layers.Layer layer) {
        return new MenuState(colony, citizen, step, layers.toggle(layer), autoCheck, tab);
    }

    /** The automatic check turned on if it was off, off if it was on. */
    public MenuState toggleAutoCheck() {
        return new MenuState(colony, citizen, step, layers, !autoCheck, tab);
    }

    /** {@code open} as the tab shown. */
    public MenuState withTab(MenuTab open) {
        return new MenuState(colony, citizen, step, layers, autoCheck, open);
    }
}
