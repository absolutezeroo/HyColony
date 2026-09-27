package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class HyColonyComponents {
    // Registered by the plugin's setup, before any system or adapter asks for them.
    private static @Nullable ComponentType<EntityStore, CitizenTag> citizenTag;
    private static @Nullable ComponentType<EntityStore, MoveTarget> moveTarget;

    private static final String NOT_REGISTERED = "HyColony components not registered yet";

    private HyColonyComponents() {}

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        citizenTag = registry.registerComponent(CitizenTag.class, "HyColonyCitizen", CitizenTag.CODEC);
        moveTarget = registry.registerComponent(MoveTarget.class, MoveTarget::new);
    }

    public static ComponentType<EntityStore, CitizenTag> citizenTag() {
        return Objects.requireNonNull(citizenTag, NOT_REGISTERED);
    }

    public static ComponentType<EntityStore, MoveTarget> moveTarget() {
        return Objects.requireNonNull(moveTarget, NOT_REGISTERED);
    }
}
