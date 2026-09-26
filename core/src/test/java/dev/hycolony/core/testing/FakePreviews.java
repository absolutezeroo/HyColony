package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.PreviewPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps each player's current previews, and records every {@link #show}. */
public final class FakePreviews implements PreviewPort {
    public record Shown(BlockPos origin, List<Block> blocks) {}

    public final Map<UUID, Map<String, Shown>> current = new HashMap<>();
    /** Every show, as "player/id". */
    public final List<String> shows = new ArrayList<>();

    @Override
    public void show(UUID player, String id, BlockPos origin, List<Block> blocks) {
        current.computeIfAbsent(player, p -> new HashMap<>()).put(id, new Shown(origin, List.copyOf(blocks)));
        shows.add(player + "/" + id);
    }

    @Override
    public void hide(UUID player, String id) {
        Map<String, Shown> mine = current.get(player);
        if (mine != null) {
            mine.remove(id);
        }
    }

    @Override
    public void hideAll(UUID player) {
        current.remove(player);
    }

    /** The player's previews, empty if none. */
    public Map<String, Shown> of(UUID player) {
        return current.getOrDefault(player, Map.of());
    }
}
