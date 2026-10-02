# Modèles des blocs de hutte

Date : 2026-10-02. Statut : constructeur, hôtel de ville, résidence, fermier, cuisinier, coursier, entrepôt, lunettes,
outil de construction et presse-papiers faits ; bûcheron à venir, avec son métier.

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
  - Une hutte dont le meuble de Hytale équivalent est plus haut monte **jusqu'à 2 blocs** (64 unités), avec la
    hitbox `HyColony_Hut_Tall` (1×2×1). Elle occupe les mêmes cases : `FillerBlockUtil` arrondit la hauteur de la
    même façon, une seule cellule de remplissage au-dessus. Ainsi le cuisinier (l'établi de cuisine de Hytale fait
    2,2 blocs de haut, le fourneau 2), la résidence, le coursier et l'entrepôt, à la demande de l'utilisateur, qui
    garde le constructeur, l'hôtel de ville et le fermier à leur taille.
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
- `tools/huts/generate.py` peint la texture (`Huts/<Hutte>.png`) avec `tools/common/paint.py` et les matières du
  module de la hutte (`tools/huts/<hutte>.py`). Il dessine aussi l'icône (`Icons/Items/HyColony/Hut_<Hutte>.png`),
  vue de face (`icons.draw_model`, vue depuis +x +z).
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

## Résidence

Un âtre douillet en pierre, en 45 boîtes, avec une texture de 96×224, à sa taille réelle (sans `CustomModelScale`) :
une grande cheminée ouverte dont le chapeau monte à 64 (2 blocs). Choisi par l'utilisateur parmi deux pistes (âtre,
table de chevet).

- **Maçonnerie** en pierre peinte (pinceau `stone`) : un âtre dallé de 32×3×24, deux montants et un fond de 28 de
  haut dont les faces tournées vers le feu sont noircies de suie, une hotte de 18 et un conduit coiffé d'un chapeau
  qui déborde.
- **Pare-feu** en fer devant le foyer, à la demande de l'utilisateur : une traverse basse, une traverse haute et six
  barreaux, entre les montants.
- **Feu** : un lit de braises lumineuses (`fullbright`, fissures orange et jaunes), trois bûches croisées (écorce, et
  anneaux de coupe aux bouts) sur deux chenets en fer. Les flammes sont des particules : le système
  `HyColony_Hearth` (`Server/Particles/HyColony/`) reprend les flammes et les étincelles de `Campfire_New_Cartoon`,
  sans sa fumée ni sa distorsion, qui monteraient à travers le manteau. Il est accroché au nœud vide `Flame`, au cœur
  des bûches, à l'échelle 0,45. L'âtre éclaire d'une lueur chaude (`Light` `#c96`), comme les braseros de Hytale.
- **Crémaillère** en fer et **bouilloire** en cuivre brossé pendue haut au-dessus du feu (fond, panse, bord, anse,
  bec).
- **Manteau** en bois sombre sur une poutre : une bougie dans son bougeoir en laiton (flamme `fullbright`), un portrait
  de famille dans un cadre doré (paysage, maisonnette au toit rouge), penché contre la hotte, et un pot en terre
  cuite. Au-dessus, une horloge murale en bois sur la hotte (cadran crème, aiguilles à dix heures dix, balancier en
  laiton).
- **Devant** : un tas de bûches qui montre ses anneaux de coupe, et un tisonnier appuyé contre le montant droit.
- **Objet** : hitbox `HyColony_Hut_Tall`. Il quitte le petit coffre `Desert/Chest_Small` et ses animations de
  couvercle ; casse, sons et particules restent ceux du bois, comme le bloc de MineColonies.
- Le dessous de l'âtre est gardé, à la demande de l'utilisateur : il se voit d'en dessous, même si le bloc est posé
  sur un support plein. Règle pour toutes les huttes : le dessous d'une large base qui ferme le meuble (dalle de
  l'âtre, corps du fourneau) est gardé ; celui d'un pied ou d'un manche posé au sol est retiré.

## Fermier

Une table de rempotage, en 60 boîtes, avec une texture de 64×256, à sa taille réelle : les pousses de l'étagère haute
montent à 44 (1,37 bloc). Choisie parmi deux pistes (établi de semis, brouette de récolte).

- **Table** en bois peint : quatre pieds, ceux de l'arrière montant en dossier, un plateau en planches, une étagère
  basse et une étagère haute devant un dosseret.
- **Étagère haute** : trois pots en terre cuite remplis de terreau, chacun avec sa jeune pousse (une tige et deux
  feuilles en V).
- **Plateau** : un bac de semis (lattes, terreau) avec deux rangs de trois pousses tournées chacune à sa façon, et un
  arrosoir en cuivre (anse, bec incliné, pomme), dont l'ouverture montre l'eau. Le terreau est mouillé autour de chaque
  pousse, à la demande de l'utilisateur : un halo un peu plus sombre et quelques reflets d'eau.
- **Étagère basse** : un sac de graines en toile de jute ouvert, et une caisse en lattes avec une citrouille à côtes et
  deux carottes.
- **Devant** : une fourche appuyée contre le plateau. Sa tête suit celle de l'établi de ferme de Hytale
  (`Blocks/Benches/Farming`, un quad de 14×16 aux dents peintes) : quatre dents de 2 espacées de 2 sur une barre de
  14, ici en boîtes ; s'y ajoutent une virole et un manche de 2×2 (celui de Hytale fait 3×31×3). Un premier jet à
  trois dents de 1 faisait une plaque.
- **Animation** : les neuf pousses se balancent dans une brise légère (`Farmer.blockyanim`, `CustomModelAnimation` du
  bloc, comme le feu de camp de Hytale) : ±6° de côté et ±3° d'avant en arrière, un cycle de 3 s, chaque pousse
  décalée d'un quart de cycle sur la précédente. `tools/huts/farmer.py` l'écrit (`animation`).
- **Goutte** : une goutte d'eau d'un pixel (nœud `Drop`, pendue à l'arête la plus basse de la pomme inclinée)
  apparaît à 70/180 du cycle, perle en grossissant (de 0,3 à 1) jusqu'à 140, tombe de 3 sur le plateau jusqu'à 152,
  puis disparaît et remonte sans se voir (`shapeVisible`, `shapeStretch`, `position` de la même animation). Hytale
  étire une forme autour de son centre : la perle est remontée de la moitié de ce qui lui manque, pour rester pendue
  à la pomme. Visible 30 % du temps, elle ne projette pas d'ombre cuite (`SEE_THROUGH`, `bake.light_map`). Une
  première version en particules (la goutte de `Goblin_Sludge_Drip` recolorée) était trop grosse à cette échelle :
  l'utilisateur a demandé une animation à nous.
- **Objet** : hitbox `HyColony_Hut` ; il quitte le petit coffre `Kweebec/Chest_Small` et ses animations de couvercle.

## Cuisinier

Un fourneau en fonte, en 68 boîtes, avec une texture de 64×320, à sa taille réelle : le chapeau du tuyau de poêle
monte à 64 (2 blocs), comme l'établi de cuisine de Hytale (63×69×32). Choisi parmi deux pistes (fourneau, billot de
cuisine). Un premier jet de 1,37 bloc était « vraiment petit » à côté de celui de Hytale.

- **Fourneau** en fonte (pinceau `metal`, à peine brossé), un corps d'un bloc de haut sur quatre pieds de 6, sous une
  plaque de cuisson qui déborde.
  En façade : la porte du foyer, un cadre et deux barreaux devant des braises lumineuses (`fullbright`, le pinceau
  `embers` de `tools/common/brushes.py`), la porte du four à poignée en laiton, et une barre en laiton sur deux
  supports, avec un torchon à carreaux rouges et crème plié dessus.
- **Plaque** : à droite, quatre feux en carré, à la demande de l'utilisateur (« comme des feux à gaz ») : chacun un
  socle en fonte claire, un chapeau en laiton et un support de casserole en croix. Un premier jet, un rond peint sur la
  plaque puis un seul brûleur, ne se lisait pas. Sur le feu avant gauche, une marmite de ragoût (carotte et herbes
  dans la sauce) et sa louche en acier.
- **Dosseret** en fonte derrière les feux, sous une étagère chauffe-plats sur deux consoles : une pile de trois
  assiettes et un bol en terre cuite.
- **À gauche** : le tuyau de poêle, son collier et son chapeau, une tresse de six têtes d'ail pendue à une corde
  devant lui, un pot à sel en grès crème à bande bleue (couvercle en bois) et un moulin à poivre en bois sombre à
  bouton de laiton. Deux simples pots d'épices d'un premier jet ont été remplacés (« boff »).
- **Vapeur** : le système `HyColony_Stove_Steam` reprend la vapeur de l'établi de cuisine de Hytale
  (`Workbench_Cooking_On`, spawner `Vapor_Water`) seule, accrochée au nœud vide `Steam` au-dessus de la marmite, à
  l'échelle 0,6. Le fourneau éclaire d'une lueur faible (`Light` `#a75`), plus sombre que l'âtre : son feu est fermé.
- **Objet** : hitbox `HyColony_Hut_Tall` ; il quitte le petit coffre `Tavern/SmallChest` et ses animations de
  couvercle.

## Coursier

Un pupitre de tri postal, en 44 boîtes, avec une texture de 64×384, à sa taille réelle : 2 blocs de haut (64), avec
la hitbox `HyColony_Hut_Tall`. Choisi parmi deux pistes (pupitre de tri, charrette à bras).

- **Bureau** en bois peint sur quatre pieds de 5×5, au ras du plateau, un caisson à deux tiroirs (boutons en laiton)
  sous le plateau et une étagère basse portant deux colis. Un premier jet sur des pieds de 3×3 faisait « des petits
  pieds et un gros truc » : l'utilisateur a demandé un meuble plus costaud.
- **Casier à lettres** de 4×4 cases (côtés de 2, trois étagères, trois cloisons en retrait d'une unité pour que leurs
  faces avant ne se confondent pas avec celles des étagères), avec des liasses de lettres (tranches rayées, enveloppe
  et cachet rouge sur le dessus), un rouleau et un petit colis ; une corniche, et une enseigne en bois à l'enveloppe.
- **Plateau** : une sacoche en cuir (rabat, boucle en laiton, bandoulière), deux colis en papier kraft ficelés, un
  tampon sur son encreur rouge et une lettre cachetée posée de biais.
- **Objet** : il quitte le petit coffre `Lumberjack/SmallChest` et ses animations de couvercle.

## Entrepôt

Une réserve de marchandises, en 31 boîtes, avec une texture de 32×672, à sa taille réelle : 2 blocs de haut (62),
avec la hitbox `HyColony_Hut_Tall`. Choisie parmi deux pistes (pile de stock, étagère à casiers).

- **Rayonnage** en bois sombre au fond : quatre montants, un fond en planches, deux étagères et un plateau. En bas,
  deux sacs de grain en toile et une caisse ; au milieu, trois caisses à étiquette ; en haut, deux rouleaux de tissu
  rouge et bleu et une cruche en terre cuite.
- **Devant** : une grande caisse à étiquette (lattes, étiquette crème écrite) portant le registre d'inventaire ouvert
  (pages écrites, reliure en cuir), et un tonneau à douelles cerclé de deux bandes de fer, bombé par deux boîtes
  croisées, portant une balance en laiton (socle, fût, fléau, deux plateaux pendus, une pile de pièces d'or).
- **Pas tout au cordeau**, à la demande de l'utilisateur : la balance est tournée de 14° et son fléau penche de 6° vers
  le plateau aux pièces (plus bas d'une demi-unité, l'autre plus haut d'autant) ; la grande caisse, les caisses du
  rayonnage, un sac et un rouleau ont chacun un léger biais.
- **Objet** : il quitte le petit coffre `Village/Chest_Small` et ses animations de couvercle.

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

## Presse-papiers

Le presse-papiers de MineColonies (`ItemClipboard`, la liste des requêtes de la colonie), objet tenu, en 10 boîtes,
validé par l'utilisateur sur captures Blockbench :
- planchette de bois rouge-brun, plus claire et plus orangée que le rouge sombre de la texture de MC ;
- liasse de papier crème posée de biais (3°), écrite d'un titre rouge et de sept requêtes, dont les deux premières
  cochées en vert ;
- pince en fer posée sur la planchette, le haut de la liasse pris dessous (plaque, charnière roulée, levier relevé
  de 25°, deux rivets de laiton) ;
- crayon ocre à pointe de graphite, penché de 8° et tenu à droite par une boucle de cuir.

Il remplace la carte de Hytale (`Items/Consumables/Scrolls/Map`) et en garde la racine `R-Attachment` (`isPiece`),
l'animation « Block », le plan y-z et la feuille tournée vers +x. La carte pend de son rouleau le long de +z ; le
presse-papiers monte le long de +y depuis son bord bas, comme l'outil de construction. Le modèle est écrit par un
script (pas dans Blockbench), en JSON indenté. L'objet lit `Items/HyColony/Clipboard.{blockymodel,png}` et son
icône (debout, vue de trois quarts côté feuille).

## Peinture

Retour de l'utilisateur sur les premiers jets : textures trop nettes, sans le travail au crayon et à la brosse douce
que conseille Hytale. Chaque modèle passe donc par trois étapes (`tools/common/paint.py`, `texture`) :

1. ses matières : les pinceaux de `tools/common/brushes.py` (partagés avec les lits et pots de HyVanilla), qui
   peignent une zone entière (bois, métal brossé, cristal taillé, papier, tissu, pierre, minerai, terre cuite ;
   pierre et minerai pour les huttes de mineur et de carrier, demandés par l'utilisateur), et quelques teintes de
   tuiles de Hytale (`tools/huts/materials.py` : cuir, plume…) ;
2. `tools/common/bake.py` cuit la lumière à partir du modèle lui-même, pixel par pixel : occlusion ambiante (rayons
   contre les autres boîtes et, pour un bloc, le sol), ombre portée d'une lumière en haut à l'avant gauche, biseau
   sur les deux anneaux de pixels du bord de chaque zone avec usure (éclats clairs) côté lumière (une arête ne
   s'assombrit que tournée vers le bas, une jointure entre deux boîtes n'a pas de biseau), crasse au pied d'un
   bloc, variation douce de teinte, ombres froides et lumières chaudes ; la direction de la lumière aussi sur les
   faces `flat` ; une occlusion réduite et pas d'ombre portée sur les faces lumineuses ;
3. le débordement des bords dans les marges (`paint.bleed`).

**Une zone d'UV par face** : une zone ne peut porter que la lumière d'une seule face. `bake.light_map` refuse un modèle
dont deux faces partagent un pixel (relecture du 2026-10-02 : l'avant d'un pied montrait la lumière de son arrière).

## Huttes suivantes

Pistes proposées à l'utilisateur, chacune à valider sur des captures Blockbench avant intégration :

| Hutte | Piste |
|---|---|
| Bûcheron | Souche avec une hache plantée, tas de bûches, jeune arbre. Bloc avec le métier (pas encore porté). |

## À vérifier en jeu

- Le constructeur, l'hôtel de ville, la résidence, le fermier, le cuisinier, le coursier et l'entrepôt s'affichent
  avec leur modèle et leur texture, sans face qui scintille ni bord de texture étranger, à la taille des meubles de
  Hytale, et font face au joueur qui les pose, dans les quatre directions.
- Le presse-papiers tenu en main montre sa feuille écrite au joueur, la planchette droite dans la main comme la carte
  de Hytale. Sinon : si la feuille est tournée vers l'extérieur, un demi-tour autour de y sous `R-Attachment` ; s'il
  est couché dans la main, un quart de tour autour de x, pour reprendre l'axe +z de la carte.
- Les icônes montrent la hutte de face.
- Les systèmes `HyColony_Hearth` et `HyColony_Stove_Steam` du pack sont chargés : un id inconnu arrêterait le serveur
  (`ModelParticle` valide `SystemId`, `ParticleSpawnerGroup` le `SpawnerId` ; `validate_pack` les vérifie avant,
  comme le chemin de `CustomModelAnimation`).
- La résidence : des flammes et des étincelles montent des bûches ; la lueur chaude éclaire autour de l'âtre. Les
  flammes (`Fire_Up`, qui monte) et les étincelles (`Fireplace_Sparks_Up`, jusqu'à 1,1 s) ne doivent pas traverser le
  manteau à l'échelle 0,45 ; sinon, essayer le feu fermé de Hytale (`Bench_Furnace`, `Fire_Furnace_On` :
  `Fire_Center` et `Furnace_Sparks`).
- Le fermier : ses pousses se balancent, l'animation continue quand sa fenêtre s'ouvre et se ferme (ses états
  héritent de `CustomModelAnimation`) ; toutes les 3 s, une goutte perle sous la pomme de l'arrosoir, tombe sur le
  plateau et disparaît, à une taille juste.
- Le cuisinier : la vapeur monte de la marmite (`HyColony_Stove_Steam`) ; le foyer luit derrière ses barreaux.
- Utiliser une hutte ouvre toujours sa fenêtre ; aucune erreur d'animation au journal. L'hôtel de ville crée la
  colonie comme avant.
- Le bloc au-dessus d'une hutte posée après ce changement est occupé (cellule de remplissage de la hitbox de 1,4, ou
  de 2 pour la résidence, le cuisinier, le coursier et l'entrepôt) : on ne peut rien y poser ; le joueur bute sur la
  hitbox de 2 blocs jusqu'en haut du modèle, sans buter dans le vide au-dessus des huttes de 1,4.
- **Vieux mondes** :
  - un hôtel de ville posé avant ce changement garde trois cellules de remplissage de l'ancienne hitbox
    `Bench_Architect` ((-1, 0, 0), (-1, 1, 0) et (0, 1, 0) pour la rotation de base ; elles tournent avec `NESW`,
    `FillerBlockUtil.forEachFillerBlock`). Rien ne les revalide au chargement. Casser l'hôtel de ville ne retire que
    la cellule au-dessus de lui (`removeFillerBlocksAt` suit la hitbox actuelle, 1×1,4×1, et ne retire qu'une case du
    même id) : les deux de côté restent. Elles sont invisibles et ne bloquent pas, mais occupent leurs cases et se
    lisent comme une hutte. Pour nettoyer : poser puis casser un bloc dans chacune des deux ;
  - une hutte de constructeur, de résidence, de fermier, de cuisinier, de coursier ou d'entrepôt posée avant ce
    changement n'a pas de cellule au-dessus : un bloc posé là traverse le nouveau modèle (1,28 bloc pour le
    constructeur, 1,37 pour le fermier, près de 2 pour les autres). Reposer la hutte règle le cas.
