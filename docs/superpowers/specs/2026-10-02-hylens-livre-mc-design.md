# HyLens V2, lot 1 : le menu en livre de MineColonies et la caméra libre

Suite de `2026-09-30-hycolony-api-hylens-design.md` (§ 6 : HyLens V1).

## 1. Objectif

Demande de l'utilisateur (2026-10-02) :
- l'apparence vanilla de HyLens ne lui plaît pas : HyLens reprend le style de MineColonies ;
- « ça manque de graphique d'action, je peux rien faire » : HyLens doit offrir des actions en boutons, dans quatre familles (citoyens, requêtes et objets, bâtiments et chantiers, temps et colonie) ;
- pendant le suivi d'un citoyen (`/hylens watch`), un bouton pour passer en caméra libre.

## 2. Découpage de la V2

Chaque lot se livre et se teste en jeu seul. Les lots 2 à 5 auront chacun leur conception, validée avant leur spec.

1. **Le livre** (cette spec) : la fenêtre au style de MC, les actions actuelles dedans, la caméra libre, le HUD sur parchemin.
2. **Citoyens** : faire apparaître, tuer, modifier (saturation, bonheur, compétences), changer de métier. Source : les commandes `citizens spawnNew`, `kill`, `modify`, `reload` de MC.
3. **Requêtes et objets** : remplir une requête, donner un objet, remettre les requêtes à zéro (MC `rsReset`).
4. **Bâtiments et chantiers** : finir un chantier, monter ou descendre de niveau, assigner un ouvrier.
5. **Temps et colonie** : temps ×N, jour ou nuit, téléportation à la colonie, statistiques (MC `colony info`, `printStats`).

Chaque action des lots 2 à 5 passe par une méthode `@Experimental` de `DebugAccess` (HyLens ne voit que l'API), une action du cœur de HyColony testée en TDD et soumise aux permissions, puis un bouton. Chaque lot ajoute son onglet au livre : aucun onglet vide n'est créé d'avance.

## 3. Le livre

### 3.1 Le cadre

Le menu reprend le cadre de la mairie de HyColony (`Pages/HyColony/TownHall.ui`, MC `gui/townhall/windowtownhall.xml`, positions de MC ×2) :
- le livre `townhall_book` ;
- l'onglet ouvert : son ruban long (`bookmark_ribbon_0N`) portant son nom ;
- les onglets fermés : leur ruban court (`bookmark_short_ribbon_0N`) sous leur sceau de cire, un bouton ;
- la page de l'onglet ouvert, ajoutée dans `#Page` (`Pages/HyLens/Book/<Onglet>.ui`), avec une page de gauche et une page de droite aux positions de `TownHall/Citizens.ui`.

HyLens ne peut pas réutiliser le code de la mairie (`plugin/` de HyColony, hors API) : il refait en petit le changement d'onglet.

| Emplacement | Onglet | Sceau | Ruban |
|---|---|---|---|
| 0 | Colonies | `red_wax_home` | `_01` |
| 1 | Citoyens | `red_wax_citizens` | `_02` |
| 2 | Vue | `red_wax_settings` | `_03` |

Les lots suivants prennent les emplacements 3 et plus (`red_wax_work_orders`, `red_wax_information`, `red_wax_stats`).

### 3.2 Les onglets

Ils ne reprennent que ce que le menu V1 fait déjà, plus la caméra libre.

- **Colonies** : à gauche, la liste des colonies (nom, citoyens, alertes) ; à droite, la colonie choisie et les résultats du contrôle (`/hylens check`), avec le bouton « Contrôler ».
- **Citoyens** : à gauche, les citoyens de la colonie choisie (nom, métier, état, alertes) ; à droite, le citoyen choisi, ses actions (Suivre, Loisir, Téléporter, Refaire le corps), « Envoyer ici » (X Y Z, Envoyer, Par la carte) et le dernier résultat.
- **Vue** : les couches à cocher, l'horloge (pause, pas × N, reprise) et le contrôle automatique.

Choisir une colonie dans l'onglet Colonies ouvre l'onglet Citoyens. Le menu s'ouvre sur l'onglet Colonies, puis garde le dernier onglet ouvert par l'opérateur tant que HyLens tourne, comme ses autres choix (`Menus`).

### 3.3 La caméra libre

Dans l'onglet Citoyens, quand l'opérateur suit le citoyen choisi, « Suivre » laisse la place à deux boutons :
- **« Caméra libre »** ou **« Reprendre le suivi »**, selon l'état de sa caméra ;
- **« Arrêter le suivi »**, qui fait ce que fait `/hylens unwatch`.

Mécanisme, vérifié dans les sources décompilées :
- la caméra libre pose `new Spectating()` sans cible, comme l'action `Detach` de Hytale (`server/core/modules/entity/spectator/SpectateControlInteraction.java:49-52`). L'opérateur reste dans le mode spectateur, qui vole et traverse les blocs. `Spectating.isSpectating` reste vrai (`server/core/modules/entity/component/Spectating.java:20-26`), donc la sortie à la déconnexion (`OperatorExit`) et `/hylens unwatch` marchent sans changement ;
- le suivi (`Watches`), l'historique, le HUD et les dessins continuent : seule la caméra se détache ;
- « Reprendre le suivi » repose `new Spectating(corps)` avec les mêmes refus que `CitizenWatch.start` : corps absent de son monde, ou mourant. Le refus le dit à l'opérateur (`hylens.watch.noBody`) ;
- en caméra libre, `FollowTarget` ne garde plus le citoyen chargé autour de l'opérateur. S'il s'éloigne trop, le corps peut se décharger : le HUD montre alors « - », comme aujourd'hui.

L'état de la caméra est lu par le plugin sur le composant `Spectating` (cible présente ou non) au moment d'afficher la page : c'est un état de Hytale, pas une règle de jeu.

### 3.4 Le HUD de suivi

Le HUD garde ses lignes et sa place. Il prend un fond parchemin (`colonist_paper`, MC `gui/citizen/colonist_paper.png`) et l'encre noire de `Book.ui`. Qu'il masque une partie du jeu est accepté (utilisateur, 2026-10-02).

### 3.5 Les textures

Elles sont **copiées** dans le pack de HyLens (`Common/UI/Custom/Pages/HyLens/Mc/`), depuis celles de HyColony (`Pages/HyColony/Mc/`, déjà agrandies ×4 au plus proche voisin en @2x). HyLens ne pointe pas vers les assets de HyColony : leurs chemins ne font pas partie de son API. Seules les textures utilisées sont copiées : le livre, les rubans et sceaux des emplacements 0 à 2, les boutons utilisés, `colonist_paper`. Le fichier de styles `Mc/Book.ui` de HyLens ne garde que les styles qu'il emploie.

Ces textures sont de MineColonies (ldtteam, GPL-3.0). Le crédit est écrit en tête de chaque `.ui` qui les emploie, et dans un `NOTICE` à la racine. Celui-ci dit aussi qu'elles sont agrandies ×4. La ligne du `README.md` qui dit « no MineColonies assets are used » est corrigée.

## 4. Code

**Cœur de HyLens** (`hylens/core`, TDD) :
- `MenuTab` (énumération : `COLONIES`, `CITIZENS`, `VIEW`) ;
- `MenuState` gagne `tab`, `withTab(MenuTab)`. `withColony` ouvre `CITIZENS`. `INITIAL` est sur `COLONIES` ;
- `MenuView` porte l'onglet ouvert (`MenuViews` le recopie).

**Plugin de HyLens** :
- `MenuRender` (160 lignes) se découpe : le cadre et les onglets, puis un rendu par onglet (`ColoniesTab`, `CitizensTab`, `ViewTab`) ;
- les nouveaux clics (`tab`, `free`, `follow`, `unwatch`) : le changement d'onglet passe par `Menus`, la caméra par `CitizenWatch`, qui gagne `free` et `follow`. `MenuPage` (216 lignes) ne grossit pas : la répartition des clics de suivi va dans un collaborateur si besoin ;
- les `.ui` : `Menu.ui` devient le livre, plus `Book/Colonies.ui`, `Book/Citizens.ui`, `Book/View.ui` ; les lignes (`MenuColonyRow.ui`, `MenuCitizenRow.ui`, `MenuLayerButton.ui`) et le HUD passent à l'encre sur parchemin.

**Textes** (`hylens.lang`, en-US et fr-FR) : les noms des trois onglets (rubans et bulles des sceaux), « Caméra libre », « Reprendre le suivi », « Arrêter le suivi », et les messages de la caméra (libérée, suivi repris). Les clés actuelles sont gardées quand leur texte ne change pas.

**Règle** : le § 7 de `CLAUDE.md` dit aujourd'hui que les autres mods copient les motifs vanilla. Il devient : « HyLens reprend aussi l'apparence de MineColonies : le livre de la mairie et ses boutons. » Garde-fou : à écrire par l'utilisateur, ou dans une session déverrouillée.

## 5. Tests

- Cœur : changer d'onglet ; choisir une colonie ouvre l'onglet Citoyens ; l'onglet survit au choix d'un citoyen, d'une couche ou d'un pas ; le menu initial est sur Colonies ; `MenuViews` recopie l'onglet.
- En jeu (`docs/TESTING.md`, nouvelles entrées) : les trois onglets et leurs actions ; la caméra libre, puis la reprise du suivi, puis l'arrêt ; la reprise refusée quand le corps est déchargé ; la déconnexion en caméra libre rend le mode de jeu normal ; le HUD lisible sur parchemin.

## 6. Écarts à MineColonies

Aucun sur le jeu. MC n'a pas d'outil de débogage en jeu : HyLens n'a pas d'équivalent et emprunte le cadre du livre de la mairie.

## 7. À vérifier en jeu

- La caméra libre garde le vol et la traversée des blocs du mode spectateur.
- Le chemin `../../Pages/HyLens/Mc/colonist_paper.png` depuis `Hud/HyLens/WatchHud.ui` (même pack, autre dossier) s'affiche ; sinon, la texture est aussi copiée sous `Hud/HyLens/`.
