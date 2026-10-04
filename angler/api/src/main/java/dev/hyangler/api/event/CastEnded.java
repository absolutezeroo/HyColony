package dev.hyangler.api.event;

import dev.hyangler.api.Angler;

/**
 * A cast ended, however it ended.
 *
 * @since 1.0
 */
public record CastEnded(Angler angler, Outcome outcome) implements FishingEvent {}
