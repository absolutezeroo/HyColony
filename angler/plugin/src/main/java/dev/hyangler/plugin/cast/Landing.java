package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.api.Angler;
import dev.hyangler.api.Catch;
import dev.hyangler.api.event.CastEnded;
import dev.hyangler.api.event.Outcome;
import dev.hyangler.core.AnglerSettings;
import dev.hyangler.core.FishingService;
import dev.hyangler.core.cast.CastEnd;
import java.util.Optional;
import java.util.logging.Level;

/**
 * How a cast ends (spec § 7.3, § 7.4): the line reeled in (the catch given and the rod worn, CatchGiver), and the one
 * place every end passes (finish). World thread only.
 */
final class Landing {
    /** World ticks the bobber stays once its cast has ended: the end animation plays with its line. */
    static final int FINISH_TICKS = 40;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final FishingService service;
    private final AnglerSettings settings;
    private final Casts casts;
    private final CastEffects effects;
    private final CastAnimations animations;
    private final CatchGiver giver;
    private boolean warned;

    Landing(
            FishingService service,
            AnglerSettings settings,
            Casts casts,
            CastEffects effects,
            CastAnimations animations) {
        this.service = service;
        this.settings = settings;
        this.casts = casts;
        this.effects = effects;
        this.animations = animations;
        this.giver = new CatchGiver(service);
    }

    /**
     * Reels the line in (the second use of the rod): during a bite, a catch given (CatchGiver.land); the rod worn as
     * vanilla (CastSession.wear), except for a bite that gave nothing (nothing can bite there, or a hook cancelled
     * it: plan, review focus 5); the cast ended. A Hytale failure in giving or wearing is logged; the cast ends
     * whatever happens.
     */
    void reel(ActiveCast cast, InteractionContext ctx, CommandBuffer<EntityStore> buffer) {
        CastEnd end = cast.session.reel();
        Optional<Catch> landed = Optional.empty();
        try {
            landed = land(cast, end, buffer);
            int wear = end == CastEnd.CAUGHT && landed.isEmpty() ? 0 : cast.session.wear();
            if (settings.rodWear() && wear > 0) {
                giver.wear(cast.angler, ctx, wear, buffer);
            }
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: giving the catch or wear failed");
            warned = true;
        }
        finish(cast, end, landed, buffer);
    }

    /** The catch given for a bite reeled in with its bobber still there; empty otherwise. */
    private Optional<Catch> land(ActiveCast cast, CastEnd end, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> bobber = cast.bobber;
        return end == CastEnd.CAUGHT && bobber != null && bobber.isValid()
                ? giver.land(cast, bobber, buffer)
                : Optional.empty();
    }

    /**
     * Ends the cast: forgets it (the player may cast again at once), publishes CastEnded, plays the reel's sound at the
     * bobber (not for a cancelled cast), and sends the bobber, if still there, out over FINISH_TICKS with its end
     * animation: Hook then Catch for a catch, Escape for a fish missed, Snap for a broken line (cut on the bobber's
     * next tick), none for a grounded cast. A cancelled cast's bobber and line go on its next tick, without animation.
     */
    void finish(ActiveCast cast, CastEnd end, Optional<Catch> landed, ComponentAccessor<EntityStore> accessor) {
        casts.end(cast.player, cast);
        service.publish(new CastEnded(new Angler.Player(cast.player), outcome(end, landed)));
        Ref<EntityStore> ref = cast.bobber;
        if (ref == null || !ref.isValid()) {
            return;
        }
        Bobber bobber = accessor.getComponent(ref, Bobber.type());
        if (bobber == null) {
            return;
        }
        TransformComponent at = accessor.getComponent(ref, TransformComponent.getComponentType());
        if (at != null && end != CastEnd.CANCELLED) {
            effects.reel(at.getPosition(), accessor);
        }
        sendOut(bobber, end, accessor);
    }

    /** Starts the bobber's exit with the end's animation; a cancelled cast's goes on the next tick, without one. */
    private void sendOut(Bobber bobber, CastEnd end, ComponentAccessor<EntityStore> accessor) {
        bobber.taut = end == CastEnd.CAUGHT;
        switch (end) {
            case CAUGHT -> {
                animations.play(bobber, CastAnimations.HOOK, accessor);
                bobber.catchIn = CastAnimations.HOOK_TICKS;
            }
            case ESCAPED -> animations.play(bobber, CastAnimations.ESCAPE, accessor);
            case BROKEN -> {
                animations.play(bobber, CastAnimations.SNAP, accessor);
                bobber.snapDue = true;
            }
            default -> {}
        }
        // a cancelled cast (rod put away, angler dead or gone) has nothing to show: bobber and line go on the next tick
        bobber.finishing = end == CastEnd.CANCELLED ? 1 : FINISH_TICKS;
    }

    private static Outcome outcome(CastEnd end, Optional<Catch> landed) {
        return switch (end) {
            case CAUGHT -> new Outcome.Caught(landed);
            case ESCAPED -> new Outcome.Escaped();
            case GROUNDED -> new Outcome.Grounded();
            case BROKEN -> new Outcome.Broken();
            case CANCELLED -> new Outcome.Cancelled();
        };
    }
}
