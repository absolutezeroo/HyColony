package dev.hycolony.plugin.api;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Subscription;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * HyColony's entry point for other Hytale mods (spec 2026-09-30, § 4). A method naming a world where HyColony runs
 * must be called on that world's thread, else it throws {@link IllegalStateException}; {@link #get} and
 * {@link #subscribeWorlds} are safe from any thread. What an addon holds for an owner plugin (a subscription, a
 * tracking, a pause) ends when that plugin stops.
 *
 * <p>HyColony implements it, addons do not: a minor version may add methods.
 *
 * @since 1.0
 */
public interface HyColonyApi {
    /**
     * HyColony's api; throws {@link IllegalStateException} before HyColony's setup or after its shutdown. Any
     * thread.
     */
    static HyColonyApi get() {
        return HyColonyApiHolder.get();
    }

    /** HyColony in {@code world}; empty where it does not run, or is disabled (asset ids missing). */
    Optional<ColonyWorld> world(World world);

    /**
     * The citizen whose body {@code entity} is, read from its entity; empty for another entity, one gone, or in a world
     * where HyColony does not run.
     */
    Optional<CitizenRef> citizenOf(Ref<EntityStore> entity, ComponentAccessor<EntityStore> accessor);

    /** The loaded body of {@code citizen}; empty while unloaded, or for an unknown citizen or world. */
    Optional<Ref<EntityStore>> bodyOf(CitizenRef citizen);

    /**
     * {@link ColonyWorld#subscribe} in {@code world}, closed when {@code owner} stops; empty where HyColony does not
     * run.
     */
    <E> Optional<Subscription> subscribe(PluginBase owner, World world, Class<E> type, Consumer<? super E> listener);

    /** {@code DebugAccess.track} of {@code citizen}, stopped when {@code owner} stops; empty for an unknown one. */
    @Experimental
    Optional<Subscription> track(PluginBase owner, CitizenRef citizen);

    /**
     * Hears each later world where HyColony starts (on that world's thread) or stops (on the thread removing it),
     * until the subscription closes or {@code owner} stops; the worlds already running are not told. Any thread: an
     * addon calls it at its start, before any world.
     */
    Subscription subscribeWorlds(PluginBase owner, Consumer<? super ColonyWorldEvent> listener);

    /** The colony clock of {@code world}; empty where HyColony does not run. */
    @Experimental
    Optional<ColonyClock> clock(World world);
}
