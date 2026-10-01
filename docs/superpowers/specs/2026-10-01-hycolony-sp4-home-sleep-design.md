# HyColony SP4 : maison, lit et sommeil des citoyens

Conception validée avec l'utilisateur le 2026-10-01. Source MineColonies : `sources/` (branche `version/main`, copiée vers le 2026-10-01).

Recherche, **à lire avant tout** : `docs/research/sp4-sleep-home.md`. Les renvois « R § x » de cette spec pointent vers ce fichier. Elle a été faite sur Hytale 0.6.8 : chaque API Hytale qu'elle cite est à revérifier dans les sources décompilées de 0.7.0-pre.4 avant de l'utiliser (skill `hytale-api`).

## Objectif

Les citoyens ont une maison. Le soir, ils rentrent, se couchent dans un vrai lit de leur résidence, et se lèvent à l'aube, comme dans MineColonies. Le travail s'arrête la nuit. Aujourd'hui, personne n'attribue de maison (`CitizenData.homeBuilding` n'est jamais écrit), `LivingModule` ne fait que `capacity = level`, et le constructeur travaille toute la nuit (R, « Réponse courte »).

## Portée

- **Dedans :**
  - l'attribution des résidences : automatique et manuelle, modes d'embauche, onglet « Habitants » ;
  - l'enregistrement des lits d'une résidence ;
  - l'horloge « façon MC » calée sur le jour et la nuit de Hytale ;
  - l'état `SLEEP` : décision du soir, trajet, lit, sommeil couché, réveil ;
  - le port pour coucher et lever un corps, et son adaptateur Hytale ;
  - la persistance (schéma 6), les textes, les tests, et les étapes de test en jeu.
- **Dehors**, faute du système dont ils dépendent (R § A.7, § D « Reporté ») :
  - la faim (saturation arrêtée la nuit, `EATING`) ;
  - le bonheur (`SLEPTTONIGHT`, sans-abri) ;
  - la maladie et l'hôpital, les raids, le deuil ;
  - la taverne comme repli des sans-abri ;
  - les plaintes « pas de garde » ;
  - la règle des mineurs sous terre ;
  - la recherche `WORK_LONGER` ;
  - le son `OFF_TO_BED` et les particules de sommeil ;
  - le plafond `calculateMaxCitizens`, qui ne sert qu'à l'immigration et aux naissances, absentes de HyColony.

## Livraison

Deux étapes, chacune testable en jeu seule :

1. **La résidence** : attribution, onglet « Habitants », lits enregistrés (§ 1, § 2, § 3).
2. **Le sommeil** : horloge, état `SLEEP`, port et adaptateur (§ 4 à § 7).

La persistance (§ 8) et les textes (§ 9) suivent chaque étape : chacune ajoute ses propres clés.

## 1. Attribution d'une maison (MC `LivingBuildingModule`, R § A.6)

`construction/hut/LivingModule` devient un module d'attribution (`PersistentModule`, `TickingModule`, `BuildingEventsModule`). Il ne partage pas de classe mère avec `WorkerModule`, qui porte la logique de métier. Il reprend `HiringMode` (`job/HiringMode`), le même enum que MC.

- **Habitants** : liste ordonnée d'ids de citoyens, persistée. `max(b) = b.level()` : 0 au niveau 0, 5 au niveau 5 (MC `getModuleMax`).
- **`assign(colony, b, citizen)`** renvoie `false` pour un doublon, une hutte pleine ou un citoyen absent. Sinon :
  - le citoyen quitte son ancienne maison (retiré de son `LivingModule`) ;
  - `homeBuilding = b.position()`, et `bedPos` est effacé (MC `CitizenData.setHomeBuilding`) ;
  - la colonie est marquée à sauvegarder.
- **`remove(colony, b, citizenId)`** : `homeBuilding` et `bedPos` du citoyen sont effacés. Si le citoyen dort, il est réveillé (§ 5.6).
- **`onRemoved`** retire tous les habitants (MC `onDestroyed`). Une baisse de niveau n'expulse personne, comme dans MC.
- **Attribution automatique** (`onColonyTick`, MC `LivingBuildingModule.onColonyTick`) : si la hutte n'est pas pleine, et que le mode est `AUTO`, ou `DEFAULT` avec le réglage de colonie `autoHousing`, elle prend les citoyens **sans maison**, dans l'ordre de la liste des citoyens, jusqu'à être pleine. Elle ne déplace jamais un citoyen déjà logé.
  - `autoHousing` rejoint `ColonySettings`, à côté de `autoHiring`, avec le défaut **true** de MC (`AUTO_HOUSING_MODE`, `BuildingModules.java:529`).
- **Mode manuel effectif** : mode `MANUAL`, ou `DEFAULT` avec `autoHousing` à false. Seul ce mode autorise l'attribution et le retrait à la main (MC `WindowAssignCitizen`).
- **Réparation au chargement** (`ColonySerializer.heal`, CLAUDE.md § 5) :
  - un habitant inconnu ou en double est retiré de la liste ;
  - un `homeBuilding` qui ne désigne pas une résidence qui le compte parmi ses habitants est effacé ;
  - un habitant d'une résidence dont la liste dépasse `max` reste logé (MC n'expulse pas).

## 2. Les lits (MC `BedHandlingModule`, R § A.4)

`construction/hut/BedModule` (`PersistentModule`) tient la liste **ordonnée** des positions de lits de la hutte, persistée. La résidence a les modules `living` et `bed`, comme les modules `HOME`, `LIVING` et `BED` de MC.

- **Enregistrement** : quand un bloc de plan est posé dans la hutte, et que le catalogue dit que c'est un lit, sa position d'origine est ajoutée sans doublon (MC `onBlockPlacedInBuilding`, appelé par `registerBlockPosition`). Les deux points de pose sont ceux qui appellent déjà `addContainer` :
  - le constructeur (`construction/builder/BuilderBlockWork`) ;
  - le collage à la baguette (`app/wand/PasteQueue`).
- Un lit Hytale est un seul bloc « modèle » avec des cellules de remplissage. Seul ce bloc d'origine est enregistré, l'équivalent de la tête du lit de MC.
- **`ItemCatalog.isBed(block)`** : `true` si le `BlockType` a des points de couchage (`getBeds() != null`, R § B.2, `BlockMountAPI`). Faux sur un bloc inconnu.
- **Résidences construites avant SP4** : la réparation au chargement relit une fois le plan au niveau de la hutte et enregistre ses lits, sans vérifier le monde (le lit est revérifié au coucher, § 5.3). Une hutte au niveau 0 n'a pas de plan à relire.
- **`removeBed(pos)`** retire une position qui n'est plus un lit (§ 5.3).
- **`onWakeUp`** n'a rien à faire : l'occupation des lits est tenue par Hytale (§ 6). C'est un écart à noter dans la Javadoc (MC remet `OCCUPIED = false`).
- *Écart avec MC* : l'ordre des lits est l'ordre de pose. Dans MC, `bedList` vient d'un `HashSet`, donc un citoyen peut changer de lit d'une session à l'autre.
- *Constat, non corrigé ici* : les plans de la résidence Outlander ont moins de lits que d'habitants aux niveaux 2, 3 et 5 (R § B.4). Les citoyens en trop dorment debout dans la hutte, comme dans MC quand le rang dépasse la liste. Le choix des préfabs est un sujet `styles.json` distinct.

## 3. Onglet « Habitants » (MC `WindowHutLiving`, `WindowAssignCitizen`, R § A.6)

`LivingModule` implémente `ProvidesTab`. Sa vue `ResidentsView` (record, `ModuleTab`) contient :

- « Assignés x/max » (`com.minecolonies.coremod.gui.home.assigned`) ;
- les habitants : « Métier : Nom », avec un bouton Retirer (MC `gui.hiring.buttonunassign`) ;
- les citoyens qu'on peut attribuer : tous sauf les habitants de cette hutte. Les sans-abri passent d'abord, puis le tri se fait par distance de leur atelier à cette maison.
  - Chaque ligne affiche « Travaille à N blocs d'ici », en vert si c'est plus près que la maison actuelle et en rouge au-delà de `FAR_DISTANCE_THRESHOLD = 300`, ou « Sans-abri » ou « Distance actuelle au travail ».
  - Un citoyen sans atelier est trié après ceux qui en ont un.
- le mode, qui fait tourner `DEFAULT → AUTO → MANUAL → LOCKED` ;
- au niveau 0, le libellé de MC `WORKERHUTS_LEVEL_0` à la place du bouton Attribuer.

Les boutons Attribuer et Retirer ne sont actifs qu'en mode manuel effectif (§ 1). Sinon, ils sont grisés avec l'infobulle `gui.home.hire.warning`.

Actions du cœur, dans `app/action` (un nouveau `HousingActions`, pour ne pas grossir `HutActions`) :

- `assign(player, hutPos, citizenId)`, `unassign(…)` et `cycleMode(player, hutPos)` ;
- elles exigent `MANAGE_HUTS` (MC `AbstractColonyServerMessage.permissionNeeded`) et un mode manuel effectif pour attribuer et retirer ;
- elles ré-affichent la fenêtre de la hutte ;
- `assign` sur une hutte pleine ou déjà la maison du citoyen ne fait rien, comme MC `AssignUnassignMessage`.

Côté plugin : un `ResidentsTab.ui` et une ligne `ResidentRow.ui`, copiés de `WorkerRow.ui` et des onglets existants, et leur rendu dans la fenêtre de hutte.

## 4. L'horloge « façon MC » (R § A.2, § B.1)

Choix de l'utilisateur : la correspondance se fait **par phases**.

`kernel/port/GameClock` reçoit :

- **`dayTime()`**, l'heure MC dans [0, 24000[ :
  - le jour Hytale, du lever (04:48) au coucher (19:12), correspond à [0, 12600[ ;
  - la nuit Hytale, du coucher au lever suivant, correspond à [12600, 24000[.

  Ainsi `NIGHT = 12600` tombe au coucher du soleil, `NIGHT - 2000` vers 16:55, et 0 au lever, qui est aussi l'heure de réveil des joueurs (`Sleep.WakeUpHour = 4.79`).
- **`realTicksUntil(dayTime)`**, les ticks réels avant la prochaine fois que l'horloge atteint cette heure MC, d'après la durée réelle du jour et de la nuit du monde. Elle sert à comparer le temps restant avec un temps de trajet réel (§ 5.1).

`isDaytime()` reste, et devient `dayTime() < NIGHT`, comme MC `WorldUtil.isDayTime`. `Colony.checkDayTime` ne change pas.

`plugin/adapter/HytaleGameClock` calcule les deux à partir de `WorldTimeResource` : l'heure normalisée de la journée, et `DaytimeDurationSeconds` / `NighttimeDurationSeconds` du monde. Une durée de jour modifiée par le monde est donc suivie. Les noms exacts des accesseurs sont à vérifier en 0.7.0-pre.4.

Le `FakeGameClock` des tests fixe `dayTime` et un rapport ticks réels / ticks MC par phase.

## 5. Le sommeil

### 5.1 La décision (MC `CitizenAI.calculateNextState`, `CitizenSleepHandler.shouldGoSleep`, R § A.1, § A.2)

`CitizenState` gagne `SLEEP`. La décision de sommeil passe **avant** la pluie, le loisir et le travail, comme dans MC, et s'évalue toutes les `DECIDE_INTERVAL_TICKS = 10` dans **tous** les états, y compris `WORKING`.

Un collaborateur `citizen/sleep/SleepDecision` reprend la règle :

- **Le soir** : si `dayTime > NIGHT - 2000` (MC `!isPastTime(NIGHT - 2000)`) :
  - un citoyen déjà en `SLEEP` y reste, et la décision suivante n'a lieu que dans 15 s (`20 * 15` ticks, MC `setCurrentDelay`) ;
  - sinon, il part si `shouldGoSleep()` ;
  - sinon, la suite de la décision s'applique.
- **`shouldGoSleep()`** :
  - faux sans position de maison (§ 5.2) ;
  - distance pondérée de la position du corps à la maison : `sqrt(dx² + dz² + (|dy| * Y_DIFF_WEIGHT)²)`, avec `Y_DIFF_WEIGHT = 1.5` ;
  - temps de trajet : `distance * TIME_PER_BLOCK`, avec `TIME_PER_BLOCK = 6`, en ticks **réels** (la marche est réelle) ;
  - temps restant : `realTicksUntil(NIGHT)`, ou 0 si l'horloge a déjà dépassé `NIGHT` ;
  - vrai si le temps restant moins le temps de trajet est ≤ 0. Le citoyen part juste à temps pour arriver au coucher du soleil, au plus tôt vers 16:55.
  - *Écart avec MC* : MC compte le trajet en ticks de jour, égaux aux ticks réels dans Minecraft. Ici, avec le calage par phases, un tick MC ne vaut plus un tick réel : on compare donc deux durées réelles.
- **La plainte** : si la maison est à plus de `MAX_NO_COMPLAIN_DISTANCE = 160` blocs de l'atelier, le message `com.minecolonies.coremod.gui.chat.hometoofar` est envoyé aux joueurs de la colonie, au départ.
- **Le jour** : dès que `dayTime <= NIGHT - 2000` (donc à 0, l'aube), un citoyen endormi se réveille (§ 5.6), puis la décision normale reprend : `WORKING` si le métier a du travail, sinon `IDLE`. Si des joueurs font passer la nuit, l'horloge saute à leur heure de réveil, et les citoyens se lèvent aussi.

`CitizenAI` (317 lignes) ne garde que le branchement : la règle va dans `SleepDecision`, la marche et le lit dans `SleepAI` (§ 5.3).

### 5.2 La position de la maison (MC `CitizenData.getHomePosition`)

La maison si le citoyen en a une, sinon l'hôtel de ville, sinon aucune. *Écart avec MC* : pas de repli sur la taverne (absente) ni sur le centre de la colonie (l'hôtel de ville en tient lieu dans HyColony).

### 5.3 Rentrer et se coucher (MC `EntityAISleep`, R § A.3, § A.4)

`citizen/sleep/SleepAI` est une petite machine d'états, créée en entrant en `SLEEP` (`usedBed` vide, `bedTicks = 0`) :

| Sous-état | Cadence | Effet |
|---|---|---|
| `WALKING_HOME` | 30 ticks | `walkHome()` |
| `FIND_BED` | 30 ticks | `findBedAndTryToSleep()`, puis `SLEEPING` si `findBed()` |
| `SLEEPING` | 30 ticks | `sleep()` |

- **`walkHome()`** :
  - avec maison : passe à `FIND_BED` dès que le corps est dans l'emprise de la hutte ; sinon il y marche (les marcheurs existants, `BlockApproach`) ;
  - sans maison : passe à `FIND_BED` à 4 blocs de l'hôtel de ville (`distSqr <= RANGE_TO_BE_HOME`, `RANGE_TO_BE_HOME = 16`) ; sinon il y marche.
- **`findBedAndTryToSleep()`** (sans maison, il ne fait rien, R § A.3) :
  - le lit est celui du **rang** du citoyen parmi les habitants (`BedModule` à cet index). Un rang hors liste donne la position de la hutte ;
  - si la position n'est plus un lit (`isBed` du bloc posé), `removeBed` et repli sur la hutte ;
  - le citoyen marche jusqu'au lit dans le bâtiment ; à l'arrivée, `bedTicks++` ;
  - il tente `sleepIn` (§ 6). En cas d'échec (lit pris, ou la hutte n'est pas un lit), `usedBed` est oublié, et la tentative suivante retombe sur la hutte.
- **`findBed()`** : vrai une fois endormi, ou après `MAX_BED_TICKS = 10` arrivées.
- **`sleep()`** : si le citoyen a un lit et en est à plus de 3 blocs (`distSqr > 9`), il retourne en `WALKING_HOME`. Sans lit, il retente `findBedAndTryToSleep()`. Un citoyen sans lit reste donc debout dans la maison, et un sans-abri debout près de l'hôtel de ville, toute la nuit, comme dans MC.

### 5.4 Se coucher (MC `CitizenSleepHandler.trySleep`)

Quand `sleepIn` réussit :

- l'objet tenu est retiré (`setHeldItem(empty)`) ;
- `asleep = true`, `leisureTime = 0` (MC `CitizenData.setAsleep`), `bedPos = lit` ;
- quand **tous** les citoyens dorment, le message `ALL_CITIZENS_ARE_SLEEPING` est envoyé une fois aux joueurs de la colonie (MC `CitizenManager.onCitizenSleep`). Il repart après un réveil.

### 5.5 Le travail interrompu

Un citoyen en `WORKING` qui part dormir perd son IA de métier (`dropJobAI`, comme une sortie vers `IDLE`). Au réveil, son métier repart de son premier état, comme MC `resetAI`. Les règles « ne pas interrompre » ne s'appliquent pas au sommeil, comme dans MC, où la décision de sommeil passe avant `canBeInterrupted`.

### 5.6 Le réveil (MC `CitizenSleepHandler.onWakeUp`, R § A.5)

- `onWakeUp` est appelé sur la maison et l'atelier (`BuildingEventsModule.onWakeUp(Colony, Building)`, à rajouter, depuis l'équivalent de MC `AbstractBuilding.onWakeUp`) ;
- si le citoyen dormait vraiment dans un lit, `wakeUp(body)` le descend au point de sortie, et `bedPos` est effacé ;
- `asleep = false`.

### 5.7 Rechargement

`asleep` et `bedPos` sont persistés (MC `TAG_ASLEEP`, `TAG_BEDS`). Hytale ne sauve pas l'état « couché » d'un PNJ (`MountedComponent` sans codec, R § B.3) : un citoyen rechargé est debout. Au chargement, un citoyen marqué endormi est donc réveillé (`asleep = false`, `bedPos` effacé, MC `CitizenData.java:574`). S'il fait nuit, la décision suivante le renvoie se coucher.

## 6. Le port des corps (R § B.3)

`kernel/port/CitizenBodies` reçoit :

- **`boolean sleepIn(BodyId body, BlockPos bed)`** : couche le corps dans le lit. Faux si le bloc n'est pas un lit chargé, si le lit est pris, ou si le corps est inconnu. Ne lève jamais d'exception (CLAUDE.md § 4).
- **`void wakeUp(BodyId body)`** : lève le corps et le pose au point de sortie à côté du lit. Sans effet sur un corps debout ou inconnu.

`plugin/adapter/HytaleCitizenBodies` :

- `sleepIn` arrête la navigation, puis appelle `BlockMountAPI.mountOnBlock`, qui place et tourne le corps et occupe le point de couchage du lit. Si le client ne montre pas le PNJ couché avec le seul `MountedUpdate`, l'adaptateur ajoute `MovementStates.sleeping = true` ;
- `wakeUp` retire `MountedComponent` (comme `DismountCommand`), ce qui libère le lit, puis téléporte le corps à un point libre à côté du lit ;
- un `DidNotMount` renvoie `false`, journalisé une fois, puis en FINE ;
- signatures, `CommandBuffer` et thread à vérifier en 0.7.0-pre.4, avec leur note dans `docs/research/plugin-b-api.md`.

Le `FakeCitizenBodies` des tests garde l'état couché, et refuse un second corps dans un lit pris.

## 7. API et HyLens

Aucune rupture de l'API :

- l'état d'un citoyen y est déjà une chaîne (`CitizenStateChanged`) : `"SLEEP"` passe tel quel, et HyLens l'affiche sans changement ;
- `homeBuilding` est déjà dans l'instantané du citoyen (`ApiSnapshots`).

On n'ajoute à l'API ni `asleep` ni les lits (YAGNI). `apiCheck` doit rester vert sans `apiDump`.

## 8. Persistance (schéma 6)

`ColonySerializer.SCHEMA_VERSION` passe de 5 à 6, par une étape de `MigrationChain`, avec la fixture d'une colonie au schéma 5 (skill `add-migration`). La lecture reste tolérante, et une clé absente prend sa valeur par défaut :

- `LivingModule` : `residents` (ids), `hiringMode` (défaut `DEFAULT`) ;
- `BedModule` : `beds` (positions) ;
- citoyen : `asleep` (défaut false), `bedPos` (défaut aucun) ;
- `ColonySettings` : `autoHousing` (défaut true).

La migration 5 → 6 n'a rien à réécrire. Ce sont la réparation au chargement (§ 1, § 2) qui reconstruit les habitants depuis les `homeBuilding` déjà sauvés, et le rescan du plan qui retrouve les lits.

## 9. Textes

Clés en en-US et fr-FR dans `hycolony.lang` (skill `add-lang-key`), reprises des libellés de MC :

- onglet « Habitants » : titre, « Assignés {p0}/{p1} », Attribuer, Retirer, les modes, « Travaille à {p0} blocs d'ici », « Sans-abri », « Distance actuelle au travail », l'infobulle du mode manuel, le libellé du niveau 0 ;
- messages : « hometoofar » et « tous les citoyens dorment » ;
- l'état `SLEEP` dans la fenêtre du citoyen.

## 10. Tests (TDD, cœur)

- **Attribution** : auto en `AUTO` et en `DEFAULT` avec `autoHousing` ; aucune en `MANUAL`, en `LOCKED` ou au niveau 0 ; refus d'une hutte pleine et d'un doublon ; déménagement (l'ancienne maison perd l'habitant, `bedPos` effacé) ; un citoyen logé n'est jamais pris par une autre hutte ; `onRemoved`.
- **Actions** : permission `MANAGE_HUTS`, refus hors mode manuel effectif, cycle des modes.
- **Vue** : tri des sans-abri d'abord puis par distance, couleurs, niveau 0.
- **Lits** : enregistrement par le constructeur et la baguette, pas de doublon, rescan d'une résidence d'avant SP4, `removeBed`.
- **Horloge** : correspondance des phases dans `FakeGameClock` (les tests du calcul Hytale se font en jeu, le plugin n'a pas de tests unitaires).
- **Décision** : départ juste à temps selon la distance ; pas de départ avant `NIGHT - 2000` ; départ au-delà de `NIGHT` ; sans position de maison, pas de départ ; plainte au-delà de 160 blocs ; délai de 15 s en dormant ; réveil à l'aube ; le sommeil passe avant la pluie et le travail.
- **Coucher** : lit par rang, repli sur la hutte (rang hors liste, bloc qui n'est plus un lit, lit pris), `MAX_BED_TICKS`, retour en `WALKING_HOME` à plus de 3 blocs, sans-abri debout près de l'hôtel de ville, objet tenu retiré, loisir remis à zéro, message « tous dorment » une seule fois.
- **Persistance** : migration de la fixture schéma 5, aller-retour des nouvelles clés, réparation des habitants, citoyen endormi réveillé au chargement.

## 11. À vérifier en jeu (`docs/TESTING.md`)

- Une résidence prend des habitants seule ; en Manuel, on attribue et on retire depuis l'onglet.
- Le soir, les citoyens rentrent, et le constructeur cesse de travailler.
- Un PNJ couché dans un lit Hytale : pose couchée, corps qui ne glisse pas hors du lit, lit refusé à un joueur pendant ce temps.
- Lever à l'aube, y compris quand un joueur fait passer la nuit.
- Une résidence construite avant SP4 retrouve ses lits au chargement.
- Les citoyens en trop dorment debout dans la hutte.
