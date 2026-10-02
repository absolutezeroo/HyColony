# Modèles des blocs de hutte

Date : 2026-10-02. Statut : constructeur, hôtel de ville, lunettes et outil de construction faits ; maison, fermier et
bûcheron à venir.

## But

Chaque bloc de hutte a son propre modèle, qui montre son métier au premier coup d'œil. Jusqu'ici, toutes les huttes
empruntaient un petit coffre de Hytale (`Decorative_Sets/<Set>/Chest_Small`).

*Écart avec MC*, demandé par l'utilisateur : MineColonies fait de chaque hutte une scène miniature
(`sources/minecolonies/.../models/block/blockhut*.json`, de 11 à 34 éléments, textures de Minecraft). Nous ne
la portons pas. Chaque hutte a un design à nous, dans le style des meubles de Hytale.

## Règles communes

- **Une case de large**, comme le bloc de hutte de MineColonies (`SHAPE = Shapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)`,
  `AbstractColonyBlock.java:80`) : les plans des bâtiments posent la hutte au milieu d'autres blocs. Posé sur un
  support plein, comme avant.
- **Taille des meubles de Hytale** (vu en jeu le 2026-10-02 : une chaise de Hytale dépassait l'hôtel de ville). Les
  meubles de Hytale sont massifs : torche de 37 unités de haut, lit Village de 48 de haut, établi d'architecte de
  59. Une hutte remplit la largeur de sa case et **monte au-dessus d'un bloc**, jusqu'à 1,4 bloc (44,8 unités).
  Elle y arrive soit par sa géométrie (constructeur), soit par `CustomModelScale` (hôtel de ville, ×1,4, dessiné
  dans 22,8 unités de large). Sa hitbox `HyColony_Hut` fait 1×1,4×1 : Hytale pose une cellule de remplissage
  au-dessus (`FillerBlockUtil.forEachFillerBlock`).
  - *Écart avec MC*, demandé par l'utilisateur pour la taille des meubles de Hytale : chez MineColonies, la hutte tient
    dans sa case (`AbstractColonyBlock.java:80`) et on peut poser un bloc dessus. Ici, la case au-dessus est occupée par
    la cellule de remplissage, que `HytaleWorldBlocks` lit comme la hutte (le constructeur ne la casse pas). Les 35
    plans MedievalOak laissent cette case vide.
- Le devant du modèle est du côté +z : c'est lui qui fait face au joueur qui pose le bloc, une fois tourné par
  `VariantRotation` `NESW`. Vu en jeu le 2026-10-02 : un premier constructeur tourné vers -z s'affichait à l'envers.
  La serrure du petit coffre de Hytale est bien à +z (ses charnières sont à -z), une fois les enfants placés selon la
  règle de `docs/research/hytale-models.md` § 3.
- Pas tout au cordeau : ce qui est posé, pendu ou en tissu prend un léger biais (registre, cloche, tapis, bannière
  plissée). Les pièces de menuiserie (pieds, mâts, pupitres) restent droites.
- Le modèle est construit dans Blockbench, au format `hytale_prop`, et suit `docs/research/hytale-models.md` :
  - boîtes seulement, ombrage `standard`, une zone d'UV par face ;
  - dessous posés sur une surface retirés ;
  - aucune face confondue ;
  - chaque groupe porte le nom de sa pièce principale ;
  - une zone d'UV par face (voir « Peinture ») ;
  - 2 pixels de marge entre les zones d'UV, que le peintre remplit en prolongeant le bord de chaque zone d'un pixel
    (`paint.bleed`) : sinon une face fine lit la zone voisine (vu sur les lunettes le 2026-10-02). Avec 1 pixel, le
    pixel entre deux zones ne prolongerait que la première.
- Il est livré tel quel : `plugin/src/main/resources/Common/Blocks/HyColony/Huts/<Hutte>.blockymodel`.
- `tools/huts/generate.py` peint la texture (`Huts/<Hutte>.png`) avec `tools/vanilla/paint.py` et les matières du
  module de la hutte (`tools/huts/<hutte>.py`). Il dessine aussi l'icône (`Icons/Items/HyColony/Hut_<Hutte>.png`),
  vue de face (`pack.draw_model`, vue depuis +x +z).
- L'objet de la hutte lit ce modèle, cette texture et cette icône. Les objets `HyColony_Hut_<Hutte>` gardent leurs
  états `OpenWindow` et `CloseWindow` (sons du coffre), sans l'animation du couvercle ; `HyColony_TownHall` n'en a
  jamais eu.

## Constructeur

Une table à dessin d'architecte, en 40 boîtes, avec une texture de 256×96 :

- **Bâti** en bois brun peint (pinceau `wood`) : quatre pieds de 4×4 (ceux de l'arrière plus hauts), des
  traverses basses, une étagère et une traverse haute sur chaque flanc.
- **Planche à dessin** en bois clair peint, de 32×28 (toute la largeur), inclinée de 24° vers le
  joueur, avec un rebord à l'avant. Dessus :
  - un plan bleu quadrillé de 24×18, avec un cadre et le croquis d'une maison qui se lit depuis l'avant, et une
    tranche bleu foncé ;
  - un rouleau de papier ;
  - un crayon (cône de bois et mine noire) ;
  - un encrier bleu nuit sur un socle, dans le coin avant de la planche, avec sa plume penchée vers la feuille.
- **Hauteur**, à sa taille réelle (sans `CustomModelScale`) : pieds avant de 28, pieds arrière de 36 ; le point le
  plus haut, le rouleau de papier, monte à 41 (1,28 bloc).
- **Sur l'étagère** : trois briques et trois rouleaux de plans (papier crème, bout en spirale, liens rouges, dont un
  plan bleu).
- **Sur les flancs** :
  - un marteau, dont la tête repose sur deux crochets en fer forgé ;
  - une scie (lame à dents, poignée fermée), pendue à un crochet qui passe dans sa poignée.

## Hôtel de ville

Un pupitre officiel sur un tapis, avec la bannière et la cloche de la colonie, en 38 boîtes, avec une texture de
128×96. Dessiné dans 22,8 unités de large et agrandi ×1,4 en jeu (`CustomModelScale`) : 1 bloc de large, 1,4 de haut.

- **Tapis** : un velours rouge peint en touffes, de 16×2×16, sur un socle de 18×18 dont le bord forme un liseré doré,
  avec cinq pompons dorés devant et cinq derrière, juste au-dessus du sol. L'ensemble est tourné de 3°, et chaque
  pompon a son propre biais.
- **Pupitre** en bois sombre peint : un pied, un fût droit qui entre dans l'épaisseur de la
  planche, et une planche inclinée de 25° vers le joueur. Dessus :
  - le registre de la colonie ouvert, en cuir rouge sombre, avec ses pages écrites, posé de biais (6°) ;
  - un encrier avec sa plume.
- **Bannière** (derrière, d'un côté) : un mât en bois coiffé d'un pommeau doré tourné d'un huitième de tour, et une
  barre. La bannière rouge est faite de quatre bandes de 2 de large, inclinées alternativement de ±25° : elle
  ondule en plis. Ses bandes portent chacune leur part de l'emblème (liserés dorés et maison dorée de la colonie). Les
  bandes extérieures, plus longues, forment les deux pointes, et le bas est irrégulier. La barre a 3 de profondeur et
  couvre le haut des plis.
- **Cloche** (derrière, de l'autre côté) : une potence en bois, une corde et une cloche en bronze
  brossé (pinceau `metal`) balancée de 7°, avec son battant en fer.
- **Objet** : hitbox `HyColony_Hut` (1×1,4×1). L'objet utilisait jusqu'ici la hitbox `Bench_Architect` de Hytale
  (2 blocs de large, 1,8 de haut) avec le modèle de l'établi d'architecte. Casse, particules et sons passent de la
  pierre au bois, comme le bloc de MineColonies (`BlockHutTownHall.java:51`, `MapColor.WOOD`, `SoundType.WOOD`).

## Lunettes de construction

Des lunettes d'architecte, pièce d'armure de tête (racine `Head` du casque `Armors/Diving_Crude/Head` de Hytale), en
29 boîtes, ombrage `flat` comme les armures de Hytale, calées sur la tête du joueur (30×28×28, yeux à y ≈ 18) :
un anneau de cuir cousu autour de la tête, deux coques de cuir à monture de laiton et verres de cristal cyan
lumineux (`fullbright`, le cristal cyan de la recette), un pont, une loupe à charnière relevée sur le front et une
molette de réglage. L'objet lit `Items/HyColony/Build_Goggles.{blockymodel,png}` et son icône.

## Outil de construction

Un marteau d'architecte, objet tenu (racine `R-Attachment`, calé comme la baguette de Hytale, animation « Item »),
en 15 boîtes : manche en hêtre, pommeau et bague en laiton, poignée gainée de cuir, tête en fer brossé avec face de
frappe en acier, panne en deux paliers, deux bagues de laiton, et un cristal cyan taillé de chaque côté (losange
tourné de 45° et sa table), serti dans un cadre en laiton. Choisi par l'utilisateur parmi quatre pistes (sceptre,
marteau, compas, bâton à plan) ; un premier jet en équerre et fil à plomb a été écarté. L'objet lit
`Items/HyColony/Build_Tool.{blockymodel,png}`, son icône et le son `ISS_Weapons_Wood` des marteaux de Hytale.
Son icône le pose en diagonale, tête en haut à gauche, vu de trois quarts côté gemme, comme les icônes d'outils de
Hytale (`Icons/ItemsGenerated/Tool_Hammer_*`) ; les huttes et les lunettes gardent la vue isométrique.

## Peinture

Retour de l'utilisateur sur les premiers jets : textures trop nettes, sans le travail au crayon et à la brosse douce
que conseille Hytale. Chaque modèle passe donc par trois étapes (`tools/huts/generate.py`, `texture`) :

1. ses matières : les pinceaux de `tools/vanilla/brushes.py` (partagés avec les lits et pots de HyVanilla), qui
   peignent une zone entière (bois, métal brossé, cristal taillé, papier, tissu, pierre, minerai, terre cuite ;
   pierre et minerai pour les huttes de mineur et de carrier, demandés par l'utilisateur), et quelques teintes de
   tuiles de Hytale (`tools/huts/materials.py` : cuir, plume…) ;
2. `tools/vanilla/bake.py` cuit la lumière à partir du modèle lui-même, pixel par pixel : occlusion ambiante (rayons
   contre les autres boîtes et, pour un bloc, le sol), ombre portée d'une lumière en haut à l'avant gauche, biseau
   sur les deux anneaux de pixels du bord de chaque zone avec usure (éclats clairs) côté lumière (une arête ne
   s'assombrit que tournée vers le bas, une jointure entre deux boîtes n'a pas de biseau), crasse au pied d'un
   bloc, variation douce de teinte, ombres froides et lumières chaudes ; la direction de la lumière aussi sur les
   faces `flat` ; une occlusion réduite et pas d'ombre portée sur les faces lumineuses ;
3. le débordement des bords dans les marges (`paint.bleed`).

**Une zone d'UV par face** : une zone ne peut porter que la lumière d'une seule face. `bake.light` refuse un modèle
dont deux faces partagent un pixel (relecture du 2026-10-02 : l'avant d'un pied montrait la lumière de son arrière).

## Huttes suivantes

Pistes proposées à l'utilisateur, chacune à valider sur des captures Blockbench avant intégration :

| Hutte | Piste |
|---|---|
| Maison | Maisonnette miniature : toit à deux pans, cheminée, porte et fenêtre éclairée. |
| Fermier | Carré de terre avec des pousses, caisse de légumes, fourche et houe croisées, arrosoir. |
| Bûcheron | Souche avec une hache plantée, tas de bûches, jeune arbre. Bloc avec le métier (pas encore porté). |

## À vérifier en jeu

- Le constructeur et l'hôtel de ville s'affichent avec leur modèle et leur texture, sans face qui scintille ni bord de
  texture étranger, à la taille des meubles de Hytale, et font face au joueur qui les pose, dans les quatre
  directions.
- Les icônes montrent la hutte de face.
- Utiliser une hutte ouvre toujours sa fenêtre ; aucune erreur d'animation au journal. L'hôtel de ville crée la
  colonie comme avant.
- Le bloc au-dessus d'une hutte posée après ce changement est occupé (cellule de remplissage de la hitbox de 1,4) : on
  ne peut rien y poser.
- **Vieux mondes** :
  - un hôtel de ville posé avant ce changement garde trois cellules de remplissage de l'ancienne hitbox
    `Bench_Architect` ((-1, 0, 0), (-1, 1, 0) et (0, 1, 0) pour la rotation de base ; elles tournent avec `NESW`,
    `FillerBlockUtil.forEachFillerBlock`). Rien ne les revalide au chargement. Casser l'hôtel de ville ne retire que
    la cellule au-dessus de lui (`removeFillerBlocksAt` suit la hitbox actuelle, 1×1,4×1, et ne retire qu'une case du
    même id) : les deux de côté restent. Elles sont invisibles et ne bloquent pas, mais occupent leurs cases et se
    lisent comme une hutte. Pour nettoyer : poser puis casser un bloc dans chacune des deux ;
  - une hutte de constructeur posée avant ce changement n'a pas de cellule au-dessus : un bloc posé là traverse le
    nouveau modèle (1,28 bloc). Reposer la hutte règle le cas.
