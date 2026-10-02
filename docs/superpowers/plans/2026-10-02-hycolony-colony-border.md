# Plan : bordure de la colonie avec la baguette

Spec : `docs/superpowers/specs/2026-10-02-hycolony-colony-border-design.md`.

1. Cœur, tests d'abord (`ColonyBorderTest`) : `TerritoryIndex.colonyAt(ClaimCell)`, puis `app/wand/ColonyBorder` (`nearest`, `lines`), port de `ColonyBorderRenderer.render` et `draw`.
2. Configuration : `ColonyConfig.Client.colonyTeamBorders` (vrai par défaut), clé `Client.ColonyTeamBorders` dans `ClientSection` ; `docs/research/config-inventory.md`.
3. Plugin : `ui/wand/BorderShapes` (cylindres `DisplayDebug` pour un seul joueur, `ClearDebugShapes`) et `ui/wand/ColonyBorderSystem` (un regard tous les quarts de seconde, redessin au changement, renouvellement toutes les 10 secondes), enregistré par `WandInteraction.register`.
4. Docs : `plugin-b-api.md` § 48, points 314 à 317 de `docs/TESTING.md`.
5. `./gradlew build`, relectures (code et fidélité), commit `feat(core)` puis `feat(plugin)`.
