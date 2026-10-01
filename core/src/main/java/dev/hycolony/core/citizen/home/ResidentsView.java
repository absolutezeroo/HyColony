package dev.hycolony.core.citizen.home;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.job.HiringMode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A residence's Residents tab (MC WindowHutLiving with WindowAssignCitizen): {@code assigned} of {@code max}, the
 * hiring mode, whether Assign and Unassign work ({@code manual}) and the viewer may use them ({@code canManage}), the
 * residents, then the other citizens in MC's order. Distances are in whole blocks; {@code far} means past MC's
 * FAR_DISTANCE_THRESHOLD.
 */
public record ResidentsView(
        int assigned,
        int max,
        HiringMode mode,
        boolean manual,
        boolean canManage,
        List<Resident> residents,
        List<Candidate> candidates)
        implements ModuleTab {
    public ResidentsView {
        residents = List.copyOf(residents);
        candidates = List.copyOf(candidates);
    }

    /** A resident: its job id, and how far its workplace is from this home. */
    public record Resident(int citizenId, String name, Optional<String> jobId, OptionalInt workDistance, boolean far) {}

    /** Another citizen: how far its workplace is from this home, and {@code closer} than from its current home. */
    public record Candidate(
            int citizenId, String name, Optional<String> jobId, OptionalInt workDistance, boolean closer, Home home) {}

    /** Where the candidate lives now: homeless, or its current home at {@code currentDistance} from its workplace. */
    public record Home(boolean homeless, OptionalInt currentDistance, boolean far) {}
}
