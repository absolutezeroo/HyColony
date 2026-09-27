package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.TreeMap;

/**
 * A citizen's walking speed factor (MC MOVEMENT_SPEED over its base), applied as one of the infinite HyColony speed
 * effects of the id-map. NPCEntity.getCurrentHorizontalSpeedMultiplier multiplies the Walk controller's MaxWalkSpeed
 * by the HorizontalSpeedMultiplier of every active effect, and RoleSystems clears that cache each tick; the
 * controller's own maximum is final, so an effect is the only runtime handle on it.
 *
 * <p>Deviation from MC: MC sets the attribute to any value; here the factor snaps to the nearest effect of the table
 * (steps of 0.05), and a factor closer to 1 than to the first step removes every speed effect.
 */
public final class CitizenSpeed {
    private final double[] factors;
    private final String[] ids;
    private int[] indexes;

    /** {@code effects}: speed factor above 1 -> entity effect id. */
    public CitizenSpeed(Map<Double, String> effects) {
        TreeMap<Double, String> sorted = new TreeMap<>(effects);
        this.factors = sorted.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        this.ids = sorted.values().toArray(String[]::new);
    }

    /**
     * Keeps exactly the effect nearest to {@code factor} on the NPC (none near 1) and removes the other speed effects,
     * including one saved with the entity. Cheap when nothing changes; no effect without an effect controller.
     */
    public void apply(Ref<EntityStore> ref, double factor, Store<EntityStore> store) {
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }
        int[] known = indexes();
        int wanted = nearest(factors, factor);
        int keep = wanted < 0 ? Integer.MIN_VALUE : known[wanted];
        for (int index : known) {
            // hasEffect is false for Integer.MIN_VALUE (an id missing from the assets).
            if (index != keep && effects.hasEffect(index)) {
                effects.removeEffect(ref, index, RemovalBehavior.COMPLETE, store);
            }
        }
        // getAsset(int) is null out of bounds, so also for Integer.MIN_VALUE.
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(keep);
        if (effect != null && !effects.hasEffect(keep)) {
            effects.addInfiniteEffect(ref, keep, effect, store);
        }
    }

    /** Index of the step nearest to {@code factor}, or -1 when 1 (no effect) is nearer than every step. */
    static int nearest(double[] steps, double factor) {
        int best = -1;
        double bestDistance = Math.abs(factor - 1);
        for (int i = 0; i < steps.length; i++) {
            double d = Math.abs(factor - steps[i]);
            if (d < bestDistance) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * Asset indexes, resolved on first use (assets are loaded by then); Integer.MIN_VALUE for a missing id. Cached for
     * the runtime's life: an asset hot-reload that renumbers or adds these effects is not supported (restart).
     */
    private int[] indexes() {
        if (indexes == null) {
            int[] out = new int[ids.length];
            for (int i = 0; i < ids.length; i++) {
                out[i] = EntityEffect.getAssetMap().getIndex(ids[i]);
            }
            indexes = out;
        }
        return indexes;
    }
}
