package dev.hyangler.plugin.cast.line;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The rod's tip from the angler's feet, in its model's frame (+x its left, +y up, +z forward), as the client draws it
 * (fishing-hytale.md § 7.5). The client turns the holding arm with the look: the item set our rod inherits
 * (zip:Server/Item/Animations/Item.json, "Camera": {"Pitch": {"AngleRange": {"Max": 60, "Min": -30}, "TargetNodes":
 * ["Head", "RShoulder"]}}) pitches the right shoulder. The tracks were measured looking level, so the tip turns about
 * the shoulder by the look's pitch, clamped to that range.
 */
final class ArmPitch {
    /**
     * The right shoulder's pivot (R-Shoulder) in blocks in the model's frame: (-14.5, 86.59, -0.94) units in
     * Characters/Player.blockymodel, its parents' shape offsets counted (models.placed, Belly +8, Chest +11; Blockbench
     * agrees), at 64 units a block; no rest animation moves it.
     */
    private static final double SHOULDER_Y = 1.353;

    private static final double SHOULDER_Z = -0.015;
    /** The arm's pitch range from Item.json's Camera.Pitch.AngleRange, in radians. */
    private static final double MIN_PITCH = Math.toRadians(-30);

    private static final double MAX_PITCH = Math.toRadians(60);

    private ArmPitch() {}

    /**
     * Fills out (x, y, z) with the line's sampled tip, turned about the shoulder by its angler's look pitch (positive
     * up, Transform.getDirection); the sampled tip as is when the angler has no head rotation.
     */
    static void tip(Line line, CommandBuffer<EntityStore> buffer, double[] out) {
        float[] s = line.sample;
        int tip = TipTracks.TIP;
        out[0] = s[tip];
        out[1] = s[tip + 1];
        out[2] = s[tip + 2];
        HeadRotation head = buffer.getComponent(line.angler, HeadRotation.getComponentType());
        if (head == null) {
            return;
        }
        double a = Math.clamp(head.getRotation().pitch(), MIN_PITCH, MAX_PITCH);
        // about the shoulder's left-right axis: a positive pitch turns forward toward up
        double dy = out[1] - SHOULDER_Y;
        double dz = out[2] - SHOULDER_Z;
        out[1] = SHOULDER_Y + dz * Math.sin(a) + dy * Math.cos(a);
        out[2] = SHOULDER_Z + dz * Math.cos(a) - dy * Math.sin(a);
    }
}
