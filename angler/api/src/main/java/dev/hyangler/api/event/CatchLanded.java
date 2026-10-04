package dev.hyangler.api.event;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.Pos;

/**
 * A catch was given, after the hooks; pos is the bobber's block.
 *
 * @since 1.0
 */
public record CatchLanded(Angler angler, Catch landed, Pos pos) implements FishingEvent {}
