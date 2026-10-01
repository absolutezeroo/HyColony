package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.citizen.HealthBar;
import dev.hycolony.core.app.citizen.SaturationBar;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The citizen's Main page (MC MainWindowCitizen, main.xml): name, health and food bars, the skills and the gender seal.
 *
 * <p>Deviation from MC: the job, workplace and activity lines, the XP bars and the job's skills first are kept at the
 * user's request (see SkillRows); the happiness bar is absent (no happiness system).
 */
final class CitizenMainTab {
    private static final String PAGE = "#MainPage";
    /** Each heart's layer element in HeartSlot.ui. */
    private static final Map<HealthBar.Heart, String> LAYERS = new EnumMap<>(Map.of(
            HealthBar.Heart.EMPTY, "Empty",
            HealthBar.Heart.RED, "Red",
            HealthBar.Heart.GOLDEN, "Golden",
            HealthBar.Heart.GREEN, "Green",
            HealthBar.Heart.BLUE, "Blue",
            HealthBar.Heart.HALF_RED, "HalfRed",
            HealthBar.Heart.HALF_GOLDEN, "HalfGolden",
            HealthBar.Heart.HALF_GREEN, "HalfGreen",
            HealthBar.Heart.HALF_BLUE, "HalfBlue"));

    private CitizenMainTab() {}

    static void render(UICommandBuilder ui, CitizenView view) {
        ui.set(PAGE + " #Name.Text", view.name());
        lines(ui, view);
        view.health().ifPresent(h -> hearts(ui, h));
        food(ui, view.saturation());
        if (view.gender() == Gender.FEMALE) {
            ui.set(PAGE + " #Male.Visible", false);
            ui.set(PAGE + " #Female.Visible", true);
        }
    }

    /** "Job: X, workplace: Y", then the activity: waiting for a request, the job's own line, or the state. */
    private static void lines(UICommandBuilder ui, CitizenView view) {
        Message none = Message.translation("hycolony.ui.citizen.none");
        ui.set(
                PAGE + " #JobLine.TextSpans",
                Message.translation("hycolony.ui.citizen.jobAndWorkplace")
                        .param("p0", view.jobId().map(ColonyPage::jobName).orElse(none))
                        .param(
                                "p1",
                                view.workBuilding()
                                        .map(ColonyPage::buildingName)
                                        .orElse(none)));
        if (view.waitingFor().isPresent()) {
            ui.set(
                    PAGE + " #Activity.TextSpans",
                    Message.translation("hycolony.ui.citizen.waitingFor")
                            .param("p0", RequestsPage.describe(view.waitingFor().get())));
        } else if (view.jobActivity().isPresent()) {
            ui.set(
                    PAGE + " #Activity.TextSpans",
                    HytaleNotifier.toMessage(view.jobActivity().get()));
        } else {
            ui.set(PAGE + " #Activity.Text", Message.translation("hycolony.status." + view.activity()));
        }
    }

    /** MC createHealthBar: ten heart slots with their layers, and half the health beside them. */
    private static void hearts(UICommandBuilder ui, int health) {
        List<List<HealthBar.Heart>> slots = HealthBar.of(health);
        for (int i = 0; i < slots.size(); i++) {
            String slot = PAGE + " #Hearts[" + i + "]";
            ui.append(PAGE + " #Hearts", "Pages/HyColony/Mc/HeartSlot.ui");
            for (HealthBar.Heart layer : slots.get(i)) {
                ui.set(slot + " #" + id(layer) + ".Visible", true);
            }
        }
        ui.set(PAGE + " #HealthLabel.Text", String.valueOf(HealthBar.label(health)));
    }

    /** MC createSaturationBar: one icon per 6 saturation, full, half or empty. */
    private static void food(UICommandBuilder ui, double saturation) {
        List<SaturationBar.Icon> icons = SaturationBar.of(saturation);
        for (int i = 0; i < icons.size(); i++) {
            String slot = PAGE + " #Food[" + i + "]";
            ui.append(PAGE + " #Food", "Pages/HyColony/Mc/FoodSlot.ui");
            switch (icons.get(i)) {
                case FULL -> ui.set(slot + " #Full.Visible", true);
                case HALF -> ui.set(slot + " #Half.Visible", true);
                case EMPTY -> {}
            }
        }
    }

    /** The heart's layer in HeartSlot.ui. */
    private static String id(HealthBar.Heart heart) {
        return LAYERS.getOrDefault(heart, "Empty");
    }
}
