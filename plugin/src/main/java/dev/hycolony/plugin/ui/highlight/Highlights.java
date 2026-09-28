package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The highlights players asked for, one at a time each, for {@link #MILLIS}: the block glows ({@link GlowingBlock},
 * seen by every player nearby) and a map marker points to it for that player ({@link HighlightMarkers}). Neither
 * shows through walls in 0.6.8 (docs/research/plugin-b-api.md § 29). Read by the world map thread, hence the
 * concurrent map.
 */
public final class Highlights {
    /** How long a highlight lasts, in milliseconds. */
    static final long MILLIS = 60_000;

    /** A player's highlight, the world it was asked in, until when (epoch ms) and its glowing copy. */
    record Active(Highlight highlight, UUID world, long until, Optional<UUID> glow) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private Highlights() {}

    /**
     * Shows {@code h} to {@code player}, or turns it off when it is the one shown; a new one replaces the previous. A
     * player offline or between worlds gets nothing.
     */
    public static void toggle(UUID player, Highlight h) {
        PlayerRef ref = Universe.get().getPlayer(player);
        Ref<EntityStore> entity = ref == null ? null : ref.getReference();
        UUID worldId = ref == null ? null : ref.getWorldUuid();
        if (entity == null || !entity.isValid() || worldId == null) {
            return;
        }
        World world = entity.getStore().getExternalData().getWorld();
        Active previous = ACTIVE.remove(player);
        if (previous != null) {
            World glowWorld = Universe.get().getWorld(previous.world()); // it may have been asked in another world
            previous.glow().ifPresent(g -> GlowingBlock.remove(glowWorld == null ? world : glowWorld, g));
            if (previous.highlight().anchor().equals(h.anchor()) && previous.until() >= System.currentTimeMillis()) {
                return; // the same one again: turned off
            }
        }
        Optional<UUID> glow = GlowingBlock.spawn(world, h.anchor(), MILLIS);
        ACTIVE.put(player, new Active(h, worldId, System.currentTimeMillis() + MILLIS, glow));
    }

    /** True while {@code player}'s highlight of {@code anchor} lasts. */
    public static boolean isActive(UUID player, BlockPos anchor) {
        return active(player).filter(a -> a.highlight().anchor().equals(anchor)).isPresent();
    }

    /** {@code player}'s highlight while it lasts; a finished one is dropped (its glow despawned by itself). */
    static Optional<Active> active(UUID player) {
        Active a = ACTIVE.get(player);
        if (a != null && a.until() < System.currentTimeMillis()) {
            ACTIVE.remove(player, a);
            return Optional.empty();
        }
        return Optional.ofNullable(a);
    }
}
