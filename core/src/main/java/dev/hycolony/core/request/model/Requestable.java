package dev.hycolony.core.request.model;

/**
 * What can be requested (MC IRequestable): item deliverables, and later courier deliveries and pickups (MC
 * AbstractDeliverymanRequestable), which are not items.
 *
 * <p>Deviation from MC: not sealed, so that tests can make a requestable no built-in resolver handles.
 */
public interface Requestable {
    /** Human readable, e.g. "64 x Wood_Oak_Trunk". */
    String describe();
}
