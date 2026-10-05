package dev.hyangler.plugin.cast.line;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

/**
 * The line as a chain of straight beams through invisible carrier entities (spec § 7.2, fishing-hytale.md § 7.5):
 * bobber → carriers → the angler's hand bone moved to the rod's tip, the carriers laid each tick on a parabola under
 * the bobber-to-tip chord: a belly when slack, nearly straight when taut. Hytale's beam is one straight segment and
 * cannot sag by itself.
 */
final class LineChain {
    /** Carriers between the bobber and the rod's tip: 19 segments, the user's choice in game (2026-10-04). */
    static final int POINTS = 18;
    /** The share of the chord the carriers span: 19 even segments, the last one straight to the client's tip. */
    private static final double REACH = POINTS / (POINTS + 1.0);
    /** The line's width, as Hytale's hookshot rope (Projectile_Config_Hookshot). */
    private static final float SCALE = 0.25f;
    /** Sag at mid-line per block of chord, slack and taut, and its cap in blocks. */
    static final double SLACK = 0.12;

    private static final double TAUT = 0.015;
    private static final double MAX_SAG = 2.5;
    /**
     * The share of the gap to the target sag the line closes each tick: a struck or freed line tightens or slackens
     * over about a third of a second (95 % in 11 ticks), as a real one does, instead of every carrier jumping at once.
     */
    private static final double TENSION_EASE = 0.25;

    private LineChain() {}

    /** The last segment's beam: to the angler's hand bone (R-Attachment) moved by tipOffset, in blocks (§ 7.5). */
    static AttachedBeam toTip(int beam, Ref<EntityStore> angler, Vector3fc tipOffset) {
        return AttachedBeam.toEntity(beam, SCALE, null, Vector3fUtil.ZERO, angler, "R-Attachment", tipOffset);
    }

    /**
     * Spawns POINTS carriers at start, living lifetimeSeconds, and ties the chain: the last carrier holds toTip, each
     * other one a beam to the next, and the bobber gets a beam to the first. Returns the carriers, from the bobber's
     * side.
     */
    static List<Ref<EntityStore>> tie(
            CommandBuffer<EntityStore> buffer,
            Ref<EntityStore> bobber,
            Vector3dc start,
            AttachedBeam toTip,
            float lifetimeSeconds) {
        List<Ref<EntityStore>> carriers = new ArrayList<>(POINTS);
        AttachedBeam next = toTip;
        for (int i = 0; i < POINTS; i++) {
            Ref<EntityStore> carrier = spawn(buffer, start, next, lifetimeSeconds);
            carriers.addFirst(carrier);
            next = AttachedBeam.toEntity(toTip.beamIndex(), SCALE, null, carrier, null);
        }
        buffer.addComponent(bobber, BeamComponent.getComponentType(), new BeamComponent(next));
        return List.copyOf(carriers);
    }

    /**
     * Lays the line's carriers between the bobber (at bobberAt) and the rod's tip of its current sample (pitched with
     * the look, ArmPitch), set from the angler's feet along its yaw, the sag easing toward taut or slack, the last one
     * at REACH of the way to the tip; skips carriers already gone.
     */
    static void lay(
            Line line, Vector3dc bobberAt, TransformComponent angler, boolean taut, CommandBuffer<EntityStore> buffer) {
        double[] m = line.tipModel;
        ArmPitch.tip(line, buffer, m);
        Vector3d feet = angler.getPosition();
        double yaw = angler.getRotation().yaw();
        // forward (-sin, -cos), right (cos, -sin) of the yaw (Transform.getDirection); the model frame has +x on the
        // angler's left and +z forward
        double forward = m[2];
        double right = -m[0];
        double tipX = feet.x - Math.sin(yaw) * forward + Math.cos(yaw) * right;
        double tipY = feet.y + m[1];
        double tipZ = feet.z - Math.cos(yaw) * forward - Math.sin(yaw) * right;
        double chord = Math.sqrt(sq(tipX - bobberAt.x()) + sq(tipY - bobberAt.y()) + sq(tipZ - bobberAt.z()));
        line.sagShare += ((taut ? TAUT : SLACK) - line.sagShare) * TENSION_EASE;
        double sag = Math.min(MAX_SAG, line.sagShare * chord);
        List<Ref<EntityStore>> carriers = line.carriers;
        for (int i = 0; i < carriers.size(); i++) {
            Ref<EntityStore> carrier = carriers.get(i);
            TransformComponent transform =
                    carrier.isValid() ? buffer.getComponent(carrier, TransformComponent.getComponentType()) : null;
            if (transform == null) {
                continue;
            }
            // the carriers stop at REACH of the chord: the last segment, straight to the client's true tip, spreads
            // the server's tip error over its whole length instead of folding it into a short one
            double t = REACH * (i + 1.0) / carriers.size();
            // the live position, no allocation per tick: the tracker sends it when it differs from the sent one
            // (TransformSystems.EntityTrackerUpdate)
            Vector3d p = transform.getPosition();
            p.x = bobberAt.x() + (tipX - bobberAt.x()) * t;
            p.y = bobberAt.y() + (tipY - bobberAt.y()) * t - sag * 4 * t * (1 - t);
            p.z = bobberAt.z() + (tipZ - bobberAt.z()) * t;
        }
    }

    /** Removes the carriers still there, which drops every segment of the line. */
    static void cut(List<Ref<EntityStore>> carriers, CommandBuffer<EntityStore> buffer) {
        for (Ref<EntityStore> carrier : carriers) {
            if (carrier.isValid()) {
                // checked again when the queue runs: an unload or a despawn may have removed it meanwhile, and removing
                // a gone entity throws out of the queue (Store.removeEntity, ref.validate)
                buffer.tryRemoveEntity(carrier, RemoveReason.REMOVE);
            }
        }
    }

    /** A model-less entity holding one beam, as BeamComponent.spawn builds it, despawning on its own. */
    private static Ref<EntityStore> spawn(
            CommandBuffer<EntityStore> buffer, Vector3dc at, AttachedBeam beam, float lifetimeSeconds) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(
                NetworkId.getComponentType(),
                new NetworkId(buffer.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(at, new Rotation3f()));
        holder.addComponent(BeamComponent.getComponentType(), new BeamComponent(beam));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        holder.addComponent(
                DespawnComponent.getComponentType(),
                DespawnComponent.despawnInSeconds(buffer.getResource(TimeResource.getResourceType()), lifetimeSeconds));
        return buffer.addEntity(holder, AddReason.SPAWN);
    }

    private static double sq(double v) {
        return v * v;
    }
}
