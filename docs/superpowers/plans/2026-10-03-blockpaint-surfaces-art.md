# Plan : la passe artistique et la validation sémantique (blockpaint, lot 2)

Spec : `docs/superpowers/specs/2026-10-03-blockpaint-surfaces-design.md` (§ 2.1, § 2.2, § 3.7, § 3.9 ; § 6, lot 2).
Recherche : `docs/research/blockpaint-surfaces.md` § 2.1 (les budgets mesurés sur Hytale). Suite du lot 1
(`2026-10-03-blockpaint-surfaces-socle.md`), même garde-fou : empreintes des ressources générées identiques avant et
après `python tools/blockpaint` et `tools/domum/generate.py` (rien ne change pour un modèle qui ne déclare rien), et
toutes les vérifications de `tools/blockpaint/` et des ensembles.

Tout passe par `ART = Art(detail="medium", rest=True)` du module, facultatif : sans lui, un modèle avec
`CONDITION` reçoit `Art()` (budget moyen) ; les pièces focales viennent de `FOCUS` ; un modèle sans `CONDITION`
n'est pas touché.

## Tâche 1 : le calme du substrat (faite)

- `art.calm(image, detail)` : chaque passe rapproche chaque texel (`CALM_PULL`) de la moyenne de ses voisins qui lui
  ressemblent (`art.alike`, lu une seule fois sur l'image d'origine, pour que les bords ne s'érodent pas d'une passe à
  l'autre : à moins de `ALIKE_STEP` (30, sommé sur r, g, b ; mesuré sur `brushes.wood` : le grain d'une planche
  s'écarte de 20 à 42, un joint de 42 à 84)) ; un point isolé (aucun voisin semblable, et pas sur une ligne) prend la
  médiane de sa boîte. Jusqu'à ce que l'île tienne l'écart et les sauts de son budget, au plus `CALM_STEPS` fois. La
  part du micro n'est pas visée (lisser ne la baisse guère) : `critique.py` la dit.
- Budgets (`TARGETS`) : `low` la médiane de Hytale, `medium` le 75e centile, `high` le 90e (recherche § 2.1).
- `surface.layered` calme le substrat avant les revêtements : les effets posés ensuite gardent leurs bords nets.
- Tests (`check_art.py`) : un grain bruyant (±15 %) sort sous son budget ; une île calme ressort identique ; les joints
  d'une caisse restent plus sombres et gardent les trois quarts au moins de leur contraste au calme le plus fort ;
  `low` calme plus que `high`.

## Tâche 2 : repos, point focal, nombre d'effets (faite)

- Zones de repos (`rest=True`) : le masque de chaque effet est multiplié par `rest(t, declared)`, plein près d'un bord
  ouvert, du contact, de l'impact ou du point focal, `REST` (0,5) loin de tout cela ; le degré garde son sens (le
  masque change, jamais l'opacité).
- Point focal : quand le module déclare `FOCUS`, ses pièces prennent le budget de détail au-dessus, les autres celui
  du dessous.
- Nombre d'effets visibles : par île, au plus `VISIBLE[detail]` effets (`low` 2, `medium` 3, `high` 4) ; on garde les
  plus utiles (degré × poids du rôle × compatibilité de la famille de l'île), les autres sont retirés du plan de
  l'île.
- Effets qui se battent : un dépôt dont la zone recouvre à plus de `FIGHT` (60 %) celle d'un dépôt déjà posé et plus
  utile n'est pas posé.
- `Island.zones` : `{effet: cellules atteintes}`, gardé pour la validation (tâche 4).
- Tests : loin des bords et du contact, un effet atteint moins de texels qu'avec `rest=False` ; une peinture en couches
  à `FOCUS` calme moins ses pièces focales que les autres (`check_art.py`) ; une île ne garde que ses `VISIBLE` effets
  les plus utiles ; deux dépôts sur la même zone : seul le plus utile est posé.

## Tâche 3 : les pinceaux sans bruit décoratif (faite)

- Retirer des pinceaux de matériaux (`organic.py`, `masonry.py`, `metals.py`, `misc.py`) les termes de bruit texel par
  texel qui n'ont pas de fonction (spec § 2.1) ; garder la structure (pores, grains, fibres, trame) en amas : fourrure
  et corde en mèches et torons de 2 et 3 texels, joints de brique sans grésillement, grains de sable plus rares,
  mousse et rouille en amas plus larges, frontière de la rouille en dégradé.
- Les pinceaux de `brushes.py` utilisés par les modèles d'aujourd'hui ne changent pas (rendu à l'octet) : le calme de
  la tâche 1 s'occupe d'eux dans le nouveau système.
- Tests : `check_brushes.py` : aucun pinceau de matériau peint seul (16 x 16) n'est « trop bruité », « bruit
  uniforme » ni « micro dominant », sauf la brique, dont les joints d'un texel se lisent en micro comme ceux des
  briques de Hytale (part du micro de 1,07 à 1,27 sur ses blocs de brique).

## Tâche 4 : la validation sémantique (faite)

- `semantics.py` (`python tools/blockpaint/semantics.py <tools/<ensemble>/<module>.py>`) repeint le modèle comme
  `catalog`, en une seule cuisson (`semantics.painted` : `catalog.surveyed`, `module_surface`, `module_texture`), en
  gardant chaque île et les zones de ses effets (`Surface.record`) et l'histoire résolue sur tout le modèle, et
  répond aux questions de la spec § 3.9 qui ont de quoi être mesurées (le grain se lit sur le substrat : une peinture
  le cache, comme elle doit) :
  - la terre et la boue surtout en bas (hauteur moyenne de leurs zones sous celle de l'île) ;
  - les pièces de contact (rôle ou usage) plus usées que les autres ;
  - la rouille seulement sur le fer à nu, et surtout exposé ;
  - les fibres selon l'axe déclaré (`AXES` : écart le long de l'axe plus faible qu'en travers) ;
  - le point focal dominant (énergie de détail des pièces focales au moins celle des autres) ;
  - la lecture à la distance de jeu (texture réduite au quart, chaque bloc de 2 x 2 texels en un seul : chaque zone
    d'effet garde au moins un bloc à moitié couvert) ;
  - les matériaux voisins différenciables (écart de couleur moyenne ou de structure au-dessus d'un seuil).
  La mousse (lot 5) et l'histoire (lot 3) ont depuis leur question : mousse à l'humidité, marques à la portée de leur
  événement.
- Une zone d'effet garde au moins `effects.MIN_PATCH` (4) texels liés (`effects.patches`) : plus petite, elle
  disparaît de loin (la question « lisible de loin » l'a montré sur la caisse).
- Tests (`check_semantics.py`) : chaque contrôle passe sur la caisse de démonstration et échoue sur une caisse
  truquée (terre en haut, usure loin des pièces touchées, rouille sur la peinture, grain à contre-sens, point focal
  terne, zone trop petite, deux matériaux de même couleur).

## Tâche 5 : avant et après (faite, montrée dans Blockbench)

- Planche (scratchpad) : l'entrepôt d'aujourd'hui et le même passé au nouveau système (familles des matériaux,
  condition, art), et le rapport de `critique.py` des deux ; montrée dans Blockbench à l'utilisateur.
- Le passage d'une hutte au nouveau système (sa migration) reste un choix de l'utilisateur, hutte par hutte (spec § 5).

## Fin

- Spec : § 3.7 dit `ART` ; recherche § 2.1 : le 75e centile.
- Relecture `hycolony-reviewer` de chaque commit (et de ses corrections) ; commits `feat(tools)`.
