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
- `moveArmorToInventory(slot)` (l. 343) : la pièce revient dans l'inventaire si elle y tient ; pour `MAINHAND`/`OFFHAND`, la main est vidée.

**Règle d'armure de la fenêtre** (`api/inventory/container/ContainerCitizenInventory.java`) :
- le niveau du bâtiment de travail choisit les pièces permises (l. 153-162) : niveau 1 cuir à or, 2 cuir à maille, 3 cuir à fer, 4 maille à diamant, 5 fer à tout ; sans bâtiment de travail, rien ;
- une case d'armure n'accepte qu'une armure de son emplacement **et** permise (`mayPlace`, l. 255-268) ;
- poser une pile dans une case (d'inventaire ou d'armure) appelle `overruleNextOpenRequestOfCitizenWithStack` (l. 176-185, 236-244) ;
- niveaux d'armure (`EquipmentLevelConstants`) : cuir 0, or 1, maille 2, fer 3, diamant 4, au-delà 5 ; le niveau d'une pièce se lit en comparant sa valeur d'armure à celle des pièces de référence (`ItemStackUtils.getArmorLevel`, l. 309-336).

**Vie de l'armure et des mains** :
- quand le citoyen perd son métier (`AbstractJob.onRemoval`, l. 449-454) : main, main secondaire, puis les 4 pièces reviennent dans l'inventaire ;
- quand il est blessé (`CitizenItemUtils.damageArmor`, l. 305-321) : chaque pièce perd `max(1, dégâts / 4)` de durabilité ; une pièce cassée disparaît ;
- l'outil qui s'use est celui de la case tenue (`CitizenItemUtils.damageItemInHand`, l. 236).

**Fenêtre** (`core/client/gui/containers/WindowCitizenInventory.java`, texture `textures/gui/citizen_container.png` 350 × 350) :
- 245 de large, `114 + 18 × rangées` de haut (168 pour 3 rangées) ;
- nom du citoyen en (80, 9) ; ses cases à partir de (8, 23), 9 par rangée, 18 de pas ;
- cadre de l'entité 49 × 72 en (172, 22) ; 4 cases d'armure en (222, 22 + 18 i) ;
- « Inventaire » en (8, 25 + 18 × rangées) ; inventaire du joueur en (8, 90 + 18 i), barre rapide en (8, 148) ;
- l'entité est dessinée dans le cadre et suit la souris (`renderEntityInInventoryFollowsMouse`, l. 134).

## 3. Cœur (TDD)

**Inventaire** (`CitizenData`) :
- les 27 cases actuelles ;
- `CitizenArmor` : 4 cases dans l'ordre de Hytale (tête, torse, mains, jambes), chacune vide ou une pile avec son usure ;
- `HeldSlots` : main et main secondaire, chacune l'indice d'une case ou rien. Un indice qui ne pointe plus sur rien (case vidée) reste, comme MC : la main est vide tant que la case l'est.

**Objet tenu** : `WorkerHands.hold` passe d'un objet à une case (`holdSlot(hand, slot)`, MC `setHeldItem(hand, slot)`) ; le corps affiche l'objet de cette case. Chaque appel actuel à `CitizenBodies.setHeldItem` (14) est repris : main vide → `clear`, outil ou nourriture de l'inventaire → sa case. L'affichage suit la case quand son contenu change.

**Armure** :
- port `ItemCatalog` : `armor(ItemKey)` → `Optional<ArmorInfo>` (emplacement, `ItemLevel` de Hytale) ;
- `ArmorLevels.of(itemLevel)` : le niveau MC d'une pièce, en comparant son `ItemLevel` à celui des pièces de référence (comme MC compare la valeur d'armure) :

  | Niveau MC | Référence Hytale (`ItemLevel`) | Familles concernées |
  |---|---|---|
  | 0 (cuir) | ≤ 15 (cuir léger) | cuivre, laine, cuir doux, lin, cuir léger |
  | 1 (or) | ≤ 20 (fer) | fer, acier, bois, kweebec |
  | 2 (maille) | ≤ 25 (bronze) | bronze, coton, cuir moyen, plongée, guerrier trork |
  | 3 (fer) | ≤ 30 (thorium) | bronze orné, thorium, acier ancien, soldat, `Wool` (≠ `Cloth_Wool`) |
  | 4 (diamant) | ≤ 40 (adamantite) | corbeau, cobalt, soie, cuir épais, adamantite |
  | 5 | au-delà | tissu de cendre, mithril, onyxium, prisma |

- `GuardGear` : les paliers de MC par niveau de bâtiment (§ 2), testés par emplacement et niveau ;
- `CitizenInventoryActions` : poser, prendre, déplacer entre les 27 cases, les 4 cases d'armure et l'inventaire du joueur ; l'armure refusée si `GuardGear` dit non ; `MANAGE_HUTS` comme aujourd'hui ; chaque pile posée appelle la clôture de requête de MC (déjà portée pour les 27 cases) ;
- `ArmorWear.onHurt(citizen, dégâts MC)` : `max(1, dégâts / 4)` sur chaque pièce, une pièce cassée retirée (MC `damageArmor`). Les dégâts de Hytale sont ramenés à l'échelle de MC (20 points de vie) comme la santé affichée (`CitizenViews.health`) ;
- perte du métier : main, main secondaire et les 4 pièces reviennent dans l'inventaire (MC `AbstractJob.onRemoval`) ; ce qui ne tient pas reste où il est, comme MC.

**Sauvegarde** : schéma 10. `armor` (4 piles ou `null`), `heldMain`, `heldOff` (−1 par défaut). `MigrationChain` 9 → 10 avec sa fixture ; une valeur absente prend son défaut, un indice hors des 27 cases devient −1 (`heal`).

**Vue** : `CitizenInventoryView` (nom, 27 cases, 4 pièces, mains) pour la fenêtre.

## 4. Plugin

**Notre fenêtre** (`ui/citizen/CitizenInventoryPage`, page personnalisée, comme `CutterPage` de HyDomum) :
- la disposition de MC ×2 (§ 2) : 490 × 336, texture `citizen_container.png` copiée dans `Pages/HyColony/Mc/` et agrandie ×4 au plus proche voisin, découpée comme MC (haut de la fenêtre, bas « joueur », cadre, case) ;
- trois grilles de HyBlockUI (`InventoryGrids.drawContainer`) : les 27 cases du citoyen (fenêtre 1), ses 4 cases d'armure (fenêtre 2), et l'inventaire du joueur (sac et barre rapide). Glisser, déposer et maj-clic sont natifs ; chaque dépôt passe par `CitizenInventoryActions` ;
- le conteneur des 27 cases reprend `CitizenItemContainer` ; celui de l'armure (4 cases, `ItemContainerUtil.trySetArmorFilters` pour le type, plus un filtre qui interroge `GuardGear` dans le cœur) ;
- titre : le nom du citoyen ; libellé « Inventaire » (clés en-US et fr-FR) ;
- remplace `CitizenInventoryWindows` (`Page.Bench`).

**Aperçu par la caméra** (`CitizenPreviewCamera`) :
- à l'ouverture : `SetServerCamera(Custom, true, …)` attachée au `NetworkId` du corps, `followAttachedEntity`, de face (lacet du corps + π), réglages de `SpectatorSystems.applyFollowCamera` ;
- le cadre de l'aperçu est **transparent** : le citoyen y apparaît. La caméra le garde au centre de l'écran : la fenêtre est placée pour que son cadre tombe au centre, ou la caméra est décalée (`positionOffset`, `rotationOffset`) ; le choix et les valeurs se règlent **en jeu** sur la vraie fenêtre ;
- retour (`SetServerCamera(Custom, false, null)`) à la fermeture de la page, à la déconnexion, et si le corps n'est plus chargé (la fenêtre reste) ;
- sans corps chargé : la fenêtre s'ouvre sans aperçu (cadre vide).

**Corps** :
- l'armure du cœur recopiée dans `InventoryComponent.Armor` du PNJ, au spawn et à chaque changement (le chevalier de test vanilla porte la sienne ainsi). Hytale n'use pas l'armure d'un PNJ (`ItemUtils.canDecreaseItemStackDurability` : joueurs seulement) : la copie ne s'use jamais d'elle-même, seul le cœur l'use ;
- l'objet en main affiché est celui de la case tenue ;
- `CitizenHurtSystem` transmet les dégâts au cœur (`ArmorWear`). Les citoyens sont encore invulnérables : l'usure jouera avec la mort des citoyens (`citizen-death.md`).

## 5. Écarts à MineColonies

Chacun porte un `Deviation from MC:` dans le code.

- Hytale a des gants au lieu de bottes : tête, torse, mains, jambes.
- Le niveau d'une armure se lit sur son `ItemLevel` (table § 3), Hytale n'ayant pas de valeur d'armure comparable.
- L'aperçu est la vraie scène, filmée par la caméra du serveur, et ne suit pas la souris.
- 27 cases fixes : pas de recherche qui agrandit l'inventaire.

## 6. Tests

- Cœur :
  - `GuardGear` : les 5 niveaux de bâtiment, sans bâtiment, mauvais emplacement ;
  - `ArmorLevels` : chaque seuil de la table ;
  - actions de la fenêtre : armure acceptée ou refusée, déplacements, clôture de requête à la pose, `MANAGE_HUTS` ;
  - mains : la case tenue, l'outil usé dans cette case, la main vide quand la case se vide ;
  - perte du métier : tout revient dans l'inventaire, ce qui ne tient pas reste ;
  - `ArmorWear` : `max(1, dégâts / 4)`, pièce cassée retirée ;
  - sauvegarde : aller-retour, fixture du schéma 9, indice hors bornes réparé.
- En jeu (`docs/TESTING.md`) : la fenêtre (disposition de MC, glisser, maj-clic), l'armure refusée selon le niveau de la hutte, l'aperçu (cadrage, retour de la caméra à la fermeture), l'armure visible sur le PNJ, l'outil en main.

## 7. À vérifier en jeu

- Le cadrage de l'aperçu sur la vraie fenêtre (§ 4).
- Le rendu de l'armure sur `PlayerTestModel_V`.
- Le comportement du citoyen pendant l'aperçu (il continue de travailler, la caméra le suit).
