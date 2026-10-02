# HyColony : l'inventaire du citoyen comme MineColonies (armure, mains, fenêtre et aperçu)

Conception validée par l'utilisateur le 2026-10-02. Recherche : `docs/research/citizen-inventory-window.md` (§ 8 armure, § 9 aperçu par la caméra, essai en jeu), `docs/research/plugin-b-api.md` § 325-333 (grilles d'inventaire dans une page à nous).

## 1. Objectif

Refaire l'inventaire du citoyen exactement comme MC :
- le modèle de MC dans le cœur : 27 cases, 4 cases d'armure à part, et les objets tenus en main et en main secondaire qui pointent vers une case ;
- la fenêtre d'inventaire de MC, dans **notre propre page** (la page de conteneur de Hytale, `Page.Bench`, est abandonnée) ;
- un aperçu du citoyen par la caméra du serveur, à la place du dessin de l'entité que fait MC ;
- l'armure visible sur le corps du PNJ.

## 2. MineColonies

**Modèle** (`api/inventory/InventoryCitizen.java`) :
- `mainInventory` : 27 cases (`DEFAULT_INV_SIZE`, l. 40), agrandies par la recherche `citizeninvslotsaddition` (l. 550), que HyColony n'a pas : 27 fixes ;
- `armorInventory` : 4 cases, indexées par `EquipmentSlot` (`HEAD`, `CHEST`, `LEGS`, `FEET`), l. 52 ;
- `mainItem`, `offhandItem` : l'**indice d'une case** de l'inventaire tenue en main et en main secondaire, `-1` pour rien (l. 57-58, `setHeldItem(hand, slot)` l. 132, `getHeldItem(hand)` l. 116). Sauvegardés par `CitizenData` (`TAG_HELD_ITEM_SLOT`, `TAG_OFFHAND_HELD_ITEM_SLOT`, l. 1351-1352, 1470-1471) ;
- `moveArmorToInventory(slot)` (l. 343) : la pièce revient dans l'inventaire si elle y tient ; pour `MAINHAND`/`OFFHAND`, rien (une main n'est pas une case d'armure).

**Règle d'armure de la fenêtre** (`api/inventory/container/ContainerCitizenInventory.java`) :
- le niveau du bâtiment de travail choisit les pièces permises (l. 153-162) : niveau 1 cuir à or, 2 cuir à maille, 3 cuir à fer, 4 maille à diamant, 5 fer à tout ; sans bâtiment de travail, rien ;
- une case d'armure n'accepte qu'une armure de son emplacement **et** permise (`mayPlace`, l. 255-268) ;
- poser une pile dans une case (d'inventaire ou d'armure) appelle `overruleNextOpenRequestOfCitizenWithStack` (l. 176-185, 236-244) ;
- bornes des paliers (`EquipmentLevelConstants`) : cuir 0, or 1, maille 2, fer 3, diamant 4 ; le niveau d'une pièce est celui de la première pièce de référence de son emplacement qu'elle ne dépasse pas : cuir 1, maille 2, fer 3, diamant 4, au-delà 5 (`ItemStackUtils.getArmorLevel`, l. 309-336).

**Vie de l'armure et des mains** :
- quand le citoyen perd son métier (`AbstractJob.onRemoval`, l. 449-454) : les 4 pièces reviennent dans l'inventaire ; les mains gardent leur case (`moveArmorToInventory` ne fait rien pour elles), et l'entité a ses mains vidées (`setItemSlot`) ;
- l'entité dessine l'armure lue dans l'inventaire (`EntityCitizen.getItemBySlot`) ; sa main, elle, est la sienne : la case tenue (`InventoryCitizen.setHeldItem`) et l'objet montré en main (`setItemInHand`) sont deux choses. `CitizenItemUtils.setHeldItem` change les deux ; `setItemInHand` (repas, ingrédient de l'artisan, `resetValues`) et `removeHeldItem` (sommeil) ne touchent que la main de l'entité ;
- quand il est blessé (`CitizenItemUtils.updateArmorDamage`, appelé par `EntityCitizen.hurt`) : chaque pièce perd `max(1, dégâts / 4)` de durabilité, sauf au hasard selon la recherche `ARMOR_DURABILITY` ; une pièce cassée disparaît ;
- l'outil qui s'use est celui de la case tenue (`CitizenItemUtils.damageItemInHand`, l. 236) ; un outil cassé vide la main de l'entité, la case tenue restant ;
- le dépôt (`AbstractEntityAIBasic.dumpOneMoreSlot`) qui range la case tenue remet la main à −1 et vide celle de l'entité.

**Fenêtre** (`core/client/gui/containers/WindowCitizenInventory.java`, texture `textures/gui/citizen_container.png` 350 × 350) :
- 245 de large, `114 + 18 × rangées` de haut (168 pour 3 rangées) ;
- nom du citoyen en (80, 9) ; ses cases à partir de (8, 23), 9 par rangée, 18 de pas ;
- cadre de l'entité 49 × 72 en (172, 22) ; 4 cases d'armure en (222, 22 + 18 i) ;
- « Inventaire » en (8, 25 + 18 × rangées) ; inventaire du joueur en (8, 90 + 18 i), barre rapide en (8, 148) ;
- l'entité est dessinée dans le cadre et suit la souris (`renderEntityInInventoryFollowsMouse`, l. 134).

## 3. Cœur (TDD)

**Inventaire** (`CitizenData`) :
- les 27 cases actuelles ;
- `CitizenEquipment` : `armor()`, 4 cases dans l'ordre de Hytale (tête, torse, mains, jambes), chacune vide ou une pile avec son usure ; `held(Hand)`, main et main secondaire, chacune l'indice d'une case ou −1. Un indice qui ne pointe plus sur rien (case vidée) reste, comme MC : la main est vide tant que la case l'est.

**Objet tenu**, comme MC, en deux choses :
- la case tenue : `HeldItems.holdSlot` (MC `CitizenItemUtils.setHeldItem`) la prend et montre son objet ; c'est la case de l'outil qu'il use (`WorkerHands.holdSlot` pour le bâtisseur, MC `holdEfficientTool`, et pour l'artisan la première case de l'outil demandé, quel que soit son niveau, MC `AbstractEntityAICrafting.craft` l. 533-538 ; `holdTool` pour la houe du fermier, la première de son inventaire, MC `equipHoe` / `getHoeSlot`, −1 sans houe) ; `HeldItems.release` la rend quand le dépôt range sa pile (`WorkerStock`, MC `dumpOneMoreSlot`) ;
- l'objet montré en main : `CitizenBodies.setHeldItem` seul (`WorkerHands.hold`, MC `setItemInHand`), la case tenue restant : nourriture du repas, ingrédient de l'artisan, graines du fermier, mains vidées (outil cassé, bâtisseur sans outil, fermier qui récolte, sommeil, repas fini, `resetValues`), et le bloc que le bâtisseur va poser, montré avant la pose même refusée, la main vide pour un ordre gratuit (MC `BuildingStructureHandler.prePlacementLogic`, `StructurePlacer` en créatif) ;
- quitter le travail (pluie, pause, repas) ne touche pas la main : MC ne remet l'IA de métier à zéro (`resetAI`) qu'en revenant au travail, sans toucher la main ;
- un corps lié montre l'armure, pas l'objet tenu : la main d'une entité de MC est la sienne, l'IA la remplit à son prochain geste.

**Armure** :
- port `ArmorCatalog` (`citizen/inventory`, dans `GamePorts.armors`) : `armor(ItemKey)` → `Optional<ArmorInfo>` (emplacement, `ItemLevel` et `MaxDurability` de Hytale) ; un port à lui, `ItemCatalog` et son adaptateur étant au bout de leurs dépendances. La vie d'une pièce, en coups, est `ItemCatalog.durability` : `ceil(MaxDurability / DurabilityLossOnHit)`, 0 (jamais usée) pour une pièce incassable ou à `DurabilityLossOnHit` 0 ;
- `ArmorLevels.of(itemLevel)` : le niveau MC d'une pièce, 1 à 5, en comparant son `ItemLevel` à celui des pièces de référence (comme MC compare la valeur d'armure) :

  | Niveau MC | Référence Hytale (`ItemLevel`) | Familles concernées |
  |---|---|---|
  | 1 (cuir) | ≤ 15 (cuir léger) | cuivre, laine, cuir doux, lin, cuir léger |
  | 2 (maille) | ≤ 25 (bronze) | fer, acier, bois, kweebec, bronze, coton, cuir moyen, plongée, guerrier trork |
  | 3 (fer) | ≤ 30 (thorium) | bronze orné, thorium, acier ancien, soldat, `Wool` (≠ `Cloth_Wool`) |
  | 4 (diamant) | ≤ 40 (adamantite) | corbeau, cobalt, soie, cuir épais, adamantite |
  | 5 | au-delà | tissu de cendre, mithril, onyxium, prisma |

- `GuardGear` : les paliers de MC par niveau de bâtiment (§ 2) sur ces niveaux : 1 → 0 à 1, 2 → 0 à 2, 3 → 0 à 3, 4 → 2 à 4, 5 → 3 et plus ; rien sans bâtiment ou au-delà du niveau 5 ; testés par emplacement et niveau ;
- `CitizenInventoryActions` : poser, prendre, déplacer entre les 27 cases, les 4 cases d'armure et l'inventaire du joueur ; l'armure refusée si `GuardGear` dit non ; `MANAGE_HUTS` comme aujourd'hui ; chaque pile posée appelle la clôture de requête de MC (déjà portée pour les 27 cases) ;
- `ArmorWear.onHurt(colonie, citoyen)` : l'usure de l'armure est une règle du **monde**, donc celle de Hytale (`DamageSystems.DamageArmor`, que Hytale ne donne qu'aux joueurs) : un coup dont la cause use l'armure (`DamageCause.isDurabilityLoss`) tombe sur une pièce non cassée tirée au hasard, incassables comprises, et lui ôte un coup de vie (rien pour une incassable) ; une pièce cassée reste portée et protège moins. Le cœur n'écrit la sauvegarde que s'il a usé une pièce ;
- perte du métier (`WorkerModule.free`, seul chemin, renvoi comme réparation au chargement) : les 4 pièces reviennent dans l'inventaire, ce qui ne tient pas reste porté ; les mains gardent leur case et le corps a ses mains vidées (MC `AbstractJob.onRemoval`).

**Sauvegarde** : schéma 10. `armor` (4 piles ou `null`), `heldMain`, `heldOff` (−1 par défaut). `MigrationChain` 9 → 10 avec sa fixture ; une valeur absente prend son défaut ; une main qui n'est pas un nombre ou hors des 27 cases devient −1 et l'armure garde ses 4 premières pièces, la colonie étant alors marquée à réécrire (`EquipmentJson.needsRepair`). Le schéma 9 comptait l'usure d'une pièce d'armure en points de Hytale (le cœur ne l'usait pas, `DurabilityScale` comptant un usage par point) : la migration marque chaque citoyen (`armorWearInPoints`), et la lecture convertit l'usure des pièces de ses 27 cases en coups avec le catalogue (`LegacyArmorWear`, `ceil(points × coups / MaxDurability)`), puis la colonie est réécrite.

**Vue** : pas de record de vue : la page lit le citoyen en direct par ses deux conteneurs (comme la fenêtre de conteneur d'avant), son nom compris.

## 4. Plugin

**Notre fenêtre** (`ui/citizen/CitizenInventoryPage`, page personnalisée, comme `CutterPage` de HyDomum) :
- la disposition de MC ×2 (§ 2) : 490 × 344, fond composé par `tools/ui/citizen_inventory.py` à partir de `citizen_container.png` comme `renderBg` le dessine (haut, bas « joueur », cadre, cases d'armure), agrandi ×4 au plus proche voisin ;
- des grilles de HyBlockUI : les 27 cases du citoyen et ses 4 cases d'armure (`InventoryGrids.drawContainer`, deux fenêtres), le sac et la barre rapide du joueur aux positions de MC (`InventoryGrids.drawPlayerPart`, ajouté à HyBlockUI). Glisser, déposer et maj-clic sont natifs ; le maj-clic est celui de Hytale (`InventoryUtils.smartMoveItem`, écart § 5) ;
- un même conteneur adossé au cœur sert les deux parties (`CitizenItemContainer` et `CitizenInventoryPart`) : l'armure refuse ce que `CitizenInventoryActions.mayWear` refuse (type, emplacement, `GuardGear`), et chaque modification est rapportée au cœur (`onPlayerEdit`, `onArmorEdit`) ;
- titre : le nom du citoyen ; libellé « Inventaire » (clés en-US et fr-FR) ;
- remplace `CitizenInventoryWindows` (`Page.Bench`).

**Aperçu par la caméra** (`CitizenPreviewCamera`) :
- à l'ouverture : `SetServerCamera(Custom, true, …)` attachée au `NetworkId` du corps, `followAttachedEntity`, de face (lacet du corps + π), réglages de `SpectatorSystems.applyFollowCamera` ;
- le cadre de l'aperçu est **transparent** : le citoyen y apparaît. La caméra le garde au centre de l'écran : la fenêtre est placée pour que son cadre tombe au centre, ou la caméra est décalée (`positionOffset`, `rotationOffset`) ; le choix et les valeurs se règlent **en jeu** sur la vraie fenêtre ;
- retour (`SetServerCamera(Custom, false, null)`) à la fermeture de la page, au changement de monde (Hytale ne remet pas la caméra, `Universe.transferPlayerAsync`), et si le corps n'est plus chargé (la fenêtre reste) ; un joueur déconnecté n'a plus de caméra à rendre : elle est seulement oubliée ;
- sans corps chargé : la fenêtre s'ouvre sans aperçu (cadre vide).

**Corps** :
- l'armure du cœur recopiée, avec son usure, dans `InventoryComponent.Armor` du PNJ, au spawn et à chaque changement (le chevalier de test vanilla porte la sienne ainsi). Cette copie protège le PNJ comme l'armure d'un joueur (`ArmorDamageReduction`). Hytale n'use pas l'armure d'un PNJ (`ItemUtils.canDecreaseItemStackDurability` : joueurs seulement) : la copie ne s'use jamais d'elle-même, seul le cœur l'use ;
- l'objet en main est celui que le cœur montre (`CitizenBodies.setHeldItem`, case 0 de la barre du PNJ), séparé de la case tenue (§ 3) ;
- `CitizenHurtSystem` transmet au cœur chaque coup dont la cause use l'armure (`ArmorWear.onHurt`). Les citoyens sont encore invulnérables : l'usure jouera avec la mort des citoyens (`citizen-death.md`).

## 5. Écarts à MineColonies

Chacun porte un `Deviation from MC:` dans le code.

- Hytale a des gants au lieu de bottes : tête, torse, mains, jambes.
- Le niveau d'une armure se lit sur son `ItemLevel` (table § 3), Hytale n'ayant pas de valeur d'armure comparable.
- Monde de Hytale (`Deviation from MC (Hytale world)`) : l'usure de l'armure est celle de Hytale (§ 3) : une pièce au hasard par coup, une pièce cassée gardée, au lieu de `max(1, dégâts / 4)` sur chaque pièce et d'une pièce cassée qui disparaît.
- Pas encore de recherche : ni la chance de `ARMOR_DURABILITY` d'épargner l'armure (`CitizenItemUtils.updateArmorDamage`), ni celle de `TOOL_DURABILITY` d'épargner l'outil (`damageItemInHand`).
- L'artisan ne montre rien dans sa main secondaire, où MC montre un ingrédient ou le produit : le corps n'a qu'une main pour un objet.
- `InventoryCitizen.setHeldItem` de MC écrit aussi la main secondaire (bogue) : non reproduit, chaque main a sa case.
- Une main absente d'une sauvegarde ne tient rien, là où le `getInt` de MC la lit comme la case 0.
- Le maj-clic est celui de Hytale (`InventoryUtils.smartMoveItem`) : des cases du citoyen ou de son armure vers l'inventaire du joueur, rangé selon les réglages du joueur ; du joueur vers les 27 cases du citoyen, puis son armure quand elles sont pleines. Le `quickMoveStack` de MC (`ContainerCitizenInventory`) envoie les cases du citoyen vers celles du joueur en partant de la fin, l'armure en dernier ; une pièce d'armure vers les 27 cases du citoyen ; et les objets du joueur vers ces 27 cases seulement.
- L'aperçu est la vraie scène, filmée par la caméra du serveur, et ne suit pas la souris.
- 27 cases fixes : pas de recherche qui agrandit l'inventaire.
- Avant une pose, le bâtisseur ne s'écarte pas de la case où il se tient (MC `walkAwayFrom`, prévu : `docs/research/architecture/audit-global/02-findings-M.md`), et son geste est le coup « Build » d'une pose réussie, là où MC fait `swing` avant de tenter la pose, même refusée.
- Écart existant, relevé par les relectures (`CitizenAI.dropJobAI`) : quitter le travail (sommeil, repas, pluie, pause) jette toute l'IA de métier et ses champs (le `skippedState` et le `forceLeave` du fermier par exemple), là où MC garde son IA et ne remet que sa machine à états à zéro en revenant au travail (`resetAI`). Le corriger demande que chaque IA de métier sache se remettre à zéro sans être recréée : un changement à part.

## 6. Tests

- Cœur :
  - `GuardGear` : les 5 niveaux de bâtiment, sans bâtiment, mauvais emplacement ;
  - `ArmorLevels` : chaque seuil de la table ;
  - actions de la fenêtre : armure acceptée ou refusée, déplacements, clôture de requête à la pose, `MANAGE_HUTS` ;
  - mains : la case de l'outil usé (bâtisseur, artisan, fermier), la main vide quand l'outil casse, la case gardée quand seule la main de l'entité change (repas, houe cassée), la case rendue au dépôt, un nouveau corps sans objet en main ;
  - perte du métier : l'armure revient dans l'inventaire, ce qui ne tient pas reste porté, les mains gardent leur case ;
  - `ArmorWear` : une pièce par coup, incassables comprises, une pièce cassée gardée, rien d'écrit sans usure ;
  - artisan : une fabrication impossible rapporte la récompense de MC, un outil cassé coûte une action et de la saturation ;
  - sauvegarde : aller-retour, fixture du schéma 9, chaque réparation (main hors bornes, main qui n'est pas un nombre, plus de 4 pièces) réécrite, usure d'armure du schéma 9 convertie de points en coups.
- En jeu (`docs/TESTING.md`) : la fenêtre (disposition de MC, glisser, maj-clic), l'armure refusée selon le niveau de la hutte, l'aperçu (cadrage, retour de la caméra à la fermeture), l'armure visible sur le PNJ, l'outil en main.

## 7. À vérifier en jeu

- Le cadrage de l'aperçu sur la vraie fenêtre (§ 4).
- Le rendu de l'armure sur `PlayerTestModel_V`.
- Le comportement du citoyen pendant l'aperçu (il continue de travailler, la caméra le suit).
