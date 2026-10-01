package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.citizen.HappinessRows;
import java.util.List;

/**
 * Happiness modifier rows (Mc/HappinessRow.ui): the mood icon with its tooltip, the modifier's name with its
 * description. Shared by the citizen's Happiness tab (MC updateHappiness) and the town hall's Citizens page (MC
 * fillHappinessList).
 */
public final class HappinessRowsUi {
    private HappinessRowsUi() {}

    /** The citizen's Happiness tab (MC updateHappiness): Mc/HappinessRow.ui, the icon telling its mood. */
    public static void citizen(UICommandBuilder ui, String container, List<HappinessRows.Row> rows) {
        render(ui, container, rows, "Pages/HyColony/Mc/HappinessRow.ui", true);
    }

    /** The town hall's happiness list (MC fillHappinessList): Mc/HappinessListRow.ui, no tooltip on the icon. */
    public static void townHall(UICommandBuilder ui, String container, List<HappinessRows.Row> rows) {
        render(ui, container, rows, "Pages/HyColony/Mc/HappinessListRow.ui", false);
    }

    /** Appends one {@code template} row per entry of {@code rows} into {@code container}. */
    private static void render(
            UICommandBuilder ui, String container, List<HappinessRows.Row> rows, String template, boolean mood) {
        for (int i = 0; i < rows.size(); i++) {
            HappinessRows.Row r = rows.get(i);
            String row = container + "[" + i + "]";
            ui.append(container, template);
            ui.set(row + " #" + moodId(r.mood()) + ".Visible", true);
            if (mood) {
                ui.set(
                        row + " #Icon.TooltipText",
                        Message.translation("hycolony.ui.happiness.mood." + moodKey(r.mood())));
            }
            ui.set(row + " #Name.Text", Message.translation("hycolony.ui.happiness." + r.id()));
            ui.set(row + " #Name.TooltipText", Message.translation("hycolony.ui.happiness.desc." + r.id()));
        }
    }

    /** The mood's layer in HappinessRow.ui. */
    private static String moodId(HappinessRows.Mood mood) {
        return switch (mood) {
            case POSITIVE -> "Positive";
            case NEUTRAL -> "Neutral";
            case SLIGHTLY_NEGATIVE -> "SlightlyNegative";
            case NEGATIVE -> "Negative";
        };
    }

    /** The mood's translation key tail (MC LABEL_HAPPINESS_*). */
    private static String moodKey(HappinessRows.Mood mood) {
        return switch (mood) {
            case POSITIVE -> "positive";
            case NEUTRAL -> "neutral";
            case SLIGHTLY_NEGATIVE -> "slightlyNegative";
            case NEGATIVE -> "negative";
        };
    }
}
