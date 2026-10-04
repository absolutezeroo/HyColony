# Plan : la passe d'illustration (V1)

Spec : `docs/superpowers/specs/2026-10-04-blockpaint-illustration-design.md`. Garde-fou : sans `ILLUSTRATION`, toutes
les ressources régénérées sont identiques à l'octet ; toutes les vérifications de `tools/blockpaint/`.

## Tâche 1 : l'image avant les effets

- `surface.layered` : quand la surface enregistre ses îles (`record`), l'île garde `base`, ses couches composées avant
  les effets et gonflées par la variation macro comme l'image finale.
- Test : `base` est l'image sans effets ; elle ne change pas l'image finale.

## Tâche 2 : la passe

- `illustration.py` : `Illustration(report, steps)`, `illustrate(image, records, illustration, level)` ; les
  opérations de la spec § 3 dans l'ordre de `STEPS` (repos et retrait, modelé, cadre des faces, contact, reflets
  sélectifs), chacune avec ses lignes de rapport ; la séparation des matières et les accents focaux sont au
  compositeur.
- `paint.Painting` gagne `illustration`, `composer` et `importance` (None : rien ne change) ; `paint.texture` applique
  la passe puis le compositeur entre la peinture et la lumière.
- `catalog` : `ILLUSTRATION` d'un module en couches (refusée sans `CONDITION`) ; les îles enregistrées ; un rapport
  neuf à chaque peinture, affiché.
- Tests (`check_illustration.py`, sur la caisse de démonstration) : ceux de la spec § 7.

## Tâche 2 bis : le grain des pinceaux et les planches (lot 6, faite)

- `brushes` : planches à joint, liseré et arrondi ; touches (`touch`) et stries larges (`STROKE`) là où un motif
  texel par texel faisait des points ; bornes de `check_brushes` calées sur Hytale (recherche § 8).
- `materials.BOARD`, `BARREL_STAVE`, `BARREL_JOINT` : planches d'au moins 4 texels, tonneaux de Hytale.

## Tâche 3 : montrer

- Planche Blockbench de la caisse, sans et avec la passe, et le rapport ; mesures de `critique.py` avant et après.
- Puis, si l'utilisateur la valide, une hutte.

## Fin

- Relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)` et `docs`.
