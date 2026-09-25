package dev.hycolony.core.construction;

/** Builder place and break delays in ticks (MC AbstractEntityAIStructure / AbstractEntityAIInteract). */
public final class BuilderTimings {
    private static final int BUILD_BLOCK_DELAY = 15;
    private static final int PROGRESS_MULTIPLIER = 10;
    private static final int BLOCK_MINING_DELAY = 500;
    private static final double LEVEL_MODIFIER = 0.85;
    /** EntityAIStructureBuilder.SPEED_BUFF_0. */
    private static final double BUILDER_BREAK_FACTOR = 0.5;

    private BuilderTimings() {}

    /** {@code 15 * 10 / (p / 2 + 10)}, integer arithmetic as in MC; at least 1. */
    public static int placeDelay(int primarySkill) {
        return Math.max(1, BUILD_BLOCK_DELAY * PROGRESS_MULTIPLIER / (primarySkill / 2 + PROGRESS_MULTIPLIER));
    }

    /** {@code (int) ((int) (500 * 0.85^(s / 2) * hardness / toolSpeed) * 0.5)}; at least 1. */
    public static int breakDelay(int secondarySkill, float hardness, float toolSpeed) {
        double speed = toolSpeed > 0 ? toolSpeed : 1;
        int base = (int) (BLOCK_MINING_DELAY * Math.pow(LEVEL_MODIFIER, secondarySkill / 2.0) * hardness / speed);
        return Math.max(1, (int) (base * BUILDER_BREAK_FACTOR));
    }
}
