package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class FakePlayers implements PlayerDirectory {
    /** Every player the game knows by name, online or not. */
    public final Map<UUID, String> names = new LinkedHashMap<>();

    public final Map<UUID, BlockPos> online = new LinkedHashMap<>();
    public final Set<UUID> operators = new HashSet<>();
    public final Set<UUID> creative = new HashSet<>();
    /** Both an operator and in creative mode. */
    public final Set<UUID> creativeOperators = new HashSet<>();

    private final Map<UUID, Integer> facing = new LinkedHashMap<>();

    @Override
    public Optional<String> name(UUID player) {
        return Optional.ofNullable(names.get(player));
    }

    @Override
    public void findByName(String name, Consumer<Optional<Profile>> then) {
        then.accept(names.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .findFirst()
                .map(e -> new Profile(e.getKey(), e.getValue())));
    }

    @Override
    public boolean isOperator(UUID player) {
        return operators.contains(player) || creativeOperators.contains(player);
    }

    @Override
    public boolean isCreative(UUID player) {
        return creative.contains(player) || creativeOperators.contains(player);
    }

    @Override
    public boolean isOnline(UUID player) {
        return online.containsKey(player);
    }

    @Override
    public Optional<BlockPos> position(UUID player) {
        return Optional.ofNullable(online.get(player));
    }

    @Override
    public Collection<UUID> onlineIn(WorldKey world) {
        return List.copyOf(online.keySet());
    }

    @Override
    public int facing(UUID player) {
        return facing.getOrDefault(player, 0);
    }

    /** Sets the quarter-turn direction {@link #facing} reports for {@code player}; 0 (north) until set. */
    public void setFacing(UUID player, int quarterTurn) {
        facing.put(player, quarterTurn);
    }
}
