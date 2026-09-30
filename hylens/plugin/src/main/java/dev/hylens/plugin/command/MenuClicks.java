package dev.hylens.plugin.command;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuView;
import java.util.Optional;

/** Reads what a click on the HyLens menu names: a colony or citizen id, a layer; empty for one no longer shown. */
final class MenuClicks {
    private MenuClicks() {}

    /** The colony whose id is {@code index}, if the page still lists it. */
    static Optional<ColonyRef> colony(MenuView v, String index) {
        return id(index)
                .flatMap(id -> v.colonies().stream()
                        .map(MenuView.ColonyRow::ref)
                        .filter(c -> c.colonyId() == id)
                        .findFirst());
    }

    /** The citizen of the chosen colony whose id is {@code index}, if the page still lists it. */
    static Optional<CitizenRef> citizen(MenuView v, String index) {
        return id(index)
                .flatMap(id -> v.citizens().stream()
                        .map(MenuView.CitizenRow::ref)
                        .filter(c -> c.citizenId() == id)
                        .findFirst());
    }

    private static Optional<Integer> id(String index) {
        try {
            return Optional.of(Integer.parseInt(index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<Layers.Layer> layer(String name) {
        try {
            return Optional.of(Layers.Layer.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
