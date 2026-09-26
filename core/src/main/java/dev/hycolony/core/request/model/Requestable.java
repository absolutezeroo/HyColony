package dev.hycolony.core.request.model;

/** What can be requested (MineColonies IRequestable). Only deliverables for now. */
public sealed interface Requestable permits Deliverable {}
