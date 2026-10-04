package dev.hyangler.core.port;

/** What a block is, for the open-water rule and the depth (spec § 5, § 6.4). */
public enum BlockKind {
    WATER_SOURCE,
    WATER_FLOWING,
    AIR,
    OTHER
}
