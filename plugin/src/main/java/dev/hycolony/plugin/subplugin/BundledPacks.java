package dev.hycolony.plugin.subplugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Reads the sub-plugin files that the build bundles in the jar under {@code subplugins/}. */
final class BundledPacks {
    private static final String ROOT = "/subplugins/";

    private BundledPacks() {}

    /** The bundled pack names ({@code subplugins/index.txt}); none when the build bundled no pack. */
    static List<String> names() throws IOException {
        try (InputStream in = BundledPacks.class.getResourceAsStream(ROOT + "index.txt")) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    /** The pack's manifest; throws when it is missing, empty, or lacks its Name (the folder's) or Version. */
    static SubPluginManifest manifest(String name) throws IOException {
        @Nullable
        SubPluginManifest manifest = read(
                ROOT + name + "/subplugin.json",
                in -> new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SubPluginManifest.class));
        if (manifest == null || !name.equals(manifest.name()) || manifest.version() == null) {
            throw new IOException("subplugin.json is missing, or has no Version or a Name other than " + name);
        }
        return manifest;
    }

    /** The pack's fragment {@code hycolony/<file>}, or empty when the pack has none. Throws if it is not an object. */
    static Optional<JsonObject> fragment(String name, String file) {
        return json(ROOT + name + "/hycolony/" + file);
    }

    /** A classpath JSON object, or empty when absent. Throws if it is not a JSON object. */
    static Optional<JsonObject> json(String path) {
        try {
            return Optional.ofNullable(read(
                    path,
                    in -> JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    /** The pack's asset zip ({@code Common/} and {@code Server/}), or null for a pack without assets. */
    static @Nullable InputStream zip(String name) {
        return BundledPacks.class.getResourceAsStream(ROOT + name + ".zip");
    }

    private interface Parser<T> {
        T parse(InputStream in) throws IOException;
    }

    private static <T> @Nullable T read(String path, Parser<T> parser) throws IOException {
        try (InputStream in = BundledPacks.class.getResourceAsStream(path)) {
            return in == null ? null : parser.parse(in);
        }
    }
}
