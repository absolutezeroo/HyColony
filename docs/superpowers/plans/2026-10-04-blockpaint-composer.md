# Plan : le compositeur de valeurs et d'accents (V1)

Spec : `docs/superpowers/specs/2026-10-04-blockpaint-composer-design.md`. Garde-fou : sans `COMPOSER` ni
`ILLUSTRATION`, toutes les ressources régénérées sont identiques à l'octet ; toutes les vérifications de
`tools/blockpaint/`.

## Tâche 1 : l'importance partagée

- `paint.Painting` gagne `importance` ({pièce: 0..3}) ; `Illustration` perd la sienne ; `illustration.importance_of`
  reste la règle (FOCUS → 2).
- `catalog` lit `IMPORTANCE` d'un module.

## Tâche 2 : ce qui passe de la passe au compositeur

- `materials` (séparation) et `focal` (accents focaux) quittent `illustration.STEPS` ; leurs tests passent dans
  `check_composer.py`, réécrits pour le compositeur (un seul axe ; renfort et recul).

## Tâche 3 : le compositeur

- `composer.py` : `Composer(groups, report, steps)`, `compose(image, records, composer, level)`, les unités, la
  taille du modèle ; `composer_values.py` : groupes, recul, renfort, routage, contraste des matières ;
  `composer_budgets.py` : accents, sombres, clairs, distance.
- `paint.texture` : peinture, passe, compositeur, lumière, débord ; `catalog` : `COMPOSER` (refusé sans
  `CONDITION`), rapport neuf affiché.
- Tests (`check_composer.py`, sur la caisse et une caisse à fond ; `check_composer_materials.py`, sur la caisse et
  des modèles faits à la main : le contraste des matières, le contraste en valeur, l'annulation des groupes, la
  distance) : ceux de la spec § 7.

## Tâche 3 bis : états lisibles (2026-10-04)

- L'apothicaire négligé ressemblait au neuf : `Condition.show`, le budget des effets (`most_useful`, `reachable`),
  le plancher des zones de repos (`art.rest_floor`), la coupe du repos de la passe, le film terne au-delà de Worn
  (`surface.dulled`), la poussière sous abri (`weathering.dust_mask` sur `maps.up`) ; spec surfaces § 3.3.
- Le contraste du compositeur joue sur la valeur seule (la rouille d'un accent virait à l'orange fluo) ; une grande
  unité ne bouge pas pour une petite (`LARGER`).
- Tests : `check_states.py` (les branchements de `show`, le film), `check_art.py`, `check_illustration.py`,
  `check_effects.py`, `check_composer_materials.py` ; chaque mutation des constats de relecture rejouée et tuée.

## Tâche 4 : montrer

- Planche Blockbench de l'entrepôt en trois rendus et rapport ; puis, si l'utilisateur valide, les autres huttes.

## Fin

- Relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)` et `docs`.
