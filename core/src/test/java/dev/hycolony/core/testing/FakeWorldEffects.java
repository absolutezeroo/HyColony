package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.ArrayList;
import java.util.List;

public final class FakeWorldEffects implements WorldEffects {
    public final List<BlockPos> celebrated = new ArrayList<>();

    @Override
    public void celebrate(BlockPos hut) {
        celebrated.add(hut);
    }
}
