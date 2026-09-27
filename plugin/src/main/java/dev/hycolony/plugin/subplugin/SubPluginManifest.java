package dev.hycolony.plugin.subplugin;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * {@code subplugins/<Name>/subplugin.json}. {@code order} sorts the packs, and so the styles they bring (lowest first,
 * then by name; 100 when absent). {@code registrar} names a {@link dev.hycolony.core.FeaturePack} class with a public
 * no-argument constructor, for a pack that brings its own building or job types. Gson leaves a missing field null.
 */
record SubPluginManifest(
        @SerializedName("Name") @Nullable String name,
        @SerializedName("Version") @Nullable String version,
        @SerializedName("EnabledByDefault") boolean enabledByDefault,
        @SerializedName("Order") @Nullable Integer order,
        @SerializedName("Description") @Nullable String description,
        @SerializedName("Registrar") @Nullable String registrar) {
    /** Order of a pack whose manifest has none. */
    static final int DEFAULT_ORDER = 100;

    /** Stands for a pack whose manifest could not be read. */
    static SubPluginManifest unreadable(String name) {
        return new SubPluginManifest(name, "?", false, null, null, null);
    }

    int sortOrder() {
        return order == null ? DEFAULT_ORDER : order;
    }

    /** The version, or {@code ?} when the manifest has none. */
    String versionOrUnknown() {
        return version == null ? "?" : version;
    }
}
