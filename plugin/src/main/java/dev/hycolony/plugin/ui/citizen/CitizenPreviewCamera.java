package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The server camera on a citizen while its inventory page is open, facing it, so the citizen shows in the page's
 * frame (settings of SpectatorSystems.applyFollowCamera; essay in game, citizen-inventory-window.md § 9).
 *
 * <p>Deviation from MC: WindowCitizenInventory draws the entity in its frame, turned toward the mouse; Hytale cannot
 * draw an entity in a page, so the camera films the citizen itself, re-aimed when it turns. World thread.
 */
final class CitizenPreviewCamera {
    /** Blocks between the camera and the citizen's eyes. */
    static final float DISTANCE = 2.5f;
    /** Blocks the camera stands to the side and above the eyes, to put the citizen in the frame. */
    static final double SIDE = 0;
    /** See {@link #SIDE}. */
    static final double HEIGHT = 0;
    /** A turn of the citizen, in radians, past which the camera is aimed again (about 15 degrees). */
    private static final float TURN = 0.26f;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final PlayerRef player;
    private final World world;
    private final Supplier<Optional<Ref<EntityStore>>> body;
    private float aimedYaw = Float.NaN;
    private boolean on;

    /** {@code body}: the citizen's loaded body, read anew at each call (it may respawn or unload). */
    CitizenPreviewCamera(PlayerRef player, World world, Supplier<Optional<Ref<EntityStore>>> body) {
        this.player = player;
        this.world = world;
        this.body = body;
    }

    /** Gives the camera back (PlayerCameraResetCommand); safe to call more than once. */
    void stop() {
        release();
    }

    /**
     * Puts the camera on the body, or aims it again once the citizen turned; gives it back when the body is gone.
     * Called when the page opens, then regularly while it is shown.
     */
    void follow() {
        try {
            Ref<EntityStore> ref = body.get().filter(Ref::isValid).orElse(null);
            TransformComponent t = ref == null
                    ? null
                    : world.getEntityStore().getStore().getComponent(ref, TransformComponent.getComponentType());
            NetworkId id = ref == null
                    ? null
                    : world.getEntityStore().getStore().getComponent(ref, NetworkId.getComponentType());
            if (t == null || id == null) {
                release();
                return;
            }
            float yaw = t.getRotation().yaw();
            if (!on || Math.abs(Math.IEEEremainder(yaw - aimedYaw, 2 * Math.PI)) > TURN) {
                player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, at(id, yaw)));
                aimedYaw = yaw;
                on = true;
            }
        } catch (RuntimeException e) {
            LOG.at(Level.WARNING).withCause(e).log("HyColony: citizen preview camera failed");
        }
    }

    private void release() {
        if (on) {
            on = false;
            player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
        }
    }

    /** Attached to the body, {@link #DISTANCE} in front of its eyes and looking back at them. */
    private static ServerCameraSettings at(NetworkId body, float bodyYaw) {
        ServerCameraSettings s = new ServerCameraSettings();
        s.attachedToType = AttachedToType.EntityId;
        s.attachedToEntityId = body.getId();
        s.followAttachedEntity = true;
        s.eyeOffset = true;
        s.isFirstPerson = false;
        s.displayCursor = true;
        s.positionOffset = new Position(SIDE, HEIGHT, 0);
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        s.distance = DISTANCE;
        s.rotationType = RotationType.Custom;
        s.applyLookType = ApplyLookType.Rotation;
        s.rotation = new Direction(bodyYaw + (float) Math.PI, 0f, 0f);
        return s;
    }
}
