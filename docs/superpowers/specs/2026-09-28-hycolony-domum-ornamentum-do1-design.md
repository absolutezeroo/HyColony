# HyColony : Domum Ornamentum, DO-1 « les blocs »

> Depuis le plan 4 de la séparation en trois mods (spec `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md`), ce code vit dans le mod HyDomum : chemins et noms renommés (`domum/`, `HyDomum_*`, `/hydomum`, `universe/hydomum/`, `HyDomum.CutterCraftSeconds`). Le texte ci-dessous garde les noms de l'époque.

Version 2 du 2026-09-28. Elle remplace la version 1 du même jour, qui générait au build un bloc par combinaison de matériaux : le prototype `/hyornament` a montré en jeu qu'on peut faire **comme DO**, en créant les combinaisons à l'exécution (`docs/research/domum-ornamentum.md` B.11). Faits vérifiés : B.5 à B.11 du même document. Mécanisme de packs : `docs/superpowers/specs/2026-09-27-hycolony-architecture-subplugins-design.md` § 7.

## Objectif et découpage

Porter Domum Ornamentum (DO) **avec son vrai fonctionnement** : un type de bloc par forme, des matériaux choisis au moment de fabriquer, n'importe quelle combinaison permise par les tags DO, sans liste fermée générée d'avance.

| # | Sous-projet | Contenu |
|---|---|---|
| **DO-1** | **Les blocs** (ce document) | Formes DO converties au build, variantes de matériaux créées à l'exécution, onglet créatif, commande de débogage |
| DO-2 | L'établi de l'architecte | Fabrication d'une variante à partir de 1 ou 2 matériaux, quantités DO (A.3) |
| DO-3 | Le lien avec MineColonies | Blocs DO dans les plans, requêtes du constructeur, artisans du cutter |

## Principe : la géométrie au build, les matériaux à l'exécution

- **Au build** (générateur `tools/domum/`, sorties commitées) : tout ce qui ne dépend **pas** des matériaux. Les modèles DO convertis (un par forme et par état), un bloc gabarit par forme avec ses états, sa rotation, ses boîtes, ses règles de connexion et ses interactions, une carte d'icône par forme, l'onglet créatif. Quelques centaines de fichiers, pas des milliers.
- **À l'exécution** (plugin, paquet `ornament`) : tout ce qui dépend des matériaux. Une combinaison demandée crée ses `BlockType` (le bloc et tous ses états), son objet, son icône et, pour deux matériaux, une texture composée. Mécanisme prouvé en jeu (B.11) :
  - `BlockType.getAssetStore().loadAssets` hors du thread du monde, puis `UpdateBlockTypes` `AddOrUpdate` **envoyé deux fois** (le client rate le premier de chaque connexion), les drapeaux de reconstruction portés par le second seulement ;
  - jamais de `RequestCommonAssetsRebuild`.

## Matériaux et tags

- Comme DO, chaque emplacement de matériau d'une forme accepte un **tag** : `timber_frames_frame`, `timber_frames_center`, `shingles_roof`, `shingles_support`, `paper_wall_frame`, `paper_wall_center`, `pillar_materials`, `post_materials`, `trapdoors_materials`, `doors_materials`, `fancy_doors_materials`, `fancy_trapdoors_materials`, `fence_materials`, `fence_gate_materials`, `wall_materials`, `stairs_materials`, `slab_materials`, `all_brick_materials` (sans forme en DO-1 : gardé pour DO-1b) (A.1).
- Les tags vivent dans `hycolony/id-map.json` (section `ornamentTags`) et listent des **ids de blocs Hytale** : `"shingles_roof": ["Rock_Stone_Brick", "Wood_Hardwood_Planks", …]`. Les listes reprennent l'esprit des tags DO (planches et bûches pour un cadre, pierres, briques, argiles, laines pour un centre…), avec les blocs Hytale équivalents.
- **Texture d'un matériau** : lue dans le `BlockType` vanilla au démarrage, jamais codée en dur. On prend la texture des faces latérales du cube (`Textures`). Un id inconnu, ou un bloc qui n'est pas un cube texturé, est journalisé en WARNING et écarté.
- **Matériaux par défaut** de chaque forme (ceux de DO, transposés) : ce sont ceux du gabarit et de l'onglet créatif.

## Géométrie : modèles DO convertis

- Le générateur convertit les modèles DO de chaque forme et de chaque état retenu (la conversion et les corrections de rendu de la version 1 restent valables : orientation de base par famille, faces superposées supprimées, faces presque coplanaires écartées, faces de bout ajoutées, B.9).
- **Disposition de texture** (clé du portage) : DO remplace, dans ses modèles, la texture de chaque composant par celle du matériau choisi (A.2). Le générateur fait correspondre ces textures à une disposition fixe :
  - **forme à 1 matériau** : le modèle lit une texture de bloc **32 × 32**. La variante prend directement la texture du matériau : aucune nouvelle PNG, aucun scintillement ;
  - **forme à 2 matériaux** : le modèle lit une texture **64 × 32**, le composant 1 dans la moitié gauche, le composant 2 dans la moitié droite. La texture de la variante est **les deux textures de matériaux côte à côte**.
  - Les UV DO (sprites 16 px) sont mis à l'échelle de 32 px par bloc.
- **Texture de paire** : elle ne dépend que de la paire de matériaux, pas de la forme. `chêne + pierre` sert aux 10 colombages, aux 5 bardeaux, etc. Elle est générée une fois par paire et publiée comme dans le prototype (PNG sur disque, inscription silencieuse, envoi de ce seul fichier), puis la variante part avec `updateBlockTextures`. **Coût connu : un scintillement à la première utilisation d'une paire** (reconstruction de l'atlas du client), aucun pour une paire déjà connue.

## Familles de DO-1

| Famille DO | Matériaux | Comportement dans Hytale (B.10) |
|---|---|---|
| Colombage (10 motifs) | 2 | Les 4 motifs orientés (côté encadré, porte haute ou basse, côté horizontal) dans les 6 directions (`DoublePipe`), les 6 symétriques jamais tournés, comme DO |
| Bardeaux (5 pentes) | 2 | Droit, coins intérieurs et extérieurs par la règle `Roof` (`Regular` seul) ; à l'envers par `UpDownNESW` |
| Demi-bardeau | 2 | 6 formes selon les voisins, gabarit de connexion à nous (d'après `ShingleSlabBlock.java:163-257`) |
| Pilier (3) | 1 | `base`, `column`, `capital`, `full_pillar` selon les piliers dessus et dessous |
| Poteau (6 types) | 1 | Un bloc par type, orientable |
| Panneau (15 motifs) | 1 | Au sol, au plafond ou contre un mur (`DoublePipe`), collision fine ; écart : pas de rotation au sol ni au plafond |
| Porte, porte ouvragée | 1 / 2 (le 2ᵉ facultatif : absent, il reprend le 1ᵉʳ ; écart, voir plus bas) | Mécanique de porte vanilla (`Use: Door`, états d'ouverture, 2 blocs de haut, portes doubles, charnière par rotation de 180°) |
| Trappe, trappe ouvragée | 1 / 2 | `Use: Door_Horizontal`, s'ouvrent, en haut ou en bas du bloc |
| Mur de papier (2) | 2 | Connexion de vitre, gabarit à nous |
| Clôture, portillon, muret, escalier, dalle | 1 | Modèles DO convertis, connexions et formes vanilla |

**Hors DO-1** :
- **Colombage dynamique** : impossible à rendre (14 voisins, texture par face, B.10 § 4). `Deviation from MC: no dynamic timber frame, placed and requested as framed`, comme le constructeur MC le demande déjà (A.4).
- **Lumières encadrées** : pas de bloc lumineux plein Hytale pour le centre. Reporté.
- **Briques et blocs « extra » DO** (sans matériau) : textures 16 px à redessiner. Reportés.
- **« All brick » et son escalier** : un cube du matériau sous une texture de briques fixe (composite forge). Le moteur de variantes ne compose que des matériaux ; il faudrait lui apprendre cette surcouche. Reportés (DO-1b).

## Variantes à l'exécution

- Une variante = (forme, 1 ou 2 matériaux). Chaque matériau doit appartenir au tag de son emplacement, sinon la demande est refusée.
- **Clé** : `<gabarit>__<Matériau1>[__<Matériau2>]` (par exemple `HyColony_DO_TimberFrame_Framed__Wood_Softwood_Planks__Rock_Stone_Brick`), stable (les tronçons enregistrent les blocs par clé). Elle est sauvegardée sous l'id `<forme>|<Matériau1>[|<Matériau2>]`. Les états suivent la règle vanilla : `*<clé>_State_Definitions_<état>`.
- **Création** (prototype, B.11) : copie du gabarit et de chacun de ses états, avec la texture de la variante, une table d'états construite en code (`VariantStateData`) et une copie des règles de connexion sans les clés résolues du gabarit. L'objet est une copie de l'objet gabarit, avec `blockId` = la variante ; casser n'importe quel état rend cet objet.
- **Cache** : une variante n'est créée qu'une fois, même demandée plusieurs fois pendant sa création.
- **Création groupée** : l'API accepte plusieurs variantes d'un coup (un seul `loadAssets`, donc un seul scintillement). DO-2 s'en servira.
- **Persistance** : les variantes créées sont notées (JSON versionné, `schemaVersion`) et recréées au démarrage pendant `LoadAssetEvent` (priorité 64), avant le chargement des tronçons, avec leurs textures de paire et leurs icônes.
- **Thread** : jamais `loadAssets` sur un thread de monde (verrou `ASSET_LOCK`, `plugin-b-api.md` § 17).

## Icônes

- Hytale n'a pas de rendu d'icône côté serveur, et un objet sans icône s'affiche « ? » (B.11).
- **Carte d'icône** : pour chaque forme, le générateur dessine une fois, au build, l'icône du modèle vu avec la caméra vanilla (`IconProperties` par défaut). Au lieu de couleurs, chaque pixel note **où lire dans la disposition de texture** (u, v) et **son ombrage**. La carte est une ressource du serveur, jamais envoyée au client.
- **À l'exécution**, l'icône d'une variante se remplit en lisant les textures de ses matériaux à travers la carte : pas de rendu 3D à l'exécution, et chaque forme a sa vraie icône.
- Publication comme dans le prototype (inscription silencieuse, puis `UpdateItems` avec `updateIcons`). Coût connu : un court gel (quelques millisecondes) à la création d'une variante.

## Onglet créatif

- Un onglet « Domum Ornamentum » qui contient **tous les gabarits dans une seule liste**, comme l'onglet unique de DO dans Minecraft (choix de l'utilisateur, 2026-09-28 : une sous-catégorie par famille ne servait à rien). La bibliothèque créative de Hytale affiche les enfants d'un onglet : la liste est donc son unique enfant, `DomumOrnamentum.All`. Il contient **les gabarits** : chaque forme avec ses matériaux par défaut. Les matériaux créés à l'exécution n'y apparaissent pas.
- Icônes d'onglet en paire `X.png` / `XActive.png` sous `Icons/ItemCategories`, vérifiées au build (une icône manquante arrête le serveur).
- Libellés en en-US et fr-FR.

## Organisation du code

- **Générateur** `tools/domum/` (Python, lancé à la main, sorties commitées) : sources DO au commit épinglé, assemblage des états, nettoyage des faces, conversion vers la disposition 32 / 64 × 32, gabarits, cartes d'icône, onglet, plus un manifeste des formes (id, gabarit, groupe du cutter, tags des emplacements, second emplacement facultatif, quantité DO pour DO-2 ; les matériaux par défaut sont dans les gabarits).
- **Pack d'assets** : le sous-plugin `DomumOrnamentum` (modèles, gabarits, onglet), **désactivé par défaut** jusqu'au test en jeu.
- **Cœur** (`core`, paquet `ornament`, Java pur, testé) : formes et emplacements lus du manifeste, tags, validation d'une demande, clé de variante, format de persistance et sa migration.
- **Plugin** (paquet `ornament`, issu du prototype) : catalogue des matériaux (textures lues dans les `BlockType`), fabrique des variantes et de leurs états, textures de paire, icônes, publication des assets, synchronisation, restauration au démarrage, commande `/hyornament` (opérateurs). Il n'est actif que si le pack est activé.
- Le prototype actuel (`OrnamentShape`, `OrnamentMaterial` codés en dur, découpe « à l'œil » du bardeau, dessin d'icône du colombage) est remplacé par ce système.

## Limites connues et écarts

- Un scintillement à la première utilisation d'une paire de matériaux ; un court gel à la création d'une variante (icône).
- **Échelle des textures** : une face lit un texel par unité depuis l'origine de son uv. Minecraft, lui, étire le rectangle uv sur la face ; ici, un uv plus large ou plus étroit que la face montre le matériau à son échelle naturelle. `Deviation from MC: uv span not stretched over the face`. Pour un matériau uniforme, seul le motif est décalé.
- **Trappe** : posée au sol (sans tangage, comme un escalier), c'est la moitié basse de DO, charnière en +Z, que l'animation vanilla ouvre vers le haut. Retournée (`UpDownNESW`, tangage de 180°), c'est la moitié haute, charnière en -Z : la trappe Crude vanilla. L'animation vanilla ne tourne que dans un sens : la charnière d'une trappe accrochée en haut est donc du côté opposé à celle d'une trappe au sol, alors que Minecraft garde le même côté. `Deviation from MC: hanging trapdoor hinge on the opposite side`. À confirmer en jeu (pose au sol, au plafond, sur la moitié haute et basse d'une face).
- **Mur de papier** : il ne se relie qu'aux autres murs de papier. Une vitre Minecraft se relie aussi à toute face pleine, ce qu'un gabarit de connexion Hytale ne sait pas exprimer. `Deviation from MC: paper walls join paper walls only`.
- **Clôture, portillon, muret** : ils se relient par le gabarit vanilla (`WallConnectedBlockTemplate`, tag `FenceConnection`), donc aux clôtures, murets et portillons, vanilla compris, jamais à une face pleine comme dans Minecraft. Seuls, ils gardent la forme vanilla droite (bras est et ouest) au lieu du poteau seul de Minecraft. Les murets n'ont ni côté haut (`tall`) ni poteau levé par le bloc du dessus. Un portillon voisin d'un muret n'est pas abaissé (`in_wall`). `Deviation from MC: fences and walls join and stand by the vanilla template`.
- **Nom des objets** : un objet porte le nom de sa forme (« Colombage encadré »). Hytale ne passe pas de paramètre à un nom d'objet ; afficher les matériaux dans le nom demanderait une clé de traduction par combinaison. `Deviation from MC: materials are not shown in the item name`. À réexaminer si un autre moyen apparaît.
- **Variante et matériau** : une variante garde les sons, les particules et l'outil de récolte du matériau par défaut de sa forme (ceux du gabarit) ; seule la texture change. `Deviation from MC: a variant sounds and breaks like its template`.
- **Dalle variante** : l'interaction qui fusionne deux dalles est un asset contenu de l'objet gabarit, qui vise le bloc du gabarit ; la variante ne la reprend pas. Une dalle variante se pose donc sans fusionner, ni avec une autre variante ni avec le gabarit (une interaction par variante serait à créer à l'exécution).
- **Clôture variante** : le motif `Gate` de la clôture nomme le portillon gabarit ; une clôture variante ne se relie qu'à lui (et aux clôtures et murets, par leur tag).
- **Icônes** : la carte d'icône suit la caméra des icônes vanilla, ajustée sur leurs silhouettes (cube, demi-bloc, escalier) ; un modèle qui sort du cadre (trappe posée au sol, pente raide) est recentré ou réduit. Le mur de papier prend l'icône de sa forme droite, comme l'objet DO.
- **Matériaux par défaut** : ceux de DO (troisième argument de chaque `SimpleRetexturableComponent`), transposés : chêne → `Wood_Hardwood_Planks`, sapin → `Wood_Softwood_Planks`, bois écorcé → `Wood_Stripped_Deco`, terre cuite blanche → `Soil_Clay_Smooth_White`, brique DO → `Soil_Clay_Brick`, briques de pierre → `Rock_Stone_Brick`, cuivre → `Metal_Copper`, quartz → `Rock_Quartzite`, andésite polie (tout-brique, DO-1b) → `Rock_Stone_Brick` (pas d'andésite en 0.6.8).
- **Verre** : Hytale 0.6.8 n'a pas de bloc de verre ; le centre du mur de papier (verre dans DO) prend la laine blanche par défaut, et aucun tag n'a de verre. `Deviation from MC: no glass material`.
- **Second matériau facultatif absent** : DO laisse la texture de remplacement du composant ; ici le premier matériau est repris, car une variante lit une tuile par emplacement. `Deviation from MC: an absent optional material repeats the first`.
- **Porte recentrée** : la porte fermée est au milieu du bloc, pas contre son bord comme dans DO, parce que l'animation et les boîtes de collision des portes vanilla sont centrées. `Deviation from MC: door leaf centred in its block`.
- **Bardeaux** : deux bardeaux font un coin quelle que soit leur pente, comme DO (`DOStairBlock.isStairs`). Leur collision est celle de DO (`ShingleBlock.getShape`) : escalier d'un bloc (coins compris) pour les pentes normale, plate et raide, demi-dalle pour la plate basse, demi-bloc vers l'avant pour la raide basse.
- **Panneau et poteau** : DO tourne aussi un panneau posé au sol ou au plafond vers le joueur, et un poteau debout sur son axe ; aucune rotation Hytale n'offre à la fois ces rotations et la pose contre un mur, gardée. Les zones de clic de DO (0,2 / 0,8) qui choisissent la moitié ne sont pas reproduites. `Deviation from MC: floor panels and standing posts do not turn`.
- **Tags des murets et escaliers** : DO les liste bloc par bloc sans `#domum_ornamentum:default` ; ici ils partent de « default », donc prennent aussi la pierre, la pierre taillée et les briques de pierre. `Deviation from MC: wall and stairs tags include plain stones`.
- Rafraîchir la liste des assets d'un joueur qui se connecte passe par réflexion (champ privé de `CommonAssetModule`, version épinglée 0.6.8), à revérifier à chaque montée de version.
- Colombage dynamique, lumières encadrées, briques DO : voir « Hors DO-1 ».

## Contrôles et tests

- **Générateur** : `validate_pack` et `python tools/domum/check.py` (orientation, uv au pixel près, faces superposées, bouts de pilier, états de porte, chaque carte d'icône lit dans sa disposition).
- **Cœur** : tests JUnit d'abord (TDD) pour les tags, la validation, les clés, la persistance et sa migration.
- `./gradlew build` vert ; relectures `hycolony-reviewer` et `mc-fidelity-checker`.
- **En jeu** (`docs/TESTING.md`) : activation du pack sans SEVERE ; onglet et libellés ; chaque famille posée dans les 4 directions, avec ses formes selon les voisins ; portes et trappes qui s'ouvrent ; variantes créées par `/hyornament` avec la bonne texture, la bonne icône et l'objet rendu à la casse ; un seul scintillement par nouvelle paire ; tout tient après un redémarrage et une reconnexion.
