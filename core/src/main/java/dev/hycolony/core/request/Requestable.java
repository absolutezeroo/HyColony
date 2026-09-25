package dev.hycolony.core.request;

/** What can be requested (MineColonies IRequestable). Only deliverables for now. */
public sealed interface Requestable permits Deliverable {}
