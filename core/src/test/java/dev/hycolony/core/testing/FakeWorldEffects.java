package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.ArrayList;
import java.util.List;

public final class FakeWorldEffects implements WorldEffects {
    public final List<BlockPos> celebrated = new ArrayList<>();
    public final List<BlockPos> hits = new ArrayList<>();
    public final List<Float> hitProgress = new ArrayList<>();
    public final List<BlockPos> tilled = new ArrayList<>();
    public final List<BlockPos> placed = new ArrayList<>();
    public final List<Vec3> sleeps = new ArrayList<>();

    @Override
    public void celebrate(BlockPos hut) {
        celebrated.add(hut);
    }

    @Override
    public void blockHit(BlockPos pos, float progress) {
        hits.add(pos);
        hitProgress.add(progress);
    }

    @Override
    public void tilled(BlockPos soil) {
        tilled.add(soil);
    }

    @Override
    public void blockPlaced(BlockPos pos) {
        placed.add(pos);
    }

    @Override
    public void sleeping(Vec3 at) {
        sleeps.add(at);
    }
}
