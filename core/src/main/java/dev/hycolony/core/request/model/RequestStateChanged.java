package dev.hycolony.core.request.model;

/**
 * The request {@code token} of colony {@code colonyId} went from {@code from} to {@code to}. Posted on the world's bus
 * only while someone listens: a request changes state several times in a tick.
 */
public record RequestStateChanged(int colonyId, RequestToken token, RequestState from, RequestState to) {}
