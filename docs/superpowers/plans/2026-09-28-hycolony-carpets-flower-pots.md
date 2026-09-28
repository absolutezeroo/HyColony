# Tapis et pot de fleurs : plan

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-carpets-flower-pots-design.md`. **Faits :** `docs/research/carpets-flower-pots.md`, `docs/research/plugin-b-api.md` § 21.

## Contraintes globales

- `CLAUDE.md` lu en entier. Le test qui échoue s'écrit avant le code du cœur. `./gradlew build` doit être vert avant chaque commit. `git add` se fait avec des chemins explicites.
- Chaque API ou asset Hytale est vérifié dans `build/vineflower/hytale-server` ou dans les assets. Rien n'est copié d'Aetherhaven.
- Le pack se trouve dans `plugin/src/subplugins/Decorations/` (`Order` 50, `EnabledByDefault` true).

### Tâche 1 : les tapis (assets seuls)
- [x] Le modèle de tapis 32×2×32, les 20 `BlockType` avec leurs objets, les recettes 2 laines → 3 et les clés de traduction en en-US et fr-FR.
- [x] Le build produit `subplugins/Decorations.zip`. Ajouter une étape à `docs/TESTING.md`.
- [x] Commit : `feat: carpets in a Decorations sub-plugin`.

### Tâche 2 : la règle du pot (cœur)
- [x] Une fonction pure : (état du pot, objet en main, mode créatif) → mettre la plante X, rendre la plante, ou ne rien faire. Elle reçoit le tableau des plantes en paramètre.
- [x] Un test par cas de la spec.
- [x] Commit : `feat(core): flower pot rule (Minecraft flower pot)`.

### Tâche 3 : générer les pots garnis
- [x] Faire l'inventaire des plantes Hytale qui correspondent aux plantes que Minecraft met en pot, et lister celles qui n'ont pas d'équivalent.
- [x] Un script dans le dépôt, sous `tools/`, génère le modèle et la texture de chaque pot garni ainsi que le tableau de données du pack. Il est lancé une fois et ses sorties sont commitées.
- [x] Un `BlockType` de pot par couleur d'argile lisse (16 couleurs), avec un état par plante, `Interactions.Use` obligatoire pour que `UseBlockEvent` soit émis, une `DropList` par état et une recette de 3 `Soil_Clay_Smooth_<C>`. Les couleurs partagent les modèles et un seul atlas.
- [x] Commit : `feat: flower pot assets generated once from vanilla plants`.

### Tâche 4 : le système du pot (plugin)
- [x] Un système `UseBlockEvent.Pre` applique la règle du cœur : il change l'état du bloc et ajuste l'inventaire, **sans annuler** l'événement (un `UseBlock` annulé ferait poser la plante à côté du pot). Le pot garni cassé rend la plante par la `DropList` de son état, sans code. Il n'est enregistré que si l'id-map fusionnée contient des pots. Il attrape toute `RuntimeException` et la journalise en SEVERE.
- [x] Ajouter les étapes en jeu dans `docs/TESTING.md`.
- [x] Commit : `feat(plugin): flower pots take and give back plants`.

### Tâche 5 : relecture
- [ ] `hycolony-reviewer` sur la série, puis corrections, puis feu vert à l'utilisateur.
