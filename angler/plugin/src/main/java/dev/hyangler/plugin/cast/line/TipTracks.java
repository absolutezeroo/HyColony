package dev.hyangler.plugin.cast.line;

import java.util.Map;
import java.util.Objects;

/**
 * Where the rod's tip is along each HyAngler_Rod entry (fishing-hytale.md § 7.5), measured in a Blockbench session on
 * Hytale's player every 3 animation frames, on the skeleton the eight rods share (R-Attachment, Rod, its four
 * sections, Rod_Tip_Top and Rod_Tip_Ring_*: the same in every model). Each sample holds, in blocks: the line's offset
 * in the frame of the hand bone (R-Attachment) as the entry poses it, the client's frame for a beam (seen in game), so
 * only the rod's own bend moves it; then the tip from the angler's feet in the model's frame (+x its left, +y up, +z
 * forward).
 */
final class TipTracks {
    /** The values of a sample: offset x, y, z in the posed hand bone, then tip x, y, z from the feet. */
    static final int SAMPLE_SIZE = 6;
    /** Where a sample's tip from the feet starts. */
    static final int TIP = 3;

    static final String REST = "Idle";
    /** Animation frames between two samples, at 60 frames a second. */
    private static final int SAMPLE_FRAMES = 3;
    /** Animation frames per world tick: 60 a second over Hytale's 30 ticks (TickingThread.java:17). */
    private static final int FRAMES_PER_TICK = 2;

    private enum Mode {
        LOOP,
        HOLD,
        ONCE
    }

    /**
     * An entry's track: its mode (the set's Looping, the file's holdLastKeyframe), length in frames, the set's
     * BlendingDuration in ticks, samples. Idle is cut to its first frame, the rest pose (HOLD over 0 frames).
     */
    private static final class Track {
        final Mode mode;
        final int frames;
        final int blendTicks;
        final float[][] samples;

        Track(Mode mode, int frames, int blendTicks, float[][] samples) {
            this.mode = mode;
            this.frames = frames;
            this.blendTicks = blendTicks;
            this.samples = samples;
        }
    }

    private static final Map<String, Track> TRACKS = Map.ofEntries(
            Map.entry("Idle", new Track(Mode.HOLD, 0, 6, new float[][] {
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
            })),
            Map.entry("CastCharging", new Track(Mode.HOLD, 30, 6, new float[][] {
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
                {0f, 1.771f, 0.117f, -0.262f, 2.152f, 1.635f},
                {0f, 1.762f, 0.176f, -0.327f, 2.499f, 1.489f},
                {0f, 1.753f, 0.22f, -0.371f, 2.947f, 1.127f},
                {0f, 1.747f, 0.242f, -0.342f, 3.293f, 0.595f},
                {0f, 1.751f, 0.228f, -0.239f, 3.448f, 0.03f},
                {0f, 1.759f, 0.193f, -0.107f, 3.445f, -0.444f},
                {0f, 1.766f, 0.154f, 0.012f, 3.366f, -0.786f},
                {0f, 1.77f, 0.121f, 0.096f, 3.28f, -0.994f},
                {0f, 1.772f, 0.099f, 0.106f, 3.266f, -1.009f},
                {0f, 1.773f, 0.086f, 0.112f, 3.258f, -1.017f},
            })),
            Map.entry("Cast", new Track(Mode.ONCE, 24, 2, new float[][] {
                {0f, 1.773f, 0.086f, 0.112f, 3.258f, -1.017f},
                {0f, 1.711f, -0.334f, -0.198f, 3.39f, -0.094f},
                {0f, 1.674f, -0.421f, -0.337f, 2.68f, 1.572f},
                {0f, 1.764f, 0.182f, -0.255f, 1.327f, 2.316f},
                {0f, 1.732f, 0.327f, -0.306f, 0.795f, 2.281f},
                {0f, 1.775f, 0.025f, -0.276f, 1.083f, 2.288f},
                {0f, 1.775f, 0.016f, -0.258f, 1.268f, 2.292f},
                {0f, 1.774f, 0.078f, -0.251f, 1.356f, 2.3f},
                {0f, 1.773f, 0.086f, -0.245f, 1.421f, 2.297f},
            })),
            Map.entry("Bite", new Track(Mode.ONCE, 30, 6, new float[][] {
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
                {0f, 1.759f, 0.172f, -0.24f, 1.948f, 1.714f},
                {0f, 1.768f, 0.129f, -0.236f, 1.985f, 1.691f},
                {0f, 1.772f, 0.102f, -0.234f, 2.007f, 1.674f},
                {0f, 1.761f, 0.166f, -0.24f, 1.954f, 1.711f},
                {0f, 1.772f, 0.098f, -0.233f, 2.01f, 1.672f},
                {0f, 1.76f, 0.182f, -0.241f, 1.942f, 1.722f},
                {0f, 1.745f, 0.246f, -0.247f, 1.885f, 1.755f},
                {0f, 1.76f, 0.183f, -0.241f, 1.941f, 1.723f},
                {0f, 1.77f, 0.118f, -0.235f, 1.994f, 1.684f},
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
            })),
            Map.entry("Hook", new Track(Mode.HOLD, 18, 2, new float[][] {
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
                {0f, 1.672f, 0.469f, -0.179f, 2.672f, 1.272f},
                {0f, 1.508f, 0.704f, -0.154f, 3.092f, 0.509f},
                {0f, 1.525f, 0.686f, -0.15f, 3.099f, 0.484f},
                {0f, 1.557f, 0.648f, -0.143f, 3.112f, 0.437f},
                {0f, 1.581f, 0.618f, -0.137f, 3.12f, 0.4f},
                {0f, 1.592f, 0.603f, -0.134f, 3.123f, 0.381f},
            })),
            Map.entry("FightLight", new Track(Mode.LOOP, 60, 6, new float[][] {
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.675f, 0.455f, -0.325f, 2.317f, 1.607f},
                {0f, 1.66f, 0.483f, -0.333f, 2.267f, 1.639f},
                {0f, 1.645f, 0.509f, -0.341f, 2.218f, 1.669f},
                {0f, 1.635f, 0.527f, -0.347f, 2.183f, 1.688f},
                {0f, 1.629f, 0.537f, -0.35f, 2.164f, 1.697f},
                {0f, 1.635f, 0.526f, -0.346f, 2.186f, 1.686f},
                {0f, 1.651f, 0.5f, -0.338f, 2.24f, 1.656f},
                {0f, 1.669f, 0.467f, -0.327f, 2.303f, 1.617f},
                {0f, 1.684f, 0.437f, -0.318f, 2.358f, 1.579f},
                {0f, 1.694f, 0.414f, -0.311f, 2.398f, 1.549f},
                {0f, 1.701f, 0.399f, -0.306f, 2.424f, 1.529f},
                {0f, 1.703f, 0.393f, -0.304f, 2.434f, 1.521f},
                {0f, 1.702f, 0.397f, -0.305f, 2.427f, 1.527f},
                {0f, 1.699f, 0.404f, -0.308f, 2.413f, 1.538f},
                {0f, 1.695f, 0.413f, -0.311f, 2.395f, 1.551f},
                {0f, 1.691f, 0.422f, -0.314f, 2.378f, 1.564f},
                {0f, 1.687f, 0.43f, -0.317f, 2.364f, 1.574f},
                {0f, 1.684f, 0.435f, -0.319f, 2.353f, 1.582f},
                {0f, 1.683f, 0.44f, -0.32f, 2.345f, 1.588f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
            })),
            Map.entry("FightHeavy", new Track(Mode.LOOP, 40, 6, new float[][] {
                {0f, 1.508f, 0.704f, -0.315f, 2.554f, 1.274f},
                {0f, 1.477f, 0.735f, -0.333f, 2.496f, 1.321f},
                {0f, 1.424f, 0.781f, -0.36f, 2.396f, 1.391f},
                {0f, 1.393f, 0.806f, -0.374f, 2.335f, 1.427f},
                {0f, 1.398f, 0.802f, -0.37f, 2.348f, 1.42f},
                {0f, 1.442f, 0.766f, -0.343f, 2.441f, 1.361f},
                {0f, 1.492f, 0.72f, -0.307f, 2.544f, 1.283f},
                {0f, 1.528f, 0.683f, -0.277f, 2.616f, 1.218f},
                {0f, 1.548f, 0.66f, -0.259f, 2.657f, 1.177f},
                {0f, 1.549f, 0.659f, -0.259f, 2.658f, 1.176f},
                {0f, 1.538f, 0.671f, -0.275f, 2.631f, 1.204f},
                {0f, 1.525f, 0.686f, -0.293f, 2.598f, 1.235f},
                {0f, 1.515f, 0.697f, -0.306f, 2.572f, 1.258f},
                {0f, 1.509f, 0.703f, -0.313f, 2.557f, 1.271f},
                {0f, 1.508f, 0.704f, -0.315f, 2.554f, 1.274f},
            })),
            Map.entry("FightPump", new Track(Mode.LOOP, 90, 6, new float[][] {
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.678f, 0.449f, -0.317f, 2.365f, 1.573f},
                {0f, 1.671f, 0.465f, -0.308f, 2.427f, 1.524f},
                {0f, 1.659f, 0.489f, -0.294f, 2.518f, 1.444f},
                {0f, 1.643f, 0.519f, -0.276f, 2.623f, 1.336f},
                {0f, 1.624f, 0.55f, -0.257f, 2.728f, 1.209f},
                {0f, 1.604f, 0.581f, -0.237f, 2.821f, 1.073f},
                {0f, 1.585f, 0.609f, -0.218f, 2.896f, 0.938f},
                {0f, 1.567f, 0.634f, -0.202f, 2.954f, 0.814f},
                {0f, 1.551f, 0.654f, -0.188f, 2.995f, 0.704f},
                {0f, 1.537f, 0.671f, -0.176f, 3.024f, 0.61f},
                {0f, 1.526f, 0.685f, -0.167f, 3.044f, 0.532f},
                {0f, 1.516f, 0.695f, -0.16f, 3.057f, 0.47f},
                {0f, 1.509f, 0.703f, -0.154f, 3.066f, 0.424f},
                {0f, 1.513f, 0.699f, -0.158f, 3.061f, 0.457f},
                {0f, 1.536f, 0.673f, -0.181f, 3.021f, 0.641f},
                {0f, 1.571f, 0.63f, -0.22f, 2.914f, 0.935f},
                {0f, 1.611f, 0.574f, -0.268f, 2.716f, 1.265f},
                {0f, 1.648f, 0.513f, -0.317f, 2.446f, 1.555f},
                {0f, 1.678f, 0.455f, -0.359f, 2.151f, 1.766f},
                {0f, 1.7f, 0.405f, -0.393f, 1.875f, 1.898f},
                {0f, 1.715f, 0.364f, -0.418f, 1.642f, 1.971f},
                {0f, 1.726f, 0.333f, -0.436f, 1.46f, 2.007f},
                {0f, 1.733f, 0.311f, -0.448f, 1.33f, 2.021f},
                {0f, 1.733f, 0.311f, -0.446f, 1.342f, 2.019f},
                {0f, 1.726f, 0.333f, -0.428f, 1.522f, 1.989f},
                {0f, 1.715f, 0.364f, -0.4f, 1.772f, 1.917f},
                {0f, 1.703f, 0.394f, -0.371f, 2.001f, 1.816f},
                {0f, 1.693f, 0.417f, -0.348f, 2.169f, 1.718f},
                {0f, 1.686f, 0.433f, -0.331f, 2.278f, 1.641f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
            })),
            Map.entry("ReelFight", new Track(Mode.LOOP, 30, 6, new float[][] {
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
            })),
            Map.entry("Reel", new Track(Mode.LOOP, 30, 6, new float[][] {
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
            })),
            Map.entry("Escape", new Track(Mode.ONCE, 40, 6, new float[][] {
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.773f, 0.086f, -0.362f, 2.109f, 1.757f},
                {0f, 1.765f, -0.115f, -0.399f, 1.84f, 1.869f},
                {0f, 1.775f, 0.047f, -0.422f, 1.705f, 1.957f},
                {0f, 1.765f, 0.169f, -0.438f, 1.595f, 2.008f},
                {0f, 1.772f, 0.113f, -0.431f, 1.647f, 1.986f},
                {0f, 1.775f, 0.071f, -0.425f, 1.685f, 1.968f},
                {0f, 1.774f, 0.076f, -0.426f, 1.679f, 1.97f},
                {0f, 1.774f, 0.084f, -0.427f, 1.673f, 1.973f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
                {0f, 1.773f, 0.086f, -0.427f, 1.671f, 1.974f},
            })),
            Map.entry("Snap", new Track(Mode.ONCE, 40, 0, new float[][] {
                {0f, 1.508f, 0.704f, -0.315f, 2.554f, 1.274f},
                {0f, 1.74f, -0.236f, -0.081f, 2.874f, -0.36f},
                {0f, 1.775f, -0.007f, -0.057f, 2.915f, -0.534f},
                {0f, 1.754f, 0.223f, -0.118f, 3.076f, 0.068f},
                {0f, 1.772f, 0.114f, -0.147f, 3.032f, 0.365f},
                {0f, 1.775f, 0.031f, -0.18f, 2.934f, 0.676f},
                {0f, 1.775f, 0.056f, -0.23f, 2.751f, 1.067f},
                {0f, 1.774f, 0.082f, -0.266f, 2.574f, 1.309f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
                {0f, 1.773f, 0.086f, -0.266f, 2.573f, 1.312f},
            })),
            Map.entry("Catch", new Track(Mode.ONCE, 30, 6, new float[][] {
                {0f, 1.681f, 0.442f, -0.321f, 2.34f, 1.592f},
                {0f, 1.687f, 0.429f, -0.297f, 2.515f, 1.487f},
                {0f, 1.698f, 0.402f, -0.247f, 2.821f, 1.229f},
                {0f, 1.705f, 0.382f, -0.207f, 3.018f, 0.99f},
                {0f, 1.709f, 0.371f, -0.187f, 3.103f, 0.857f},
                {0f, 1.729f, 0.318f, -0.183f, 3.038f, 0.943f},
                {0f, 1.757f, 0.212f, -0.183f, 2.85f, 1.146f},
                {0f, 1.77f, 0.128f, -0.194f, 2.588f, 1.365f},
                {0f, 1.773f, 0.086f, -0.209f, 2.338f, 1.518f},
                {0f, 1.773f, 0.086f, -0.224f, 2.139f, 1.618f},
                {0f, 1.773f, 0.086f, -0.232f, 2.019f, 1.664f},
            })));
    private static final Track REST_TRACK = Objects.requireNonNull(TRACKS.get(REST));

    private TipTracks() {}

    /** The entry's track; an unknown entry, the rest pose's. */
    private static Track track(String entry) {
        Track track = TRACKS.get(entry);
        return track == null ? REST_TRACK : track;
    }

    /** The ticks the client takes to blend into the entry (its BlendingDuration); an unknown entry, Idle's. */
    static int blendTicks(String entry) {
        return track(entry).blendTicks;
    }

    /** Whether a non-looping, non-held entry has played out ticks after it started: the client is back at rest. */
    static boolean playedOut(String entry, int ticks) {
        Track track = TRACKS.get(entry);
        return track != null && track.mode == Mode.ONCE && ticks >= endTicks(track);
    }

    /** The tick after its start an entry ends on (its first tick at or past its last frame); an unknown one, 0. */
    static int endTicks(String entry) {
        Track track = TRACKS.get(entry);
        return track == null ? 0 : endTicks(track);
    }

    /** Fills out with an entry's last sample (an unknown entry: the rest pose's). */
    static void last(String entry, float[] out) {
        float[][] samples = track(entry).samples;
        System.arraycopy(samples[samples.length - 1], 0, out, 0, SAMPLE_SIZE);
    }

    private static int endTicks(Track track) {
        return (track.frames + FRAMES_PER_TICK - 1) / FRAMES_PER_TICK;
    }

    /**
     * Fills out with the sample at ticks after the entry started, interpolated between the two nearest; a HOLD entry
     * played out keeps its last sample; an unknown entry, or a ONCE one played out, gives the rest pose.
     */
    static void at(String entry, int ticks, float[] out) {
        Track track = track(entry);
        int frame = Math.max(0, ticks) * FRAMES_PER_TICK;
        if (track.mode == Mode.LOOP) {
            frame %= track.frames;
        } else if (frame >= track.frames) {
            track = track.mode == Mode.HOLD ? track : REST_TRACK;
            frame = track.frames;
        }
        float[][] samples = track.samples;
        if (samples.length == 1) {
            System.arraycopy(samples[0], 0, out, 0, SAMPLE_SIZE);
            return;
        }
        // the last sample sits on the entry's last frame, closer than SAMPLE_FRAMES to the one before
        int i = Math.min(frame / SAMPLE_FRAMES, samples.length - 2);
        int from = i * SAMPLE_FRAMES;
        int to = i == samples.length - 2 ? track.frames : from + SAMPLE_FRAMES;
        float k = to == from ? 0f : (frame - from) / (float) (to - from);
        for (int j = 0; j < SAMPLE_SIZE; j++) {
            out[j] = samples[i][j] + (samples[i + 1][j] - samples[i][j]) * k;
        }
    }
}
