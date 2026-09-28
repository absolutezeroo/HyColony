package dev.hycolony.core.crafting.job;

/** How long a crafter works on one run of a recipe (MC AbstractEntityAICrafting's progress constants). */
final class CraftingProgress {
    /** MC AbstractEntityAICrafting.PROGRESS_MULTIPLIER. */
    static final int PROGRESS_MULTIPLIER = 10;
    /** MC AbstractEntityAICrafting.MAX_LEVEL: past this, the speed skill no longer shortens the work. */
    static final int MAX_LEVEL = 50;
    /** MC AbstractEntityAICrafting.HITTING_TIME: how many times the product needs to be hit. */
    static final int HITTING_TIME = 3;

    private CraftingProgress() {}

    /**
     * MC getRequiredProgressForMakingRawMaterial: the hits one run takes at this level of the crafter's speed skill,
     * MC's integer division kept (30 at level 1, 0 from level 20: the first hit makes the run).
     */
    static int requiredHits(int speedSkillLevel) {
        return PROGRESS_MULTIPLIER / Math.min(speedSkillLevel / 2 + 1, MAX_LEVEL) * HITTING_TIME;
    }
}
