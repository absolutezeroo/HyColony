package dev.hylens.core.menu;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hylens.core.draw.Layers;
import java.util.List;
import java.util.Optional;

/**
 * What the HyLens menu shows (spec 2026-09-30, § 6.4): the world's colonies, the chosen one's citizens, the chosen
 * citizen, the layers, the world's colony clock with the step asked, and whether the colonies are checked every few
 * seconds for the operator. Built by {@link MenuViews}.
 */
public record MenuView(
        List<ColonyRow> colonies,
        Optional<ColonyRef> colony,
        List<CitizenRow> citizens,
        Optional<CitizenRef> citizen,
        Layers layers,
        boolean paused,
        int step,
        boolean autoCheck) {
    /** Keeps its own copies of the lists. */
    public MenuView {
        colonies = List.copyOf(colonies);
        citizens = List.copyOf(citizens);
    }

    /**
     * One colony of the world: its name, number of citizens and confirmed alerts (those no citizen carries too, such as
     * a request without resolver), and whether it is the chosen one.
     */
    public record ColonyRow(ColonyRef ref, String name, int citizens, int alerts, boolean chosen) {}

    /**
     * One citizen of the chosen colony: its job's name, AI state and job step ("-" for none, or while its body is
     * unloaded), its confirmed alerts, and whether it is chosen, and watched by the operator.
     */
    public record CitizenRow(
            CitizenRef ref,
            String name,
            ApiText job,
            String ai,
            String step,
            int alerts,
            boolean chosen,
            boolean watched) {}
}
