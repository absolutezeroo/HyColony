package dev.hycolony.core.request.resolver;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Resolver;
import java.util.List;

/** The resolvers every colony's request system starts with (MC StandardRequestManager.setup). */
public final class BuiltInResolvers {
    private BuiltInResolvers() {}

    /** New player and retrying resolvers at the colony's {@code center}. */
    public static List<Resolver> of(BlockPos center) {
        return List.of(new PlayerResolver(center), new RetryingResolver(center));
    }
}
