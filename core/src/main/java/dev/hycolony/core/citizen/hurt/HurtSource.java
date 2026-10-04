package dev.hycolony.core.citizen.hurt;

import java.util.UUID;

/** Who dealt a hit to a citizen, as MC EntityCitizen.hurt tells them apart (DamageSource.getEntity). */
public sealed interface HurtSource {
    /** No entity: the world (a fall, drowning, a trap). */
    record None() implements HurtSource {}

    /** A player, by its id (MC ServerPlayer). */
    record Player(UUID id) implements HurtSource {}

    /** A citizen of colony {@code colonyId} (MC EntityCitizen). */
    record Citizen(int colonyId) implements HurtSource {}

    /** Any other living entity (MC LivingEntity: a monster, an animal). */
    record Creature() implements HurtSource {}

    /** Whether an entity dealt it (MC getLastHurtByMob is set: the citizen does not heal meanwhile). */
    default boolean attacker() {
        return !(this instanceof None);
    }
}
