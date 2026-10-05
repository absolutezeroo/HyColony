package dev.hyangler.plugin.data;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/** Builds the JSON a data file hands the core: only the keys the file had (spec § 6, tolerant reading). */
final class BsonFields {
    private BsonFields() {}

    /** Puts value under key when the file gave one; an absent key stays absent, so the core takes its default. */
    static void put(BsonDocument doc, String key, @Nullable Integer value) {
        if (value != null) {
            doc.put(key, new BsonInt32(value));
        }
    }

    /** Puts value under key when the file gave one; an absent key stays absent, so the core takes its default. */
    static void put(BsonDocument doc, String key, @Nullable BsonValue value) {
        if (value != null) {
            doc.put(key, value);
        }
    }
}
