package dev.hyangler.core.catalog;

/** A data file left out, and why (shown by the selftest, spec § 6). */
public record Rejection(RawFile.Kind kind, String id, String reason) {}
