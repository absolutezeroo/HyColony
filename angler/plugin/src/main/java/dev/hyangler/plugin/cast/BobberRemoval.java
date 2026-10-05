package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * Whenever a bobber goes, however it goes (its exit ended, Hytale's despawn, its chunk unloaded, its thrower gone),
 * removes its line and gives its angler's camera back, unless that player has cast again meanwhile: the one place
 * every exit passes (spec § 7.2). A bobber gone while its cast still runs (despawn, unload) ends that cast, cancelled.
 */
final class BobberRemoval extends RefSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Casts casts;
    private final Landing landing;
    private boolean failed;

    BobberRemoval(Casts casts, Landing landing) {
        this.casts = casts;
        this.landing = landing;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Bobber.type();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {}

    /** Removes the bobber's line and releases its angler's camera unless they cast again; never throws. */
    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer) {
        try {
            Bobber bobber = buffer.getComponent(ref, Bobber.type());
            if (bobber == null) {
                return;
            }
            if (bobber.line != null) {
                bobber.line.remove(buffer);
            }
            endCast(bobber.owner, ref, buffer);
            Ref<EntityStore> angler = bobber.angler;
            if (angler != null && !castAgain(bobber.owner, ref)) {
                CastCamera.release(bobber.owner, angler, buffer);
            }
        } catch (RuntimeException e) { // out of a RefSystem, an exception would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler: bobber removal failed");
            failed = true;
        }
    }

    /** Ends, cancelled, the owner's cast whose bobber went first (despawn, unload): it cannot go on without it. */
    private void endCast(@Nullable UUID owner, Ref<EntityStore> gone, CommandBuffer<EntityStore> buffer) {
        ActiveCast cast = owner == null ? null : casts.running(owner);
        if (cast != null && Objects.equals(cast.bobber, gone)) {
            cast.session.cancel();
            landing.finish(cast, cast.session.end().orElseThrow(), Optional.empty(), buffer);
        }
    }

    /** Whether the owner has a cast in progress with another bobber: their camera stays as that cast set it. */
    private boolean castAgain(@Nullable UUID owner, Ref<EntityStore> gone) {
        return owner != null
                && casts.of(owner).filter(c -> !Objects.equals(c.bobber, gone)).isPresent();
    }
}
