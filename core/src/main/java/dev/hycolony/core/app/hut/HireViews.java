package dev.hycolony.core.app.hut;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds a worker hut's {@link HireView} (MC WindowHireWorker.updateCitizens and updateElement). */
public final class HireViews {
    private HireViews() {}

    /** The hire window of {@code b}, whose worker module is {@code w}. */
    public static HireView of(Colony c, Building b, WorkerModule w) {
        BlockPos hut = b.position();
        List<HireView.Candidate> all = c.citizens().all().stream()
                .filter(d -> !d.isChild())
                .sorted(Comparator.comparingInt((CitizenData d) -> priority(d, hut, w))
                        .thenComparingDouble(d -> homeBucket(d, hut))
                        .thenComparing(CitizenData::name))
                .map(d -> candidate(d, hut, w))
                .toList();
        return new HireView(
                w.job().id(),
                w.hiringMode(),
                w.workers().size(),
                w.workers().size() >= w.maxWorkers(),
                w.primary(),
                w.secondary(),
                all);
    }

    /** MC getCitizenPriority: employed here, then unemployed, then other jobs, then those the hut may not take. */
    private static int priority(CitizenData d, BlockPos hut, WorkerModule w) {
        if (hut.equals(d.workBuilding())) {
            return 0;
        }
        if (!assignable(d, w)) {
            return 3;
        }
        return d.workBuilding() == null ? 1 : 2;
    }

    /**
     * MC WorkerBuildingModuleView.canAssign for an adult: no workplace, or a worker of this hut. MC also lets a hut
     * whose job another workplace can be hired as (the library's students) take that workplace's workers; HyColony has
     * no such workplace yet.
     */
    private static boolean assignable(CitizenData d, WorkerModule w) {
        return d.workBuilding() == null || w.workers().contains(d.id());
    }

    /** MC: the home's distance to {@code hut} rounded to the nearest 40 blocks; 100 when homeless. */
    private static double homeBucket(CitizenData d, BlockPos hut) {
        BlockPos home = d.homeBuilding();
        if (home == null) {
            return 100.0;
        }
        double distance = Math.sqrt((double) home.distSq(hut));
        double rest = distance % 40;
        return rest > 20 ? distance - rest + 40 : distance - rest;
    }

    private static HireView.Candidate candidate(CitizenData d, BlockPos hut, WorkerModule w) {
        BlockPos home = d.homeBuilding();
        HireView.HomeLine line;
        int distance = 0;
        if (home == null) {
            line = HireView.HomeLine.HOMELESS;
        } else if (home.equals(hut)) {
            line = HireView.HomeLine.LIVES_HERE;
        } else if (home.equals(d.workBuilding())) {
            line = HireView.HomeLine.LIVES_AT_WORK;
        } else {
            line = HireView.HomeLine.DISTANCE;
            distance = (int) Math.sqrt((double) home.distSq(hut));
        }
        return new HireView.Candidate(
                d.id(),
                d.name(),
                d.job().map(j -> j.type().id()),
                hut.equals(d.workBuilding()),
                assignable(d, w),
                line,
                distance,
                skills(d, w));
    }

    /** MC: every skill, the job's primary first, then its secondary, then the others in their order. */
    private static List<CitizenRow.SkillLevel> skills(CitizenData d, WorkerModule w) {
        List<CitizenRow.SkillLevel> skills = new ArrayList<>();
        skills.add(new CitizenRow.SkillLevel(w.primary(), d.skills().level(w.primary())));
        skills.add(new CitizenRow.SkillLevel(w.secondary(), d.skills().level(w.secondary())));
        for (Skill s : Skill.values()) {
            if (s != w.primary() && s != w.secondary()) {
                skills.add(new CitizenRow.SkillLevel(s, d.skills().level(s)));
            }
        }
        return skills;
    }

    /** The view's {@link HireView} if {@code b} employs workers; empty otherwise. */
    public static Optional<HireView> of(Colony c, Building b) {
        return b.module(WorkerModule.class).map(w -> of(c, b, w));
    }
}
