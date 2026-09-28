package dev.hycolony.core.crafting.job;

/**
 * A job that crafts for its hut's crafting module (MC AbstractJobCrafter). Deviation from MC: not a {@code Job}
 * subtype, as {@code ArchitectureTest} forbids extending one; a crafter job extends {@code Job}, implements this and
 * owns one {@link CraftingTasks}. Its {@code onRemoval} calls {@link CraftingTasks#cancelAll}, as MC's does.
 */
public interface Crafter {
    /** The job's crafting state: its tasks and the counters of the recipe under way. */
    CraftingTasks craftingTasks();
}
