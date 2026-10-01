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
