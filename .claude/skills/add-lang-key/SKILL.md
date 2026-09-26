---
name: add-lang-key
description: Add or change a player-visible HyColony translation key in both en-US and fr-FR hycolony.lang files. Use whenever code shows text to a player (message, window label, button, item name).
argument-hint: <key> = <English text>
---

Every player-visible text is a key present in **both** files (CLAUDE.md § 7):

- `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang`
- `plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang`

Format: one `key = text` per line, keys grouped by prefix (`hut.`, `colony.`, `permission.`, `ui.<window>.`…). Put the new key next to its group, at the same position in both files.

Rules:
- Parameters are `{p0}`, `{p1}`… and must be identical in both languages.
- A translation nested in another (`param(key, Message)`) is displayed on `.TextSpans`, never `.Text`.
- A button gets one complete key per variant (no concatenation).
- French: natural French with proper typography (espace avant `:` `!` `?`), not a literal translation.

Then check both files still have the same key set:

```bash
cd "$CLAUDE_PROJECT_DIR/plugin/src/main/resources/Server/Languages" && diff <(grep -o '^[^=#]*' en-US/hycolony.lang | sed 's/ *$//' | sort) <(grep -o '^[^=#]*' fr-FR/hycolony.lang | sed 's/ *$//' | sort) && echo "keys match"
```
