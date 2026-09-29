package dev.hycolony.core.construction.workorder;

/** A work order's current phase. */
public enum Stage {
    CLEAR,
    SOLID,
    DECORATE,
    /**
     * After DECORATE. For a Hytale prefab (UPGRADE only): mines the previous level's blocks, still as it placed them,
     * that the new plan does not want. Deviation from MC: its CLEAR_NON_SOLIDS (AbstractEntityAIStructure.structureStep)
     * only mines where the new blueprint has air, as its levels share one footprint; our prefabs are distinct, so the
     * old level's leftovers outside the new plan are removed explicitly. For a MineColonies plan (every order): MC
     * CLEAR_NON_SOLIDS itself, the blocks on the plan's air cells. Deviation from MC: it runs after DECORATE, not
     * before, and leaves fluids (MC CLEAR_WATER is not ported).
     */
    CLEAR_LEFTOVERS,
    REMOVE,
    DONE
}
