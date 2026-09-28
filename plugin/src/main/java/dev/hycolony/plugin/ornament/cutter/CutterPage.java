package dev.hycolony.plugin.ornament.cutter;

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
import dev.hycolony.core.ornament.cutter.CutterActions;
import dev.hycolony.core.ornament.cutter.CutterCatalog;
import dev.hycolony.plugin.inventory.InventoryDrop;
import dev.hycolony.plugin.inventory.InventoryGrids;
import dev.hycolony.plugin.inventory.InventoryMoves;
import dev.hycolony.plugin.inventory.InventoryWatch;
import dev.hycolony.plugin.inventory.PlayerPanels;
import dev.hycolony.plugin.inventory.PlayerSection;
import dev.hycolony.plugin.inventory.ReturningContainerWindow;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ui.PageEvents;
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

    /** What the window works on: its world, the ornaments and the players' last groups. */
    record Setup(
            World world,
            OrnamentVariantRegistry registry,
            OrnamentVariantRegistry.Catalogs catalogs,
            CutterGroupMemory memory) {}

    private final Setup setup;
    private final UUID player;
    private final CutterActions actions;
    private final CutterSlots slots;
    private @Nullable InventoryWatch watch;
    private boolean redrawPending;

    CutterPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.player = playerRef.getUuid();
        this.actions = new CutterActions(
                CutterCatalog.of(setup.catalogs().shapes()),
                setup.catalogs().materials().tags());
        actions.selectGroup(setup.memory().group(player));
        this.slots = new CutterSlots(actions::accepts);
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
            watch = InventoryWatch.start(store, ref, this::redrawSoon, slots.container());
        }
        ui.append("Pages/HyColony/Cutter.ui");
        CutterDrawing.draw(ui, events, actions.view(slots.contents(), CutterCrafting.creative(store, ref)));
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
                    setup.memory().remember(player, actions.group());
                }
                case "shape" -> actions.selectShape(act.index);
                case InventoryGrids.DROP_ACTION -> {
                    // No redraw here: the move changes the containers, and the watch redraws once.
                    drop(ref, store, act.drop);
                    return;
                }
                case "craft" -> craft(ref, 1);
                case "craft10" -> craft(ref, BATCH);
                case "craftAll" -> craft(ref, Integer.MAX_VALUE);
                default -> {
                    return;
                }
            }
            rebuild();
        });
    }

    /** Stops following the inventory and closes the slots' window, which gives their content back. Never throws. */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryWatch current = watch;
        watch = null;
        if (current != null) {
            current.stop();
        }
        slots.window().closeIfOpen(ref, store);
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

    /** Asks for up to crafts crafts of the chosen shape; CutterCrafting caps them by what the slots hold. */
    private void craft(Ref<EntityStore> ref, int crafts) {
        actions.shape()
                .ifPresent(shape -> CutterCrafting.craft(new CutterCrafting.Request(
                        setup.world(),
                        ref,
                        shape,
                        slots,
                        setup.catalogs().materials().tags(),
                        setup.registry(),
                        crafts,
                        // Up to 30 s later: redraw only if the player still looks at this page.
                        this::redrawSoon)));
    }

    /**
     * Redraws once, later on the world thread, however many changes come before (a craft or a drop changes several
     * containers); nothing once the slots' window has closed (the page is gone or the player is leaving).
     */
    private void redrawSoon() {
        if (redrawPending || slots.window().isClosed()) {
            return;
        }
        redrawPending = true;
        try {
            setup.world().execute(() -> {
                redrawPending = false;
                PageEvents.guard(getClass(), this::redrawIfShown);
            });
        } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing to redraw
            redrawPending = false;
        }
    }

    /** Redraws while the player still looks at this page in this world; otherwise does nothing. */
    private void redrawIfShown() {
        if (slots.window().isClosed()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null
                || !ref.isValid()
                || !setup.world().equals(ref.getStore().getExternalData().getWorld())) {
            return;
        }
        Player shown = ref.getStore().getComponent(ref, Player.getComponentType());
        if (shown != null && equals(shown.getPageManager().getCustomPage())) {
            rebuild();
        }
    }
}
