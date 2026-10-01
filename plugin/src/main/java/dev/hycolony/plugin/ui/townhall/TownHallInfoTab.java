package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;

/**
 * The town hall's Information tab (MC WindowInfoPage): on the left page the colony's events within the chosen
 * interval (page state, "All Time" first shown, as MC), on the right page the work orders.
 */
final class TownHallInfoTab implements TownHallTab {
    /** MC WindowStatsPage.INTERVAL, in days; -1 for all time. */
    private static final List<Integer> INTERVALS = List.of(1, 7, 100, -1);

    private static final List<String> INTERVAL_KEYS = List.of("yesterday", "lastweek", "100days", "alltime");

    private final TownHallView.Info info;
    private final WorkOrderListTab orders;
    private int interval = -1;

    TownHallInfoTab(TownHallView.Info info, WorkOrderListTab orders) {
        this.info = info;
        this.orders = orders;
    }

    /** Keeps the interval {@code previous} showed (the core re-shows the window after each action). */
    void keepIntervalOf(TownHallInfoTab previous) {
        interval = previous.interval;
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        intervalDropdown(ui, events, root + " #Interval");
        List<TownHallView.EventRow> shown = info.within(interval);
        for (int i = 0; i < shown.size(); i++) {
            row(ui, root + " #Events", i, shown.get(i));
        }
        orders.render(ui, events, root);
    }

    private void intervalDropdown(UICommandBuilder ui, UIEventBuilder events, String dropdown) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (int i = 0; i < INTERVALS.size(); i++) {
            entries.add(new DropdownEntryInfo(
                    LocalizableString.fromMessageId("hycolony.ui.interval." + INTERVAL_KEYS.get(i)),
                    String.valueOf(INTERVALS.get(i))));
        }
        ui.set(dropdown + ".Entries", entries);
        ui.set(dropdown + ".Value", String.valueOf(interval));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                dropdown,
                EventData.of("Action", "interval").append("@Name", dropdown + ".Value"),
                false);
    }

    /** MC fillEventsList: the action, then the citizen's name or the hut and its level, then x y z. */
    private static void row(UICommandBuilder ui, String list, int i, TownHallView.EventRow e) {
        String row = list + "[" + i + "]";
        ui.append(list, "Pages/HyColony/Mc/EventRow.ui");
        ui.set(row + " #Action.Text", Message.translation("hycolony.ui.townhall.event." + e.type()));
        if (e.type().equals("citizenSpawned")) {
            ui.set(row + " #Name.Text", e.params().isEmpty() ? "" : e.params().getFirst());
        } else if (e.params().size() >= 2) {
            // The hut's name is a nested translation: TextSpans, not Text; MC repeats it as the tooltip.
            Message name = Message.join(
                    ColonyPage.buildingName(e.params().get(0)),
                    Message.raw(" " + e.params().get(1)));
            ui.set(row + " #Name.TextSpans", name);
            ui.set(row + " #Name.TooltipTextSpans", name);
        }
        ui.set(
                row + " #Pos.Text",
                e.pos().map(p -> p.x() + " " + p.y() + " " + p.z()).orElse(""));
    }

    /** The interval is page state (MC selectedInterval); the orders' buttons go to the core. */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        if ("interval".equals(act.action())) {
            try {
                int days = Integer.parseInt(act.name());
                if (INTERVALS.contains(days)) {
                    interval = days;
                    return Outcome.REDRAW;
                }
            } catch (NumberFormatException _) {
                // A forged value: ignored.
            }
            return Outcome.NONE;
        }
        orders.handle(act);
        return Outcome.NONE;
    }
}
