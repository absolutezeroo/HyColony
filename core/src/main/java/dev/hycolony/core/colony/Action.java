package dev.hycolony.core.colony;

/** Colony permission actions. Flags are MineColonies' exact bit indices: never renumber (saved masks). */
public enum Action {
    ACCESS_HUTS(0),
    PLACE_HUTS(2),
    BREAK_HUTS(3),
    EDIT_PERMISSIONS(4),
    MANAGE_HUTS(5),
    RECEIVE_MESSAGES(6),
    USE_SCAN_TOOL(7),
    PLACE_BLOCKS(8),
    BREAK_BLOCKS(9),
    TOSS_ITEM(10),
    PICKUP_ITEM(11),
    FILL_BUCKET(12),
    OPEN_CONTAINER(13),
    RIGHTCLICK_BLOCK(14),
    RIGHTCLICK_ENTITY(15),
    THROW_POTION(16),
    SHOOT_ARROW(17),
    ATTACK_CITIZEN(18),
    ATTACK_ENTITY(19),
    TELEPORT_TO_COLONY(21),
    EXPLODE(22),
    RALLY_GUARDS(25),
    HURT_CITIZEN(26),
    HURT_VISITOR(27),
    MAP_BORDER(28),
    MAP_DEATHS(29),
    ACCESS_TOGGLEABLES(30);

    private final int flag;

    Action(int flag) {
        this.flag = flag;
    }

    public int flag() {
        return flag;
    }

    public long mask() {
        return 1L << flag;
    }
}
