package dev.hycolony.core.app.hut;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Builds a hut's {@link HireView} (MC WindowHireWorker.updateCitizens and updateElement) for its assignment module:
 * the worker module of a workplace, or the warehouse's courier module (MC CourierAssignmentModuleView).
 */
public final class HireViews {
    /**
     * What the window needs of an assignment module (MC IAssignmentModuleView): its job, mode, citizens, whether it is
     * full, its job's skills, which citizens it may take (canAssign) and whether "Show employed?" is enabled (MC
     * setupShowEmployed: worker modules only).
     */
    private record Assignment(
            String jobId,
            HiringMode mode,
            List<Integer> assigned,
            boolean full,
            Optional<Skill> primary,
            Optional<Skill> secondary,
            Predicate<CitizenData> canAssign,
            boolean showEmployed) {}

    private HireViews() {}

    /** The hire window of {@code b}: its worker module's, else its courier module's; empty if it has neither. */
    public static Optional<HireView> of(Colony c, Building b) {
        return b.module(WorkerModule.class)
                .map(w -> workers(b, w))
                .or(() -> b.module(CourierAssignmentModule.class).map(m -> couriers(c, b, m)))
                .map(m -> of(c, b, m));
    }

    /**
     * MC WorkerBuildingModuleView: full when it may not assign yet or has all its workers (isFull); takes adults
     * without a workplace or working here (canAssign). MC also lets a hut whose job another workplace can be hired as
     * (the library's students) take that workplace's workers; HyColony has no such workplace yet.
     */
    private static Assignment workers(Building b, WorkerModule w) {
        return new Assignment(
                w.job().id(),
                w.hiringMode(),
                w.workers(),
                !w.canAssignCitizens(b) || w.workers().size() >= w.maxWorkers(),
                Optional.of(w.primary()),
                Optional.of(w.secondary()),
                d -> d.workBuilding() == null || w.workers().contains(d.id()),
                true);
    }

    /**
     * MC CourierAssignmentModuleView: level × 2 couriers; takes an adult courier attached to no other warehouse; no
     * primary nor secondary skill.
     */
    private static Assignment couriers(Colony c, Building b, CourierAssignmentModule m) {
        return new Assignment(
                CourierAssignmentModule.COURIER_JOB_ID,
                m.hiringMode(),
                m.couriers(),
                m.couriers().size() >= CourierAssignmentModule.maxCouriers(b),
                Optional.empty(),
                Optional.empty(),
                d -> CourierAssignmentModule.isCourier(d)
                        && CourierAssignmentModule.warehouseOf(c, d.id())
                                .map(w -> w.position().equals(b.position()))
                                .orElse(true),
                false);
    }

    private static HireView of(Colony c, Building b, Assignment m) {
        BlockPos hut = b.position();
        List<HireView.Candidate> all = c.citizens().all().stream()
                .filter(d -> !d.isChild())
                .sorted(Comparator.comparingInt((CitizenData d) -> priority(d, hut, m))
                        .thenComparingDouble(d -> homeBucket(d, hut))
                        .thenComparing(CitizenData::name))
                .map(d -> candidate(d, hut, m))
                .toList();
        return new HireView(
                m.jobId(), m.mode(), m.assigned().size(), m.full(), m.primary(), m.secondary(), m.showEmployed(), all);
    }

    /** MC getCitizenPriority: working here, then unemployed, then other jobs, then those the module may not take. */
    private static int priority(CitizenData d, BlockPos hut, Assignment m) {
        if (hut.equals(d.workBuilding())) {
            return 0;
        }
        if (!m.canAssign().test(d)) {
            return 3;
        }
        return d.workBuilding() == null ? 1 : 2;
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

    private static HireView.Candidate candidate(CitizenData d, BlockPos hut, Assignment m) {
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
                m.assigned().contains(d.id()),
                m.canAssign().test(d),
                line,
                distance,
                skills(d, m));
    }

    /** MC: every skill once, the job's primary first, then its secondary, then the others in their order. */
    private static List<CitizenRow.SkillLevel> skills(CitizenData d, Assignment m) {
        List<Skill> order = new ArrayList<>();
        m.primary().ifPresent(order::add);
        m.secondary().filter(s -> !order.contains(s)).ifPresent(order::add);
        for (Skill s : Skill.values()) {
            if (!order.contains(s)) {
                order.add(s);
            }
        }
        return order.stream()
                .map(s -> new CitizenRow.SkillLevel(s, d.skills().level(s)))
                .toList();
    }
}
