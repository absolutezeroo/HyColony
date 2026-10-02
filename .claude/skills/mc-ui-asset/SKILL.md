---
name: mc-ui-asset
description: Use when a HyColony or HyLens window needs an icon or small texture drawn in MineColonies' style instead of copied from MC (wax seals, side tab icons, buttons, an icon MC has no equivalent for), or when the user dislikes one of our drawn icons. Not for MC textures still copied as they are (CLAUDE.md § 7).
argument-hint: <icon name and where it shows, e.g. "seal for the Alliances tab">
---

Draw `$ARGUMENTS` as **our own** icon in MineColonies' style. Each icon is a Python drawing in `tools/ui/`; its PNG
is a generated file, committed (the Gradle build never runs the scripts): never edit a PNG by hand. Answer the user
in French.

## Tools

| File | Holds |
|---|---|
| `tools/ui/mc_icon.py` | pixelstudio import, the wax seal style (`blank_seal`, `soft`, `rim`, `press`, `seal`), `export` (x4 nearest into the @2x file) |
| `tools/ui/mc_stamp.py` | the tab icon style: `stamp(shape, grooves)` |
| `tools/ui/seals.py`, `tabs.py` | one function per icon, listed in the file's table (`SEALS`, `TABS`); `python tools/ui/<file>.py` writes every PNG of the table |
| `tools/ui/chest.py` | one icon, `summary_button` (the hut's inventory summary button) |

Outputs go to the pack of the mod that shows the icon: `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc/`
or `hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Mc/` (a mod never points at another mod's assets;
an icon both mods show has one table entry per pack).

They need the user-level skill **pixel-art-studio** (`~/.claude/skills/pixel-art-studio`, commit f8c2466; the script
exits with install steps if missing). Its loop (brief, look, critique, fix) applies; its default rules on hue-shifted
ramps and 4-6 colours do **not** fit MC's styles.

## Order of work

1. **Family.** Find the MC texture (`sources/minecolonies/src/main/resources/assets/minecolonies/textures/gui/`) and
   its size, and who shows it (`grep` the `.ui` files and `SideTabs`). Our file keeps MC's name and pixel size: the
   `.ui` anchors are fixed. An icon MC has none for takes the size of its family and of its place in the `.ui`. An
   icon is drawn with the window that shows it: no icon ships unused. An existing family reuses its style module
   (its palette keeps MC's main tones; MC's stray in-between tones are dropped on purpose). A new family is studied
   first: print the MC icons as pixel grids and palettes (`study.py` of pixel-art-studio, or PIL) and write the style
   module from what you measure.
2. **Meaning.** Same meaning as MC's icon, our own drawing (never MC's pixels). An object that exists in Hytale is
   drawn as Hytale's (its icon in `Common/Icons/ItemsGenerated/` of the pinned assets zip gives shape and palette).
3. **Draw, then look.** Write a small scratchpad script that imports the drawing function and renders a sheet, MC
   left / ours right, x8 and x2 (the in-game size), then read the PNG. Critique against the rules below, fix,
   re-render: at least two passes before showing the user.
4. **User.** Show the sheet path and say what changed. When the user dislikes an icon, offer 2-3 labelled variants
   on one sheet, with MC's next to them; never guess again silently.
5. **Ship.** Export, check the size equals the replaced file (or the `.ui` place), update the credits (`NOTICE`, the
   header of each `.ui` showing it, the dated note in `docs/research/ui-vs-minecolonies.md`), `./gradlew build`,
   `hycolony-reviewer` (and `ui-lang-checker` if a `.ui` changed), review fixes reviewed again (CLAUDE.md § 9.3),
   green light, the user tests in game, then commit with explicit paths.

## Style rules (each one broke an icon once)

| Rule | Why |
|---|---|
| Palette measured on MC's icon of the family, single hue per material | MC's ramps do not shift hue; pixel-art defaults look wrong |
| Wax: blur the material inside the silhouette only (`soft`), then light crisp on top | a blur over everything melts the rim and the symbol |
| Wax rim coloured by the edge's angle to the top-left light (`rim`) | two flat crescents leave stray bright pixels |
| Pressed symbols are broad filled shapes, at least 3 px inside the edge, with lips (`press`); a hole erased in the shape stays raised | thin rings and lipless grooves read as ink on the surface |
| A 45-degree stroke is 2 px wide (rows of 2) | 3 px gives a shadow/floor checker |
| Tab icons keep a 2-6 px margin in 20 x 20, as MC's (1 to 6); a part that must show its dark fill is at least 4 px wide | full-size icons look heavy; a 3 px part is all outline |
| Small round shapes: centre even-sized ones between pixels (`ellipse`) and check the silhouette | a pixel circle at r <= 7 reads as an octagon |
| pixelstudio: `ellipse`/`polygon` cannot erase (use `rect`, `px`, `circle` with `None`); `get` returns `None` when transparent | silent no-ops |
