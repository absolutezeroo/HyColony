# Plan : l'histoire (blockpaint, lot 3)

Spec : `docs/superpowers/specs/2026-10-03-blockpaint-surfaces-design.md` (§ 3.6 ; § 6, lot 3). Recherche :
`docs/research/blockpaint-surfaces.md` § 5 (γ-ton tracing : des sources, un transport). Même garde-fou que les lots 1
et 2 : rien ne change pour un modèle qui ne déclare rien ; toutes les vérifications de `tools/blockpaint/`.

## Tâche 1 : les événements (`history.py`) (faite)

- `Event(kind, source, direction, radius, falloff, severity, age, effects, shape="round")` :
  - `source` : `point(part, offset, side="front")` (le centre d'une face de la pièce, décalé dans le monde ; sans
    face, le centre de la pièce), `edge(part, side, towards)` (le milieu du bord d'une face le plus loin vers une
    direction du monde : `up`, `down`, `left`, `right`, `front`, `back`), `sky()` (toutes les faces sous le ciel) ;
    résolue en position dans le monde à partir des `Texel` du modèle ;
  - `direction` : `None` (rayonne), un vecteur, ou `GRAVITY` (coule vers le bas) ;
  - `radius` (unités du monde), `falloff` (exposant de l'atténuation ; 0 : entier jusqu'au rayon), `severity` (0 à 1 :
    le degré), `age` (0 récent à 1 ancien) ;
  - `effects` : les effets qu'il pose (`(effect, weight)`) ; `shape` : rond, ou carré dans le plan de la face (une
    pièce de réparation).
- Masque d'un événement en un texel (`reach_of`) :
  - rayonnant : `(1 - d / radius) ** falloff` jusqu'au rayon, `d` la distance à la source ; une face qui tourne le dos
    à la source (normale opposée) n'est pas atteinte ; carré : dans le plan de la face source seulement ;
  - coulant (`GRAVITY` ou un vecteur) : distance au filet qui part de la source dans la direction, l'aval seulement
    (rien en amont), qui s'allonge et s'amincit ; une face tournée dans le sens de la coulée n'est pas atteinte (un
    dessous pour l'eau, un dessus pour la fumée) ; d'une face à l'autre, puisque tout est mesuré dans le monde ;
  - `sky()` : `maps.sky`.
- Ordre : `effects.MOMENTS` devient `("degrade", "past", "deposit", "recent")` ; un événement d'âge ≥ `PAST_AGE` (0,5)
  passe avant les dépôts (un incendie ancien a sa suie sous la poussière), un plus récent après (un coup récent
  tranche la crasse).
- Tests : la zone part de la source et s'arrête au rayon ; une face de dos n'est pas atteinte ; une coulée ne remonte
  pas, passe d'une face à celle du dessous et s'amincit ; l'âge range l'événement avant ou après les dépôts.

## Tâche 2 : les événements de la spec (faite)

- `history.py` (chaque événement ponctuel prend aussi `severity`, `age` et `side`) : `battle_damage(count, part)`
  (quelques impacts lisibles dans le plan de la face : éclats et bosses, le plus gros légèrement décentré),
  `impact(part, offset)`, `dropped(part, side)` (éclats sur l'arête la plus basse), `fire(part, offset)` (roussi et
  suie au-dessus du foyer), `water(part, side)` (coulures et auréoles sous un bord), `blood(part, offset)` (une tache
  et des gouttes), `rust_from(part, offset)` (coulure de rouille sous un rivet), `chemical(part, offset)`,
  `magic(part, offset)` (des veines qui gagnent), `repaired(part, offset, radius)` (pièce carrée d'une autre teinte,
  sa couture plus sombre).
- Les effets qu'ils demandent (`marks.py`, aux noms que le plan ne pose jamais) : `dent` (un creux en relief),
  `scratch`, `scorch`, `smoke_soot`, `water_stain`, `blood`, `rust_streak`, `discolour`, `glow`, `patch` ; et les
  `chips` de `weathering.py`.
- `HISTORY` du module, lu par `catalog.module_surface` ; `Surface.history`.
- Tests, pour chaque événement : sa zone est là où il le dit (autour de la source, en aval d'une coulée), rien
  ailleurs ; déterministe ; alpha intact ; et `semantics.py` répond bien (un incendie noircit au-dessus du foyer).

## Tâche 3 : la planche (faite, montrée dans Blockbench)

- La caisse dans plusieurs histoires (coups de bataille, incendie ancien, dégât des eaux, rouille sous les rivets),
  montrée dans Blockbench.
- Ce que la planche a montré, corrigé avec son test :
  - une source au centre d'une boîte n'atteint aucune face : la source ponctuelle se pose sur une face ;
  - `0 ** 0` vaut 1 : une pièce sans atténuation couvrait toute la face ;
  - les zones de repos effaçaient presque les marques : elles n'amortissent plus que les effets planifiés ;
  - un effet qui revient écrasait la zone de la fois d'avant ;
  - sang et magie en disques pleins : une tache et des gouttes, des veines ;
  - une bosse invisible sous la peinture ou sur un bois teint : son relief (`Island.press`).

## Fin

- Spec § 3.6 à jour ; relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)`.
