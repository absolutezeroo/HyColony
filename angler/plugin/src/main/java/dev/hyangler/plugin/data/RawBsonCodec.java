package dev.hyangler.plugin.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import org.bson.BsonValue;

/**
 * Any JSON value, kept as it was written, for the rules the core judges (Conditions, Modifiers). Hytale's
 * BsonDocumentCodec throws on a value that is no object, and a failed asset stops the server from a zip or jar pack
 * (fishing-hytale.md § 5.13): this codec never throws on a well-formed value, so the core rejects the file alone.
 * Codec's default decodeJson reads the raw value, then hands it to decode.
 */
final class RawBsonCodec implements Codec<BsonValue> {
    static final RawBsonCodec INSTANCE = new RawBsonCodec();

    private RawBsonCodec() {}

    @Override
    public BsonValue decode(BsonValue value, ExtraInfo extraInfo) {
        return value;
    }

    @Override
    public BsonValue encode(BsonValue value, ExtraInfo extraInfo) {
        return value;
    }

    @Override
    public Schema toSchema(SchemaContext context) {
        return new Schema(); // any value: the core checks it
    }
}
