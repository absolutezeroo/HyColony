package dev.hyangler.api.event;

import dev.hyangler.api.Angler;
import dev.hyangler.api.Pos;

/**
 * A bobber was cast; pos is the angler's block.
 *
 * @since 1.0
 */
public record CastStarted(Angler angler, Pos pos) implements FishingEvent {}
