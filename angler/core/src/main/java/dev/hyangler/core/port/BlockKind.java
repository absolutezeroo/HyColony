package dev.hyangler.core.port;

/**
 * What a block is, for the open-water rule and the depth (spec § 5, § 6.4; vanilla getOpenWaterTypeForBlock): water
 * counts only in a cell no solid block fills; AIR is also a block that floats on water and keeps it open (vanilla's
 * lily pad).
 */
public enum BlockKind {
    WATER_SOURCE,
    WATER_FLOWING,
    AIR,
    OTHER
}
