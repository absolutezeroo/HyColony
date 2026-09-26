package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class HyColonyComponents {
    private static ComponentType<EntityStore, CitizenTag> citizenTag;
    private static ComponentType<EntityStore, MoveTarget> moveTarget;

    private HyColonyComponents() {}

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        citizenTag = registry.registerComponent(CitizenTag.class, "HyColonyCitizen", CitizenTag.CODEC);
        moveTarget = registry.registerComponent(MoveTarget.class, MoveTarget::new);
    }

    public static ComponentType<EntityStore, CitizenTag> citizenTag() {
        return citizenTag;
    }

    public static ComponentType<EntityStore, MoveTarget> moveTarget() {
        return moveTarget;
    }
}
