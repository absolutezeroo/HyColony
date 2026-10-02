# Faire un modèle Hytale (blocs et props)

Vérifié le 2026-10-02 sur Hytale 0.7.0-pre.5 (`pre-release-0.7.0-pre.5-Assets.zip`, la version épinglée dans
`gradle.properties`), Blockbench 5.2.1 et son plugin MCP 1.10.0 (serveur `hytale` de `.mcp.json`,
`http://localhost:3000/bb-mcp`).

## 1. Sources

- Article officiel : <https://hytale.com/news/2025/12/an-introduction-to-making-models-for-hytale>.
- Mesures faites sur l'archive d'assets (scripts jetables) : les 1 221 `.blockymodel` et les 1 512 textures de
  `Common/Blocks/`, et pour comparaison les 3 013 modèles de toute l'archive (`Common/`).
- Modèles de référence ouverts dans Blockbench : `Decorative_Sets/{Village,Tavern,Kweebec,Lumberjack}/Bed`,
  `Human_Ruins/Pot_Small`, `Royal_Magic/Carpet`, `Jungle/Pot_Secondary`.

## 2. Règles

### Respectées par 100 % des modèles de Hytale (à ne jamais enfreindre)

| Règle | Mesure |
|---|---|
| Formes : boîtes (`box`) et quads (`quad`) seulement | toute l'archive : 24 253 boîtes, 12 126 quads, rien d'autre |
| Côtés des textures multiples de 32 (non carrées permises) | 0 exception sur 1 512 textures de blocs |
| 255 nœuds au plus (`maxNodes` du format) | maximum observé : 83 dans `Blocks/`, 239 dans toute l'archive |
| Densité des props et blocs : 32 px par bloc, soit 1 px par unité (32 unités par bloc) | article ; format `hytale_prop` (`blockSize` 32) |
| Densité des personnages et attachements (outils, armes, nourriture, cosmétiques) : 64 px par unité | article ; format `hytale_character` |

### Tendances (l'article les conseille, Hytale les enfreint parfois)

- **Ombrage `standard`** pour les meubles. Dans `Blocks/`, 70 % des boîtes sont en `standard` et 29 % en `flat`
  (l'inverse dans toute l'archive : personnages, objets et PNJ sont surtout en `flat`).
  Les sets « humains » (Village, Human_Ruins, Tavern, Lumberjack, Ancient, Desert) sont presque tous en `standard`.
  Goblin, Royal_Magic et Frozen_Castle sont presque tous en `flat` : c'est un choix de style. `flat` ignore la
  lumière : à réserver aux tapis, aux plantes et aux quads.
- **Étirement** d'un nœud entre 0,7 et 1,3 par axe ; -1 sert de miroir. 4,4 % des valeurs des boîtes et quads de
  `Blocks/` sortent de cet intervalle.
- **Faces cachées** : Hytale en retire peu (87 % des faces de boîtes de `Blocks/` sont gardées). Nous retirons :
  - le dessous d'un bloc qui exige un support plein (`Support.Down` `Full`, comme le lit). Un bloc qui se pose
    au-dessus du vide (le pot) garde ses dessous ;
  - une face entièrement enfermée dans une autre boîte, ou dans le même plan qu'une face plus grande tournée du même
    côté : deux faces confondues scintillent (z-fighting).
- **Une texture propre au modèle**, peinte, avec une zone d'UV par face. Le lit Kweebec a 28 zones pour 47 faces, le
  lit Tavern 53 pour 86 (les faces identiques partagent leur zone).
- **Peinture** : ombres, occlusion ambiante et reflets peints dans la texture ; pas de bruit, pas d'aplat parfait ; ni
  blanc ni noir purs ; des ombres teintées (désaturées, avec une nuance froide) plutôt que grises.
- **Silhouette** : géométrie minimale, plus de boîtes seulement là où la silhouette y gagne. Les lits de Hytale ont
  de 8 à 19 boîtes, dont de gros montants aux coins.

## 3. Blockbench par MCP

- **Toujours le format `hytale_prop`** pour un bloc. `Codecs.blockymodel.load` ouvre un `.blockymodel` dans un nouveau
  projet, mais le devine en `hytale_character` (UV de 64 par unité, densité fausse). Il faut d'abord créer un projet
  `hytale_prop` (`create_project`), puis y appeler `Codecs.blockymodel.parse(json, chemin)` par `risky_eval`.
- **Un cube créé en `hytale_prop` est en `flat` par défaut** : passer `shading_mode = 'standard'` sur chaque cube
  (`hytale_set_cube_properties`, ou par script).
- Tailles entières (`integer_size`) ; positions libres. Une face Hytale garde des dimensions d'UV égales à celles de
  la face (`set_cube_uv` refuse un rectangle d'une autre taille) : le dépliage choisit seulement la position de chaque
  zone. Le plugin ne range pas les zones : on calcule le rangement soi-même (rangement en étagères, les faces
  identiques sur la même zone).
- Désactiver une face : `face.texture = null` (elle disparaît de l'export).
- Groupes : à l'export, le premier cube d'un groupe devient la forme du nœud du groupe, qui garde **le nom du
  groupe**. Les cubes suivants deviennent ses enfants, renommés `<cube>--C1`, `--C2`… On nomme donc un groupe
  d'après sa pièce principale (`Board`, `Hammer_Handle`), et un outil qui lit les noms retire le suffixe `--C<n>`.
  La rotation d'un groupe devient l'`orientation` de son nœud.
- **Placement des enfants** : la position d'un enfant se compte depuis la position de son parent **plus le décalage
  (`offset`) de la forme du parent**, tourné par le parent (`BlockyModelBoundsParser.accumulateNodeBounds` du serveur :
  `worldPosition = parentPos + parentRot·(position + orientation·offset)`, transmis aux enfants). Exemple :
  `Decorative_Sets/Crude/Chest_Small`, dont la serrure, enfant du couvercle, tombe sur la jointure avant seulement
  ainsi. L'exporteur de Blockbench suit cette règle.
- **Devant d'un bloc** : le côté +z fait face au joueur qui pose le bloc (vu en jeu, et serrure du petit coffre à +z).
  Vu depuis +z, la droite du joueur est +x ; sur un dessus, u va vers sa droite et v vers lui.
- Correspondance des faces à l'export : north → `back`, south → `front` (+z), west → `left`, east → `right` (+x),
  up → `top`, down → `bottom`. Une texture se lit u vers la droite et v vers le bas ; le dessus a u selon +x et v
  selon +z.
- Export : `Codecs.blockymodel.compile()` renvoie le texte du `.blockymodel` (format `prop`, `lod` `auto`). Le
  chemin d'outil est `export_model`.
- `hytale_validate_model` ne vérifie que le nombre de nœuds et les côtés multiples de 32 : la qualité se juge à l'œil.
  Créer une vue hors écran (`create_offscreen_view`), la placer (`set_camera_angle` avec `view`) et comparer la
  capture à un modèle de référence de Hytale ouvert de la même façon. Ne jamais déplacer la vue de l'utilisateur.
- Pour afficher un atlas, régler `uv_width` et `uv_height` de la texture à sa taille en pixels.

## 4. Notre chaîne (HyVanilla)

- Le modèle se construit dans Blockbench, puis s'exporte :
  - directement dans le pack quand il est livré tel quel (`Common/Blocks/HyVanilla/Bed.blockymodel`) ;
  - dans `tools/vanilla/models/` quand le générateur le reprend (le pot, recopié par couleur et par plante, dans un
    dossier que le générateur efface).
  Le générateur lit ces modèles et ne les écrit plus.
- `tools/vanilla/paint.py` peint la texture zone par zone, selon la matière de chaque nœud et le côté de chaque face,
  avec les pinceaux de `tools/vanilla/brushes.py` (bois, tissu, terre cuite, terre, métal, cristal…), dans la
  couleur moyenne de la matière de Hytale qu'ils imitent (laine, planches, argile). `tools/vanilla/bake.py` cuit
  ensuite la lumière du modèle (occlusion ambiante, ombre portée, biseaux, salissure au pied, variation de teinte).
  Chaque face a sa propre zone d'UV. Un bord dont la surface se poursuit dans une autre boîte, un demi-pixel plus
  loin, n'a pas de biseau : jointure (un mur en plusieurs boîtes), coin rentrant, ou pied d'un bloc posé au sol. Les
  arêtes ne s'assombrissent que tournées vers le bas. `light_map` se calcule une fois par modèle, puis `lit` l'applique
  à chaque couleur (lits, pots).
- `pack.draw_model` dessine l'icône à partir du modèle et de sa texture. Il suit les nœuds imbriqués et tournés,
  avec un tampon de profondeur, vu depuis +x +z, donc côté devant. Un outil tenu se dessine plutôt comme les icônes
  d'outils de Hytale (`Icons/ItemsGenerated/Tool_Hammer_*`, pioches, haches) : en diagonale, tête en haut à gauche,
  de trois quarts (`pack.turned`, vue orthographique tournée). `models.placed` applique la règle de placement des
  enfants (§ 3) pour tous les outils : bornes (hitbox du lit, mise à l'échelle des plantes en pot), icônes, cuisson.
- Les huttes de HyColony suivent la même chaîne : `tools/huts/generate.py`, spec
  `docs/superpowers/specs/2026-10-02-hycolony-hut-models-design.md`.
- Pour modifier un modèle : l'ouvrir dans un projet `hytale_prop` (§ 3), le retoucher, le réexporter, puis relancer
  `python tools/vanilla/generate.py`.
