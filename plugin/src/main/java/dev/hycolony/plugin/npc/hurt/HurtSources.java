package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.hurt.HurtSource;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Optional;

/** Who dealt a Hytale damage, as the core tells them apart. */
final class HurtSources {
    private HurtSources() {}

    /**
     * The entity behind {@code damage} (an {@link Damage.EntitySource}, a projectile's shooter included): a player, a
     * citizen or another creature; {@link HurtSource.None} for the world or an entity no longer there.
     */
    static HurtSource of(Damage damage, ComponentAccessor<EntityStore> store) {
        if (!(damage.getSource() instanceof Damage.EntitySource entity)) {
            return new HurtSource.None();
        }
        Ref<EntityStore> ref = entity.getRef();
        if (!ref.isValid()) {
            return new HurtSource.None();
        }
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            return new HurtSource.Player(player.getUuid());
        }
        CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
        return tag != null ? new HurtSource.Citizen(tag.colonyId()) : new HurtSource.Creature();
    }

    /** Where the entity behind {@code damage} stands; empty for the world or an entity gone. */
    static Optional<Vec3> position(Damage damage, ComponentAccessor<EntityStore> store) {
        if (!(damage.getSource() instanceof Damage.EntitySource entity)
                || !entity.getRef().isValid()) {
            return Optional.empty();
        }
        TransformComponent t = store.getComponent(entity.getRef(), TransformComponent.getComponentType());
        return t == null
                ? Optional.empty()
                : Optional.of(new Vec3(t.getPosition().x, t.getPosition().y, t.getPosition().z));
    }
}
