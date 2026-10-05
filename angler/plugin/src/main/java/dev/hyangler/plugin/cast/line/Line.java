package dev.hyangler.plugin.cast.line;

import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * One fishing line, from a bobber to its angler's rod tip (spec § 7.2, fishing-hytale.md § 7.5): a sagging chain of
 * beams through carriers (LineChain), whose last segment follows the tip of the angler's current HyAngler_Rod entry
 * (TipFollow, TipTracks, ArmPitch). World thread only; never saved.
 */
public final class Line {
    final int beam;
    final Ref<EntityStore> angler;
    final List<Ref<EntityStore>> carriers;
    boolean cut;
    /** The current sag per block of chord, easing between LineChain.SLACK and TAUT. */
    double sagShare = LineChain.SLACK;
    /** The angler's entry the tip follows, and the line's tick it started on. */
    String pose = "Cast";

    int poseStart;
    /** The line's ticks laid so far. */
    int ticks;
    /** This tick's sample of the pose (TipTracks.SAMPLE_SIZE values), filled in place. */
    final float[] sample = new float[TipTracks.SAMPLE_SIZE];
    /** The tip offset last sent to the clients, in blocks. */
    final float[] sentOffset = new float[3];
    /** The sample a new pose blends from. */
    final float[] blendFrom = new float[TipTracks.SAMPLE_SIZE];
    /** This tick's tip from the angler's feet, in its model's frame. */
    final double[] tipModel = new double[3];
    /** The last pose whose line change was logged. */
    String loggedPose = "";

    private Line(int beam, Ref<EntityStore> angler, List<Ref<EntityStore>> carriers) {
        this.beam = beam;
        this.angler = angler;
        this.carriers = carriers;
    }

    /**
     * Ties a new line from the bobber (and its carriers, spawned at start: the angler's eye) to the angler's rod tip
     * at rest, the cast pose blending in from the held charge; empty when the beam asset is unknown. The carriers
     * despawn on their own after assets.lifetimeSeconds, a safety net only (remove).
     */
    public static Optional<Line> tie(
            CommandBuffer<EntityStore> buffer,
            Ref<EntityStore> bobber,
            Vector3dc start,
            Ref<EntityStore> angler,
            LineAssets assets) {
        int beam = Beam.getAssetMap().getIndex(assets.beamId());
        if (beam == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        float[] rest = new float[TipTracks.SAMPLE_SIZE];
        TipTracks.last(TipTracks.REST, rest);
        Vector3f restOffset = new Vector3f(rest[0], rest[1], rest[2]);
        List<Ref<EntityStore>> carriers = LineChain.tie(
                buffer, bobber, start, LineChain.toTip(beam, angler, restOffset), assets.lifetimeSeconds());
        Line line = new Line(beam, angler, carriers);
        // the cast blends in from the held charge, where the arm is when the bobber leaves
        TipTracks.last("CastCharging", line.blendFrom);
        System.arraycopy(rest, 0, line.sentOffset, 0, line.sentOffset.length);
        return Optional.of(line);
    }

    /** Starts entry as the pose the tip follows from the next tick laid, blending from the current sample. */
    public void pose(String entry) {
        TipFollow.pose(this, entry);
    }

    /**
     * One tick: samples the tip of the angler's pose, moves the line's end onto it, and lays the carriers between the
     * bobber (at bobberAt) and it, taut while a fish pulls. Does nothing once cut or with its angler gone.
     */
    public void lay(Vector3dc bobberAt, boolean taut, CommandBuffer<EntityStore> buffer) {
        ticks++;
        if (cut || !angler.isValid()) {
            return;
        }
        TipFollow.sample(this);
        TipFollow.follow(this, carriers.getLast(), buffer);
        TransformComponent anglerAt = buffer.getComponent(angler, TransformComponent.getComponentType());
        if (anglerAt != null) {
            LineChain.lay(this, bobberAt, anglerAt, taut, buffer);
        }
    }

    /** Snaps the line at once: the bobber's beam and every carrier go; nothing puts it back. */
    public void snap(Ref<EntityStore> bobber, CommandBuffer<EntityStore> buffer) {
        cut = true;
        buffer.tryRemoveComponent(bobber, BeamComponent.getComponentType());
        LineChain.cut(carriers, buffer);
    }

    /** Removes the carriers still there, with their bobber: the line goes whole. */
    public void remove(CommandBuffer<EntityStore> buffer) {
        cut = true;
        LineChain.cut(carriers, buffer);
    }

    /** A copy for a cloned component: the same carriers, its own arrays. */
    public Line copy() {
        Line copy = new Line(beam, angler, carriers);
        copy.cut = cut;
        copy.sagShare = sagShare;
        copy.pose = pose;
        copy.poseStart = poseStart;
        copy.ticks = ticks;
        System.arraycopy(sample, 0, copy.sample, 0, sample.length);
        System.arraycopy(sentOffset, 0, copy.sentOffset, 0, sentOffset.length);
        System.arraycopy(blendFrom, 0, copy.blendFrom, 0, blendFrom.length);
        System.arraycopy(tipModel, 0, copy.tipModel, 0, tipModel.length);
        copy.loggedPose = loggedPose;
        return copy;
    }

    /**
     * What a line is made of: its beam asset (the id-map's) and how long its carriers live on their own, which must
     * outlast the bobber so they never go first or on the same tick.
     */
    public record LineAssets(String beamId, float lifetimeSeconds) {}
}
