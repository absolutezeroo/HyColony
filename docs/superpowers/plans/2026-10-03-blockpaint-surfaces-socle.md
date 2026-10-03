# Plan : le socle des surfaces en couches (blockpaint, lot 1)

Spec : `docs/superpowers/specs/2026-10-03-blockpaint-surfaces-design.md` (§ 6, lot 1). Recherche :
`docs/research/blockpaint-surfaces.md`. Tout se passe dans `tools/blockpaint/` ; tests d'abord. Ce plan décrit le
code tel qu'il est fait.

Garde-fou de chaque tâche :

- empreintes (sha1) des ressources générées (`plugin/src/main/resources`, `vanilla/plugin/src/main/resources`,
  `domum/plugin/src/main/resources`) avant, puis `python tools/blockpaint` et `tools/domum/generate.py`, puis
  empreintes après : identiques (une comparaison d'empreintes, pas `git status`, que les changements d'une autre
  session fausseraient) ;
- les vérifications `tools/blockpaint/check.py`, `check_brushes.py`, `check_surfaces.py`, `check_layers.py`,
  `check_effects.py`, `check_art.py`, `check_critique.py`, `tools/armor/check.py`, `tools/tape/check.py`.

## Tâche 1 : les cartes cuites (`bake.py`)

- `bake.survey(nodes, grounded, see_through, wear=1.0, grime=1.0)` rend `(values, contexts)` en un seul passage :
  `values` est la lumière de `light_map` (usure des arêtes `WEAR * wear`, crasse du pied `GRIME * grime`, au même
  endroit et dans le même ordre qu'avant) ; `contexts` est `{texel: Texel}` : `point`, `normal`, `edge` (anneau du
  biseau), `rim` (tâche 2), `occlusion`, `height` (0 pour un modèle sans épaisseur), `ground` (proximité du sol, 0 sur
  un modèle qui n'est pas posé), `light`, `face`, `u_dir`, `v_dir`.
- `light_map` vaut `survey(...)[0]` ; `brightness` reçoit l'occlusion déjà calculée (facultative) ; `face_frame` est
  partagé par `texels`, `rims` et `survey`.
- La couleur de la lumière (`graded`, ses deux modes) est dans `shading.py` ; `bake` la réexporte.
- Tests : `check_surfaces.py` (`SurveyTest`).

## Tâche 2 : distance au bord et cartes déduites

- `Texel.rim` (`bake.rims`) : la distance en texels au bord ouvert le plus proche de la face (une jointure ne compte
  pas), bornée à `RIM_CAP` (8).
- `maps.py` : `up`, `down`, `sky` (`up * (1 - occlusion)`), `exposure` (le plus fort du ciel et de la proximité d'un
  bord ouvert, à `RIM_REACH` texels), `ground` (`Texel.ground`), `water` (`up * occlusion`).
- Tests : `RimTest`, `MapsTest`.

## Tâche 3 : graine par face et sens du grain

- `brushes.jitter` mêle `salt * 40503 + seed * SEED_MIX` (une graine 0 redonne les mêmes octets) ; `paint` pose la
  graine le temps d'une île (`brushes.seeded`, remise à 0 même sur une erreur) ; `paint.face_seed` (`zlib.crc32` du
  nœud, du côté et de la graine du modèle). Une tuile (image) d'une face graine est décalée de la graine.
- Sens du grain : `AXES` déclare l'axe d'un nœud dans le repère de sa boîte ; `models.FACE_AXES` dit quelle arête
  suit u et v ; `paint.grain_of` en déduit `u`, `v` ou rien (bois de bout : le grand côté) et le pose
  (`brushes.grain`) ; `brushes.along`, `along_u` et `wood` le lisent. Les pinceaux ne sont jamais tournés.
- Tests : `SeedTest`, `GrainTest`.

## Tâche 4 : l'île en couches (`layers.py`)

- Familles : `brushes.family("wood")` marque les pinceaux d'une fabrique ; `effects.material(..., family=)` la
  remplace ; une famille hors de `compat.FAMILIES` arrête la peinture.
- `Coat(colour, mode, thickness, opacity, gloss, family)` : `cover`, `clear`, `tint`, lustre ; famille de film
  (`paint_film`, `varnish_film`, `metal_film`).
- `Island` : substrat, `Texel` de l'île, revêtements et leurs teintes par texel, profondeur creusée, dépôts, poids
  propres du matériau, zones des effets (`zones`) et signaux (`signals`, `signal()`). `left`, `bare`, `visible`,
  `family_at` se lisent à tout moment ; `dig` (retire les dépôts là où il creuse), `shade`, `tint` (substrat, un
  revêtement, ou la couche visible), `deposit`, `clean`.
- `compose` : substrat (assombri dans le creux d'un éclat), revêtements qui restent (une couche sous le quart de son
  épaisseur laisse transparaître le dessous), leurs teintes, le lustre, les dépôts.
- Variation macro (`swell`) : valeur, teinte et saturation, en position dans le monde, posée sur la pièce entière
  après la composition (`surface.layered`).
- Tests : `check_layers.py` (`LayersTest`, `LayerToolsTest`).

## Tâche 5 : rôles et usage (`roles.py`)

- Le vocabulaire de la spec § 3.4.1 ; chaque rôle donne des cartes déclarées (`contact`, `impact`, `abrasion`,
  `ground`, `focus`) et des poids d'effets ; `USAGE` corrige ; `FOCUS` met `focus` à 1 ; `role_of` arrête la peinture
  sur un rôle inconnu, quel que soit le chemin.
- Tests : `RolesTest` ; `CatalogTest` (un rôle inconnu passé par `catalog`).

## Tâche 6 : effets, degrés, compatibilité, dépendances (`effects.py`, `compat.py`)

- `Effect(name, moment, mask, act, reads, writes, scale, spread, soft)` ; `MOMENTS = ("degrade", "past", "deposit",
  "recent")` (`past` et `recent` : les événements de l'histoire, lot 3).
- `Pass(declared, seed, rest, fight)` : comment se fait un passage sur une face.
- `reached(effect, island, degree, how)` : le score vaut `mask × compat (couche visible) × poids propre × repos
  (art.rest, sauf pour les marques d'un événement) × (1 - spread + spread × amas)` ; atteint strictement au-dessus de
  `1 - degree`, plein dans la zone ; une tache de moins de `MIN_PATCH` (4) texels qui se touchent n'est jamais gardée
  (`patches`) ; un effet doux se fond à `SOFT_AMOUNT` sur les texels voisins à moins de `SOFT_BAND` ; degré 0 : rien.
- `run(island, uses, how)` : une seule suite d'opérations, triée par moment puis par signaux (`ordered`, cycle
  refusé) ; chaque zone s'ajoute à `zones` (un effet qui revient garde les deux) et aux signaux écrits ; un signal lu
  qu'aucun effet du passage n'écrit, ou écrit qu'aucun ne lit, arrête la peinture, sauf l'un de `SIGNALS` (`bare`,
  `damage`, `rust`, `water_retention`, `sticky`).
- `material(substrate, family, coats, effects, weights)` ; `compat.weight` (compatible, possible, impossible ; un
  effet absent de la matrice convient à toutes).
- `paint.paint(nodes, size, look, painting)` / `texture(nodes, size, look, values, painting)` : `Look(tiles,
  material_of, pictures)` et `Painting(seed, axes, surface, light)` ; une entrée `Material` de `tiles` passe par
  `surface.layered` (substrat calmé, revêtements, effets les plus utiles, composition, macro) ; sans `Surface`, un
  `Material` arrête la peinture.
- Tests : `ReachTest`, `RunTest` (`check_runs.py`).

## Tâche 7 : âge, condition, environnement (`conditions.py`)

- `Age(name, degrees)`, `Condition(name, degrees, care, wear, grime, age)`, `Environment(name, weights, degrees)` ;
  `DEFAULT` (le rendu d'avant), `PRISTINE`, `MAINTAINED`, `USED`, `WORN`, `NEGLECTED`, `RUINED` ; `NEW`, `MATURE`,
  `OLD`, `ANCIENT` ; `DRY_INTERIOR`, `TEMPERATE_OUTDOOR` (les sept autres au lot 5).
- `plan(surface, part)` (le catalogue, la condition, l'âge, l'environnement et les rôles de `surface.Surface`) :
  degré de la condition, plus ceux de l'âge (celui de la condition sans âge) et de l'environnement retenus par `care`,
  pondéré par l'environnement et le rôle, au plus 1.
- `catalog.model_texture` : sans `CONDITION`, ou avec `DEFAULT`, le rendu d'avant à l'octet ; sinon `survey` avec
  `wear`, `grime` de la condition, `Surface` (âge, environnement, rôles, usage, focal, graine, art) et lumière
  `hytale`.
- Tests : `PlanTest` (`check_conditions.py`), `CatalogTest` (`check_effects.py` : chaque déclaration d'un module change
  le rendu).

## Tâche 8 : la lumière à la manière de Hytale (`shading.py`)

- `graded(pixel, value, mode)` ; `hytale` : la teinte tourne vers `SHADE_HUE` (275°) dans l'ombre, vers `LIGHT_HUE`
  (50°) dans la lumière, par le plus court chemin ; une teinte lumineuse à plus de `LIGHT_REACH` (110°) de la cible
  garde la sienne (un bleu ne passe pas par le vert) ; saturation gardée ; bornes `LOW`, `HIGH`.
- Tests : `LightTest`, `LayerToolsTest` (`lit` passe le mode).

## Tâche 9 : `critique.py`

- Mesure île par île (`islands_of` : îles d'au moins `MIN_SIDE` texels, ni tournées ni miroir) : écart moyen, part des
  sauts, micro / (meso + macro), étendue des sauts, virage des ombres ; verdicts « trop plat », « trop bruité »,
  « bruit uniforme », « micro dominant », « ombres sans virage », d'après les budgets tirés de 1 741 îles de mobilier
  de Hytale (recherche § 2.1). `python tools/blockpaint/critique.py <model.blockymodel>`.
- Tests : `check_critique.py` (chaque verdict tombe sur 7 à 13 % des îles de Hytale, 3 à 13 % pour les ombres ;
  l'archive est lue, jamais recopiée ; le test est sauté sans elle).

## Tâche 10 : le contenu de démonstration

- `coatings.py` : `primer_coat`, `paint_coat`, `varnish_coat`, `stain_coat`.
- `weathering.py` : `edge_wear`, `chips` (écrit `bare` et `damage`), `rust` (lit `bare` et `damage` : plus forte
  autour des dégâts), `grime`, `dirt`, `dust`.
- Tests : `ContentTest` (chaque effet : stable, zone qui grandit, plein là où il est, rien hors de son masque, alpha
  intact, et son sens).

## Tâche 11 : la planche

- Caisse peinte cerclée de fer dans plusieurs conditions, et l'entrepôt avant et après, montrés dans Blockbench.

## Fin

- Relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)`.
