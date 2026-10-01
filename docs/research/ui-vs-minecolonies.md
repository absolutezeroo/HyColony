# Fenêtres HyColony comparées à MineColonies

Recherche du 2026-09-26. Référence MC : `ldtteam/minecolonies`, branche `version/main` (fichiers bruts sous `raw.githubusercontent.com/ldtteam/minecolonies/version/main/`). Chemins abrégés :

- `gui/…` = `src/main/resources/assets/minecolonies/gui/…`
- `client/gui/…` = `src/main/java/com/minecolonies/core/client/gui/…`

Captures du wiki (URL relatives renvoyées par les pages, préfixe `https://minecolonies.com`) :

- hôtel de ville (`https://minecolonies.com/wiki/buildings/townhall/`) : `/_astro/th_actions.GcdtXQcV_Z2lwfuR.webp`, `/_astro/th_information.Bn6Ig32q_14Llrk.webp`, `/_astro/th_permissions1.D6JBfh4B_13VeVv.webp`, `/_astro/th_citizens.qyV_sPBz_O1Inz.webp`, `/_astro/th_statistics.sjj02jHD_Zdayps.webp`, `/_astro/th_settings.wWuEqLjc_Z1j7DUX.webp` ;
- hutte du constructeur (`https://minecolonies.com/wiki/buildings/builder/`) : `/_astro/main.ldbNfPgk_zqBAe.webp`, `/_astro/required-resources.Bp1T7X7G_Zxfv21.webp`, `/_astro/settings.lQRJHID4_Z3n6fv.webp`, `/_astro/workorders.B0GUKFpp_2qh5dd.webp` ;
- presse-papiers (`https://minecolonies.com/wiki/items/clipboard/`) : `/_astro/clipboardgui1.BHNn3TkP_41U1u.webp`, `/_astro/clipboardgui2.CDX-OShf_Z7cwHu.webp`.

Le wiki n'a pas de page dédiée à la fenêtre du citoyen (`/wiki/systems/citizens/` renvoie 404). Pour elle, seul le code fait foi. Quand le wiki et le code divergent, c'est le code qui gagne (voir l'hôtel de ville, § 1).

Légende :

- **[actuel]** : relève d'un système qu'on a déjà (colonie, huttes, constructeur, requêtes, ordres, citoyens et compétences, permissions) ;
- **[futur]** : dépend d'un système absent (bonheur, santé et nourriture, recherche, gardes, coursiers, alliances, bannière, carte, mercenaires, artisanat, stock minimum, statistiques de production). On l'écarte pour l'instant.

---

## 0. Ce que notre système `.ui` sait faire (rappel vérifié)

- **Listes, icônes, barres** : voir `plugin-b-api.md` § 9 (`TopScrolling`, `ItemIcon`/`ItemSlot`, `$C.@ProgressBar`).
- **Onglets, motif vanilla vérifié dans le code serveur** : `builtin/triggervolumes/ui/TriggerVolumeInspectorPage.java:518-535` (`buildTabs`) procède ainsi :
  - il vide `#TabButtons` (un `Group` en `LayoutMode: Left`, `TriggerVolumeInspectorPage.ui:147-150`) ;
  - il y ajoute un `TriggerVolumeInspectorTabButton.ui` par onglet (un `$C.@SmallSecondaryTextButton #TabButton`, 96 × 30) ;
  - il pose `.Text`, `.TooltipText` et `.Disabled = (onglet sélectionné)` ;
  - il lie `Activating` avec `EventData("Action","ChangeTab").append("Tab", nom)`.
  
  Le contenu de chaque onglet est un `Group` à `Visible: false` (`#VolumeTab`, `#TagsTab`, `#EffectsTab`, `TriggerVolumeInspectorPage.ui:182-198`), et la page ne rend visible que l'onglet choisi (`TriggerVolumeInspectorPage.java:696-698`). C'est le motif à copier : l'onglet choisi est un état de la page Java, pas une règle de jeu, donc il reste dans le plugin. **[in-game]** Rendu exact d'un `@SmallSecondaryTextButton` désactivé comme « onglet actif ».
- **`TabNavigation`/`TabButton`** existent (`Common.ui:668-708` : `@TopTabsStyle`, `@HeaderTabsStyle` ; exemple dans `Pages/UIGallery/Categories/NavigationContent.ui:27-49`), à onglets **icônes** (`Icon`, `TooltipText`, `Id`, `SelectedTab`). Le type d'événement `CustomUIEventBindingType.SelectedTabChanged(23)` existe (`protocol/packets/interface_/CustomUIEventBindingType.java:29`), mais aucune page serveur ne l'utilise. **[in-game]** : on ne sait pas quelle donnée l'événement renvoie. À éviter tant que ce n'est pas vérifié ; le motif des boutons suffit.
- **Règle TextSpans** : un `Message` imbriqué va sur `.TextSpans`, jamais sur `.Text` ; sur un bouton, une clé complète par variante (`plugin-b-api.md`, « Verified in game »).
- **Taille des classes** : `BuildingPage.java` fait déjà 265 lignes. Un passage aux onglets impose d'extraire un rendu par onglet (par exemple `BuildingMainTab`, `BuildingResourcesTab`), sinon on dépasse 300/400 lignes (CLAUDE.md § 2).

---

## 1. Hôtel de ville

### MineColonies

Cadre commun : `gui/townhall/windowtownhall.xml`. Sept marque-pages à gauche, dans cet ordre :

| # | id | Libellé (clé) | Classe | Layout |
|---|---|---|---|---|
| 1 | `actions` | `townhalltab.actions` | `WindowMainPage` | `layoutactions.xml` |
| 2 | `infopage` | `townhalltab.information` (icône `red_wax_work_orders`) | `WindowInfoPage` | `layoutinfo.xml` |
| 3 | `permissions` | `townhalltab.permissions` | `WindowPermissionsPage` | `layoutpermissions.xml` |
| 4 | `citizens` | `townhalltab.citizens` | `WindowCitizenPage` | `layoutcitizens.xml` |
| 5 | `happiness` | `townhalltab.stats` | `WindowStatsPage` | `layoutstats.xml` |
| 6 | `alliances` | `townhalltab.alliances` | `WindowAlliancePage` | `layoutalliance.xml` |
| 7 | `settings` | `townhalltab.settings` | `WindowSettings` | `layoutsettings.xml` |

Source : `client/gui/townhall/AbstractWindowTownHall.java`, `registerButton(BUTTON_ACTIONS…BUTTON_ALLIANCE)`. Le bouton d'id `happiness` ouvre en fait **les statistiques** (label `townhalltab.stats`). Le wiki appelle aussi « happiness » la capture des statistiques ; le code tranche.

1. **Actions** (`layoutactions.xml`) :
   - à gauche : « nom actuel » + nom + bouton crayon `rename` ; boutons `build` (options de construction de l'hôtel de ville, voir § 2), `map` et `mercenaries` ;
   - à droite (« visuel ») : `bannerPicker`, couleur d'équipe (`colorPicker`), style de colonie (`colonyStylePicker`), `textureStylePicker`, `nameStylePicker` ;
   - le titre porte le nom de la colonie (`WindowMainPage.java:371`).
2. **Information** (`layoutinfo.xml`) :
   - à gauche : journal d'événements de la colonie (`eventsList` : action, nom, position), filtré par intervalle (`intervals`) ;
   - à droite : **liste des ordres de travail** (`workOrderList`), chaque ligne portant `plus` ↥, `minus` ↧, `work` (nom d'affichage), `assignee` (constructeur, gris) et `delete` X. Tri `IWorkOrder.WORK_ORDER_COMPARATOR` ; ↥ et ↧ sont masqués aux extrémités (`WindowInfoPage.java`, `fillWorkOrderList`).
3. **Permissions** : sous-pages pour les joueurs (ajout par nom ou joueur en ligne, liste avec rang et retrait), les rangs (ajout, type, déclencheurs par action), les blocs libres et l'historique des événements de permission.
4. **Citoyens** (`layoutcitizens.xml`) :
   - à droite : champ `search` (filtre sur le nom ou le métier) et `citizenList`, des boutons au nom du citoyen, triés par nom (`WindowCitizenPage.java:37,103-106`) ;
   - un clic sélectionne le citoyen : métier en gras, genre, santé/max, bonheur/10, saturation/20, bouton `recallone` ;
   - à gauche : bonheur global et liste des modificateurs.
5. **Statistiques** (`layoutstats.xml`) : à gauche, population totale / capacité (avec bulle d'avertissement), puis un effectif par métier (« métier : n / m »), les sans-emploi et les enfants (`WindowStatsPage.java:98-217`) ; à droite, les statistiques de production par intervalle.
6. **Alliances** et 7. **Réglages** (`layoutsettings.xml` : liste générique de réglages).

### HyColony (`TownHall.ui`, `TownHallPage.java`, `TownHallViews.java`)

Une seule page de 600 px. De haut en bas :

- nom de la colonie, propriétaire, jour ;
- trois boutons : `#BuildingButton` (ouvre la fenêtre de hutte de l'hôtel de ville), `#WorkOrdersButton` et `#RequestsButton` (fenêtres séparées) ;
- champ de renommage avec son bouton (visible pour un gestionnaire de colonie) ;
- liste des citoyens (nom + statut `absent`/`idle`/`wandering`/`working`), non cliquable.

**Mise à jour du 2026-10-01** : la fenêtre a maintenant l'apparence du livre de MC, avec les textures de MC copiées dans `Pages/HyColony/Mc/` et les positions de `windowtownhall.xml` et des `layout*.xml` doublées (CLAUDE.md § 7). Les onglets sont les sceaux de cire. Écarts restants, chacun marqué `Deviation from MC` dans les `.ui` et dans `TownHallPage` :

- un onglet fermé donne son nom en infobulle, et non par le ruban qui sort au survol (`onHoverId`) ;
- les emplacements des sceaux sont tassés, puisqu'il n'y a pas d'onglet Permissions ;
- la ligne du nom est remontée (y 48 au lieu de 64-70), pour que le champ de renommage ne touche pas le ruban ; l'en-tête « nom actuel » manque ;
- la liste des citoyens occupe toute la page de droite (ni sélection, ni recherche) et montre le statut à côté du nom ;
- les boutons ↥ et ↧ des ordres sont étiquetés « + » et « − », car les polices du client n'ont pas de flèches.

### Écarts

Ce qui manque chez nous :

- **[actuel]** Onglets. La liste des ordres est dans l'onglet Information chez MC ; chez nous c'est une fenêtre à part.
- **[actuel]** Onglet Permissions : le cœur a `Permissions.members()`, `ranks()` et `ColonyAdministration.setRank`, mais aucune fenêtre (commande seulement).
- **[actuel]** Citoyens : tri par nom, filtre de recherche, sélection avec le détail du métier. Santé, bonheur et saturation sont **[futur]**, de même que `recallone` (pas de rappel dans le cœur).
- **[actuel]** Statistiques : population, effectif par métier, sans-emploi, enfants. Capacité de logement et statistiques de production : **[futur]**.
- **[actuel]** Style de colonie (`colonyStylePicker`) : on a des styles par hutte, pas un style de colonie. Écart à documenter s'il reste.
- **[futur]** Journal d'événements, carte, mercenaires, bannière, couleur d'équipe, textures et styles de noms, alliances, réglages de colonie.

Ce qu'on montre et que MC ne montre pas :

- le bouton **Requêtes**. Chez MC, les requêtes passent par l'objet presse-papiers, pas par l'hôtel de ville. C'est un raccourci acceptable tant qu'on n'a pas l'objet, à marquer `Deviation from MC`.
- le jour de la colonie et le propriétaire en en-tête.

### Proposition

`TownHall.ui` avec une rangée `#TabButtons` (motif vanilla) et un `Group` par onglet :

1. **Actions** : nom + renommage, « Options de construction » (ouvre la fenêtre de hutte de l'hôtel de ville), et **Requêtes** en attendant le presse-papiers.
2. **Information** : le contenu actuel de `WorkOrders.ui`, repris dans un groupe (`OrderRow.ui` inchangé) ; journal d'événements plus tard.
3. **Permissions** : membres (nom, rang, bouton de rang pour `EDIT_PERMISSIONS`).
4. **Citoyens** : liste triée par nom, dont chaque ligne est un bouton qui ouvre la fenêtre du citoyen (écart assumé : MC affiche le détail sur place ; on peut aussi faire une ligne sélectionnée + un panneau de détail), et un `$C.@TextField` de filtre.
5. **Statistiques** : population et effectifs par métier (lignes texte).

La vue `TownHallView` s'enrichit des ordres (déjà calculés par `WorkOrderViews`), des membres et des effectifs.

### Inventaire complet, 2026-10-01

Inventaire de tout ce que contient la fenêtre de l'hôtel de ville de MC, élément par élément, avant la conception « finir l'hôtel de ville ». Les sources MC sont lues dans la copie locale `sources/` (CLAUDE.md § 6), avec les numéros de ligne de cette copie. Rien n'est conçu ici.

Abréviations :

- `th/` = `sources/minecolonies/src/main/java/com/minecolonies/core/client/gui/townhall/` ;
- `xml/` = `sources/minecolonies/src/main/resources/assets/minecolonies/gui/townhall/` ;
- `msg/` = `sources/minecolonies/src/main/java/com/minecolonies/core/network/messages/` ;
- `mc:` = `sources/minecolonies/src/main/java/com/minecolonies/` ;
- `tex/` = `sources/minecolonies/src/main/resources/assets/minecolonies/textures/gui/` ;
- `hc:` = `core/src/main/java/dev/hycolony/core/` ;
- `pl:` = `plugin/src/main/java/dev/hycolony/plugin/` ;
- `ui:` = `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/` (`Mc/` = les textures MC déjà copiées) ;
- `v:` = `Common/UI/Custom/` de `pre-release-0.7.0-pre.4-Assets.zip` ; `srv:` = `build/vineflower/hytale-server/com/hypixel/hytale/`.

Statuts : **fait** = déjà dans la fenêtre ; **A** = le cœur l'a, seule la fenêtre manque ; **B** = le cœur en a une partie (le manque est dit) ; **C** = il faut un système entier absent (nommé) ; **hors-port** = propre à MC sur Minecraft (Patreon).

**Règle commune des messages MC.** Tout message qui hérite de `AbstractColonyServerMessage` exige `MANAGE_HUTS` sauf s'il redéfinit `permissionNeeded()` (`msg/server/AbstractColonyServerMessage.java:61-63`, contrôle `:122`). Aucun message de l'hôtel de ville ne redéfinit `ownerOnly()` (seule `AbstractColonyServerMessage` contient ce nom). `PermissionsMessage.*` n'hérite pas de cette classe et vérifie lui-même `EDIT_PERMISSIONS`.

#### Cadre commun (`xml/windowtownhall.xml`, `th/AbstractWindowTownHall.java`)

| Élément MC | Ce qu'il fait (source) | HyColony | Statut |
|---|---|---|---|
| Livre `townhall_book` | Fond 374 × 243 (`windowtownhall.xml:3`) | `ui:TownHall.ui:51-54` | fait |
| Sept sceaux de cire + rubans courts | Un bouton par onglet, ouvre la page (`AbstractWindowTownHall.java:36-42`, `windowtownhall.xml:5-11,57-70`) | Quatre onglets (`pl:ui/townhall/TownHallPage.java:26-37`), emplacements tassés (`TownHall.ui:150-154`) | fait pour 4 ; Permissions, Alliances, Réglages manquent |
| Ruban long de l'onglet ouvert | Bouton désactivé portant le nom de l'onglet (`windowtownhall.xml:13-33`, `AbstractWindowTownHall.java:44-47`) | `TownHall.ui:156-174` | fait |
| Ruban au survol (`*Ext`, `onHoverId`) | `windowtownhall.xml:35-55,57` | Infobulle à la place (écart déjà noté plus haut) | écart documenté |
| Libellé du ruban « Actions » | MC y écrit **le nom de la hutte et son niveau** (`th/WindowMainPage.java:93-94`), pas « Actions » | `TownHall.ui:158` écrit « Actions » ; `TownHallView` n'a pas le niveau de l'hôtel de ville (`hc:app/ui/TownHallView.java:7-15`) | B (le niveau existe sur `Building`, pas dans la vue) |

#### Onglet 1 : Actions (`th/WindowMainPage.java`, `xml/layoutactions.xml`)

| Élément MC | Ce qu'il fait (source, message serveur, règle) | Équivalent HyColony | Statut |
|---|---|---|---|
| Titre « nom actuel » + nom | `layoutactions.xml:4,6` ; nom posé à l'ouverture (`WindowMainPage.java:371`) | `pl:ui/townhall/TownHallActionsTab.java:32` ; pas d'en-tête (écart noté plus haut) | fait |
| Crayon `rename` | Ouvre `WindowTownHallNameEntry` (`WindowMainPage.java:97,383-386`) ; voir sous-fenêtre plus bas | Champ en ligne + bouton (`TownHallActionsTab.java:38-49`), `hc:app/action/ColonyAdministration.java:52` (`MANAGE_HUTS`) | fait (forme différente) ; longueur : voir sous-fenêtre |
| `build` (« Options de construction ») | `mc:core/client/gui/AbstractBuildingMainWindow.java:65-86` : annule l'ordre en cours si le libellé est « annuler », sinon ouvre `WindowBuildBuilding` | `#BuildingButton` ouvre la fenêtre de hutte (`hc:app/view/ColonyWindows.java:86`), qui contient le sous-panneau d'options (`pl:ui/BuildOptionsPanel.java:13`) | A (ouvrir directement les options ; texture `_build` absente) |
| `map` | Ouvre `WindowColonyMap` (`WindowMainPage.java:99,399-402` ; `mc:core/client/gui/map/WindowColonyMap.java:63`, 754 lignes : `ZoomDragView`, liste des colonies par `ColonyListMessage` `:224`, classement de prestige `:221`) | rien | C : carte de la colonie |
| `mercenaries` | Ouvre `WindowTownHallMercenary` (`WindowMainPage.java:98,391-394`) ; désactivé 40 min après usage (`:373-377`, `TICKS_FOURTY_MIN` `mc:api/util/constant/Constants.java:34`) | rien | C : mercenaires |
| `bannerPicker` | Ouvre `WindowBannerPicker` (`WindowMainPage.java:105,238-242`, 573 lignes), qui envoie `ColonyFlagChangeMessage` → `colony.setColonyFlag` (`msg/server/colony/ColonyFlagChangeMessage.java:44-46`, `MANAGE_HUTS`) | rien | C : bannière |
| `colorPicker` (liste déroulante) | Les couleurs `ChatFormatting.isColor` (`WindowMainPage.java:140`) ; `TeamColonyColorChangeMessage` → `colony.setColonyColor` (`msg/server/colony/TeamColonyColorChangeMessage.java:67-69`, `MANAGE_HUTS`). Sert à la couleur du nom des citoyens (`mc:api/entity/citizen/AbstractEntityCitizen.java:733,746`) et de la bordure (`mc:core/client/render/worldevent/ColonyBorderRenderer.java:155`) | `hc:colony/CitizenNameplates.java` pose le nom, sans couleur | C : couleur d'équipe (rendu coloré d'une plaque de nom Hytale **[in-game]**) |
| `colonyStylePicker` | Ouvre `WindowSwitchPack` (Structurize), puis `ColonyStructureStyleMessage` → `colony.setStructurePack` (`WindowMainPage.java:102,121-128` ; `msg/server/colony/ColonyStructureStyleMessage.java:39-41`, `MANAGE_HUTS`) | Style par hutte seulement (`hc:app/persistence/BuildingSerializer.java:37`, `hc:app/ColonyFoundation.java:29,97-98`) ; aucun style de colonie | B : manque le style par défaut de la colonie |
| `patreon` | Lien Patreon (`WindowMainPage.java:100,354-364`), visible si la fonction n'est pas débloquée (`:253-307`) | — | hors-port |
| `textureStylePicker` + `resettexture` | Texture des citoyens ; `ColonyTextureStyleMessage` → `colony.setTextureStyle` (`WindowMainPage.java:202-208,247-250` ; `msg/server/colony/ColonyTextureStyleMessage.java:39-41`). **Activé seulement pour le propriétaire avec Patreon** (`WindowMainPage.java:260-267`, requête `auth.minecolonies.com` `:312-350`) | rien | C : apparences de citoyens (`docs/research/citizen-portraits.md`), et Patreon hors-port |
| `nameStylePicker` | Fichier de noms ; `ColonyNameStyleMessage` → `colony.setNameStyle` (`WindowMainPage.java:215-221` ; `msg/server/colony/ColonyNameStyleMessage.java:39-41`). Même verrou Patreon (`:260-267`) | Un seul fichier, `names/default.json` (`hc:citizen/CitizenNames.java:28-31`) | B (plusieurs fichiers de noms manquent) et Patreon hors-port |
| (HyColony) Requêtes, propriétaire, jour | — | `TownHallActionsTab.java:33-37` | écart documenté plus haut |

#### Onglet 2 : Information (`th/WindowInfoPage.java`, `xml/layoutinfo.xml`)

| Élément MC | Ce qu'il fait | Équivalent HyColony | Statut |
|---|---|---|---|
| `intervals` (liste déroulante) | Filtre les événements : hier, semaine, 100 jours, toujours (`th/WindowStatsPage.java:37-45`) ; défaut « toujours » (`WindowInfoPage.java:49,65-93`), filtre `event.getDay() >= jour - intervalle` (`:106-124`). Côté client seulement | `hc:colony/EventLog.java:11` porte un `day` | A |
| `eventsList` | 100 événements au plus (`mc:api/util/constant/ColonyConstants.java:69`, `mc:core/colony/managers/EventDescriptionManager.java:30`). Ligne : action (cause de mort pour `CitizenDiedEvent`), nom du citoyen ou « hutte niveau », position x y z (`WindowInfoPage.java:126-164`) ; `addfakeplayer` caché (`:162`). Producteurs MC : `CitizenSpawnedEvent` (`mc:core/colony/managers/CitizenManager.java:293,633`), `CitizenBornEvent` (`ReproductionManager.java:216`), `CitizenGrownUpEvent` (`EntityAICitizenChild.java:289`), `CitizenDiedEvent` (`EntityCitizen.java:1624`), construit, amélioré, réparé, déconstruit (`AbstractEntityAIStructureWithWorkOrder.java:403-418`) | `EventLog` (`hc:colony/EventLog.java:8-33`, `MAX_ENTRIES = 100`), persisté (`ColonySerializer.java:73,106`). Producteurs : `colonyCreated` (`hc:app/ColonyFoundation.java:93`), `citizenSpawned` (`hc:citizen/CitizenManager.java:155`), `buildingBuilt/Upgraded/Repaired/Deconstructed` (`hc:construction/workorder/BuildCompletion.java:41`), et trois types absents de MC : `buildingPlaced`, `buildingRemoved` (`hc:app/action/HutActions.java:123,155`), `debrisLost` (`hc:job/work/WorkerStock.java:178`) | B : **pas de position** dans `Entry` ; naissance, passage à l'âge adulte et mort sont C (enfants, mort des citoyens) ; aucune vue n'expose le journal |
| `workOrderList` + `plus`/`minus`/`delete` | Ordres filtrés par `shouldShowIn` et triés (`WindowInfoPage.java:171-184`) ; flèches masquées aux extrémités (`:265-278`) ; nom du constructeur (`:281-293`) ; `WorkOrderChangeMessage` (`:204,209,235` → `msg/server/colony/WorkOrderChangeMessage.java:81-91`, `MANAGE_HUTS`) | `pl:ui/townhall/WorkOrderListTab.java:33-98`, `hc:app/action/WorkOrderActions.java:117,127` | fait |

#### Onglet 3 : Permissions (`th/WindowPermissionsPage.java`, `xml/layoutpermissions.xml`)

Trois sous-pages dans un `switch` (`layoutpermissions.xml:4`), tournées par `prevPage`/`nextPage` avec le numéro `pageNum` (`:111-113`, gérés par `mc:core/client/gui/AbstractWindowSkeleton.java:81-87`). Sans `EDIT_PERMISSIONS`, les champs et boutons d'ajout sont désactivés, avec une infobulle d'erreur (`WindowPermissionsPage.java:314-339`).

Ce que le cœur a déjà : les 26 `Action` de MC avec les mêmes bits (`hc:colony/permission/Action.java:5-31` = `mc:api/colony/permissions/Action.java:9-43`), les cinq rangs initiaux (`hc:colony/permission/Permissions.java:13,104`), `setRank` (`:176`, refuse OWNER, le propriétaire et un rang inconnu), `ColonyAdministration.setRank` (`hc:app/action/ColonyAdministration.java:24`, `EDIT_PERMISSIONS`), `Rank.add/remove` (`hc:colony/permission/Rank.java:23,27`), `isColonyManager/isHostile` et leurs setters package-private (`:47-59`). Le port `PlayerDirectory` ne sait pas trouver un joueur par son nom ni donner le nom d'un UUID (`hc:kernel/port/PlayerDirectory.java:9-33`).

| Sous-page / élément MC | Ce qu'il fait (message, règle) | Équivalent HyColony | Statut |
|---|---|---|---|
| **Joueurs** : `addPlayerName` + `addPlayer` | `PermissionsMessage.AddPlayer` : `EDIT_PERMISSIONS`, ajoute au rang NEUTRAL en cherchant le profil par nom (`msg/PermissionsMessage.java:258-260`, `mc:core/colony/permissions/Permissions.java:804-830`) | `setRank` avec un UUID connu ; pas de recherche par nom | B : port nom → UUID manquant |
| `addOnlinePlayer` (👥) + `playerPicker` | Liste des joueurs en ligne pas encore membres ; un clic remplit le champ (`WindowPermissionsPage.java:146-194`, `layoutpermissions.xml:10,47-51`) | `PlayerDirectory.onlineIn` (`PlayerDirectory.java:14`) donne des UUID, pas des noms | B : noms des joueurs en ligne |
| `users` : nom, `rankPicker`, `removePlayer` | Triés par rang (`:285-290`) ; propriétaire : rang en texte, retrait désactivé (`:661-666`) ; les autres : liste déroulante des rangs sauf propriétaire (`:669-685`) → `ChangePlayerRank` : `EDIT_PERMISSIONS` et rang ≠ propriétaire (`msg/PermissionsMessage.java:485-487`) ; `removePlayer` → `RemovePlayer` : hostile + `EDIT_PERMISSIONS`, ou non hostile + `EDIT_PERMISSIONS` + rang gestionnaire, ou soi-même (`:561-568`) | `Permissions.members()` (`:188`) + `setRank` | B : retrait d'un membre manquant ; rang : A |
| `eventsList` « événements de permission » + `addfakeplayer` | 100 refus au plus, du plus récent au plus ancien (`WindowPermissionsPage.java:422-452`), enregistrés quand MC annule une action (`mc:core/colony/permissions/ColonyPermissionEventHandler.java:159-176`, `mc:core/colony/buildings/workerbuildings/BuildingTownHall.java:110`) ; « ajouter » visible si le joueur est connu → `AddPlayerOrFakePlayer` au rang NEUTRAL, `EDIT_PERMISSIONS` (`msg/PermissionsMessage.java:403-405`) | Les refus existent (`hc:app/ColonyProtection.java:50`, `hc:colony/ColonyRefusal.java:14`) mais rien n'est enregistré | B : journal des refus manquant |
| **Rangs** : `addRankName` + `buttonAddRank` | Nom non vide et unique, côté client (`WindowPermissionsPage.java:226-262`) → `AddRank` : `EDIT_PERMISSIONS`, premier id libre ≥ HOSTILE (`msg/PermissionsMessage.java:328-330`, `Permissions.java:1054-1068`) | rien | B : rangs personnalisés |
| `rankButtonList` | Choisit le rang édité, Officier par défaut (`:117,364-420`) | `Permissions.ranks()` (`:184`) | A |
| `rankTypePicker` (liste déroulante) | Gestionnaire, hostile, aucun (`:113-115,341`) → `EditRankType` : `EDIT_PERMISSIONS` (`msg/PermissionsMessage.java:700-720`) | `Rank.setColonyManager/setHostile` package-private (`Rank.java:55-59`) | B : action manquante |
| `rankList` : un bouton marche/arrêt par `Action` | Toutes les actions (`:111,603-638`), désactivé si `!canAlterPermission` (`:626-635`) → `PermissionsMessage.Permission` → `alterPermission` (`msg/PermissionsMessage.java:166`) : un rang propriétaire ne change que par le propriétaire ; il faut `EDIT_PERMISSIONS` ; on ne retire pas à son propre rang `EDIT_PERMISSIONS`, `MANAGE_HUTS`, `ACCESS_HUTS` (`mc:core/colony/permissions/Permissions.java:304-323`). Le rang NEUTRAL n'a jamais `EDIT_PERMISSIONS` ni `TELEPORT_TO_COLONY` (`:641`) | `Rank.add/remove` sans ces règles ; `Permissions.hasPermission` n'a pas l'exception NEUTRAL (`hc:colony/permission/Permissions.java:161`) | B : `canAlterPermission`/`alterPermission` |
| `removeRank` | Actif seulement pour un rang non initial (`:416`) → `RemoveRank` : `EDIT_PERMISSIONS`, ses joueurs passent NEUTRAL (`msg/PermissionsMessage.java:632-634`, `Permissions.java:1078-1090`) | `Rank.isInitial` (`Rank.java:43`) | B |
| **Blocs libres** : `addBlockName` + `addBlock` | Un id de bloc ou une position « x y z » (`:535-565`) → `ChangeFreeToInteractBlockMessage` (`EDIT_PERMISSIONS`, `msg/server/colony/ChangeFreeToInteractBlockMessage.java:105-145`) | rien | C : blocs et positions libres d'accès |
| `blocks` + `removeBlock` | Liste blocs puis positions (`:454-530`) | rien | C (même système) |
| `blockTool` | `GiveToolMessage` donne le sceptre de permission (`:482-485`, `msg/server/colony/building/GiveToolMessage.java:61`) | rien | C : objet sceptre de permission |

#### Onglet 4 : Citoyens (`th/WindowCitizenPage.java`, `xml/layoutcitizens.xml`)

| Élément MC | Ce qu'il fait | Équivalent HyColony | Statut |
|---|---|---|---|
| `citizenList` | Boutons au nom, **triés par nom** (`WindowCitizenPage.java:37,106`) ; le premier est choisi à l'ouverture (`:71-74`) ; le choisi est désactivé (`:200-207`) ; infobulle des compétences « nom : niveau » (`:190-199`) | Liste non cliquable, statut à côté (`pl:ui/townhall/TownHallCitizensTab.java:16`) ; `TownHallViews.of` garde l'ordre des id (`hc:citizen/CitizenManager.java:28,45` : `TreeMap`) ; compétences : `CitizenData.skills()` (`hc:citizen/CitizenData.java:73`) | A (tri, sélection, infobulle) |
| `search` | Filtre sur le nom ou le métier, sans casse (`:81-88,94-107`), 32 caractères (`layoutcitizens.xml:28`) | rien | A |
| `job` (gras) | `layoutcitizens.xml:12`, `:140` | `CitizenData.job()` (`:182`) | A |
| `gender` | Sceau homme/femme (`:131-138`) | `CitizenData.gender()` (`:57`) | A (textures absentes) |
| `health` « pv/max » | `:142` | aucune santé dans le cœur | C : santé |
| `happinessLevel` « n/10 » | `:143` | `PLACEHOLDER_HAPPINESS = 5.5` (`hc:citizen/CitizenManager.java:25`) | C : bonheur |
| `saturation` « n/20 » | `:144` (MC affiche /20 alors que `MAX_SATURATION = 60`, `mc:api/colony/ICitizenData.java:30`) | `CitizenData.saturation()` (`:134`), jamais diminuée (seul `JobXp.java:36` la lit) | B : système de nourriture |
| `entity` (rendu du citoyen) | Montré quand le citoyen dort (`:146-152,282-289`) | rien | C (portraits, `docs/research/citizen-portraits.md`) |
| `recallone` | `RecallSingleCitizenMessage` : `MANAGE_HUTS` ; dernière position = hôtel de ville, crée le corps s'il manque, téléporte, sinon `WARNING_CITIZEN_RECALL_FAILED` (`:160-167` ; `msg/server/colony/citizen/RecallSingleCitizenMessage.java:66-90`) | `HousingActions.recall` rappelle les résidents d'une hutte (`hc:app/action/HousingActions.java:71`) | B : rappel d'un seul citoyen vers l'hôtel de ville |
| `happinessTitle` + `happinessList` | Bonheur global arrondi (`:228-232`) ; par modificateur, la moyenne des facteurs, icône selon > 1, = 1, > 0,75, sinon (`:216-274`) | rien | C : bonheur |

#### Onglet 5 : Statistiques (`th/WindowStatsPage.java`, `xml/layoutstats.xml`)

| Élément MC | Ce qu'il fait | Équivalent HyColony | Statut |
|---|---|---|---|
| `totalCitizens` coloré + infobulle | `WindowStatsPage.java:84-124` | `hc:app/view/TownHallStats.java:24`, `pl:ui/townhall/TownHallStatsTab.java:18,55` | fait |
| `citizen-stats` | Métier « n / m » triés, puis enfants, puis sans-emploi (`:126-221`) | `TownHallStats.of` | fait |
| `intervals` (liste déroulante) | Hier par défaut (`:55`), 4 choix (`:37-45,263-291`) | rien | C : historique des statistiques |
| `stats` | Une ligne par type de statistique, total ou somme sur l'intervalle (`:227-261`) ; `mc:core/colony/managers/StatisticsManager.java`, 62 constantes dans `mc:api/util/constant/StatisticsConstants.java` | rien (écart déjà noté dans `TownHallStats`) | C : statistiques de production |

#### Onglet 6 : Alliances (`th/WindowAlliancePage.java`, `xml/layoutalliance.xml`)

Tout l'onglet est **C : alliances et diplomatie** (`mc:core/colony/managers/ColonyConnectionManager.java`). Éléments :

- `missingconnections` (texte d'aide) ou `activeconnections` selon qu'il existe des événements ou des colonies connectées (`WindowAlliancePage.java:69-78`) ;
- `connectioneventlist` : événements reçus, du plus récent ; `acceptally` visible pour une demande d'alliance pas encore acceptée (`:192-226`) ;
- `directcolonylist` et `indirectcolonylist` : nom, distance en blocs, état ; `requestally` et `startfeud` si neutre, `setneutral` sinon (`:152-187`) ;
- chaque bouton envoie `TriggerConnectionEventMessage` (`:93-122`), qui exige `MANAGE_HUTS` par défaut **et** le revérifie (`msg/server/colony/TriggerConnectionEventMessage.java:50`) avant `triggerConnectionEvent` sur la colonie cible (`:59`).

#### Onglet 7 : Réglages (`th/WindowSettings.java`, `xml/layoutsettings.xml`)

Cinq `BoolSetting` de la colonie, tous à `true` par défaut (`mc:core/colony/buildings/modules/BuildingModules.java:527-531`, clés `BuildingTownHall.java:64-83`). Chaque bouton marche/arrêt (`builder_button_very_small`) envoie `TriggerSettingMessage` avec la position `ZERO` → `colony.getSettings().updateSetting` (`msg/server/colony/building/TriggerSettingMessage.java:88-93`), sans redéfinir la permission : `MANAGE_HUTS`.

| Réglage MC | Effet MC | HyColony | Statut |
|---|---|---|---|
| `movein` (`kidspawn`) | Arrivée des citoyens initiaux, naissances, visiteurs (`CitizenManager.java:233,594`, `ReproductionManager.java:92`, `VisitorManager.java:240`) | Arrivée initiale sans ce test (`hc:citizen/CitizenManager.java:128-143`) | B : réglage absent ; naissances et visiteurs sont C |
| `job` (`autohiring`) | Embauche automatique en mode DEFAULT (`mc:core/util/BuildingUtils.java:114`, `WorkAtHomeBuildingModule.java:57`) | `ColonySettings.autoHiring` (`hc:colony/ColonySettings.java:5,8-14`), appliqué (`hc:job/HiringMode.java:33`) | B : aucune action pour le basculer |
| `housing` (`autohousing`) | Logement automatique en DEFAULT (`LivingBuildingModule.java:70`) | `ColonySettings.autoHousing` (`:6,17-23`), appliqué (`hc:citizen/home/LivingModule.java:51-53`) | B : même manque |
| `entermessages` (`enterleave`) | Messages d'entrée et de sortie pour un non-gestionnaire (`mc:core/colony/Colony.java:1632,1653`) | rien | C : messages d'entrée/sortie |
| `tape` | Ruban de chantier (`mc:core/entity/ai/workers/util/ConstructionTapeHelper.java:79`) | rien | C : ruban de chantier |

#### Sous-fenêtres

| Fenêtre MC | Contenu et règle | HyColony | Statut |
|---|---|---|---|
| `WindowTownHallNameEntry` (`xml/windowtownhallnameentry.xml`) | Fenêtre à voile : champ de 25 caractères (`:6`), Terminé, Annuler, puis rouvre l'hôtel de ville (`th/WindowTownHallNameEntry.java:43-62`). `ColonyView.setName` envoie `TownHallRenameMessage` (`mc:core/colony/ColonyView.java:995-999`) ; le serveur **tronque** à 24 un nom de plus de 25 (`msg/server/colony/TownHallRenameMessage.java:17-18,56-57`) | Champ en ligne ; `ColonyAdministration.rename` **refuse** au-delà de `MAX_NAME_LENGTH = 32` avec un message (`hc:app/action/ColonyAdministration.java:52-77`, `hc:app/ColonyManager.java:31`) ; 32 est la limite de la fondation MC (`xml/windowcolonymanagement.xml:12`) | B : longueur et troncature du renommage diffèrent de MC |
| `WindowTownHallMercenary` (`xml/windowtownhallmercenary.xml`) | Texte d'histoire, `min(citoyens / 10 + 3, 9)` portraits (`th/WindowTownHallMercenary.java:41-58`), Engager → `HireMercenaryMessage` (`MANAGE_HUTS`) → `EntityMercenary.spawnMercenariesInColony` : 40 min de délai revérifiées, `citoyens / 10 + 3` soldats et un chef (`mc:core/entity/mobs/EntityMercenary.java:485-518`) | rien | C : mercenaires |
| `WindowColonyMap`, `WindowBannerPicker` | Voir l'onglet Actions | rien | C |
| `WindowTownHallColonyManage`, `…ColonyReactivate`, `…DeleteAbandonColony`, `…CantCreateColony` | Pas atteintes depuis les onglets : ouvertes par le serveur à la pose de l'hôtel de ville (`msg/client/OpenColonyFoundingCovenantMessage.java:40`, `OpenReactivateColonyMessage.java:40`, `OpenDeleteAbandonColonyMessage.java:54`, `OpenCantFoundColonyWarningMessage.java:50`) | Fondation : `pl:ui/FoundColonyPage.java` | hors du périmètre des onglets |

#### Textures MC par onglet

Déjà copiées dans `ui:Mc/` : `townhall_book`, `bookmark_ribbon_01/02/04/05`, `bookmark_short_ribbon_01/02/04/05`, `red_wax_home/work_orders/citizens/stats`, `builder_button_medium_large`, `builder_button_mini`, `edit`. Toutes celles qui manquent existent dans `tex/` (vérifié, taille d'origine entre parenthèses) :

- **cadre** : `bookmark_short_ribbon_03` (31 × 15), `_06` (31 × 14, partagé par Alliances et Réglages, `windowtownhall.xml:10-11`), `bookmark_ribbon_03`, `_06` (204 × 17), `bookmark_medium_ribbon_01` à `_06` (104 × 14, rubans de survol, inutiles tant que l'écart de l'infobulle reste), `red_wax_permissions`, `red_wax_information` (sceau des **Alliances**, `windowtownhall.xml:67-68`), `red_wax_settings` (17 × 17) ;
- **Actions** : `builderhut/builder_button_medium_large_build`, `_map`, `_merc`, `_banner` (129 × 17), `builder_button_medium` (86 × 17), `builder_button_medium_disabled` ; `patreonwidget` est hors-port ;
- **Information**, **Statistiques** : rien de plus (`builder_button_medium_large` pour la liste déroulante, `builder_button_mini` pour les flèches) ;
- **Permissions** : `builder_button_medium`, `builder_button_very_small` (29 × 16), `turn_page_left`, `turn_page_right` (18 × 10), `button_x` (14 × 15), `scepterpermission` (14 × 15) ;
- **Citoyens** : `builder_button_medium` (rappel), `builder_button_medium_large_disabled` (citoyen choisi), `citizen/colonist_wax_male_smaller` et `_female_smaller` (30 × 30, `mc:api/util/constant/WindowConstants.java:887,892`), `citizen/icons` (87 × 9, cœur de bonheur), `happy_icon`, `satisfied_icon`, `unsatisfied_icon`, `unhappy_icon` (16 × 16, `WindowConstants.java:599-602`). Les icônes de santé et de faim viennent de `minecraft:textures/gui/icons.png` (`layoutcitizens.xml:19,21`), une texture de Mojang absente de `sources/` : à ne pas copier ;
- **Alliances** : `builder_button_medium`, `builder_button_medium_small` (71 × 17), `builder_button_small` (64 × 17) ;
- **Réglages** : `builder_button_very_small` ;
- **Mercenaires** : `citizen/colonist_paper`, `colonist_text_decor_down`, `colonist_wax_male`, `textures/item/moneygold`, plus `minecraft:textures/item/diamond_sword` (Mojang).

#### Widgets Hytale que ces éléments demandent

Nos `.ui` utilisent aujourd'hui `TextField`, `ItemGrid`, `ItemIcon`, `ProgressBar` et `TopScrolling`, et le plugin lie déjà `ValueChanged` sur un champ de recherche (`pl:ui/ItemPickerPage.java:61`). Aucun n'utilise `DropdownBox` ni `CheckBox`.

- **Liste déroulante** (couleur d'équipe, intervalles, rang de chaque joueur, type de rang, styles) : `$C.@DropdownBox` et `@DefaultDropdownBoxStyle` (`v:Common.ui:494-540`). Le style prend des textures (`DefaultBackground`, `HoveredBackground`, `PressedBackground`, `PanelBackground`), donc celle de MC peut s'y mettre **[in-game]**. Le serveur pose `.Entries` (une liste de `DropdownEntryInfo(LocalizableString label, String value, LocalizableString tooltip)`, `srv:server/core/ui/DropdownEntryInfo.java:8`) et `.Value`, et lit le choix par `ValueChanged` avec `@… = "#X.Value"`. Exemples : une liste par ligne d'une liste, comme le `rankPicker` de MC, dans `srv:builtin/blockspawner/ui/BlockSpawnerSettingsPage.java:177-199` avec `v:Pages/BlockSpawner/BlockSpawnerSpawnerEntryRow.ui:28,66` ; une liste simple dans `srv:builtin/adventure/teleporter/page/TeleporterSettingsPage.java:92-103` (libellé traduit par `LocalizableString.fromMessageId`, `:93`) ; une ligne type dans `v:Pages/Fields/DropdownRow.ui:13`.
- **Case à cocher** : `$C.@CheckBox` et `@CheckBoxWithLabel` (`v:Common.ui:407-450`), lus par `ValueChanged` sur `#… #CheckBox` (`srv:builtin/buildertools/objimport/ObjImportPage.java:148`, `v:Pages/ObjImportPage.ui:148`, ligne type `v:Pages/Fields/CheckboxRow.ui:13`). MC n'en a pas besoin : ses réglages et ses déclencheurs de rang sont des **boutons texte marche/arrêt** (`xml/layoutsettings.xml:5`, `xml/layoutpermissions.xml:85-87`), qu'un `TextButton` dont on change le texte reproduit.
- **Pages tournées** (sous-pages Permissions) : aucun widget `switch` n'est connu dans les `.ui` vanilla. Le seul couple précédent/suivant vanilla est dans un HUD (`ActionButton #PreviousPage`, `#NextPage`, `v:Hud/ToolsLegends/ToolsLegendsCommon.ui:112,128`). Le motif déjà employé pour les onglets (des `Group` dont un seul est `Visible`, § 0) suffit à tourner des pages.
- **Champ de recherche** (Citoyens) : déjà en place, même motif que `srv:builtin/teleport/WarpListPage.java:68`.
- **Rendu d'un citoyen** (`entityicon`) : pas de widget connu ; voir `docs/research/citizen-portraits.md`.

---

## 2. Fenêtre de hutte (constructeur en particulier)

### MineColonies

- **Onglets latéraux** : `client/gui/AbstractBuildingWindow.java:55-83`. Un onglet « principal » (`textures/gui/modules/main.png`), puis un par module dont `isPageVisible()` est vrai, **dans l'ordre d'enregistrement des modules**. Le clic joue `BOOK_PAGE_TURN`.
- **Modules de la hutte du constructeur** (`apiimp/initializer/ModBuildingsInitializer.java:95-107`, dans l'ordre) :
  1. `BUILDER_WORK` : `WorkerBuildingModuleView`, qui n'a **pas** d'onglet (`isPageVisible` = false, `moduleviews/WorkerBuildingModuleView.java:124-127`) ;
  2. `BUILDER_CRAFT` : recettes **[futur]** ;
  3. `BUILDING_RESOURCES` : « Liste des ressources », icône `inventory.png` (`BuildingResourcesModuleView.java:125-140`) ;
  4. `BUILDER_SETTINGS` : « Réglages » (`SettingsModuleView.java:99-114`) ;
  5. `WORKORDER_VIEW` : « Ordres de travail », icône `info.png` (`WorkOrderListModuleView.java:28-50`) ;
  6. `MIN_STOCK` : stock minimum **[futur]** ;
  7. `STATS_MODULE` : statistiques **[futur]**.
  
  Le wiki montre bien, dans cet ordre, Principal, Recettes, Ressources, Réglages, Ordres de travail et Stock minimum.
- **Page principale** : `gui/windowhutworkerplaceholder.xml`, qui inclut `layouthutpageactions.xml`, qui inclut `layouthutpageactionsmin.xml`, qui inclut `layouthutpageactionsminwoinv.xml`. De haut en bas :
  - titre « Nom de la hutte + niveau » (`AbstractBuildingMainWindow.onOpened`) et crayon `editName` (renommer la hutte) ;
  - « Travailleurs assignés » : liste `workers`, une ligne « Métier : Nom » (`AbstractWindowWorkerModuleBuilding.onOpened`) ;
  - bouton `hire` (« Gérer les travailleurs », ouvre `WindowHireWorker`) et bouton `recall` ;
  - **un seul** bouton `build` (« Construire/Réparer »), qui ouvre `WindowBuildBuilding`. Quand un ordre existe, son libellé devient « Annuler construction / amélioration / réparation / déconstruction » et le clic annule (`AbstractBuildingMainWindow.buildClicked`, `updateButtonBuild`) ;
  - priorité de ramassage `prioValue` avec `-`/`+` (0 = jamais, 1..10) et `forcePickup` **[futur : coursiers]** ;
  - en bas : `inventory` (conteneur de la hutte), `allinventory` (somme hutte + étagères) et `info` (guide).
- **`WindowBuildBuilding`** (`gui/windowbuildbuilding.xml`, `client/gui/WindowBuildBuilding.java`) :
  - en haut : style `<` [liste] `>`, et liste déroulante **du constructeur** (« Constructeur : » + constructeurs de la colonie triés par distance, `updateBuilders`) ;
  - au centre : **liste des ressources du niveau suivant** (icône, nom, quantité), calculée depuis le plan ;
  - en bas : `repair`, `build` (« Construire » au niveau 0, « Améliorer » sinon, masqué au niveau max), `deconstruct` et `pickup` (visible au niveau 0 ou si la hutte est déconstruite ; au niveau 0 `repair` et `deconstruct` sont masqués). Si la hutte est déconstruite, `repair` devient « Construire ».
- **`WindowHireWorker`** (`gui/windowhireworker.xml`) :
  - une ligne par citoyen : nom en bleu (métier ou « sans emploi »), distance du logement, et compétences sur une ligne, **primaire puis secondaire en tête et colorées** (`WindowHireWorker.java:455-470`) ;
  - boutons `done` (embaucher) ou `fire`, `pause` et `restart` ;
  - tri : employés d'ici, puis sans emploi, puis autre métier, puis non assignables, puis distance (`getCitizenPriority`, `updateCitizens`) ;
  - mode d'embauche `mode` et bascule « montrer les employés ».

### HyColony (`Building.ui`, `BuildingPage.java`, `BuildingViews.java`)

Une seule page. De haut en bas :

- type, niveau/max, état ;
- ligne d'ordre (« type niveau, constructeur, % ») ;
- **quatre boutons d'ordre en ligne** (Construire, Améliorer, Réparer, Retirer) + Annuler ;
- rangée Style (bouton qui fait défiler) ;
- bouton mode d'embauche ;
- liste Travailleurs (Renvoyer) et liste Embauchables (Embaucher) ;
- boutons Ressources (autre fenêtre), Stockage et Ramasser.

### Écarts

Ce qui manque chez nous :

- **[actuel]** Onglets de modules : Ressources, Réglages, Ordres de travail (voir §§ 3 et 6).
- **[actuel]** Un bouton unique « Options de construction » ou « Annuler … », au lieu de quatre boutons plus Annuler, avec une sous-vue d'options : style `<` `>`, choix du constructeur, coût du niveau suivant, et Réparer/Améliorer/Déconstruire/Ramasser.
- **[actuel]** Titre « Nom + niveau » et renommage de hutte : `Building.customName()` existe dans le cœur, mais aucune action ne l'écrit hors du chargement.
- **[actuel]** Lignes de travailleurs « Métier : Nom ».
- **[actuel]** Fenêtre d'embauche : compétences par candidat (primaire et secondaire en tête et colorées), distance du logement, tri MC.
- **[actuel]** Onglet Réglages du constructeur : mode Auto/Manuel (`BuilderSettingsModule.Mode` existe dans le cœur, sans aucune interface).
- **[futur]** Rappel (`recall`), priorité de ramassage et ramassage forcé (coursiers), total des inventaires (étagères), guide, pause/redémarrage, recettes, stock minimum, statistiques, stratégie de construction, cisailles, bloc de remplissage.

Ce qu'on montre et que MC ne montre pas :

- la ligne d'état (construite, non construite, déconstruite) ;
- la ligne d'ordre avec son % sur la page principale (chez MC, il est dans l'onglet Ressources) ;
- les listes d'embauche **en ligne** (MC ouvre une fenêtre à part) ;
- les quatre boutons d'ordre visibles d'emblée.

### Proposition

1. `Building.ui` : `#TabButtons` + des groupes `#MainTab`, `#ResourcesTab`, `#SettingsTab` et `#OrdersTab`. Les trois derniers ne s'affichent que si le module existe (`BuildingResourcesModule`, `BuilderSettingsModule`). L'ordre est celui de MC : Principal, Ressources, Réglages, Ordres de travail.
2. Onglet Principal :
   - titre « Nom niveau » + crayon ;
   - travailleurs « Métier : Nom » ;
   - « Gérer les travailleurs » (ouvre un groupe ou une page d'embauche, à la manière de `WindowHireWorker`) ;
   - un bouton `#BuildButton` à libellé variable, une clé par variante (`…buildOptions`, `…cancelBuild`, `…cancelUpgrade`, `…cancelRepair`, `…cancelRemove`) ;
   - en bas : Stockage et Ramasser.
3. Sous-vue « Options de construction » (un groupe de la même page) :
   - style `<` / libellé / `>` ;
   - constructeur (bouton qui fait défiler les constructeurs, par distance) ;
   - coût du niveau suivant (liste `ResourceRow.ui` sans bouton) ;
   - Réparer / Améliorer / Déconstruire / Ramasser, selon les règles de visibilité ci-dessus.

---

## 3. Ressources du constructeur

### MineColonies (`gui/layouthuts/layoutbuilderres.xml`, `client/gui/modules/building/WindowBuilderResModule.java`)

De haut en bas :

- en-tête `desc` (« Liste des ressources ») ;
- `constructionName` (nom d'affichage de l'ordre) ;
- `stepprogress` (« étape X/Y », `progress.step` avec `getCurrentStage()` et `getTotalStages()`) ;
- `progress` (« livré X % / avancement Y % », `progress.res` : `supplied/total` et `moduleView.getProgress()`) ;
- liste `resources`, dont chaque ligne de 30 px contient :
  - l'icône 16 px et le nom ;
  - `resourceMissing`, le manque chez le joueur (`amountPlayer + available - amount`, affiché seulement s'il est négatif) ;
  - `available / amount` ;
  - le bouton ↥ `resourceAdd`, désactivé en DONT_HAVE et NOT_NEEDED.
  
  **Toute la ligne** (nom, manque, compte) prend la couleur du statut : rouge, orange, vert foncé ou noir.
- **Tri** : `BuildingBuilderResource.ResourceComparator` sans ordre explicite. On trie par statut décroissant (HAVE_ENOUGH, NEED_MORE, DONT_HAVE, IN_DELIVERY, NOT_NEEDED), puis par nom (`BuildingBuilderResource.java:194-240`).
- Rafraîchi toutes les 20 images.

### HyColony (`BuilderResources.ui`, `BuilderResourcesPage.java`, `BuilderResourcesViews.java`)

- `#Progress` : « X % – étape » ;
- liste : icône, nom, `available / needed` coloré, bouton Ajouter (masqué si NOT_NEEDED ou si le joueur n'a rien) ;
- bouton « retour à la hutte ».

### Écarts

Ce qui manque chez nous, tout en **[actuel]** :

- le nom de l'ordre ;
- l'étape « X/Y » (nous n'affichons que le nom de l'étape) ;
- le « livré % » séparé de l'avancement % ;
- le manque chez le joueur (nombre négatif) ;
- la couleur sur toute la ligne (nous ne colorons que le compte) ;
- le **tri MC** : nous gardons l'ordre d'insertion de `NeededResources.remaining()`.
- **[futur]** IN_DELIVERY (coursiers).

Ce qu'on montre et que MC ne montre pas : rien de notable. Le bouton « retour » disparaît avec les onglets.

### Proposition

Onglet `#ResourcesTab` de `Building.ui` :

- trois labels d'en-tête ;
- `ResourceRow.ui` avec un label `#Missing` en plus ;
- `Style.TextColor` posé sur `#Name`, `#Missing` et `#Count` ;
- tri fait **dans le cœur** (`BuilderResourcesViews`), car c'est une règle MC, pas de la présentation.

---

## 4. Fenêtre du citoyen

### MineColonies

- **Navigation** : `gui/citizen/nav.xml`, `client/gui/citizen/AbstractWindowCitizen.java`. Onglets à gauche, dans l'ordre :
  1. Principal (`info.png`) ;
  2. Requêtes ;
  3. Inventaire (qui **ouvre le vrai conteneur** du citoyen, `OpenInventoryMessage`) ;
  4. Bonheur ;
  5. Famille ;
  6. Métier, visible seulement si le citoyen a un lieu de travail autre que la bibliothèque ;
  7. Debug, en mode debug seulement.
- **Principal** (`gui/citizen/main.xml`, `MainWindowCitizen.java`) :
  - nom centré ;
  - barres de santé, de saturation et de bonheur ;
  - **compétences dans l'ordre fixe** Athletics, Dexterity, Strength, Agility, Stamina, Mana, Adaptability, Focus, Creativity, Knowledge, Intelligence : libellé, petite icône, **niveau seul** (ni XP ni barre, `CitizenWindowUtils.createSkillContent`), et boutons +/- en créatif ;
  - icône de statut (`getVisibleStatus`, avec infobulle) et sceau de genre.
- **Métier** (`gui/citizen/job.xml`, `CitizenWindowUtils.updateJobPage`) :
  - « Métier : X » et une explication ;
  - primaire « (100% XP) » avec sa complémentaire « (10% XP) » et son adverse « (-10% XP) » ;
  - secondaire « (50% XP) » avec sa complémentaire « (5% XP) » et son adverse « (-5% XP) ».
  
  Les pourcentages sont `PRIMARY_DEPENDENCY_SHARE` et `SECONDARY_DEPENDENCY_SHARE`, déjà dans notre `core/.../job/JobXp.java:9-10` (0.10 et 0.05).
- **Requêtes** (`gui/citizen/requests.xml` + `RequestTreeWindowModule`, `layouthuts/layoutrequeststree.xml`) :
  - les requêtes ouvertes du citoyen **et celles de sa hutte** (citoyen −1) (`RequestWindowCitizen.getOpenRequests`) ;
  - affichées en **arbre**, les enfants décalés de 2 px par profondeur ;
  - chaque ligne : icône de l'objet (qui alterne entre les piles possibles), description courte, demandeur, `fulfill`, `cancel` (racines seulement) et `detail` ;
  - `fulfill` n'est visible que si le joueur possède l'objet (ou en créatif) et si la requête appartient à cette hutte (`isFulfillable`).
- **Bonheur**, **Famille** : **[futur]**.

### HyColony (`Citizen.ui`, `CitizenPage.java`, `CitizenViews.java`)

Une page de 860 px :

- nom, métier, lieu de travail, activité (« en attente de … » ou l'état, plus la ligne d'activité du métier) ;
- deux colonnes : **Compétences** (icône, nom, XP x/y, **barre de progression**, compétences du métier en tête et surlignées) et **Inventaire** (liste en lecture seule) ;
- **Requêtes** (liste plate avec bouton Fournir).

### Écarts

Ce qui manque chez nous :

- **[actuel]** Onglets Principal, Requêtes, Inventaire et Métier.
- **[actuel]** Onglet Métier : primaire, secondaire, complémentaires et adverses avec leurs parts d'XP. Toutes les données sont dans le cœur (`Skill.adverse()`, `JobXp`).
- **[actuel]** Requêtes en arbre, requêtes de la hutte (citoyen −1), bouton Annuler sur les racines et icône d'objet. Le cœur a `RequestManager.updateState(token, CANCELLED)`, mais pas d'action joueur `RequestActions.cancel`.
- **[actuel]** Ordre fixe MC des compétences. Chez nous, les compétences du métier passent en tête : c'est l'ordre de `WindowHireWorker`, pas celui de `main.xml`.
- **[futur]** Santé, saturation, bonheur, famille, icône de statut visible, boutons +/- en créatif.

Ce qu'on montre et que MC ne montre pas :

- XP et barre de progression par compétence (MC n'affiche que le niveau) ;
- surlignage des compétences du métier ;
- ligne d'activité détaillée ;
- inventaire en liste lecture seule (MC ouvre un conteneur).

Ces ajouts sont utiles. Soit on les garde en `Deviation from MC` documenté, soit on les retire pour une fidélité stricte. **À trancher par l'utilisateur.**

### Proposition

`Citizen.ui` avec `#TabButtons` : Principal, Requêtes, Inventaire, et Métier si employé.

- **Principal** : nom, métier, activité, puis compétences dans l'ordre MC (on garde `SkillRow.ui` avec barre si l'écart est accepté, sinon icône + niveau seulement).
- **Requêtes** : `RequestRow.ui` + `ItemIcon`, indentation par `Padding.Left` selon la profondeur, Fournir et Annuler.
- **Inventaire** : on garde la liste. Ouvrir le vrai conteneur de l'inventaire du PNJ est un autre chantier (**[in-game]**, non vérifié ici).
- **Métier** : six lignes « icône + compétence (±n % XP) ».

---

## 5. Requêtes (presse-papiers)

### MineColonies (`gui/windowclipboard.xml`, `client/gui/WindowClipBoard.java`)

- Titre « Requêtes » et bouton `important` (« ! », vert ou rouge, qui masque ou montre les requêtes de stock minimum et asynchrones ; `toggleImportant`).
- Contenu : l'arbre `RequestTreeWindowModule` des **racines** des requêtes assignées au résolveur joueur et au résolveur de réessai, triées par distance du demandeur au joueur puis par id.
- **Pas de bouton Fournir** : `ClipboardRequestTreeWindowModule` ne redéfinit pas `isFulfillable`, qui vaut `false` par défaut (`RequestTreeWindowModule.java:274-277`). Il reste `cancel` (racines) et `detail`.
- Détail (`gui/windowrequestdetail.xml`) : icône 24 px, demandeur, `targetLocation`, `resolver`, description longue, puis Retour, Fournir et Annuler.

### HyColony (`Requests.ui`, `RequestsPage.java`, `RequestViews.java`)

Liste plate des racines, avec la même sélection et le même tri que MC (`RequestViews.of` cite `WindowClipBoard`). Chaque ligne : description, « demandeur, vous en avez n », bouton **Fournir** si n > 0.

### Écarts

Ce qui manque chez nous :

- **[actuel]** Icône d'objet, arbre des enfants, Annuler, bouton ou groupe de détail (demandeur, position, résolveur).
- **[futur]** Le filtre « ! » (stock minimum et requêtes asynchrones).

Ce qu'on montre et que MC ne montre pas : le bouton **Fournir** dans le presse-papiers (MC ne le propose que dans l'onglet Requêtes du citoyen et dans le détail).

### Proposition

- Ajouter `ItemIcon #Icon`, l'indentation par profondeur et Annuler (`RequestActions.cancel` dans le cœur, avec contrôle de permission).
- Fournir : soit on le garde avec un `Deviation from MC: fulfil from the list (no clipboard item, no detail window)`, soit on le déplace dans un groupe « détail » ouvert par un bouton de ligne, comme MC.

---

## 6. Ordres de travail

### MineColonies

- **Hôtel de ville, onglet Information** : décrit au § 1 (↥ ↧ X, nom d'affichage, constructeur). **Pas de numéro de priorité affiché.**
- **Hutte du constructeur, onglet Ordres de travail** (`gui/layouthuts/layoutworkorders.xml`, `client/gui/modules/building/WorkOrderModuleWindow.java`) :
  - filtre : en mode Auto, les ordres réclamés par cette hutte ; en mode Manuel, aussi les non réclamés. Il faut `shouldShowIn` et `canBuildIgnoringDistance` ;
  - tri : l'ordre courant, puis ceux réclamés ici, puis les autres, puis `WORK_ORDER_COMPARATOR` ;
  - ligne : nom, **distance** (bleu), bouton `manage` libellé « Annuler » si l'ordre est réclamé ici, « Sélectionner » en mode manuel (désactivé avec infobulle : pas de travailleur, déjà réclamé, niveau insuffisant) ;
  - l'ordre courant a un **cadre vert** ;
  - texte vide : `townhall.workorders.empty`.

### HyColony (`WorkOrders.ui`, `OrderRow.ui`, `WorkOrdersPage.java`, `WorkOrderViews.java`)

Fenêtre ouverte depuis l'hôtel de ville. Chaque ligne : « type bâtiment niveau », « priorité n, constructeur », Monter, Descendre, Supprimer (gestionnaires seulement).

### Écarts

Ce qui manque chez nous :

- **[actuel]** Placement dans l'onglet Information de l'hôtel de ville.
- **[actuel]** Masquage de ↥ sur la première ligne et de ↧ sur la dernière.
- **[actuel]** Onglet Ordres de la hutte du constructeur : distance, ordre courant mis en valeur, Annuler et Sélectionner en mode manuel. Le cœur a déjà le mode `MANUAL` (`WorkOrderAssignment`), mais aucune action de sélection.

Ce qu'on montre et que MC ne montre pas : le numéro de priorité.

### Proposition

- Déplacer `WorkOrders.ui` dans l'onglet Information de `TownHall.ui`.
- Masquer ↥ et ↧ aux extrémités.
- Retirer « priorité n » de la ligne d'info (garder le constructeur).
- Ajouter `#OrdersTab` à la hutte, avec des lignes « nom / distance / bouton » et un fond coloré (`Background`) pour l'ordre courant.

---

## 7. Ajustements proposés, par priorité (à valider)

| # | Ajustement | Systèmes | Effort |
|---|---|---|---|
| 1 | Motif d'onglets vanilla (`#TabButtons` + groupes `Visible`, bouton actif `Disabled`) appliqué à la hutte : Principal, Ressources, Réglages, Ordres. `BuilderResources.ui` devient un onglet. Extraire un rendu par onglet (taille de `BuildingPage`). | actuel | M |
| 2 | Page principale de la hutte : un bouton « Options de construction », ou « Annuler … » selon l'ordre (une clé par variante), remplace les 4 boutons + Annuler. Sous-vue d'options : style `<` `>`, Réparer, Améliorer ou Construire, Déconstruire, Ramasser, avec les règles de visibilité de `WindowBuildBuilding`. | actuel | M |
| 3 | Ressources : en-tête MC (nom de l'ordre, étape X/Y, livré % / avancement %), colonne du manque joueur, couleur sur toute la ligne, **tri `ResourceComparator` dans le cœur**. | actuel | S |
| 4 | Hôtel de ville en onglets : Actions, Information (ordres de travail), Citoyens, Statistiques (population, effectif par métier, sans-emploi, enfants). Le bouton Requêtes reste dans Actions (écart documenté). | actuel | M |
| 5 | Requêtes (presse-papiers et onglet du citoyen) : icône d'objet, arbre des enfants indenté, bouton **Annuler** sur les racines (nouvelle action cœur `RequestActions.cancel`). Décider du sort de Fournir dans le presse-papiers (retrait ou écart documenté). | actuel | M |
| 6 | Citoyen en onglets : Principal, Requêtes, Inventaire, Métier (si employé). Onglet Métier avec primaire, secondaire, complémentaires et adverses (100/50 %, ±10/±5 %). | actuel | M |
| 7 | Onglet Réglages du constructeur : mode Auto/Manuel (`BuilderSettingsModule`), avec une action cœur `setBuilderMode` qui vérifie MANAGE_HUTS. | actuel | S |
| 8 | Onglet Ordres de la hutte : ordres réclamés (plus les non réclamés en manuel), distance, ordre courant en vert, Annuler, Sélectionner (nouvelle action cœur de sélection manuelle). | actuel | M |
| 9 | Liste d'ordres de l'hôtel de ville : ↥ et ↧ masqués aux extrémités, numéro de priorité retiré. | actuel | S |
| 10 | Gestion des travailleurs à la MC : lignes « Métier : Nom » sur la page principale, bouton « Gérer les travailleurs » qui ouvre une vue d'embauche (compétences par candidat, primaire et secondaire en tête et colorées, tri MC). | actuel | M |
| 11 | Titre de hutte « Nom niveau » + renommage (`Building.setCustomName` existe, sans action joueur). | actuel | S |
| 12 | Onglet Permissions : membres, rang, changement de rang (`ColonyAdministration.setRank` existe). | actuel | M |
| 13 | Citoyens de l'hôtel de ville : tri par nom, filtre `TextField`, ligne cliquable vers la fenêtre du citoyen. | actuel | S |
| 14 | Options de construction : choix du constructeur (défilement par distance) et coût du niveau suivant (liste d'objets du plan). | actuel | L |
| 15 | Compétences du citoyen : trancher entre l'ordre fixe MC avec niveau seul, et notre version XP + barre + métier en tête (écart). | actuel | S |
| — | Bonheur, santé et saturation, famille, journal d'événements, rappel, priorité de ramassage, recettes, stock minimum, statistiques de production, alliances, carte, bannière, mercenaires, filtre « ! » du presse-papiers. | futur | — |

Incertitudes :

- **[in-game]** Rendu d'un `@SmallSecondaryTextButton` `Disabled` comme onglet actif.
- **[in-game]** `TabNavigation` et la donnée de `SelectedTabChanged` ne sont pas vérifiés.
- **[in-game]** Ouverture du vrai conteneur d'inventaire d'un PNJ : non étudiée ici.
