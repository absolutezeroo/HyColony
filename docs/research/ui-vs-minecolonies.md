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

**Remplacé par le § 8 (inventaire complet du 2026-10-01) ; gardé pour l'historique.**

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

**Remplacé par le § 8.5 (inventaire complet du 2026-10-01) ; gardé pour l'historique.**

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

**Pour l'onglet de la hutte du constructeur, remplacé par le § 8.5 (2026-10-01) ; l'hôtel de ville est au § 1.**

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

---

## 8. Fenêtres de huttes : inventaire complet, 2026-10-01

Inventaire de tout ce que montrent les fenêtres des huttes de MC (hors hôtel de ville, déjà fait), élément par élément, avant le sous-projet « huttes à l'apparence et au contenu de MC ». Même méthode qu'au § 1 « Inventaire complet » : XML, classe Java (textes posés, listes, infobulles, règles d'activation), message serveur de chaque bouton, puis ce que HyColony montre. Rien n'est conçu ici.

Abréviations (en plus de celles du § 1) :

- `gui/` = `sources/minecolonies/src/main/java/com/minecolonies/core/client/gui/` ; `mod/` = `gui/modules/building/` ;
- `xml/` = `sources/minecolonies/src/main/resources/assets/minecolonies/gui/` ; `lh/` = `xml/layouthuts/` ;
- `mv/` = `mc:core/colony/buildings/moduleviews/` ; `bm:` = `mc:core/colony/buildings/modules/BuildingModules.java` ; `mbi:` = `mc:apiimp/initializer/ModBuildingsInitializer.java` ;
- `lang:` = `sources/minecolonies/src/main/resources/assets/minecolonies/lang/manual_en_us.json` (seul fichier de langue de la copie) ;
- `bui:` = `sources/blockui/src/main/java/com/ldtteam/blockui/` ;
- « ×2 » : position et taille MC doublées (CLAUDE.md § 7), notées `(x,y) l×h`.

Statuts : **fait** ; **A** = le cœur a la donnée et le comportement, seul le rendu MC manque ; **B** = le système existe mais la vue n'a pas tout ce que MC montre (le manque est dit) ; **C** = il faut un système absent (nommé), à repousser ; **hors-port** = propre à Minecraft.

**Règle commune des messages.** Tous les messages envoyés par ces fenêtres héritent d'`AbstractBuildingServerMessage` ou d'`AbstractColonyServerMessage` sans redéfinir `permissionNeeded()`, donc exigent `MANAGE_HUTS` (`msg/AbstractColonyServerMessage.java:61-63`). Vérifié pour : `BuildRequestMessage`, `HutRenameMessage`, `RecallCitizenMessage`, `RecallCitizenHutMessage`, `ChangeDeliveryPriorityMessage`, `ForcePickupMessage`, `OpenInventoryMessage`, `HireFireMessage`, `BuildingHiringModeMessage`, `PauseCitizenMessage`, `RestartCitizenMessage`, `AssignUnassignMessage`, `TransferItemsRequestMessage`, `MarkBuildingDirtyMessage`, `TriggerSettingMessage`, `ToggleRecipeMessage`, `ChangeRecipePriorityMessage`, `AddRemoveRecipeMessage`, `AssignFieldMessage`, `AssignmentModeMessage`, `UpgradeWarehouseMessage`, `SortBuildingMessage`, `Add/RemoveMinimumStock…Message`, `CourierHiringModeMessage`, `BuildPickUpMessage`, `WorkOrderChangeMessage`, `BuilderSelectWorkOrderMessage` (déclarations `extends` lues dans `msg/`). En plus, chaque ouverture de fenêtre et chaque clic enregistré envoient `OpenGuiWindowTriggerMessage` et `ClickGuiButtonTriggerMessage` (`gui/AbstractWindowSkeleton.java:96,196`), qui ne servent qu'aux succès et quêtes : hors-port.

### 8.1 Cadre commun d'une hutte

#### Le papier et la page principale (`lh/layouthutpageactionsminwoinv.xml`, `lh/layouthutpageactionsmin.xml`, `gui/AbstractBuildingMainWindow.java`)

Les fenêtres font 190 × 244 (×2 : 380 × 488). Trois niveaux de mise en page s'incluent : `minwoinv` (papier, titre, renommer, construire, aide) ⊂ `min` (+ inventaire, inventaire total) ⊂ `layouthutpageactions.xml` (+ travailleurs, embauche, rappel, priorité de ramassage).

| Élément MC (id) | XML (pos, taille, texture, libellé) | ×2 | Java (contenu, règle, message) |
|---|---|---|---|
| papier | `minwoinv.xml:4`, 190×244, `builderhut/builder_paper` (texture 192×240, étirée : BlockUI dessine l'image à la taille du nœud, `bui:controls/Image.java:184-198`) | (0,0) 380×488 | — |
| bandeau du titre | `minwoinv.xml:6-8` : `builder_sketch_left` (24,12) 6×15, `_center` (30,12) 130×15 (texture 154×15), `_right` (160,12) 6×15 | (48,24) 12×30 ; (60,24) 260×30 ; (320,24) 12×30 | — |
| `name` | `minwoinv.xml:9`, (30,14) 128×11, centré, rouge (`red` = #FF0000, `bui:Color.java:32`) | (60,28) 256×22 | `getBuildingDisplayName()` + « » + niveau (`AbstractBuildingMainWindow.java:171-176`) ; le nom d'affichage est le nom personnalisé s'il existe, sinon la clé du type (`mc:api/colony/buildings/views/IBuildingView.java:82-86`) |
| `editName` | `minwoinv.xml:10-11`, (150,11) 15×15, `builderhut/edit`, infobulle `com.minecolonies.gui.edit` (« Edit ») | (300,22) 30×30 | ouvre `WindowHutNameEntry` (`AbstractBuildingMainWindow.java:50,108-111`), § 8.6 |
| `build` | `minwoinv.xml:13-15`, (30,110) 129×17, `builder_button_medium_large`, `workerhuts.buildrepair` (« Build Options ») | (60,220) 258×34 | libellé recalculé à chaque image : annuler construction (niveau 0) ou amélioration, réparation, déconstruction, sinon « Build Options » (`:121-163`) ; un clic sur un libellé d'annulation envoie `BuildRequestMessage` BUILD, REPAIR ou REMOVE avec `BlockPos.ZERO` (= annuler), sinon ouvre `WindowBuildBuilding` (`:65-87`) |
| `info` | `minwoinv.xml:16-17`, (14,214) 17×17, `red_wax_information`, infobulle `core.building.help` (« Help ») | (28,428) 34×34 | visible seulement si la clé `com.minecolonies.coremod.info.<type>.0` existe (`:55-59`) ; ouvre `WindowInfo` (`:92-95`), § 8.6 |
| `inventory` | `min.xml:5-7`, (52,214) 86×17, `builder_button_medium`, `container.inventory` | (104,428) 172×34 | `OpenInventoryMessage` : ouvre le conteneur du bloc de hutte (`:100-103` ; `msg/colony/OpenInventoryMessage.java:153-161`), `MANAGE_HUTS` |
| `allinventory` | `min.xml:8-10`, (159,214) 17×17, `textures/gui/chest.png` (25×25), sans texte, infobulle `core.building.inv_sum` (« Building Inventory Summary ») | (318,428) 34×34 | ouvre `WindowHutAllInventory` (`:116-119`), § 8.6 |

Qui utilise quoi : une hutte avec un `WorkerBuildingModuleView` ouvre `WindowHutWorkerModulePlaceholder` (`windowhutworkerplaceholder.xml` = `layouthutpageactions.xml`), sinon `WindowHutMinPlaceholder` (`layouthutpageactionsmin.xml`) (`mc:core/colony/buildings/views/AbstractBuildingView.java:391-398`). Le constructeur ouvre `WindowHutBuilderModule` (même XML, `mc:core/colony/buildings/workerbuildings/BuildingBuilder.java:228-231`), l'entrepôt `WindowHutMinPlaceholder` (`BuildingWareHouse.java:217-220`), la résidence `WindowHutLiving` (`HomeBuildingModule.java:42-45`, § 8.4).

#### Travailleurs (`lh/layouthutpageactions.xml`, `gui/AbstractWindowWorkerModuleBuilding.java`)

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| titre | `:6-7`, (13,32) 164×11, centré, `workerhuts.workerassigned` (« §lAssigned Workers: », gras par le code §l) | (26,64) 328×22 | — |
| `workers` | `:9-15`, (13,43) 164×30, ligne de 11, `workerName` centré | (26,86) 328×60, ligne 22 | une ligne par travailleur de chaque `WorkerBuildingModuleView` : « Métier: Nom », infobulle « Nom (id) » (`AbstractWindowWorkerModuleBuilding.java:163-203`, texte `:195`, infobulle `:196`) |
| `hire` | `:17-19`, (30,74) 129×17, `medium_large`, `workerhuts.manage` (« Manage Workers ») | (60,148) 258×34 | si `!allowsAssignment()` (serveur : `canAssignCitizens`, `mc:core/colony/buildings/AbstractBuilding.java:711`), message de chat `workerhuts.level0` ; sinon ouvre `WindowHireWorker` (`:143-152`), § 8.6 |
| `recall` | `:20-22`, (30,92), `workerhuts.recall` (« Recall Worker ») | (60,184) 258×34 | `RecallCitizenMessage` (`:157-160`) : chaque travailleur est téléporté à la hutte ; un corps absent est recréé ; échec → `WARNING_CITIZEN_RECALL_FAILED` (`msg/colony/building/worker/RecallCitizenMessage.java:58-96`) |
| `prioValue` | `:24`, (30,135) 95×15, aligné à gauche | (60,270) 190×30 | « Pickup Prio.: » + « n/10 » ou « Never » (`:97-111`, clés `workerhuts.buildprio`, `workerhuts.deliveryprio.never`) |
| `deliveryPrioDown` / `Up` | `:26-29`, (127,135) et (144,135) 14×15, `builder_button_mini`, « - » « + » | (254,270) et (288,270) 28×30 | borne côté client 0..10, puis `ChangeDeliveryPriorityMessage` (`:113-131`) → `alterPickUpPriority(±1)` si la hutte a un `WorkerBuildingModule` ou est un `Stash` (`msg/colony/building/ChangeDeliveryPriorityMessage.java:63-77`) |
| `forcePickup` | `:31-33`, (30,154), `workerhuts.forcepickup` (« Request Pickup Now ») | (60,308) 258×34 | `ForcePickupMessage` (`:133-136`) → `createPickupRequest(STACKSIZE, true)`, message de réussite ou d'échec (`msg/colony/building/ForcePickupMessage.java:54-71`) |

Ordre de dessin : le XML d'abord, puis les onglets ajoutés par `addChild` (donc par-dessus le papier).

#### Onglets latéraux (`gui/modules/TabsWindowModule.java`, `gui/AbstractBuildingWindow.java`)

- Constantes (`TabsWindowModule.java:22-30`) : `TAB_X_OFFSET` 12, `TAB_Y_OFFSET` 10, onglet 32 × 26, `TAB_Y_SPACING` 2, icône 20 × 20 décalée de (5, 3).
- Côté gauche : x = −(32 − 12) = −20 (`:135`), y = 10 + 28 × index (`:105`). **×2 : x −40, y 20 + 56 × i, onglet 64 × 52, icône (10, 6) 40 × 40.** Le côté droit (x = largeur − 12, `:136`) n'est pas utilisé par les huttes.
- Fond de chaque onglet tiré au hasard parmi `modules/tab_left_side1..4` (32 × 26) (`:172-175`), avec un `Random(buildingView.getID().hashCode())` (`AbstractBuildingWindow.java:55`) : même suite d'images à chaque ouverture d'une même hutte, différente d'une hutte à l'autre. L'icône est une `ButtonImage` de 20 × 20 qui porte le même gestionnaire (`TabsWindowModule.java:113-122`).
- Infobulle sur tout l'onglet = `getDesc()` du module (`:124-127`).
- **Aucun état « onglet ouvert »** : tous les onglets se dessinent pareil ; chacun ouvre une nouvelle fenêtre (`AbstractBuildingWindow.java:65,81`).
- Liste (`AbstractBuildingWindow.java:57-84`) : d'abord **Principal** (`modules/main.png`, infobulle `coremod.gui.maintab` « Main », rouvre `buildingView.getWindow()`, sans son), puis chaque vue de module dont `isPageVisible()` est vrai, dans l'ordre d'enregistrement (`moduleViews` est un `Int2ObjectLinkedOpenHashMap`, `AbstractBuildingView.java:152,715-718`, rempli dans l'ordre des producteurs, `mc:api/colony/buildings/registry/BuildingEntry.java:158`). Un clic de module joue `BOOK_PAGE_TURN` (`:80`). `isPageVisible()` vaut vrai par défaut (`mc:api/colony/buildings/modules/IBuildingModuleView.java:40`).
- Les fenêtres de module (`gui/AbstractModuleWindow.java:50,58-65`) posent `getDesc()` dans le texte d'id `desc` s'il existe (en-tête (30,14) 130 × 11, noir ; ×2 (60,28) 260 × 22).
- Les fenêtres annexes (`WindowHireWorker`, `WindowAssignCitizen`, `WindowHutAllInventory`, `WindowInfo`) héritent d'`AbstractWindowSkeleton` et **n'ont pas d'onglets**.

Icône et infobulle de chaque vue de module rencontrée (textures 20 × 20 dans `tex/modules/`) :

| Vue (source) | Onglet ? | Icône | Infobulle / en-tête (texte anglais) |
|---|---|---|---|
| `WorkerBuildingModuleView` (`mv/WorkerBuildingModuleView.java:112-127`) | non | `custom` | — |
| `CraftingModuleView` (`mv/CraftingModuleView.java:180-202`) | si `isVisible` du serveur : types d'artisanat ou recettes (`mc:core/colony/buildings/modules/AbstractCraftingBuildingModule.java:334,440-443`) | `<id>.png`, ici `crafting` | `workerhuts.recipe.crafting` « Crafting Recipes » |
| `BuildingResourcesModuleView` (`mv/BuildingResourcesModuleView.java:125-140`) | oui | `inventory` | `workerhuts.resourcelist` « Required Resources » |
| `SettingsModuleView` (`mv/SettingsModuleView.java:99-114`) | oui | `settings` | `workerhuts.settings` « Settings » |
| `WorkOrderListModuleView` (`mv/WorkOrderListModuleView.java:28-50`) | oui | `info` | `townhall.workorders` « Work Orders » |
| `MinimumStockModuleView` (`mv/MinimumStockModuleView.java:54-81`) | oui | `stock` | `warehouse.stock` « Minimum Stock » |
| `BuildingStatisticsModuleView` (`mv/BuildingStatisticsModuleView.java:31-46`) | oui | `stats` | `core.gui.modules.stats` « Building Statistics » |
| `FieldsModuleView` (`mv/FieldsModuleView.java:47-56`) | oui | `field` | `workerhuts.fields` « Fields » |
| `RequestTaskModuleView` et ses sous-classes (`mv/RequestTaskModuleView.java:30-45`) | oui | `info` | `workerhuts.crafter.tasks` « Tasks » |
| `CourierAssignmentModuleView` (`mv/CourierAssignmentModuleView.java:69-78,123-126`) | oui | `entity` | `workerhuts.warehouse.couriers` « Couriers » |
| `WarehouseOptionsModuleView` (`mv/WarehouseOptionsModuleView.java:25-47`) | oui | `settings` | `workerhuts.settings` « Settings » |
| `LivingBuildingModuleView` (`mv/LivingBuildingModuleView.java:73-76,104`) | **non** | `custom` | `null` |

Textes anglais lus dans `lang:` (clés préfixées `com.minecolonies.`).

#### HyColony aujourd'hui (cadre)

- Une seule page vanilla : `$C.@DecoratedContainer` de 760 px, titre générique, rangée `#TabButtons` en haut (`ui:Building.ui:7-23`), un `TabButton.ui` texte par onglet, l'onglet ouvert désactivé (`pl:ui/TabBar.java:23-33`, `pl:ui/BuildingPage.java:46,87-92`).
- Les onglets viennent des modules `ProvidesTab`, dans l'ordre des modules (`hc:app/view/BuildingViews.java:105-111`), rendus par `pl:ui/hut/HutTabs.java:35-53`.
- Onglet Principal (`pl:ui/BuildingMainTab.java`) :
  - type, « niveau n/max », état (`:40-47`), ligne d'ordre (`:62-79`), bouton de construction fidèle à `updateButtonBuild` (`:85-98`) ;
  - bouton du mode d'embauche **sur la page principale** (`:100-123`), listes en ligne Travailleurs (Renvoyer) et Embauchables (Embaucher) (`ui:Building.ui:54-89`) ;
  - priorité de ramassage et ramassage forcé (`pl:ui/logistics/PickupPanel.java:31-62`) ;
  - Stockage (`pl:ui/HutStorage.java:36-38`, droit `OPEN_CONTAINER`) et Inventaire total en sous-vue (`pl:ui/HutStockPanel.java:13-14,34`).

| Élément du cadre | Statut | Manque ou écart |
|---|---|---|
| Papier 380 × 488, bandeau, titre rouge | A (rendu) / B (titre) | `BuildingView` n'a pas `customName` (`hc:app/ui/BuildingView.java:21-40`) ; `Building.customName()` existe (`hc:building/Building.java:119-124,192`) |
| Crayon + renommage | B | aucune action cœur n'appelle `setCustomName` hors chargement (`hc:app/persistence/BuildingSerializer.java:75`) |
| Construire | fait | texture `builder_button_medium_large` à poser |
| Aide (`info`) | A | pages de texte MC, § 8.6 |
| Inventaire | A, écart de droit | MC exige `MANAGE_HUTS`, HyColony `OPEN_CONTAINER` (`HutStorage.java:36-38`) |
| Inventaire total | B | § 8.6 |
| Onglets latéraux | A | rendu à refaire ; MC n'a pas d'état « ouvert » (écart actuel : `TabBar` désactive l'onglet ouvert) |
| Travailleurs « Métier: Nom » + infobulle | B | `WorkerRow` n'a pas le métier (`BuildingView.java:45`) ; l'id y est |
| Gérer les travailleurs | A | données de candidats présentes ; fenêtre séparée à faire (§ 8.6) |
| Rappel des travailleurs | B | `CitizenRecall.bring` existe (`hc:app/action/CitizenRecall.java:52`), utilisé pour les résidents (`hc:app/action/HousingActions.java:70-84`), pas d'action pour les travailleurs |
| Priorité, ramassage forcé | fait (contenu) | rendu à refaire ; MC laisse les boutons actifs pour tous, le serveur refuse ; HyColony les désactive sans `MANAGE_HUTS` (`PickupPanel.java:42-48`) |
| (HyColony) état, « niveau n/max », ligne d'ordre, listes d'embauche en ligne, bouton du mode d'embauche sur la page | écart | absents de la page MC : le mode est dans `WindowHireWorker`, l'avancement dans l'onglet Ressources |

### 8.2 Modules de chaque hutte, dans l'ordre des onglets

Ordre MC lu dans `mbi:` ; définitions dans `bm:`. Le premier onglet est toujours Principal (`main`).

**Constructeur** (`mbi:95-107`) — HyColony : ressources, réglages, ordres (`hc:construction/hut/ConstructionBuildingTypes.java:22-38`).

| # | Module MC (`bm:`) | Onglet, icône | Statut | Ce qui manque |
|---|---|---|---|---|
| 0 | Principal | `main` | B | § 8.1 (nom, rappel, métier des lignes) ; et `WindowHutGuide` (§ 8.6) |
| — | `BUILDER_WORK` (`:430-432`) | aucun | — | — |
| 1 | `BUILDER_CRAFT` : `SimpleCraftingModule`, artisanat 2 × 2 (`:440-441` ; `mc:core/colony/buildings/modules/SimpleCraftingModule.java:52-55`) | Crafting Recipes, `crafting` | C | artisanat du constructeur : HyColony n'a pas de `CraftingModule` sur le constructeur ; MC sert ces recettes par le résolveur d'artisanat privé (`mc:core/colony/requestsystem/resolvers/PrivateWorkerCraftingProductionResolver.java:77`) ; l'exécution par l'IA du constructeur n'est pas vérifiée ici |
| 2 | `BUILDING_RESOURCES` (`:444-445`) | Required Resources, `inventory` | A | § 8.5 |
| 3 | `BUILDER_SETTINGS` (`:433-438`) | Settings, `settings` | B | 3 réglages sur 5, § 8.5 |
| 4 | `WORKORDER_VIEW` (`:442-443`) | Work Orders, `info` | A | § 8.5 |
| 5 | `MIN_STOCK` (`:43-44`) | Minimum Stock, `stock` | C | stock minimum |
| 6 | `STATS_MODULE` (`:62-64`) | Building Statistics, `stats` | C | statistiques de hutte (`BuildingStatisticsModule`) |

**Fermier** (`mbi:204-216`) — HyColony : recettes, champs (`hc:farming/hut/FarmerHut.java:27-39`). Attention aux noms de MC : `FARMER_WORK` est le module d'**artisanat** (`bm:125-126`) et `FARMER_CRAFT` le module de **travailleur** (`bm:122-124`).

| # | Module MC | Onglet, icône | Statut | Ce qui manque |
|---|---|---|---|---|
| 0 | Principal | `main` | B | § 8.1 |
| 1 | `FARMER_WORK` : `BuildingFarmer.CraftingModule` (artisanat 2 × 2 et 3 × 3, `AbstractCraftingBuildingModule.java:1026-1029`) | Crafting Recipes, `crafting` | A | § 8.5 (l'apprentissage par liste reste un écart documenté) |
| — | `FARMER_CRAFT` (travailleur, Stamina, Athletics) | aucun | — | — |
| 2 | `FARMER_FIELDS` (`bm:127-128`) | Fields, `field` | A | § 8.5 |
| 3 | `FARMER_SETTINGS` : `fertilize` (vrai), `recipemode` (`bm:129-132`) | Settings, `settings` | B | `FarmerSettingsModule` n'a pas d'onglet (`hc:farming/hut/FarmerSettingsModule.java`) ; « Request Fertilizer » est aujourd'hui dans l'onglet Champs (`pl:ui/hut/FieldsTab.java:55-56`) ; `recipemode` absent |
| 4 | `CRAFT_TASK_VIEW` : `CrafterRequestTaskModuleView` (`bm:52`), la file des métiers artisans (`mv/CrafterRequestTaskModuleView.java:15-32`) ; le fermier MC est un artisan (`mc:core/colony/jobs/JobFarmer.java:12`, vue `CrafterJobView`) | Tasks, `info` | B | la file existe (`hc:crafting/task/CraftingTasks.java:51`), aucune vue d'onglet |
| 5 | `MIN_STOCK` | Minimum Stock, `stock` | C | stock minimum |
| 6 | `STATS_MODULE` | Building Statistics, `stats` | C | statistiques de hutte |

**Coursier** (`mbi:194-202`) — HyColony : tâches (`hc:logistics/courier/DeliverymanHut.java:23-31`).

| # | Module MC | Onglet, icône | Statut | Ce qui manque |
|---|---|---|---|---|
| 0 | Principal | `main` | B | § 8.1 |
| — | `COURIER_WORK` (`bm:390-392`) | aucun | — | — |
| 1 | `COURIER_TASK_VIEW` : `CourierRequestTaskModuleView` (`bm:53` ; `mv/CourierRequestTaskModuleView.java:15-32`) | Tasks, `info` | B | § 8.5 (icônes, infobulle des positions) |
| 2 | `STATS_MODULE` | Building Statistics, `stats` | C | statistiques de hutte |

**Entrepôt** (`mbi:395-403`) — HyColony : coursiers, tâches (`hc:logistics/warehouse/WarehouseBuilding.java:18-26`). Page principale `WindowHutMinPlaceholder` : ni travailleurs ni priorité.

| # | Module MC | Onglet, icône | Statut | Ce qui manque |
|---|---|---|---|---|
| 0 | Principal (min) | `main` | B | § 8.1 (nom, inventaire total) |
| 1 | `WAREHOUSE_COURIERS` (`bm:394-395`) | Couriers, `entity` | B | rattacher ou détacher à la main, mode d'embauche, rappel : § 8.5 |
| 2 | `WAREHOUSE_OPTIONS` (`bm:396-397`) | Settings, `settings` | C | amélioration de stockage (bloc d'émeraude) et tri de l'entrepôt |
| 3 | `MIN_STOCK` | Minimum Stock, `stock` | C | stock minimum |
| 4 | `WAREHOUSE_REQUEST_QUEUE` (`bm:398-399`) | Tasks, `info` | B | § 8.5 |

**Résidence** (`mbi:245-253`) : `HOME` (sans vue, `bm:486-487`), `LIVING` (vue sans onglet, `mv/LivingBuildingModuleView.java:104`), `BED` (sans vue, `bm:50`). **Seul l'onglet Principal**, et l'info est masquée (pas de clé `info.residence.*` dans `lang:`). HyColony : `living` et `bed` (`ConstructionBuildingTypes.java:40-44`), avec un onglet Résidents.

| # | Module MC | Onglet | Statut | Ce qui manque |
|---|---|---|---|---|
| 0 | Principal (`WindowHutLiving`) | `main` | B | le contenu est aujourd'hui dans l'onglet Résidents (`pl:ui/hut/ResidentsTab.java:52-79`) : écart à défaire ; renommage manquant |

Bilan des onglets (Principal compris) : constructeur A 2 / B 2 / C 3 ; fermier A 2 / B 3 / C 2 ; coursier A 0 / B 2 / C 1 ; entrepôt A 0 / B 3 / C 2 ; résidence B 1.

### 8.3 Fenêtres annexes ouvertes depuis une hutte

| Fenêtre MC | Ouverte par | Statut |
|---|---|---|
| `WindowHireWorker` | `hire` (huttes à travailleurs), `hire` de l'onglet Coursiers | B |
| `WindowAssignCitizen` | `assign` de la résidence | A |
| `WindowHutNameEntry` | `editName` | B |
| `WindowHutAllInventory` | `allinventory` | B |
| `WindowInfo` | `info` | A |
| `WindowHutGuide` | ouverture de la hutte du constructeur sans le succès « check_out_guide » (`gui/huts/WindowHutBuilderModule.java:52-66`) | C : succès (advancements) |
| `WindowBuildBuilding` (+ `WindowConfirm`) | `build` | fait (`pl:ui/BuildOptionsPage.java`, déjà au style MC ; double clic au lieu de `WindowConfirm`, `:42`) |
| `WindowSelectRes` | bloc de remplissage, ajout de stock minimum | fait pour le bloc de remplissage (`pl:ui/ItemPickerPage.java`) |

### 8.4 Résidence : page principale (`xml/windowhuthome.xml`, `gui/huts/WindowHutLiving.java`)

Inclut `layouthutpageactionsmin.xml` (§ 8.1 : papier, titre, renommer, construire à (30,110), inventaire, inventaire total ; aide masquée).

| Élément MC | XML | ×2 | Java, message | HyColony | Statut |
|---|---|---|---|---|---|
| `assignedlabel` | `:4`, (13,32) 164×11, centré | (26,64) 328×22 | « Assigned Citizens: %d/%d » (`home.assigned`, `WindowHutLiving.java:106-110`) | `ResidentsTab.java:53-57` | A |
| `assignedCitizen` | `:6-12`, (13,48) 164×30, ligne de 11 | (26,96) 328×60 | « Métier: Nom » ou « Nom » sans emploi (`:81-98`) | lignes avec distance du travail (`ResidentsTab.java:62-66`) | A |
| `assign` | `:14-16`, (30,92) 129×17, `medium_large`, `home.manage` « Manage Housing » | (60,184) 258×34 | niveau 0 → message `workerhuts.level0` ; sinon `WindowAssignCitizen` (`:115-124`) | listes dans l'onglet | A |
| `recall` | `:17-19`, (30,128), `townhall.recall` « Recall Citizens » | (60,256) 258×34 | `RecallCitizenHutMessage` (`:71-74` ; `msg/colony/building/RecallCitizenHutMessage.java:48-76`) | `HousingActions.recall` (`hc:app/action/HousingActions.java:70-84`) | fait (contenu) |

`WindowAssignCitizen` (`xml/windowassigncitizen.xml`, `gui/WindowAssignCitizen.java`), papier large `builder_paper_wide2` 400 × 244 (×2 800 × 488), sans onglets :

| Élément MC | XML | ×2 | Java | Statut |
|---|---|---|---|---|
| description | `:6`, (0,20) 100 %×11, `assigning.description` | (0,40) 800×22 | — | A |
| `assigned` (gauche) | `:8-18`, (10,40) 190×160, ligne 34 (fond `gradient` 430265344) : `citizen` bleu (5,2), `fire` (120,2) 44×10 `builder_button_quite_small` libellé `hiring.buttonunassign`, `job` (5,13) 175×20 | (20,80) 380×320, ligne 68 ; bouton (240,4) 88×20 | « Métier: à N blocs », en rouge au-delà de 300 (`:341-398`, `FAR_DISTANCE_THRESHOLD` `:39`) ; bouton actif si (DEFAULT et logement auto désactivé) ou MANUAL, sinon infobulle `home.hire.warning` ; un citoyen en voyage : désactivé, `home.travelling` | A (voyage : C) |
| `unassigned` (droite) | `:20-30`, (201,40), même ligne, `hire` « Assign » | (402,80) 380×320 | candidats : pas logés à leur travail, pas déjà ici ; triés sans logis d'abord puis distance du travail (`:193-217`) ; ligne « Métier: à N » en vert si plus près, « actuellement M » en rouge au-delà de 300, « sans logis » (`:245-318`) ; actif si place libre | A |
| `mode` | `:32-34`, label (40,210), bouton (170,207) 169×17 `builder_button_large` | (80,420) ; (340,414) 338×34 | 4 modes en boucle, LOCKED compris (`:154-165`) → `BuildingHiringModeMessage` | A |
| `cancel` | `:36-37`, (375,10) 14×15 `button_x` | (750,20) 28×30 | rouvre la hutte (`:172-178`) | A |
| messages | — | — | `AssignUnassignMessage` (`:100-136`) | `HousingActions.assign/unassign/cycleMode` | fait (contenu) |

### 8.5 Pages de modules

#### Ressources du constructeur (`lh/layoutbuilderres.xml`, `mod/WindowBuilderResModule.java`) — A

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| `desc` | `:4` | (60,28) 260×22 | « Required Resources » (`:64`) |
| `constructionName` | `:6`, (13,29) 164×11 | (26,58) 328×22 | nom de l'ordre, infobulle identique (`:136-146`) |
| `stepprogress` | `:7`, (13,40) | (26,80) | « Step %d/%d » (`:147`) |
| `progress` | `:8`, (13,51) | (26,102) | « Supplied %s / Used %s », posé seulement si le total > 0 (`:103-106`) |
| `resources` | `:9-22`, (13,62) 164×163, ligne `box` 30 bordée | (26,124) 328×326, ligne 60 | icône (1,1) 16 ; nom (20,3) 100×12 ; manque joueur (2,18) 50×12 si < 0 ; « dispo / requis » centré (0,18) ; toute la ligne rouge, orange, vert foncé #006400 ou noire (`:164-191`) ; tri `ResourceComparator` (`:108`) ; rafraîchi toutes les 20 images (`:218-228`) |
| `resourceAdd` « ↥ » | `:16-17`, 14×15 `builder_button_mini`, repositionné par le code en (143,13) (`:194-196`) | (286,26) 28×30 | actif en NEED_MORE et HAVE_ENOUGH ; `TransferItemsRequestMessage` (`:235-263`) ; `MarkBuildingDirtyMessage` à l'ouverture (`:134`) |

HyColony : `pl:ui/hut/BuilderResourcesTab.java:54-110`, vue `hc:construction/resources/BuilderResourcesView.java` : tout y est (nom, étape, livré/avancement, manque joueur, couleur de ligne, tri). `IN_DELIVERY` : MC ne remplit jamais `amountInDelivery` dans cette fenêtre (seule `gui/WindowResourceList.java:111-116` le fait), donc ce statut n'y apparaît jamais ; rien ne manque. Écart actuel : NOT_NEEDED en gris (`BuilderResourcesTab.java:34-41`), à remettre en noir sur le papier.

#### Réglages (`lh/layoutsettings.xml`, `mod/SettingsModuleWindow.java`)

- Liste `settingslist` (13,40) 164 × 170, `box` de 45 bordée (`layoutsettings.xml:6-8`) ; ×2 (26,80) 328 × 340, ligne 90.
- Chaque ligne charge le XML de son type (`SettingsModuleWindow.java:88-109`) ; texte `desc` = `com.minecolonies.coremod.setting.<id>` (`:104-108`).
- Les réglages s'affichent dans l'ordre d'insertion (`LinkedHashMap`, `mc:core/colony/buildings/moduleviews/SettingsModuleView.java:37`). Un réglage inactif (recherche manquante) est montré désactivé, car `shouldHideWhenInactive` vaut faux par défaut (`mc:api/colony/buildings/modules/settings/ISetting.java:83`, `SettingsModuleView.java:76-87`).
- Infobulle (`ISetting.java:115-140`) : `setting.tooltip.<id>` s'il existe, sinon `getToolTipText()` ; inactif, la raison (« needs research »).
- Clic → `TriggerSettingMessage` (`SettingsModuleView.java:117-124`).

| Type (XML) | Ligne MC | ×2 | Contenu |
|---|---|---|---|
| `BoolSetting` (`lh/layoutboolsetting.xml`) | `desc` (5,5) 100 %×15 enroulé ; `trigger` (5,25) 30×17 `builder_button_very_small` | (10,10) ×30 ; (10,50) 60×34 | « On »/« Off » (`retrieveon/off`) |
| `StringSetting` (`layoutstringsetting.xml`) | `trigger` (5,25) 145×17 `medium_large`, largeur bornée à 145 | (10,50) 290×34 | la valeur traduite ; un clic passe à la suivante |
| `BlockSetting` (`layoutblocksetting.xml`) | `trigger` (5,25) 80×17 `builder_button_small` « Switch », `icon` (100,25) 16 | (10,50) 160×34 ; (200,50) 32×32 | ouvre `WindowSelectRes` des blocs pleins |
| `CrafterRecipeSetting` (`StringSettingWithDesc`) | comme `StringSetting` | idem | « Priority » / « Max stock » ; inactif sans la recherche `recipemodeunlock` |
| `BuilderModeSetting` | comme `StringSetting` | idem | itérateurs de Structurize, infobulle par valeur ; inactif sans la recherche `buildermodes` |

Constructeur (`bm:433-438`), dans l'ordre :

| Réglage MC | Texte | HyColony | Statut |
|---|---|---|---|
| `mode` (Auto / Manuel) | « Task Assignment Mode: » | `BuilderSettingsTab.java:47-63`, `HutActions.setBuilderMode` (`hc:app/action/HutActions.java:223`) | fait (contenu) |
| `recipemode` | « Recipe Mode: » | rien | B : ligne désactivée avec la raison (HyColony n'a pas de recherche, donc toujours inactive) |
| `buildmode` | « Construction Strategy: » | rien | B : même chose (recherche `buildermodes`) |
| `useshears` (faux) | « Use Shears: » | rien | écart déjà assumé (pas de cisailles, `ConstructionBuildingTypes.java:33-34`) |
| `fillblock` (terre) | « Fill block: » | bloc de remplissage + `ItemPickerPage` (`BuilderSettingsTab.java:77-89`, `HutActions.setFillBlock` `:243`) | fait (contenu) |

Fermier (`bm:129-132`) : `fertilize` (BoolSetting, vrai, « Request Fertilizer ») — action `FieldActions.toggleFertilize` (`hc:app/action/FieldActions.java:110-116`), à sortir de l'onglet Champs ; `recipemode` — B comme ci-dessus.

#### Ordres de travail du constructeur (`lh/layoutworkorders.xml`, `mod/WorkOrderModuleWindow.java`) — A

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| `desc` | `:4`, « Work Orders » | (60,28) | — |
| libellé égaré | `:6`, (201,28) 148×11 « Work Orders » : hors du papier de 190 | (402,56) | bizarrerie MC : à reproduire ou à écarter (question ouverte) |
| `workOrders` | `:8-18`, (13,35) 164×154, vide : « No work orders » ; `box` 30 bordée | (26,70) 328×308, ligne 60 | filtre `shouldShowIn`, `canBuildIgnoringDistance`, puis réclamés ici (+ non réclamés en Manuel) (`:114-137`) ; tri ordre courant, réclamés ici, autres, puis `WORK_ORDER_COMPARATOR` (`:142-168`) ; rafraîchi toutes les 20 images (`:99-109`) |
| `buildingName` | (2,2) 150×12, infobulle identique | (4,4) 300×24 | `order.getDisplayName()` (`:192-197`) |
| `buildingPos` | (2,17) 50×12, bleu | (4,34) 100×24 | « %d blocks », distance 2D (`:198-199`) |
| `manage` | (80,13) 64×17 `builder_button_small` | (160,26) 128×34 | réclamé ici : « Cancel » → `WorkOrderChangeMessage(id, remove=true)`, qui **supprime** l'ordre (`msg/colony/WorkOrderChangeMessage.java:81-87`) ; en Manuel : « Assign » → `BuilderSelectWorkOrderMessage`, désactivé avec infobulle (pas de travailleur, déjà réclamé, ne peut construire) (`:201-252`) ; en Auto, un ordre non réclamé ici n'est pas listé |
| cadre de l'ordre courant | la **bordure** du `box` passe à (0,170,0) (`:180-188` ; `Box` ne dessine qu'un contour, `bui:views/Box.java:55-57`) | — | — |

HyColony : `pl:ui/hut/BuilderOrdersTab.java:47-110`, `WorkOrderActions.select/cancelFromBuilder` (`hc:app/action/WorkOrderActions.java:96,124`, suppression comme MC). Écart : l'ordre courant a un **fond** vert (`BuilderOrdersTab.java:58`) au lieu d'un contour ; un `Group` sait poser `OutlineSize`/`OutlineColor` (déjà fait dans `ui:Mc/ActionRow.ui:9-10`).

#### Recettes (`lh/layoutlistrecipes.xml`, `mod/WindowListRecipes.java`) — A pour le fermier, C pour le constructeur

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| en-tête fixe | `:4-5`, « List of Recipes » | (60,28) | — |
| `recipestatus` | `:7`, (70,30) | (140,60) | « %s of %s » actives / max, à chaque image (`:290`) ; visible si le module accepte des recettes (`:85-86`, `CraftingModuleView.java:145-148`) |
| `recipes` | `:8-31`, (20,40) 85 %×150, ligne de 50 | (40,80) ≈322×300, ligne 100 | `output` (80,17) 16 qui alterne les sorties (`:178-180`) ; grille 3 × 3 `res1..9` (20/34/48, 1/15/29) avec quantités, cas spécial de 4 entrées (`:235-267`) ; fond gris `gradient` rgb(160,160,160) si désactivée (`:221-232`) ; `intermediate` (105,33) : outil requis ou bloc intermédiaire (`:208-219`) |
| `up` « ↥ », `down` « ↧ » | (0,0), (0,15) 15×15, **sans texture** (bouton vanilla de Minecraft, `bui:controls/ButtonImage.java:23`) | (0,0), (0,30) 30×30 | `ChangeRecipePriorityMessage`, Maj = jusqu'au bout (`:111-131`) |
| `remove` | (105,3) 45×15, vanilla | (210,6) 90×30 | `AddRemoveRecipeMessage` ; recette native désactivée sauf Ctrl, avec infobulle `removebuiltin` (`:137-143,182-206`) |
| `toggle` | (105,18) 45×15, vanilla | (210,36) 90×30 | « Enable »/« Disable » ; une désactivée se cache si le maximum est atteint, sauf recette native (`:221-232`) ; `ToggleRecipeMessage` (`:100-105`) |
| `crafting` | `:34-36`, (30,200) 129×17 `medium_large`, « workerhuts.crafting » | (60,400) 258×34 | ouvre l'interface d'apprentissage (conteneur d'artisanat) (`:148-157`) |

HyColony : `pl:ui/hut/RecipesTab.java:51`, `hc:crafting/module/RecipesView.java`. Écarts déjà documentés : apprentissage par liste (`RecipesView`), pas de Maj (`RecipesTab.java:21`). Manque : la suppression d'une recette native avec Ctrl (pas d'événement de touche modificatrice connu).

#### Champs du fermier (`lh/layoutfarmfields.xml`, `mod/FarmFieldsModuleWindow.java`) — A

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| en-tête fixe | `:4-5`, « Fields » | (60,28) | — |
| `AssignmentModeLabel` | `:6-7`, (13,29) 164×11 « Assign fields to worker » | (26,58) 328×22 | — |
| `assignmentMode` | `:8-11`, (52,42) 86×17 `builder_button_medium` (désactivé : `_medium_disabled`) | (104,84) 172×34 | « Manual »/« Automatic » (`hiring.on/off`) ; `AssignmentModeMessage` (`:112-116,141-147`) |
| `fieldCount` | `:12-13`, (13,69) | (26,138) 328×22 | « %d/%d fields in use » (`:145-146`) |
| `fields` | `:15-25`, (13,82) 164×145, `box` 30 | (26,164) 328×290, ligne 60 | `icon` (4,4) graine ; `dist` (25,4) 108×9 « N m dir » ou texte long pour haut/bas (`:184-192`) ; `nextstagetext` (25,17) « Status: » ; `nextstageicon` (60,13) 16 : icône de l'étape (houe de fer, graines de blé, durum, `mc:core/colony/buildingextensions/FarmField.java:242-244`) avec infobulle « actuelle / suivante » (`:167-182`) |
| `assign` | (137,4) 14×15 `builder_button_mini` ; coché : `_mini_check` (`:72-87,241-253`) | (274,8) 28×30 | actif en Manuel ; refus : désactivé, infobulle rouge (`:194-221`) ; `AssignFieldMessage` (`:123-136`) |

HyColony : `pl:ui/hut/FieldsTab.java:47-110`, vue `hc:farming/hut/FieldsView.java`. Données complètes. Écarts : bouton « Fertiliser » (à déplacer dans Réglages), « fait aujourd'hui » et Localiser (ajouts documentés, `FieldsTab.java:21`) ; étape en texte au lieu d'une icône avec infobulle ; bouton texte Assigner/Libérer au lieu de la case mini cochée.

#### Tâches (`lh/layouttasklist.xml`, `mod/WindowHutRequestTaskModule.java`) — B

Même fenêtre pour les tâches d'artisan (fermier), de coursier et de l'entrepôt (`mv/RequestTaskModuleView.java:30-32`). L'arbre `layoutrequeststree.xml` sert au citoyen et au presse-papiers, pas aux huttes.

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| en-tête fixe | `:4-5`, « Tasks » | (60,28) | — |
| `tasks` | `:7-17`, (13,29) 164×185, vide : « No tasks remaining » ; `box` 34 | (26,58) 328×370, ligne 68 | tâches dont la requête existe encore (`:50-58`) |
| `deliveryImage` | (1,3) 16 | (2,6) 32 | `request.getDisplayIcon()` (`:115-116`) |
| `shortDetail` + `detailIcon` | (20,2) 130×9 ; (80,1) 9×9 | (40,4) 260×18 ; (160,2) 18×18 | tâche de pile : préfixe + objet avec quantité ; sinon texte court ; **vert foncé** si IN_PROGRESS, noir sinon (`:94-107`) |
| `priority` | (20,12) 130×9 | (40,24) 260×18 | « Priority: n » pour une requête de coursier (`:109-113`) |
| `requester` | (2,22) 150×9 | (4,44) 300×18 | « demandeur -> parent » en remontant les parents du même lieu, infobulle des positions « x, y, z -> x, y, z » (`:63-91`) |

HyColony : `pl:ui/hut/TaskRows.java`, `CourierTasksTab.java:30`, `WarehouseTasksTab.java:27`, vue `hc:logistics/warehouse/TaskRow.java`. Manquent : l'icône de requête et l'icône d'objet, l'infobulle des positions. Écart : la tâche en cours a un fond vert (`TaskRows.java:30`) au lieu d'un texte vert foncé. HyColony ajoute en tête la ligne « entrepôt servi » du coursier (`CourierTasksTab.java:30-38`), absente de MC. Pour le fermier, la file existe (`CraftingTasks.java:51`) mais aucune vue ne l'expose.

#### Coursiers de l'entrepôt (`lh/layoutcourierassignment.xml`, `mod/SpecialAssignmentModuleWindow.java`) — B

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| titre | `:4-5`, (13,22) « Assigned Workers: » (pas d'en-tête `desc`) | (26,44) 328×22 | — |
| `workers` | `:7-13`, (13,43) 164×110, ligne 11 | (26,86) 328×220 | « Métier: Nom » (`:83-121`) |
| `hire` | `:15-17`, (30,154) `medium_large` « Manage Workers » | (60,308) 258×34 | niveau 0 → `workerhuts.level0` ; sinon `WindowHireWorker` sur le module des coursiers (`:72-81`), qui rattache par `HireFireMessage` et change le mode par `CourierHiringModeMessage` (`mv/CourierAssignmentModuleView.java:48-98`) ; candidats : coursiers rattachés à aucun autre entrepôt (`:101-113`) ; maximum niveau × 2 (`:116-119`) |
| `recall` | `:18-20`, (30,172) « Recall Worker » | (60,344) 258×34 | `RecallCitizenMessage` (`:62-65`) |

HyColony : `pl:ui/hut/WarehouseCouriersTab.java:33-50` (« n / max » et les noms ; écart documenté `:12`). Le maximum est le même (`hc:logistics/warehouse/CourierAssignmentModule.java:37-38`), le mode existe (`:29,41-46`), mais aucune action ne rattache, ne détache ni ne change le mode ; pas de rappel.

#### Options de l'entrepôt (`lh/layoutwarehouseoptions.xml`, `mod/WarehouseOptionsModuleWindow.java`) — C

- En-tête « Upgrade Storage ».
- `box` (13,29) 164 × 30 ; ×2 (26,58) 328 × 60. Il contient : l'icône du bloc d'émeraude, seulement au niveau max (`:177-182`) ; le nom (18,2) ; « ↥ » (145,2) 16 × 16 vanilla ; le manque (2,20) ; « dispo / requis » (67,20) ; « n of 3 » (132,20) (`:89-183`).
- Ligne noire et « X » rouge avec infobulle tant que la hutte n'est pas au niveau max (`:142-160`).
- Clic → `UpgradeWarehouseMessage` (`:188-194`).
- `sort` (50,70) 86 × 17 `medium` ; ×2 (100,140) 172 × 34. Désactivé sous le niveau 3, avec infobulle (`:68-77`) ; sinon `SortBuildingMessage` et message `WAREHOUSE_SORTED` (`:199-206`).
- HyColony : rien (amélioration de stockage et tri absents, `WarehouseBuilding.java` Javadoc).

#### Stock minimum (`lh/layoutminimumstock.xml`, `mod/MinimumStockModuleWindow.java`) — C

- `addStock` (50,30) 86 × 17 `medium` « Add » ; ×2 (100,60) 172 × 34. Il devient « Limit Reached » avec la texture `_medium_disabled` à la limite (`:63-70`). Sinon il ouvre `WindowSelectRes` (objets + inventaire, avec quantité) → `AddMinimumStockToBuildingModuleMessage` (`:91-103`).
- `resourcesstock` (13,50) 170 × 180 ; ×2 (26,100) 340 × 360. Chaque ligne de 17 (×2 34) : icône 17, nom (20,1), quantité (115,1), `removeStock` « X » rouge (130,1) 29 × 15 `very_small` → `RemoveMinimumStock…Message` (`:79-86,115-149`).
- HyColony : rien.

#### Statistiques de hutte (`lh/layoutstatsmodule.xml`, `mod/WindowStatsModule.java`) — C

- Liste déroulante `intervals` (30,30) 129 × 17 : hier par défaut, semaine, 100 jours, toujours (`:61-72`).
- `stats` (20,55) 160 × 160 : une ligne `com.minecolonies.coremod.statistic.*` par type, avec infobulle (`:102-185`).
- « masquer les zéros » (20,220) avec la case `hidezero` (127,218) `mini_check`, cochée par défaut (`:79`).
- HyColony : rien.

### 8.6 Fenêtres annexes : détail

#### `WindowHireWorker` (`xml/windowhireworker.xml`, `gui/WindowHireWorker.java`) — B

Papier `builder_paper_wide2` 400 × 244 (×2 800 × 488), sans onglets.

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| `jobLabel` | `:6`, (0,15) 100 %×11 | (0,30) 800×22 | « Choose workers for the %s. » ; hutte dédiée : « métier/métier … » (`:123-136`) |
| boutons de métier | ajoutés par le code : (15 + 90 i, 30) 86×17 `builder_button_medium` | (30 + 180 i, 60) 172×34 | un par module d'affectation permis, « Métier n » ; le choisi désactivé ; infobulle `<job>.job.desc` (`:510-552`) |
| `unemployed` | `:7-28`, (20,48) 375×135, ligne 35 | (40,96) 750×270, ligne 70 | `citizen` bleu (5,0) « Métier ou Unemployed: Nom » (`:475-478`) ; `distance` (2,10) ×0.8 : sans logis, habite ici, habite au travail, à N blocs (`:479-495`) ; `attributes` (2,18) ×0.7 : toutes les compétences « nom: niveau », primaire en vert foncé gras, secondaire en or gras, en tête (`:449-473,562-573`), infobulle `<job>.skills.desc` (`:499-503`) |
| `done` / `fire` | (200,2) 44×10 `builder_button_quite_small` | (400,4) 88×20 | embaucher si assignable, module pas plein, pas déjà là ; plein : rien ; sinon renvoyer (masqué si en voyage) (`:405-438`) ; un employé d'ailleurs est d'abord retiré de son ancienne hutte (`:241-258`) ; `HireFireMessage` |
| `pause` / `restart` | (250,2), (300,2) | (500,4), (600,4) | « Pause »/« Unpause », `PauseCitizenMessage` ; « Restart » visible si en pause, `RestartCitizenMessage` (`:198-220,436-447`) |
| `mode` | `:69-72`, label (20,189), bouton (150,187) 169×17 `builder_button_large` | (40,378) ; (300,374) 338×34 | modes en boucle **sans LOCKED** pour un lieu de travail (`:166-181`) → `BuildingHiringModeMessage` |
| `showEmployed` | `:74-77`, (200,208) 29×17 `very_small` « No »/« Yes » | (400,416) 58×34 | montre aussi les employés d'ailleurs (`:313-344`) |
| `cancel` | `:79-80`, (375,10) `button_x` | (750,20) 28×30 | rouvre la hutte (`:143-149`) |

Tri : employés ici, sans emploi, autre métier, non assignables, puis distance du logement arrondie à 40 (sans logis = 100), puis nom (`:266-284,349-377`). HyColony trie seulement les sans-emploi, par distance puis nom (`hc:app/view/BuildingViews.java:121-145`).

Manques : compétences par candidat (`CitizenData.skills()` existe), employés d'ici dans la même liste, « Montrer les employés » (`WorkerModule.hire` refuse un employé, `hc:job/WorkerModule.java:96-101`), boutons de métier. Pause et Redémarrer : C (pause d'un citoyen). **Écart** : le bouton de mode HyColony passe par LOCKED (`pl:ui/BuildingMainTab.java:163`, `HiringMode.next()` `hc:job/HiringMode.java:14-17`).

#### `WindowHutNameEntry` (`xml/windowhutnameentry.xml`, `gui/WindowHutNameEntry.java`) — B

- Fenêtre sans texture, assombrie (`lightbox`), à la taille par défaut 420 × 240 (`bui:views/BOWindow.java:27,32`) ; ×2 840 × 480.
- Titre blanc « Rename Your Building » (0,100) ; ×2 (0,200).
- `name` (135,110) 150 × 18, `maxlength` 25 ; ×2 (270,220) 300 × 36.
- `done` (110,170) et `cancel` (110,194), boutons vanilla 200 × 20 ; ×2 (220,340) et (220,388) 400 × 40.
- À l'ouverture : le nom personnalisé en minuscules (`:46-50`).
- Terminé : au-delà de **15** caractères, coupé à 15 avec le message `gui.name.toolong` (`:28,55-63`), puis `setCustomName` → `HutRenameMessage` → `building.setCustomBuildingName` sans contrôle (`mc:core/colony/buildings/views/AbstractBuildingView.java:640-644` ; `msg/colony/building/HutRenameMessage.java:56-59`). Puis la hutte se rouvre (`:72-75`).
- HyColony : rien (pas d'action de renommage de hutte). Le renommage de la colonie (`TownHall/Actions.ui`) donne un motif à reprendre.

#### `WindowHutAllInventory` (`xml/windowhutallinventory.xml`, `gui/WindowHutAllInventory.java`) — B

| Élément MC | XML | ×2 | Java |
|---|---|---|---|
| `desc` | `:6`, « All Items » | (60,28) | — |
| `names` (filtre) | `:7`, (15,30) 132×18, 25 car. | (30,60) 264×36 | filtre sur l'id ou l'infobulle, 10 images après la frappe (`:97-115,239-253`) ; tri par distance de Levenshtein au filtre (`:254`) |
| `sortStorageFilter` | `:8-10`, (158,31) 14×15 `mini` | (316,62) 28×30 | 5 états « v^ » (aucun), « A^ », « Av », « 1^ », « 1v » ; état **statique**, partagé entre fenêtres (`:63,162-200,256-276`) |
| `allinventorylist` | `:11-20`, (15,55) 165×156, `box` 18 | (30,110) 330×312, ligne 36 | icône 17 ; nom tronqué à 17 caractères (20,3) ; quantité abrégée « 1.2k » (`mc:api/util/Utils.java:178-191`), exacte avec Maj (`:321-341`) ; contenu de la hutte et de ses étagères (`:205-232`) |
| `locate` « ? » | (141,1) 14×15 `mini` | (282,2) 28×30 | ferme la fenêtre, message `coremod.locating`, surligne 60 s chaque étagère qui contient l'objet, avec le nombre et une couleur selon la quantité (`:117-148`) |
| `back` | `:21-23`, (50,215) 86×17 `medium` | (100,430) 172×34 | rouvre la fenêtre précédente (`:153-156`) |

HyColony : sous-vue de l'onglet Principal (`pl:ui/HutStockPanel.java`, écart `:13-14`), tri fixe par quantité décroissante (`hc:app/view/BuildingViews.java:89-103`, écart `:93`). Manquent : filtre, bouton de tri, Localiser. Les surlignages existent déjà (`pl:ui/hut/FieldsTab.java:96-99`, `Highlights`), mais la vue ne dit pas quel conteneur tient quoi.

#### `WindowInfo` (`xml/windowinfo.xml`, `gui/WindowInfo.java`) — A

- Papier, bandeau (44,12) 6 + 90 + 6 ; ×2 (88,24) 12 + 180 + 12.
- `pages` (20,14) 150 × 210 ; ×2 (40,28) 300 × 420.
- `exit` et `prevPage` superposés en (13,13) 18 × 10, `turn_page_left` ; `nextPage` (159,13) `turn_page_right` ; ×2 (26,26) et (318,26) 36 × 20.
- `pageNum` (158,222) ; ×2 (316,444).
- Une page par clé `com.minecolonies.coremod.info.<type>.<i>` (`:42-67`) : titre rouge `.name` (30,0) 90 × 11, texte noir (0,16) 150 × 194.
- `lang:` a 4 pages pour `builder`, `farmer`, `deliveryman`, `warehouse` (0 à 3), aucune pour `residence`.
- HyColony : rien. Il faut seulement porter ces textes (en-US et fr-FR).

#### `WindowHutGuide` (`xml/windowhutguide.xml`) — C

Plein écran 960 × 540, image `guide/background.png`, trois textes et « confirm ». MC l'ouvre à la place de la hutte du constructeur tant que le succès `minecolonies/check_out_guide` n'est pas obtenu (`gui/huts/WindowHutBuilderModule.java:46-66`). Il faut un système de succès : à repousser.

### 8.7 Textures à copier

Déjà dans `ui:Mc/` : `builder_button_medium`, `_medium_disabled`, `_medium_large`, `_medium_large_build`, `_medium_large_disabled`, `_mini`, `_very_small`, `button_x`, `edit`, `turn_page_left/right`. Tailles d'origine vérifiées dans `tex/`.

| Fenêtre | À copier (taille d'origine) |
|---|---|
| Cadre et Principal | `builderhut/builder_paper` (192×240), `builder_sketch_left/right` (6×15), `builder_sketch_center` (154×15), `red_wax_information` (17×17), `chest` (25×25) |
| Onglets | `modules/tab_left_side1..4` (32×26) ; icônes 20×20 : `main`, `crafting`, `inventory`, `settings`, `info`, `stock`, `stats`, `field`, `entity` |
| Réglages | `builder_button_small` (64×17) |
| Champs, statistiques | `builder_button_mini_check`, `_mini_disabled`, `_mini_disabled_check` (14×15) |
| Embauche, affectation | `builder_paper_wide2` (400×244), `builder_button_quite_small` (44×16), `builder_button_large` (169×17) |
| Ordres de travail | `builder_button_small` |
| Variantes désactivées utiles | `builder_button_small_disabled`, `_quite_small_disabled`, `_very_small_disabled`, `_large_disabled` (présentes dans `tex/builderhut/`) |
| Guide (C) | `guide/background` (960×540) |

Ne pas copier, car ce sont des textures de Mojang absentes de `sources/` : les boutons vanilla sans `source` (recettes, « ↥ » de l'entrepôt, nom de hutte : `bui:controls/ButtonImage.java:23`), `minecraft:textures/misc/shadow.png` (tâches) et les icônes d'étape `iron_hoe`, `wheat_seeds` (champs). Pour celles-ci, prendre une icône d'objet Hytale ou un bouton Hytale (écart à nommer).

### 8.8 Widgets Hytale à ajouter à ceux de l'hôtel de ville

Déjà là : `ui:Mc/Book.ui` (`@Ink`, `@InkCentered`, `@Faded`, `@Heading`, `@WideButtonStyle`, `@MiniButtonStyle`, `@DropdownStyle`, `@MediumDropdownStyle`), les lignes `ui:Mc/*.ui` (`ResourceRow`, `OrderRow`, `PickerRow`, `StatLine`, `CitizenRow`…), les contours de ligne (`OutlineSize`/`OutlineColor`, `Mc/ActionRow.ui:9-10`), la page tournée (`TownHall/Permissions.ui:134-143`) et `$C.@TextField` (`TownHall/Citizens.ui:40`).

- **Onglet latéral** : un `Button` à fond `tab_left_sideN` et un `Group` d'icône par-dessus, avec `TooltipText` et `TextTooltipStyle`. C'est le motif du sceau `@Seal` (`ui:TownHall.ui:15-21`). L'image N se tire dans le plugin avec une graine stable par hutte. Le `hashCode` du `BlockPos` de Minecraft n'est pas dans `sources/` : la même suite que MC n'est pas vérifiable.
- **Styles de bouton** à ajouter dans `Book.ui` :
  - `small` (64×17), `quite_small` (44×16, texte ×0.8), `large` (169×17), `very_small`, et un `@MediumButtonStyle` (aujourd'hui en ligne dans `Mc/RankButtonRow.ui:16`) ;
  - un état `Disabled` pour `@WideButtonStyle` et `@MiniButtonStyle`. Sans attribut `disabled`, BlockUI garde la même texture et grise le texte (`bui:controls/ButtonImage.java:71,95`) ; `TextButtonStyle` accepte `Disabled:` (`v:Common.ui:119,127`).
- **Case mini cochée** (champs, masquer les zéros) : deux boutons dont un seul est `Visible`, comme Localiser (`pl:ui/hut/FieldsTab.java:96-99`).
- **Infobulle sur un texte** (« Nom (id) » d'un travailleur, nom d'ordre, positions d'une tâche) : un `Label` accepte `TooltipText` et `TextTooltipStyle` (`v:Pages/PrefabEditorSaveSettings.ui:5,104-107`) ; le serveur pose aussi `.TooltipTextSpans` (`srv:builtin/adventure/memories/page/MemoriesPage.java:227`). **[in-game]** Survol d'un `Label` dans une liste défilante.
- **Texte enroulé** (`desc` des réglages, `job` de l'affectation) : `Wrap: true` dans le `LabelStyle` (`v:Pages/PrefabEditorSaveSettings.ui:7`).
- **Texte réduit** (×0.8, ×0.7 de l'embauche) : `FontSize` plus petit.
- **Fenêtre assombrie** (nom de hutte) : `$C.@PageOverlay` sans papier, comme l'hôtel de ville.
- **Son** : MC joue `BOOK_PAGE_TURN` sur un onglet de module. HyColony a seulement `Sounds/DefaultTabActivate.ogg` (`ui:TabButton.ui:5-7`) ; aucun son de page n'a été cherché dans les assets.
- Liste déroulante (statistiques) : déjà là, mais le module est C.

### 8.9 Questions ouvertes et risques

1. **Mode d'embauche des huttes à travailleurs** : MC saute LOCKED (`gui/WindowHireWorker.java:166-181`), HyColony non (`pl:ui/BuildingMainTab.java:163`). La résidence, elle, passe bien par LOCKED (`gui/WindowAssignCitizen.java:154-165`). C'est un bug de fidélité à corriger.
2. **Droit d'ouverture de l'inventaire de hutte** : `MANAGE_HUTS` chez MC (`OpenInventoryMessage` sans redéfinition), `OPEN_CONTAINER` chez HyColony (`pl:ui/HutStorage.java:36-38`). Faut-il s'aligner, ou est-ce un écart voulu ?
3. **Onglet ouvert** : MC n'en montre aucun (chaque onglet est une fenêtre). Garder un état visible serait un écart à nommer.
4. **Contenus déplacés chez HyColony** : Résidents (onglet au lieu de la page principale et de `WindowAssignCitizen`), Fertiliser (Champs au lieu de Réglages), embauche en ligne et mode sur la page principale (au lieu de `WindowHireWorker`), sous-vue Inventaire total. Ils sont à remettre aux places de MC.
5. **Renommer** : MC coupe à 15 caractères côté client, le champ en accepte 25, le serveur ne vérifie rien. Faut-il reproduire tel quel ?
6. **Libellé « Work Orders » hors du papier** (`lh/layoutworkorders.xml:6`, x = 201 > 190) : bizarrerie MC, à reproduire ou non.
7. **Fenêtres absentes** : `WindowHutGuide` (succès), `WindowConfirm` (déjà remplacé par un double clic), apprentissage de recette (conteneur d'artisanat, remplacé par une liste).
8. **Stock minimum, statistiques de hutte, options de l'entrepôt, recettes du constructeur, pause d'un citoyen, recherche** (réglages `recipemode`, `buildmode`) : systèmes C. Les onglets correspondants manqueront tant qu'ils n'existent pas. Les lignes de réglage liées à la recherche peuvent s'afficher désactivées, avec la raison de MC.
9. **Textures Mojang** (boutons vanilla, ombre, houe, graines) : à remplacer par des éléments Hytale, écart à nommer.
10. **[in-game]** Rendu des textures étirées (`builder_paper` 192×240 dans 190×244, `sketch_center` 154 dans 130, `chest` 25 dans 17) une fois agrandies ×4 puis affichées ×2.
