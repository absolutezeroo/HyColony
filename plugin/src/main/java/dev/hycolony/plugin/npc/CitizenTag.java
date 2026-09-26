package dev.hycolony.plugin.npc;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Persistent tag linking an NPC to its core citizen. Saved with the entity. */
public final class CitizenTag implements Component<EntityStore> {
    public static final BuilderCodec<CitizenTag> CODEC = BuilderCodec.builder(CitizenTag.class, CitizenTag::new)
            .append(new KeyedCodec<>("ColonyId", Codec.INTEGER), (t, v) -> t.colonyId = v, t -> t.colonyId)
            .add()
            .append(new KeyedCodec<>("CitizenId", Codec.INTEGER), (t, v) -> t.citizenId = v, t -> t.citizenId)
            .add()
            .build();

    private int colonyId;
    private int citizenId;

    public CitizenTag() {}

    public CitizenTag(int colonyId, int citizenId) {
        this.colonyId = colonyId;
        this.citizenId = citizenId;
    }

    public int colonyId() {
        return colonyId;
    }

    public int citizenId() {
        return citizenId;
    }

    @Override
    public Component<EntityStore> clone() {
        return new CitizenTag(colonyId, citizenId);
    }
}
