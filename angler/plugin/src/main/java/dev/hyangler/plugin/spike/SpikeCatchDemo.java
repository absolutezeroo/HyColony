package dev.hyangler.plugin.spike;

import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Throwaway (animation spike, fishing-hytale.md § 7.4): a floating bobber's scripted catch, played on its angler from
 * Java at fixed times: bite, hook, light then heavy fight, pumping, reeling, then a catch, an escape or a snapped line,
 * in turn from one cast to the next. Times in Hytale's world ticks (30 a second, TickingThread.java:17).
 */
final class SpikeCatchDemo {
    /** The endings a bobber's ending indexes: 0, 1 or 2. */
    static final int ENDING_COUNT = 3;

    private static final int CATCH = 0;
    private static final int SNAP = 2;
    private static final String SET = "HyAngler_Rod";
    /** The floating ticks of the catch's entries, then of its ending; ENTRIES and ENDINGS name them. */
    private static final int[] TICKS = {90, 120, 140, 260, 380, 560, 680};

    private static final String[] ENTRIES = {"Bite", "Hook", "FightLight", "FightHeavy", "FightPump", "ReelFight"};
    /** Indexed by the bobber's ending: CATCH first, SNAP last. */
    private static final String[] ENDINGS = {"Catch", "Escape", "Snap"};

    private static final int END_TICK = TICKS[TICKS.length - 1];
    private static final int DONE_TICK = END_TICK + 60;

    private SpikeCatchDemo() {}

    /** Whether a fish pulls the line taut: from the hook to the ending, and on through a catch's lift. */
    static boolean taut(SpikeBobber bobber) {
        int tick = bobber.floatingTicks;
        return bobber.floating && tick >= TICKS[1] && (tick < END_TICK || bobber.ending == CATCH);
    }

    /**
     * Plays the entry due at the bobber's floating tick, if any, and records it as the bobber's pose for its line; at
     * the end, stops the angler's Action slot and removes the bobber, its line and camera lock going with it
     * (SpikeBobberRemoval). A snap drops the line at once.
     * A bobber whose angler is gone is only removed.
     */
    static void step(SpikeBobber bobber, Ref<EntityStore> self, CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> angler = bobber.angler;
        if (angler == null || !angler.isValid()) {
            buffer.tryRemoveEntity(self, RemoveReason.REMOVE);
            return;
        }
        int tick = ++bobber.floatingTicks;
        for (int i = 0; i < TICKS.length; i++) {
            if (TICKS[i] == tick) {
                String entry = i < ENTRIES.length ? ENTRIES[i] : ENDINGS[bobber.ending];
                AnimationUtils.playAnimation(angler, AnimationSlot.Action, SET, entry, true, buffer);
                SpikeTipFollow.pose(bobber, entry);
            }
        }
        if (tick == END_TICK && bobber.ending == SNAP) {
            bobber.lineCut = true;
            buffer.tryRemoveComponent(self, BeamComponent.getComponentType());
            SpikeLine.cut(bobber.carriers, buffer);
        }
        if (tick == DONE_TICK) {
            AnimationUtils.stopAnimation(angler, AnimationSlot.Action, true, buffer);
            buffer.tryRemoveEntity(self, RemoveReason.REMOVE);
        }
    }
}
