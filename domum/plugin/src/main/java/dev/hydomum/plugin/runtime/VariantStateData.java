package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A variant's state table (state name -> state BlockType key), built in code: vanilla builds it only while decoding
 * {@code State.Definitions}, and its fields are private, so this subclass answers every lookup from its own maps.
 * Shared by the variant's main block and all its state blocks, as vanilla shares one table per block family.
 */
final class VariantStateData extends StateData {
    private final Map<String, String> stateToBlock;
    private final Map<String, String> blockToState = new HashMap<>();

    /** @param stateToBlock every non-default state of the variant -> its BlockType key */
    VariantStateData(Map<String, String> stateToBlock) {
        this.stateToBlock = Map.copyOf(stateToBlock);
        stateToBlock.forEach((state, block) -> blockToState.put(block, state));
    }

    @Override
    public @Nullable String getBlockForState(String state) {
        return stateToBlock.get(state);
    }

    @Override
    public @Nullable String getStateForBlock(String blockTypeKey) {
        return blockToState.get(blockTypeKey);
    }

    @Override
    public Set<String> getStateNames() {
        return stateToBlock.keySet();
    }

    @Override
    public String toString() {
        return "VariantStateData{stateToBlock=" + stateToBlock + "}";
    }

    /** State name -> block id for the client, as {@code StateData.toPacket} does; unregistered states are left out. */
    @Override
    public Map<String, Integer> toPacket(BlockType current) {
        Map<String, Integer> data = new HashMap<>();
        for (String state : stateToBlock.keySet()) {
            String key = current.getBlockKeyForState(state);
            int index =
                    key == null ? Integer.MIN_VALUE : BlockType.getAssetMap().getIndex(key);
            if (index != Integer.MIN_VALUE) {
                data.put(state, index);
            }
        }
        return data;
    }
}
