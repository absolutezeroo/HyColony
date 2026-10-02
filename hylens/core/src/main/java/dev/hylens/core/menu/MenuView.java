package dev.hylens.core.menu;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hylens.core.draw.Layers;
import java.util.List;
import java.util.Optional;

/**
 * What the HyLens menu shows (spec 2026-09-30, § 6.4): the world's colonies, the chosen one's citizens, the chosen
 * citizen, the layers, the world's colony clock with the step asked, whether the colonies are checked every few
 * seconds for the operator, the tab open (spec 2026-10-02, § 3.2), and the chosen colony's open requests (lot 3, § 6).
 * Built by {@link MenuViews}.
 */
public record MenuView(
        List<ColonyRow> colonies,
        Optional<ColonyRef> colony,
        List<CitizenRow> citizens,
        Optional<CitizenRef> citizen,
        Layers layers,
        boolean paused,
        int step,
        boolean autoCheck,
        MenuTab tab,
        List<RequestRow> requests) {
    /** Keeps its own copies of the lists. */
    public MenuView {
        colonies = List.copyOf(colonies);
        citizens = List.copyOf(citizens);
        requests = List.copyOf(requests);
    }

    /**
     * One open request of the chosen colony: its id, the item asked (empty for a tool or a pickup) and how many, its
     * kind, the citizen who asks if one does (by name) and the hut that asks, its state, what handles it, and whether
     * it is the chosen one. Its words are the api's (RequestSnapshot), shown as they are.
     */
    public record RequestRow(
            String id,
            Optional<String> item,
            int count,
            String kind,
            Optional<String> citizen,
            Optional<Pos> building,
            String state,
            Optional<String> resolver,
            boolean chosen) {}

    /**
     * One colony of the world: its name, number of citizens and confirmed alerts (those no citizen carries too, such as
     * a request without resolver), and whether it is the chosen one.
     */
    public record ColonyRow(ColonyRef ref, String name, int citizens, int alerts, boolean chosen) {}

    /**
     * One citizen of the chosen colony: its job's name, AI state and job step ("-" for none, or while its body is
     * unloaded), its confirmed alerts, whether it is chosen, and watched by the operator, and its saturation (empty
     * when HyColony reads none).
     */
    public record CitizenRow(
            CitizenRef ref,
            String name,
            ApiText job,
            String ai,
            String step,
            int alerts,
            boolean chosen,
            boolean watched,
            Optional<Saturation> saturation) {}

    /** A citizen's saturation, as HyColony read it, and its maximum (spec 2026-10-02 lot 2, § 5). */
    public record Saturation(double value, double max) {}
}
