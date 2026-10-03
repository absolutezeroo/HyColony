package dev.hylens.core.hud;

import dev.hycolony.api.ApiText;
import java.util.Optional;

/** One line of the watch panel: how it shows, its text and, for a field only, the value beside it. */
public record HudLine(Kind kind, ApiText label, Optional<ApiText> value) {
    /** How a line shows: the citizen's name, a section header, a labelled field or an alert. */
    public enum Kind {
        TITLE,
        SECTION,
        FIELD,
        ALERT
    }

    /** Refuses a value on a line that is not a field, and a field without one: the panel has nowhere to show it. */
    public HudLine {
        if (value.isPresent() != (kind == Kind.FIELD)) {
            throw new IllegalArgumentException("only a field has a value, and it always has one: " + kind);
        }
    }

    /** The citizen's name and job, in bold. */
    static HudLine title(ApiText text) {
        return new HudLine(Kind.TITLE, text, Optional.empty());
    }

    /** A section header, in red. */
    static HudLine section(ApiText text) {
        return new HudLine(Kind.SECTION, text, Optional.empty());
    }

    /** {@code label} in the label column and {@code value} beside it. */
    static HudLine field(ApiText label, ApiText value) {
        return new HudLine(Kind.FIELD, label, Optional.of(value));
    }

    /** An alert across the whole line, in red. */
    static HudLine alert(ApiText text) {
        return new HudLine(Kind.ALERT, text, Optional.empty());
    }
}
