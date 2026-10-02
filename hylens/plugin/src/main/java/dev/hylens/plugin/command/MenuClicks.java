package dev.hylens.plugin.command;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuTab;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.menu.SaturationStep;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a click on the HyLens menu: which kind it is, and what it names (a colony or citizen id, a layer, a tab;
 * empty for one no longer shown).
 */
final class MenuClicks {
    /** The clicks that record a choice. */
    static final Set<String> CHOICES = Set.of("colony", "citizen", "layer", "stepLess", "stepMore", "tab", "request");
    /** The clicks on the watch: start it, free or follow its camera, stop it. */
    static final Set<String> WATCH = Set.of("watch", "free", "follow", "unwatch");
    /** The clicks that change a colony's citizens: a new one, the chosen one's saturation. */
    static final Set<String> EDITS = Set.of("spawn", "saturation", "fulfil", "resetRequests");
    /** The clicks on the colony clock. */
    static final Set<String> CLOCK = Set.of("pause", "step", "resume");
    /** The clicks on the checks. */
    static final Set<String> CHECKS = Set.of("checkNow", "autoCheck");

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

    /** The request whose id is {@code index}, if the page still lists it. */
    static Optional<String> request(MenuView v, String index) {
        return v.requests().stream()
                .map(MenuView.RequestRow::id)
                .filter(index::equals)
                .findFirst();
    }

    /** The saturation step named {@code name}, if it is one. */
    static Optional<SaturationStep> saturation(String name) {
        try {
            return Optional.of(SaturationStep.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The tab named {@code name}, if it is one. */
    static Optional<MenuTab> tab(String name) {
        try {
            return Optional.of(MenuTab.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
