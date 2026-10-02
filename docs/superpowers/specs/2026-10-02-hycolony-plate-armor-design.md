# Armure de plates

Date : 2026-10-02. Demandé par l'utilisateur : « on va rajouter l'armure du chevalier que rajoute MineColonies ».
Conception validée dans la conversation, sur captures Blockbench (« Beaucoup beaucoup mieux »).

Port de MineColonies : `ItemPlateArmor` (`PLATE_ARMOR`), ses quatre objets `plate_armor_helmet`, `_chest`, `_legs`,
`_boots`, sa recette au forgeron (`crafterrecipes/blacksmith/plate_armor_*.json`) et sa recherche
(`researches/combat/platearmor.json`, effet `platearmorunlock`).

## 1. Ce que fait MineColonies

- Quatre pièces d'armure, matériau `minecolonies:plate_armor` : défense 3 (casque), 8 (plastron), 6 (jambières),
  3 (bottes), soit 20 comme le diamant ; multiplicateur de durabilité 37 (celui de la netherite), enchantabilité 9,
  ténacité et recul nuls, réparée avec des lingots de fer, son d'équipement du fer.
- Fabriquée par le forgeron de niveau 4, une fois la recherche « Plate Armor » faite (32 lingots de fer, après
  « Iron Armor »). Plastron : 7 lingots de fer, 1 cuir, 3 charbon.
- Texture : acier gris uni ; heaume fermé à fente de visière en T, plastron et jambières à bandes.

## 2. Choix (décidés par l'utilisateur)

- **Quatre pièces Hytale** : Hytale n'a que les emplacements `Head`, `Chest`, `Hands`, `Legs` (`ItemArmorSlot`), et
  ses jambières couvrent les pieds (`CosmeticsToHide` `Pants`, `Shoes`). Casque, plastron, gantelets (ajoutés pour
  l'emplacement des mains), jambières avec les solerets (les bottes de MC).
- **Palier Cobalt** : la plate de MC vaut 20/15 du fer, soit environ 1,3 fois ; le Cobalt de Hytale vaut 1,28 fois le
  fer. Résistance physique et aux projectiles 6,4 / 11,52 / 5,12 / 8,96 %, santé +12 / +22 / +10 / +17 (tête, torse,
  mains, jambes). Durabilité 250 (environ 2,5 fois le fer de Hytale, comme 37/15 chez MC), perte 0,5 par coup, comme
  le Cobalt. Sans le bonus `DamageClassEnhancement` `Signature` des objets Cobalt : MC n'a ni ténacité ni recul pour
  cette armure (`ItemPlateArmor`, `0F, 0.0F`).
- **Créatif seulement** : pas de recette tant que le forgeron et les recherches ne sont pas portés.

## 3. Modèles

Chaque pièce reprend **les nœuds d'os de l'armure Cobalt** de Hytale, avec leurs noms, positions, rotations et
décalages exacts (`Head` ; `Pelvis` › `Belly` › `Chest`, `R-Arm`, `L-Arm` ; `R-Forearm` › `R-Hand` ; `Pelvis` ›
`R-Thigh` › `R-Calf`, `R-Foot`, et leurs pendants à gauche ; les jambières Cobalt ont en plus des racines `R-Thigh` et
`R-Calf` pour leurs bottes, dont les nôtres n'ont pas besoin), marqués `isPiece` comme les siens (le client accroche
un tel nœud à l'os du joueur de même nom ; sans le drapeau, tout tombait aux pieds lors du premier essai en jeu), et
pose ses plaques
où le Cobalt pose les siennes : elle tombe en jeu au même endroit que lui. Comme dans les armures de Hytale, les os
sont les seuls nœuds sans forme. Les positions des racines changent d'une armure de Hytale à l'autre, alors que leurs
décalages sont constants ; la règle exacte du client n'est pas connue (l'aperçu `tools/armor/preview.py` assemble les
pièces sur le squelette du joueur selon la plus probable, à quelques unités près). Ombrage `flat`, comme les armures
de Hytale. Le gauche est le miroir du droit (positions en x opposé, rotations miroir), sans étirement négatif.

**Mouvement**, demandé par l'utilisateur (« ça manque de physique sur la bannière et les plumes ») : Hytale n'a pas
de physique de tissu, mais les animations du joueur (2 340 fichiers sous `Characters/Animations/`) font aussi tourner
des nœuds qui ne sont pas des os, présents dans les objets portés : le tissu des armures (`Front_Cloth_1`/`_2`,
`Back_Cloth_1`/`_2`, jusqu'à 35° devant et 60° derrière en courant) et les cheveux (`Hair-B`/`B2`, `Hair-L`/`L2`,
`Hair-R`/`R2`, jusqu'à 45°), autour de leur pivot. Le tabard porte donc ces noms de tissu, en deux moitiés articulées à
la ceinture et à mi-hauteur ; chaque plume est une chaîne de segments articulés bout à bout. Les noms de mèches (le
rouge `Hair-B`/`B2`, les blancs `Hair-L`/`L2` et `Hair-R`/`R2`) vont aux 4ᵉ et 5ᵉ segments, les premiers qui partent
vers l'arrière au-delà du heaume, comme une mèche qui pend (test `test_running_lifts_the_plume_back_not_forward`) :
en marchant et en courant, la queue du plumet se relève vers l'arrière et le haut (jusqu'à 26 unités en course), et
le suivant suit. Sur les segments qui montent, la même rotation pencherait le plumet
vers l'avant, contre le vent. Balayage des 2 340 animations du joueur : la queue n'entre jamais dans le heaume.

- **Heaume** : grand heaume de chevalier à sommet plat et crête ; visière en proue (deux plaques à 12° depuis une
  arête de nez) portant la fente en T de MC et les trous d'aération ; bord inférieur et pivots de visière en laiton ;
  panache de trois plumes en éventail depuis une douille dorée (une rouge au centre, deux blanches plus courtes,
  inclinées de 20°), qui montent puis retombent en arc derrière le heaume.
- **Plastron** : cuirasse et cuirasse basse, emblème en losange doré, gorgerin à deux lames ; trois lames de ventre
  qui s'élargissent vers le bas ; ceinture de cuir à boucle dorée ; tabard rouge devant et derrière (bordure, croix et
  frange dorées) ; épaulières à trois lames avec garde-cou doré ; maille sous les épaulières.
- **Gantelets** : brassard à lames et sangle de cuir, manchette dorée, cubitière en losange ; gantelet à lames, plaque
  des jointures, doigts de cuir.
- **Jambières** : maille aux hanches ; chausses de maille sous un cuissard lisse, sangle de cuir ; genouillère en
  losange ; grève lisse à nervure ; soleret à lames sur le dessus.
- **Peinture** (`tools/common`, `paint.texture`) : plaques encadrées comme l'armure Steel de Hytale (liseré clair en
  haut et à gauche, sombre en bas et à droite, rainure intérieure et rivets aux coins, bombé par un centre plus
  clair), lames qui se chevauchent, maille sombre, cuir, tissu rouge, laiton, plumes à tige claire et barbes obliques.

*Écarts avec MC* : la forme est la nôtre, dans le style de Hytale, à la demande de l'utilisateur (« ça fait pas trop
armure en plaque », « ça manque de détails ») ; MC n'a qu'un acier gris uni. Des gantelets s'ajoutent, les bottes
deviennent les solerets des jambières.

## 4. Objets

`HyColony_Plate_Armor_Head`, `_Chest`, `_Hands`, `_Legs` (`plugin/src/main/resources/Server/Item/Items/HyColony/`),
écrits par `tools/armor/generate.py`, sur le modèle des armures de Hytale : `Armor` (emplacement, résistances,
santé, `CosmeticsToHide` du fer), `Quality` `Rare`, `ItemLevel` 35, `MaxDurability` 250, `DurabilityLossOnHit` 0,5,
`ItemSoundSetId` `ISS_Armor_Heavy`, `Categories` `Items.Armors`, interactions `EquipItem`, `Tags` `Type` `Armor`.
Modèles et textures dans `Common/Items/HyColony/Plate_Armor/`, icônes dans `Common/Icons/Items/HyColony/`. Noms et
descriptions en en-US et fr-FR (`hycolony.lang`).

## 5. Épée et écu de chevalier

Demandés par l'utilisateur (« maintenant l'épée et le bouclier »), assortis à l'armure. *Écart avec MC* : MineColonies
n'ajoute ni épée ni bouclier de chevalier ; ses gardes chevaliers portent l'épée et le bouclier de Minecraft
(`EntityAIMelee`, `Items.SHIELD`) ; ses seules épées sont celle du chef barbare (`ItemChiefSword`) et le cimeterre des
pirates. Choix de l'utilisateur : palier Cobalt, créatif seulement.

- **Objets** `HyColony_Knight_Sword` et `HyColony_Knight_Shield`, écrits par `tools/armor/generate.py` : la copie de
  `Weapon_Sword_Cobalt` et `Weapon_Shield_Cobalt` de Hytale (leur gabarit `Template_Weapon_Sword`/`_Shield`, leurs
  attaques et dégâts `InteractionVars`, leur parade, `Quality`, `ItemLevel`, durabilité), sans leur recette, avec
  notre nom, notre modèle et notre icône.
- **Modèles** (`tools/armor/arms.py`) : la chaîne de nœuds de l'épée et du bouclier en fer de Hytale est reprise
  telle quelle (`R-Attachment` ou `L-Attachment` marqués `isPiece`, puis les repères `Origin_Projectile`, marqué lui
  aussi, `Origin_Blade` pour l'épée et `Origin_Item`, avec leurs positions, rotations et décalages), plus un nœud
  `Handle` placé comme la poignée du fer : les 28 épées et boucliers de Hytale en ont un, et leurs effets le visent
  (`TargetNodeName` : traînées des coups et particules du coup spécial le long de la lame, particule de la charge au
  bouclier) ; nos boîtes sont sous
  `Origin_Item`, aux dimensions de l'épée en fer (poignée de 0 à 22, lame jusqu'à 92) et du bouclier en fer
  (40 de large, 66 de haut, face vers +x).
  - **Épée** : pommeau doré serti d'une gemme rouge, poignée de cuir rouge et bague dorée, garde dorée à quillons
    évasés traversée d'une gemme rouge, talon de lame, lame en paliers (gouttière sombre à filet d'or, fil clair)
    jusqu'à une pointe en losange.
  - **Écu** : écu de chevalier en bandes qui se resserrent jusqu'à une pointe en losange, émail rouge bordé d'acier
    sur son contour, croix et bosse dorées ; au dos, une poignée sur deux pattes.

## 6. À vérifier en jeu

- Chaque pièce se porte, à sa place sur le corps, comme une pièce Cobalt ; le heaume couvre la tête, le plumet ne
  traverse rien ; les gantelets suivent les mains, les jambières les jambes en marchant.
- En marchant et en courant, le tabard bat devant et derrière, et les plumes se balancent, sans traverser le heaume
  ni le corps.
- Les résistances et la santé s'appliquent ; la durabilité baisse.
- Les icônes montrent chaque pièce.
- L'épée se tient comme l'épée en fer, frappe comme celle en Cobalt (attaques et coup spécial), et pare ; ses
  traînées et les particules du coup spécial suivent la lame ; l'écu se porte au bras gauche, face vers l'extérieur,
  bloque comme l'écu en Cobalt, et sa charge montre sa particule.
