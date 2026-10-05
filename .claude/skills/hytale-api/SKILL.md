---
name: hytale-api
description: Verify a Hytale server API of the pinned version (gradle.properties, today 0.7.0-pre.4) (class, method, event, component, asset) before using it in a mod's plugin (HyColony, HyDomum, HyVanilla, HyBlockUI, HyLens, HyAngler). Use whenever plugin code calls com.hypixel.* or an adapter needs a Hytale capability.
user-invocable: false
---

CLAUDE.md § 1: Hytale API usage is verified in the decompiled sources, never assumed.

1. **Cheat sheet first**: `docs/research/plugin-b-api.md` (and `docs/research/hytale-api-spike.md`) may already have the verified answer; `docs/research/pieges-portage.md` § 1 lists the traps that already caused bugs.
2. **Decompiled server**: `build/vineflower/hytale-server/com/hypixel/hytale/`. Grep the class/method, read the actual signature, what it returns on missing input, and which thread it expects (world thread). Check the **exact type** you hold: several classes share a name (two `Rotation` enums, with different methods). Also read what the call triggers (systems reacting to an entity removal, a block entity recreated on placement) and whether it can throw on the world thread. Look at vanilla callers for the usage pattern.
3. **Assets**: `%USERPROFILE%\.gradle\caches\hytale-assets\<patchline>-<hytale_version>-Assets.zip` (values from `gradle.properties`, today `pre-release-0.7.0-pre.4-Assets.zip`) for asset ids, `.ui` patterns and JSON schemas. Asset ids used by HyColony live only in `hycolony/id-map.json`, except in its open asset types (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), whose files name the items they describe.
4. **Docs MCP**: `hytale-docs` (`search_docs`, `get_doc`) for context, but the decompiled source wins when they disagree (the version is pinned in `gradle.properties`).
5. **Record it**: add what you verified to `docs/research/plugin-b-api.md` (in French for prose, code in English), with the file path it came from (several mods together, class loading, packs across mods: § 28). Flag anything only source-verified whose in-game result is unknown as **[in-game]**.

If the sources are missing, say so and ask the user; don't invent a signature.
