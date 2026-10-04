package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;

/** The interval a town hall tab shows (MC WindowStatsPage.INTERVAL and its dropdown): page state, in days. */
final class IntervalChoice {
    /** MC WindowStatsPage.INTERVAL, in days; -1 for all time. */
    static final int ALL_TIME = -1;

    static final int YESTERDAY = 1;

    private static final List<Integer> INTERVALS = List.of(YESTERDAY, 7, 100, ALL_TIME);
    private static final List<String> KEYS = List.of("yesterday", "lastweek", "100days", "alltime");

    private int days;

    /** Starts on {@code days}, one of MC's intervals. */
    IntervalChoice(int days) {
        this.days = days;
    }

    /** The chosen interval in days; {@link #ALL_TIME} for all time. */
    int days() {
        return days;
    }

    /** Keeps the interval {@code previous} showed (the core re-shows the window after each action). */
    void keep(IntervalChoice previous) {
        days = previous.days;
    }

    /** Fills the dropdown at {@code dropdown} with MC's intervals, the chosen one shown; a change sends "interval". */
    void render(UICommandBuilder ui, UIEventBuilder events, String dropdown) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (int i = 0; i < INTERVALS.size(); i++) {
            entries.add(new DropdownEntryInfo(
                    LocalizableString.fromMessageId("hycolony.ui.interval." + KEYS.get(i)),
                    String.valueOf(INTERVALS.get(i))));
        }
        ui.set(dropdown + ".Entries", entries);
        ui.set(dropdown + ".Value", String.valueOf(days));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                dropdown,
                EventData.of("Action", "interval").append("@Name", dropdown + ".Value"),
                false);
    }

    /** Whether {@code act} chose an interval, now kept; a forged value is ignored. */
    boolean handle(ColonyPage.Act act) {
        if (!"interval".equals(act.action())) {
            return false;
        }
        try {
            int chosen = Integer.parseInt(act.name());
            if (INTERVALS.contains(chosen)) {
                days = chosen;
                return true;
            }
        } catch (NumberFormatException _) {
            // A forged value: ignored.
        }
        return false;
    }
}
