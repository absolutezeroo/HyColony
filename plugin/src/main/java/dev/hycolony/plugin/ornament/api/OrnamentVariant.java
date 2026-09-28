package dev.hycolony.plugin.ornament.api;

/** A registered variant: its key and the block id clients know it by (valid until restart). */
public record OrnamentVariant(VariantKey key, int blockId) {}
