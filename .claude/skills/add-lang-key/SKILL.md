---
name: add-lang-key
description: Add or change a player-visible translation key in both en-US and fr-FR .lang files of the mod that shows the text (hycolony.lang, hydomum.lang, hyvanilla.lang, hyblockui.lang, hylens.lang, hyangler.lang). Use whenever code shows text to a player (message, window label, button, item name).
argument-hint: <key> = <English text>
---

Every player-visible text is a key present in **both** files of the mod that shows it (CLAUDE.md § 7):

| Mod | Files (`en-US` and `fr-FR`) | Key prefix in code and `.ui` |
|---|---|---|
| HyColony | `plugin/src/main/resources/Server/Languages/<lang>/hycolony.lang` | `hycolony.` |
| HyDomum | `domum/plugin/src/main/resources/Server/Languages/<lang>/hydomum.lang` | `hydomum.` |
| HyVanilla | `vanilla/plugin/src/main/resources/Server/Languages/<lang>/hyvanilla.lang` | `hyvanilla.` |
| HyBlockUI | `blockui/src/main/resources/Server/Languages/<lang>/hyblockui.lang` | `hyblockui.` |
| HyLens | `hylens/plugin/src/main/resources/Server/Languages/<lang>/hylens.lang` | `hylens.` |
| HyAngler | `angler/plugin/src/main/resources/Server/Languages/<lang>/hyangler.lang` | `hyangler.` |

The prefix is the `.lang` file's name (Hytale's I18nModule). HyDomum's block names (`hydomum_blocks.lang`) are written by `tools/domum/generate.py`: never edit them by hand.

Format: one `key = text` per line, keys grouped by prefix (`hut.`, `colony.`, `permission.`, `ui.<window>.`…). Put the new key next to its group, at the same position in both files.

Rules:
- Parameters are `{p0}`, `{p1}`… and must be identical in both languages.
- A translation nested in another (`param(key, Message)`) is displayed on `.TextSpans`, never `.Text`.
- A button gets one complete key per variant (no concatenation).
- French: natural French with proper typography (espace avant `:` `!` `?`), not a literal translation.

Then check both files still have the same key set (`DIR` = `plugin/src/main/resources/Server/Languages`, `domum/plugin/src/main/resources/Server/Languages`, `vanilla/plugin/src/main/resources/Server/Languages`, `blockui/src/main/resources/Server/Languages`, `hylens/plugin/src/main/resources/Server/Languages` or `angler/plugin/src/main/resources/Server/Languages`; `NAME` = `hycolony`, `hydomum`, `hyvanilla`, `hyblockui`, `hylens` or `hyangler`):

```bash
cd "$CLAUDE_PROJECT_DIR/$DIR" && diff <(grep -o '^[^=#]*' en-US/$NAME.lang | sed 's/ *$//' | sort) <(grep -o '^[^=#]*' fr-FR/$NAME.lang | sed 's/ *$//' | sort) && echo "keys match"
```
