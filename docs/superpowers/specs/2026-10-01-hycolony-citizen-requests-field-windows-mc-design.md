# Fenêtres du citoyen, des requêtes et du champ, comme MineColonies

Date : 2026-10-01. Sous-projets 2 et 3 du portage des fenêtres, après les huttes (`2026-10-01-hycolony-hut-windows-mc-design.md`, dont cette spec reprend les règles d'apparence et le § 3.3 des styles). Validé par l'utilisateur : « Oui, autonomie complète ». La fondation de colonie et l'outil de construction restent pour un sous-projet suivant.

## 1. But

La fenêtre du citoyen, les requêtes (l'onglet Requêtes du citoyen, le presse-papiers et le détail d'une requête) et la fenêtre du champ prennent l'apparence et le contenu de MineColonies :

- positions et tailles des `.xml` de MC ×2, textures de `sources/` agrandies ×4 au plus proche voisin en `@2x` sous `Pages/HyColony/Mc/` (CLAUDE.md § 7) ;
- le contenu que montre la classe Java de chaque fenêtre de MC, à sa place, et chaque bouton appelle l'action du cœur qui correspond au message de MC, avec son droit ;
- un écart seulement s'il est imposé, nommé `Deviation from MC:` et listé au § 8.

Succès : en jeu, ces fenêtres ressemblent à celles de MC (papier du colon, onglets latéraux à icône, presse-papiers, papier court du champ avec les boutons de l'épouvantail) et montrent ce que montre MC.

## 2. Périmètre

| Lot | Contenu | Sources MC |
|---|---|---|
| 1 | Fenêtre du citoyen : cadre, onglets, Principal, Métier, Inventaire | `gui/citizen/nav.xml`, `main.xml`, `job.xml`, `AbstractWindowCitizen`, `MainWindowCitizen`, `JobWindowCitizen`, `CitizenWindowUtils` |
| 2 | Arbre des requêtes (onglet Requêtes du citoyen), détail d'une requête, Annuler | `layouthuts/layoutrequeststree.xml`, `RequestTreeWindowModule`, `RequestWindowCitizen`, `windowrequestdetail.xml`, `WindowRequestDetail`, `UpdateRequestStateMessage` |
| 3 | Presse-papiers : l'objet et sa fenêtre | `ItemClipboard`, `windowclipboard.xml`, `WindowClipBoard` |
| 4 | Fenêtre du champ et son choix de graine | `windowfield.xml`, `containers/WindowField`, Structurize `WindowSelectRes`, `windowselectres.xml`, `layoutselectres.xml` |

Hors périmètre (systèmes absents) : bonheur (barre, onglet), famille (onglet), onglet Debug, climat des cultures de MC, statut visible (`VisibleCitizenStatus` : MC le pose depuis chaque IA de métier et l'affiche aussi au-dessus de la tête ; c'est un système de jeu à porter à part, avec les icônes de tête). Leurs éléments n'apparaissent pas.

## 3. Fenêtre du citoyen (lot 1)

### 3.1 Cadre

- Une page Hytale (`CitizenPage`) ; papier `citizen/colonist_paper.png` 380 × 488 en (40, 0) ; la fenêtre fait 420 × 488.
- Onglets latéraux de `nav.xml` : bouton 64 × 52 en (0, y) et icône 40 × 40 en (10, y + 6), y = 72, 128, 184, 236, 288, 340, 392 pour Principal (`tab_left_side1`, `info`), Requêtes (`side2`, `requests`), Inventaire (`side3`, `inventory`), Bonheur (`side2`, `happiness`), Famille (`side1`, `family`), Métier (`side3`, `main`), Debug (`side3`, `settings`). Les fonds sont fixés par le `.xml` (pas de tirage, contrairement aux huttes). Infobulle de chaque icône (`gui.citizen.main`, `requests`, `inventory`, `job`).
- Bonheur, Famille et Debug n'apparaissent pas (§ 2). Métier n'apparaît que si le citoyen a un lieu de travail (MC : `AbstractBuildingView` qui n'est pas la bibliothèque).
- Même architecture que les huttes : un document `.ui` par onglet ajouté dans `#Page`, l'onglet ouvert gardé à travers les ré-affichages du cœur. Inventaire ouvre l'écran de conteneur du jeu à la place de la fenêtre, comme MC (`OpenInventoryMessage`) ; demandé par l'utilisateur le 2026-10-01, il remplace le panneau dessiné dans la fenêtre.

### 3.2 Principal (`main.xml`, `MainWindowCitizen`)

- Nom centré, noir, 328 × 22 en (66, 28) ; décor `colonist_text_decor_down` 164 × 32 en (148, 58).
- Barre de santé en (110, 84) : 10 cœurs de 18 px (`createHealthBar`), le libellé « santé / 2 » à sa droite. La santé vient du corps Hytale du citoyen (nouvelle lecture du port `CitizenBodies`), ramenée à l'échelle de MC (20 points) ; cœurs rouges, dorés, verts et bleus selon les seuils de MC.
- Gardées **à la demande de l'utilisateur** (Javadoc de `CitizenMainTab`) : sous la barre de nourriture, à la place de la barre de bonheur absente, deux lignes de 12 px en (66, 146) et (66, 162) : « Métier : X — lieu de travail » et l'activité (« En attente de … », l'état, ou la ligne du métier).
- Barre de saturation en (110, 106) : `MAX_SATURATION / 6` icônes `empty`, puis `full` pour chaque tranche de 6, puis `half` (règle de `createSaturationBar`, sur dix par ligne).
- Compétences en liste en (110, 180) de 210 × 286, une ligne de 26 : libellé aligné à droite 136 × 24, icône `entity/skills/small/<skill>` 18 × 18 en (140, 4), niveau en (164, 2). Ordre et ajouts gardés **à la demande de l'utilisateur** (Javadoc de `SkillRows`) : les compétences du métier d'abord, en gras, puis les autres dans l'ordre de `main.xml` ; sous chaque ligne, une barre d'XP de 2 px vers le niveau suivant, et « XP x / y » en infobulle.
- Boutons + et − (24 × 26) de chaque compétence, en (112, 0) et (136, 0) de sa ligne, montrés au survol de l'icône pour un joueur en mode créatif seulement (BlockUI n'affiche le panneau de survol que s'il est actif, et `createSkillContent` ne l'active qu'en créatif) : le niveau monte ou descend d'un (MC `AdjustSkillCitizenMessage` : `MANAGE_HUTS`, refus annoncé, puis créatif).
- Santé : celle du corps ramenée à 20 points ; sans corps, 20 comme MC (`CitizenDataView.getHealth`).
- Ruban `colonist_decor_up_ribbon_smaller` 30 × 98 en (354, 418) ; sceau de genre `colonist_wax_male_smaller` ou `_female_smaller` 60 × 60 en (340, 408).

### 3.3 Métier (`job.xml`, `updateJobPage`)

« Job: X » centré en (90, 64), l'explication `gui.citizen.job.desc` en (100, 88) sur 300 × 60, puis six lignes icône 22 × 22 et texte : primaire « (100% XP) », complémentaire « (10% XP) », adverse « (-10% XP) » à y = 148, 172, 196 ; secondaire « (50% XP) », « (5% XP) », « (-5% XP) » à y = 248, 272, 296 ; icône en x = 100, texte en x = 130. Le cœur a déjà ces données (`CitizenView.JobSkills`).

## 4. Requêtes (lots 2 et 3)

### 4.1 Arbre des requêtes (`layoutrequeststree.xml`, `RequestTreeWindowModule`)

- Liste 316 × 368 ; une ligne de 80, contour 1 px, décalée de 4 px par profondeur (MC : 2 px).
- Ligne : icône de l'objet 32 × 32 en (2, 6) (ou, sans objet, l'icône de la requête avec l'infobulle de son résolveur) ; bouton détail `citizen/detail_button` 32 × 32 en (2, 46) avec l'infobulle `gui.requests.details` ; texte court en (40, 4) (pour une tâche par pile, son préfixe et l'objet 18 × 18 en (170, 2) ; sinon la description courte) ; demandeur en (40, 26) ; Fournir 74 × 32 en (40, 46) et Annuler en (140, 46), `colonist_button_small`, texte noir.
- Annuler : sur les racines seulement (`isCancellable`). Fournir : selon le contexte (§ 4.2, § 4.4).
- Ordre : chaque requête ouverte suivie de ses enfants, en profondeur (`constructTreeFromRequest`).

### 4.2 Onglet Requêtes du citoyen (`requests.xml`, `RequestWindowCitizen`)

- Titre « Open requests: » centré 328 × 22 en (66, 28) ; l'arbre en (66, 58).
- Requêtes : celles du citoyen dans sa hutte puis celles de la hutte sans citoyen (−1).
- Fournir visible si la requête est livrable, si c'est une racine ou si son demandeur est à la place de la hutte, et si le joueur est en créatif ou a l'objet (`isFulfillable`). En créatif, l'objet affiché est donné gratuitement (spec HyLens 2026-10-02 lot 3, § 3).

### 4.3 Détail d'une requête (`windowrequestdetail.xml`, `WindowRequestDetail`)

- Fenêtre à part, papier du colon 380 × 266 ; titre « Requests » ; objet 48 × 48 en (24, 60) ; demandeur, position (« x, y, z »), « Resolver: nom » en (80, 60 / 80 / 100) ; description longue 340 × 80 en (24, 120) (ou le préfixe et l'objet d'une tâche par pile) ; Retour, Fournir, Annuler 74 × 32 en (70, 210), (152, 210), (234, 210).
- Fournir et Annuler sont désactivés selon les mêmes règles que la ligne ; chacun ferme le détail. Retour rouvre la fenêtre d'où il vient (citoyen ou presse-papiers).
- `RequestView` du cœur gagne la position du demandeur, le nom du résolveur et la description longue.

### 4.4 Presse-papiers (`windowclipboard.xml`, `WindowClipBoard`, `ItemClipboard`)

- **Objet** `HyColony_Clipboard` (modèle et icône à partir d'un objet Hytale proche, recette d'artisanat à l'établi comme l'outil de construction ; nom `item.minecolonies.clipboard`).
  - Utilisé sur un bloc de hutte d'une colonie : il retient cette colonie (métadonnée de l'objet) et dit `clipboard.registered` avec le nom de la colonie.
  - Utilisé ailleurs : ouvre la fenêtre de la colonie retenue ; sans colonie, dit `clipboard.needcolony`.
- **Fenêtre** : `gui/clipboard.png` 380 × 488 ; titre « Requests » 316 × 22 en (32, 56) ; bouton « ! » 28 × 30 en (300, 46) (`builder_button_mini`), rouge ou vert, infobulle `gui.request.hideshow` ; l'arbre en (32, 88).
- Contenu : les racines des requêtes tenues par le résolveur joueur et le résolveur de réessai, la plus proche du joueur d'abord puis par id (`RequestViews` actuel). Avec « ! » éteint, les requêtes de stock minimum et les requêtes asynchrones des métiers sont cachées ; l'état du bouton est gardé dans l'objet (`hideunimportant`).
- Fournir sur une ligne : garde l'ajout déjà demandé par l'utilisateur (MC ne l'a pas, `RequestsView`).
- Le bouton Requests de l'hôtel de ville, qui remplaçait le presse-papiers, disparaît ; sa place reste celle du plan de la ville de MC (non porté).

### 4.5 Annuler

Nouvelle action du cœur `RequestActions.cancel(player, colonyId, token)` : droit `MANAGE_HUTS` (MC `AbstractColonyServerMessage.permissionNeeded`), toute requête encore ouverte comme le message de MC (les fenêtres ne proposent Annuler que sur les racines de leur arbre), `updateState(CANCELLED)` comme `UpdateRequestStateMessage` ; la fenêtre se ré-affiche ensuite.

## 5. Fenêtre du champ (lot 4, `windowfield.xml`, `WindowField`)

- Papier `builderhut/builder_paper_short` 380 × 260 ; bandeau sketch en (88, 24) ; titre « Field » rouge 260 × 22 en (60, 28).
- « Current farmer: nom » ou « No farmer assigned » en (26, 60) ; « Biome: nom » en (26, 86) si Hytale donne le biome de la position ; la ligne de climat est absente (§ 2).
- « Select seed » (`builder_button_medium` 172 × 34 en (26, 144)) ouvre le choix de graine ; la graine actuelle 32 × 32 en (26, 184).
- Croix des rayons : bloc du champ 32 × 32 en (266, 146) ; boutons `scarecrow.png` 48 × 48 nord (258, 90), sud (258, 186), ouest (210, 138), est (306, 138), avec le rayon écrit dessus, l'état normal ou survolé de la texture (désactivé pour un joueur sans droit), et l'infobulle « direction absolue » puis, en gris italique, la direction relative au regard du joueur (`getDirectionalTranslationKey`).
- Clic : rayon suivant selon `MAX_RANGE` partagé (règle actuelle du cœur `cycleRadius`).
- **Choix de graine** (`WindowSelectRes`) : sans papier, fond assombri ; description blanche en haut ; graine actuelle « de → » ; filtre 300 × 36 ; liste 540 × 240 de lignes 36 : icône, nom blanc, bouton « Select » `builder_button_medium` ; tri : objets que le joueur porte d'abord, puis par nom (sans filtre) ou par distance de Levenshtein au filtre ; Annuler (`structurize` cancel) rouvre le champ.

## 6. Cœur

- Vues : `CitizenView` (santé, saturation, compétences avec celles du métier en tête, genre, créatif, lignes métier et activité gardées, requêtes en arbre avec les nouveaux champs), `RequestsView` (racines, état « ! »), `FieldView` (biome). La ligne `RequestRow` porte tout ce qu'affiche le détail.
- Actions : `RequestActions.cancel`, `CitizenSkillActions.adjust` (créatif), le presse-papiers (retenir une colonie, ouvrir), l'état « ! ».
- Ports : `CitizenBodies.healthPercent` (pour cent de la santé maximale, 0 sans corps vivant, jamais d'exception) ; `PlayerDirectory.isCreative` (existant) ; le biome par `WorldQuery` s'il existe dans l'API.
- La colonie du presse-papiers et « ! » vivent dans l'objet. Seul ajout persisté : le marqueur `async` d'une requête (`CitizenData.createRequestAsync` de MC), clé facultative écrite seulement à vrai, absente = faux, comme `deliveredToCitizen` : une vieille sauvegarde se lit telle quelle, sans migration.
- TDD : ordre des compétences, cœurs et saturation (règles de MC dans le cœur, en nombre d'icônes de chaque sorte), ajustement en créatif seulement, arbre, Annuler (droit, racine), Fournir (règles de `isFulfillable`), presse-papiers (colonie retenue, sans colonie, filtre « ! »).

## 7. Tests en jeu

`docs/TESTING.md` reçoit un point par fenêtre et par onglet, avec les vérifications **[in-game]** (textures, onglets, cœurs, Annuler, presse-papiers sur une hutte puis en l'air, rayons du champ).

## 8. Écarts à MC retenus

- Compétences du métier en tête, barre d'XP, lignes métier, lieu de travail et activité (demandés par l'utilisateur, déjà documentés).
- Bonheur, Famille, Debug, statut visible et climat des cultures absents.
- Le survol qui montre les +/− passe par les événements `MouseEntered` et `MouseExited` (`CustomUIEventBindingType`), donc après un aller-retour au serveur.
- La santé du corps Hytale ramenée à l'échelle de MC (20 points) ; les cœurs vides, rouges et dorés de Minecraft (`gui/icons.png` de Mojang, absent de `sources/`) remplacés par des cœurs dessinés à partir de ceux de `citizen/green_bluehearts.png` de MC.
- Les icônes de compétences gardent la taille de leur source (64 px), sans agrandissement ×4.
- L'ajustement d'une compétence ne demande pas que le corps du citoyen soit chargé (MC sort sans son entité ; ici les compétences vivent dans le cœur).
- Décalage de 4 px par profondeur dans l'arbre (2 px chez MC, ×2).
- Les piles que MC fait défiler sur une requête à plusieurs objets : la première seulement.
- Fournir dans le presse-papiers (ajout demandé).
- Fournir une requête propre à la hutte (sans citoyen) remplit les conteneurs de la hutte ; MC la donne au citoyen de la fenêtre.
- Une requête d'outil montre l'outil rudimentaire de son type ; le logo d'une livraison ou d'une tâche d'artisanat a pour infobulle le nom de son résolveur (MC : « From: », la place dans la file) ; le compte d'une tâche par pile s'écrit à côté de son objet.
- Détail : Retour fonctionne (MC ne le relie à rien, seul Échap revient) ; la place s'écrit « x, y, z » sans la dimension ; Annuler laisse une requête déjà terminée.
- Les textes « En attente de … » et les messages de chat gardent la plage de niveaux d'un outil (l'arbre et le détail suivent les textes court et long de MC).
- Choix de graine : les graines de HyColony, sans les cultures de MC.
- Le niveau d'un outil s'écrit en chiffre ; MC nomme la qualité du niveau (bois, pierre…), dont Hytale n'a pas l'équivalent.
- Presse-papiers : `clipboard.needcolony` va dans le chat (MC : au-dessus de la barre d'objets ; le port `Notifier` n'a que le chat). « ! » s'écrit dans le dernier presse-papiers utilisé, oublié à la déconnexion (MC : l'objet en main).
- Le filtre « ! » cache les requêtes marquées `async`, celles que MC crée par `createRequestAsync` ; elles sont rangées sous la hutte, sans citoyen (écart déjà documenté de `FarmWork.askOnce`). Seul le fermier en crée aujourd'hui.
- Le presse-papiers se redessine toutes les 100 ticks (`AUTO_REFRESH_TICKS` de MC) ; l'onglet Requêtes du citoyen suit la fenêtre du citoyen (toutes les 20 ticks), où MC ne refait l'arbre que toutes les 100.
- Pas de nombre de requêtes dessiné sur l'icône du presse-papiers (MC `ClipBoardDecorator`) : aucune API connue pour écrire sur l'icône d'un objet.
- Champ : changer un rayon demande `MANAGE_HUTS` et les boutons sont grisés sans ce droit, de même que « Choisir la graine » (MC ne vérifie aucun droit sur le rayon et laisse le serveur refuser la graine) ; un bloc de champ hors de toute colonie n'ouvre pas de fenêtre (MC en montre une avec les seuls rayons). La fenêtre se redessine toutes les 20 ticks, où MC le fait à chaque tick. Le biome est le nom brut du générateur, non traduit.
- Choix d'un objet : la liste se refait à chaque frappe (MC attend 10 ticks après la dernière) ; le filtre porte sur l'id de l'objet et son nom (Structurize : sa clé de traduction et son nom).
- L'écran d'inventaire du citoyen passe le nom du citoyen en `name` de la fenêtre, comme le titre de MC ; le client peut l'ignorer pour un conteneur (à vérifier en jeu).
