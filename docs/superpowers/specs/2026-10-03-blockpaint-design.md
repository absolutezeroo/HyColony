# blockpaint : l'outil unique de peinture des modèles Blockbench

Date : 2026-10-03. Demandé par l'utilisateur : « c'est un outil unique, c'est pas spécifique aux huttes, c'est plus
global à Blockbench » ; plan validé dans la conversation (« oui faisons ça »).

## Avant

La peinture des modèles faits main était dispersée : la boîte à outils dans `tools/common/`, le reflet des cristaux
(`glint.py`), les animations continues (`motion.py`), le tri des faces (`trim.py`) et le peintre de modèles décrits
par un module (dans `generate.py`) dans `tools/huts/`, et un générateur à lancer par ensemble (huttes, armure, ruban,
HyVanilla).

## Après

- **`tools/blockpaint/`, le moteur**, sans rien de propre à un mod : `models.py` (géométrie, placement, faces,
  bornes, dépliage), `brushes.py` (pinceaux), `paint.py` (peinture d'une texture), `bake.py` (lumière cuite),
  `icons.py`, `cull.py` (faces cachées), `trim.py` (dessous rendus, faces cachées et dépliage d'un bloc avant sa
  peinture), `glint.py` (reflet des cristaux), `motion.py` (animations continues), `catalog.py` (peinture d'une liste
  de modèles décrits chacun par un module : texture, animation, icône), `pack.py` et `pack_rules.py` (écriture et
  validation d'un pack). Ses tests : `tools/blockpaint/check.py`.
- **Une commande** : `python tools/blockpaint [huts] [armor] [tape] [vanilla] [--assets chemin]` lance le
  `generate.py` de chaque ensemble nommé, ou de tous.
- **Chaque ensemble ne garde que sa description** :
  - `tools/huts` : la liste des modules des huttes et des objets tenus (`generate.py`, qui la passe à
    `catalog.paint`), un module par modèle (chemin, icône, matière de chaque pièce, dessins propres à ce modèle),
    et ses pinceaux partagés dans `materials.py` ;
  - `tools/armor`, `tools/tape` : leurs modèles construits par le code, peints par le moteur ;
  - `tools/vanilla` : le lit et le pot faits dans Blockbench (le pot recopié par couleur et par plante), peints par
    le moteur sans passer par `catalog` ;
  - `tools/domum` garde son convertisseur de blocs Minecraft, à part, et se sert du moteur pour écrire son pack.
- **Rendu inchangé** : après chaque étape du rangement, tous les fichiers générés (modèles, textures, animations,
  icônes des quatre ensembles) sont identiques à l'octet à ceux d'avant ; les vérifications de tous les outils
  (`tools/*/check.py`) passent.

## Pinceaux de matériaux

Demandés par l'utilisateur (« voilà pour moi ce qu'il manque comme brush »), validés sur des planches dans Blockbench
(un cube par pinceau, peint et éclairé par blockpaint). Chacun peint une zone entière, large et doux, sans bruit
texel par texel là où la matière n'en a pas (la peau) :

- **organique** (`organic.py`) : cuir (grain irrégulier, plis, bords usés), peau (variations très douces, rougeur,
  ombre basse), cheveux (mèches fines qui ondulent, racines sombres, reflet), fourrure (courtes touffes de la racine
  sombre à la pointe claire), os et ivoire (ivoire jauni, pores, bouts sombres, fissure fine qui serpente), corne et
  griffe (stries dans la longueur, base sombre), écailles (rangées chevauchées, bord inférieur arrondi ombré),
  plumes (tige pâle, barbes en biais vers la pointe) ;
- **maçonnerie** (`masonry.py`) : brique (assises décalées, joints de mortier), plâtre (aplats, petits éclats
  montrant le mur), béton (grandes variations, pores), sable (amas, grains clairs et sombres), boue (flaques sombres
  aux bords organiques), pierre moussue (mousse en amas, plus bas sur les côtés et sur le dessus) ;
- **métaux** (`metals.py`) : or, bronze, cuivre, chacun avec sa lumière et son ombre (`worked`), métal peint
  (usé jusqu'au métal par endroits et sur le bord), métal rouillé (rouille en amas, piqûres, qui coule un peu) ;
- **divers** (`misc.py`) : verre (dégradé léger, reflet et liseré clairs), caoutchouc (mat, léger lustre), cire
  (laiteuse, plus claire au milieu), bois brûlé (charbon craquelé en petits pavés), corde (torons en biais).

`brushes.smooth2` (bruit lisse en 2D) sert à leurs amas. Les modèles existants n'en changent pas : leurs pinceaux et
leur rendu restent ceux d'avant. Tests : `tools/blockpaint/check_brushes.py`.

## Hors de ce rangement

- Un plugin Blockbench qui peindrait dans Blockbench même.
- La peinture de plans à fond transparent (les fleurs de Hytale), que `paint` et `bake` ne posent pas encore.
- Un paquet Python au lieu des `sys.path` (`docs/BACKLOG.md`, outils Python).
