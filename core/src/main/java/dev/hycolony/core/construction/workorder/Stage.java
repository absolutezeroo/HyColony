package dev.hycolony.core.construction.workorder;

/** A work order's current phase. */
public enum Stage {
    CLEAR,
    SOLID,
    DECORATE,
    /**
     * UPGRADE only, after DECORATE: mines the previous level's blocks, still as it placed them, that the new plan does
     * not want. Deviation from MC: its CLEAR_NON_SOLIDS (AbstractEntityAIStructure.structureStep) only mines where the
     * new blueprint has air, as its levels share one footprint; ours are distinct prefabs, so the old level's
     * leftovers outside the new plan are removed explicitly.
     */
    CLEAR_LEFTOVERS,
    REMOVE,
    DONE
}
