package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The highlights players asked for, one group at a time each, for {@link #MILLIS}: each block glows
 * ({@link GlowingBlock}, seen by every player nearby) and a map marker points to it for that player
 * ({@link HighlightMarkers}). Neither shows through walls in 0.6.8 (docs/research/plugin-b-api.md § 29). Read by the
 * world map thread, hence the concurrent map.
 */
public final class Highlights {
    /** How long a highlight lasts, in milliseconds (MC's 60 s TimedBoxRenderData). */
    static final long MILLIS = 60_000;

    /** A player's highlighted blocks, the world they were asked in, until when (epoch ms) and their glowing copies. */
    record Active(List<Highlight> highlights, UUID world, long until, List<UUID> glows) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private Highlights() {}

    /**
     * Shows {@code h} to {@code player}, or turns it off when it alone is shown; it replaces any previous group. A
     * player offline or between worlds gets nothing.
     */
    public static void toggle(UUID player, Highlight h) {
        Optional<Active> previous = active(player);
        boolean same = previous.filter(a -> a.highlights().size() == 1
                        && a.highlights().getFirst().anchor().equals(h.anchor()))
                .isPresent();
        clear(player);
        if (!same) {
            show(player, List.of(h));
        }
    }

    /** MC WindowHutAllInventory.locate: shows every block of {@code hs} to {@code player}, replacing any previous. */
    public static void showAll(UUID player, List<Highlight> hs) {
        clear(player);
        if (!hs.isEmpty()) {
            show(player, hs);
        }
    }

    private static void show(UUID player, List<Highlight> hs) {
        PlayerRef ref = Universe.get().getPlayer(player);
        Ref<EntityStore> entity = ref == null ? null : ref.getReference();
        UUID worldId = ref == null ? null : ref.getWorldUuid();
        if (entity == null || !entity.isValid() || worldId == null) {
            return;
        }
        World world = entity.getStore().getExternalData().getWorld();
        List<UUID> glows = new ArrayList<>();
        hs.forEach(h -> GlowingBlock.spawn(world, h.anchor(), MILLIS).ifPresent(glows::add));
        ACTIVE.put(player, new Active(List.copyOf(hs), worldId, System.currentTimeMillis() + MILLIS, glows));
    }

    /** Removes {@code player}'s group and its glows, in the world they were asked in. */
    private static void clear(UUID player) {
        Active previous = ACTIVE.remove(player);
        if (previous == null) {
            return;
        }
        World glowWorld = Universe.get().getWorld(previous.world());
        if (glowWorld != null) {
            previous.glows().forEach(g -> GlowingBlock.remove(glowWorld, g));
        }
    }

    /** True while {@code player}'s highlights include {@code anchor}. */
    public static boolean isActive(UUID player, BlockPos anchor) {
        return active(player)
                .filter(a -> a.highlights().stream().anyMatch(h -> h.anchor().equals(anchor)))
                .isPresent();
    }

    /** {@code player}'s highlights while they last; finished ones are dropped (their glows despawned by themselves). */
    static Optional<Active> active(UUID player) {
        Active a = ACTIVE.get(player);
        if (a != null && a.until() < System.currentTimeMillis()) {
            ACTIVE.remove(player, a);
            return Optional.empty();
        }
        return Optional.ofNullable(a);
    }
}
