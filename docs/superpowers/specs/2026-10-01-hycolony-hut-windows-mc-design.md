# Fenêtres de huttes, comme MineColonies

Date : 2026-10-01. Sous-projet 1 sur 3 du portage des fenêtres (2 : fenêtre du citoyen ; 3 : champ, presse-papiers, fondation, baguette). L'hôtel de ville est déjà fait (`2026-10-01-hycolony-town-hall-complete-design.md`).

Référence : l'inventaire complet `docs/research/ui-vs-minecolonies.md` § 8 (positions, textures, contenus, messages et état actuel de HyColony, avec les lignes des sources de MC). Cette spec décide ; l'inventaire donne le détail.

## 1. But

Chaque fenêtre de hutte de HyColony (résidence, constructeur, fermier, livreur, entrepôt) prend l'apparence et le contenu de MineColonies :

- positions et tailles des `.xml` de MC ×2, textures de `sources/` agrandies ×4 au plus proche voisin en `@2x` sous `Pages/HyColony/Mc/` (CLAUDE.md § 7) ;
- le contenu que montre la classe Java de chaque fenêtre de MC, à sa place ; chaque bouton appelle l'action du cœur qui correspond au message de MC, avec son droit (`MANAGE_HUTS` pour tous, § 8 préambule de l'inventaire) ;
- un écart seulement s'il est imposé, nommé `Deviation from MC:` et listé au § 10.

Succès : en jeu, une hutte ressemble à celle de MC (papier, bandeau rouge, onglets latéraux à icône) et chaque onglet ou fenêtre annexe montre ce que montre MC.

## 2. Périmètre

| Lot | Contenu | Statut inventaire |
|---|---|---|
| 0 | Cadre : papier, bandeau, titre, crayon, Construire, Aide, Inventaire, Inventaire total, onglets latéraux ; styles de boutons | A |
| 1 | Pages principales (hutte à travailleurs, hutte simple, résidence) ; renommage ; fenêtre d'aide | B |
| 2 | Fenêtres d'embauche (`WindowHireWorker`) et d'affectation de la résidence (`WindowAssignCitizen`) | A/B |
| 3 | Constructeur : Ressources, Réglages, Ordres de travail | A/B |
| 4 | Fermier : Recettes, Champs, Réglages, Tâches | A/B |
| 5 | Livreur et entrepôt : Tâches, Coursiers | B |
| 6 | Inventaire total (`WindowHutAllInventory`) | B |

Hors périmètre (catégorie C, systèmes absents, à repousser) : stock minimum, statistiques de hutte, options de l'entrepôt (amélioration de stockage, tri), recettes du constructeur, pause et redémarrage d'un citoyen, guide du constructeur (succès), recherche (les réglages qui en dépendent s'affichent désactivés, § 6). Leurs onglets n'apparaissent pas tant que le système n'existe pas.

## 3. Architecture de la fenêtre

### 3.1 Une fenêtre Hytale par hutte, onglets dans la page

MC ouvre une fenêtre par module ; HyColony garde **une page** (`BuildingPage`) qui dessine le papier, les onglets latéraux et le contenu de l'onglet choisi, comme l'hôtel de ville (`TownHallPage`). Raison : le rafraîchissement en direct, l'onglet gardé après une action et l'absence de clignotement. Le rendu reste celui de MC : **aucun onglet ne montre d'état « ouvert »** (MC n'en a pas, inventaire § 8.1).

- Le cadre `Building.ui` est réécrit : papier 380 × 488, bandeau, `#Title` rouge, `#EditName`, `#Info`, un `#Page` où le document de l'onglet est ajouté, une colonne `#Tabs` à x −40.
- Chaque onglet est un document `.ui` à part (`Hut/Main*.ui`, `Hut/BuilderResources.ui`…), ajouté dans `#Page` ; seul l'onglet ouvert est construit (motif de `TownHallPage` depuis le lot 0 de l'hôtel de ville).
- Onglets latéraux (`TabsWindowModule`) : onglet 64 × 52 en (−40, 20 + 56 i), fond `tab_left_sideN` (N de 1 à 4 tiré, comme MC, par `new Random(hashCode de la position)` ; la formule `(y + z·31)·31 + x` de `Vec3i.hashCode` vient de Minecraft, pas de `sources/`), icône 40 × 40 en (10, 6), infobulle du module. Ordre : Principal, puis les modules de la hutte dans l'ordre de MC (inventaire § 8.2).
- Son d'un onglet : le son de bouton du jeu (Hytale 0.7.0-pre.4 n'a pas de son de page de livre, écart).

### 3.2 Vues et actions

- `BuildingView` garde un record par hutte et des `ModuleTab` par module (existant). Il gagne : `customName` (titre « nom niveau » : nom personnalisé, sinon le nom du type), le métier de chaque ligne (`WorkerLine`), le type de page principale (`MainKind` : WORKERS, SIMPLE, LIVING), la fenêtre d'embauche (`HireView`, du module de travailleurs ou des livreurs de l'entrepôt) et le stock par conteneur (`HutStock`).
- Les fenêtres annexes (embauche, affectation, inventaire total, recettes à apprendre) dessinent des parties de `BuildingView` : le cœur réaffiche la hutte après chaque action, et le plugin redessine la fenêtre annexe ouverte pour cette hutte (interface `HutWindow`) au lieu de revenir à la page principale, comme MC garde sa sous-fenêtre ouverte. Pas de nouvelle méthode de `UiPort`. La croix ou « Retour » ouvre la page principale de la hutte.
- Les paquets `app/ui`, `app/view` et `app/action` sont presque pleins (15 fichiers au plus) : les vues et constructeurs des fenêtres de huttes vont dans un sous-paquet `app/hut` (vues, constructeurs, actions propres aux fenêtres annexes), seul son point d'entrée public.
- La fenêtre d'aide (`WindowInfo`) n'a pas de règle de jeu : le plugin l'ouvre seul avec les clés de langue (`hycolony.ui.info.<type>.<i>.name` et `.text`), le nombre de pages par type est une constante du plugin (4 pour le constructeur, le fermier, le livreur et l'entrepôt ; 0 pour la résidence, bouton caché comme MC).

### 3.3 Styles et textures

- `Mc/Book.ui` gagne : `@SmallButtonStyle` (64 × 17), `@QuiteSmallButtonStyle` (44 × 16, texte réduit), `@LargeButtonStyle` (169 × 17), `@VerySmallButtonStyle`, `@MediumButtonStyle`. Comme le `ButtonImage` de BlockUI avec `color="black"`, un bouton ne change ni survolé, ni pressé, ni désactivé ; seul un bouton dont le XML de MC nomme une texture `disabled` la prend (styles `*DisabledTexture`, la case des champs).
- Textures à copier : inventaire § 8.7 (papier, bandeau, `red_wax_information`, `chest`, onglets et 9 icônes de module, boutons `small`, `quite_small`, `large`, `mini_check`, `mini_disabled*`, `builder_paper_wide2`). Les textures de Mojang absentes de `sources/` (boutons vanilla, `shadow`, houe, graines) sont remplacées par un bouton ou une icône d'objet Hytale (écart).
- Un texte avec infobulle (« Nom (id) », nom d'ordre, positions d'une tâche) est un `Label` avec `TooltipText` ou `.TooltipTextSpans` ; le survol dans une liste défilante est à noter en jeu **[in-game]**.

## 4. Lot 0 : cadre

Contenu exact : inventaire § 8.1 (tableaux « Le papier et la page principale » et « Onglets latéraux »).

- Titre : « nom niveau » en rouge (`getBuildingDisplayName` + niveau).
- Crayon : ouvre la fenêtre de renommage (lot 1).
- Construire : comportement actuel (fidèle à `updateButtonBuild`), texture `builder_button_medium_large`.
- Aide : visible si la hutte a des pages d'aide ; ouvre la fenêtre d'aide (lot 1).
- Inventaire (pages à `min` et plus) : ouvre le conteneur du bloc de hutte, **droit `MANAGE_HUTS`** comme `OpenInventoryMessage` (aujourd'hui `OPEN_CONTAINER`, à aligner).
- Inventaire total : ouvre la fenêtre du lot 6 (en attendant le lot 6, l'actuelle sous-vue).
- `TabBar` n'est plus utilisé par les huttes.

## 5. Lots 1 et 2 : pages principales, renommage, aide, embauche

### 5.1 Pages principales (inventaire § 8.1 « Travailleurs », § 8.4)

- **Hutte à travailleurs** (constructeur, fermier, livreur ; `layouthutpageactions.xml`) : « Assigned Workers: » en gras ; une ligne « Métier: Nom » par travailleur, infobulle « Nom (id) » ; Gérer les travailleurs (niveau 0 : message `workerhuts.level0` ; sinon fenêtre d'embauche) ; Rappeler (tous les travailleurs à la hutte, corps recréé si absent, échec dit) ; « Pickup Prio.: n/10 » ou « Never » avec − et + ; Demander un ramassage. Les boutons restent actifs pour tous ; le cœur refuse sans `MANAGE_HUTS`.
- **Hutte simple** (entrepôt ; `layouthutpageactionsmin.xml`) : papier, titre, crayon, Construire, Aide, Inventaire, Inventaire total, rien d'autre.
- **Résidence** (`windowhuthome.xml`) : « Assigned Citizens: n/m » ; une ligne « Métier: Nom » (ou « Nom ») par résident ; Gérer le logement (niveau 0 : message ; sinon fenêtre d'affectation) ; Rappeler les citoyens (`HousingActions.recall`). L'onglet Résidents disparaît.
- Disparaissent aussi de la page principale : l'état, « niveau n/max », la ligne d'ordre, les listes Travailleurs et Embauchables, le bouton du mode d'embauche (ils sont ailleurs chez MC).

### 5.2 Renommage (`WindowHutNameEntry`, inventaire § 8.6)

- Fenêtre assombrie sans papier, « Rename Your Building », champ (25 caractères), Terminé, Annuler (motif de `RenameColonyPage`).
- À l'ouverture : le nom personnalisé (écart déjà retenu pour la colonie : nom exact, pas en minuscules).
- Terminé : au-delà de 15 caractères, coupé à 15 avec le message `gui.name.toolong` (MC le fait côté client) ; action cœur `HutActions.rename` (`MANAGE_HUTS`) ; nom vide = retour au nom du type (MC enregistre la chaîne vide, ce qui affiche le nom du type) ; puis la hutte se rouvre.
- `Building.customName` est déjà sauvegardé : pas de migration.

### 5.3 Aide (`WindowInfo`, inventaire § 8.6)

Papier, bandeau, pages tournées par flèches (`turn_page_left/right`), numéro de page ; une page = titre rouge et texte. Les textes anglais de MC (`com.minecolonies.coremod.info.<type>.<i>`) sont repris dans `hycolony.lang` en-US et traduits en fr-FR.

### 5.4 Embauche (`WindowHireWorker`, inventaire § 8.6)

- Papier large `builder_paper_wide2` 800 × 488, sans onglets ; « Choose workers for the %s. » ; un bouton par module de travailleur de la hutte (un seul chez nous) ; liste : employés d'ici, sans emploi, autres métiers (si « Montrer les employés »), triés comme MC (employés d'ici, sans emploi, autre métier, non assignables, puis distance arrondie à 40, sans logis = 100, puis nom).
- Ligne : « Métier ou Unemployed: Nom » en bleu ; la ligne de distance (sans logis, habite ici, habite au travail, à N blocs) ; toutes les compétences « nom: niveau », primaire en vert foncé gras et secondaire en or gras en tête, infobulle de la description des compétences du métier ; Embaucher ou Renvoyer (`builder_button_quite_small`) selon MC (un employé d'ailleurs est d'abord retiré de son ancienne hutte).
- Mode d'embauche : bouton qui cycle **sans LOCKED** pour un lieu de travail (correction de fidélité : aujourd'hui HyColony passe par LOCKED, `HiringMode.next`).
- « Montrer les employés » Non/Oui : état de page.
- Pause et Redémarrer : absents (C).
- X : rouvre la hutte.

### 5.5 Affectation de la résidence (`WindowAssignCitizen`, inventaire § 8.4)

Papier large ; à gauche les résidents (« Métier: à N blocs », rouge au-delà de 300, « Unassign » actif selon le mode et le logement automatique, infobulle sinon) ; à droite les candidats (triés sans logis d'abord puis distance du travail, couleurs de MC, « Assign » si place libre) ; le mode (4 modes, LOCKED compris) ; X. Le contenu existe (`HousingActions`, `ResidentsTab`) : seule la fenêtre change.

## 6. Lots 3 et 4 : constructeur et fermier

### 6.1 Réglages génériques (`SettingsModuleWindow`, inventaire § 8.5)

- Vue `SettingsView` : une liste ordonnée de `SettingRow(id, kind, value, active, reasonKey)`, `kind` ∈ BOOL, STRING, BLOCK ; ligne de MC par type (`layoutboolsetting`, `layoutstringsetting`, `layoutblocksetting`), texte `setting.<id>`, infobulle `setting.tooltip.<id>` si elle existe.
- Un clic envoie l'id à une action cœur (`HutActions.triggerSetting`) qui passe à la valeur suivante (STRING), inverse (BOOL) ou ouvre le choix de bloc (BLOCK, `ItemPickerPage` existant).
- Constructeur, dans l'ordre de MC : `mode` (Auto/Manuel), `recipemode` et `buildmode` **désactivés** avec la raison de MC (« needs research » : HyColony n'a pas de recherche), `useshears` absent (écart existant, pas de cisailles), `fillblock`.
- Fermier : `fertilize` (sort de l'onglet Champs), `recipemode` désactivé avec sa raison.

### 6.2 Autres pages

- **Ressources du constructeur** : rendu de `layoutbuilderres.xml` ; NOT_NEEDED en noir (aujourd'hui gris).
- **Ordres de travail** : rendu de `layoutworkorders.xml` ; l'ordre courant a un **contour** vert (0,170,0), plus un fond.
- **Recettes** (fermier) : rendu de `layoutlistrecipes.xml` ; boutons vanilla remplacés par les boutons de `Book.ui` (écart) ; l'apprentissage par liste reste l'écart déjà documenté.
- **Champs** : rendu de `layoutfarmfields.xml` ; l'assignation devient la case mini cochée de MC ; l'étape devient une icône d'objet Hytale avec l'infobulle « actuelle / suivante » ; « fait aujourd'hui » et Localiser restent (ajouts documentés) ; Fertiliser part dans Réglages.
- **Tâches du fermier** : la file d'artisanat (`CraftingTasks`) gagne une vue d'onglet `Tasks`, rendue comme les tâches (§ 7).

## 7. Lot 5 : livreur et entrepôt

- **Tâches** (fermier, livreur, entrepôt ; `layouttasklist.xml`) : icône de la requête, texte court ou objet avec quantité (vert foncé si en cours, noir sinon ; plus de fond vert), « Priority: n » pour une requête de coursier, « demandeur -> parent » avec l'infobulle des positions « x, y, z -> x, y, z ». La ligne « entrepôt servi » du livreur reste (ajout documenté).
- **Coursiers** (`layoutcourierassignment.xml`) : « Assigned Workers: », lignes « Métier: Nom », Gérer (fenêtre d'embauche sur le module des coursiers : candidats = coursiers rattachés à aucun autre entrepôt, maximum niveau × 2, rattacher et détacher, mode des coursiers), Rappeler. Actions cœur nouvelles : rattacher, détacher, changer le mode, rappeler (`MANAGE_HUTS`).

## 8. Lot 6 : inventaire total (`WindowHutAllInventory`, inventaire § 8.6)

- Fenêtre à part, papier, « All Items » ; filtre (25 caractères, sur le nom affiché ou l'id ; tri par distance de Levenshtein au filtre comme MC) ; tri à 5 états « v^ », « A^ », « Av », « 1^ », « 1v », gardé par joueur entre deux ouvertures (MC : champ statique) ; liste : icône, nom tronqué à 17 caractères, quantité abrégée (« 1.2k », règle `Utils.format` de MC portée dans le cœur) ; « ? » Localiser : ferme la fenêtre, dit `coremod.locating`, surligne 60 s chaque conteneur qui tient l'objet (`Highlights` existant) avec sa couleur selon la quantité ; Retour.
- `HutInventoryView` : pour chaque objet, sa quantité totale et les conteneurs (position, quantité) qui le tiennent.
- La sous-vue Inventaire total de la page principale disparaît.

## 9. Robustesse, persistance, tests

- Chaque action vérifie la colonie, la hutte, le citoyen ou le réglage et le droit, renvoie `false` sans exception ; les événements de fenêtre portent des identifiants stables (citoyen, position, id de réglage), bornés côté plugin.
- Pas de nouvel état persisté sauf ce qui existe (nom de hutte, modes, réglages) : pas de migration. Si un lot en ajoute un, il passe par `add-migration`.
- **Cœur, en TDD** : chaque règle portée a son test (renommage à 15, nom vide, métier des lignes, tri et contenu de l'embauche, mode sans LOCKED, rappel des travailleurs, réglages actifs ou non et leur cycle, tâches du fermier, rattachement des coursiers et son maximum, inventaire par conteneur et abréviation des quantités, droit `MANAGE_HUTS` de l'inventaire).
- **Plugin** : `docs/TESTING.md` reçoit un point par page et par fenêtre, avec les vérifications **[in-game]** (textures étirées, onglets latéraux, infobulles dans les listes, case cochée).
- Chaque lot : build vert, relectures (`hycolony-reviewer`, `ui-lang-checker`, `mc-fidelity-checker`), commit ; relecture finale de tout le sous-projet ; puis feu vert pour le test en jeu.

## 10. Écarts à MC retenus

Chacun porte un `Deviation from MC` dans le code.

- Une seule fenêtre Hytale par hutte, onglets dans la page (pas de nouvelle fenêtre par module) ; le rendu reste sans onglet « ouvert ».
- Son des onglets : le son de bouton du jeu (pas de son de page de livre dans Hytale 0.7.0-pre.4).
- Boutons vanilla de Minecraft (recettes, renommage), ombre, houe et graines : textures de Mojang absentes de `sources/`, remplacées par des boutons de `Book.ui` ou de Hytale ; l'étape d'un champ s'écrit au lieu de son icône ; « ↥ » et « ↧ » deviennent « + » et « - » (pas de flèches dans les polices du client).
- Le libellé « Work Orders » que `layoutworkorders.xml:6` pose hors du papier (x 201 > 190) n'est pas repris.
- Le champ de renommage est pré-rempli avec le nom exact (pas en minuscules), comme pour la colonie.
- Pas de Ctrl pour retirer une recette native (aucun événement de touche modificatrice connu), pas de Maj pour les quantités exactes ni pour monter une recette tout en haut.
- Localiser (inventaire total) : chaque conteneur brille avec un marqueur de carte « objet nombre », sans la couleur rouge-vert de MC selon le nombre (un bloc brillant n'a pas de couleur), et remplace toute autre surbrillance du joueur.
- Le filtre de l'inventaire total cherche dans le nom affiché et l'id de l'objet (MC : l'id de description et l'infobulle, données d'objet de Minecraft).
- Le tri de l'inventaire total est gardé par joueur jusqu'à l'arrêt du serveur (MC : un champ statique du client).
- Pas de Pause ni de Redémarrer dans la fenêtre d'embauche (HyColony ne met pas encore un citoyen en pause).
- « Apprendre une recette » ouvre la liste des recettes que la hutte peut apprendre (MC : une grille d'artisanat).
- Les onglets C (stock minimum, statistiques, options de l'entrepôt, recettes du constructeur) manquent tant que leur système n'existe pas.
- Ajouts déjà documentés conservés : « fait aujourd'hui » et Localiser dans Champs, ligne « entrepôt servi » du livreur, apprentissage des recettes par liste, confirmation par double clic dans la fenêtre de construction.

## 11. Points tranchés

- Droit de l'inventaire de hutte : `MANAGE_HUTS`, comme MC.
- Contenus déplacés (Résidents, Fertiliser, embauche et mode sur la page principale, sous-vue Inventaire total) : remis à la place de MC.
- Mode d'embauche d'un lieu de travail : sans LOCKED, comme MC.
- Renommage à 15 caractères avec le message de MC.
