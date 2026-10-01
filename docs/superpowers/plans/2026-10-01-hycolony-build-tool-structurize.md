# Outil de construction comme Structurize : plan

> Exécution : en ligne (superpowers:executing-plans), TDD, relectures à la fin de chaque lot puis relecture finale.

**But :** la baguette prend la fenêtre, la navigation par dossiers et la fenêtre des packs de Structurize.

**Spec :** `docs/superpowers/specs/2026-10-01-hycolony-build-tool-structurize-design.md`

## Contraintes globales

- CLAUDE.md entier : 400 lignes par fichier, 40 par méthode, 15 fichiers par paquet, PMD, NullAway, spotless fichier par fichier, en-US et fr-FR, `Deviation from MC:`.
- Modifications par Edit et Write seulement ; Python seulement pour produire les PNG.
- Positions XML ×2 ; textures en `@2x` à la taille affichée ×4, au plus proche voisin.

## Points à surveiller en relecture

- Un pack changé alors qu'une hutte est choisie : le fantôme disparaît, le dossier repart à la racine.
- Une hutte verrouillée choisie : le fantôme montre le plan, Valider reste caché ; une action Valider forgée est refusée par `WandPlacement`.
- Un dossier vide (style sans hutte à cet endroit) : il n'apparaît pas dans les icônes ni dans les sous-dossiers.
- `packs.json` absent ou mal formé : les styles s'affichent avec leurs valeurs par défaut.
- La fenêtre des packs sans aucun style : texte « aucun pack » de Structurize.

## Lot 1 : données des packs (cœur et plugin)

- Cœur : `construction/blueprint/PackInfo` (record), `BlueprintSource.pack(style)` et `category(typeId)` (méthodes par défaut : défauts du § 2) ; `FakeBlueprints` les simule.
- Plugin : `prefab/PrefabPacks` lit `hycolony/packs.json` fusionné (comme `PrefabStyles`), tolérant ; `HytaleBlueprintSource` les expose ; `packs.json` du mod (layout + rien) et des sous-plugins Kweebec et Outlander (métadonnées).
- Tests : défauts (style inconnu, hutte sans dossier). Le plugin n'a pas de tests unitaires (§ 8).

## Lot 2 : navigation dans le cœur

- `WandSession.depth` ; `WandTree` (pur) : dossiers de premier niveau, enfants d'un dossier, huttes d'un dossier, parent ; seulement les dossiers qui mènent à une hutte du style.
- `WandViews` construit `WandView` (§ 5) avec les verrous (`WandLocks` si besoin) ; `WandActions.openCategory`, `back`, `selectBuilding` au niveau 1.
- Tests (`WandTreeTest`, `WandNavigationTest`, mise à jour de `WandActionsTest`) : racine, sous-dossier, plans, retour, verrous (créatif, bloc porté, hors colonie, hôtel de ville), Valider caché si verrouillé, niveau 1.

## Lot 3 : fenêtre des packs dans le cœur

- `WandPacksView`, `UiPort.showWandPacks` ; `WandActions.switchPack`, `filterPacks`, `selectStyle` ouvre la fenêtre principale ; `open` sans pack → fenêtre des packs ; Annuler des packs.
- Tests : groupes par propriétaire en ordre alphabétique, filtre sans casse, ouverture sans pack, Annuler avec et sans pack, changement de pack qui oublie dossier et hutte.

## Lot 4 : fenêtres du plugin

- Textures (`Structurize/`), `BuildTool.ui`, `SwitchPack.ui`, gabarits ; `WandPage` réécrite, `WandPacksPage` ; clés de langue en-US et fr-FR (anciennes clés retirées si inutiles) ; `docs/TESTING.md`.
- Validation des `.ui` avec le vérificateur de l'éditeur ; build vert ; relectures (`hycolony-reviewer`, `mc-fidelity-checker`, `ui-lang-checker`).
