package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.app.ui.WandView;
import java.util.Locale;

/**
 * The build tool's texts: a folder's name (ST handleSubCat and the category tooltips capitalise the folder's own name;
 * a known MC folder is translated) and the tree (ST {@code pack/folder/blueprint}).
 */
final class WandTexts {
    private static final String FOLDER_KEY = "hycolony.ui.wand.folder.";

    private WandTexts() {}

    /** The last segment of {@code folder}, translated when HyColony knows it, else capitalised as ST does. */
    static String folder(PlayerRef player, String folder) {
        String name = folder.substring(folder.lastIndexOf('/') + 1);
        String translated = I18nModule.get().getMessage(player.getLanguage(), FOLDER_KEY + name);
        return translated != null
                ? translated
                : name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** ST's tree label: the pack's name, then the core's path ({@code /folder/builder2}). */
    static Message tree(WandView view) {
        return Message.join(
                Message.translation(view.packName()), Message.raw(view.panel().treePath()));
    }
}
