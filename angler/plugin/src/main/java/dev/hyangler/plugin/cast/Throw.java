package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hyangler.api.Angler;
import dev.hyangler.api.Pos;
import dev.hyangler.api.RodStats;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.event.CastStarted;
import dev.hyangler.core.AnglerSettings;
import dev.hyangler.core.FishingService;
import dev.hyangler.core.cast.CastSession;
import dev.hyangler.plugin.AnglerIds;
import dev.hyangler.plugin.cast.line.Line;
import dev.hyangler.plugin.world.WorldContexts;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Throws a player's bobber (spec § 7.2): the projectile from the eye along the look at the charged power, tied by the
 * line to the rod's tip, the view turned to third person, the core's cast started and published. World thread only.
 */
final class Throw {
    /**
     * The bobber's own lifetime: an hour. A cast has no fixed longest time (each wait has its bound, spec § 7.4), so
     * this only removes a forgotten bobber; BobberRemoval then ends its cast.
     */
    static final long BOBBER_LIFETIME_MS = 3_600_000L;
    /** The line's carriers outlive their bobber by this much, so they never go first or on the same tick. */
    private static final float CARRIERS_EXTRA_SECONDS = 5f;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final FishingService service;
    private final AnglerSettings settings;
    private final AnglerIds ids;
    private final Casts casts;
    private boolean warned;

    Throw(FishingService service, AnglerSettings settings, AnglerIds ids, Casts casts) {
        this.service = service;
        this.settings = settings;
        this.ids = ids;
        this.casts = casts;
    }

    /** Throws the player's bobber with this rod at this power; nothing without the bobber's projectile asset. */
    void cast(UUID player, Ref<EntityStore> user, ItemStack rod, double power, CommandBuffer<EntityStore> buffer) {
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(ids.bobber());
        if (config == null) {
            unknown("projectile", ids.bobber());
            return;
        }
        Transform look = TargetUtil.getLook(user, buffer);
        // spawnProjectile changes its position and direction: copies (fishing-hytale.md § 5.1)
        Ref<EntityStore> bobberRef = ProjectileModule.get()
                .spawnProjectile(
                        null,
                        null,
                        user,
                        buffer,
                        config,
                        new Vector3d(look.getPosition()),
                        new Vector3d(look.getDirection()),
                        1f,
                        (float) power,
                        BOBBER_LIFETIME_MS);
        Bobber bobber = new Bobber();
        bobber.owner = player;
        bobber.angler = user;
        bobber.line = tie(bobberRef, look, user, buffer);
        buffer.addComponent(bobberRef, Bobber.type(), bobber);
        ActiveCast cast = start(player, user, rod, buffer);
        cast.bobber = bobberRef;
        CastCamera.thirdPerson(user, buffer);
        Vector3d at = look.getPosition();
        service.publish(
                new CastStarted(new Angler.Player(player), new Pos((int) Math.floor(at.x), (int) Math.floor(at.y), (int)
                        Math.floor(at.z))));
    }

    /** The line from the bobber to the user's rod tip, its carriers spawned at the eye; null, logged, without it. */
    private @Nullable Line tie(
            Ref<EntityStore> bobber, Transform look, Ref<EntityStore> user, CommandBuffer<EntityStore> buffer) {
        float carriersLife = BOBBER_LIFETIME_MS / 1000f + CARRIERS_EXTRA_SECONDS;
        Line line = Line.tie(buffer, bobber, look.getPosition(), user, new Line.LineAssets(ids.beam(), carriersLife))
                .orElse(null);
        if (line == null) {
            unknown("line beam", ids.beam());
        }
        return line;
    }

    /** Starts and records the player's cast in the core: its rod's tackle, the config's bite time, this world. */
    private ActiveCast start(UUID player, Ref<EntityStore> user, ItemStack rod, CommandBuffer<EntityStore> buffer) {
        Tackle tackle = service.rod(rod.getItemId()).map(RodStats::tackle).orElse(Tackle.NONE);
        CastSession session = new CastSession(
                tackle.lure(), tackle.maxLine(), settings.biteTimeMultiplier(), RandomGenerator.of("L64X128MixRandom"));
        ActiveCast cast = new ActiveCast(
                player,
                session,
                user,
                new ActiveCast.Rod(rod.getItemId(), tackle),
                WorldContexts.of(buffer.getExternalData().getWorld(), ids));
        casts.start(player, cast);
        return cast;
    }

    /** Logs the first unknown asset in WARNING (the id-map names it), the next ones in FINE. */
    private void unknown(String what, String id) {
        LOG.at(warned ? Level.FINE : Level.WARNING).log("HyAngler: the %s asset '%s' is unknown", what, id);
        warned = true;
    }
}
