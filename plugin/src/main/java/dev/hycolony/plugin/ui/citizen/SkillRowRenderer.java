package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.colony.ui.CitizenView.SkillRow;
import dev.hycolony.plugin.IdMap;
import java.util.Locale;

/** One row of the citizen window's skill list: icon, "name - level", XP bar and "xp / needed XP". */
final class SkillRowRenderer {
    private static final String JOB_SKILL_COLOR = "#e2c27a";

    private final IdMap ids;

    SkillRowRenderer(IdMap ids) {
        this.ids = ids;
    }

    /** Appends {@code r} to {@code list} as its {@code index}-th row. */
    void append(UICommandBuilder ui, String list, int index, SkillRow r) {
        String row = list + "[" + index + "]";
        ui.append(list, "Pages/HyColony/SkillRow.ui");
        ui.set(row + " #Icon.ItemId", ids.skillIcon(r.skill()));
        ui.set(
                row + " #Name.TextSpans",
                Message.translation("hycolony.ui.citizen.skillLevel")
                        .param(
                                "p0",
                                Message.translation(
                                        "hycolony.ui.skill." + r.skill().name().toLowerCase(Locale.ROOT)))
                        .param("p1", String.valueOf(r.level())));
        ui.set(
                row + " #Xp.Text",
                r.maxed()
                        ? Message.translation("hycolony.ui.citizen.skillXpMax")
                        : Message.translation("hycolony.ui.citizen.skillXp")
                                .param("p0", String.valueOf(r.xp()))
                                .param("p1", String.valueOf(r.xpForNextLevel())));
        ui.set(row + " #XpBar.Value", r.progress());
        if (r.jobSkill()) {
            ui.set(row + " #Highlight.Visible", true);
            ui.set(row + " #Name.Style.TextColor", JOB_SKILL_COLOR);
        }
    }
}
