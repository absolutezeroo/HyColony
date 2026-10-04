package dev.hycolony.plugin.npc.hurt;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.Role;
import dev.hycolony.core.citizen.death.DeathCause;
import dev.hycolony.plugin.npc.BodyTeleport;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** What killed a citizen body, as the core tells it (MC DamageSource for its death message). */
final class DeathCauses {
    /** The damage causes of 0.7.0-pre.5 (Server/Entity/Damage), each with its {@code citizen.deathCause} text. */
    private static final Set<String> KNOWN = Set.of(
            "Bludgeoning",
            "Command",
            "Crush",
            "Drowning",
            "Earth",
            "Elemental",
            "Environment",
            "Environmental",
            "Fall",
            "Fire",
            "Ice",
            "Lightning",
            "OutOfWorld",
            "Physical",
            "Poison",
            "Projectile",
            "Slashing",
            "Suffocation",
            "Water",
            "Wind");

    private static final String UNKNOWN = "Unknown";

    private DeathCauses() {}

    /**
     * The cause of {@code cause} ("Unknown" for one without a text, a mod's), and the entity behind {@code damage}: a
     * player's name, a citizen's ({@code citizenNames}), or {@code %} and an NPC role's name key; no killer for the
     * world or an entity gone.
     */
    static DeathCause of(
            @Nullable DamageCause cause,
            @Nullable Damage damage,
            Store<EntityStore> store,
            Function<CitizenTag, Optional<String>> citizenNames) {
        String id = cause != null && KNOWN.contains(cause.getId()) ? cause.getId() : UNKNOWN;
        return new DeathCause(id, killer(damage, store, citizenNames));
    }

    private static Optional<String> killer(
            @Nullable Damage damage, Store<EntityStore> store, Function<CitizenTag, Optional<String>> citizenNames) {
        if (damage == null || !(damage.getSource() instanceof Damage.EntitySource entity)) {
            return Optional.empty();
        }
        Ref<EntityStore> ref = entity.getRef();
        if (!ref.isValid()) {
            return Optional.empty();
        }
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            return Optional.of(player.getUsername());
        }
        CitizenTag citizen = store.getComponent(ref, HyColonyComponents.citizenTag());
        Optional<String> name = citizen == null ? Optional.empty() : citizenNames.apply(citizen);
        if (name.isPresent()) {
            return name;
        }
        Role role = BodyTeleport.role(store, ref);
        return role == null ? Optional.empty() : Optional.of("%" + role.getNameTranslationKey());
    }
}
