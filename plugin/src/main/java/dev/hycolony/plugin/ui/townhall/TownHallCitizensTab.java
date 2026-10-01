package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.api.read.JobNames;
import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/**
 * The town hall's Citizens tab (MC WindowCitizenPage): the citizens by name, filtered by the search field on their
 * name or job, one selected (its job, gender seal and recall button); the filter and the selection are page state.
 */
final class TownHallCitizensTab implements TownHallTab {
    private final List<CitizenRow> rows;
    /** A language key to the viewer's text, for the job filter (MC compares the job's shown name). */
    private final UnaryOperator<String> text;
    /** Recalls the citizen of this id (the core checks the right). */
    private final IntConsumer recall;

    private String filter = "";
    /** The selected citizen's id: the first by name on opening (MC WindowCitizenPage); empty without citizens. */
    private Optional<Integer> selected;

    TownHallCitizensTab(List<CitizenRow> rows, UnaryOperator<String> text, IntConsumer recall) {
        this.rows = rows;
        this.text = text;
        this.recall = recall;
        this.selected = rows.stream().findFirst().map(CitizenRow::id);
    }

    /** Keeps the search and the selection {@code previous} showed (the core re-shows the window after actions). */
    void keepStateOf(TownHallCitizensTab previous) {
        filter = previous.filter;
        selected = previous.selected;
    }

    /** The citizen whose details show; empty when the selected one is gone. */
    Optional<CitizenRow> selection() {
        return selected.flatMap(id -> rows.stream().filter(r -> r.id() == id).findFirst());
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Search.Value", filter);
        // As the game's own search lists (CommandListPage): every keystroke sends the field's value.
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                root + " #Search",
                EventData.of("Action", "citizenSearch").append("@Name", root + " #Search.Value"),
                false);
        ColonyPage.bind(events, root + " #RecallButton", "recallOne");
        refresh(ui, events, root);
    }

    /** The list (MC updateCitizens) and the selected citizen's details (MC updateCitizen). */
    @Override
    public void refresh(UICommandBuilder ui, UIEventBuilder events, String root) {
        Optional<CitizenRow> sel = selection();
        String list = root + " #CitizenList";
        ui.clear(list);
        List<CitizenRow> shown = shown();
        for (int i = 0; i < shown.size(); i++) {
            CitizenRow r = shown.get(i);
            String button = list + "[" + i + "] #Name";
            ui.append(list, "Pages/HyColony/Mc/CitizenRow.ui");
            ui.set(button + ".Text", r.name());
            ui.set(button + ".TooltipTextSpans", skills(r));
            if (sel.filter(s -> s.id() == r.id()).isPresent()) {
                ui.set(button + ".Disabled", true);
            } else {
                ColonyPage.bind(events, button, "citizenSelect", r.id());
            }
        }
        ui.set(root + " #Job.TextSpans", sel.map(TownHallCitizensTab::job).orElse(Message.raw("")));
        // MC's layout shows the male seal until a female citizen is selected, and the recall button always.
        boolean female = sel.filter(r -> r.gender() == Gender.FEMALE).isPresent();
        ui.set(root + " #Male.Visible", !female);
        ui.set(root + " #Female.Visible", female);
        ui.set(root + " #RecallButton.Visible", true);
    }

    /** The citizens matching the search (MC updateCitizens), already sorted by name by the core. */
    private List<CitizenRow> shown() {
        return rows.stream()
                .filter(r -> r.matches(filter, text.apply(jobKey(r))))
                .toList();
    }

    /** MC CitizenDataView.getJobComponent: the job's name, or "Unemployed". */
    private static String jobKey(CitizenRow r) {
        return r.jobId().isEmpty()
                ? "hycolony.ui.townhall.unemployed"
                : JobNames.of(r.jobId()).key();
    }

    private static Message job(CitizenRow r) {
        return Message.translation(jobKey(r));
    }

    /** MC's tooltip: "skill: level " for every skill. */
    private static Message skills(CitizenRow r) {
        List<Message> parts = new ArrayList<>();
        for (CitizenRow.SkillLevel s : r.skills()) {
            parts.add(
                    Message.translation("hycolony.ui.skill." + s.skill().name().toLowerCase(Locale.ROOT)));
            parts.add(Message.raw(": " + s.level() + " "));
        }
        return Message.join(parts.toArray(Message[]::new));
    }

    /** The search and the selection update in place; Recall goes to the core. */
    @Override
    public Outcome handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "citizenSearch" -> {
                filter = act.name();
                return Outcome.REFRESH;
            }
            case "citizenSelect" -> {
                if (rows.stream().anyMatch(r -> r.id() == act.index())) {
                    selected = Optional.of(act.index());
                    return Outcome.REFRESH;
                }
                return Outcome.NONE;
            }
            case "recallOne" -> {
                selection().ifPresent(r -> recall.accept(r.id()));
                return Outcome.NONE;
            }
            default -> {
                return Outcome.NONE;
            }
        }
    }
}
