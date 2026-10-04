package dev.hyangler.plugin.spike;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Throwaway (plan task 2): throws the spike bobber at the charged power, tied to the hand by Hytale's rope; Freeze
 * picks whether it is pinned in water. Its asset ids are written here, not in the id-map (CLAUDE.md § 7): this code
 * goes with task 14.
 */
public final class SpikeCastInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<SpikeCastInteraction> CODEC = BuilderCodec.builder(
                    SpikeCastInteraction.class, SpikeCastInteraction::new, SimpleInstantInteraction.CODEC)
            .appendInherited(
                    new KeyedCodec<>("Power", Codec.DOUBLE),
                    (o, v) -> o.power = v,
                    o -> o.power,
                    (o, p) -> o.power = p.power)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Freeze", Codec.BOOLEAN),
                    (o, v) -> o.freeze = v,
                    o -> o.freeze,
                    (o, p) -> o.freeze = p.freeze)
            .add()
            .build();
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long LIFETIME_MS = 60_000L;
    private static volatile boolean failed;
    private double power = 1.0;
    private boolean freeze = true;

    /**
     * Throws the bobber from the user's eye along its look, with the rope to its right hand. Never throws: Hytale
     * removes the entity whose interaction fails (InteractionSystems.java:227-229), here the player.
     */
    @Override
    protected void firstRun(
            @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldown) {
        try {
            cast(ctx);
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler spike: cast failed");
            failed = true;
        }
    }

    private void cast(InteractionContext ctx) {
        CommandBuffer<EntityStore> buffer = ctx.getCommandBuffer();
        Ref<EntityStore> user = ctx.getEntity();
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset("HyAngler_Spike_Bobber");
        if (buffer == null || !user.isValid() || config == null) {
            return;
        }
        Transform look = TargetUtil.getLook(user, buffer);
        // spawnProjectile changes its position and direction: copies (fishing-hytale.md § 5.1)
        Ref<EntityStore> bobber = ProjectileModule.get()
                .spawnProjectile(
                        null,
                        null,
                        user,
                        buffer,
                        config,
                        new Vector3d(look.getPosition()),
                        new Vector3d(look.getDirection()),
                        1f,
                        (float) power,
                        LIFETIME_MS);
        SpikeBobber marker = new SpikeBobber();
        marker.freeze = freeze;
        buffer.addComponent(bobber, SpikeBobberSystem.bobberType(), marker);
        int rope = Beam.getAssetMap().getIndex("Rope");
        if (rope != Integer.MIN_VALUE) {
            buffer.addComponent(
                    bobber,
                    BeamComponent.getComponentType(),
                    new BeamComponent(AttachedBeam.toEntity(rope, 0.25f, null, user, "R-Attachment")));
        }
    }

    /** Nothing in simulation: an entity without a remote client runs both (InteractionManager.java:509-530). */
    @Override
    protected void simulateFirstRun(
            @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldown) {}
}
