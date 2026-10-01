# Plan : clôtures et murets reliés comme dans Minecraft

Spec : `docs/superpowers/specs/2026-10-01-hydomum-fence-connections-design.md`.

1. **Cœur, TDD** (`domum/core`, `dev.hydomum.core.connect`) : `Side`, `Joiner`, `NeighbourKind`, `Neighbour`, `Connections` (`ConnectionsTest` : familles, face pleine, côtés du portillon, barreaux sans portillon), `ConnectedShape` (`ConnectedShapeTest` : les 16 ensembles de côtés).
2. **Plugin** (`domum/plugin`, `dev.hydomum.plugin.connect`) : `HytaleFenceRules` (codec hérité, clé `Joins`), `HytaleNeighbours` (familles vanilla de l'id-map), inscription dans `HyDomumPlugin.setup()`, `DomumIds.connections`.
3. **Générateur** (`tools/domum`) : `compat.py` (type, `Joins`), `blocks/vanilla_fences.py` (familles vanilla dans l'id-map), vérifications de `check_connected.py` ; régénération du pack.
4. **Planches à l'établi** (demandé le 2026-10-01) : `tags.py` ajoute `planks` aux tags des clôtures, portillons et escaliers ; `check_materials.py`.
5. **Doc** : écart de `compat.py` et de `tags.py`, spec DO1, `docs/TESTING.md` (140, 140 bis, 151), `docs/research/connected-blocks.md` § 5, `docs/research/domum-ornamentum.md`.
6. Build, relecture indépendante, commit, feu vert à l'utilisateur.
