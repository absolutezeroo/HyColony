package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildOptionsView;
import dev.hycolony.core.app.ui.BuildOptionsView.BuilderChoice;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A hut's build options (MC WindowBuildBuilding): its style (MC lists only the hut's own, so the arrows change
 * nothing), the builder, the plan's resources and the order buttons. The builder chosen and a pending confirmation are
 * page state.
 */
public final class BuildOptionsPage extends ColonyPage {
    private final BuildOptionsView view;
    private final Runnable pickUp;
    /** The chosen builder's hut; empty for any builder (MC "Builder:"). */
    private Optional<BlockPos> builder = Optional.empty();
    /**
     * The build button was clicked once despite the upgrade warning: the next click confirms.
     *
     * <p>Deviation from MC: a second click on the button, where MC opens a WindowConfirm (spec SP4).
     */
    private boolean confirming;

    public BuildOptionsPage(PlayerRef playerRef, BuildOptionsView view, ColonyManager manager, Runnable pickUp) {
        super(playerRef, manager);
        this.view = view;
        this.pickUp = pickUp;
    }

    /** Keeps the builder chosen in {@code previous} if it shows the same hut and that builder is still offered. */
    public BuildOptionsPage keepStateOf(@Nullable CustomUIPage previous) {
        if (previous instanceof BuildOptionsPage p
                && p.view.building().pos().equals(view.building().pos())) {
            builder = p.builder.filter(
                    hut -> view.builders().stream().anyMatch(b -> b.hut().equals(hut)));
        }
        return this;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/BuildOptions.ui");
        ui.set(
                "#Style.Entries",
                List.of(new DropdownEntryInfo(LocalizableString.fromString(view.style()), view.style())));
        ui.set("#Style.Value", view.style());
        builders(ui, events);
        resources(ui);
        buttons(ui, events);
        bind(events, "#Close", "close");
    }

    /** MC updateBuilders: "Builder:" (any), then the colony's builders nearest first. */
    private void builders(UICommandBuilder ui, UIEventBuilder events) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("hycolony.ui.buildOptions.anyBuilder"), "0"));
        int chosen = 0;
        for (int i = 0; i < view.builders().size(); i++) {
            BuilderChoice b = view.builders().get(i);
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(b.workerName()), String.valueOf(i + 1)));
            if (builder.filter(b.hut()::equals).isPresent()) {
                chosen = i + 1;
            }
        }
        ui.set("#Builder.Entries", entries);
        ui.set("#Builder.Value", String.valueOf(chosen));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#Builder",
                EventData.of("Action", "builder").append("@Name", "#Builder.Value"),
                false);
    }

    private void resources(UICommandBuilder ui) {
        for (int i = 0; i < view.resources().size(); i++) {
            ItemAmount r = view.resources().get(i);
            String row = "#Resources[" + i + "]";
            ui.append("#Resources", "Pages/HyColony/Mc/ResourceRow.ui");
            ui.set(row + " #Icon.ItemId", r.item().id());
            ui.set(row + " #Name.TextSpans", itemName(r.item().id()));
            ui.set(row + " #Count.Text", String.valueOf(r.count()));
        }
    }

    /**
     * WindowBuildBuilding's buttons: "Build Building" at level 0, Upgrade below the max level (else hidden); Repair,
     * labelled Build once deconstructed; Deconstruct, or Pick Up in its place at level 0, once deconstructed or without
     * a plan; without a plan, no Build nor Repair (MC updateResources).
     */
    private void buttons(UICommandBuilder ui, UIEventBuilder events) {
        BuildingView b = view.building();
        WorkOrderType build = b.allowed().contains(WorkOrderType.BUILD) ? WorkOrderType.BUILD : WorkOrderType.UPGRADE;
        String buildKey = build == WorkOrderType.BUILD ? "hycolony.ui.buildOptions.build" : typeKey(build);
        orderButton(ui, events, "#BuildButton", build, confirming ? "hycolony.ui.build.confirm" : buildKey);
        b.upgradeWarning().ifPresent(warning -> {
            ui.set("#BuildButton.TooltipText", Message.translation(warning));
            if (confirming) {
                ui.set("#Warning.Visible", true);
                ui.set(
                        "#Warning.TextSpans",
                        Message.join(
                                Message.translation("hycolony.ui.build.confirm.title"),
                                Message.raw(" "),
                                Message.translation(warning)));
            }
        });
        // MC: Repair reads "Build Building" (ACTION_BUILD) on a deconstructed hut.
        String repairKey = b.deconstructed() ? "hycolony.ui.buildOptions.build" : typeKey(WorkOrderType.REPAIR);
        orderButton(ui, events, "#RepairButton", WorkOrderType.REPAIR, repairKey);
        if (!view.blueprintFound()) {
            ui.set("#BuildButton.Visible", false);
            ui.set("#RepairButton.Visible", false);
        }
        if (view.showPickUp()) {
            ui.set("#RemoveButton.Visible", false);
            ui.set("#PickUpButton.Visible", true);
            bind(events, "#PickUpButton", "pickUp");
        } else {
            orderButton(ui, events, "#RemoveButton", WorkOrderType.REMOVE, typeKey(WorkOrderType.REMOVE));
        }
    }

    private static String typeKey(WorkOrderType type) {
        return "hycolony.ui.workorder.type." + type.name().toLowerCase(Locale.ROOT);
    }

    private void orderButton(
            UICommandBuilder ui, UIEventBuilder events, String button, WorkOrderType type, String key) {
        if (view.building().allowed().contains(type)) {
            ui.set(button + ".Text", Message.translation(key));
            bind(events, button, "order", type.ordinal()); // the core refuses (with a message) if not allowed
        } else {
            ui.set(button + ".Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "builder" -> builder = choice(act.name());
            case "order" -> order(act.index());
            case "pickUp" -> pickUp.run();
            case "close" ->
                manager.windows().openBuildingGui(player, view.building().pos());
            default -> {}
        }
    }

    /** The builder hut of the dropdown's value; empty (any builder) for "0" or anything unknown. */
    private Optional<BlockPos> choice(String value) {
        try {
            int i = Integer.parseInt(value);
            return i >= 1 && i <= view.builders().size()
                    ? Optional.of(view.builders().get(i - 1).hut())
                    : Optional.empty();
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    /**
     * MC confirmClicked/repairClicked/deconstructBuildingClicked: a build or upgrade the hut warns about first asks
     * for a second click; the order goes to the chosen builder. The core shows the building's window again.
     */
    private void order(int typeIndex) {
        if (typeIndex < 0 || typeIndex >= WorkOrderType.values().length) {
            return;
        }
        WorkOrderType type = WorkOrderType.values()[typeIndex];
        boolean buildButton = type == WorkOrderType.BUILD || type == WorkOrderType.UPGRADE;
        if (buildButton && view.building().upgradeWarning().isPresent() && !confirming) {
            confirming = true;
            rebuild();
            return;
        }
        confirming = false;
        manager.workOrders().order(player, view.building().pos(), type, view.style(), builder);
    }
}
