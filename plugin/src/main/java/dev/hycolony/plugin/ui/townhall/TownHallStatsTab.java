package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.ui.TownHallView.JobCount;
import dev.hycolony.core.app.ui.TownHallView.Stats;
import dev.hycolony.plugin.ui.ColonyPage;

/**
 * The town hall's Statistics tab (MC WindowStatsPage, layoutstats.xml): the citizen count, then one line per job
 * "job: workers/places", then the children and the unemployed, in MC's list order.
 */
final class TownHallStatsTab {
    private TownHallStatsTab() {}

    static void render(UICommandBuilder ui, Stats stats) {
        ui.set(
                "#TotalCitizens.Text",
                Message.translation("hycolony.ui.townhall.stats.citizens")
                        .param("p0", String.valueOf(stats.citizens())));
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

    private static void line(UICommandBuilder ui, int index, String property, Message text) {
        ui.append("#StatLines", "Pages/HyColony/StatLine.ui");
        ui.set("#StatLines[" + index + "] #Line." + property, text);
    }
}
