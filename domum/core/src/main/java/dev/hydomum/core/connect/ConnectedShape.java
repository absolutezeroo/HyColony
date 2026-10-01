package dev.hydomum.core.connect;

import java.util.Map;
import java.util.Set;

/**
 * A shape of the HyDomum fence template (HyDomum_FenceConnectedBlockTemplate) and its yaw (0 to 3 quarter turns):
 * MC's multipart fence, an arm toward each joined side, drawn as one of six shapes.
 */
public record ConnectedShape(String name, int yaw) {
    /** The shape with no joined side, symmetric: the template's default. */
    static final String POST = "Post";

    // Shape -> its arms at yaw 0, as tools/domum/blocks/compat.py SHAPES (the vanilla template's turn). Each set of
    // sides is one shape only, so their order does not matter.
    private static final Map<String, Set<Side>> SHAPES = Map.of(
            "End", Set.of(Side.NORTH),
            "Straight", Set.of(Side.EAST, Side.WEST),
            "Corner", Set.of(Side.WEST, Side.SOUTH),
            "T_Junction", Set.of(Side.EAST, Side.WEST, Side.SOUTH),
            "Cross_Junction", Set.of(Side.values()));

    /** The shape and smallest yaw whose arms are exactly joined. */
    public static ConnectedShape of(Set<Side> joined) {
        if (joined.isEmpty()) {
            return new ConnectedShape(POST, 0);
        }
        for (Map.Entry<String, Set<Side>> shape : SHAPES.entrySet()) {
            for (int yaw = 0; yaw < Side.values().length; yaw++) {
                if (Side.turned(shape.getValue(), yaw).equals(joined)) {
                    return new ConnectedShape(shape.getKey(), yaw);
                }
            }
        }
        // Unreachable: every non-empty set of sides is one of the shapes, turned.
        throw new IllegalArgumentException("no shape for " + joined);
    }

    /** The arms of the named shape at yaw 0; none for the post. */
    static Set<Side> sidesOf(String name) {
        return SHAPES.getOrDefault(name, Set.of());
    }
}
