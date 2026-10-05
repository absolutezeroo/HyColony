package dev.hyangler.plugin.cast.line;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.joml.Vector3f;

/**
 * Keeps the line's last segment on the rod's tip (fishing-hytale.md § 7.5). The client puts a beam's offset in the
 * hand bone as the playing entry poses it, so the server only adds the rod's own bend, sampled from TipTracks, and
 * changes the beam in place (replacing it flickers). The sample runs LEAD_TICKS ahead (the change reaches the client
 * after the animation it follows) and blends from the previous pose as the client does (BlendingDuration). Allocates
 * only while the tip moves (beams are immutable).
 */
final class TipFollow {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The smallest move of the tip's offset, in blocks, worth a beam change. */
    private static final float RETARGET_BLOCKS = 0.01f;
    /** How far ahead the sample runs, in world ticks: a calibration knob, judged in game (2026-10-04). */
    private static final int LEAD_TICKS = 2;

    private TipFollow() {}

    /** Starts entry as the line's pose from its next tick, blending from the last sample. */
    static void pose(Line line, String entry) {
        line.pose = entry;
        line.poseStart = line.ticks + 1;
        System.arraycopy(line.sample, 0, line.blendFrom, 0, line.blendFrom.length);
    }

    /**
     * Fills the line's sample for this tick from its pose, LEAD_TICKS ahead, blended in from the previous pose, and
     * back to rest (blended too) once a non-looping entry has played out.
     */
    static void sample(Line line) {
        int t = line.ticks - line.poseStart + LEAD_TICKS;
        if (TipTracks.playedOut(line.pose, t)) {
            // the client starts Idle itself when the entry ends, blending from its last frame: rest time counts
            // from that end, not from this tick
            int sinceEnd = t - TipTracks.endTicks(line.pose);
            TipTracks.last(line.pose, line.blendFrom);
            line.pose = TipTracks.REST;
            line.poseStart = line.ticks + LEAD_TICKS - sinceEnd;
            t = sinceEnd;
        }
        float[] s = line.sample;
        TipTracks.at(line.pose, t, s);
        int blend = TipTracks.blendTicks(line.pose);
        if (t < blend) {
            float k = t / (float) blend;
            for (int j = 0; j < s.length; j++) {
                s[j] = line.blendFrom[j] + (s[j] - line.blendFrom[j]) * k;
            }
        }
    }

    /** Moves the beam held by holder (the last carrier) to the sample's offset if it moved enough; logs at FINE. */
    static void follow(Line line, Ref<EntityStore> holder, CommandBuffer<EntityStore> buffer) {
        if (!holder.isValid()) {
            return;
        }
        float[] s = line.sample;
        float[] old = line.sentOffset;
        if (Math.abs(s[0] - old[0]) + Math.abs(s[1] - old[1]) + Math.abs(s[2] - old[2]) < RETARGET_BLOCKS) {
            return;
        }
        BeamComponent beams = buffer.getComponent(holder, BeamComponent.getComponentType());
        if (beams == null) {
            return;
        }
        AttachedBeam tip = LineChain.toTip(line.beam, line.angler, new Vector3f(s[0], s[1], s[2]));
        beams.set(List.of(tip));
        System.arraycopy(s, 0, old, 0, old.length);
        if (!line.pose.equals(line.loggedPose)) {
            line.loggedPose = line.pose;
            LOG.atFine().log("HyAngler: line follows %s, offset (%.2f, %.2f, %.2f)", line.pose, s[0], s[1], s[2]);
        }
    }
}
