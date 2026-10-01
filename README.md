# HyColony

Colony management for Hytale, recreating MineColonies' systems. Found a colony with a Town Hall, then grow it with:
- citizens who live and work;
- buildings raised by a builder;
- a logistics request system;
- jobs, needs, research and defense.

**Status:**
- sub-project 0 (foundations & colony) is done: Town Hall, citizens, permissions, persistence;
- sub-projects 1+2 (requests & construction) are implemented: builder and residence huts, hiring, work orders, a builder that clears, builds, decorates and deconstructs from MineColonies' Medieval Oak plans, material requests and the building, resources, requests and work order windows. In-game testing: `docs/TESTING.md` items 13+.

- Server: Hytale 0.7.0-pre.5, Update 7 (see `docs/UPGRADING.md`)
- License: GPL-3.0. Game mechanics are ported from [MineColonies](https://github.com/ldtteam/minecolonies) (GPL-3.0); no MineColonies assets are used.

## Build

After cloning, enable the versioned git hooks once (format, sizes, commit message, full build before push):

    git config core.hooksPath .githooks

    ./gradlew build              # core tests + plugin jar (plugin/build/libs/HyColony-*.jar)
    ./gradlew setupHytaleDev     # once: download assets (Hytale login)
    ./gradlew runAllMods         # local dev server (run/)

## Install

HyColony is five mods, one jar each after `./gradlew build`. Put them together in the server's `mods/` folder:

| Jar | What it is | Needs |
|---|---|---|
| `blockui/build/libs/HyBlockUI-*.jar` | The windows library | Nothing |
| `domum/plugin/build/libs/HyDomum-*.jar` | Domum Ornamentum's blocks | HyBlockUI |
| `vanilla/plugin/build/libs/HyVanilla-*.jar` | Minecraft's vanilla blocks missing from Hytale | Nothing |
| `plugin/build/libs/HyColony-*.jar` | The colonies, and their API for addons (`api/README.md`) | HyBlockUI, HyDomum, HyVanilla |
| `hylens/plugin/build/libs/HyLens-*.jar` | Optional: a debugging lens for operators (`/hylens`) | HyColony, HyBlockUI |

**If a jar that another one needs is missing, Hytale does not start at all.** Every mod here has an asset pack,
and when a mod with an asset pack lacks a dependency, Hytale cannot order the packs and shuts the whole server down. The log names the missing
dependency: `Failed to load 'HyColony:hylens' because the dependency 'HyColony:hycolony' could not be found!`. Add the
missing jar, or remove the one that needs it. HyColony runs without HyLens.

## Commands

- `/hycolony info` shows the colony at your position.
- `/hycolony rank <player> <officer|friend|neutral|hostile>` requires the colony's EDIT_PERMISSIONS.
- `/hycolony delete <id>` and `/hycolony selftest` are for operators.
- `/hylens menu`, `watch`, `unwatch`, `check`, `autocheck`, `send`, `perf` and `selftest` (HyLens, operators only) debug
  the colonies.

## Layout

- `core/`: pure Java game logic (no Hytale imports), fully unit-tested.
- `plugin/`: the Hytale adapter and the asset pack.
- `docs/`: research, specs, plans, and the upgrade and test checklists.
