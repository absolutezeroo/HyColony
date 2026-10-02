package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Commands} section of config.json (MC ServerConfiguration commands). */
final class CommandsSection {
    static final BuilderCodec<CommandsSection> CODEC = BuilderCodec.builder(CommandsSection.class, CommandsSection::new)
            .append(
                    new KeyedCodec<>("CanPlayerUseShowColonyInfoCommand", Codec.BOOLEAN),
                    (s, v) -> s.canPlayerUseShowColonyInfoCommand = v,
                    s -> s.canPlayerUseShowColonyInfoCommand)
            .add()
            .append(
                    new KeyedCodec<>("CanPlayerUseAddOfficerCommand", Codec.BOOLEAN),
                    (s, v) -> s.canPlayerUseAddOfficerCommand = v,
                    s -> s.canPlayerUseAddOfficerCommand)
            .add()
            .append(
                    new KeyedCodec<>("CanPlayerUseDeleteColonyCommand", Codec.BOOLEAN),
                    (s, v) -> s.canPlayerUseDeleteColonyCommand = v,
                    s -> s.canPlayerUseDeleteColonyCommand)
            .add()
            .append(
                    new KeyedCodec<>("CanPlayerUseModifyCitizensCommand", Codec.BOOLEAN),
                    (s, v) -> s.canPlayerUseModifyCitizensCommand = v,
                    s -> s.canPlayerUseModifyCitizensCommand)
            .add()
            .build();

    private static final ColonyConfig.Commands DEFAULTS =
            ColonyConfig.defaults().commands();

    boolean canPlayerUseShowColonyInfoCommand = DEFAULTS.canPlayerUseShowColonyInfoCommand();
    boolean canPlayerUseAddOfficerCommand = DEFAULTS.canPlayerUseAddOfficerCommand();
    boolean canPlayerUseDeleteColonyCommand = DEFAULTS.canPlayerUseDeleteColonyCommand();
    boolean canPlayerUseModifyCitizensCommand = DEFAULTS.canPlayerUseModifyCitizensCommand();

    ColonyConfig.Commands toCore() {
        return new ColonyConfig.Commands(
                canPlayerUseShowColonyInfoCommand,
                canPlayerUseAddOfficerCommand,
                canPlayerUseDeleteColonyCommand,
                canPlayerUseModifyCitizensCommand);
    }
}
