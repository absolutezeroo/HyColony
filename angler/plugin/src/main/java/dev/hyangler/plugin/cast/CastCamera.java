package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.asset.type.gamemode.GameModeType;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Fishing is played in third person (spec § 7.2, the user's decision; fishing-hytale.md § 7.5), the only view where
 * the line meets the rod others see (the first-person rod is drawn elsewhere). The cast turns the view to third
 * person, unlocked: the player may go back to first person (the server cannot see it: no packet reports the view).
 * At the end, the player's camera is given back, as leaving spectating does (SpectatorSystems.applyFreeCamera).
 */
final class CastCamera {
    private CastCamera() {}

    /** Turns a player's view to third person, which they may change; anything else is left alone. */
    static void thirdPerson(Ref<EntityStore> who, ComponentAccessor<EntityStore> accessor) {
        PlayerRef player = who.isValid() ? accessor.getComponent(who, PlayerRef.getComponentType()) : null;
        if (player != null) {
            player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.ThirdPerson, false, null));
        }
    }

    /**
     * Gives a player their camera back: the view their game mode locks, if any, else their own (as
     * CameraManager.resetCamera). A player who left this world (who is no longer valid here) gets their own camera,
     * through the universe: their game mode lives in the other world's thread.
     */
    static void release(@Nullable UUID owner, Ref<EntityStore> who, ComponentAccessor<EntityStore> accessor) {
        if (who.isValid()) {
            PlayerRef player = accessor.getComponent(who, PlayerRef.getComponentType());
            if (player != null) {
                GameModeType mode = GameModeTypes.getCurrentType(who, accessor);
                ClientCameraView locked = mode != null ? mode.getLockedCameraView() : null;
                player.getPacketHandler()
                        .writeNoCache(
                                locked != null
                                        ? new SetServerCamera(locked, true, null)
                                        : new SetServerCamera(ClientCameraView.Custom, false, null));
            }
            return;
        }
        PlayerRef elsewhere = owner == null ? null : Universe.get().getPlayer(owner);
        if (elsewhere != null) {
            elsewhere.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
        }
    }
}
