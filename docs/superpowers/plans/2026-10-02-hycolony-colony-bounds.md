# Plan : citoyens dans la colonie, monstres dehors

Spec : `docs/superpowers/specs/2026-10-02-hycolony-colony-bounds-design.md`. Recherche : `docs/research/colony-bounds-and-mob-spawns.md`.

1. Cœur, citoyens (tests d'abord, `citizen/wander/CitizenWanderTest`) : `CitizenWander` déménage dans `citizen/wander/` ; `LeisureSites` (site de loisir), `LeisureWalk` (aller au site, flâner dans ses coins, repartir) ; bornage au territoire et retour à la maison ; `CitizenAI` ajoute la transition de loisir (20 ticks), la pause de l'IA et la fin du loisir hors du repos.
2. Cœur, monstres : `ColonyProtection` (ou voisin) dit si une apparition hostile est permise à une position (non dans une cellule revendiquée).
3. Plugin, monstres : groupe de PNJ propre à HyColony (`Aggressive` + `Outlander` + `Scarak` + rôles nommés) ; `RefSystem` sur `NPCEntity` qui, pour un PNJ d'apparition naturelle de ce groupe dans le territoire, le fait disparaître au tick suivant (`setDespawning`, `setDespawnRemainingSeconds(0)`).
4. Docs : points de `docs/TESTING.md`.
5. `./gradlew build`, relectures (code et fidélité), commits par partie.
