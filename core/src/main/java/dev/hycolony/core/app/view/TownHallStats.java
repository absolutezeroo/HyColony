package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.TownHallView.JobCount;
import dev.hycolony.core.app.ui.TownHallView.StatCount;
import dev.hycolony.core.app.ui.TownHallView.Stats;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.HousingCapacity;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.stats.ColonyStatistics;
import dev.hycolony.core.job.WorkerModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The town hall Statistics tab (MC WindowStatsPage.createAndSetStatistics and updateStats): the population, then the
 * colony's statistics ({@link ColonyStatistics}, which counts only what HyColony records).
 */
final class TownHallStats {
    private TownHallStats() {}

    /** Workers over places per job, summed over every hut, then children and unemployed adults. */
    static Stats of(Colony c) {
        Map<String, int[]> perJob = new TreeMap<>();
        for (Building b : c.buildings().all()) {
            b.module(WorkerModule.class).ifPresent(w -> {
                int[] count = perJob.computeIfAbsent(w.job().id(), _ -> new int[2]);
                count[0] += w.workers().size();
                count[1] += w.maxWorkers();
            });
        }
        List<JobCount> jobs = new ArrayList<>(perJob.size());
        perJob.forEach((job, count) -> jobs.add(new JobCount(job, count[0], count[1])));
        int children = 0;
        int unemployed = 0;
        for (CitizenData d : c.citizens().all()) {
            if (d.isChild()) {
                children++;
            } else if (d.job().isEmpty()) {
                unemployed++;
            }
        }
        HousingCapacity housing = HousingCapacity.of(c);
        return new Stats(
                c.citizens().all().size(),
                housing.shownMax(),
                housing.population(),
                jobs,
                children,
                unemployed,
                c.day(),
                statistics(c.registries().statistics()));
    }

    /** MC updateStats: every statistic counted, first counted first. */
    private static List<StatCount> statistics(ColonyStatistics stats) {
        List<StatCount> out = new ArrayList<>();
        for (String id : stats.types()) {
            out.add(new StatCount(id, stats.perDay(id)));
        }
        return out;
    }
}
