package dev.hydomum.plugin.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The ornament variants other mods need from boot, such as the Domum blocks of a style's prefabs: without them, a
 * variant block loads as Hytale's "Unknown". A mod calls {@link #require} during its setup; HyDomum creates them
 * with its saved variants once assets are loaded. An id that names no known shape, or a material its slot refuses,
 * is logged and skipped.
 */
public final class RequiredVariants {
    private static final List<String> REQUIRED = new ArrayList<>();

    private RequiredVariants() {}

    /**
     * Adds variant ids ({@code shape|material|material}, as HyDomum saves them); call during setup only. Throws
     * {@link NullPointerException} at the caller for a null id, which would otherwise break every variant at boot.
     */
    public static synchronized void require(Collection<String> ids) {
        REQUIRED.addAll(List.copyOf(ids));
    }

    /** Every id required so far, in order; HyDomum reads it at boot. */
    public static synchronized List<String> required() {
        return List.copyOf(REQUIRED);
    }
}
