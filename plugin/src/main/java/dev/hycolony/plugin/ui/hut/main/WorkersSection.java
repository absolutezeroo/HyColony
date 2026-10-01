package dev.hycolony.plugin.ui.hut.main;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Optional;
import java.util.UUID;

/**
 * The workers part of a hut's main page (MC AbstractWindowWorkerModuleBuilding, layouthutpageactions.xml): "Job: Name"
 * per worker with "Name (id)" as tooltip, Manage Workers, Recall Worker, the pickup priority with - and +, and Request
 * Pickup Now. Every button stays enabled, as MC's: the core refuses a player without MANAGE_HUTS.
 */
final class WorkersSection {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;

    WorkersSection(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        String list = root + " #Workers";
        for (int i = 0; i < view.workers().size(); i++) {
            BuildingView.WorkerLine w = view.workers().get(i);
            String line = list + "[" + i + "] #Line";
            ui.append(list, "Pages/HyColony/Mc/WorkerLine.ui");
            ui.set(line + ".TextSpans", Message.join(ColonyPage.jobName(w.jobId()), Message.raw(": " + w.name())));
            ui.set(line + ".TooltipText", w.name() + " (" + w.citizenId() + ")");
        }
        ColonyPage.bind(events, root + " #Hire", "hire");
        ColonyPage.bind(events, root + " #Recall", "recall");
        ColonyPage.bind(events, root + " #PrioDown", "pickupDown");
        ColonyPage.bind(events, root + " #PrioUp", "pickupUp");
        ColonyPage.bind(events, root + " #ForcePickup", "forcePickup");
        int priority = view.pickupPriority().orElse(0);
        ui.set(
                root + " #Prio.Text",
                priority == 0
                        ? Message.translation("hycolony.ui.pickup.never")
                        : Message.translation("hycolony.ui.pickup.priority").param("p0", String.valueOf(priority)));
    }

    /** The core checks the right and shows the hut again; Manage Workers answers with the hire window to open. */
    Optional<MainTab.Annex> handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "hire" -> {
                if (manager.hutWindows().mayAssign(player, view.pos())) {
                    return Optional.of(MainTab.Annex.HIRE);
                }
            }
            case "recall" -> manager.hutWindows().recallWorkers(player, view.pos());
            case "pickupDown" -> manager.hutWindows().pickup().alterPickupPriority(player, view.pos(), false);
            case "pickupUp" -> manager.hutWindows().pickup().alterPickupPriority(player, view.pos(), true);
            case "forcePickup" -> manager.hutWindows().pickup().forcePickup(player, view.pos());
            default -> {}
        }
        return Optional.empty();
    }
}
