package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.plugin.cast.line.Line;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Marks a HyAngler bobber (spec § 7.2): its angler, pinned in water once floating, its line, and its exit once the
 * cast has ended (finishing: the end animation plays with its line). Never saved: Hytale never saves a projectile.
 */
final class Bobber implements Component<EntityStore> {
    private static @Nullable ComponentType<EntityStore, Bobber> type;

    /** The player who cast it; null for a bobber with no player's cast (an NPC's, plan task 17). */
    @Nullable
    UUID owner;
    /** The entity that fishes, player or NPC: BobberRemoval gives its camera back after the cast is forgotten. */
    @Nullable
    Ref<EntityStore> angler;

    @Nullable
    Line line;

    boolean floating;
    /** Whether Hytale's physics has it on the ground this tick. */
    boolean onGround;

    double surfaceY;
    int ticks;
    /** World ticks left before the bobber goes, once its cast has ended; 0 while it runs. */
    int finishing;
    /** World ticks until a catch's lift (Catch) follows its strike (Hook); 0 when none is due. */
    int catchIn;
    /** Whether a fish pulls the line taut. */
    boolean taut;
    /** Whether the line must snap on the bobber's next tick (a broken line, cut with the system's buffer). */
    boolean snapDue;

    /** Registers the component; call once, in setup(), before the systems that query it. */
    static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(Bobber.class, Bobber::new);
    }

    /** The component's type; throws {@link IllegalStateException} before register. */
    static ComponentType<EntityStore, Bobber> type() {
        ComponentType<EntityStore, Bobber> registered = type;
        if (registered == null) {
            throw new IllegalStateException("HyAngler's Bobber is not registered");
        }
        return registered;
    }

    @Override
    public Component<EntityStore> clone() {
        Bobber copy = new Bobber();
        copy.owner = owner;
        copy.angler = angler;
        copy.line = line == null ? null : line.copy();
        copy.floating = floating;
        copy.onGround = onGround;
        copy.surfaceY = surfaceY;
        copy.ticks = ticks;
        copy.finishing = finishing;
        copy.catchIn = catchIn;
        copy.taut = taut;
        copy.snapDue = snapDue;
        return copy;
    }
}
