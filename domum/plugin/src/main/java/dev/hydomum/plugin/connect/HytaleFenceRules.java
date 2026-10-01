package dev.hydomum.plugin.connect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.plugin.registry.CodecMapRegistry;
import com.hypixel.hytale.server.core.prefab.selection.mask.BlockPattern;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil.ConnectedBlockResult;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hydomum.core.connect.ConnectedShape;
import dev.hydomum.core.connect.Connections;
import dev.hydomum.core.connect.Joiner;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.joml.Vector3ic;

/**
 * The connection rules of a HyDomum fence or wall (type {@value #TYPE}): its template's shapes, chosen from its four
 * neighbours by MC's rule ({@link Connections}) for its family ({@code Joins}) instead of the template's patterns, so a
 * full face joins too.
 */
public final class HytaleFenceRules extends CustomTemplateConnectedBlockRuleSet {
    /** The rule set type our fences and walls name in their BlockType. */
    public static final String TYPE = "HyDomum_Fence";

    /** The CustomTemplate keys, plus {@code Joins}: WoodenFence, Fence, Wall or Pane. */
    public static final BuilderCodec<HytaleFenceRules> CODEC = BuilderCodec.builder(
                    HytaleFenceRules.class, HytaleFenceRules::new, CustomTemplateConnectedBlockRuleSet.CODEC)
            .append(
                    new KeyedCodec<>("Joins", new EnumCodec<>(Joiner.class)),
                    (rules, joiner) -> rules.joiner = joiner,
                    rules -> rules.joiner)
            .add()
            .build();

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean FAILED = new AtomicBoolean();

    // Hytale builds rule sets through CODEC, with no way to hand them a collaborator: register() sets it once, before
    // any is used.
    private static volatile HytaleNeighbours neighbours = new HytaleNeighbours(Map.of());

    private Joiner joiner = Joiner.FENCE;

    /**
     * Adds our type to Hytale's rule set codec through registry (the plugin's, which removes it on shutdown), our rule
     * sets reading their neighbours with reader; call from the plugin's setup(), before the BlockTypes naming the type
     * are decoded.
     */
    public static void register(
            HytaleNeighbours reader,
            CodecMapRegistry<ConnectedBlockRuleSet, Codec<? extends ConnectedBlockRuleSet>> registry) {
        neighbours = reader;
        registry.register(TYPE, HytaleFenceRules.class, CODEC);
    }

    Joiner joiner() {
        return joiner;
    }

    /**
     * The shape and turn MC's rule gives the block at at from its neighbours; empty (no change) when a neighbour's
     * section is not loaded, when the template has no pattern for the shape, or on a failure, logged.
     */
    @Override
    @SuppressWarnings("PMD.ExcessiveParameterList") // Hytale's ConnectedBlockRuleSet signature
    public Optional<ConnectedBlockResult> getConnectedBlockType(
            ChunkStore chunkStore,
            Vector3ic at,
            BlockType blockType,
            int rotation,
            Vector3ic placementNormal,
            boolean isPlacement) {
        try {
            return neighbours
                    .around(chunkStore, at)
                    .flatMap(around -> result(ConnectedShape.of(Connections.joinedSides(joiner, around))));
        } catch (RuntimeException e) {
            LOG.at(FAILED.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                    "HyDomum: could not shape the fence at %s", at);
            return Optional.empty();
        }
    }

    /** The block of shape's pattern, turned by shape's yaw; empty when the template has no such shape. */
    private Optional<ConnectedBlockResult> result(ConnectedShape shape) {
        BlockPattern pattern = getShapeNameToBlockPatternMap().get(shape.name());
        BlockPattern.BlockEntry entry = pattern == null ? null : pattern.nextBlockTypeKey(ThreadLocalRandom.current());
        if (entry == null) {
            return Optional.empty();
        }
        Rotation yaw = RotationTuple.get(entry.rotation()).yaw().add(Rotation.VALUES[shape.yaw()]);
        return Optional.of(new ConnectedBlockResult(
                entry.blockTypeKey(), RotationTuple.of(yaw, Rotation.None).index()));
    }
}
