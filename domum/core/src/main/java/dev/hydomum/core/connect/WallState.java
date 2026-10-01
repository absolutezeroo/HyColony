package dev.hydomum.core.connect;

import java.util.Set;

/**
 * The name of a HyDomum wall's shape state (tools/domum/blocks/compat.py writes one per name): its template shape,
 * then {@code _Tall} and its tall sides at yaw 0 in N, E, S, W order, then {@code _Up} when the post, optional on a
 * straight run or a cross, is raised. A wall with low sides and no optional post keeps its shape's name.
 */
public final class WallState {
    private static final String TALL = "_Tall";
    private static final String UP = "_Up";
    // The shapes whose post depends on what is above (MC WallBlock.shouldRaisePost); the others always have one.
    private static final Set<String> OPTIONAL_POST = Set.of("Straight", "Cross_Junction");
    private static final Side[] LETTER_ORDER = {Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST};

    private WallState() {}

    /** The state name of a wall of shape with look (its tall sides in the world). */
    public static String name(ConnectedShape shape, WallLook look) {
        // The world sides turned back to the shape's yaw 0: a side turned by yaw then by 4 - yaw is itself.
        Set<Side> tall = Side.turned(look.tall(), (Side.values().length - shape.yaw()) % Side.values().length);
        StringBuilder name = new StringBuilder(shape.name());
        if (!tall.isEmpty()) {
            name.append(TALL);
            for (Side side : LETTER_ORDER) {
                if (tall.contains(side)) {
                    name.append(side.name().charAt(0));
                }
            }
        }
        if (look.post() && OPTIONAL_POST.contains(shape.name())) {
            name.append(UP);
        }
        return name.toString();
    }

    /**
     * Whether a wall whose state is named state (a name of {@link #name}, i.e. a template pattern key, not the Hytale
     * state id such as "Cross") has its post raised (MC WallBlock.UP).
     */
    public static boolean hasPost(String state) {
        String shape = state.contains(TALL) ? state.substring(0, state.indexOf(TALL)) : state.replace(UP, "");
        return !OPTIONAL_POST.contains(shape) || state.endsWith(UP);
    }
}
