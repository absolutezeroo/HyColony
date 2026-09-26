package dev.hycolony.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.colony.ColonyManager;
import java.util.UUID;

/**
 * A HyColony window: buttons send {@code Action} (+ {@code Index} for list rows) and call ColonyManager, which
 * re-shows a fresh snapshot. Buttons do not lock the interface, so no "unlock" update is ever needed; a re-shown
 * page waits for the client's acknowledgement, which drops double clicks. World thread only.
 */
abstract class ColonyPage extends InteractiveCustomUIPage<ColonyPage.Act> {
    public static final class Act {
        static final BuilderCodec<Act> CODEC = BuilderCodec.builder(Act.class, Act::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (d, v) -> d.index = parse(v), d -> String.valueOf(d.index)).add()
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

    protected final ColonyManager manager;
    protected final UUID player;

    ColonyPage(PlayerRef playerRef, ColonyManager manager) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.manager = manager;
        this.player = playerRef.getUuid();
    }

    static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    static void bind(UIEventBuilder events, String selector, String action, int index) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Index", String.valueOf(index)), false);
    }

    /** "hycolony:builder" -> hycolony.ui.building.type.builder; a custom name stays as is. */
    static Message buildingName(String typeIdOrName) {
        return typeIdOrName.startsWith("hycolony:")
                ? Message.translation("hycolony.ui.building.type." + typeIdOrName.substring("hycolony:".length()))
                : Message.raw(typeIdOrName);
    }

    /** "hycolony:builder" -> hycolony.ui.job.builder; no job -> hycolony.ui.job.none. */
    static Message jobName(String jobId) {
        return jobId.isEmpty() ? Message.translation("hycolony.ui.job.none")
                : Message.translation("hycolony.ui.job." + jobId.substring(jobId.indexOf(':') + 1));
    }

    static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : item.getTranslationMessage();
    }
}
