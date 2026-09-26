package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.CitizenView.SkillRow;
import dev.hycolony.core.colony.ui.RequestsView.RequestRow;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.List;
import javax.annotation.Nonnull;

/** MineColonies WindowCitizen: job, workplace, activity, skills, inventory and open requests with "Supply". */
public final class CitizenPage extends ColonyPage {
    private final CitizenView view;
    private final SkillRowRenderer skillRows;

    public CitizenPage(PlayerRef playerRef, CitizenView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.skillRows = new SkillRowRenderer(ids);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Citizen.ui");
        Message none = Message.translation("hycolony.ui.citizen.none");
        ui.set("#Name.Text", view.name());
        ui.set(
                "#Job.TextSpans",
                Message.translation("hycolony.ui.citizen.job")
                        .param("p0", view.jobId().map(ColonyPage::jobName).orElse(none)));
        ui.set(
                "#Workplace.TextSpans",
                Message.translation("hycolony.ui.citizen.workplace")
                        .param(
                                "p0",
                                view.workBuilding()
                                        .map(ColonyPage::buildingName)
                                        .orElse(none)));
        if (view.waitingFor().isPresent()) {
            ui.set(
                    "#Activity.TextSpans",
                    Message.translation("hycolony.ui.citizen.waitingFor")
                            .param("p0", RequestsPage.describe(view.waitingFor().get())));
        } else if (view.jobActivity().isPresent()) {
            ui.set(
                    "#Activity.TextSpans",
                    HytaleNotifier.toMessage(view.jobActivity().get()));
        } else {
            ui.set("#Activity.Text", Message.translation("hycolony.status." + view.activity()));
        }

        List<SkillRow> skills = view.skills();
        for (int i = 0; i < skills.size(); i++) {
            skillRows.append(ui, "#Skills", i, skills.get(i));
        }

        List<ItemAmount> items = view.inventory();
        if (items.isEmpty()) {
            ui.set("#InventoryEmpty.Visible", true);
            ui.set("#InventoryEmpty.Text", Message.translation("hycolony.ui.citizen.inventoryEmpty"));
        }
        for (int i = 0; i < items.size(); i++) {
            String row = "#Inventory[" + i + "]";
            ui.append("#Inventory", "Pages/HyColony/ResourceRow.ui");
            ui.set(row + " #Icon.ItemId", items.get(i).item().id());
            ui.set(row + " #Name.Text", itemName(items.get(i).item().id()));
            ui.set(row + " #Count.Text", String.valueOf(items.get(i).count()));
            ui.set(row + " #AddButton.Visible", false);
        }

        List<RequestRow> rows = view.requests();
        if (rows.isEmpty()) {
            ui.set("#RequestsEmpty.Visible", true);
            ui.set("#RequestsEmpty.Text", Message.translation("hycolony.ui.requests.empty"));
        }
        for (int i = 0; i < rows.size(); i++) {
            RequestRow r = rows.get(i);
            String row = "#Requests[" + i + "]";
            ui.append("#Requests", "Pages/HyColony/RequestRow.ui");
            ui.set(row + " #Description.TextSpans", RequestsPage.describe(r.requestable()));
            ui.set(
                    row + " #Info.TextSpans",
                    Message.translation("hycolony.ui.requests.info")
                            .param("p0", buildingName(r.requesterName()))
                            .param("p1", String.valueOf(r.playerHas())));
            if (r.playerHas() > 0) {
                bind(events, row + " #FulfilButton", "fulfil", i);
            } else {
                ui.set(row + " #FulfilButton.Visible", false);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (act.action.equals("fulfil")
                && act.index >= 0
                && act.index < view.requests().size()) {
            manager.requestActions()
                    .fulfil(
                            player,
                            view.colonyId(),
                            view.requests().get(act.index).token());
            manager.windows().openCitizen(player, view.colonyId(), view.citizenId()); // fulfil does not re-show
        }
    }
}
