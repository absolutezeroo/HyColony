package dev.hydomum.plugin.api;

import dev.hydomum.api.VariantKey;

/** A registered variant: its key and the block id clients know it by (valid until restart). */
public record OrnamentVariant(VariantKey key, int blockId) {}
