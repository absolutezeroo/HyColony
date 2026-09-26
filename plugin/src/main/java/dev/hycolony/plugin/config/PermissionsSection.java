package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.config.Explosions;

/**
 * The {@code Permissions} section of config.json (MC ServerConfiguration permissions). The explosion mode is read as
 * a string and parsed by the core, so an unknown value falls back to MC's default instead of failing the load.
 */
final class PermissionsSection {
    static final BuilderCodec<PermissionsSection> CODEC = BuilderCodec.builder(
                    PermissionsSection.class, PermissionsSection::new)
            .append(
                    new KeyedCodec<>("EnableColonyProtection", Codec.BOOLEAN),
                    (s, v) -> s.enableColonyProtection = v,
                    s -> s.enableColonyProtection)
            .add()
            .append(
                    new KeyedCodec<>("TurnOffExplosionsInColonies", Codec.STRING),
                    (s, v) -> s.turnOffExplosionsInColonies = v,
                    s -> s.turnOffExplosionsInColonies)
            .add()
            .append(
                    new KeyedCodec<>("PermissionEventBypassMinPermLevel", Codec.INTEGER),
                    (s, v) -> s.permissionEventBypassMinPermLevel = v,
                    s -> s.permissionEventBypassMinPermLevel)
            .add()
            .build();

    private static final ColonyConfig.Permissions DEFAULTS =
            ColonyConfig.defaults().permissions();

    boolean enableColonyProtection = DEFAULTS.enableColonyProtection();
    String turnOffExplosionsInColonies = DEFAULTS.turnOffExplosionsInColonies().name();
    int permissionEventBypassMinPermLevel = DEFAULTS.permissionEventBypassMinPermLevel();

    ColonyConfig.Permissions toCore() {
        return new ColonyConfig.Permissions(
                enableColonyProtection,
                Explosions.parse(turnOffExplosionsInColonies),
                permissionEventBypassMinPermLevel);
    }
}
