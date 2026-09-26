package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import org.joml.Vector3d;

public final class HytalePlayerDirectory implements PlayerDirectory {
    private final World world;

    private static final System.Logger LOG = System.getLogger(HytalePlayerDirectory.class.getName());

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
            LOG.log(System.Logger.Level.WARNING, "Operator check failed for " + player, e);
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
            LOG.log(System.Logger.Level.WARNING, "Creative check failed for " + player, e);
            return false;
        }
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
