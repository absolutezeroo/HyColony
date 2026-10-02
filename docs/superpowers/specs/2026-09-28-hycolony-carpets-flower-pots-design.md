# HyColony : tapis et pot de fleurs (sous-plugin « Decorations »)

Validé avec l'utilisateur le 2026-09-28. Faits vérifiés : `docs/research/carpets-flower-pots.md`. Mécanisme de packs : `docs/superpowers/specs/2026-09-27-hycolony-architecture-subplugins-design.md` § 7.

## Objectif

Ajouter le vrai tapis et le vrai pot de fleurs de Minecraft. Ce sont des blocs vanilla de Minecraft, que MineColonies n'ajoute pas : c'est un **ajout demandé** (CLAUDE.md § 6), livré dans un sous-plugin `Decorations`, activé par défaut. Ce sera aussi le premier pack qui a de vrais assets, donc le premier test en jeu de `registerPack`.

## Tapis

- Un tapis par couleur de laine Hytale : 20 couleurs, avec la texture vanilla `BlockTextures/Cloth_<C>.png`. Aucun dessin à créer.
- Modèle de 32×2×32, soit 1/16 de bloc, avec la hitbox `Block_Flat`.
- Il se pose sur une face pleine (`Support.Down: Full`) et tombe en objet si on retire le bloc en dessous.
  - *Écart avec MC* : il est refusé sur les dalles, les escaliers et les barrières, car Hytale n'a pas de règle « tout sauf l'air ».
- Recette : 2 laines de la même couleur donnent 3 tapis.
- Clés de traduction en en-US et en fr-FR.

## Pot de fleurs

- Un pot vide par couleur d'argile lisse Hytale (16, ajout demandé : Minecraft n'a qu'un pot), craft de 3 argiles de sa couleur, haut de 3/8 de bloc. Comme dans Minecraft Java, il se pose même au-dessus du vide.
- Il accepte l'équivalent Hytale de toutes les plantes que Minecraft met en pot : fleurs, pousses d'arbre, champignons, fougère, buisson mort, cactus, bambou, etc. Le tableau exact « plante Hytale → état du pot » vit dans les données du pack. L'implémentation l'établit à partir des assets, et les plantes sans équivalent Hytale sont listées : 121 plantes retenues, inventaire et exclusions dans `docs/research/carpets-flower-pots.md` § 6.
- Comportement de Minecraft :
  - utiliser le pot vide avec une plante acceptée en main met la plante dans le pot et en retire une de la main (rien n'est consommé en créatif) ;
  - utiliser un pot garni la main vide, ou avec un objet qui n'est pas une plante acceptée, rend la plante ;
  - utiliser un pot garni avec une plante acceptée ne fait rien ;
  - casser un pot garni rend le pot et la plante.
- Chaque plante correspond à un état du pot. Hytale n'accepte qu'un modèle et une texture par état : chaque pot garni a donc son modèle et sa texture.
- Ces modèles sont **générés une seule fois** par un script gardé dans le dépôt, puis commités. Le script assemble le modèle du pot et le modèle vanilla de la plante, et range leurs textures dans un atlas. Le pot (forme de Minecraft, ombrage `standard`, une zone d'UV par face) est fait dans Blockbench (`tools/vanilla/models/Flower_Pot.blockymodel`, `docs/research/hytale-models.md`). `tools/vanilla/paint.py` peint une case de 96×32 px par couleur au pinceau (`tools/vanilla/brushes.py` : terre cuite tournée dans la couleur de l'argile, terre en miettes), puis `tools/vanilla/bake.py` y cuit la lumière du pot (l'intérieur s'assombrit de lui-même). Pour ajouter une plante, on ajoute une ligne et on relance le script. C'est l'équivalent du modèle parent `flower_pot_cross` de Minecraft, qui reçoit la texture de la plante.
- Le code se partage ainsi :
  - la règle (quelle plante, mettre, rendre, ne rien faire) est une fonction pure du cœur, testée ;
  - le plugin l'applique : un système `UseBlockEvent.Pre` lit l'objet en main, change l'état du bloc et ajuste l'inventaire. Il **n'annule pas** l'événement : un `UseBlock` annulé échoue, et le repli de l'objet tenu poserait alors la plante à côté du pot (`Block_Secondary` → `PlaceModeSelect`) ; la racine du pot est un `Simple` sans effet (`docs/research/carpets-flower-pots.md` § 6). Le pot garni cassé rend le pot et la plante par la `DropList` de son état, sans code ;
  - ce système ne tourne que si le pack `Decorations` est activé.

## À vérifier en jeu

- Le journal et l'autotest montrent le pack `Decorations` enregistré. C'est le premier `registerPack` réel.
- Les tapis : les 20 couleurs, la recette, la chute quand le dessous disparaît, la collision.
- Le pot : poser une plante, la reprendre, ne rien faire quand on tient une autre plante. En casser un garni rend deux objets. Un joueur en créatif ne perd rien.
- Avec `"SubPlugins": {"Decorations": false}`, les blocs posés s'affichent comme « inconnu », sans plantage.
