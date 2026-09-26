package dev.hycolony.core.kernel.config;

/** What an explosion may damage inside a colony. MC api.colony.permissions.Explosions, same names. */
public enum Explosions {
    DAMAGE_NOTHING,
    DAMAGE_PLAYERS,
    DAMAGE_ENTITIES,
    DAMAGE_EVERYTHING;

    /** The mode named {@code name} (any case); MC's default DAMAGE_ENTITIES when absent or unknown. */
    public static Explosions parse(String name) {
        for (Explosions e : values()) {
            if (name != null && e.name().equalsIgnoreCase(name.trim())) {
                return e;
            }
        }
        return DAMAGE_ENTITIES;
    }
}
