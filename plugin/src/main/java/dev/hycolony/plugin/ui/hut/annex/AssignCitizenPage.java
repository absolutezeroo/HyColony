package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.HousingActions;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.HutWindow;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A residence's assign window (MC WindowAssignCitizen): its residents with Unassign, the other citizens with Assign
 * (MC's order and colours), the building assignment mode and the cross back to the residence. Assign and Unassign
 * work in manual mode only (else disabled with MC's hire warning); Assign also needs a free place. The core checks
 * MANAGE_HUTS and shows the residence again, which redraws this window.
 */
public final class AssignCitizenPage extends ColonyPage implements HutWindow {
    /** MC ChatFormatting.DARK_GREEN and RED, the colours of WindowAssignCitizen's distances. */
    private static final String CLOSER_COLOR = "#00aa00";

    private static final String FAR_COLOR = "#ff5555";

    /** MC windowassigncitizen.xml: the candidates' line at text scale 0.8 (the row's 14 for the residents). */
    private static final int CANDIDATE_FONT_SIZE = 12;

    private final BuildingView view;

    public AssignCitizenPage(PlayerRef playerRef, ColonyManager manager, BuildingView view) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public BlockPos hutPos() {
        return view.pos();
    }

    @Override
    public ColonyPage with(PlayerRef playerRef, BuildingView fresh) {
        return new AssignCitizenPage(playerRef, manager, fresh);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/AssignCitizen.ui");
        bind(events, "#Cancel", "close");
        view.tab(ResidentsView.class).ifPresent(r -> render(ui, events, r));
    }

    private void render(UICommandBuilder ui, UIEventBuilder events, ResidentsView r) {
        ui.set(
                "#Mode.Text",
                Message.translation(
                        "hycolony.ui.hut.hiringMode." + r.mode().name().toLowerCase(Locale.ROOT)));
        bind(events, "#Mode", "mode");
        for (int i = 0; i < r.residents().size(); i++) {
            ResidentsView.Resident res = r.residents().get(i);
            Message line = workLine(res.jobId(), res.workDistance(), res.far() ? FAR_COLOR : null);
            row(ui, events, new Row("#Residents", i, res.citizenId(), res.name(), line, false), r, true);
        }
        boolean room = r.assigned() < r.max();
        for (int i = 0; i < r.candidates().size(); i++) {
            ResidentsView.Candidate c = r.candidates().get(i);
            // MC: "Job: works... home", or "Unemployed", a line break, then the home.
            Message line = Message.join(
                    workLine(c.jobId(), c.workDistance(), c.closer() ? CLOSER_COLOR : null),
                    Message.raw(c.jobId().isPresent() ? " " : "\n"),
                    homeLine(c.home()));
            row(ui, events, new Row("#Candidates", i, c.citizenId(), c.name(), line, true), r, room);
        }
    }

    /** Row {@code index} of {@code list}: its citizen, its line, and whether its button assigns (else unassigns). */
    private record Row(String list, int index, int citizenId, String name, Message line, boolean assign) {}

    private void row(UICommandBuilder ui, UIEventBuilder events, Row row, ResidentsView r, boolean room) {
        String sel = row.list() + "[" + row.index() + "]";
        ui.append(row.list(), "Pages/HyColony/Mc/AssignRow.ui");
        ui.set(sel + " #Name.Text", row.name());
        ui.set(sel + " #Line.TextSpans", row.line());
        if (row.assign()) {
            ui.set(sel + " #Line.Style.FontSize", CANDIDATE_FONT_SIZE);
        }
        String button = sel + " #Button";
        // A button's Text renders no nested message: one full key per button.
        ui.set(
                button + ".Text",
                Message.translation("hycolony.ui.residence." + (row.assign() ? "assign" : "unassign")));
        if (!r.manual()) {
            ui.set(button + ".Disabled", true);
            ui.set(button + ".TooltipText", Message.translation("hycolony.ui.residence.hireWarning"));
        } else if (!room) {
            ui.set(button + ".Disabled", true);
        } else {
            bind(events, button, row.assign() ? "assign" : "unassign", row.citizenId());
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

    /** Mode, Assign, Unassign (by citizen id), the cross back to the residence. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        HousingActions actions = new HousingActions(manager);
        switch (act.action()) {
            case "mode" -> actions.cycleMode(player, view.pos());
            case "assign" -> actions.assign(player, view.pos(), act.index());
            case "unassign" -> actions.unassign(player, view.pos(), act.index());
            case "close" -> BuildingPage.back(ref, store, playerRef, view, manager);
            default -> {}
        }
    }
}
