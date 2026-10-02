package dev.hylens.core.menu;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.RequestSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The chosen colony's open requests, as the menu lists them (spec 2026-10-02 lot 3, § 6). */
final class MenuRequests {
    /** The states before COMPLETED: what HyColony's core counts as open (RequestState.isBefore). */
    static final Set<String> OPEN =
            Set.of("CREATED", "REPORTED", "ASSIGNING", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "FOLLOWUP_IN_PROGRESS");

    private MenuRequests() {}

    /**
     * The open requests of {@code colony}, in HyColony's order, the one whose id is {@code chosen} marked; a citizen
     * who asks is named from {@code members}. None without a colony.
     */
    static List<MenuView.RequestRow> of(
            ColonyWorld world, Optional<ColonyRef> colony, List<CitizenSnapshot> members, Optional<String> chosen) {
        if (colony.isEmpty()) {
            return List.of();
        }
        Map<CitizenRef, String> names =
                members.stream().collect(Collectors.toMap(CitizenSnapshot::ref, CitizenSnapshot::name, (a, _) -> a));
        Function<RequestSnapshot, MenuView.RequestRow> row = r -> new MenuView.RequestRow(
                r.id(),
                r.item(),
                r.count(),
                r.kind(),
                r.citizen().map(names::get),
                r.building(),
                r.state(),
                r.resolver(),
                chosen.equals(Optional.of(r.id())));
        return world.requests(colony.get()).stream()
                .filter(r -> OPEN.contains(r.state()))
                .map(row)
                .toList();
    }
}
