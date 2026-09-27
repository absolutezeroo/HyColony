package dev.hycolony.core.request.model;

/**
 * What can be requested (MC IRequestable): item deliverables, and courier deliveries and pickups (MC
 * AbstractDeliverymanRequestable), which are not items.
 */
public sealed interface Requestable permits Deliverable, Delivery, Pickup {
    /** Human readable, e.g. "64 x Wood_Oak_Trunk". */
    String describe();
}
