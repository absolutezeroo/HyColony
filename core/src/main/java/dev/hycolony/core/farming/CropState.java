package dev.hycolony.core.farming;

/** Where a crop block stands in its growth: none there, still growing, or ready to harvest (MC CropBlock.isMaxAge). */
public enum CropState {
    NONE,
    GROWING,
    MATURE
}
