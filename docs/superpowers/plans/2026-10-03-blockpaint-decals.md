# Plan : les dessins et les décalques (blockpaint)

Spec : `docs/superpowers/specs/2026-10-03-blockpaint-decals-design.md`. Garde-fou : un modèle qui ne déclare ni
`DECALS` ni `CONDITION` se peint à l'octet comme avant (empreintes sha1 de toutes les ressources) ; toutes les
vérifications de `tools/blockpaint/`.

## Tâche 1 : le dessin (faite)

- `brushes.drawing(brush)` (une enveloppe marquée `drawn`, le pinceau d'origine intact) ; `catalog` fait des
  matériaux de `PICTURES` des dessins ; les braises et le cristal sont des dessins (leur lueur est de la lumière) ;
  `paint.tiled(tile, picture)` pose une tuile image comme pinceau (un dessin : ni tournée ni décalée).
- `paint.paint` : une tuile de `PICTURES` n'est plus décalée par la graine de la face.
- `surface.layered` : un dessin n'est pas calmé et ne reçoit du plan que les dépôts (moment `deposit`) ; l'histoire
  l'atteint comme le reste.
- Tests (`check_art`, `check_surfaces`) : dessin non calmé ; ni usure ni éclats mais la poussière ; tuile dessin non
  décalée par `SEED` ; une image posée par `paint.tiled`.

## Tâche 2 : les quads (faite)

- `models` : `face_size`, `face_local`, `face_normal` d'une boîte ou d'un quad `+Z` (une autre normale est refusée :
  l'orientation du nœud le tourne) ; `quad_shape` ; `unwrap` et `paint.islands` déroulent les quads.
- `bake.survey` / `baked` : texels d'un quad, sans biseau, `rim` à `RIM_CAP`.
- `icons.draw_model` : un quad dessiné, transparence comprise.
- Tests (`check_decals`) : un quad se déroule, se cuit et se peint ; sa transparence reste jusqu'au PNG ; sa lumière
  est celle de la face dessous ; l'icône le montre ; une autre normale refusée.

## Tâche 3 : les décalques (faite)

- `decals.py` : `Decal(part, side, rect, brush)`, `DECAL_GAP` (0,1) ; `place(nodes, decals)` retire les anciens
  décalques et pose un quad par décalque devant la face : enfant de la pièce, ou à côté d'elle (boîte statique dans
  le même groupe) quand elle est une boîte statique.
- `catalog` : pose les quads de `DECALS` avant de dérouler (modèles de blocs seulement) ; le matériau d'un décalque
  est son dessin ; un modèle à `CONDITION` passe toutes ses tuiles par le système en couches.
- Tests (`check_decals`) : chaque texel du décalque sur son texel de face, `DECAL_GAP` devant, sur les six côtés d'une
  pièce tournée, statique ou non ; ancien retiré ; pièce absente ou étirée refusée ; peint en dessin, en couches ou
  non ; tout le modèle en couches ; décalques sur un objet refusés.

## Tâche 4 : les premiers modèles (faite)

- Constructeur : dents de scie en décalque débordant sous la lame (en miroir sur le flanc gauche) ; croquis du plan.
- Résidence : cadran en décalque ; tableau et cernes en dessins.
- Montrés dans Blockbench (avant/après), validés, puis intégrés en couches (`USED`, `DRY_INTERIOR`, `SEED` 11).

## Tâche 5 : tous les modèles en couches (faite)

- Chaque module des huttes et des objets déclare sa `CONDITION`, son lieu et sa `SEED` (tableau de la spec, § 3.4) ;
  plus rien ne se peint par l'ancien rendu.
- Familles : un matériau en couches en a toujours une (`catalog.layered_tiles` refuse une famille absente ou
  inconnue) ; `FAMILY` la nomme pour une tuile image ou la corrige (laiton, cuivre, bronze en `cuprous`, or en
  `noble`, légumes en `plant`…) ; un décalque prend celle de sa pièce ; `plant` et `liquid` (`compat.SETTLED_ONLY`)
  ne reçoivent que les dépôts et l'histoire.
- `catalog.material_of(module)` : les matériaux du modèle tel que `catalog` le peint, décalques compris (aussi pour
  `semantics.answers`) ; `GROUNDED` pour un modèle posé au sol hors de `Blocks/`.
- Armure et armes (`finish.py`), ruban de chantier, lits et pots de HyVanilla passent par `catalog.model_texture` ou
  `module_texture`.
- Tests (`check_art`, `check_decals`, `check_semantics`, `check_conditions`, `huts/check_materials`) : plante et
  liquide sans usure ; famille obligatoire, `FAMILY` prioritaire ; `GROUNDED` ; famille d'un décalque ; un décalque
  sur une face absente ou d'un matériau inconnu refusé ; `semantics` sur un modèle à décalques.
- Tous montrés dans Blockbench (avant/après).

## Fin

- Relecture `hycolony-reviewer` (et de ses corrections) ; commits `feat(tools)` et `docs`.
