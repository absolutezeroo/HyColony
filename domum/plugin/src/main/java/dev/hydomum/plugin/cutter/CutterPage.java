package dev.hydomum.plugin.cutter;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.InventoryDrop;
import dev.hyblockui.api.InventoryGrids;
import dev.hyblockui.api.InventoryMoves;
import dev.hyblockui.api.InventoryWatch;
import dev.hyblockui.api.PageEvents;
import dev.hyblockui.api.PageRedraw;
import dev.hyblockui.api.PlayerPanels;
import dev.hyblockui.api.PlayerSection;
import dev.hyblockui.api.ReturningContainerWindow;
import dev.hyblockui.api.UiSounds;
import dev.hydomum.core.cutter.CutterActions;
import dev.hydomum.core.cutter.CutterCatalog;
import dev.hydomum.plugin.registry.OrnamentVariantRegistry;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The architect's cutter window (MC DO ArchitectsCutterScreen), laid out as Hytale's crafting benches: tabs, shapes,
 * the player's 2 material slots and the preview above, their character and inventory below, which they drag materials
 * from. Redrawn from the core's view after every action and whenever the slots or the inventory change; it opens on
 * the player's last group. World thread.
 */
final class CutterPage extends InteractiveCustomUIPage<CutterPage.Act> {
    /** Crafts one x10 click asks for (Hytale's benches' x10). */
    private static final int BATCH = 10;

    /** The slot grid's selector, also its grid name in drop events. */
    static final String SLOTS_GRID = "#CutterSlots";

    /** A button's event: its action and list index, as ColonyPage.Act, or an inventory drop. */
    static final class Act {
        static final BuilderCodec<Act> CODEC = InventoryDrop.appendTo(
                        BuilderCodec.builder(Act.class, Act::new)
                                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                                .add()
                                .append(
                                        new KeyedCodec<>("Index", Codec.STRING),
                                        (d, v) -> d.index = parse(v),
                                        d -> String.valueOf(d.index))
                                .add(),
                        d -> d.drop)
                .build();
        String action = "";
        int index = -1;
        final InventoryDrop drop = new InventoryDrop();

        private static int parse(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    /**
     * What the window works on: its world, the shared cutter settings (ornaments, players' last groups, craft time in
     * milliseconds from HyDomum.CutterCraftSeconds, sounds), and the loaded catalogs.
     */
    record Setup(World world, CutterSettings settings, OrnamentVariantRegistry.Catalogs catalogs) {}

    private final Setup setup;

    private final UUID player;
    private final CutterActions actions;
    private final CutterSlots slots;
    private @Nullable InventoryWatch watch;
    private final PageRedraw redraw;
    private final CutterPreviewVariants previews;
    private final CutterCraftClicks crafts;

    CutterPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.player = playerRef.getUuid();
        this.actions = new CutterActions(
                CutterCatalog.of(setup.catalogs().shapes()),
                setup.catalogs().materials().tags());
        actions.selectGroup(setup.settings().memory().group(player));
        this.slots = new CutterSlots(actions::accepts);
        // Nothing once the slots' window has closed: the page is gone or the player is leaving.
        this.redraw = new PageRedraw(
                setup.world(), this::redrawIfShown, () -> !slots.window().isClosed());
        this.previews = new CutterPreviewVariants(
                setup.world(),
                setup.settings().registry(),
                redraw::soon,
                () -> !slots.window().isClosed());
        this.crafts = new CutterCraftClicks(
                setup,
                actions,
                slots,
                setup.settings().craftMillis() <= 0
                        ? null
                        : new CutterCraftQueue(
                                setup.world(),
                                setup.settings().craftMillis(),
                                () -> !slots.window().isClosed(),
                                this::showProgress),
                redraw::soon);
    }

    /** The slots' window, to open with this page. */
    ReturningContainerWindow slotsWindow() {
        return slots.window();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        if (watch == null) {
            watch = InventoryWatch.start(store, ref, redraw::soon, slots.container());
        }
        ui.append("Pages/HyDomum/Cutter.ui");
        previews.prepare(actions.groupVariants(slots.contents())); // first: the preview shows whether it waits
        CutterDrawing.draw(
                ui,
                events,
                actions.view(slots.contents(), CutterCrafting.creative(store, ref)),
                previews.preparing(),
                crafts.busy());
        ui.set("#CraftProgress.Value", (float) crafts.progress()); // a redraw mid-craft keeps the bar where it is
        InventoryGrids.drawContainer(
                ui, events, SLOTS_GRID, slots.container(), slots.window().getId());
        PlayerPanels.drawCharacter(ui, events, "#Character", store, ref);
        PlayerPanels.drawStorage(ui, events, "#Storage", store, ref);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        PageEvents.guard(getClass(), () -> {
            switch (act.action) {
                case "group" -> {
                    actions.selectGroup(act.index);
                    setup.settings().memory().remember(player, actions.group());
                }
                case "shape" -> actions.selectShape(act.index);
                case InventoryGrids.DROP_ACTION -> {
                    // No redraw here: the move changes the containers, and the watch redraws once.
                    drop(ref, store, act.drop);
                    return;
                }
                case "craft" -> crafts.craft(ref, store, 1);
                case "craft10" -> crafts.craft(ref, store, BATCH);
                case "craftAll" -> crafts.craft(ref, store, Integer.MAX_VALUE);
                default -> {
                    return;
                }
            }
            rebuild();
        });
    }

    /**
     * Stops following the inventory, closes the slots' window, which gives their content back, and plays the close
     * sound. Never throws.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryWatch current = watch;
        watch = null;
        if (current != null) {
            current.stop();
        }
        slots.window().closeLater(ref);
        UiSounds.play(playerRef, setup.settings().closeSound()); // as a bench's LocalCloseSoundEventId
        super.onDismiss(ref, store);
    }

    /** Moves what the player dropped on the slot grid or their own grids; a drop elsewhere does nothing. */
    private void drop(Ref<EntityStore> ref, Store<EntityStore> store, InventoryDrop drop) {
        if (SLOTS_GRID.equals(drop.grid())) {
            InventoryMoves.apply(ref, store, drop, slots.window().getId());
            return;
        }
        PlayerSection.byGrid(drop.grid()).ifPresent(part -> InventoryMoves.apply(ref, store, drop, part.id()));
    }

    /** Moves the craft bar to progress (0-1) in place, without redrawing the page, while it is shown. */
    private void showProgress(double progress) {
        if (isShown()) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#CraftProgress.Value", (float) progress);
            sendUpdate(ui, false);
        }
    }

    /** Redraws while the player still looks at this page in this world; otherwise does nothing. */
    private void redrawIfShown() {
        if (isShown()) {
            rebuild();
        }
    }

    /** Whether the player still looks at this page, in this world, with its slots open. */
    private boolean isShown() {
        if (slots.window().isClosed()) {
            return false;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null
                || !ref.isValid()
                || !setup.world().equals(ref.getStore().getExternalData().getWorld())) {
            return false;
        }
        Player shown = ref.getStore().getComponent(ref, Player.getComponentType());
        return shown != null && equals(shown.getPageManager().getCustomPage());
    }
}
