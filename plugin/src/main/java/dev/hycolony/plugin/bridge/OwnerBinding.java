package dev.hycolony.plugin.bridge;

import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.IBaseEvent;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hycolony.api.Subscription;

/**
 * Ties something an addon holds to the addon's lifetime: {@code EventRegistry.register(EventRegistration)} only adds
 * its unregister step to the plugin's shutdown tasks (EventRegistry, PluginBase.cleanup), without touching the event
 * bus, so the step runs when the plugin stops, on the thread that stops it.
 */
final class OwnerBinding {
    /** A key type for the registration, never dispatched. */
    private static final class OwnerStopped implements IBaseEvent<Void> {}

    private OwnerBinding() {}

    /**
     * Runs {@code onStop} when {@code owner} stops; it must be idempotent and safe from any thread. Returns what drops
     * it (running it once more): the plugin's shutdown tasks are a copy-on-write list, safe from any thread. Throws
     * {@link IllegalStateException} for an owner already stopped, after running it. A registration racing the owner's
     * shutdown may land after its tasks ran and never run (Registry.enabled is not volatile): a narrow window, as
     * an addon calls on the world's thread and stops from another.
     */
    static Runnable bind(PluginBase owner, Runnable onStop) {
        EventRegistration<Void, OwnerStopped> r = owner.getEventRegistry()
                .register(new EventRegistration<Void, OwnerStopped>(OwnerStopped.class, () -> true, onStop));
        return r::unregister;
    }

    /** {@code s}, closed when {@code owner} stops; closing it drops that tie from the owner's shutdown tasks. */
    static Subscription bound(PluginBase owner, Subscription s) {
        Runnable drop = bind(owner, s::close);
        return () -> {
            s.close();
            drop.run();
        };
    }

    /** The key of one instance of {@code owner}: a plugin reloaded is another owner. */
    static String key(PluginBase owner) {
        return owner.getIdentifier() + "#" + Integer.toHexString(System.identityHashCode(owner));
    }
}
