package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;
import java.util.function.UnaryOperator;

/** Upgrades a document from schema {@code from} to {@code from + 1}. */
public record Migration(int from, UnaryOperator<JsonObject> apply) {}
