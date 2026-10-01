package dev.hycolony.plugin.ui;

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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.InventoryDrop;
import dev.hyblockui.api.PageEvents;
import dev.hycolony.api.read.JobNames;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A HyColony window: buttons send {@code Action} (+ {@code Index} for a row or a number, {@code Ref} for a row a live
 * refresh may move, {@code @Name} for a field's value) and call ColonyManager, which
 * re-shows a fresh snapshot. Buttons do not lock the interface, so no "unlock" update is ever needed; a re-shown
 * page waits for the client's acknowledgement, which drops double clicks. World thread only. Public for the window
 * sub-packages.
 */
public abstract class ColonyPage extends InteractiveCustomUIPage<ColonyPage.Act> {
    public static final class Act {
        static final BuilderCodec<Act> CODEC = InventoryDrop.appendTo(
                        BuilderCodec.builder(Act.class, Act::new)
                                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                                .add()
                                .append(
                                        new KeyedCodec<>("Index", Codec.STRING),
                                        (d, v) -> d.index = parse(v),
                                        d -> String.valueOf(d.index))
                                .add()
                                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name)
                                .add()
                                .append(new KeyedCodec<>("Ref", Codec.STRING), (d, v) -> d.ref = v, d -> d.ref)
                                .add(),
                        d -> d.drop)
                .build();
        String action = "";
        int index = -1;
        /** A text field's value, sent as {@code @Name}; empty when the event carries none. */
        String name = "";
        /**
         * A stable id of what the event acts on (a player's UUID, a rank id), sent as {@code Ref}: a window not redrawn
         * by a live refresh still names the right one, where an index would point into the new list. Empty when the
         * event carries none.
         */
        String ref = "";
        /** An item dropped on one of the window's inventory grids (InventoryGrids); empty for other events. */
        final InventoryDrop drop = new InventoryDrop();

        public String action() {
            return action;
        }

        public int index() {
            return index;
        }

        public String name() {
            return name;
        }

        public String ref() {
            return ref;
        }

        public InventoryDrop drop() {
            return drop;
        }

        private static int parse(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    protected final ColonyManager manager;
    protected final UUID player;
    /** The page answering for this one since the last live refresh; null until then. */
    @Nullable
    private ColonyPage successor;

    protected ColonyPage(PlayerRef playerRef, ColonyManager manager) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.manager = manager;
        this.player = playerRef.getUuid();
    }

    /** The page that draws and answers this window now (see {@link #refreshWith}). */
    public final ColonyPage live() {
        return successor == null ? this : successor;
    }

    /**
     * Redraws this open window in place with {@code fresh}'s content, which from now on answers its events. Unlike
     * {@code openCustomPage}, the client gets a non-initial update (as a tab change), so the window is not reopened.
     */
    public final void refreshWith(ColonyPage fresh) {
        boolean redraw = !live().showsInput();
        successor = fresh;
        if (redraw) {
            fresh.rebuild();
        }
    }

    /**
     * True while a text field is shown: a redraw would reset what the player is typing, so a live refresh only swaps
     * the page answering events and the new content shows at the next redraw (a tab change or an action).
     */
    protected boolean showsInput() {
        return false;
    }

    /** Every HyColony window event enters here: routed to the live page, a failure is logged, never thrown. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String rawData) {
        PageEvents.guard(getClass(), () -> {
            if (successor == null) {
                super.handleDataEvent(ref, store, rawData);
            } else {
                successor.handleDataEvent(ref, store, rawData);
            }
        });
    }

    public static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    /** A button whose event names its row by a stable id ({@link Act#ref}). */
    public static void bindRef(UIEventBuilder events, String selector, String action, String ref) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action).append("Ref", ref),
                false);
    }

    public static void bind(UIEventBuilder events, String selector, String action, int index) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Action", action).append("Index", String.valueOf(index)),
                false);
    }

    /** "hycolony:builder" -> hycolony.ui.building.type.builder; a custom name stays as is. */
    public static Message buildingName(String typeIdOrName) {
        return typeIdOrName.startsWith("hycolony:")
                ? Message.translation("hycolony.ui.building.type." + typeIdOrName.substring("hycolony:".length()))
                : Message.raw(typeIdOrName);
    }

    /** The job's name ({@link JobNames}): "hycolony:builder" names hycolony.ui.job.builder, "" no job. */
    public static Message jobName(String jobId) {
        return Message.translation(
                JobNames.of(Optional.of(jobId).filter(j -> !j.isEmpty())).key());
    }

    /** The translated work order type ("Build", "Upgrade"...). */
    public static Message workOrderTypeName(WorkOrderType type) {
        return Message.translation("hycolony.ui.workorder.type." + type.name().toLowerCase(Locale.ROOT));
    }

    public static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : item.getTranslationMessage();
    }
}
