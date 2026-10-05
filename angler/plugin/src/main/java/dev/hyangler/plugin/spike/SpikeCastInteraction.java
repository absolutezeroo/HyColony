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
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Throwaway (plan task 2): throws the spike bobber at the charged power, tied by our line (beam HyAngler_Line) to the
 * hand bone moved by LineOffset to the rod's tip (fishing-hytale.md § 7.5), straight or sagging (Sag); each cast ends
 * its scripted catch in the next of catch, escape and snap. Its asset ids are written here, not in the
 * id-map (CLAUDE.md § 7): this code goes with task 14.
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
                    new KeyedCodec<>("LineOffset", Vector3fUtil.CODEC),
                    (o, v) -> o.lineOffset = v,
                    o -> o.lineOffset,
                    (o, p) -> o.lineOffset = p.lineOffset)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Sag", Codec.BOOLEAN), (o, v) -> o.sag = v, o -> o.sag, (o, p) -> o.sag = p.sag)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Compensate", Codec.BOOLEAN),
                    (o, v) -> o.compensate = v,
                    o -> o.compensate,
                    (o, p) -> o.compensate = p.compensate)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Points", Codec.INTEGER),
                    (o, v) -> o.points = v,
                    o -> o.points,
                    (o, p) -> o.points = p.points)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Reach", Codec.DOUBLE),
                    (o, v) -> o.reach = v,
                    o -> o.reach,
                    (o, p) -> o.reach = p.reach)
            .add()
            .appendInherited(
                    new KeyedCodec<>("PitchArm", Codec.BOOLEAN),
                    (o, v) -> o.pitchArm = v,
                    o -> o.pitchArm,
                    (o, p) -> o.pitchArm = p.pitchArm)
            .add()
            .build();
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long LIFETIME_MS = 60_000L;
    private static final AtomicInteger CASTS = new AtomicInteger();
    /** The least share of the chord a sagging line's carriers may span (Reach). */
    private static final double MIN_REACH = 0.1;

    private static volatile boolean failed;
    private double power = 1.0;
    private Vector3f lineOffset = new Vector3f();
    private boolean sag;
    private boolean compensate;
    private int points = SpikeLine.DEFAULT_POINTS;
    private double reach = SpikeLine.DEFAULT_REACH;
    private boolean pitchArm;

    /**
     * Throws the bobber from the user's eye along its look, with our line to the user's hand bone (R-Attachment)
     * moved by LineOffset: one straight beam, or with Sag a chain of Points carriers that hangs (SpikeLine); locks the
     * user's view in third person for the cast (SpikeCamera). Never throws: Hytale removes the entity whose
     * interaction fails (InteractionSystems.java:227-229), here the player.
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
        marker.angler = user;
        marker.ending = Math.floorMod(CASTS.getAndIncrement(), SpikeCatchDemo.ENDING_COUNT);
        tieLine(marker, buffer, bobber, look.getPosition());
        buffer.addComponent(bobber, SpikeBobberSystem.bobberType(), marker);
        SpikeCamera.lockThirdPerson(user, buffer);
    }

    /** Ties the bobber to the angler's rod tip by our line, straight or sagging; without the line asset, nothing. */
    private void tieLine(SpikeBobber marker, CommandBuffer<EntityStore> buffer, Ref<EntityStore> bobber, Vector3d at) {
        int line = Beam.getAssetMap().getIndex("HyAngler_Line");
        Ref<EntityStore> angler = marker.angler;
        if (line == Integer.MIN_VALUE || angler == null) {
            return;
        }
        marker.line = line;
        marker.compensate = compensate;
        marker.reach = Math.clamp(reach, MIN_REACH, 1);
        marker.pitchArm = pitchArm;
        // the cast blends in from the held charge, where the arm is when the bobber leaves; read only while the cast's
        // blend (2 ticks) outlasts SpikeTipFollow's lead plus the bobber's first tick, so never at a lead of 2
        SpikeTipTracks.last("CastCharging", marker.blendFrom);
        marker.sentOffset[0] = lineOffset.x;
        marker.sentOffset[1] = lineOffset.y;
        marker.sentOffset[2] = lineOffset.z;
        AttachedBeam toTip = SpikeLine.toTip(line, angler, lineOffset);
        if (sag) {
            marker.carriers = SpikeLine.tie(buffer, bobber, at, toTip, points);
        } else {
            buffer.addComponent(bobber, BeamComponent.getComponentType(), new BeamComponent(toTip));
        }
    }

    /** Nothing in simulation: an entity without a remote client runs both (InteractionManager.java:509-530). */
    @Override
    protected void simulateFirstRun(
            @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldown) {}
}
