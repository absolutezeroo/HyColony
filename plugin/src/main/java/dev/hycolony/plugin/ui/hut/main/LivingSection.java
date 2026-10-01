package dev.hycolony.plugin.ui.hut.main;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.HousingActions;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Optional;
import java.util.UUID;

/**
 * The residents part of a residence's main page (MC WindowHutLiving, windowhuthome.xml): "Assigned Citizens: n/m",
 * "Job: Name" (or "Name") per resident, Manage Housing and Recall Citizens.
 */
final class LivingSection {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;

    LivingSection(ColonyManager manager, UUID player, BlockPos hut) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
    }

    void render(UICommandBuilder ui, UIEventBuilder events, String root, ResidentsView view) {
        ui.set(
                root + " #Assigned.Text",
                Message.translation("hycolony.ui.residence.assigned")
                        .param("p0", String.valueOf(view.assigned()))
                        .param("p1", String.valueOf(view.max())));
        String list = root + " #Residents";
        for (int i = 0; i < view.residents().size(); i++) {
            ResidentsView.Resident r = view.residents().get(i);
            ui.append(list, "Pages/HyColony/Mc/WorkerLine.ui");
            // MC WindowHutLiving: "Job: Name", or the name alone without a job.
            ui.set(
                    list + "[" + i + "] #Line.TextSpans",
                    r.jobId()
                            .map(j -> Message.join(ColonyPage.jobName(j), Message.raw(": " + r.name())))
                            .orElse(Message.raw(r.name())));
        }
        ColonyPage.bind(events, root + " #Assign", "assign");
        ColonyPage.bind(events, root + " #Recall", "recallResidents");
    }

    /** Manage Housing answers with the assign window to open (MC: a level 0 residence says so instead). */
    Optional<MainTab.Annex> handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "assign" -> {
                if (manager.hutWindows().mayAssign(player, hut)) {
                    return Optional.of(MainTab.Annex.ASSIGN);
                }
            }
            case "recallResidents" -> new HousingActions(manager).recall(player, hut);
            default -> {}
        }
        return Optional.empty();
    }
}
