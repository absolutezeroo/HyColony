package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.construction.resources.BuilderResourcesView.ResourceRow;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.UUID;

/**
 * The builder hut's Resources tab (MC WindowBuilderResModule): order name, step, supplied / progress, then one row per
 * item, whole row coloured by status, with the player's shortfall and an Add button.
 */
final class BuilderResourcesTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final BuilderResourcesView view;

    BuilderResourcesTab(ColonyManager manager, UUID player, BlockPos hut, BuilderResourcesView view) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
    }

    /** MC WindowBuilderResModule's row colours: BlockUI's red, orange, darkgreen and black. */
    private static String color(BuilderResourcesView.Status status) {
        return switch (status) {
            case DONT_HAVE -> "#ff0000";
            case NEED_MORE -> "#ffa500";
            case HAVE_ENOUGH -> "#006400";
            case NOT_NEEDED -> "#000000";
        };
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/BuilderResources.ui";
    }

    @Override
    public String icon() {
        return "inventory";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.resources";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Desc.Text", Message.translation(descKey()));
        header(ui, root);
        List<ResourceRow> rows = view.rows();
        for (int i = 0; i < rows.size(); i++) {
            ResourceRow r = rows.get(i);
            String row = root + " #Resources[" + i + "]";
            ui.append(root + " #Resources", "Pages/HyColony/Mc/BuilderResourceRow.ui");
            ui.set(row + " #Icon.ItemId", r.item().id());
            ui.set(row + " #Name.Text", ColonyPage.itemName(r.item().id()));
            int missing = r.missingFromPlayer();
            ui.set(row + " #Missing.Text", missing < 0 ? String.valueOf(missing) : "");
            ui.set(row + " #Count.Text", r.available() + " / " + r.needed());
            String color = color(r.status());
            for (String label : new String[] {" #Name", " #Missing", " #Count"}) {
                ui.set(row + label + ".Style.TextColor", color);
            }
            if (r.status().canAdd()) {
                ColonyPage.bind(events, row + " #AddButton", "add", i);
            } else {
                ui.set(row + " #AddButton.Disabled", true);
            }
        }
    }

    /** MC onOpened: the order's name (none without one), "Step n/m" always, "Supplied / Used" once one is needed. */
    private void header(UICommandBuilder ui, String root) {
        if (view.header().isEmpty()) {
            ui.set(
                    root + " #Step.Text",
                    Message.translation("hycolony.ui.resources.step")
                            .param("p0", "0")
                            .param("p1", "0"));
            return;
        }
        BuilderResourcesView.Header h = view.header().get();
        Message name = Message.translation("hycolony.ui.workorders.line")
                .param("p0", ColonyPage.workOrderTypeName(h.type()))
                .param("p1", ColonyPage.buildingName(h.buildingName()))
                .param("p2", String.valueOf(h.targetLevel()));
        ui.set(root + " #OrderName.TextSpans", name);
        ui.set(root + " #OrderName.TooltipTextSpans", name); // MC: the same text as tooltip
        ui.set(
                root + " #Step.Text",
                Message.translation("hycolony.ui.resources.step")
                        .param("p0", String.valueOf(h.step()))
                        .param("p1", String.valueOf(h.totalSteps())));
        if (!view.rows().isEmpty()) {
            ui.set(
                    root + " #Supply.Text",
                    Message.translation("hycolony.ui.resources.supply")
                            .param("p0", String.valueOf(h.suppliedPercent()))
                            .param("p1", String.valueOf(h.percent())));
        }
    }

    /** Add: the player's items go into the hut (MC TransferItemsRequestMessage); the core shows the window again. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("add".equals(act.action())
                && act.index() >= 0
                && act.index() < view.rows().size()) {
            ResourceRow r = view.rows().get(act.index());
            manager.requestActions().addToHut(player, hut, r.item(), r.missing());
        }
    }
}
