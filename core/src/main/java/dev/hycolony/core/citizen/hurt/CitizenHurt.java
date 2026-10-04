package dev.hycolony.core.citizen.hurt;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.port.BodyId;

/**
 * Who may hurt a citizen and how much a hit takes before its armour (MC EntityCitizen.hurt, checkIfValidDamageSource
 * and handleDamagePerformed). Raids and guards are not ported: their branches are left out.
 */
public final class CitizenHurt {
    /** MC handleDamagePerformed: a hit takes at most this share of the maximum health (MC 0.2f). */
    static final double MAX_HIT_SHARE = 0.2;
    /** MC checkIfValidDamageSource: a player without HURT_CITIZEN deals at most 1 of MC's 20 health points. */
    static final double PLAYER_WITHOUT_RIGHT_MAX_POINTS = 1;

    private CitizenHurt() {}

    /**
     * The damage a hit of {@code amount} by {@code source} does to {@code body} before its armour: 0 (cancelled) for
     * a citizen of the same colony and for a player without HURT_CITIZEN above 1 point, else at most a fifth of the
     * body's maximum health. Deviation from MC (Hytale world): MC's points on its 20 health → the same share of the
     * body's maximum (100).
     */
    public static double allowed(Colony colony, BodyId body, double amount, HurtSource source) {
        double max = colony.context().health().maxHealth(body);
        boolean refused = switch (source) {
            case HurtSource.Citizen c -> c.colonyId() == colony.id();
            case HurtSource.Player p ->
                amount > PLAYER_WITHOUT_RIGHT_MAX_POINTS * max / CitizenData.MC_MAX_HEALTH
                        && !colony.permissions().hasPermission(p.id(), Action.HURT_CITIZEN);
            case HurtSource.None _, HurtSource.Creature _ -> false;
        };
        return refused ? 0 : Math.min(amount, max * MAX_HIT_SHARE);
    }
}
