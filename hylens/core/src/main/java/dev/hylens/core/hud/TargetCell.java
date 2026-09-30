package dev.hylens.core.hud;

/**
 * What the world holds at a walk target and just below it, as block or fluid ids read by the plugin; {@code ""} for
 * nothing there.
 */
public record TargetCell(String block, String below) {}
