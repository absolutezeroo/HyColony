package dev.hycolony.plugin.subplugin;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

/**
 * {@code subplugins/<Name>/subplugin.json}. {@code registrar} names a {@link dev.hycolony.core.FeaturePack} class with
 * a public no-argument constructor, for a pack that brings its own building or job types.
 */
record SubPluginManifest(
        @SerializedName("Name") String name,
        @SerializedName("Version") String version,
        @SerializedName("EnabledByDefault") boolean enabledByDefault,
        @SerializedName("Description") @Nullable String description,
        @SerializedName("Registrar") @Nullable String registrar) {}
