package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.annex.HireWorkerPage;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The warehouse's Couriers page (MC SpecialAssignmentModuleWindow over CourierAssignmentModuleView): "Courier: Name"
 * per attached courier, Manage Workers (the hire window on the courier module; at level 0 MC's message instead) and
 * Recall Worker. The core checks MANAGE_HUTS.
 */
final class WarehouseCouriersTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView hut;
    private final CourierAssignmentView assignment;

    WarehouseCouriersTab(ColonyManager manager, UUID player, BuildingView hut, CourierAssignmentView assignment) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.assignment = assignment;
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Couriers.ui";
    }

    @Override
    public String icon() {
        return "entity";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.couriers";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        String list = root + " #Couriers";
        Message job = ColonyPage.jobName(CourierAssignmentModule.COURIER_JOB_ID);
        for (int i = 0; i < assignment.couriers().size(); i++) {
            ui.append(list, "Pages/HyColony/Mc/WorkerLine.ui");
            ui.set(
                    list + "[" + i + "] #Line.TextSpans",
                    Message.join(job, Message.raw(": " + assignment.couriers().get(i))));
        }
        ColonyPage.bind(events, root + " #Hire", "couriersHire");
        ColonyPage.bind(events, root + " #Recall", "couriersRecall");
    }

    /** MC RecallCitizenMessage: the couriers come to the warehouse. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("couriersRecall".equals(act.action())) {
            manager.hutWindows().recallWorkers(player, hut.pos());
        }
    }

    /** Manage Workers: the hire window on the courier module, or MC's level 0 message. */
    @Override
    public Optional<Function<PlayerRef, CustomUIPage>> opens(ColonyPage.Act act) {
        if (!"couriersHire".equals(act.action()) || !manager.hutWindows().mayAssign(player, hut.pos())) {
            return Optional.empty();
        }
        return Optional.of(pr -> new HireWorkerPage(pr, manager, hut));
    }
}
