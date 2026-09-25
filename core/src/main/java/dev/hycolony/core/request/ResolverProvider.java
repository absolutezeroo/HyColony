package dev.hycolony.core.request;

import java.util.List;

public interface ResolverProvider {
    String providerId();

    List<Resolver> resolvers();
}
