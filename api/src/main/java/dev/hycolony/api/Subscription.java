package dev.hycolony.api;

/**
 * Something an addon holds and ends: listening to events, following a citizen.
 *
 * @since 1.0
 */
public interface Subscription extends AutoCloseable {
    /**
     * Ends it. Idempotent, callable from any thread, and it never throws: a plugin's shutdown may call it from outside
     * the world's thread. No delivery starts after it returns; one already under way on the world's thread may still
     * finish. HyColony tidies up on the world's thread.
     */
    @Override
    void close();
}
