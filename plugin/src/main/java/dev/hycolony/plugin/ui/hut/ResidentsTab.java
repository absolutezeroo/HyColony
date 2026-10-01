package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.HousingActions;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A residence's Residents tab (MC WindowHutLiving and WindowAssignCitizen in one tab): assigned of max, the hiring
 * mode and Recall buttons, the residents with Unassign, then the other citizens with Assign. Assign and Unassign work
 * in manual mode only (MC greys them with its hire warning); Assign also needs a free place. The core checks
 * MANAGE_HUTS and re-shows the window.
 */
final class ResidentsTab implements HutTab {
    /** MC ChatFormatting.DARK_GREEN and RED, the colours of WindowAssignCitizen's distances. */
    private static final String CLOSER_COLOR = "#3c9a4f";

    private static final String FAR_COLOR = "#c0392b";

    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final ResidentsView view;

    ResidentsTab(ColonyManager manager, UUID player, BlockPos hut, ResidentsView view) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
    }

    @Override
    public String document() {
        return "Pages/HyColony/ResidentsTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.residents";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(
                root + " #Assigned.Text",
                Message.translation("hycolony.ui.residence.assigned")
                        .param("p0", String.valueOf(view.assigned()))
                        .param("p1", String.valueOf(view.max())));
        String mode = "hycolony.ui.building.hiring." + view.mode().name().toLowerCase(Locale.ROOT);
        header(ui, events, root + " #ModeButton", mode, "residentsMode");
        header(ui, events, root + " #RecallButton", "hycolony.ui.residence.recall", "residentsRecall");
        ui.set(root + " #ResidentsTitle.Text", Message.translation("hycolony.ui.residence.residentsTitle"));
        ui.set(root + " #CandidatesTitle.Text", Message.translation("hycolony.ui.residence.candidatesTitle"));
        for (int i = 0; i < view.residents().size(); i++) {
            ResidentsView.Resident r = view.residents().get(i);
            Message line = workLine(r.jobId(), r.workDistance(), r.far() ? FAR_COLOR : null);
            row(ui, events, root + " #Residents", i, new Row(r.name(), line, false, true));
        }
        boolean room = view.assigned() < view.max();
        for (int i = 0; i < view.candidates().size(); i++) {
            ResidentsView.Candidate c = view.candidates().get(i);
            Message line = Message.join(
                    workLine(c.jobId(), c.workDistance(), c.closer() ? CLOSER_COLOR : null),
                    Message.raw(" "),
                    homeLine(c.home()));
            row(ui, events, root + " #Candidates", i, new Row(c.name(), line, true, room));
        }
    }

    /** A header button showing {@code key}, sending {@code action}; disabled for a viewer who may not manage. */
    private void header(UICommandBuilder ui, UIEventBuilder events, String button, String key, String action) {
        ui.set(button + ".Text", Message.translation(key));
        if (view.canManage()) {
            ColonyPage.bind(events, button, action);
        } else {
            ui.set(button + ".Disabled", true);
        }
    }

    /** A row: the citizen's name, its line, an Assign (else Unassign) button and whether there is room for it. */
    private record Row(String name, Message line, boolean assign, boolean room) {}

    /** Appends row {@code i} of {@code list}: the name, its line, and its Assign or Unassign button. */
    private void row(UICommandBuilder ui, UIEventBuilder events, String list, int i, Row r) {
        String sel = list + "[" + i + "]";
        ui.append(list, "Pages/HyColony/ResidentRow.ui");
        ui.set(sel + " #Name.Text", r.name());
        ui.set(sel + " #Line.TextSpans", r.line());
        String button = sel + " #ActionButton";
        // A button's Text renders no nested message: one full key per button.
        ui.set(button + ".Text", Message.translation("hycolony.ui.residence." + (r.assign() ? "assign" : "unassign")));
        if (!view.manual()) {
            ui.set(button + ".Disabled", true);
            ui.set(button + ".TooltipText", Message.translation("hycolony.ui.residence.hireWarning"));
        } else if (!view.canManage() || !r.room()) {
            ui.set(button + ".Disabled", true);
        } else {
            ColonyPage.bind(events, button, r.assign() ? "residentAssign" : "residentUnassign", i);
        }
    }

    /** MC: "Job: Works N blocks from here." in {@code color} if any, or "Unemployed". */
    private static Message workLine(Optional<String> jobId, OptionalInt distance, @Nullable String color) {
        if (jobId.isEmpty()) {
            return Message.translation("hycolony.ui.residence.unemployed");
        }
        Message works = distance.isPresent()
                ? Message.translation("hycolony.ui.residence.works").param("p0", String.valueOf(distance.getAsInt()))
                : Message.raw("");
        if (color != null) {
            works = works.color(color);
        }
        return Message.join(ColonyPage.jobName(jobId.get()), Message.raw(": "), works);
    }

    /** MC: "Homeless", or "Current work distance: M blocks" in red past MC's far threshold, or nothing. */
    private static Message homeLine(ResidentsView.Home home) {
        if (home.homeless()) {
            return Message.translation("hycolony.ui.residence.homeless");
        }
        if (home.currentDistance().isEmpty()) {
            return Message.raw("");
        }
        Message current = Message.translation("hycolony.ui.residence.currently")
                .param("p0", String.valueOf(home.currentDistance().getAsInt()));
        return home.far() ? current.color(FAR_COLOR) : current;
    }

    /** Mode, recall, then Assign or Unassign for the row's citizen; an unknown row does nothing. */
    @Override
    public void handle(ColonyPage.Act act) {
        HousingActions actions = new HousingActions(manager);
        int i = act.index();
        switch (act.action()) {
            case "residentsMode" -> actions.cycleMode(player, hut);
            case "residentsRecall" -> actions.recall(player, hut);
            case "residentAssign" -> {
                if (i >= 0 && i < view.candidates().size()) {
                    actions.assign(player, hut, view.candidates().get(i).citizenId());
                }
            }
            case "residentUnassign" -> {
                if (i >= 0 && i < view.residents().size()) {
                    actions.unassign(player, hut, view.residents().get(i).citizenId());
                }
            }
            default -> {} // BuildingPage offers every action to every tab
        }
    }
}
