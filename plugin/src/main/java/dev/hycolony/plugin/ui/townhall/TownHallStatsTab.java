package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ui.TownHallView.JobCount;
import dev.hycolony.core.app.ui.TownHallView.StatCount;
import dev.hycolony.core.app.ui.TownHallView.Stats;
import dev.hycolony.core.citizen.home.HousingCapacity;
import dev.hycolony.plugin.ui.ColonyPage;

/**
 * The town hall's Statistics tab (MC WindowStatsPage, layoutstats.xml): on the left page the citizen count over the
 * housing, coloured, then one line per job "job: workers/places", then the children and the unemployed, in MC's list
 * order; on the right page the colony's statistics within the chosen interval.
 */
final class TownHallStatsTab implements TownHallTab {
    private final Stats stats;
    /** MC WindowStatsPage.selectedInterval, "Since Yesterday" first. */
    private final IntervalChoice interval = new IntervalChoice(IntervalChoice.YESTERDAY);

    TownHallStatsTab(Stats stats) {
        this.stats = stats;
    }

    /** Keeps the interval {@code previous} showed (the core re-shows the window after each action). */
    void keepIntervalOf(TownHallStatsTab previous) {
        interval.keep(previous.interval);
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        populationPage(ui, root);
        interval.render(ui, events, root + " #Interval");
        // MC updateStats: one line per statistic, its count within the interval.
        for (int i = 0; i < stats.statistics().size(); i++) {
            StatCount s = stats.statistics().get(i);
            ui.append(root + " #Statistics", "Pages/HyColony/Mc/StatRow.ui");
            ui.set(
                    root + " #Statistics[" + i + "] #Desc.Text",
                    Message.translation("hycolony.ui.townhall.stats." + s.id())
                            .param("p0", String.valueOf(s.within(interval.days(), stats.day()))));
        }
    }

    /** The interval is page state (MC selectedInterval). */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        return interval.handle(act) ? Outcome.REDRAW : Outcome.NONE;
    }

    /** MC createAndSetStatistics: the population on the left page. */
    private void populationPage(UICommandBuilder ui, String root) {
        ui.set(
                root + " #TotalCitizens.Text",
                Message.translation("hycolony.ui.townhall.stats.citizens")
                        .param("p0", String.valueOf(stats.citizens()))
                        .param("p1", String.valueOf(stats.maxCitizens())));
        population(ui, root, stats.population());
        int i = 0;
        for (JobCount j : stats.jobs()) {
            // The job name is a nested translation: TextSpans, not Text.
            line(
                    ui,
                    root,
                    i++,
                    "TextSpans",
                    Message.translation("hycolony.ui.townhall.stats.job")
                            .param("p0", ColonyPage.jobName(j.jobId()))
                            .param("p1", String.valueOf(j.workers()))
                            .param("p2", String.valueOf(j.max())));
        }
        line(
                ui,
                root,
                i++,
                "Text",
                Message.translation("hycolony.ui.townhall.stats.children")
                        .param("p0", String.valueOf(stats.children())));
        line(
                ui,
                root,
                i,
                "Text",
                Message.translation("hycolony.ui.townhall.stats.unemployed")
                        .param("p0", String.valueOf(stats.unemployed())));
    }

    /**
     * MC WindowStatsPage: the count in dark green while housing and the cap leave room, orange with "Needs Housing"
     * past 90 % of the housing, red with "Reached Configured Limit" at the configured cap (BlockUI colour names).
     */
    private static void population(UICommandBuilder ui, String root, HousingCapacity.Population population) {
        String color = switch (population) {
            case OK -> "#006400";
            case NEEDS_HOUSING -> "#ffa500";
            case CONFIG_LIMITED -> "#ff0000";
        };
        ui.set(root + " #TotalCitizens.Style.TextColor", color);
        switch (population) {
            case NEEDS_HOUSING ->
                ui.set(
                        root + " #TotalCitizens.TooltipText",
                        Message.translation("hycolony.ui.townhall.stats.needsHousing"));
            case CONFIG_LIMITED ->
                ui.set(
                        root + " #TotalCitizens.TooltipText",
                        Message.translation("hycolony.ui.townhall.stats.configLimited"));
            case OK -> {}
        }
    }

    private static void line(UICommandBuilder ui, String root, int index, String property, Message text) {
        ui.append(root + " #StatLines", "Pages/HyColony/Mc/StatLine.ui");
        ui.set(root + " #StatLines[" + index + "] #Line." + property, text);
    }
}
