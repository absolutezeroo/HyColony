package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.plugin.AnglerIds;
import java.util.logging.Level;
import org.joml.Vector3dc;

/**
 * A cast's sounds and particles at the bobber (spec § 7.3), from the id-map (fishing-hytale.md § 5.6: Hytale has no
 * fishing sound, so water's and the hookshot's), as HyColony's HytaleWorldEffects plays its own. An unknown id plays
 * nothing; never throws (first failure WARNING, then FINE).
 */
final class CastEffects {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final AnglerIds ids;
    private boolean warned;

    CastEffects(AnglerIds ids) {
        this.ids = ids;
    }

    /** The approaching fish's bubbles, at where the fish is (CastTicks places them, as vanilla). */
    void bubbles(Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        particles(ids.bubbles(), at, accessor);
    }

    /** A small splash where a fish teases the bait while the wait runs (vanilla's teasing splashes). */
    void tease(Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        particles(ids.tease(), at, accessor);
    }

    /** The bite: a splash and the water's sound. */
    void bite(Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        particles(ids.splash(), at, accessor);
        sound(ids.biteSound(), at, accessor);
    }

    /** The line reeled in. */
    void reel(Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        sound(ids.reelSound(), at, accessor);
    }

    private void particles(String id, Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        if (id.isEmpty()) {
            return;
        }
        try {
            ParticleUtil.spawnParticleEffect(id, at, accessor);
        } catch (RuntimeException e) {
            failed(e, id);
        }
    }

    private void sound(String id, Vector3dc at, ComponentAccessor<EntityStore> accessor) {
        int index = id.isEmpty() ? Integer.MIN_VALUE : SoundEvent.getAssetMap().getIndex(id);
        if (index == Integer.MIN_VALUE) {
            return;
        }
        try {
            SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, at.x(), at.y(), at.z(), accessor);
        } catch (RuntimeException e) {
            failed(e, id);
        }
    }

    private void failed(RuntimeException e, String id) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: effect %s failed", id);
        warned = true;
    }
}
