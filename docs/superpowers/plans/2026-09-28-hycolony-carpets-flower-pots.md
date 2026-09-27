# Tapis et pot de fleurs : plan

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-carpets-flower-pots-design.md`. **Faits :** `docs/research/carpets-flower-pots.md`, `docs/research/plugin-b-api.md` § 21.

## Contraintes globales

- `CLAUDE.md` lu en entier. Le test qui échoue s'écrit avant le code du cœur. `./gradlew build` doit être vert avant chaque commit. `git add` se fait avec des chemins explicites.
- Chaque API ou asset Hytale est vérifié dans `build/vineflower/hytale-server` ou dans les assets. Rien n'est copié d'Aetherhaven.
- Le pack se trouve dans `plugin/src/subplugins/Decorations/` (`Order` 50, `EnabledByDefault` true).

### Tâche 1 : les tapis (assets seuls)
- [ ] Le modèle de tapis 32×2×32, les 20 `BlockType` avec leurs objets, les recettes 2 laines → 3 et les clés de traduction en en-US et fr-FR.
- [ ] Le build produit `subplugins/Decorations.zip`. Ajouter une étape à `docs/TESTING.md`.
- [ ] Commit : `feat: carpets in a Decorations sub-plugin`.

### Tâche 2 : la règle du pot (cœur)
- [ ] Une fonction pure : (état du pot, objet en main, mode créatif) → mettre la plante X, rendre la plante, ou ne rien faire. Elle reçoit le tableau des plantes en paramètre.
- [ ] Un test par cas de la spec.
- [ ] Commit : `feat(core): flower pot rule (Minecraft flower pot)`.

### Tâche 3 : générer les pots garnis
- [ ] Faire l'inventaire des plantes Hytale qui correspondent aux plantes que Minecraft met en pot, et lister celles qui n'ont pas d'équivalent.
- [ ] Un script dans le dépôt, sous `tools/`, génère le modèle et la texture de chaque pot garni ainsi que le tableau de données du pack. Il est lancé une fois et ses sorties sont commitées.
- [ ] Le `BlockType` du pot a un état par plante, `Interactions.Use` obligatoire pour que `UseBlockEvent` soit émis, une `DropList` et une recette de 3 briques.
- [ ] Commit : `feat: flower pot assets generated once from vanilla plants`.

### Tâche 4 : le système du pot (plugin)
- [ ] Un système `UseBlockEvent.Pre` applique la règle du cœur : il change l'état du bloc, ajuste l'inventaire, rend la plante quand on casse le pot, puis annule l'événement. Il ne tourne que si le pack est activé. Il attrape toute `RuntimeException` et la journalise en SEVERE.
- [ ] Ajouter les étapes en jeu dans `docs/TESTING.md`.
- [ ] Commit : `feat(plugin): flower pots take and give back plants`.

### Tâche 5 : relecture
- [ ] `hycolony-reviewer` sur la série, puis corrections, puis feu vert à l'utilisateur.
