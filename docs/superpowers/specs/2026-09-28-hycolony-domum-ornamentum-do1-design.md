# HyColony : Domum Ornamentum, DO-1 « les blocs »

Validé avec l'utilisateur le 2026-09-28. Faits vérifiés : `docs/research/domum-ornamentum.md`, en particulier B.9 (défauts du banc d'essai et leurs causes) et B.10 (mécanismes Hytale pour chaque comportement DO). Mécanisme de packs : `docs/superpowers/specs/2026-09-27-hycolony-architecture-subplugins-design.md` § 7.

## Objectif et découpage

Porter Domum Ornamentum (DO) **avec son vrai fonctionnement**, et non seulement ses formes. Le banc d'essai `bd451ec` a montré que convertir chaque fichier de modèle en un bloc figé ne suffit pas : un bloc DO est assemblé par son **blockstate** (pièces multipart, forme selon les voisins, rotation, moitié haute ou basse, un matériau par composant).

Le portage est découpé en trois sous-projets, chacun avec sa spec, son plan et son test en jeu :

| # | Sous-projet | Contenu |
|---|---|---|
| **DO-1** | **Les blocs** (ce document) | Toutes les familles avec leur comportement, un seul jeu de matériaux, onglet créatif |
| DO-2 | Le cutter | Établi de l'architecte, recettes et quantités DO, liste des matériaux par emplacement, génération de toutes les combinaisons |
| DO-3 | Le lien avec MineColonies | Blocs DO dans les plans, requêtes du constructeur, artisans qui fabriquent au cutter |

DO-1 remplace le banc d'essai. Tout se fait **en assets**, générés d'avance : le cœur Java n'est pas touché.

## Matériaux

- Un seul jeu : **Darkwood** pour le 1ᵉʳ composant, **Lightwood** pour le 2ᵉ. Les textures sont les planches des objets `Wood_Darkwood_*` et `Wood_Lightwood_*` du zip vanilla.
- L'emplacement se déduit du **composant DO**, comme dans DO : l'id d'un composant est la texture « placeholder » du modèle source (A.2). L'ordre des composants est celui de la classe du bloc (`SimpleRetexturableComponent`, tableau A.1).
- Une famille à un seul composant sort en Darkwood.

## Familles

| Famille DO | Blocs | Comportement dans Hytale |
|---|---|---|
| Colombage (`TimberFrameType`) | 10 motifs | Blocs orientables (`VariantRotation`) |
| Colombage dynamique | 0 | **Non porté** : il devient le colombage `framed`. `Deviation from MC: no dynamic timber frame, placed and requested as framed` : c'est déjà ainsi que le constructeur MC le demande (`DoBlockPlacementHandler`, A.4). Rendu impossible en 0.6.8 (14 voisins, texture par face, B.10) |
| Bardeaux | 5 pentes | Droit, coin intérieur, coin extérieur par la règle de connexion `Roof` des toits vanilla ; à l'envers par `UpDownNESW` |
| Demi-bardeau | 1 | Les 6 formes (`top`, `one_way`, `two_way`, `three_way`, `four_way`, `curved`) selon les voisins, par un gabarit de connexion écrit d'après `ShingleSlabBlock.java:163-257` |
| Pilier | 3 | `base`, `column`, `capital`, `full_pillar` selon les piliers au-dessus et en dessous, par `PillarConnectedBlockTemplate` complété de `Full`. Faces de bout fermées |
| Poteau | 6 types | Un bloc par type, orientable |
| Panneau | 15 motifs | Fixe, collision fine |
| Porte, porte ouvragée | 4 + 2 | Mécanique de porte vanilla : `Use: Door`, états `OpenDoorIn/Out`, `CloseDoorIn/Out`, `DoorBlocked`, animations `Door_*`, 2 blocs de haut, portes doubles par `DoorConnectedBlockTemplate`, charnière droite par rotation de 180° |
| Trappe, trappe ouvragée | 15 + 2 | `Use: Door_Horizontal`, s'ouvrent ; posées en haut ou en bas du bloc (`UpDownNESW` ou un bloc `_Bottom`, à trancher au plan d'après B.10) |
| Mur de papier, mur de papier carrelé | 2 | Connexion de vitre : poteau seul, bouts, droit, coins, par un gabarit propre (store `Item/CustomConnectedBlockTemplates`) |
| Clôture, portillon, muret, escalier, dalle (« compat vanilla ») | 5 | DO les dessine avec la géométrie vanilla de MC : on prend les **modèles Hytale vanilla** équivalents, retexturés, avec leurs connexions et formes vanilla |
| « All brick » et son escalier | 4 | Blocs pleins ; l'escalier suit l'escalier vanilla |

### Hors DO-1

- **Lumières encadrées** : Hytale n'a pas de bloc lumineux plein comme la glowstone pour le centre, qui est obligatoirement une lampe dans DO (`framed_light_center`). Reporté (BACKLOG).
- **Briques DO et blocs « extra »** : de simples cubes, mais leurs textures DO (16 px) seraient à redessiner en 32 px. Reporté (BACKLOG).
- Tonneaux et tapis flottants DO, cutter et recettes (DO-2), lien avec MineColonies (DO-3).

## Qualité de rendu, pour tous les blocs

Chaque point corrige un défaut du banc (B.9) :

- **Orientation** : chaque famille DO a son orientation de base (bardeaux et portes vers l'est, trappes et panneaux vers le sud). La conversion applique la rotation de la variante `facing=north` de la famille, pour ramener le modèle à l'orientation de base de Hytale (côté haut en -Z, mesuré sur `Stairs.blockymodel` et `Slope_Hay.blockymodel`).
- **Plus de scintillement** : on supprime une face cachée par une face superposée dans le même plan, et on écarte d'au moins 0,1 unité deux faces presque dans le même plan (les décalages de 0,01 px de DO). Les bardeaux débordent du bloc (z de -4 à 20 px) : un débordement recouvert par un voisin ne doit pas scintiller (à vérifier en jeu). `Opacity` suit le vanilla de la forme équivalente (toits et piliers vanilla : `Solid`).
- **Faces de bout** : on ajoute les faces que DO omet parce qu'un voisin les cache, dès que la forme peut se retrouver sans ce voisin (bouts de pilier). Pour `blockpillar`, un seul couvercle, pas un par lame.
- **Collisions** : une hitbox qui suit la forme (panneaux, trappes, poteaux, murs de papier, portes). Bardeaux en marches, comme les toits vanilla.
- **Icônes** : des PNG 64×64, obligatoires (`ICON_ITEM`, B.10 § 8). Elles sont rendues depuis le **modèle final orienté**, avec la caméra des icônes vanilla (`IconProperties` par défaut : `Scale 0.58823`, `Rotation 22.5/45/22.5`), un tri des faces correct pour les formes imbriquées et un cadrage selon la taille réelle du modèle.

## Onglet créatif

- Un onglet « Domum Ornamentum » dans le store `Item/Category/CreativeLibrary`, avec une sous-catégorie par famille, dans l'ordre des groupes du cutter DO (`avanilla`, `btimberframe`, `cshingle`, `ddoor`, `etrapdoor`, `fpanel`, `gpillar`, `hpaperwall`, `kpost`…, A.3).
- Les icônes de l'onglet et des sous-catégories vont sous `Icons/ItemCategories`, en paire `X.png` / `XActive.png` comme le vanilla. Une icône de catégorie manquante arrête le serveur : le générateur et le build la vérifient.
- Plus aucun objet DO dans les catégories vanilla.
- Libellés en en-US et fr-FR.

## Générateur

`tools/domum/`, en Python, lancé à la main, sorties commitées (comme `tools/decorations`). Il est **piloté par les blockstates DO**, et non plus par les fichiers de modèles.

| Module | Rôle |
|---|---|
| `source` | Télécharge au commit épinglé (`82729d6`) les blockstates, les modèles et les composants DO ; cache dans `build/domum-cache/` |
| `assemble` | Pour chaque état retenu d'un blockstate : assemble les pièces multipart, applique la rotation de base de la famille |
| `faces` | Nettoie la géométrie : faces superposées, faces presque dans le même plan, faces de bout |
| `convert` | Produit le `.blockymodel` et son atlas (code du banc, jugé correct à la relecture : unités, pivots, correspondance des faces, uv inversés et tournés) |
| `blocks` | Écrit par famille le `BlockType` : états, règle de connexion, `VariantRotation`, hitbox, interactions |
| `icons` | Rendu des icônes (voir ci-dessus) |
| `tabs` | Onglet, sous-catégories, leurs icônes, et les traductions en-US et fr-FR |

Les helpers partagés restent dans `tools/decorations/pack.py` et `models.py` (sa docstring est mise à jour : ils servent aux deux générateurs).

Le sous-plugin `plugin/src/subplugins/DomumOrnamentum/` remplace le contenu du banc et reste **désactivé par défaut** (`EnabledByDefault: false`) jusqu'au test en jeu. Pas d'entrée dans `id-map.json` (DO-3).

## Contrôles et tests

Un asset invalide dans un pack zip arrête le serveur : les contrôles hors ligne sont obligatoires.

- **Au bout de chaque génération** : `validate_pack`, puis :
  - chaque état référencé existe ;
  - chaque gabarit de connexion pointe vers des blocs existants ;
  - chaque icône, catégorie, hitbox et clé de traduction existe ;
  - chaque face lit dans son atlas.
- **`python tools/domum/check.py`**, à assertions :
  - au pixel près : miroir d'un uv inversé, rotation de 90° ;
  - orientation : le côté haut d'un bardeau en -Z ;
  - plus aucune paire de faces superposées dans aucun modèle ;
  - bouts de pilier fermés ;
  - une porte a ses états complets et un nœud `Door` à la charnière.
- `./gradlew build` vert (`checkSubpluginAssets` couvre le pack).
- Relectures : `hycolony-reviewer` et `mc-fidelity-checker`.
- Le cœur Java n'est pas touché : pas de test Java.

## À vérifier en jeu

Une étape est ajoutée à `docs/TESTING.md` :

- activation par `"SubPlugins": {"DomumOrnamentum": true}`, démarrage sans SEVERE ;
- l'onglet « Domum Ornamentum » et ses sous-catégories s'affichent, avec leurs libellés (5ᵉ onglet : jamais vu en vanilla) ;
- chaque famille se pose dans le bon sens, dans les 4 directions, et les formes à l'envers ;
- portes : ouverture, porte double, charnière, 2 blocs de haut, hitbox sur le battant ; trappes : ouverture, en haut et en bas ;
- bardeaux : coins intérieurs et extérieurs, hauteur de marche franchissable ;
- demi-bardeau, pilier, murs de papier, clôtures, murets : les formes suivent les voisins ;
- aucun scintillement, aucun trou ;
- icônes vues de face, lisibles.
