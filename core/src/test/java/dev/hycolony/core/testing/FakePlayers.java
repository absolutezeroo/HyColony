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

public final class FakePlayers implements PlayerDirectory {
    public final Map<UUID, BlockPos> online = new LinkedHashMap<>();
    public final Set<UUID> creativeOperators = new HashSet<>();

    @Override public boolean isCreativeOperator(UUID player) { return creativeOperators.contains(player); }

    @Override public boolean isOnline(UUID player) { return online.containsKey(player); }
    @Override public Optional<BlockPos> position(UUID player) { return Optional.ofNullable(online.get(player)); }
    @Override public Collection<UUID> onlineIn(WorldKey world) { return List.copyOf(online.keySet()); }
}
