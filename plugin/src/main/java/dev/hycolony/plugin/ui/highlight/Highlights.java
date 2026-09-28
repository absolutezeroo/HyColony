package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4d;
import org.joml.Vector3f;

/**
 * The highlights players asked for, one at a time each, for {@link #MILLIS}: its boxes drawn with the game's debug
 * shapes (DebugUtils.add, sent to that player only) and its map marker ({@link HighlightMarkers}), which walls do not
 * hide. Turning one off, or showing another, clears every debug shape of the player (ClearDebugShapes has no
 * target), which HyColony uses for nothing else. Read by the world map thread, hence the concurrent map.
 */
public final class Highlights {
    /** How long a highlight lasts, in milliseconds. */
    private static final long MILLIS = 60_000;
    /** The shapes' colour: gold. */
    private static final Vector3f COLOR = new Vector3f(1f, 0.78f, 0.2f);
    /** The solid shapes' opacity. */
    private static final float OPACITY = 0.45f;
    /** DebugUtils.FLAG_FADE: the shape fades out at its end. */
    private static final byte FADE = 1;
    /** DebugUtils.FLAG_FADE | FLAG_NO_SOLID: the edges only. */
    private static final byte OUTLINE = 1 | 4;

    /** A player's highlight, the world it was asked in and until when (epoch ms). */
    record Active(Highlight highlight, UUID world, long until) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private Highlights() {}

    /**
     * Shows {@code h} to {@code player}, or turns it off when it is the one shown; a player offline or between worlds
     * gets nothing.
     */
    public static void toggle(UUID player, Highlight h) {
        PlayerRef ref = Universe.get().getPlayer(player);
        UUID world = ref == null ? null : ref.getWorldUuid();
        if (ref == null || world == null) {
            return;
        }
        if (isActive(player, h.anchor())) {
            ACTIVE.remove(player);
            ref.getPacketHandler().write(new ClearDebugShapes());
            return;
        }
        if (ACTIVE.put(player, new Active(h, world, System.currentTimeMillis() + MILLIS)) != null) {
            ref.getPacketHandler().write(new ClearDebugShapes()); // the previous highlight's shapes
        }
        for (Highlight.Box b : h.boxes()) {
            send(ref, b);
        }
    }

    /** True while {@code player}'s highlight of {@code anchor} lasts. */
    public static boolean isActive(UUID player, BlockPos anchor) {
        return active(player).filter(a -> a.highlight().anchor().equals(anchor)).isPresent();
    }

    /** {@code player}'s highlight while it lasts; a finished one is dropped. */
    static Optional<Active> active(UUID player) {
        Active a = ACTIVE.get(player);
        if (a != null && a.until() < System.currentTimeMillis()) {
            ACTIVE.remove(player, a);
            return Optional.empty();
        }
        return Optional.ofNullable(a);
    }

    /** One box, as DebugUtils builds its matrices (translate, then scale). */
    private static void send(PlayerRef ref, Highlight.Box b) {
        Matrix4d m = new Matrix4d().translate(b.x(), b.y(), b.z()).scale(b.sx(), b.sy(), b.sz());
        DebugShape shape = b.style() == Highlight.Style.BEAM ? DebugShape.Cylinder : DebugShape.Cube;
        ref.getPacketHandler()
                .write(new DisplayDebug(
                        shape,
                        Matrix4dUtil.asFloatData(m),
                        COLOR,
                        MILLIS / 1000f,
                        b.style() == Highlight.Style.OUTLINE ? OUTLINE : FADE,
                        null,
                        OPACITY));
    }
}
