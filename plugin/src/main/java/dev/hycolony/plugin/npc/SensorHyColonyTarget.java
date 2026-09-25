package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import javax.annotation.Nonnull;

public final class SensorHyColonyTarget extends SensorBase {
    private final PositionProvider positionProvider = new PositionProvider();

    public SensorHyColonyTarget(@Nonnull BuilderSensorHyColonyTarget builder) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, support, dt, store)) {
            positionProvider.clear();
            return false;
        }
        MoveTarget target = store.getComponent(ref, HyColonyComponents.moveTarget());
        if (target == null || !target.active) {
            positionProvider.clear();
            return false;
        }
        positionProvider.setTarget(target.target);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider;
    }
}
