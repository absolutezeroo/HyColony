package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/** A hut's window: level, work order, workers, style, resources, storage and pick-up. */
public final class BuildingPage extends ColonyPage {
    private final BuildingView view;
    private final Runnable pickUp;
    /** Local choice sent with the next order; the core has no "set style" action. */
    private int styleIndex;
    /** OPEN_CONTAINER, as for any chest in the colony: the storage button is hidden without it. */
    private final boolean canOpenStorage;

    public BuildingPage(PlayerRef playerRef, BuildingView view, ColonyManager manager, Runnable pickUp) {
        super(playerRef, manager);
        this.view = view;
        this.pickUp = pickUp;
        this.styleIndex = Math.max(0, view.styles().indexOf(view.style()));
        this.canOpenStorage = mayOpenStorage();
    }

    /** Same rule as ProtectionSystems' OPEN_CONTAINER check. */
    private boolean mayOpenStorage() {
        return !manager.protectionEnabled() || manager.isAllowed(player, view.pos(), Action.OPEN_CONTAINER);
    }

    private String style() {
        return view.styles().isEmpty() ? view.style() : view.styles().get(styleIndex);
    }

    private static Message typeName(WorkOrderType type) {
        return Message.translation("hycolony.ui.workorder.type." + type.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Building.ui");
        ui.set("#TypeName.Text", buildingName(view.typeId()));
        ui.set(
                "#Level.Text",
                Message.translation("hycolony.ui.building.level")
                        .param("p0", String.valueOf(view.level()))
                        .param("p1", String.valueOf(view.maxLevel())));
        String state = view.deconstructed() ? "deconstructed" : view.built() ? "built" : "notBuilt";
        ui.set("#State.Text", Message.translation("hycolony.ui.building.state." + state));

        if (view.order().isPresent()) {
            BuildingView.OrderRow o = view.order().get();
            ui.set(
                    "#OrderInfo.TextSpans",
                    Message.translation("hycolony.ui.building.order")
                            .param("p0", typeName(o.type()))
                            .param("p1", String.valueOf(o.targetLevel()))
                            .param(
                                    "p2",
                                    o.builderName()
                                            .map(Message::raw)
                                            .orElse(Message.translation("hycolony.ui.building.noBuilder")))
                            .param("p3", String.valueOf(o.percent())));
        } else {
            ui.set("#OrderInfo.Text", Message.translation("hycolony.ui.building.noOrder"));
        }
        for (WorkOrderType type : WorkOrderType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            String button = "#" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Button";
            if (view.allowed().contains(type)) {
                bind(events, button, "order", type.ordinal()); // the core refuses (with a message) if not allowed
            } else {
                ui.set(button + ".Visible", false);
            }
        }
        if (view.order().isPresent() && view.canManage()) {
            bind(events, "#CancelButton", "cancel");
        } else {
            ui.set("#CancelButton.Visible", false);
        }

        if (view.allowed().isEmpty() || view.styles().size() < 2) {
            ui.set("#StyleRow.Visible", false);
        } else {
            ui.set("#StyleButton.Text", style());
            bind(events, "#StyleButton", "style");
        }

        if (view.hiringMode().isPresent()) {
            // A button's Text renders no nested message: one full key per mode.
            ui.set(
                    "#HiringButton.Text",
                    Message.translation("hycolony.ui.building.hiring."
                            + view.hiringMode().get().name().toLowerCase(Locale.ROOT)));
            if (view.canManage()) {
                bind(events, "#HiringButton", "hiring");
            } else {
                ui.set("#HiringButton.Disabled", true);
            }
            rows(ui, events, "#Workers", view.workers(), "fire", "hycolony.ui.building.fire");
            if (view.canManage()) {
                rows(ui, events, "#Hireable", view.hireable(), "hire", "hycolony.ui.building.hire");
            } else {
                ui.set("#HireSection.Visible", false);
            }
        } else {
            ui.set("#HiringButton.Visible", false);
            ui.set("#WorkerSection.Visible", false);
            ui.set("#HireSection.Visible", false);
        }

        if (view.typeId().equals(ConstructionBuildingTypes.BUILDER.id())) {
            bind(events, "#ResourcesButton", "resources");
        } else {
            ui.set("#ResourcesButton.Visible", false);
        }
        if (canOpenStorage) {
            bind(events, "#StorageButton", "storage");
        } else {
            ui.set("#StorageButton.Visible", false);
        }
        if (view.canPickUp()) {
            bind(events, "#PickUpButton", "pickUp");
        } else {
            ui.set("#PickUpButton.Visible", false);
        }
    }

    private void rows(
            UICommandBuilder ui,
            UIEventBuilder events,
            String list,
            List<BuildingView.WorkerRow> rows,
            String action,
            String buttonKey) {
        for (int i = 0; i < rows.size(); i++) {
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/WorkerRow.ui");
            ui.set(row + " #Name.Text", rows.get(i).name());
            if (view.canManage()) {
                ui.set(row + " #Button.Text", Message.translation(buttonKey));
                bind(events, row + " #Button", action, i);
            } else {
                ui.set(row + " #Button.Visible", false);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        BlockPos pos = view.pos();
        int i = act.index;
        switch (act.action) {
            // A refusal is already sent to the player by the core (hycolony.workorder.refused.*).
            case "order" -> {
                if (i >= 0 && i < WorkOrderType.values().length) {
                    manager.workOrders().order(player, pos, WorkOrderType.values()[i], style());
                }
            }
            case "cancel" -> manager.workOrders().cancel(player, pos);
            case "style" -> {
                if (view.styles().size() < 2) {
                    return; // the button is hidden then: a forged event
                }
                styleIndex = (styleIndex + 1) % view.styles().size();
                UICommandBuilder ui = new UICommandBuilder();
                ui.set("#StyleButton.Text", style());
                sendUpdate(ui, false);
            }
            case "hiring" ->
                view.hiringMode()
                        .ifPresent(m -> manager.huts()
                                .setHiring(
                                        player,
                                        pos,
                                        HiringMode.values()[(m.ordinal() + 1) % HiringMode.values().length]));
            case "fire" -> {
                if (i >= 0 && i < view.workers().size()) {
                    manager.huts().fire(player, pos, view.workers().get(i).citizenId());
                }
            }
            case "hire" -> {
                if (i >= 0 && i < view.hireable().size()) {
                    manager.huts().hire(player, pos, view.hireable().get(i).citizenId());
                }
            }
            case "resources" -> manager.windows().openBuilderResources(player, pos);
            case "storage" -> openStorage(ref, store);
            case "pickUp" -> pickUp.run();
            default -> {}
        }
    }

    /**
     * The hut's own container, as vanilla OpenContainerInteraction opens it. On close the window leaves the block's
     * window table (or it never opens again) and the core re-checks the building's stuck requests.
     */
    private void openStorage(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!mayOpenStorage()) { // checked again: ranks may have changed since the page was built
            manager.colonyAt(view.pos())
                    .ifPresent(c -> playerRef.sendMessage(
                            HytaleNotifier.toMessage(Msg.of("hycolony.permission.denied", c.name()))));
            return;
        }
        World world = store.getExternalData().getWorld();
        BlockPos p = view.pos();
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        ItemContainerBlock container =
                BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
        if (section == null || container == null) {
            playerRef.sendMessage(Message.translation("hycolony.ui.building.noStorage"));
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type = blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(p.x(), p.y(), p.z()));
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (type == null || playerComponent == null) {
            return;
        }
        ContainerBlockWindow window = new ContainerBlockWindow(
                p.x(), p.y(), p.z(), blocks.getRotationIndex(p.x(), p.y(), p.z()), type, container.getItemContainer());
        Map<UUID, ContainerBlockWindow> windows = container.getWindows();
        if (windows.putIfAbsent(player, window) != null) {
            return; // already open
        }
        if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            window.registerCloseEvent(e -> {
                windows.remove(player, window);
                world.execute(() -> manager.requestActions().onContainerChanged(p));
            });
        } else {
            windows.remove(player, window);
        }
    }
}
