package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import java.util.logging.Level;

/** A Hytale damage cause's index, resolved by id on first use (the asset map is loaded by then). World thread only. */
public final class CauseIndex {
    /** The asset map's answer for an unknown id. */
    public static final int MISSING = Integer.MIN_VALUE;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String id;
    private int index = MISSING;
    private boolean resolved;

    public CauseIndex(String id) {
        this.id = id;
    }

    /** The cause's index; {@link #MISSING} without it, logged once as a WARNING. */
    public int get() {
        if (!resolved) {
            index = DamageCause.getAssetMap().getIndex(id);
            resolved = true;
            if (index == MISSING) {
                LOG.at(Level.WARNING).log("HyColony: damage cause '%s' not found", id);
            }
        }
        return index;
    }
}
