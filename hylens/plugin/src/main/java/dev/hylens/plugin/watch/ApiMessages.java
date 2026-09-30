package dev.hylens.plugin.watch;

import com.hypixel.hytale.server.core.Message;
import dev.hycolony.api.ApiText;
import java.util.List;

/** HyColony's api texts as Hytale messages: a translation key with p0, p1… params, a nested text translated too. */
public final class ApiMessages {
    private ApiMessages() {}

    /** {@code text} as a message; show it on a label's .TextSpans, since it may nest translations. */
    public static Message of(ApiText text) {
        Message m = Message.translation(text.key());
        List<Object> params = text.params();
        for (int i = 0; i < params.size(); i++) {
            m = switch (params.get(i)) {
                case ApiText nested -> m.param("p" + i, of(nested));
                case Object plain -> m.param("p" + i, plain.toString());
            };
        }
        return m;
    }
}
