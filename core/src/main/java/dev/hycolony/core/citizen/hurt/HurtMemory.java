package dev.hycolony.core.citizen.hurt;

/** A citizen's memory of its last attacker, which stops its healing (MC getLastHurtByMob). Runtime only. */
public final class HurtMemory {
    /**
     * Ticks an attacker is remembered. Deviation from MC (Hytale world): Minecraft's 100-tick attacker memory (MC
     * LivingEntity.getLastHurtByMob) → Health.json's NoDamageTaken delay of 15 s.
     */
    public static final long HURT_MEMORY_TICKS = 300;

    /** The tick the attacker is forgotten at; 0 when never attacked. */
    private long forgottenAt;

    /** MC setLastHurtByMob: an entity hurt the citizen at {@code tick}. */
    public void attacked(long tick) {
        forgottenAt = tick + HURT_MEMORY_TICKS;
    }

    /** Whether an attacker hurt the citizen less than {@link #HURT_MEMORY_TICKS} before {@code tick}. */
    public boolean recentlyAttacked(long tick) {
        return tick < forgottenAt;
    }
}
