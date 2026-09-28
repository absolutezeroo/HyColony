package dev.hyblockui.api;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Interface sounds heard by one player, as a bench window plays its open sound (CraftingWindow). Never throws: the
 * first failure is a WARNING, the next ones FINE. World thread.
 */
public final class UiSounds {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private static boolean warned;

    private UiSounds() {}

    /** Plays the sound event {@code soundEventId} to {@code player}; nothing when absent or unknown to the game. */
    public static void play(PlayerRef player, Optional<String> soundEventId) {
        try {
            int sound = soundEventId
                    .map(id -> SoundEvent.getAssetMap().getIndex(id))
                    .orElse(Integer.MIN_VALUE);
            if (sound != Integer.MIN_VALUE) {
                SoundUtil.playSoundEvent2dToPlayer(player, sound, SoundCategory.UI);
            }
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("UI sound %s failed", soundEventId);
            warned = true;
        }
    }
}
