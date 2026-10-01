package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.hut.HireView;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.HutWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * A worker hut's hire window (MC WindowHireWorker): "Choose workers for the ...", the job button, the citizens in
 * MC's order with where they live and their skills (the job's primary in dark green bold, its secondary in gold bold),
 * Hire or Fire, the building hiring mode and "Show employed?". On a warehouse it lists couriers (MC
 * CourierAssignmentModuleView): no coloured skill, "Show employed?" greyed. The core checks MANAGE_HUTS and shows the
 * hut again, which redraws this window.
 *
 * <p>Deviation from MC: no Pause nor Restart buttons, as HyColony cannot pause a citizen yet.
 */
public final class HireWorkerPage extends ColonyPage implements HutWindow {
    /** MC ChatFormatting.DARK_GREEN and GOLD, WindowHireWorker.createColor. */
    private static final String PRIMARY_COLOR = "#00aa00";

    private static final String SECONDARY_COLOR = "#ffaa00";

    private final BuildingView view;
    /** MC showEmployed: off when the window opens. */
    private boolean showEmployed;

    public HireWorkerPage(PlayerRef playerRef, ColonyManager manager, BuildingView view) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public BlockPos hutPos() {
        return view.pos();
    }

    @Override
    public ColonyPage with(PlayerRef playerRef, BuildingView fresh) {
        HireWorkerPage page = new HireWorkerPage(playerRef, manager, fresh);
        page.showEmployed = showEmployed;
        return page;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/HireWorker.ui");
        bind(events, "#Cancel", "close");
        view.hire().ifPresent(h -> render(ui, events, h));
    }

    private void render(UICommandBuilder ui, UIEventBuilder events, HireView h) {
        Message hut = view.customName().isEmpty() ? buildingName(view.typeId()) : Message.raw(view.customName());
        ui.set(
                "#JobLabel.TextSpans",
                Message.translation("hycolony.ui.hut.hire.description").param("p0", hut));
        // MC setupJobButtons: the job's name, with its worker count once it has some; the selected job disabled. A
        // button renders no nested message: one full key per job, with and without the count.
        String jobKey = "hycolony.ui.hut.hire.job." + shortJob(h.jobId()) + (h.workers() > 0 ? ".count" : "");
        ui.set("#Job.Text", Message.translation(jobKey).param("p0", String.valueOf(h.workers())));
        ui.set("#Job.Disabled", true);
        ui.set("#Job.TooltipText", Message.translation("hycolony.ui.hut.jobDesc." + shortJob(h.jobId())));
        ui.set(
                "#Mode.Text",
                Message.translation(
                        "hycolony.ui.hut.hiringMode." + lower(h.mode().name())));
        bind(events, "#Mode", "mode");
        ui.set("#ShowEmployed.Text", Message.translation("hycolony.ui.hut.hire." + (showEmployed ? "yes" : "no")));
        if (h.showEmployedEnabled()) {
            bind(events, "#ShowEmployed", "showEmployed");
        } else {
            ui.set("#ShowEmployed.Disabled", true);
        }
        List<HireView.Candidate> rows = h.listed(showEmployed);
        for (int i = 0; i < rows.size(); i++) {
            row(ui, events, "#Citizens[" + i + "]", h, rows.get(i));
        }
    }

    private void row(UICommandBuilder ui, UIEventBuilder events, String row, HireView h, HireView.Candidate c) {
        ui.append("#Citizens", "Pages/HyColony/Mc/HireRow.ui");
        Message job =
                c.jobId().map(ColonyPage::jobName).orElse(Message.translation("hycolony.ui.residence.unemployed"));
        ui.set(row + " #Name.TextSpans", Message.join(job, Message.raw(": " + c.name())));
        ui.set(row + " #Distance.TextSpans", homeLine(c));
        ui.set(row + " #Skills.TextSpans", skills(h, c));
        ui.set(row + " #Skills.TooltipText", Message.translation("hycolony.ui.hut.skillsDesc." + shortJob(h.jobId())));
        String button = row + " #Button";
        switch (h.button(c, showEmployed)) {
            case HIRE -> {
                ui.set(button + ".Text", Message.translation("hycolony.ui.building.hire"));
                bind(events, button, "hire", c.citizenId());
            }
            case FIRE -> {
                ui.set(button + ".Text", Message.translation("hycolony.ui.building.fire"));
                bind(events, button, "fire", c.citizenId());
            }
            case NONE -> ui.set(button + ".Visible", false);
        }
    }

    /** MC WindowHireWorker's distance label: homeless, lives here, lives at its workplace, or N blocks away. */
    private static Message homeLine(HireView.Candidate c) {
        return switch (c.home()) {
            case HOMELESS -> Message.translation("hycolony.ui.hiring.homeless");
            case LIVES_HERE -> Message.translation("hycolony.ui.hiring.livesHere");
            case LIVES_AT_WORK -> Message.translation("hycolony.ui.hiring.livesAtWork");
            case DISTANCE ->
                Message.translation("hycolony.ui.hiring.distance").param("p0", String.valueOf(c.homeDistance()));
        };
    }

    /** MC: "skill: level" for every skill, the primary and secondary coloured and bold. */
    private static Message skills(HireView h, HireView.Candidate c) {
        List<Message> parts = new ArrayList<>();
        for (CitizenRow.SkillLevel s : c.skills()) {
            Message name =
                    Message.translation("hycolony.ui.skill." + lower(s.skill().name()));
            Message level = Message.raw(": " + s.level());
            boolean primary = h.primary().filter(p -> p == s.skill()).isPresent();
            boolean secondary = h.secondary().filter(p -> p == s.skill()).isPresent();
            if (primary || secondary) {
                String color = primary ? PRIMARY_COLOR : SECONDARY_COLOR;
                name = name.color(color).bold(true);
                level = level.color(color).bold(true);
            }
            if (!parts.isEmpty()) {
                parts.add(Message.raw(" "));
            }
            parts.add(name);
            parts.add(level);
        }
        return Message.join(parts.toArray(Message[]::new));
    }

    private static String shortJob(String jobId) {
        return jobId.substring(jobId.indexOf(':') + 1);
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Hire and Fire by citizen id, the mode, "Show employed?" (page state) and the cross back to the hut. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "hire" -> manager.huts().hire(player, view.pos(), act.index());
            case "fire" -> manager.huts().fire(player, view.pos(), act.index());
            case "mode" -> manager.hutWindows().cycleHiring(player, view.pos());
            case "showEmployed" -> {
                showEmployed = !showEmployed;
                rebuild();
            }
            case "close" -> BuildingPage.back(ref, store, playerRef, view, manager);
            default -> {}
        }
    }
}
