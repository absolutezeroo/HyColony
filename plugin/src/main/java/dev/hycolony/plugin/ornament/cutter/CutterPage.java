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
import dev.hycolony.core.ornament.cutter.CutterView;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ui.PageEvents;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * The architect's cutter window (MC DO ArchitectsCutterScreen), laid out as Hytale's crafting benches: the core's
 * {@link CutterView} for what the player holds, drawn by {@link CutterDrawing} and redrawn after every action. It opens
 * on the player's last group. World thread.
 */
final class CutterPage extends InteractiveCustomUIPage<CutterPage.Act> {
    /** Crafts one x10 click asks for (Hytale's benches' x10). */
    private static final int BATCH = 10;

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

    /** What the window works on: its world, the ornaments and the players' last groups. */
    record Setup(
            World world,
            OrnamentVariantRegistry registry,
            OrnamentVariantRegistry.Catalogs catalogs,
            CutterGroupMemory memory) {}

    private final Setup setup;
    private final UUID player;
    private final CutterActions actions;

    CutterPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.player = playerRef.getUuid();
        this.actions = new CutterActions(
                CutterCatalog.of(setup.catalogs().shapes()),
                setup.catalogs().materials().tags());
        actions.selectGroup(setup.memory().group(player));
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Cutter.ui");
        CutterDrawing.draw(ui, events, actions.view(CutterInventory.counts(store, ref)));
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
                case "slot" -> actions.selectSlot(act.index);
                case "material" -> choose(ref, store, act.index);
                case "craft" -> craft(ref, store, 1);
                case "craft10" -> craft(ref, store, BATCH);
                case "craftAll" -> craft(ref, store, Integer.MAX_VALUE);
                default -> {
                    return;
                }
            }
            rebuild();
        });
    }

    /** Puts the index-th material listed (what the player holds now) in the selected slot; a stale index does nothing. */
    private void choose(Ref<EntityStore> ref, Store<EntityStore> store, int index) {
        var materials = actions.view(CutterInventory.counts(store, ref)).materials();
        if (index >= 0 && index < materials.size()) {
            actions.choose(materials.get(index).itemId());
        }
    }

    /** Asks for up to crafts crafts of the chosen shape; CutterCrafting caps them by what the player holds. */
    private void craft(Ref<EntityStore> ref, Store<EntityStore> store, int crafts) {
        Map<String, Integer> inventory = CutterInventory.counts(store, ref);
        actions.shape()
                .ifPresent(shape -> CutterCrafting.craft(new CutterCrafting.Request(
                        setup.world(),
                        ref,
                        shape,
                        actions.slots(inventory).stream()
                                .map(slot -> slot.isEmpty() ? "" : slot.itemId())
                                .toList(),
                        setup.catalogs().materials().tags(),
                        setup.registry(),
                        crafts,
                        // Up to 30 s later: redraw only if the player still looks at this page.
                        this::redrawIfShown)));
    }

    /** Redraws while the player still looks at this page in this world (a late craft); otherwise does nothing. */
    private void redrawIfShown() {
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
