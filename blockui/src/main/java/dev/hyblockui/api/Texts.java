package dev.hyblockui.api;

import com.hypixel.hytale.server.core.Message;
import java.util.List;

/** Translated texts with parameters, written {p0}, {p1}… in the .lang files. */
public final class Texts {
    private Texts() {}

    /**
     * The translation of {@code key}, its params set as p0, p1…; a param written "%key" is itself translated, and a
     * label showing such a message takes it on .TextSpans, not .Text.
     */
    public static Message translated(String key, List<String> params) {
        Message m = Message.translation(key);
        for (int i = 0; i < params.size(); i++) {
            String p = params.get(i);
            m = p.startsWith("%") ? m.param("p" + i, Message.translation(p.substring(1))) : m.param("p" + i, p);
        }
        return m;
    }
}
