# HyColony

Colony management for Hytale, recreating MineColonies' systems. Found a colony with a Town Hall, then grow it with:
- citizens who live and work;
- buildings raised by a builder;
- a logistics request system;
- jobs, needs, research and defense.

**Status:**
- sub-project 0 (foundations & colony) is done: Town Hall, citizens, permissions, persistence;
- sub-projects 1+2 (requests & construction) are implemented: builder and residence huts, hiring, work orders, a builder that clears, builds, decorates and deconstructs from vanilla Outlander/Kweebec prefabs, material requests and the building, resources, requests and work order windows. In-game testing: `docs/TESTING.md` items 13+.

- Server: Hytale 0.6.8 (see `docs/UPGRADING.md`)
- License: GPL-3.0. Game mechanics are ported from [MineColonies](https://github.com/ldtteam/minecolonies) (GPL-3.0); no MineColonies assets are used.

## Build

After cloning, enable the versioned git hooks once (format, sizes, commit message, full build before push):

    git config core.hooksPath .githooks

    ./gradlew build              # core tests + plugin jar (plugin/build/libs/HyColony-*.jar)
    ./gradlew setupHytaleDev     # once: download assets (Hytale login)
    ./gradlew :plugin:runServer  # local dev server

## Commands

- `/hycolony info` shows the colony at your position.
- `/hycolony rank <player> <officer|friend|neutral|hostile>` requires the colony's EDIT_PERMISSIONS.
- `/hycolony delete <id>` and `/hycolony selftest` are for operators.

## Layout

- `core/`: pure Java game logic (no Hytale imports), fully unit-tested.
- `plugin/`: the Hytale adapter and the asset pack.
- `docs/`: research, specs, plans, and the upgrade and test checklists.
