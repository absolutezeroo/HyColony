package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuilderResourcesView;
import dev.hycolony.core.colony.ui.BuilderResourcesView.ResourceRow;
import java.util.List;
import javax.annotation.Nonnull;

/** The builder hut's resource list, coloured like MineColonies'. */
public final class BuilderResourcesPage extends ColonyPage {
    private final BuilderResourcesView view;

    public BuilderResourcesPage(PlayerRef playerRef, BuilderResourcesView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
    }

    private static String color(BuilderResourcesView.Status status) {
        return switch (status) {
            case DONT_HAVE -> "#962f2f";
            case NEED_MORE -> "#cc8844";
            case HAVE_ENOUGH -> "#3d913f";
            case NOT_NEEDED -> "#7a8a9a";
        };
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/BuilderResources.ui");
        if (view.header().isEmpty()) {
            ui.set("#Progress.Text", Message.translation("hycolony.ui.resources.noOrder"));
        } else {
            BuilderResourcesView.Header h = view.header().get();
            ui.set(
                    "#Progress.Text",
                    Message.translation("hycolony.ui.resources.progress")
                            .param("p0", String.valueOf(h.percent()))
                            .param("p1", h.step() + "/" + h.totalSteps()));
        }
        List<ResourceRow> rows = view.rows();
        if (rows.isEmpty() && view.header().isPresent()) {
            ui.set("#Empty.Visible", true);
            ui.set("#Empty.Text", Message.translation("hycolony.ui.resources.empty"));
        }
        for (int i = 0; i < rows.size(); i++) {
            ResourceRow r = rows.get(i);
            String row = "#Resources[" + i + "]";
            ui.append("#Resources", "Pages/HyColony/ResourceRow.ui");
            ui.set(row + " #Icon.ItemId", r.item().id());
            ui.set(row + " #Name.Text", itemName(r.item().id()));
            ui.set(row + " #Count.Text", r.available() + " / " + r.needed());
            ui.set(row + " #Count.Style.TextColor", color(r.status()));
            if (r.status() == BuilderResourcesView.Status.NOT_NEEDED || r.playerHas() == 0) {
                ui.set(row + " #AddButton.Visible", false);
            } else {
                bind(events, row + " #AddButton", "add", i);
            }
        }
        bind(events, "#HutButton", "hut");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action) {
            case "add" -> {
                if (act.index >= 0 && act.index < view.rows().size()) {
                    ResourceRow r = view.rows().get(act.index);
                    manager.requestActions().addToHut(player, view.hut(), r.item(), r.needed() - r.available());
                    manager.windows().openBuilderResources(player, view.hut()); // addToHut does not re-show
                }
            }
            case "hut" -> manager.windows().openBuilding(player, view.hut());
            default -> {}
        }
    }
}
