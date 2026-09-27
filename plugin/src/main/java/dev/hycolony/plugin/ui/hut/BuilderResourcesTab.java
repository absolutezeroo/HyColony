package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.tab.BuilderResourcesView;
import dev.hycolony.core.colony.ui.tab.BuilderResourcesView.ResourceRow;
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

    /**
     * MC colours red, orange, dark green and black; black is unreadable on Hytale's dark panels, so NOT_NEEDED is grey.
     */
    private static String color(BuilderResourcesView.Status status) {
        return switch (status) {
            case DONT_HAVE -> "#962f2f";
            case NEED_MORE -> "#cc8844";
            case HAVE_ENOUGH -> "#3d913f";
            case NOT_NEEDED -> "#7a8a9a";
        };
    }

    @Override
    public String document() {
        return "Pages/HyColony/BuilderResourcesTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.resources";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        header(ui, root);
        List<ResourceRow> rows = view.rows();
        if (rows.isEmpty() && view.header().isPresent()) {
            ui.set(root + " #ResourcesEmpty.Visible", true);
            ui.set(root + " #ResourcesEmpty.Text", Message.translation("hycolony.ui.resources.empty"));
        }
        for (int i = 0; i < rows.size(); i++) {
            ResourceRow r = rows.get(i);
            String row = root + " #Resources[" + i + "]";
            ui.append(root + " #Resources", "Pages/HyColony/ResourceRow.ui");
            ui.set(row + " #Icon.ItemId", r.item().id());
            ui.set(row + " #Name.Text", ColonyPage.itemName(r.item().id()));
            int missing = r.missingFromPlayer();
            ui.set(row + " #Missing.Text", missing < 0 ? String.valueOf(missing) : "");
            ui.set(row + " #Count.Text", r.available() + " / " + r.needed());
            String color = color(r.status());
            for (String label : new String[] {" #Name", " #Missing", " #Count"}) {
                ui.set(row + label + ".Style.TextColor", color);
            }
            // MC disables the button in DONT_HAVE and NOT_NEEDED.
            boolean canAdd = r.status() == BuilderResourcesView.Status.NEED_MORE
                    || r.status() == BuilderResourcesView.Status.HAVE_ENOUGH;
            if (canAdd) {
                ColonyPage.bind(events, row + " #AddButton", "add", i);
            } else {
                ui.set(row + " #AddButton.Disabled", true);
            }
        }
    }

    private void header(UICommandBuilder ui, String root) {
        if (view.header().isEmpty()) {
            ui.set(root + " #OrderName.Text", Message.translation("hycolony.ui.resources.noOrder"));
            ui.set(root + " #Step.Visible", false);
            ui.set(root + " #Supply.Visible", false);
            return;
        }
        BuilderResourcesView.Header h = view.header().get();
        ui.set(
                root + " #OrderName.TextSpans",
                Message.translation("hycolony.ui.workorders.line")
                        .param("p0", ColonyPage.workOrderTypeName(h.type()))
                        .param("p1", ColonyPage.buildingName(h.buildingName()))
                        .param("p2", String.valueOf(h.targetLevel())));
        ui.set(
                root + " #Step.Text",
                Message.translation("hycolony.ui.resources.step")
                        .param("p0", String.valueOf(h.step()))
                        .param("p1", String.valueOf(h.totalSteps())));
        if (view.rows().isEmpty()) {
            ui.set(root + " #Supply.Visible", false); // MC sets it only when something is needed
        } else {
            ui.set(
                    root + " #Supply.Text",
                    Message.translation("hycolony.ui.resources.supply")
                            .param("p0", String.valueOf(h.suppliedPercent()))
                            .param("p1", String.valueOf(h.percent())));
        }
    }

    /** Add: the player's items go into the hut (MC TransferItemsRequestMessage), then the window is shown again. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("add".equals(act.action())
                && act.index() >= 0
                && act.index() < view.rows().size()) {
            ResourceRow r = view.rows().get(act.index());
            manager.requestActions().addToHut(player, hut, r.item(), r.needed() - r.available());
            manager.windows().openBuilding(player, hut); // addToHut does not re-show
        }
    }
}
