package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.ArrayList;
import java.util.List;

public final class FakeWorldEffects implements WorldEffects {
    public final List<BlockPos> celebrated = new ArrayList<>();
    public final List<BlockPos> hits = new ArrayList<>();
    public final List<Float> hitProgress = new ArrayList<>();

    @Override
    public void celebrate(BlockPos hut) {
        celebrated.add(hut);
    }

    @Override
    public void blockHit(BlockPos pos, float progress) {
        hits.add(pos);
        hitProgress.add(progress);
    }
}
