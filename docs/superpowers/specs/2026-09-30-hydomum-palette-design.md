# HyDomum : variantes à deux matériaux sans scintillement (la planche)

Sous-projet de Domum Ornamentum, dans le mod HyDomum. Il change la façon dont DO-1 (spec `2026-09-28-hycolony-domum-ornamentum-do1-design.md`) habille une variante à deux matériaux. Plus rien d'autre ne change : formes, tags, établi, sauvegarde, recettes.

Source : `docs/research/client-block-atlas.md` (analyse du client, puis contournement vérifié en jeu le 2026-09-30 avec `/hydomum palette`).

## Problème

- Aujourd'hui, une variante à deux matériaux lit une **texture de paire** de 64×32 px (1ᵉʳ matériau à gauche, 2ᵉ à droite), créée à la première demande de la paire.
- Pour une texture neuve, le client reconstruit tout son atlas de blocs, puis abandonne le maillage de tous les tronçons affichés : l'écran scintille une fois par paire neuve.
- **Vérifié en jeu (2026-09-30)** : un bloc qui ne lit que des textures déjà dans l'atlas s'affiche sans drapeau de reconstruction et sans scintillement, même avec un **modèle** envoyé en cours de partie.

## Décisions

- **Une planche au démarrage.** HyDomum dessine une seule texture, `Blocks/HyDomum/Palette.png`, avec la face de chaque matériau DO dans une case de 32 px (grille carrée, matériaux triés par id), soit au plus 296 matériaux étiquetés et environ 576×544 px aujourd'hui. Un bloc caché `HyDomum_Palette` la lit, donc chaque client la met dans son atlas dès la connexion.
- **Un modèle par paire et par modèle de gabarit.** Chaque modèle d'un gabarit à deux matériaux (un par état : un bardeau en a 5, un mur de papier 6) est remappé pour lire les cases des deux matériaux dans la planche. On l'inscrit comme asset commun sous `Blocks/HyDomum/Variants/<modèle du gabarit>__<m1>__<m2>.blockymodel`. Un même modèle sert à toutes les variantes qui partagent ce modèle et cette paire.
- **Plus aucune texture de bloc créée en cours de partie.** Les variantes à deux matériaux lisent toutes la planche. Les variantes à un matériau lisent, comme aujourd'hui, la texture du matériau. Les gabarits gardent leurs 5 textures de paire livrées dans le pack (`tools/domum/pairs.py`), présentes dès la connexion.
- **Deuxième matériau optionnel** (portes ouvragées) : rien de spécial. `VariantRequests` répète déjà le 1ᵉʳ matériau dans la clé, et les deux moitiés lisent alors la même case.
- **Icônes inchangées.** L'icône est toujours peinte par la carte d'icône de la forme (`IconMap.sample`) à partir de la disposition 64×32 de la paire, mais cette image est dessinée **en mémoire**, sans être inscrite comme texture. Elle part comme aujourd'hui, avec `UpdateItems` et `updateIcons`, qui ne scintille pas (vérifié le 2026-09-28).
- **Sauvegarde inchangée.** `variants.json` ne garde que les clés. Au démarrage, les variantes sauvegardées sont recréées sur la planche : même aspect, rien à migrer. Les PNG de paire déjà écrits dans `universe/hydomum/assets/` ne sont plus lus.
- **Fin du test.** `/hydomum palette`, `PaletteGive` et `PaletteVariants` disparaissent, fondus dans le moteur de variantes.

## Remappage d'un modèle

- **Règle d'une face**, vérifiée sur les modèles vanilla (`tools/blockpaint/models.py` `face_rects`, reprise par `tools/domum/convert.py`) :
  - l'offset est un pivot ;
  - la taille lue par la face est (x, y) pour front, back et quad, (z, y) pour left et right, (x, z) pour top et bottom ;
  - le miroir inverse la largeur ou la hauteur, puis l'angle (0, 90, 180, 270) fait tourner le rectangle autour du pivot ;
  - le générateur garantit que ce rectangle reste dans une seule moitié (`_snap`).
- **Remappage** : on calcule le rectangle de la face, sa moitié est `floor(minX / 32)`, puis on **translate** l'offset de `(case.x - 32 × moitié, case.y)`. Miroir, angle, taille et tout le reste sont gardés.
- Le remappage du test (`x % 32`) est faux pour une face retournée ou tournée dont l'offset est à droite de son rectangle. La relecture du test l'a mesuré : 44 des 56 modèles à deux matériaux ont de telles faces, dont `TimberFrame_Framed` (faces `E6 top` et `E12 bottom`, offset (32, 32), angle 180°, qui lisent les texels 30 à 32 du cadre). Le test en jeu a validé l'absence de scintillement, pas le rendu au texel près.
- Une face dont le rectangle chevauche les deux moitiés, ou sort de 64×32, est une erreur du générateur. La variante échoue avec un message clair. `tools/domum/check_convert.py` (`every_do_face_reads_inside_one_tile`) ne le vérifie que pour les états par défaut ; le test du cœur ci-dessous couvre tous les modèles.

## Création d'une variante

L'ordre compte : un modèle doit arriver chez le client **avant** le bloc qui le nomme, puisque plus aucun drapeau ne demande de relecture.

1. **Construction** (hors du thread du monde, comme aujourd'hui) :
   - blocs de la variante (principal et états) : copie du gabarit, texture = planche, modèle = modèle remappé de l'état ;
   - objet et icône ;
   - modèles et icônes inscrits sans être envoyés.
2. **Envoi aux joueurs connectés des modèles neufs du lot** (`sendAssets(…, false)`).
3. **Inscription des blocs** : `loadAssets` sans drapeau, puis le second `UpdateBlockTypes`, sans drapeau lui non plus (un client manque le premier de sa connexion).
4. **Inscription des objets**, puis, si une icône est neuve, envoi des icônes et de `UpdateItems` avec `updateIcons`.

`BlockTypeSynchronizer` n'envoie donc plus jamais `updateBlockTextures`.

## En jeu

- **Aucun scintillement** à la création d'une paire neuve : `/hydomum give`, l'établi (aperçu et fabrication) et les variantes demandées par la colonie plus tard.
- **Rendu identique** à aujourd'hui pour toutes les formes à deux matériaux :
  - colombages, bardeaux et demi-bardeaux, avec leurs coins ;
  - portes et trappes ouvragées, ouvertes et fermées ;
  - murs de papier, avec leurs connexions.
- **Icônes justes**, comme aujourd'hui.
- **Établi** : l'aperçu attend toujours que les emplacements soient stables (500 ms depuis le 2026-09-30, contre 1 s avant, `CutterPreviewVariants`), pour ne pas créer une variante par matériau essayé. Le scintillement qui suivait disparaît.

## Découpage du code

- **Cœur (`domum/core`, `dev.hydomum.core.palette`), tests d'abord :**
  - `PaletteLayout`, repris du test ;
  - `PaletteModel.remap`, corrigé : rectangle de chaque face, translation vers la case, refus d'une face à cheval sur les deux moitiés.
- **Plugin (`domum/plugin`) :**
  - `runtime/VariantPalette` (nouveau) : dessine et inscrit la planche et le bloc caché au démarrage, donne le modèle remappé d'un modèle de gabarit pour une paire (inscrit une fois, avec son asset à envoyer) ;
  - `DynamicBlockTypeFactory.create` : reçoit la texture et, pour chaque bloc de la famille, le chemin de son modèle (identité pour une variante à un matériau) ;
  - `VariantBuilder` : texture = planche et modèles remappés pour deux matériaux ; icône peinte depuis la paire dessinée en mémoire ; renvoie les modèles neufs du lot ;
  - `VariantAssets` : plus de texture de paire ni de liste de textures à envoyer, reste l'icône ; garde `generated` pour la planche et les modèles ;
  - `BlockTypeSynchronizer` : envoie les modèles neufs avant les blocs ; plus de drapeau de textures ;
  - `OrnamentVariantRegistry` : démarre la planche avant de restaurer les variantes sauvegardées, suit le nouvel ordre ;
  - suppression de `/hydomum palette`, `PaletteGive` et `PaletteVariants`.
- Taille : aucun fichier au-delà de 300 lignes, aucun paquet au-delà de 15 fichiers (`runtime` : 9 fichiers).

## Robustesse

- **Planche impossible à dessiner** (texture illisible, AWT absent) : journalisé en SEVERE. Les variantes à deux matériaux échouent avec un message, celles à un matériau marchent toujours.
- **Matériau absent de la planche** (il ne peut pas l'être : elle est faite depuis le même catalogue) : la variante échoue, journalisé.
- **Taille de la planche** : un mod qui ajouterait beaucoup de matériaux agrandirait la planche. À 1 024 matériaux, elle ferait 1 024×1 024 px ; la taille d'une page d'atlas du client n'est pas connue. Au-delà, il faudra plusieurs planches, avec une paire dans la même planche. Hors portée : on journalise un avertissement au-delà de 2 048 px de côté.
- **Création en double** : le futur d'une création est mis dans le cache **avant** d'être lancé, puis complété de l'extérieur, comme dans `OrnamentVariantRegistry` ; un échec immédiat retire bien la clé (le test avait une course qui la gardait en cache).
- **Empreinte de la planche** : la mise en page (`TILE_PX`, nombre de colonnes) entre dans l'empreinte du PNG gardé sur disque, pour qu'un changement de mise en page ne réutilise pas un ancien fichier.
- **Modèle mal formé** (offset sans `x`, `nodes` qui n'est pas un tableau) : `PaletteModel.remap` le refuse par `IllegalArgumentException`, jamais une autre exception.
- **Nombre d'assets** : un modèle par (modèle de gabarit, paire), soit quelques ko chacun. Ce sont des assets que chaque joueur télécharge à la connexion, sans effet sur l'atlas.

## Tests

- **Cœur** :
  - `PaletteLayoutTest` (repris du test) ;
  - `PaletteModelTest` : face simple de chaque moitié ; pivot à 32 d'une face du 1ᵉʳ matériau tournée de 180° ou en miroir ; pivot à 64 d'une face du 2ᵉ en miroir ; faces tournées de 90° et 270° avec leur taille ; face `quad` ; face à cheval refusée ; modèle mal formé refusé ; champs gardés.
  - Un test lit **tous** les `.blockymodel` de `domum/plugin/src/main/resources/Common/Blocks/HyDomum/` des formes à deux matériaux et vérifie que le remappage laisse chaque face entière dans la case de son matériau. C'est ce qui protège contre un cas de face oublié.
- **En jeu** (`docs/TESTING.md`) :
  - l'étape 209 est remplacée ;
  - les étapes DO-1 et DO-2a qui attendent « un seul scintillement » (143, 159) attendent désormais **aucun** ;
  - nouvelle étape : bardeaux avec coins, porte et trappe ouvragées ouvertes et fermées, mur de papier connecté, chacun avec une paire neuve, sans scintillement et avec le rendu juste ;
  - redémarrage : mêmes rendus ;
  - second joueur connecté après la création : mêmes rendus.

## Hors portée

- La quincaillerie en fer des portes ouvragées « creeper » : DO la laisse en fer (texture qui n'est pas un composant, `FancyDoorBlock`), le générateur la met sur le 2ᵉ matériau (`tools/domum/convert.py` `component_index`, écart documenté). La planche rendrait une case « fer » fixe possible : à faire à part.
- Les formes à un matériau, qui ne scintillent déjà pas.
- Les gabarits et leurs textures de paire du pack.
