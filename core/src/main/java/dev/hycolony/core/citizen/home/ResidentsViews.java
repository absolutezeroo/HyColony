package dev.hycolony.core.citizen.home;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Builds a residence's Residents tab (MC WindowAssignCitizen.updateCitizens and its two lists' rows). */
final class ResidentsViews {
    /** MC WindowAssignCitizen.FAR_DISTANCE_THRESHOLD, in blocks. */
    static final int FAR_DISTANCE_THRESHOLD = 300;

    private ResidentsViews() {}

    /** The tab of {@code house} as {@code viewer} sees it; reads only. */
    static ResidentsView of(Colony c, Building house, LivingModule living, UUID viewer) {
        return new ResidentsView(
                living.residents().size(),
                living.max(house),
                living.hiringMode(),
                living.manual(c),
                ColonyAccess.allows(c, viewer, Action.MANAGE_HUTS),
                residents(c, house, living),
                candidates(c, house));
    }

    private static List<ResidentsView.Resident> residents(Colony c, Building house, LivingModule living) {
        return living.residents().stream()
                .flatMap(id -> c.citizens().get(id).stream())
                .map(d -> {
                    OptionalInt work = distance(d.workBuilding(), house.position());
                    return new ResidentsView.Resident(
                            d.id(), d.name(), jobId(d), work, work.orElse(0) > FAR_DISTANCE_THRESHOLD);
                })
                .toList();
    }

    /**
     * MC updateCitizens: everyone but this home's residents and those living at their workplace; the homeless first,
     * then by workplace distance to this home (without a workplace: first if homeless, last if housed). Stable.
     */
    private static List<ResidentsView.Candidate> candidates(Colony c, Building house) {
        BlockPos here = house.position();
        return c.citizens().all().stream()
                .filter(d -> (!Objects.equals(d.homeBuilding(), d.workBuilding()) || d.homeBuilding() == null)
                        && !here.equals(d.homeBuilding()))
                .sorted(Comparator.comparingInt((CitizenData d) -> d.homeBuilding() == null ? 0 : 1)
                        .thenComparingLong(d -> sortValue(d, here)))
                .map(d -> candidate(d, here))
                .toList();
    }

    private static long sortValue(CitizenData d, BlockPos here) {
        if (d.workBuilding() == null) {
            return d.homeBuilding() == null ? 0 : Integer.MAX_VALUE;
        }
        return distance(d.workBuilding(), here).orElse(0);
    }

    /** MC's unassigned row: "Works N blocks from here", green when closer than now, then the current home's line. */
    private static ResidentsView.Candidate candidate(CitizenData d, BlockPos here) {
        OptionalInt work = distance(d.workBuilding(), here);
        OptionalInt current =
                d.homeBuilding() == null ? OptionalInt.empty() : distance(d.workBuilding(), d.homeBuilding());
        boolean closer = work.isPresent() && current.isPresent() && work.getAsInt() < current.getAsInt();
        ResidentsView.Home home =
                new ResidentsView.Home(d.homeBuilding() == null, current, current.orElse(0) > FAR_DISTANCE_THRESHOLD);
        return new ResidentsView.Candidate(d.id(), d.name(), jobId(d), work, closer, home);
    }

    private static Optional<String> jobId(CitizenData d) {
        return d.job().map(j -> j.type().id());
    }

    /** MC BlockPosUtil.getDistance truncated to int: empty without a workplace. */
    private static OptionalInt distance(@Nullable BlockPos a, BlockPos b) {
        return a == null ? OptionalInt.empty() : OptionalInt.of((int) Math.sqrt((double) a.distSq(b)));
    }
}
