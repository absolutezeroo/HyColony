package dev.hyangler.api;

/**
 * A registration that can end: a listener or a catch hook.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    /** Ends it; idempotent, safe from any thread, never throws. */
    @Override
    void close();
}
