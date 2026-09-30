package dev.hylens.core.menu;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.JobNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the HyLens menu's view from HyColony's api, on the world's thread (spec 2026-09-30, § 6.4). */
public final class MenuViews {
    private static final String NONE = "-";

    private MenuViews() {}

    /**
     * The menu of {@code world}, its colonies {@code paused} or not, for an operator who chose {@code s} and watches
     * {@code watched}. A chosen colony or citizen that no longer exists is shown as not chosen. Asks HyColony for the
     * colonies' alerts, which confirms a lasting one across calls.
     */
    public static MenuView of(ColonyWorld world, boolean paused, MenuState s, Optional<CitizenRef> watched) {
        Optional<ColonyRef> colony = s.colony().filter(c -> world.colony(c).isPresent());
        Map<ColonyRef, List<Violation>> checked = new HashMap<>();
        List<MenuView.ColonyRow> colonies = world.colonies().stream()
                .map(c -> new MenuView.ColonyRow(
                        c.ref(),
                        c.name(),
                        c.citizens(),
                        checked.computeIfAbsent(c.ref(), world.debug()::check).size(),
                        colony.map(c.ref()::equals).orElse(false)))
                .toList();
        List<CitizenSnapshot> members = colony.map(world::citizens).orElse(List.of());
        Optional<CitizenRef> citizen =
                s.citizen().filter(c -> members.stream().anyMatch(m -> m.ref().equals(c)));
        Map<CitizenRef, Long> alerts = colony.map(checked::get).orElse(List.of()).stream()
                .map(Violation::citizen)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<MenuView.CitizenRow> citizens = members.stream()
                .map(m -> row(world, m, alerts.getOrDefault(m.ref(), 0L).intValue(), citizen, watched))
                .toList();
        return new MenuView(colonies, colony, citizens, citizen, s.layers(), paused, s.step(), s.autoCheck());
    }

    private static MenuView.CitizenRow row(
            ColonyWorld world,
            CitizenSnapshot m,
            int alerts,
            Optional<CitizenRef> chosen,
            Optional<CitizenRef> watched) {
        Optional<CitizenDebugSnapshot> d = world.debug().inspect(m.ref());
        return new MenuView.CitizenRow(
                m.ref(),
                m.name(),
                JobNames.of(m.job()),
                d.map(CitizenDebugSnapshot::aiState).filter(a -> !a.isEmpty()).orElse(NONE),
                d.map(CitizenDebugSnapshot::jobStep).filter(j -> !j.isEmpty()).orElse(NONE),
                alerts,
                chosen.map(m.ref()::equals).orElse(false),
                watched.map(m.ref()::equals).orElse(false));
    }
}
