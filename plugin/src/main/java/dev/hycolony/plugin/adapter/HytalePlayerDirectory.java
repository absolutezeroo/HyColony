package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.joml.Vector3d;

public final class HytalePlayerDirectory implements PlayerDirectory {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** A quarter turn, in radians: {@code HeadRotation}'s yaw is a full-circle angle, not degrees. */
    private static final float QUARTER_TURN_RAD = (float) (Math.PI / 2.0);

    private final World world;
    private boolean warned;

    public HytalePlayerDirectory(World world) {
        this.world = world;
    }

    @Override
    public boolean isOnline(UUID player) {
        return Universe.get().getPlayer(player) != null;
    }

    /** Position only if the player is in this world. */
    @Override
    public Optional<BlockPos> position(UUID player) {
        Ref<EntityStore> ref = refIn(player);
        if (ref == null) {
            return Optional.empty();
        }
        TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
    }

    /** In the group {@code /op} adds players to (hytale:Admin), which holds "*" and so every operator-only node. */
    @Override
    public boolean isOperator(UUID player) {
        try {
            return PermissionsModule.get().getGroupsForUser(player).contains(HytalePermissionsProvider.GROUP_ADMIN);
        } catch (RuntimeException e) {
            fail("isOperator", player, e);
            return false;
        }
    }

    /** The Player component's game mode; only a player in this world counts. */
    @Override
    public boolean isCreative(UUID player) {
        try {
            Ref<EntityStore> ref = refIn(player);
            Player p = ref == null ? null : ref.getStore().getComponent(ref, Player.getComponentType());
            return p != null && p.getGameMode() == GameMode.Creative;
        } catch (RuntimeException e) {
            fail("isCreative", player, e);
            return false;
        }
    }

    /**
     * {@code HeadRotation}, not {@code TransformComponent}, follows the camera: the movement packet carries
     * {@code bodyOrientation} and {@code lookOrientation} as two separate fields ({@code GamePacketHandler}), queued
     * as {@code PlayerInput.SetBody} into the transform's rotation and {@code PlayerInput.SetHead} into
     * {@code HeadRotation} respectively, and {@code PlayerSystems.UpdatePlayerRef} requires both components on every
     * player. Yaw 0 rad is north (-Z) and grows counterclockwise (north to west), the convention
     * {@code HeadRotation.getAxisDirection} and {@code PhysicsMath.headingFromDirection} both use
     * ({@code x = -sin(yaw)}, {@code z = -cos(yaw)}). The port's clockwise quarter (0=north, 1=east, 2=south,
     * 3=west) is the negated, rounded quarter turn.
     */
    @Override
    public int facing(UUID player) {
        try {
            Ref<EntityStore> ref = refIn(player);
            if (ref == null) {
                return 0;
            }
            HeadRotation head = ref.getStore().getComponent(ref, HeadRotation.getComponentType());
            if (head == null) {
                return 0;
            }
            float yaw = head.getRotation().yaw();
            return Math.floorMod(-Math.round(yaw / QUARTER_TURN_RAD), 4);
        } catch (RuntimeException e) {
            fail("facing", player, e);
            return 0;
        }
    }

    /** CLAUDE.md § 4: the first failure at WARNING, the following ones at FINE. */
    private void fail(String op, UUID player, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("PlayerDirectory.%s failed for %s", op, player);
        warned = true;
    }

    private Ref<EntityStore> refIn(UUID player) {
        Optional<PlayerRef> pr = world.getPlayerRefs().stream()
                .filter(p -> p.getUuid().equals(player))
                .findFirst();
        Ref<EntityStore> ref = pr.map(PlayerRef::getReference).orElse(null);
        return ref != null && ref.isValid() ? ref : null;
    }

    @Override
    public Collection<UUID> onlineIn(WorldKey key) {
        return world.getPlayerRefs().stream().map(PlayerRef::getUuid).toList();
    }
}
