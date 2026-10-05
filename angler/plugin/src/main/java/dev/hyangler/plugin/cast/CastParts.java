package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.core.AnglerSettings;
import dev.hyangler.core.FishingService;
import dev.hyangler.plugin.AnglerIds;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The player's fishing wired into Hytale (spec § 7): the bobber's component and systems, and the parts the rod's
 * interaction reaches. Hytale's codec builds the interaction without our constructor, so it finds them here, set once
 * at HyAngler's setup, as the api's holder.
 */
public final class CastParts {
    private static volatile @Nullable Parts parts;

    /** What the rod's use reaches: the casts in progress, the throw, the end of a cast. */
    record Parts(Casts casts, Throw thrower, Landing landing) {}

    private CastParts() {}

    /** HyAngler's setup: registers the bobber's component and systems, and the parts its rod's use reaches. */
    public static void install(
            ComponentRegistryProxy<EntityStore> entities,
            FishingService service,
            AnglerIds ids,
            AnglerSettings settings) {
        Bobber.register(entities);
        Casts casts = new Casts();
        CastEffects effects = new CastEffects(ids);
        CastAnimations animations = new CastAnimations(ids);
        Landing landing = new Landing(service, settings, casts, effects, animations);
        CastTicks ticks = new CastTicks(service, landing, effects, animations);
        entities.registerSystem(new BobberSystem(casts, ticks, landing, animations));
        entities.registerSystem(new BobberRemoval(casts, landing));
        parts = new Parts(casts, new Throw(service, settings, ids, casts), landing);
    }

    /** The parts, empty before HyAngler's setup. */
    static Optional<Parts> get() {
        return Optional.ofNullable(parts);
    }
}
