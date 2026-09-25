package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
        Optional<PlayerRef> pr = world.getPlayerRefs().stream().filter(p -> p.getUuid().equals(player)).findFirst();
        if (pr.isEmpty()) {
            return Optional.empty();
        }
        Ref<EntityStore> ref = pr.get().getReference();
        if (ref == null || !ref.isValid()) {
            return Optional.empty();
        }
        TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
    }

    @Override
    public Collection<UUID> onlineIn(WorldKey key) {
        return world.getPlayerRefs().stream().map(PlayerRef::getUuid).toList();
    }
}
