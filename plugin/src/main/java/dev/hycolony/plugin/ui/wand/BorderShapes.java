package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import dev.hycolony.core.app.wand.ColonyBorder;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3f;

/**
 * The colony borders as shapes for one player only (DisplayDebug on their packet handler, as HyLens's ShapePackets:
 * DebugUtils would show them to the whole world). A line is a thin cylinder turned from Y to its direction, as
 * DebugUtils.addLine builds it.
 *
 * <p>Deviation from MC: DisplayDebug has no line, so cylinders {@link #LINE_WIDTH} wide at {@link #OPACITY}, where MC
 * draws opaque one-pixel lines.
 */
final class BorderShapes {
    private static final double LINE_WIDTH = 0.1;
    private static final float OPACITY = 0.8f;
    /** MC's red for the other colonies: (255, 70, 70). */
    private static final Vector3f RED = new Vector3f(1f, 70 / 255f, 70 / 255f);

    private BorderShapes() {}

    /** Sends {@code lines} to {@code out}, each lasting {@code seconds}. */
    static void send(PacketHandler out, List<ColonyBorder.Line> lines, float seconds) {
        Matrix4d m = new Matrix4d();
        Quaterniond turn = new Quaterniond();
        for (ColonyBorder.Line l : lines) {
            BlockPos a = l.from();
            BlockPos b = l.to();
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            m.identity()
                    .translate((a.x() + b.x()) / 2.0, (a.y() + b.y()) / 2.0, (a.z() + b.z()) / 2.0)
                    .rotate(turn.rotationTo(0, 1, 0, dx, dy, dz))
                    .scale(LINE_WIDTH, length, LINE_WIDTH);
            Vector3f colour = l.colour() == ColonyBorder.Colour.RED ? RED : DebugUtils.COLOR_WHITE;
            out.write(new DisplayDebug(
                    DebugShape.Cylinder,
                    Matrix4dUtil.asFloatData(m),
                    colour,
                    seconds,
                    (byte) DebugUtils.FLAG_NONE,
                    null,
                    OPACITY));
        }
    }

    /** Takes every shape off {@code out}'s player, HyLens's too (it draws its own again at its next refresh). */
    static void clear(PacketHandler out) {
        out.write(new ClearDebugShapes());
    }
}
