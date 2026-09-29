# HyVanilla : lits 1×2 de Minecraft

Validé avec l'utilisateur le 2026-09-29 : cadre en softwood, recoloration gardée. Mod : `2026-09-29-hyvanilla-design.md`.

## Objectif

Ajouter le lit de Minecraft à HyVanilla : 1 bloc de large, 2 de long, 9/16 de haut. C'est un **ajout demandé** (CLAUDE.md § 6) : un bloc vanilla de Minecraft, pas de MineColonies. Les lits vanilla de Hytale sont des meubles d'environ 2×3 blocs.

## Faits vérifiés (Hytale 0.7.0-pre.4)

- **Un lit est un seul bloc « modèle »** (`Furniture_Village_Bed.json`) :
  - `HitboxType` fixe les cellules qu'il occupe. Le jeu génère les cellules de remplissage, refuse la pose si l'une d'elles est occupée (`testPlaceBlock`) et casse le tout d'un coup ;
  - `Beds: [{Offset, Yaw}]` donne le point de couchage ;
  - `Interactions.Use: "Block_Bed"` est la racine partagée, avec le tag `Type=Bed` ;
  - `Interactions.Primary: "Check_Can_Break_Respawn"` ;
  - `BlockEntity.Components.RespawnBlock`, `VariantRotation: "NESW"`, `Support.Down: Full`, `MaxStack: 1`.
- **Offset** : il se compte depuis le centre du bloc d'origine, `(0,5 ; 0,5 ; 0,5)`, et tourne avec lui (`BlockMountPoint.java:21, 54-61`).
- **Position du modèle** : l'origine du modèle est le centre bas de la cellule d'origine. Les lits vanilla s'étendent vers +X et +Z, la tête vers Z bas (bornes du modèle Village de -12,5 à 60,5 unités, hitbox `Bed_Village` de Z 0,15 à 2,35, tête haute en Z 0,15-0,35).
- **Planches** : le type de ressource `Wood_Planks` regroupe toutes les planches, comme le tag `planks` de Minecraft.
- **Établi** : les lits vanilla se fabriquent à l'établi de meubles (`Furniture_Bench`), catégorie `Furniture_Beds`.
- **Laines de couleur** : chaque laine a sa recette à l'établi de meubles, catégorie `Furniture_Textiles`. La laine de base plus un pétale (ou du blanc pour les `_Light`) donne la couleur voulue.
- **Recettes autonomes** : un objet n'a qu'une `Recipe`. Une recette de plus est un asset `Server/Item/Recipes/<Id>.json` (`AssetRegistryLoader.java:634-641`), avec les clés `Input`, `Output`, `PrimaryOutput` et `BenchRequirement` (`CraftingRecipe.java`). Le jeu en livre environ 400 (`Server/Item/Recipes/Salvage/**`, type `Processing`) ; aucune de type `Crafting` **[in-game]**.

## Le lit

- **20 lits** `HyVanilla_Bed_<C>`, un par couleur de laine Hytale, comme les tapis.
- **Forme**, sur les cellules d'origine et Z+1, en unités de 32 par bloc :
  - comme dans Minecraft : 1×2 blocs, le dessus du matelas à 9/16, un oreiller en laine blanche (20 unités) à la tête et la couverture de la couleur sur le reste ;
  - *écart avec MC*, demandé par l'utilisateur pour aller avec les meubles de Hytale :
    - à la place des quatre pieds, une tête de lit (épaisseur 4, hauteur 28) et un pied de lit (épaisseur 4, hauteur 22) en planches ;
    - un socle en planches (hauteur 4 à 8) porte le matelas (8 à 18).
  - La géométrie est la nôtre. Le pack Better Beds (CC BY-NC-SA 4.0), montré par l'utilisateur, n'a servi que d'idée : sa licence est incompatible avec la GPL, et rien n'en est repris.
- **Hitbox** `HyVanilla_Bed` : le matelas (X 0-1, Y 0-9/16, Z 0-2), la tête de lit (Z 0-1/8, Y 0-7/8) et le pied de lit (Z 15/8-2, Y 0-11/16).
- **Textures** : un seul modèle, et une texture 128×128 générée par couleur. Elle assemble la laine de la couleur et les planches `Wood_Softwood_Planks_Top`, chacune répétée 2×2, puis la laine blanche. Aucun dessin n'est à créer. `beds.py` échoue si une face lit hors de sa zone.
- **Comportement du lit vanilla de Hytale** : même `Use`, `Primary` et `RespawnBlock` que `Furniture_Village_Bed`. Le joueur dort et le lit devient son point de réapparition. Les citoyens (SP4) reconnaissent un lit par `getBeds()`.
  - *Écart avec MC* : le sommeil suit les règles de Hytale (propriétaire du `RespawnBlock`, passage de la nuit), pas celles de Minecraft. MineColonies n'y touche pas.
- **Point de couchage** : `Offset` de `(-0,1 ; 0,1 ; 0,8)`, `Yaw` de 0. C'est une première valeur, à régler en jeu. Elle reprend `Furniture_Crude_Bed`, le seul lit Hytale de 1×2 : ses `Offset` sont `(-0,1 ; -0,4 ; 0,8)`, soit le même décalage vers le pied que les autres lits.
- **Casse** : le lit rend 1 lit de sa couleur.
- **Recettes** :
  - 3 `Cloth_Block_Wool_<C>` et 3 `Wood_Planks` (n'importe quelles planches) donnent 1 lit, à l'établi de meubles, catégorie `Furniture_Beds` (MC `<color>_bed`) ;
  - recoloration : pour chaque couleur autre que le blanc, la recette de sa laine avec les laines remplacées par les lits de même couleur. Par exemple, lit blanc + `Plant_Petals_Red` donne un lit rouge, et lit rouge + `Plant_Petals_White` donne un lit rouge clair. Même établi, catégorie `Furniture_Beds`. Ce sont des recettes autonomes `HyVanilla_Bed_<C>_Dye`.
  - *Écart avec MC* : Minecraft teint n'importe quel lit avec une teinture. Hytale n'a pas de teinture : on reprend donc le chemin de sa laine.
- **Icône** générée, dans le style des tapis : le lit vu en perspective.
- **Clés de langue** : `hyvanilla.item.bed.<c>.name`, en en-US et fr-FR.

## Réalisation

Tout est en assets, générés par `tools/vanilla/beds.py`, que `generate.py` appelle. Aucun code Java. `validate_pack` vérifie aussi les recettes autonomes et les `ResourceTypeId`.

## À vérifier en jeu

- Le lit se pose sur deux cellules, dans le sens du regard du joueur (noter si la tête est vers le joueur ou à l'opposé), et il est refusé si la seconde cellule est occupée.
- Le modèle et la hitbox coïncident.
- On dort à la bonne place et à la bonne hauteur (sinon, régler `Offset`), et le lit devient le point de réapparition.
- Casser une moitié casse tout le lit et rend un lit. Les recettes, dont la recoloration (recette autonome), apparaissent à l'établi de meubles.
