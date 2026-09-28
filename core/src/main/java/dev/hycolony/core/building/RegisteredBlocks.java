package dev.hycolony.core.building;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The blocks of its plan a hut registered once placed, besides the hut block: its containers (MC
 * AbstractBuildingContainer.containerList) and its crafting benches (MC FurnaceUserModule, the furnaces a smelter got
 * from AbstractBuilding.registerBlockPosition). A block a player places never comes here.
 */
public final class RegisteredBlocks {
    private final BlockPos hut;
    private final Set<BlockPos> containers = new LinkedHashSet<>();
    private final Map<BlockPos, Workstation> workstations = new LinkedHashMap<>();

    RegisteredBlocks(BlockPos hut) {
        this.hut = hut;
    }

    /** MC AbstractBuildingContainer.addContainerPosition; the hut block is always a container, never registered. */
    public void addContainer(BlockPos pos) {
        if (!pos.equals(hut)) {
            containers.add(pos);
        }
    }

    /** MC AbstractBuildingContainer.removeContainerPosition; no-op if {@code pos} is not registered. */
    public void removeContainer(BlockPos pos) {
        containers.remove(pos);
    }

    /** The registered containers, read-only, in registration order; the hut block is not among them. */
    public Set<BlockPos> containers() {
        return Collections.unmodifiableSet(containers);
    }

    /**
     * MC FurnaceUserModule.onBlockPlacedInBuilding for a bench the builder placed from the plan, with the tier the plan
     * gave it; replaces the bench already at {@code pos}.
     */
    public void addWorkstation(BlockPos pos, Workstation workstation) {
        workstations.put(pos, workstation);
    }

    /** Forgets the bench at {@code pos} once broken (MC FurnaceUserModule.removeFromFurnaces); no-op if none. */
    public void removeWorkstation(BlockPos pos) {
        workstations.remove(pos);
    }

    /** The registered benches by position, read-only, in registration order. */
    public Map<BlockPos, Workstation> workstations() {
        return Collections.unmodifiableMap(workstations);
    }
}
