package dev.hyangler.core.catalog;

/** One data file as the plugin read it: its type, its id (the item it names) and its JSON text. */
public record RawFile(Kind kind, String id, String json) {
    /** Which {@code Server/HyAngler/} type the file belongs to. */
    public enum Kind {
        FISH,
        CATCH,
        ROD
    }
}
