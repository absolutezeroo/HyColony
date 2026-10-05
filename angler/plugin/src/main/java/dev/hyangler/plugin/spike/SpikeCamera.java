package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.asset.type.gamemode.GameModeType;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/**
 * Throwaway (fishing-hytale.md § 7.5): fishing is played in third person, the only view where the line meets the rod
 * others see (the first-person rod is drawn elsewhere). The server cannot see the player switch views (no packet
 * reports it), so it locks the view for the cast, as spectating does (SpectatorSystems.java:287), and gives the
 * player's camera back at the end, as leaving spectating does (SpectatorSystems.applyFreeCamera, l. 291-305).
 */
final class SpikeCamera {
    private SpikeCamera() {}

    /** Puts a player in third person, locked; anything else is left alone. */
    static void lockThirdPerson(Ref<EntityStore> who, ComponentAccessor<EntityStore> accessor) {
        PlayerRef player = player(who, accessor);
        if (player != null) {
            player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.ThirdPerson, true, null));
        }
    }

    /**
     * Gives a player their camera back: the view their game mode locks, if any, else their own (as
     * CameraManager.resetCamera); anything else is left alone.
     */
    static void release(Ref<EntityStore> who, ComponentAccessor<EntityStore> accessor) {
        PlayerRef player = player(who, accessor);
        if (player == null) {
            return;
        }
        GameModeType mode = GameModeTypes.getCurrentType(who, accessor);
        ClientCameraView locked = mode != null ? mode.getLockedCameraView() : null;
        player.getPacketHandler()
                .writeNoCache(
                        locked != null
                                ? new SetServerCamera(locked, true, null)
                                : new SetServerCamera(ClientCameraView.Custom, false, null));
    }

    private static @Nullable PlayerRef player(Ref<EntityStore> who, ComponentAccessor<EntityStore> accessor) {
        return who.isValid() ? accessor.getComponent(who, PlayerRef.getComponentType()) : null;
    }
}
