# Modèles des blocs de hutte

Date : 2026-10-02. Statut : constructeur fait ; hôtel de ville, maison, fermier et bûcheron à venir.

## But

Chaque bloc de hutte a son propre modèle, qui montre son métier au premier coup d'œil. Jusqu'ici, toutes les huttes
empruntaient un petit coffre de Hytale (`Decorative_Sets/<Set>/Chest_Small`).

*Écart avec MC*, demandé par l'utilisateur : MineColonies fait de chaque hutte une scène miniature
(`sources/minecolonies/.../models/block/blockhut*.json`, de 11 à 34 éléments, textures de Minecraft). Nous ne
la portons pas. Chaque hutte a un design à nous, dans le style des meubles de Hytale.

## Règles communes

- Un bloc de 1×1×1 (32 unités par côté), posé sur un support plein, comme avant : le placement et le reste du jeu ne
  changent pas.
- Le devant du modèle est du côté +z : c'est lui qui fait face au joueur qui pose le bloc, une fois tourné par
  `VariantRotation` `NESW`. Vu en jeu le 2026-10-02 : un premier constructeur tourné vers -z s'affichait à l'envers.
  La serrure du petit coffre de Hytale est bien à +z (ses charnières sont à -z), une fois les enfants placés selon la
  règle de `docs/research/hytale-models.md` § 3.
- Le modèle remplit la hauteur du bloc (32 unités) sans la dépasser.
- Le modèle est construit dans Blockbench, au format `hytale_prop`, et suit `docs/research/hytale-models.md` :
  - boîtes seulement, ombrage `standard`, une zone d'UV par face ;
  - dessous posés sur une surface retirés ;
  - aucune face confondue ;
  - chaque groupe porte le nom de sa pièce principale.
- Il est livré tel quel : `plugin/src/main/resources/Common/Blocks/HyColony/Huts/<Hutte>.blockymodel`.
- `tools/huts/generate.py` peint la texture (`Huts/<Hutte>.png`) avec `tools/vanilla/paint.py` et les matières du
  module de la hutte (`tools/huts/<hutte>.py`). Il dessine aussi l'icône (`Icons/Items/HyColony/Hut_<Hutte>.png`),
  vue de face (`pack.draw_model`, vue depuis +x +z).
- L'objet `HyColony_Hut_<Hutte>` lit ce modèle, cette texture et cette icône. Il garde ses états `OpenWindow` et
  `CloseWindow` (sons du coffre), mais sans l'animation du couvercle.

## Constructeur

Une table à dessin d'architecte, en 40 boîtes, avec une texture de 128×64 :

- **Bâti** en planches de hêtre (`Wood_Hardwood_Planks`) : quatre pieds (ceux de l'arrière plus hauts), des traverses
  basses, une étagère et une traverse haute sur chaque flanc.
- **Planche à dessin** en bois clair (`Wood_Lightwood_Planks`), inclinée de 18° vers le joueur, avec un rebord à
  l'avant. Dessus :
  - un plan bleu quadrillé, avec un cadre et le croquis d'une maison qui se lit depuis l'avant, et une tranche bleu
    foncé ;
  - un rouleau de papier ;
  - un crayon (cône de bois et mine noire) ;
  - un encrier bleu nuit sur un socle, dans le coin avant de la planche, avec sa plume.
- **Hauteur** : pieds avant de 20, pieds arrière de 26 ; le haut de la plume touche le haut du bloc.
- **Sur l'étagère** : trois briques et trois rouleaux de plans (papier crème, bout en spirale, liens rouges, dont un
  plan bleu).
- **Sur les flancs** :
  - un marteau, dont la tête repose sur deux crochets en fer forgé ;
  - une scie (lame à dents, poignée fermée), pendue à un crochet qui passe dans sa poignée.

## Huttes suivantes

Pistes proposées à l'utilisateur, chacune à valider sur des captures Blockbench avant intégration :

| Hutte | Piste |
|---|---|
| Hôtel de ville | Pupitre avec un grand registre, une plume et un encrier, une bannière de la colonie, une cloche. |
| Maison | Maisonnette miniature : toit à deux pans, cheminée, porte et fenêtre éclairée. |
| Fermier | Carré de terre avec des pousses, caisse de légumes, fourche et houe croisées, arrosoir. |
| Bûcheron | Souche avec une hache plantée, tas de bûches, jeune arbre. Bloc avec le métier (pas encore porté). |

## À vérifier en jeu

- Le constructeur s'affiche avec son modèle et sa texture, sans face qui scintille, et fait face au joueur qui le pose,
  dans les quatre directions.
- L'icône de l'objet montre la table de face.
- Utiliser la hutte ouvre toujours sa fenêtre ; aucune erreur d'animation au journal.
