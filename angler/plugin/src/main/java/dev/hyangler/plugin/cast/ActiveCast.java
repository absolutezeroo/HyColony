package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.api.Tackle;
import dev.hyangler.core.cast.CastSession;
import dev.hyangler.core.context.ContextFactory;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One player's cast in progress (spec § 7.3): the core's session and the Hytale entities it drives. Its world's thread
 * only; never saved. The world ticks at 30 a second, the core at 20: acc carries the remainder (ColonyTickSystem's
 * way).
 */
final class ActiveCast {
    static final float CORE_TICK_SECONDS = 0.05f;
    /** At most this many core ticks in one world tick: a long stall drops its backlog (ColonyTickSystem). */
    static final int MAX_CATCH_UP = 10;
    /** How often, in core ticks, the rain and sky over the bobber are read again. */
    static final int WEATHER_EVERY = 20;

    final UUID player;
    final CastSession session;
    final Ref<EntityStore> angler;
    final Rod rod;
    final ContextFactory world;
    /** The bobber, set right after the throw (six constructor parameters would break the rule of five). */
    @Nullable
    Ref<EntityStore> bobber;

    float acc;
    int coreTicks;
    /** The approaching fish's heading around the bobber, in radians (vanilla fishAngle). */
    double fishHeading;

    boolean raining;
    boolean skyVisible = true;

    ActiveCast(UUID player, CastSession session, Ref<EntityStore> angler, Rod rod, ContextFactory world) {
        this.player = player;
        this.session = session;
        this.angler = angler;
        this.rod = rod;
        this.world = world;
    }

    /** The rod cast with: its item id (put away, it ends the cast) and its tackle (the catch's luck). */
    record Rod(String itemId, Tackle tackle) {}
}
