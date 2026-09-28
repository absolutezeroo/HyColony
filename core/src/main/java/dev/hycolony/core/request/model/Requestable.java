package dev.hycolony.core.request.model;

/**
 * What can be requested (MC IRequestable): item deliverables; courier deliveries and pickups (MC
 * AbstractDeliverymanRequestable) and crafting tasks (MC AbstractCrafting), which are not items.
 */
public sealed interface Requestable permits Deliverable, Delivery, Pickup, Crafting {
    /** Human readable, e.g. "64 x Wood_Oak_Trunk". */
    String describe();
}
