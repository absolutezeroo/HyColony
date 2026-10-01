# Hôtel de ville complet, comme MineColonies

Date : 2026-10-01. Statut : à relire par l'utilisateur.

## 1. But

L'hôtel de ville de HyColony doit montrer ce que montre la fenêtre de MineColonies, rempli comme MineColonies le remplit, avec son apparence (le livre, CLAUDE.md § 7). L'apparence est en place depuis `7d338928`, mais le contenu reste celui de l'ancienne fenêtre et quatre onglets sur sept seulement existent.

Ce chantier couvre tout ce que le cœur sait faire, ou presque (statuts A et B de l'inventaire). Les éléments qui demandent un système entier absent (statut C) restent pour plus tard, chacun avec son sous-projet.

Références :

- inventaire élément par élément, avec les sources MC et HyColony : `docs/research/ui-vs-minecolonies.md` § 1, « Inventaire complet, 2026-10-01 » (abrégé « Inv. ») ;
- MC : `sources/minecolonies/` (fenêtres `core/client/gui/townhall/*.java`, layouts `gui/townhall/*.xml`, messages `core/network/messages/server/colony/…`, `PermissionsMessage`) ;
- règle de portage d'une fenêtre : le XML **et** la classe Java qui le remplit **et** le message serveur de chaque bouton. Un écart n'est admis que si Hytale ou un système absent l'impose, et il porte un commentaire `Deviation from MC`.

## 2. Périmètre

**Dans ce chantier** (cinq lots, dans cet ordre) :

1. Accueil, refait fidèlement, et la fenêtre de construction de MC (`WindowBuildBuilding`) ;
2. Informations : le journal d'événements de la colonie ;
3. Citoyens : tri, recherche, sélection, détail et rappel ;
4. Réglages : les réglages de colonie que le cœur applique ;
5. Permissions : joueurs, rangs et journal des refus.

**Hors de ce chantier** (statut C, un sous-projet chacun, plus tard) : bonheur ; santé et nourriture ; alliances et diplomatie (tout l'onglet Alliances) ; mercenaires ; carte de la colonie ; bannière et couleur d'équipe ; statistiques de production et leur intervalle ; blocs libres et sceptre de permission ; naissances, enfants qui grandissent et mort des citoyens (dans le journal) ; messages d'entrée et de sortie ; ruban de chantier ; rendu du citoyen dans la fenêtre. Le bouton Patreon n'est pas porté.

## 3. Architecture de la fenêtre

### 3.1 Cadre et pages

- `TownHall.ui` ne garde que le cadre : le livre, les sept emplacements de sceaux et leurs rubans courts et longs, et un groupe `#Page` vide.
- Chaque onglet a son propre `.ui` sous `Pages/HyColony/TownHall/` (`Actions.ui`, `Info.ui`, `Citizens.ui`, `Stats.ui`, `Settings.ui`, `Permissions.ui`). La page ajoute dans `#Page` **seulement** le `.ui` de l'onglet ouvert. On évite ainsi un `.ui` géant, et chaque reconstruction n'envoie que l'onglet visible.
- Côté Java, une classe de rendu par onglet (`TownHallActionsTab`, `TownHallInfoTab`, …), qui reçoit la vue et lie ses boutons. `TownHallPage` ne fait que choisir l'onglet, poser les sceaux et déléguer. Aucune classe ne dépasse les tailles du § 2 de CLAUDE.md : un onglet qui grossit (Permissions) se découpe par sous-page.
- Les sceaux reprennent **les emplacements exacts de MC** (`windowtownhall.xml:57-70`), sans les tasser. Le sceau des Alliances n'est pas affiché tant que le système manque. `Deviation from MC: no Alliances tab (no diplomacy system)` ; l'emplacement reste vide.
- L'infobulle remplace le ruban de survol (écart déjà documenté : Hytale n'a pas d'équivalent à `onHoverId` qui montre un autre élément).

### 3.2 Vues et actions

- Chaque onglet lit une partie de `TownHallView`, qui devient un record par onglet (`TownHallView.Actions`, `.Info`, `.Citizens`, `.Settings`, `.Permissions`), construit par `TownHallViews`. Seule la partie de l'onglet ouvert est utile, mais le coût de construction est faible ; on garde une seule vue pour rester simple.
- Chaque bouton appelle une action du cœur, qui vérifie la permission de MC puis réaffiche la fenêtre (§ 7 de CLAUDE.md). Permission par défaut d'un message de colonie MC : `MANAGE_HUTS` ; les messages de permissions vérifient `EDIT_PERMISSIONS` (Inv., « Règle commune »).
- L'onglet ouvert reste l'état de la page (`keepTabOf`), comme aujourd'hui.

### 3.3 Widgets Hytale nouveaux

- **Liste déroulante** : `$C.@DropdownBox` (`Common.ui:494-540`), `.Entries` et `.Value` posés par le serveur, choix lu par `ValueChanged` (exemples vanilla : Inv., « Widgets »). Son style reçoit les textures de MC (`builder_button_medium_large`, `builder_button_medium`). **[in-game]** : rendu de ces textures dans une liste déroulante.
- **Interrupteurs** : comme chez MC, des boutons texte marche/arrêt (`builder_button_very_small`), pas des cases à cocher.
- **Pages tournées** (Permissions) : le motif des onglets (des groupes dont un seul est visible), avec `turn_page_left`/`right` et le numéro de page.

### 3.4 Textures

Les textures manquantes listées par l'inventaire (Inv., « Textures MC par onglet ») sont copiées de `sources/` comme les premières : ×4 au plus proche voisin, en `@2x`, dans `Pages/HyColony/Mc/`. Celles de Mojang (`minecraft:textures/gui/icons.png`, épée en diamant) ne sont pas copiées ; aucun élément de ce chantier n'en a besoin.

### 3.5 Textes

Les libellés reprennent ceux de MC (`sources/minecolonies/.../lang/manual_en_us.json`, mêmes mots en en-US), avec une traduction française à nous. Chaque texte passe par une clé dans les deux `.lang` (`add-lang-key`).

## 4. Lot 1 : Accueil et fenêtre de construction

### 4.1 Onglet Accueil (`WindowMainPage`, `layoutactions.xml`)

Page de gauche :

- **Grand ruban** : le nom de l'hôtel de ville suivi de son niveau, « Town Hall 5 » (`WindowMainPage.java:93-94`). `TownHallView.Actions` reçoit le niveau.
- **En-tête vert** « Colony Name: » (`layoutactions.xml:4`, clé MC `currtownhallname`), puis le crayon et le nom en texte (`:6-9`), aux positions de MC doublées. Le champ de saisie en ligne disparaît.
- **Crayon** : visible pour qui a `MANAGE_HUTS`, il ouvre la fenêtre de renommage (§ 4.2).
- **Build Options** (`build`, texture `builder_button_medium_large_build`) : sans ordre en cours, il ouvre la fenêtre de construction (§ 4.3) ; avec un ordre en cours, son libellé devient « Annuler la construction / l'amélioration / la réparation / la déconstruction » et un clic annule l'ordre (`AbstractBuildingMainWindow.buildClicked`, l. 65-86 ; action existante `WorkOrderActions.cancel`).
- **Requêtes** : garde sa place, à l'emplacement du bouton Carte. `Deviation from MC: requests go through the clipboard item in MC, which HyColony does not have yet.`
- Carte et Mercenaires : absents (C).

Page de droite, « Cosmetic Options: » (`layoutactions.xml:25-90`) :

- **Colony Pack** (`colonyStylePicker`) : le pack de style par défaut de la colonie. Chez MC, il ouvre `WindowSwitchPack` de Structurize ; ici, une liste déroulante des styles disponibles, puisque Structurize n'est pas porté (écart forcé). Le cœur gagne un **style de colonie** : persisté, choisi à la fondation (le style de l'hôtel de ville), il sert de style proposé par défaut aux nouvelles huttes (MC `IColony.getStructurePack`). Action `ColonyAdministration.setStyle`, `MANAGE_HUTS` (`ColonyStructureStyleMessage`).
- **Citizen Style** et **Name Pack** : affichés **désactivés**, avec « default », comme MC les montre à un joueur sans Patreon (`WindowMainPage.java:260-267`). HyColony n'a pas de Patreon : ils restent désactivés.
- Drapeau de colonie, couleur de colonie, Patreon : absents (C ou hors-port).
- Le propriétaire et le jour, propres à HyColony, disparaissent de l'onglet (le propriétaire reste dans `/hycolony info`).

### 4.2 Fenêtre de renommage (`WindowTownHallNameEntry`)

- Page à voile : titre (`townhall.rename.title`), champ, Terminé et Annuler (`windowtownhallnameentry.xml`). Terminé renomme puis rouvre l'hôtel de ville ; Annuler le rouvre sans rien changer (`WindowTownHallNameEntry.java:43-62`).
- Règle de MC pour le renommage (`TownHallRenameMessage.java:17-18,56`) : un nom de plus de 25 caractères est **tronqué à 24**. `ColonyAdministration.rename` suit cette règle, séparée de celle de la fondation (32 caractères, inchangée). Le nom vide reste refusé (Hytale envoie un champ vide si le joueur efface tout ; MC ne le refuse pas, mais un nom vide casserait l'affichage : `Deviation from MC`).

### 4.3 Fenêtre de construction (`WindowBuildBuilding`, `windowbuildbuilding.xml`)

Elle remplace le sous-panneau `BuildOptionsPanel` de la fenêtre de hutte : le bouton « Options de construction » de **toutes** les huttes l'ouvre, comme chez MC. Page à voile, positions de MC doublées :

- en haut : `<`, liste déroulante des styles, `>` ; liste déroulante des constructeurs ; croix `button_x` qui revient à la fenêtre d'origine ;
- au centre : la liste des ressources du plan du niveau visé (icône, nom, quantité), le niveau suivant si la hutte peut monter, sinon le niveau actuel (`updateResources`, l. 298-330). Le cœur calcule cette liste depuis le plan (`BlueprintSource`) ; plan introuvable : la liste est vide et seuls Déconstruire et Ramasser restent, comme MC ;
- en bas : Réparer (« Construire » si la hutte est déconstruite), Construire ou Améliorer (caché au niveau max ; infobulle d'avertissement et confirmation, comme aujourd'hui), Déconstruire, Ramasser. Les règles d'affichage sont celles de `WindowBuildBuilding.java:139-160`, déjà portées dans `BuildingView.allowed`.
- **Choix du constructeur** : la première entrée est « Builder: » (automatique), puis les constructeurs de la colonie qui ont un travailleur, triés par distance à la hutte (`updateBuilders`, l. 246-262). Le cœur gagne ce paramètre : `WorkOrderActions.order` reçoit le constructeur choisi, et l'ordre est réservé à ce constructeur (MC `BuildRequestMessage` avec la position du constructeur). Les règles de réservation sont celles de MC, vérifiées au plan.

## 5. Lot 2 : Informations (`WindowInfoPage`, `layoutinfo.xml`)

- **Page de gauche** : liste déroulante des intervalles (hier, semaine, 100 jours, depuis toujours ; défaut « depuis toujours », `WindowInfoPage.java:49,65-93`) et la liste des événements, du plus récent au plus ancien, filtrée par `jour >= jourActuel - intervalle` (`:106-124`).
- **Ligne d'événement** (`:126-164`) : l'action, le nom du citoyen ou « hutte niveau N », et la position x y z.
- **Cœur** : `EventLog.Entry` gagne une position facultative (schéma de sauvegarde relevé par `add-migration` ; une ancienne entrée n'a pas de position). Chaque producteur existant la renseigne.
- Les trois types d'événements propres à HyColony (`buildingPlaced`, `buildingRemoved`, `debrisLost`) ne sont pas journalisés par MC. Ils ne sont pas affichés. S'ils n'ont aucun autre lecteur (à vérifier au plan), on cesse de les produire.
- **Page de droite** : les ordres de travail, déjà faits.

## 6. Lot 3 : Citoyens (`WindowCitizenPage`, `layoutcitizens.xml`)

- **Liste** (page de droite) : un bouton par citoyen, **trié par nom** ; le premier est sélectionné à l'ouverture ; le bouton du citoyen sélectionné est désactivé (`builder_button_medium_large_disabled`) ; son infobulle donne les compétences « nom : niveau » (`WindowCitizenPage.java:37,71-74,106,190-207`).
- **Recherche** : un champ de 32 caractères au-dessus de la liste, filtre sans casse sur le nom ou le métier (`:81-107`). Le filtre est un état de la page.
- **Détail** (en haut de la page de droite) : le métier en gras et le sceau de genre (`colonist_wax_male_smaller`/`_female_smaller`) ; le bouton « Rappeler ».
- **Rappel d'un citoyen** : action nouvelle `CitizenActions.recall`, `MANAGE_HUTS` ; le citoyen est ramené à l'hôtel de ville, son corps est recréé s'il manque, sinon le message d'échec de MC (`RecallSingleCitizenMessage.java:66-90`). Le cœur réutilise ce que fait déjà le rappel des résidents (`HousingActions.recall`).
- Santé, bonheur, saturation, liste des modificateurs de bonheur (page de gauche) et rendu du citoyen : absents (C). La page de gauche reste vide. `Deviation from MC: no happiness, health nor food system yet.`

## 7. Lot 4 : Réglages (`WindowSettings`, `layoutsettings.xml`)

- Un bouton marche/arrêt par réglage, aux positions de MC : **movein**, **job** (embauche automatique), **housing** (logement automatique). Chacun déclenche `ColonyAdministration.toggleSetting`, `MANAGE_HUTS` (`TriggerSettingMessage.java:88-93`), puis réaffiche l'onglet.
- **Cœur** : `ColonySettings` gagne `moveIn` (défaut vrai, persisté). L'arrivée des citoyens initiaux le respecte (`CitizenManager.java:233` chez MC).
- Messages d'entrée et de sortie, ruban de chantier : absents (C).

## 8. Lot 5 : Permissions (`WindowPermissionsPage`, `layoutpermissions.xml`)

Trois sous-pages, tournées par `<` `>` avec leur numéro. Sans `EDIT_PERMISSIONS`, les champs et boutons d'ajout sont désactivés avec l'infobulle de MC (`:314-339`).

1. **Joueurs** :
   - champ + « Ajouter » : ajoute le joueur nommé au rang Neutre (`PermissionsMessage.AddPlayer`, `EDIT_PERMISSIONS`) ;
   - bouton des joueurs en ligne : la liste des joueurs en ligne qui ne sont pas membres ; un clic remplit le champ ;
   - liste des membres triés par rang : nom, rang (texte pour le propriétaire, liste déroulante des rangs sauf Propriétaire pour les autres), bouton de retrait (désactivé pour le propriétaire) ; les règles de retrait sont celles de `PermissionsMessage.RemovePlayer` (`:561-568`) ;
   - journal des refus : les 100 derniers refus d'action, du plus récent ; « Ajouter » sur une ligne dont le joueur est connu (`AddPlayerOrFakePlayer`).
2. **Rangs** :
   - champ + « Ajouter un rang » : nom non vide et unique, premier identifiant libre au-delà des rangs initiaux (`Permissions.java:1054-1068`) ;
   - liste des rangs (Officier choisi par défaut) ; type du rang choisi (gestionnaire, hostile, aucun) par liste déroulante ; « Retirer » pour un rang non initial, ses joueurs passant Neutre (`:1078-1090`) ;
   - un bouton marche/arrêt par action (les 26 de MC), désactivé quand `canAlterPermission` le refuse.
3. **Blocs libres** : absente (C). La page n'est pas proposée ; `<` `>` tournent entre les deux premières.

**Cœur** :

- `Permissions` : `canAlterPermission` et `alterPermission` (`Permissions.java:304-323`), l'exception du rang Neutre qui n'a jamais `EDIT_PERMISSIONS` ni `TELEPORT_TO_COLONY` (`:641`), `addRank`, `removeRank`, `setRankType`, `removePlayer`, `addPlayer`, avec les règles de MC ; persistance des rangs personnalisés (`add-migration` si le format change).
- **Journal des refus** : chaque refus d'action (`ColonyProtection`) est enregistré, 100 au plus, avec le joueur, l'action et la position ; persisté comme chez MC (`BuildingTownHall.java:110`).
- **Port `PlayerDirectory`** : `uuidByName(String)` (joueur connu du serveur, en ligne ou non) et `name(UUID)`. Son adaptateur Hytale s'appuie sur une API vérifiée dans les sources décompilées (`hytale-api`) ; s'il n'en existe pas pour un joueur hors ligne, l'ajout se limite aux joueurs en ligne et l'écart est documenté.
- Actions : `ColonyAdministration` délègue à une nouvelle classe `PermissionActions` (une responsabilité : modifier les permissions), `EDIT_PERMISSIONS` partout, comme `PermissionsMessage`.

## 9. Robustesse et persistance

- Toute action vérifie l'existence de la colonie, du joueur, du rang ou du citoyen, et renvoie `false` sans lever d'exception ; l'index d'une ligne vient de la vue affichée et est borné.
- Les nouveaux états persistés (style de colonie, position des événements, `moveIn`, rangs personnalisés, journal des refus) passent par `MigrationChain` avec une fixture de l'ancienne version, et une lecture tolérante (§ 5 de CLAUDE.md).
- Une liste déroulante ou un champ renvoyant une valeur inconnue est ignoré, sans plantage.

## 10. Tests

- **Cœur, en TDD** : chaque règle de MC portée a son test (troncature du renommage, style de colonie par défaut d'une nouvelle hutte, réservation par constructeur choisi, filtre des événements par intervalle, tri et filtre des citoyens, rappel d'un citoyen, `moveIn` à l'arrivée, chaque règle de `canAlterPermission`/`alterPermission`, retrait de joueur, ajout et retrait de rang, exception Neutre, journal des refus borné à 100), et chaque migration sa fixture.
- **Plugin** : pas de tests unitaires (§ 8 de CLAUDE.md). `docs/TESTING.md` reçoit un point par onglet et pour chaque fenêtre (renommage, construction), avec les vérifications **[in-game]** : listes déroulantes texturées, interrupteurs, pages tournées, recherche.
- Chaque lot : build vert, relectures (`hycolony-reviewer`, `ui-lang-checker`, `mc-fidelity-checker`), test en jeu par l'utilisateur, puis commit.

## 11. Points ouverts, à trancher au plan

- API Hytale pour retrouver un joueur hors ligne par son nom (§ 8).
- Règles exactes de réservation d'un ordre par un constructeur choisi chez MC (§ 4.3).
- Lecteurs éventuels des trois types d'événements propres à HyColony (§ 5).
- Rendu d'une liste déroulante avec les textures de MC **[in-game]**.
