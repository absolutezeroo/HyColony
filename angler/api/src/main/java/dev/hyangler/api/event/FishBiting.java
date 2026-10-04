package dev.hyangler.api.event;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Pos;

/**
 * A fish bites the bobber at pos: the hooking window opens.
 *
 * @since 1.0
 */
public record FishBiting(Angler angler, Pos pos) implements FishingEvent {}
