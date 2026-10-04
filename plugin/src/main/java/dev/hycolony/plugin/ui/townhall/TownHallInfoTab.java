package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/**
 * The town hall's Information tab (MC WindowInfoPage): on the left page the colony's events within the chosen
 * interval (page state, "All Time" first shown, as MC), on the right page the work orders.
 */
final class TownHallInfoTab implements TownHallTab {
    private final TownHallView.Info info;
    private final WorkOrderListTab orders;
    private final IntervalChoice interval = new IntervalChoice(IntervalChoice.ALL_TIME);

    TownHallInfoTab(TownHallView.Info info, WorkOrderListTab orders) {
        this.info = info;
        this.orders = orders;
    }

    /** Keeps the interval {@code previous} showed (the core re-shows the window after each action). */
    void keepIntervalOf(TownHallInfoTab previous) {
        interval.keep(previous.interval);
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        interval.render(ui, events, root + " #Interval");
        List<TownHallView.EventRow> shown = info.within(interval.days());
        for (int i = 0; i < shown.size(); i++) {
            row(ui, root + " #Events", i, shown.get(i));
        }
        orders.render(ui, events, root);
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
            return interval.handle(act) ? Outcome.REDRAW : Outcome.NONE;
        }
        orders.handle(act);
        return Outcome.NONE;
    }
}
