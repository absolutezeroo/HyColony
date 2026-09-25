package dev.hycolony.core.kernel.ai;

/** Priority groups evaluated before state transitions, in the order AI_BLOCKING, EVENT, STATE_BLOCKING. */
public enum AIBlockingEventType implements IStateEventType {
    AI_BLOCKING,
    STATE_BLOCKING,
    EVENT
}
