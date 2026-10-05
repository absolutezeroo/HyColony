package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Throwaway (plan task 2): marks the spike's bobber, pinned in water once floating at the height it entered it; its
 * angler and the ending (0 catch, 1 escape, 2 snap: SpikeCatchDemo) of the scripted catch it plays once floating, and
 * its line: the beam asset, the carriers of a sagging one, the angler's animation it follows (SpikeTipTracks).
 */
final class SpikeBobber implements Component<EntityStore> {
    @Nullable
    Ref<EntityStore> angler;

    /** The sagging line's carriers (SpikeLine), from the bobber's side; empty for a straight line. */
    List<Ref<EntityStore>> carriers = List.of();

    int ending;
    boolean floating;
    double surfaceY;
    int ticks;
    int floatingTicks;
    /** The line's beam asset index. */
    int line;
    /** The angler's HyAngler_Rod entry, and the bobber's tick it started on: the cast plays from the throw. */
    String pose = "Cast";

    int poseStart;
    /** This tick's sample of the pose (SpikeTipTracks.SAMPLE_SIZE values), filled in place. */
    float[] sample = new float[SpikeTipTracks.SAMPLE_SIZE];
    /** The tip offset last sent to the clients, in blocks. */
    float[] sentOffset = new float[3];
    /** Whether the line runs ahead and blends between poses (SpikeTipFollow), and the sample it blends from. */
    boolean compensate;

    float[] blendFrom = new float[SpikeTipTracks.SAMPLE_SIZE];
    /** Whether the line snapped: nothing may put it back. */
    boolean lineCut;
    /** The sagging line's current sag per block of chord, easing between SpikeLine.SLACK and TAUT. */
    double sagShare = SpikeLine.SLACK;
    /** The share of the bobber-to-tip chord the carriers span, the rest one straight segment to the tip (Reach). */
    double reach = SpikeLine.DEFAULT_REACH;
    /** Whether the tip turns with the angler's look pitch (SpikeArmPitch), and this tick's tip in the model's frame. */
    boolean pitchArm;

    double[] tipModel = new double[3];
    /** The last pose whose line change was logged. */
    String loggedPose = "";

    @Override
    public Component<EntityStore> clone() {
        SpikeBobber copy = new SpikeBobber();
        copy.angler = angler;
        copy.carriers = carriers;
        copy.ending = ending;
        copy.floating = floating;
        copy.surfaceY = surfaceY;
        copy.ticks = ticks;
        copy.floatingTicks = floatingTicks;
        copy.line = line;
        copy.pose = pose;
        copy.poseStart = poseStart;
        copy.sample = sample.clone();
        copy.sentOffset = sentOffset.clone();
        copy.compensate = compensate;
        copy.blendFrom = blendFrom.clone();
        copy.lineCut = lineCut;
        copy.sagShare = sagShare;
        copy.reach = reach;
        copy.pitchArm = pitchArm;
        copy.tipModel = tipModel.clone();
        copy.loggedPose = loggedPose;
        return copy;
    }
}
