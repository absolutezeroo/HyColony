package dev.hyangler.plugin.cast;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyangler.plugin.AnglerIds;

/**
 * Plays the angler's HyAngler_Rod entries the server times (spec § 7.1 bis): Bite at the bite, Hook then Catch at a
 * catch, Escape for a fish missed, Snap for a broken line; and sets the pose the line's tip follows.
 */
final class CastAnimations {
    static final String BITE = "Bite";
    static final String HOOK = "Hook";
    static final String CATCH = "Catch";
    static final String ESCAPE = "Escape";
    static final String SNAP = "Snap";
    /** World ticks from Hook to Catch: Hook's 18 frames at 60 a second (Hook.blockyanim), 30 world ticks a second. */
    static final int HOOK_TICKS = 9;

    private final String set;

    CastAnimations(AnglerIds ids) {
        this.set = ids.animations();
    }

    /** Plays entry on the bobber's angler (Action slot) and makes it the pose its line follows; nothing without one. */
    void play(Bobber bobber, String entry, ComponentAccessor<EntityStore> accessor) {
        Ref<EntityStore> angler = bobber.angler;
        if (angler == null || !angler.isValid() || set.isEmpty()) {
            return;
        }
        AnimationUtils.playAnimation(angler, AnimationSlot.Action, set, entry, true, accessor);
        if (bobber.line != null) {
            bobber.line.pose(entry);
        }
    }
}
