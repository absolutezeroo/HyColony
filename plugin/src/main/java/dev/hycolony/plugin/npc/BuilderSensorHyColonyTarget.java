package dev.hycolony.plugin.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.Feature;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/** JSON: { "Type": "HyColonyTarget" }. Matches while the colony core has a move target. */
public final class BuilderSensorHyColonyTarget extends BuilderSensorBase {
    @Nonnull
    @Override
    public String getShortDescription() {
        return "Position chosen by the HyColony colony core";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport builderSupport) {
        return new SensorHyColonyTarget(this);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.provideFeature(Feature.Position);
        return this;
    }
}
