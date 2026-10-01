package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.ui.TownHallView.JobCount;
import dev.hycolony.core.app.ui.TownHallView.Stats;
import dev.hycolony.core.citizen.home.HousingCapacity;
import dev.hycolony.plugin.ui.ColonyPage;

/**
 * The town hall's Statistics tab (MC WindowStatsPage, layoutstats.xml): the citizen count over the housing, coloured,
 * then one line per job
 * "job: workers/places", then the children and the unemployed, in MC's list order.
 */
final class TownHallStatsTab {
    private TownHallStatsTab() {}

    static void render(UICommandBuilder ui, Stats stats) {
        ui.set(
                "#TotalCitizens.Text",
                Message.translation("hycolony.ui.townhall.stats.citizens")
                        .param("p0", String.valueOf(stats.citizens()))
                        .param("p1", String.valueOf(stats.maxCitizens())));
        population(ui, stats.population());
        int i = 0;
        for (JobCount j : stats.jobs()) {
            // The job name is a nested translation: TextSpans, not Text.
            line(
                    ui,
                    i++,
                    "TextSpans",
                    Message.translation("hycolony.ui.townhall.stats.job")
                            .param("p0", ColonyPage.jobName(j.jobId()))
                            .param("p1", String.valueOf(j.workers()))
                            .param("p2", String.valueOf(j.max())));
        }
        line(
                ui,
                i++,
                "Text",
                Message.translation("hycolony.ui.townhall.stats.children")
                        .param("p0", String.valueOf(stats.children())));
        line(
                ui,
                i,
                "Text",
                Message.translation("hycolony.ui.townhall.stats.unemployed")
                        .param("p0", String.valueOf(stats.unemployed())));
    }

    /**
     * MC WindowStatsPage: the count in dark green while housing and the cap leave room, orange with "Needs Housing"
     * past 90 % of the housing, red with "Reached Configured Limit" at the configured cap (BlockUI colour names).
     */
    private static void population(UICommandBuilder ui, HousingCapacity.Population population) {
        String color = switch (population) {
            case OK -> "#006400";
            case NEEDS_HOUSING -> "#ffa500";
            case CONFIG_LIMITED -> "#ff0000";
        };
        ui.set("#TotalCitizens.Style.TextColor", color);
        switch (population) {
            case NEEDS_HOUSING ->
                ui.set("#TotalCitizens.TooltipText", Message.translation("hycolony.ui.townhall.stats.needsHousing"));
            case CONFIG_LIMITED ->
                ui.set("#TotalCitizens.TooltipText", Message.translation("hycolony.ui.townhall.stats.configLimited"));
            case OK -> {}
        }
    }

    private static void line(UICommandBuilder ui, int index, String property, Message text) {
        ui.append("#StatLines", "Pages/HyColony/Mc/StatLine.ui");
        ui.set("#StatLines[" + index + "] #Line." + property, text);
    }
}
