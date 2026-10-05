package dev.hyangler.plugin.spike;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.joml.Vector3f;

/**
 * Throwaway (line-follows-the-rod spike, fishing-hytale.md § 7.5): keeps the line's last segment on the rod's tip. The
 * client puts a beam's offset in the hand bone as the playing entry poses it, so the server only adds the rod's own
 * bend, sampled from SpikeTipTracks, and changes the beam in place (replacing it flickers). With compensate, the
 * sample runs LEAD_TICKS ahead (the change reaches the client after the animation it follows) and blends from the
 * previous pose as the client does (BlendingDuration). Allocates only while the tip moves (beams are immutable).
 */
final class SpikeTipFollow {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The smallest move of the tip's offset, in blocks, worth a beam change. */
    private static final float RETARGET_BLOCKS = 0.01f;
    /** How far ahead a compensated sample runs, in world ticks: a calibration knob, judged in game. */
    private static final int LEAD_TICKS = 2;

    private SpikeTipFollow() {}

    /** Starts entry as the bobber's pose on this tick, its line blending from the last sample. */
    static void pose(SpikeBobber bobber, String entry) {
        bobber.pose = entry;
        bobber.poseStart = bobber.ticks;
        System.arraycopy(bobber.sample, 0, bobber.blendFrom, 0, bobber.blendFrom.length);
    }

    /**
     * Fills the bobber's sample for this tick from its pose; compensated, ahead by LEAD_TICKS, blended in from the
     * previous pose, and back to rest (blended too) once a non-looping entry has played out.
     */
    static void sample(SpikeBobber bobber) {
        int lead = bobber.compensate ? LEAD_TICKS : 0;
        int t = bobber.ticks - bobber.poseStart + lead;
        if (bobber.compensate && SpikeTipTracks.playedOut(bobber.pose, t)) {
            // the client starts Idle itself when the entry ends, blending from its last frame: rest time counts
            // from that end, not from this tick
            int sinceEnd = t - SpikeTipTracks.endTicks(bobber.pose);
            SpikeTipTracks.last(bobber.pose, bobber.blendFrom);
            bobber.pose = SpikeTipTracks.REST;
            bobber.poseStart = bobber.ticks + lead - sinceEnd;
            t = sinceEnd;
        }
        float[] s = bobber.sample;
        SpikeTipTracks.at(bobber.pose, t, s);
        int blend = bobber.compensate ? SpikeTipTracks.blendTicks(bobber.pose) : 0;
        if (t < blend) {
            float k = t / (float) blend;
            for (int j = 0; j < s.length; j++) {
                s[j] = bobber.blendFrom[j] + (s[j] - bobber.blendFrom[j]) * k;
            }
        }
    }

    /**
     * Moves the beam held by holder (the last carrier, or the bobber of a straight line) to the sample's offset if it
     * moved enough; logs each pose's first change at INFO. Does nothing once the line snapped.
     */
    static void follow(SpikeBobber bobber, Ref<EntityStore> holder, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> angler = bobber.angler;
        if (angler == null || !holder.isValid() || bobber.lineCut) {
            return;
        }
        float[] s = bobber.sample;
        float[] old = bobber.sentOffset;
        if (Math.abs(s[0] - old[0]) + Math.abs(s[1] - old[1]) + Math.abs(s[2] - old[2]) < RETARGET_BLOCKS) {
            return;
        }
        BeamComponent beams = buffer.getComponent(holder, BeamComponent.getComponentType());
        if (beams == null) {
            return;
        }
        AttachedBeam tip = SpikeLine.toTip(bobber.line, angler, new Vector3f(s[0], s[1], s[2]));
        beams.set(List.of(tip));
        System.arraycopy(s, 0, old, 0, old.length);
        if (!bobber.pose.equals(bobber.loggedPose)) {
            bobber.loggedPose = bobber.pose;
            LOG.atInfo().log(
                    "HyAngler spike: line moved for %s (compensated %s), offset (%.2f, %.2f, %.2f)",
                    bobber.pose, bobber.compensate, s[0], s[1], s[2]);
        }
    }
}
