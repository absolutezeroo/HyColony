package dev.hycolony.core.colony.ui;

import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.ui.RequestsView.RequestRow;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Deliverable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MineColonies WindowCitizen. {@code activity} is an i18n key suffix: "waitingFor" (with {@code waitingFor}, the
 * first open request), else "working", "wandering", "idle" or "absent". {@code workBuilding} is a building type id or
 * custom name. {@code jobActivity} is the job AI's own line (e.g. the builder's stage, block and action). {@code
 * requests} are the citizen's open requests, each with what the viewer holds of it.
 */
public record CitizenView(int colonyId, int citizenId, String name, Optional<String> jobId,
        Optional<String> workBuilding, String activity, Optional<Deliverable> waitingFor, Optional<Msg> jobActivity,
        Map<Skill, Integer> skills,
        List<ItemAmount> inventory, List<RequestRow> requests) {
    public CitizenView {
        skills = java.util.Collections.unmodifiableMap(new EnumMap<>(skills));
        inventory = List.copyOf(inventory);
        requests = List.copyOf(requests);
    }
}
