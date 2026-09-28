package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.cutter.CutterActions;
import dev.hycolony.core.ornament.cutter.CutterCatalog;
import dev.hycolony.core.ornament.cutter.CutterView;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ui.PageEvents;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The architect's cutter window (MC DO ArchitectsCutterScreen): group tabs, the group's shapes, a label per slot and
 * the preview, redrawn from the core's {@link CutterView} after every action and whenever the slots change. It opens
 * on the player's last group. World thread.
 */
final class CutterPage extends InteractiveCustomUIPage<CutterPage.Act> {
    /** A button's event: its action and list index, as ColonyPage.Act. */
    static final class Act {
        static final BuilderCodec<Act> CODEC = BuilderCodec.builder(Act.class, Act::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(
                        new KeyedCodec<>("Index", Codec.STRING),
                        (d, v) -> d.index = parse(v),
                        d -> String.valueOf(d.index))
                .add()
                .build();
        String action = "";
        int index = -1;

        private static int parse(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    /** What the window works on: its world, the block's slots, the ornaments and the players' last groups. */
    record Setup(
            World world,
            ItemContainer slots,
            OrnamentVariantRegistry registry,
            OrnamentVariantRegistry.Catalogs catalogs,
            CutterGroupMemory memory) {}

    private final Setup setup;
    private final UUID player;
    private final CutterActions actions;
    private final CutterFollower follower;
    private @Nullable ContainerBlockWindow window;

    CutterPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.player = playerRef.getUuid();
        this.actions = new CutterActions(
                CutterCatalog.of(setup.catalogs().shapes()),
                setup.catalogs().materials().tags());
        actions.selectGroup(setup.memory().group(player));
        this.follower = new CutterFollower(playerRef, setup.world(), setup.slots(), this, this::rebuild);
    }

    /** The player this window belongs to. */
    UUID player() {
        return player;
    }

    /** Called once the page and its window are open: the preview then follows the slots (CutterFollower). */
    void attach(ContainerBlockWindow opened) {
        this.window = opened;
        follower.start();
    }

    /** Stops following the slots and forgets the window (it closed or is closing); safe to call more than once. */
    void detach() {
        follower.stop();
        window = null;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Cutter.ui");
        CutterView view = actions.view(CutterSlots.read(setup.slots()));
        tabs(ui, events, view.tabs());
        shapes(ui, events, view.shapes());
        for (int i = 0; i < CutterSlots.COUNT; i++) {
            boolean shown = i < view.slotLabelKeys().size();
            ui.set("#Slot" + i + ".Visible", shown);
            if (shown) {
                ui.set(
                        "#Slot" + i + ".Text",
                        Message.translation(view.slotLabelKeys().get(i)));
            }
        }
        preview(ui, events, view.preview());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        PageEvents.guard(getClass(), () -> {
            switch (act.action) {
                case "group" -> {
                    actions.selectGroup(act.index);
                    setup.memory().remember(player, actions.group());
                }
                case "shape" -> actions.selectShape(act.index);
                case "craft" ->
                    actions.shape()
                            .ifPresent(shape -> CutterCrafting.craft(new CutterCrafting.Request(
                                    setup.world(),
                                    ref,
                                    setup.slots(),
                                    shape,
                                    setup.catalogs().materials().tags(),
                                    setup.registry(),
                                    // Up to 30 s later: redraw only if the player still looks at this page.
                                    follower::redrawIfShown)));
                default -> {
                    return;
                }
            }
            rebuild();
        });
    }

    /**
     * Another page replaced this one, or it was closed: stop following the slots and close their window too, if the
     * player still holds it (the client may have closed it first, or the block broke). Never throws.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ContainerBlockWindow opened = window;
        detach();
        if (opened != null) {
            CutterOpener.closeIfOpen(ref, store, opened);
        }
        super.onDismiss(ref, store);
    }

    /** One tab button per group, the open one disabled. */
    private static void tabs(UICommandBuilder ui, UIEventBuilder events, List<CutterView.Tab> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            String button = "#TabButtons[" + i + "]";
            ui.append("#TabButtons", "Pages/HyColony/TabButton.ui");
            ui.set(button + ".Text", Message.translation(tabs.get(i).nameKey()));
            ui.set(button + ".Disabled", tabs.get(i).selected());
            bind(events, button, "group", i);
        }
    }

    /** One icon button per shape of the open group, the chosen one disabled and named. */
    private static void shapes(UICommandBuilder ui, UIEventBuilder events, List<CutterView.ShapeButton> shapes) {
        for (int i = 0; i < shapes.size(); i++) {
            CutterView.ShapeButton shape = shapes.get(i);
            String button = "#Shapes[" + i + "]";
            ui.append("#Shapes", "Pages/HyColony/CutterShapeButton.ui");
            ui.set(button + " #Icon.ItemId", shape.templateKey());
            ui.set(button + ".Disabled", shape.selected());
            bind(events, button, "shape", i);
            if (shape.selected()) {
                ui.set("#ShapeName.Text", itemName(shape.templateKey()));
            }
        }
    }

    /** The preview icon and text, and the craft button, enabled only when crafting is possible. */
    private static void preview(UICommandBuilder ui, UIEventBuilder events, CutterView.Preview preview) {
        switch (preview) {
            case CutterView.Empty _ -> {
                ui.set("#Preview.Visible", false);
                ui.set("#PreviewText.Text", Message.translation("hycolony.ornament.cutter.placeMaterials"));
            }
            case CutterView.Ready ready -> {
                // Deviation from MC: the template's icon until the variant exists (its icon is painted on creation).
                boolean exists = Item.getAssetMap().getAsset(ready.itemId()) != null;
                ui.set("#Preview.ItemId", exists ? ready.itemId() : ready.templateKey());
                ui.set(
                        "#PreviewText.Text",
                        Message.translation("hycolony.ornament.cutter.quantity")
                                .param("p0", String.valueOf(ready.quantity())));
            }
            case CutterView.Refused refused -> {
                ui.set("#Preview.Visible", false);
                ui.set(
                        "#PreviewText.Text",
                        HytaleNotifier.toMessage(
                                Msg.of(refused.reasonKey(), refused.params().toArray(String[]::new))));
            }
        }
        ui.set("#CraftButton.Disabled", !(preview instanceof CutterView.Ready));
        bind(events, "#CraftButton", "craft", -1);
    }

    /** The item's translated name; its id when it is not loaded. */
    static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : item.getTranslationMessage();
    }

    /** Binds a button's click to an action and its list index. */
    private static void bind(UIEventBuilder events, String selector, String action, int index) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action).append("Index", String.valueOf(index)),
                false);
    }
}
