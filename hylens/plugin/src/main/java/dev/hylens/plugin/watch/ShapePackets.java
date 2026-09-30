package dev.hylens.plugin.watch;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.api.Vec;
import dev.hylens.core.draw.Shape;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3f;

/**
 * Sends HyLens's shapes to one player only, as WildernessDebugShapeSystem does: DebugUtils.add would show them to the
 * whole world. A line is a thin cylinder turned from Y to its direction, as DebugUtils.addLine builds it.
 */
final class ShapePackets {
    /**
     * Seconds a shape lasts: a little over a refresh, so that no gap shows; the old and the new shape overlap for the
     * difference, as Hytale's wilderness shapes do (1 s refreshed, 1.25 s lasting).
     */
    static final float SECONDS = WatchRefreshSystem.REFRESH_SECONDS + 0.15f;

    private static final float OPACITY = 0.5f;
    private static final double LINE_WIDTH = 0.08;
    /** Blocks under which a line is not drawn. */
    private static final double MIN_LINE = 0.05;

    private static final double SPHERE_SIZE = 0.5;
    /** A hair over a block, so the cube's edges show around the block it boxes. */
    private static final double CUBE_SIZE = 1.02;

    private ShapePackets() {}

    /** Sends {@code shapes} to {@code player}; a cube is drawn as edges only, not to hide the block it boxes. */
    static void send(PlayerRef player, List<Shape> shapes) {
        PacketHandler out = player.getPacketHandler();
        Matrix4d m = new Matrix4d();
        for (Shape s : shapes) {
            Vec a = s.from();
            m.identity().translate(a.x(), a.y(), a.z());
            DebugShape kind;
            int flags = DebugUtils.FLAG_NONE;
            switch (s.kind()) {
                case LINE -> {
                    Vec b = s.to();
                    double dx = b.x() - a.x();
                    double dy = b.y() - a.y();
                    double dz = b.z() - a.z();
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length < MIN_LINE) {
                        continue; // the body is on its target: no direction to turn to, and nothing to show
                    }
                    m.identity()
                            .translate((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2)
                            .rotate(new Quaterniond().rotationTo(0, 1, 0, dx, dy, dz))
                            .scale(LINE_WIDTH, length, LINE_WIDTH);
                    kind = DebugShape.Cylinder;
                }
                case SPHERE -> {
                    m.scale(SPHERE_SIZE);
                    kind = DebugShape.Sphere;
                }
                case CUBE -> {
                    m.scale(CUBE_SIZE);
                    kind = DebugShape.Cube;
                    flags = DebugUtils.FLAG_NO_SOLID;
                }
                default -> throw new IllegalStateException(s.kind().name());
            }
            out.write(new DisplayDebug(
                    kind, Matrix4dUtil.asFloatData(m), colour(s.colour()), SECONDS, (byte) flags, null, OPACITY));
        }
    }

    private static Vector3f colour(Shape.Colour c) {
        return switch (c) {
            case WALK -> DebugUtils.COLOR_CYAN;
            case FAILED -> DebugUtils.COLOR_RED;
            case STOP -> DebugUtils.COLOR_YELLOW;
            case WORK -> DebugUtils.COLOR_LIME;
        };
    }
}
