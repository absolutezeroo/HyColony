# Plan : le catalogue et le bouclier (blockpaint, lots 4 et 5)

Spec : `docs/superpowers/specs/2026-10-03-blockpaint-surfaces-design.md` (§ 3.1 à 3.5 ; § 6, lots 4 et 5). Même
garde-fou que les lots 1 à 3 : rien ne change pour un modèle qui ne déclare rien (`python tools/blockpaint` régénère
tout à l'octet) ; toutes les vérifications de `tools/blockpaint/`.

## Tâche 1 : les matériaux (faite)

- `woods.py` : `timber(species, finish, plank)`, chêne, pin, noyer, acajou (palettes des blocs de bois de Hytale :
  Hardwood, Lightwood, Darkwood, Redwood) ; finitions rabotée, poncée, sciée brute, sculptée, fendue.
- `metalwork.py` : `metal(name, finish)`, fer, acier, fonte, argent, laiton ; finitions forgée, martelée, brossée,
  polie, brute de fonderie, usinée.
- `textiles.py` (lin, coton, laine, soie, toile), `stones.py` (marbre, granit, céramique).
- Chaque pinceau porte sa famille (`brushes.family`) ; `crystal` est du verre, `paper` du papier (famille ajoutée à
  `compat.FAMILIES`), `ore` et `embers` de la pierre.
- Tests : `check_materials.py`, `check_brushes.py` (toute famille connue).

## Tâche 2 : revêtements, dégradations, dépôts (faite)

- `coatings.py` : les 21 revêtements (`COATS`).
- `degradations.py`, `deposits.py` : les effets du § 3.5.2 ; `surface.CATALOGUE` les réunit avec le premier lot.
- Signaux : la rouille écrit `rust` (piqûres et coulures la lisent), les fissures `water_retention` (mousse et
  moisissure), la graisse `sticky` (la poussière s'y colle, `weathering.dust_mask`).
- `compat.MATRIX` : chaque effet qui ne convient pas à toutes les familles (rouille sur le fer, vert-de-gris sur le
  cuivre, farinage sur la peinture, effilochage sur le tissu…).
- Tests : `check_weathering.py`, `check_coats.py`, `check_conditions.py` (`CompatTest` : effets et familles connus,
  les lignes du tableau de la spec § 3.5.3).

## Tâche 3 : âge, condition, environnement (faite)

- Les âges donnent les effets du temps, les conditions ceux de l'usage (éraflures, rayures, effilochage).
- Les neuf environnements de la spec (`conditions.ENVIRONMENTS`) ; les effets d'un lieu (`PLACED`) n'existent que là
  où l'environnement les nomme ; ce que le lieu pose (`Environment.degrees`) est retenu par le soin comme l'âge.
- `semantics.py` demande si la mousse et la moisissure poussent là où il fait plus humide (`deposits.damp`).
- Tests : `check_conditions.py` (`PlanTest`), `check_semantics.py`.

## Tâche 4 : le bouclier du chevalier (fait, montré dans Blockbench seulement)

- `arms.shield()` peint deux fois côte à côte, avant (`finish.py`, le rendu du jeu) et après : planches de chêne
  teintes et vernies, dos huilé, bord en fer forgé, croix et boss en fer apprêté et peint en bleu, poignée en cuir ;
  `WORN`, `TEMPERATE_OUTDOOR` ; rôles `shield_face` et `grip` ; point focal sur le boss ; trois coups
  (`battle_damage(3, "Face_2", side="right")`).
- Le jeu n'est pas touché : la migration du bouclier se décide à part, montrée avant.

## Fin

- Relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)`.
