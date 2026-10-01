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
import dev.hydomum.core.connect.Side;
import dev.hydomum.core.connect.WallState;
import dev.hydomum.core.connect.WallTop;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.joml.Vector3ic;

/**
 * The connection rules of a HyDomum fence or wall (type {@value #TYPE}): its template's shapes, chosen from its four
 * neighbours by MC's rule ({@link Connections}) for its family ({@code Joins}) instead of the template's patterns, so a
 * full face joins too; a wall's top also follows the block above it ({@link WallTop}).
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
    private static final AtomicBoolean MISSING = new AtomicBoolean();

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
     * The shape and turn MC's rule gives the block at at from its neighbours, and for a wall its top from the block
     * above (WallTop, WallState); empty (no change) when a neighbour's section is not loaded, when the template has no
     * pattern for the shape, or on a failure, logged.
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
            HytaleNeighbours reader = neighbours;
            return reader.around(chunkStore, at).flatMap(around -> {
                Set<Side> joined = Connections.joinedSides(joiner, around);
                ConnectedShape shape = ConnectedShape.of(joined);
                if (joiner != Joiner.WALL) {
                    return result(shape.name(), shape);
                }
                return HytaleAbove.read(chunkStore, at)
                        .flatMap(above -> result(
                                WallState.name(shape, WallTop.of(joined, above.footprint(), above.wallPost())), shape));
            });
        } catch (RuntimeException e) {
            LOG.at(FAILED.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                    "HyDomum: could not shape the fence at %s", at);
            return Optional.empty();
        }
    }

    /**
     * The block of the pattern named key (shape's own when there is none, with a warning the first time: a wall top
     * the generator did not write), turned by shape's yaw; empty when the template has neither.
     */
    private Optional<ConnectedBlockResult> result(String key, ConnectedShape shape) {
        Map<String, BlockPattern> patterns = getShapeNameToBlockPatternMap();
        BlockPattern pattern = patterns.get(key);
        if (pattern == null && !key.equals(shape.name())) {
            LOG.at(MISSING.getAndSet(true) ? Level.FINE : Level.WARNING).log(
                    "HyDomum: no wall state %s, its shape %s instead", key, shape.name());
            pattern = patterns.get(shape.name());
        }
        BlockPattern.BlockEntry entry = pattern == null ? null : pattern.nextBlockTypeKey(ThreadLocalRandom.current());
        if (entry == null) {
            return Optional.empty();
        }
        Rotation yaw = RotationTuple.get(entry.rotation()).yaw().add(Rotation.VALUES[shape.yaw()]);
        return Optional.of(new ConnectedBlockResult(
                entry.blockTypeKey(), RotationTuple.of(yaw, Rotation.None).index()));
    }
}
