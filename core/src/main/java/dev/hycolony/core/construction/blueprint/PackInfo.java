package dev.hycolony.core.construction.blueprint;

import java.util.List;

/**
 * A style's pack metadata, as Structurize's {@code pack.json} (ST StructurePackMeta): its name, description, authors,
 * icon name (resolved by the plugin) and owner, which the pack window groups by.
 */
public record PackInfo(String name, String desc, List<String> authors, String icon, String owner) {
    /** The folder of a hut type no layout names (MC packs keep the town hall and the first huts there). */
    public static final String DEFAULT_CATEGORY = "fundamentals";

    /** The owner of a style with no metadata. */
    public static final String DEFAULT_OWNER = "hycolony";

    public PackInfo {
        authors = List.copyOf(authors);
    }

    /** A style without metadata: named after its id, no description nor author, the default icon. */
    public static PackInfo defaults(String style) {
        return new PackInfo(style, "", List.of(), "", DEFAULT_OWNER);
    }
}
