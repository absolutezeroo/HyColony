package dev.hycolony.plugin.ui;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.BlockPos;
import java.util.UUID;
import org.joml.Matrix4d;
import org.joml.Vector3f;

/**
 * Shows one player where a block is: a glowing cube around it and a beam above it, seen from afar, for
 * {@link #SECONDS}. Drawn with the game's debug shapes (DebugUtils.add, sent to this player only). An offline player
 * sees nothing.
 */
public final class BlockHighlight {
    /** How long the highlight stays, in seconds. */
    private static final float SECONDS = 30f;

    private static final Vector3f COLOR = new Vector3f(1f, 0.78f, 0.2f);
    private static final float OPACITY = 0.45f;
    /** The beam's height above the block, in blocks. */
    private static final double BEAM_HEIGHT = 48;
    /** DebugUtils.FLAG_FADE: the shape fades out at its end. */
    private static final byte FLAG_FADE = 1;

    private BlockHighlight() {}

    /** Highlights {@code pos} for {@code player}. */
    public static void show(UUID player, BlockPos pos) {
        PlayerRef ref = Universe.get().getPlayer(player);
        if (ref == null) {
            return;
        }
        double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;
        Matrix4d cube = new Matrix4d().translate(x, y, z).scale(1.1);
        Matrix4d beam =
                new Matrix4d().translate(x, pos.y() + 1 + BEAM_HEIGHT / 2, z).scale(0.25, BEAM_HEIGHT, 0.25);
        send(ref, DebugShape.Cube, cube);
        send(ref, DebugShape.Cylinder, beam);
    }

    private static void send(PlayerRef ref, DebugShape shape, Matrix4d matrix) {
        ref.getPacketHandler()
                .write(new DisplayDebug(
                        shape, Matrix4dUtil.asFloatData(matrix), COLOR, SECONDS, FLAG_FADE, null, OPACITY));
    }
}
