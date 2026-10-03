# Spec : les dessins et les décalques de blockpaint

Date : 2026-10-03. Suite de `2026-10-03-blockpaint-surfaces-design.md` (le système en couches). Outil seulement :
`tools/blockpaint/` et les modules des modèles ; aucune règle de jeu.

## 1. Le problème

Les petits détails se perdent dans le système en couches : le cadran de l'horloge et le tableau de la résidence, le
croquis du plan et les dents de la scie du constructeur, les cernes des bûches. Trois causes :

- le **calme du substrat** (`art.calm`) traite un dessin comme du bruit et l'efface ;
- les **effets** (usure des arêtes, éclats) s'y posent comme sur du bois ;
- un **détail plus petit que sa face** (un cadran dans un boîtier, des dents au bas d'une lame) est peint dans la
  même île que le matériau autour : il ne peut ni avoir sa propre surface ni dépasser du bord de la boîte (les dents
  d'une scie sont une découpe, pas une teinte).

## 2. Ce que fait Hytale

Mesuré sur les 447 modèles de `Decorative_Sets/` (Assets.zip de la version épinglée, 2026-10-03) :

- 3 867 boîtes et 949 quads (plans sans épaisseur). La plupart des quads sont des plans libres (toiles, tissus,
  feuilles, vitres) ; 31 sont des **décalques** posés à plat devant la face d'une boîte, à 0 (10), 0,1 (6) ou entre
  0,04 et 0,08 unité : surfaces gravables (`Editable_Surface`), mousse, peaux, intérieurs. 17 de ces 31 sont en
  ombrage `standard` et à deux faces (`doubleSided`), 11 en `flat` à deux faces.
- La densité reste **1 px par unité** partout sur les accessoires (`docs/research/hytale-models.md` § 2) : un décalque
  n'apporte pas plus de résolution. L'étirement sous 1 qu'on trouve (541 boîtes) sert à ajuster une pièce (0,9 à
  0,99), jamais à densifier un détail.
- La plupart des détails restent peints dans la texture de la face.

## 3. Deux outils

### 3.1 Le dessin (une face entière dessinée)

Quand le détail occupe **toute** la face (le tableau, le cadran s'il remplit la face, la page du registre, les cernes
au bout d'une bûche), il reste peint sur la face, mais son matériau est un **dessin** :

- `brushes.drawing(brush)` renvoie un pinceau qui peint comme lui, marqué dessin (`drawn`), sans toucher au pinceau
  d'origine (un autre matériau peut le partager) ; un module le déclare comme aujourd'hui dans `PICTURES`, et `catalog`
  en fait d'office des dessins ;
- une tuile image d'un dessin est posée telle quelle, ni tournée ni décalée par la graine de la face (correction du
  moteur : avec un `SEED`, `paint.paint` décalait aussi les dessins) ;
- dans le système en couches, un dessin n'est **jamais calmé** et ne reçoit que **ce qui se dépose dessus** : les
  dépôts du plan (poussière, crasse, terre…) et les événements de l'histoire. Pas d'usure des arêtes, d'éclats ni de
  dégradation de matériau : un dessin n'est pas un substrat.

### 3.2 Le décalque (un détail sur une partie de face, ou qui en déborde)

Quand le détail est plus petit que la face, ou doit découper la silhouette (dents de scie), il devient un
**décalque** : un quad posé devant la face, avec sa propre île et de la transparence.

- **Géométrie**, comme Hytale : un quad `+Z` à `DECAL_GAP` = 0,1 unité devant la face (l'écart le plus courant chez
  Hytale après 0, qui scintille avec la face), tourné vers la normale de la face par l'orientation de son nœud, de
  l'ombrage de sa pièce (`standard` le plus souvent, pour que le moteur l'éclaire comme elle) et à deux faces. Nom :
  `<Pièce>_Decal_<nom>`. Il suit les animations de sa pièce :
  - enfant de la pièce quand elle est un nœud à part entière ;
  - à côté d'elle, dans le même groupe et boîte statique lui-même (`isStaticBox`), quand elle est une boîte statique
    (un cube rangé dans un groupe Blockbench) : le greffon Hytale de Blockbench ne lit pas les enfants d'une boîte
    statique (`hytale_plugin.js`, `parseNode` : seul un nœud sans `isStaticBox` devient un groupe).
- Une face déroulée dans l'autre sens (le flanc gauche d'une lame) voit son dessin à l'envers : ses dents se dessinent
  en miroir pour tomber sur celles du flanc droit.
- **Déclaration** dans le module : `DECALS = {nom: Decal(part, side, rect, brush)}` :
  - `part`, `side` : la face qui le porte ;
  - `rect` : `(s, t, w, h)` en texels de la face (`s`, `t` depuis le coin haut gauche, comme Blockbench la déroule) ;
    il peut déborder de la face (les dents sous la lame) ;
  - `brush` : un dessin `(w, h, side) -> image RGBA`, transparent hors du détail.
- **`catalog`** pose les quads avant de peindre : il retire les anciens `<Pièce>_Decal_*` de la pièce, ajoute ceux de
  `DECALS`, puis déroule comme aujourd'hui. Un modèle sans `DECALS` ne change pas.
- **Peinture** : le décalque est un dessin (§ 3.1). Sa transparence est gardée. Sa lumière est cuite à son propre
  point, 0,1 devant la face : presque celle de la face dessous (même occlusion et même ombre portée à 0,1 près ;
  sans biseau ni coin d'arête), et, là où il déborde de la face (les dents sous la lame), celle de l'air à cet
  endroit.
- `Decal.rect` est en texels entiers, d'au moins 1 x 1 : sinon ses texels ne tomberaient plus sur ceux de la face.

### 3.3 Le moteur apprend les quads

Aujourd'hui `paint.islands`, `models.unwrap`, `bake.survey` et `icons.draw_model` ne voient que des boîtes. Un quad
n'a qu'une face (`front` dans `textureLayout`) qui couvre son `x` et son `y`. Le moteur ne peint que les quads de
normale `+Z` (la plus courante chez Hytale : 10 076 sur 12 126) ; l'orientation de leur nœud les tourne, et un quad
d'une autre normale est refusé avec un message clair :

- `models.face_size`, `face_local` et `face_normal` d'une boîte ou d'un quad, `quad_shape` ; `unwrap` et `islands`
  déroulent un quad comme une face de boîte ;
- `bake.survey` : ses texels, sa normale, pas de bord ouvert (`rim` à `RIM_CAP`, aucun anneau de biseau : un
  décalque n'a pas d'arête) ; il n'occulte rien (`world_boxes` ignore déjà ce qui n'est pas une boîte) ;
- `icons.draw_model` le dessine, transparence comprise ;
- `trim` et `cull` le laissent tel quel ; `critique.py` ne mesure que les boîtes : un décalque n'y entre pas. Un dessin
  peint sur la face d'une boîte y est mesuré comme le reste (`critique.py` lit le modèle et sa texture, pas les
  matériaux) : un verdict « trop bruité » sur un tableau se lit comme tel. `semantics.py` juge les zones d'un dessin
  comme les autres : ce sont des dépôts.

### 3.4 Un modèle en couches l'est en entier

Un module qui déclare une `CONDITION` passe **toutes** ses tuiles par le système en couches, sans rien de l'ancien
rendu : un pinceau devient un matériau de sa famille, une tuile image le substrat de son matériau (`paint.tiled` :
tournée et décalée comme `paint` la pose, sauf un dessin), un `PICTURES` un dessin. Chaque matériau a une famille, celle
de son pinceau ou celle que le module nomme dans `FAMILY` (spec surfaces § 3.1), sinon la peinture s'arrête ; un
décalque prend la famille du matériau de la face où il se pose : ce qui se dépose sur la pièce se dépose sur lui.

Les modèles peints hors du catalogue (armure et armes, ruban de chantier, lits et pots de HyVanilla) passent par le
même chemin (`catalog.model_texture`, ou `module_texture` quand une seule mesure du modèle sert plusieurs couleurs) :
leur générateur décrit ses matériaux comme un module (`finish.py`, un `SimpleNamespace` pour les autres), avec
`GROUNDED` quand le modèle pose sur le sol sans être sous `Blocks/`.

L'état et le lieu de chaque modèle du jeu (tous avec `SEED` 11) :

| Modèles | État | Lieu |
|---|---|---|
| mairie, entrepôt, coursier, constructeur, résidence | `USED` | `DRY_INTERIOR` |
| cuisinier | `USED` | `INDUSTRIAL` |
| fermier, plantation, fleuriste | `USED` | `TEMPERATE_OUTDOOR` |
| bûcheron | `WORN` | `FOREST` |
| mineur, les trois carrières | `WORN` | `TEMPERATE_OUTDOOR` |
| lunettes, outil de construction, presse-papiers | `MAINTAINED` | aucun |
| armure, épée et bouclier | `MAINTAINED` | aucun (portés partout) |
| ruban de chantier | `USED` | `TEMPERATE_OUTDOOR` |
| lits | `MAINTAINED` | `DRY_INTERIOR` |
| pots de fleurs | `MAINTAINED` | aucun (dedans ou dehors) |

## 4. Rétrocompatibilité

Un modèle qui ne déclare ni `DECALS` ni `CONDITION` se peint à l'octet comme avant : les dessins n'y sont pas calmés
(rien ne l'est hors du système en couches) et, sans `SEED`, aucun décalage n'existait. Vérifié par la régénération de
toutes les ressources (empreintes sha1).

## 5. Premiers modèles

Montrés dans Blockbench (avant/après) et validés par l'utilisateur le 2026-10-03, puis intégrés, en couches
(`USED`, `DRY_INTERIOR`, `SEED` 11) :

- **constructeur** : la lame en fer nu, ses dents en décalque sur ses deux flancs, une rangée sous la lame ; la feuille
  en papier quadrillé, le croquis de la maison en décalque ;
- **résidence** : le boîtier de l'horloge en bois, le cadran en décalque ; le tableau et les cernes en dessins ;
- puis les autres `PICTURES` des huttes (registre, panneau du coursier, ragoût, arrosoir, bannières), au cas par cas.

## 6. Tests

- un dessin n'est jamais calmé et ne reçoit ni usure ni éclats, mais reçoit la poussière ;
- une tuile dessin n'est pas décalée par la graine ;
- un quad se déroule, se cuit (normale, pas de biseau) et se peint ; sa transparence est gardée jusqu'au PNG ;
- `catalog` pose les quads de `DECALS` à `DECAL_GAP` devant la face, enfants de la pièce, et retire les anciens ;
- un décalque reçoit la lumière de la face dessous, et prend la famille de sa pièce ;
- une plante ou un liquide reçoit la poussière, jamais l'usure ni les éclats ;
- un module en couches nomme la famille de chaque matériau (`FAMILY` l'emporte sur celle du pinceau) ou la peinture
  s'arrête ; `GROUNDED` l'emporte sur le chemin du modèle ;
- un modèle sans `DECALS` ni `CONDITION` : rendu identique à l'octet.
