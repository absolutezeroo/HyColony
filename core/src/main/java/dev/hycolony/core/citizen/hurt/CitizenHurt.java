package dev.hycolony.core.citizen.hurt;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.body.BodyHealth;

/**
 * Who may hurt a citizen and how much a hit takes before its armour (MC EntityCitizen.hurt, checkIfValidDamageSource
 * and handleDamagePerformed), and the harm its surroundings do (a wall, a stuck walk). Raids, guards and the PvP mode
 * (MC pvp_mode: a colony's citizen hitting another colony's makes it hostile) are not ported: their branches are
 * left out.
 */
public final class CitizenHurt {
    /** MC handleDamagePerformed: a hit takes at most this share of the maximum health (MC 0.2f). */
    static final double MAX_HIT_SHARE = 0.2;
    /** MC checkIfValidDamageSource: a player without HURT_CITIZEN deals at most 1 of MC's 20 health points. */
    static final double PLAYER_WITHOUT_RIGHT_MAX_POINTS = 1;
    /** MC MinecoloniesAdvancedPathNavigate: withTakeDamageOnStuck(0.2f), the share of maximum health a stuck costs. */
    static final double STUCK_DAMAGE_SHARE = 0.2;

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

    /**
     * MC handleInWallDamage: a citizen suffocating in a block takes no damage and is moved to a free spot where it
     * stands (MC TeleportHelper.teleportCitizen at its blockPosition: woken first, its walks reset, through its AI).
     * Nothing for a body without a position.
     */
    public static void outOfWall(Colony colony, BodyId body) {
        CitizenBodies bodies = colony.context().bodies();
        bodies.position(body)
                .ifPresent(at -> colony.citizens()
                        .citizenOf(body)
                        .flatMap(d -> colony.citizens().ai(d.id()))
                        .ifPresentOrElse(ai -> ai.teleport(at), () -> bodies.teleport(body, at)));
    }

    /**
     * MC PathingStuckHandler.completeStuckAction: a citizen whose walk is fully stuck takes {@link #STUCK_DAMAGE_SHARE}
     * of its maximum health (MC withTakeDamageOnStuck(0.2f), MinecoloniesAdvancedPathNavigate), teleported or not.
     * Nothing without a living body.
     */
    public static void stuck(Colony colony, CitizenData citizen) {
        BodyHealth health = colony.context().health();
        colony.citizens()
                .bodyOf(citizen.id())
                .filter(colony.context().bodies()::isAlive)
                .ifPresent(b -> health.damage(b, health.maxHealth(b) * STUCK_DAMAGE_SHARE));
    }
}
