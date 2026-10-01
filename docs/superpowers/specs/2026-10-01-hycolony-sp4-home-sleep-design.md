# HyColony SP4 : maison, lit et sommeil des citoyens

Conception validée avec l'utilisateur le 2026-10-01. Source MineColonies : `sources/` (branche `version/main`, copiée vers le 2026-10-01).

Recherche, **à lire avant tout** :
- `docs/research/sp4-sleep-home.md`, noté « R § x ». Sa section **E** est l'audit ligne à ligne sur les sources locales de MC (2026-10-01), qui fait foi quand elle contredit le reste ; sa section **B** est revérifiée en 0.7.0-pre.4 ;
- `docs/research/plugin-b-api.md` § 41 : horloge, lits et montage d'un PNJ en 0.7.0-pre.4, noté « API § 41 ».

## Objectif

Les citoyens ont une maison. Le soir, ils rentrent, se couchent dans un vrai lit de leur résidence, et se lèvent à l'aube, comme dans MineColonies. Le travail s'arrête la nuit. Aujourd'hui, personne n'attribue de maison (`CitizenData.homeBuilding` n'est jamais écrit), `LivingModule` ne fait que `capacity = level`, et le constructeur travaille toute la nuit (R, « Réponse courte »).

## Portée

- **Dedans :**
  - l'attribution des résidences : automatique et manuelle, modes d'embauche, onglet « Habitants », bouton « Rappeler les citoyens », avertissements d'amélioration ;
  - la capacité de logement de la colonie (statistiques de l'hôtel de ville, `/hycolony info`) et la distance à la maison dans l'onglet d'embauche ;
  - l'emprise d'une hutte (boîte du plan) ;
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
  - le son `OFF_TO_BED` (pas de sons de citoyen) ;
  - les interactions de citoyen, donc la plainte « hometoofar » (§ 5.1) ;
  - les statuts visibles autres que le sommeil (icône `HOUSE`…) : le sommeil est montré sur la plaque de nom (§ 5.4) ;
  - l'usage du plafond de citoyens par l'immigration et les naissances, absentes de HyColony (le plafond lui-même est calculé et affiché, § 1.1) ;
  - la purge quotidienne des monstres par le constructeur (`purgedMobsToday`, remis à faux à son réveil), une fonction du constructeur non portée ;
  - un lit posé à la main par un joueur : MC ne l'enregistre pas non plus (`registerBlockPosition` n'a que le constructeur, la pose créative et le marteau d'assistant pour appelants, R § E).
- **Conséquence à connaître** : le loisir, l'expérience et le plafond de compétence dépendent déjà du niveau de la maison (portés). Un citoyen logé dépassera donc le plafond de 10 des sans-abri.

## Livraison

Deux étapes, chacune testable en jeu seule :

1. **La résidence** : attribution, capacité, onglet « Habitants », rappel, emprise des huttes, lits enregistrés (§ 1 à § 3).
2. **Le sommeil** : horloge, état `SLEEP`, port et adaptateur (§ 4 à § 7).

La persistance (§ 8) et les textes (§ 9) suivent chaque étape : chacune ajoute ses propres clés.

## 1. Attribution d'une maison (MC `LivingBuildingModule`, `AbstractAssignedCitizenModule`, R § A.6, § E)

`LivingModule` passe de `construction/hut` à `citizen/home` (l'IA de sommeil lit ses habitants, et la matrice figée de `FeatureDependenciesTest` interdit à `citizen` de dépendre de `construction`). Il devient un module d'attribution (`PersistentModule`, `TickingModule`, `BuildingEventsModule`). Il ne partage pas de classe mère avec `WorkerModule`, qui porte la logique de métier. Il reprend `HiringMode` (`job/HiringMode`), le même enum que MC.

- **Habitants** : liste ordonnée d'ids de citoyens, persistée par le module. `max(b) = b.level()` : 0 au niveau 0, 5 au niveau 5 (MC `getModuleMax`).
- **`assign(colony, b, citizen)`** renvoie `false` pour un doublon, une hutte pleine ou un citoyen absent. Sinon :
  - le citoyen quitte son ancienne maison (retiré de son `LivingModule`) ;
  - `homeBuilding = b.position()`, et `bedPos` est effacé (MC `CitizenData.setHomeBuilding`) ;
  - la colonie est marquée à sauvegarder.
- **`remove(colony, b, citizenId)`** : `homeBuilding` et `bedPos` du citoyen sont effacés (MC `LivingBuildingModule:96-100`). Comme dans MC, un citoyen qui dort n'est pas réveillé : il finit sa nuit, et la décision suivante le traite en sans-abri.
- **`onRemoved`** retire tous les habitants (MC `onDestroyed`). Une baisse de niveau n'expulse personne, comme dans MC.
- **Attribution automatique** (`onColonyTick`, MC `LivingBuildingModule.onColonyTick`) : si la hutte n'est pas pleine, et que le mode est `AUTO`, ou `DEFAULT` avec le réglage de colonie `autoHousing`, elle prend les citoyens **sans maison**, dans l'ordre de la liste des citoyens, jusqu'à être pleine. Elle ne déplace jamais un citoyen déjà logé.
  - `autoHousing` rejoint `ColonySettings`, à côté de `autoHiring`, avec le défaut **true** de MC (`AUTO_HOUSING_MODE`, `BuildingModules.java:529`).
- **Mode manuel effectif** : mode `MANUAL`, ou `DEFAULT` avec `autoHousing` à false. La fenêtre n'active Attribuer et Retirer que dans ce mode (§ 3).
- **Chargement** (MC `LivingBuildingModule:23-50`) : MC ne sauve pas la maison du citoyen, il réattribue chaque habitant lu par `assignCitizen`. On fait pareil : la liste du module fait foi, chaque habitant est réattribué dans l'ordre, et `assign` refuse au-delà de `max`. Un habitant en trop, inconnu ou en double redevient donc sans-abri, et un `homeBuilding` sauvé qui ne correspond à aucune liste est effacé (`ColonySerializer.heal`, CLAUDE.md § 5). La clé `home` du citoyen reste écrite pour l'API et les vieilles sauvegardes.

### 1.1 Capacité de la colonie (MC `CitizenManager.calculateMaxCitizens`, `WindowStatsPage:97-123`)

- `CitizenManager.maxCitizens()` : la somme des `max` des résidences construites (niveau > 0) ; une résidence `LOCKED` ne compte que ses habitants actuels. Le résultat est `max(1, min(somme, maxCitizenPerColony))`. Il est recalculé à l'attribution, au retrait et à l'amélioration.
- `ColonyConfig.Gameplay.maxCitizenPerColony` est enfin lu (sa Javadoc « read by nothing yet » change).
- Les statistiques de l'hôtel de ville (`TownHallStats`) affichent « Citoyens x/max » en couleur, avec les infobulles de MC « Needs Housing » (plus de citoyens que de places) et « Reached Configured Limit » (`maxCitizenPerColony` atteint). La déviation « no housing capacity » de `TownHallStats` disparaît.
- `/hycolony info` affiche la même ligne.
- L'usage du plafond par l'immigration et les naissances reste hors portée.

### 1.2 Rappel des citoyens (MC `RecallCitizenHutMessage:47-74`)

Le bouton « Rappeler les citoyens » de la résidence téléporte chaque habitant à la hutte (réveillé d'abord, § 5.6), par la même voie que la téléportation existante. Si aucun point libre n'est trouvé, le message `workerhuts.recallfail` est envoyé. Il exige `MANAGE_HUTS`.

### 1.3 Avertissements d'amélioration (MC `LivingBuildingView:93-130`)

La fenêtre de la résidence reprend les avertissements que MC montre avant une amélioration. Aux niveaux 3 à 5, MC avertit qu'il manque une cantine : faute de cantine dans HyColony, l'avertissement s'affiche toujours, comme dans MC sans cantine.

### 1.4 La maison dans l'embauche (MC `WindowHireWorker:349-376, 479-495`)

La liste d'embauche d'une hutte de travail trie aussi par distance à la maison et affiche « Habite ici » ou « Habite à N blocs », comme MC.

## 2. Les lits et l'emprise (MC `BedHandlingModule`, `AbstractSchematicProvider`, R § A.4, § E)

### 2.1 L'emprise d'une hutte

`Building` reçoit sa boîte d'emprise : les coins du plan posé (position, rotation, miroir), élargis d'**1 bloc** (MC `AbstractSchematicProvider:505-520`), et `isInBuilding(pos)`. Elle est calculée à la pose du plan et au chargement, à partir du plan de son niveau. Une hutte au niveau 0 prend la boîte du plan de niveau 1. C'est un prérequis du § 5.3.

### 2.2 Les lits

`citizen/home/BedModule` (`PersistentModule`) tient la liste **ordonnée** des positions de lits de la hutte, persistée. La résidence a les modules `living` et `bed`, comme les modules `HOME`, `LIVING` et `BED` de MC.

- **Enregistrement** : quand un bloc de plan est posé dans la hutte, et que le catalogue dit que c'est un lit, sa position est ajoutée sans doublon (MC `onBlockPlacedInBuilding`, appelé par `registerBlockPosition`). Les deux points de pose sont ceux qui appellent déjà `addContainer` :
  - le constructeur (`construction/builder/BuilderBlockWork`) ;
  - le collage à la baguette (`app/wand/PasteQueue`), l'équivalent de la pose créative de MC.
- Un lit Hytale est un seul bloc « modèle » avec des cellules de remplissage. Seul le **bloc de base** est enregistré, l'équivalent de la tête du lit de MC ; c'est aussi lui que le montage attend (API § 41, `BlockOperations.resolveBaseBlockPosition`).
- **`ItemCatalog.isBed(block)`** : `true` si le `BlockType` a des points de couchage (`getBeds() != null`, `BlockType.java:1661`). Faux sur un bloc inconnu. Tous les lits vanilla et les 20 lits HyVanilla en ont.
- **Résidences construites avant SP4** : la réparation au chargement relit une fois le plan au niveau de la hutte et enregistre ses lits, sans vérifier le monde (le lit est revérifié au coucher, § 5.3).
- **`removeBed(pos)`** retire une position qui n'est plus un lit (§ 5.3).
- **`onWakeUp`** ne fait rien : l'occupation des lits est tenue par Hytale (§ 6). `Deviation from MC` dans la Javadoc : MC remet `OCCUPIED = false`.
- *Écart avec MC* : l'ordre des lits est l'ordre de pose. Dans MC, `bedList` vient d'un `HashSet`, donc un citoyen peut changer de lit d'une session à l'autre.
- *Constat, non corrigé ici* : les plans de la résidence Outlander ont moins de lits que d'habitants aux niveaux 2, 3 et 5 (R § B.4). Les citoyens en trop dorment debout dans la hutte, comme dans MC quand le rang dépasse la liste. Le choix des préfabs est un sujet `styles.json` distinct.

## 3. Onglet « Habitants » (MC `WindowHutLiving`, `WindowAssignCitizen`, R § A.6, § E)

*Écart d'interface avec MC* : MC a deux fenêtres (la résidence, avec un bouton « Manage Housing », puis `WindowAssignCitizen`). Les huttes de HyColony montrent leurs modules en onglets : `LivingModule` implémente `ProvidesTab`, et son onglet réunit les deux fenêtres. Sa vue `ResidentsView` (record, `ModuleTab`) contient :

- « Assignés x/max » (`com.minecolonies.coremod.gui.home.assigned`) ;
- les habitants, avec un bouton Retirer (MC `gui.hiring.buttonunassign`). Chaque ligne affiche « Travaille à N blocs d'ici », en rouge au-delà de `FAR_DISTANCE_THRESHOLD = 300`, ou « Sans emploi » ;
- les citoyens qu'on peut attribuer : tous sauf les habitants de cette hutte. Tri de MC (`WindowAssignCitizen:196-210`) : la valeur est la distance de l'atelier à cette maison ; un citoyen sans atelier vaut 0 s'il est sans-abri (en tête) et `Integer.MAX_VALUE` s'il est logé (en dernier).
  - Chaque ligne affiche « Travaille à N blocs d'ici », en vert si N est plus court que sa distance actuelle maison-atelier ; puis « Sans-abri », ou « Distance actuelle au travail : M », en rouge au-delà de 300 ; ou « Sans emploi ».
- le mode, qui fait tourner `DEFAULT → AUTO → MANUAL → LOCKED` ;
- le bouton « Rappeler les citoyens » (§ 1.2) et les avertissements d'amélioration (§ 1.3).

Boutons (MC `WindowAssignCitizen:301-317`) :

- Attribuer et Retirer ne sont actifs qu'en mode manuel effectif (§ 1). Sinon, ils sont grisés avec l'infobulle `gui.home.hire.warning` ;
- Attribuer est aussi grisé quand la hutte est pleine ;
- au niveau 0, les boutons d'attribution envoient dans le chat le message de MC `workerhuts.level0` et ne font rien (MC `WindowHutLiving:115-124`).

Actions du cœur, dans `app/action` (un nouveau `HousingActions`, pour ne pas grossir `HutActions`) :

- `assign(player, hutPos, citizenId)`, `unassign(…)`, `cycleMode(player, hutPos)` et `recall(player, hutPos)` ;
- elles exigent `MANAGE_HUTS` (MC `AbstractColonyServerMessage.permissionNeeded`) et ré-affichent la fenêtre de la hutte ;
- comme MC `AssignUnassignMessage:119-130`, le serveur ne vérifie pas le mode : seule la fenêtre le fait. `assign` sur une hutte pleine ou déjà la maison du citoyen ne fait rien ; sinon le citoyen quitte son ancienne maison, puis il est attribué.

Côté plugin : un `ResidentsTab.ui` et une ligne `ResidentRow.ui`, copiés de `WorkerRow.ui` et des onglets existants, et leur rendu dans la fenêtre de hutte (agent `ui-lang-checker` à la relecture).

## 4. L'horloge « façon MC » (R § A.2, § B.1, API § 41)

Choix de l'utilisateur : la correspondance se fait **par phases**.

`kernel/port/GameClock` reçoit :

- **`dayTime()`**, l'heure MC dans [0, 24000[ :
  - le jour Hytale, du lever (`SUNRISE_SECONDS`, 04:47:59) au coucher (`SUNRISE_SECONDS + DAYTIME_SECONDS`, 19:11:59), correspond à [0, 12600[ ;
  - la nuit Hytale, du coucher au lever suivant, correspond à [12600, 24000[.

  Ainsi `NIGHT = 12600` tombe au coucher du soleil, `NIGHT - 2000` vers 16:55, et 0 au lever.
- **`realTicksUntil(dayTime)`**, les ticks réels avant la prochaine fois que l'horloge atteint cette heure MC. **`Long.MAX_VALUE` si l'heure du monde est en pause**.

`isDaytime()` devient `dayTime() <= NIGHT`, comme MC `WorldUtil.isDayTime` (`WorldUtil:165-168`). `Colony.checkDayTime` ne change pas.

`plugin/adapter/HytaleGameClock` (API § 41) :

- l'heure du jour vient de `WorldTimeResource.getGameDateTime()` ou `getDayProgress()`, les durées réelles de `World.getDaytimeDurationSeconds()` et `getNighttimeDurationSeconds()` (surcharge du monde, sinon GameplayConfig ; 1728 / 1152 par défaut). Une durée de jour modifiée par le monde est donc suivie ;
- `realTicksUntil` place l'heure `s` (secondes de jeu) sur le cycle réel, comme `WorldTimeResource.tick` : `x(s) = (s − SUNRISE)·D/DAYTIME` le jour, `D + floorMod(s − SUNRISE − DAYTIME, 86400)·N/NIGHTTIME` la nuit, puis `20 · floorMod(x(cible) − x(maintenant), D + N)` ;
- un tick MC vaut donc environ 2,74 ticks réels le jour et 2,02 la nuit, avec les durées par défaut.

Le passage de nuit des joueurs fait sauter l'heure à leur réveil, 04:47:00, soit `dayTime ≈ 23980` : les citoyens se lèvent environ 2 s réelles plus tard, quand l'horloge atteint 0. Si un joueur se lève avant la fin du saut, l'heure s'arrête en pleine nuit, et les citoyens dorment jusqu'à l'aube (API § 41).

Le `FakeGameClock` des tests fixe `dayTime`, la pause, et un rapport ticks réels / ticks MC par phase.

## 5. Le sommeil

### 5.1 La décision (MC `CitizenAI.calculateNextState`, `CitizenSleepHandler.shouldGoSleep`, R § A.1, § A.2, § E)

`CitizenState` gagne `SLEEP`. La décision de sommeil passe **avant** la pluie, le loisir et le travail, comme dans MC, et s'évalue toutes les `DECIDE_INTERVAL_TICKS = 10` dans **tous** les états, y compris `WORKING`.

Un collaborateur `citizen/sleep/SleepDecision` reprend la règle :

- **Le soir** : si `dayTime > NIGHT - 2000` (MC `!isPastTime(NIGHT - 2000)`) :
  - un citoyen déjà en `SLEEP` y reste, et la décision suivante n'a lieu que dans 15 s (`20 * 15` ticks, MC `setCurrentDelay`) ;
  - sinon, il part si `shouldGoSleep()` ;
  - sinon, la suite de la décision s'applique.
- **`shouldGoSleep()`** (MC `CitizenSleepHandler:231-281`) :
  - faux sans position de maison (§ 5.2) ;
  - distance pondérée de la position du corps à la maison, en `int` comme MC : `yDiff = (int)(|dy| * Y_DIFF_WEIGHT)`, puis `(int) sqrt(dx² + dz² + yDiff²)`, avec `Y_DIFF_WEIGHT = 1.5` ;
  - temps de trajet : `distance * TIME_PER_BLOCK`, avec `TIME_PER_BLOCK = 6`, en ticks **réels** ;
  - temps restant : `realTicksUntil(NIGHT)`, ou 0 si l'horloge a déjà dépassé `NIGHT` ;
  - vrai si le temps restant moins le temps de trajet est ≤ 0. Le citoyen part juste à temps pour arriver au coucher du soleil, au plus tôt vers 16:55.
  - *Écart avec MC* : MC compte le trajet en ticks de jour, égaux aux ticks réels dans Minecraft. Ici, avec le calage par phases, un tick MC ne vaut plus un tick réel : on compare donc deux durées réelles.
- **La plainte « hometoofar »** (maison à plus de `MAX_NO_COMPLAIN_DISTANCE = 160` blocs de l'atelier) est une interaction du citoyen dans MC (priorité `IMPORTANT`, `CitizenSleepHandler:266-276`). HyColony n'a pas d'interactions : elle est reportée avec elles, et le seuil reste une constante citée dans la Javadoc.
- **Le jour** : dès que `dayTime <= NIGHT - 2000` (donc à 0, l'aube), un citoyen endormi se réveille (§ 5.6), puis la décision normale reprend : `WORKING` si le métier a du travail, sinon `IDLE`.

`CitizenAI` ne garde que le branchement : la règle va dans `SleepDecision`, la marche et le lit dans `SleepAI` (§ 5.3).

### 5.2 La position de la maison (MC `CitizenData.getHomePosition`)

La maison si le citoyen en a une, sinon l'hôtel de ville, sinon aucune. *Écart avec MC* : pas de repli sur la taverne (absente) ni sur le centre de la colonie (l'hôtel de ville en tient lieu dans HyColony).

### 5.3 Rentrer et se coucher (MC `EntityAISleep`, R § A.3, § A.4, § E)

`citizen/sleep/SleepAI` est une petite machine d'états, créée en entrant en `SLEEP` (`usedBed` vide, `bedTicks = 0`) :

| Sous-état | Cadence | Effet |
|---|---|---|
| `WALKING_HOME` | 30 ticks | `walkHome()` |
| `FIND_BED` | 30 ticks | `findBedAndTryToSleep()`, puis `SLEEPING` si `findBed()` |
| `SLEEPING` | 30 ticks | `sleep()` |

- **`walkHome()`** :
  - avec maison : passe à `FIND_BED` dès que le corps est dans l'emprise de la hutte (§ 2.1) ; sinon il y marche (MC `walkToBuilding`, nos marcheurs `BlockApproach`) ;
  - sans maison : passe à `FIND_BED` à 4 blocs de l'hôtel de ville (`distSqr <= RANGE_TO_BE_HOME`, `RANGE_TO_BE_HOME = 16`) ; sinon il y marche.
- **`findBedAndTryToSleep()`** (MC `EntityAISleep:159-220` ; sans maison, il ne fait rien) :
  - tant que `usedBed` est vide **ou vaut la hutte**, le lit est rechoisi à chaque tentative : celui du **rang** du citoyen parmi les habitants (`BedModule` à cet index), sinon la position de la hutte ;
  - un lit n'est retenu que si son bloc est un lit (`isBed`) et que le bloc au-dessus n'est pas solide (MC : lit, panneau DO, trappe ou bloc non solide ; seul « non solide » a un sens pour un lit Hytale) ;
  - si la position n'est plus un lit : `removeBed`, puis la tentative s'arrête là, sans marcher. La suivante recalcule le rang sur la liste raccourcie ;
  - le citoyen marche jusqu'au lit dans le bâtiment. Il est **arrivé** à 1,5 bloc, ou à 12 blocs une fois son chemin fini (MC `walkToPosInBuilding(…, 12)`). Tant qu'il n'est pas arrivé, `bedTicks` revient à 0 ; à l'arrivée, `bedTicks++` ;
  - il tente `sleepIn` (§ 6). Si le lit est déjà pris, ou si `sleepIn` échoue (la hutte n'est pas un lit), `usedBed` et `bedPos` sont effacés.
- **`findBed()`** : vrai une fois endormi, ou quand `bedTicks` atteint `MAX_BED_TICKS = 10`.
- **`sleep()`** : si le citoyen a un lit et en est à plus de 3 blocs (`distSqr > 9`), il retourne en `WALKING_HOME`. Sans lit, il retente `findBedAndTryToSleep()`. Un citoyen sans lit reste donc debout dans la maison, et un sans-abri debout près de l'hôtel de ville, toute la nuit, comme dans MC.

### 5.4 Se coucher (MC `CitizenSleepHandler.trySleep`)

Quand `sleepIn` réussit :

- l'objet tenu est retiré (`setHeldItem(empty)`) ;
- `asleep = true`, `leisureTime = 0` (MC `CitizenData.setAsleep`), `bedPos = lit` ;
- la plaque de nom montre le sommeil (MC : statut visible `SLEEP` et interaction « zZzz… » ; *écart* : comme le « ! » de `CitizenNameplates`, Hytale n'a pas d'icône au-dessus de la tête, donc le nom la porte) ;
- des particules de sommeil toutes les 30 ticks pendant `SLEEPING` (MC `EntityAISleep`). Hytale a `Server/Particles/NPC/Emotions/Sleepy.particlesystem` et `ParticleUtil.spawnParticleEffect` : un nouvel effet `SLEEP` du port `WorldEffects` ;
- quand **tous** les citoyens dorment, le message `ALL_CITIZENS_ARE_SLEEPING` est envoyé une fois aux joueurs de la colonie (MC `CitizenManager.onCitizenSleep`). Le drapeau « déjà envoyé » repasse à faux **à la tombée de la nuit** (MC `Colony:645`, sur `NightFell`).

### 5.5 Le travail interrompu

Un citoyen en `WORKING` qui part dormir perd son IA de métier (`dropJobAI`, comme une sortie vers `IDLE`). Au réveil, son métier repart de son premier état, comme MC `resetAI`. Les règles « ne pas interrompre » ne s'appliquent pas au sommeil : dans MC, la décision de sommeil passe avant `canBeInterrupted`.

### 5.6 Le réveil (MC `CitizenSleepHandler.onWakeUp`, R § A.5)

- `onWakeUp` est appelé sur l'atelier, le **métier** (`Job.onWakeUp`, à rajouter, vide par défaut) et la maison (`BuildingEventsModule.onWakeUp(Colony, Building)`, à rajouter, depuis l'équivalent de MC `AbstractBuilding.onWakeUp`) ;
- si le citoyen dormait vraiment dans un lit, `wakeUp(body)` le descend au point de sortie, et `bedPos` est effacé ;
- `asleep = false`, et la plaque de nom redevient normale.

Le réveil a aussi lieu, comme dans MC :

- **avant toute téléportation** du citoyen (MC `TeleportHelper:49-52`) : le rappel (§ 1.2), la téléportation de débogage de HyLens, et le dernier recours de l'anti-blocage. Une téléportation Hytale fait de toute façon descendre du lit (API § 41) ;
- **à l'aube, pour un citoyen sans corps** : il réapparaît (MC `CitizenManager.onWakeUp`), par la voie de réapparition existante.

### 5.7 Rechargement et réapparition (MC `CitizenData:574-577`)

`asleep` et `bedPos` sont persistés (MC `TAG_ASLEEP`, `TAG_BEDS`). MC réveille un citoyen marqué endormi à chaque apparition de son corps, s'il n'a pas de `bedPos`. Hytale ne sauve pas l'état « couché » d'un PNJ (`MountedComponent` sans codec, API § 41) : un corps qui apparaît est toujours debout. `Deviation from MC` : on réveille donc tout citoyen endormi à l'apparition de son corps, `bedPos` ou non. S'il fait nuit, la décision suivante le renvoie se coucher.

### 5.8 Le corps endormi

- **Pas d'anti-blocage pendant le sommeil** : `StuckHandler` ne surveille que les marches, et le citoyen couché ne marche pas. Le dernier recours de l'anti-blocage est une téléportation, qui réveille (§ 5.6).
- **Pas de poussée** : MC ne pousse pas un citoyen endormi (`EntityCitizen`). Dans Hytale, la poussée d'un PNJ monté est **[in-game]**. Si elle existe, l'adaptateur la coupe le temps du sommeil.

## 6. Le port des corps (R § B.3, API § 41)

`kernel/port/CitizenBodies` reçoit :

- **`boolean sleepIn(BodyId body, BlockPos bed)`** : couche le corps dans le lit (bloc de base). Faux si le bloc n'est pas un lit chargé, si le lit est pris, ou si le corps est inconnu. Ne lève jamais d'exception (CLAUDE.md § 4).
- **`boolean isInBed(BodyId body)`** : le corps est toujours couché. Un lit quitté sans nous (passage de nuit des joueurs, téléportation, lit cassé) se voit ainsi au tick suivant de `sleep()`, qui le traite comme un citoyen loin de son lit.
- **`void wakeUp(BodyId body)`** : lève le corps et le pose au point de sortie à côté du lit, et met fin à sa pose couchée, même pour un corps que Hytale a déjà fait descendre (pour qu'il ne marche jamais couché). Sans effet sur un corps inconnu.

`plugin/adapter/HytaleCitizenBodies` (API § 41) :

- `sleepIn` arrête la navigation (`MoveTarget.active = false`, comme aujourd'hui), puis appelle `BlockMountAPI.mountOnBlock(ref, commandBuffer, pos, hit)`. Le `CommandBuffer` vient de `Store.forEachChunk(query, (chunk, cb) -> …)`, la seule voie publique hors d'un système, ce qui garde la réponse synchrone. `mountOnBlock` place et tourne le corps et occupe le point de couchage du lit ;
- le client ne couche pas un PNJ d'après le seul `MountedUpdate` ni d'après `MovementStates.sleeping` (vu en jeu le 2026-10-01) : l'adaptateur joue l'animation `Sleep` du modèle `Player` sur le créneau `Status` (elle couche le `Pelvis`, comme pour `Outlander_Peon`, `plugin-b-api.md` § 44) et pose aussi `sleeping = true` pour la boîte de collision ; au lever, il arrête l'animation et remet `sleeping` à faux ;
- si la gravité de `SteeringSystem` fait glisser le corps, l'adaptateur pose `Frozen` pendant le sommeil, et le retire au réveil et à l'apparition du corps (il est sauvegardé) **[in-game]** ;
- `wakeUp` retire `MountedComponent` (comme `DismountCommand`), ce qui libère le lit, retire le `PlayerSomnolence` que `WakeUpOnDismountSystem` pose sur tout PNJ qui sort d'un lit, puis téléporte le corps à un point libre à côté du lit ;
- un `DidNotMount` renvoie `false`, journalisé une fois, puis en FINE ;
- un citoyen peut prendre le lit d'un joueur (`mountOnBlock` ignore le propriétaire du `RespawnBlock`), comme dans MC où le lit est celui de la résidence. Un joueur qui veut un lit occupé par un citoyen est refusé par Hytale (`NO_MOUNT_POINT_FOUND`, `BedInteraction:85-99`) ; MC lui envoie « bed occupied » (`EventHandler:610-631`) : le plugin envoie ce message s'il peut intercepter l'interaction, sinon le refus natif suffit **[in-game]**.

Le `FakeCitizenBodies` des tests garde l'état couché, refuse un second corps dans un lit pris, et peut simuler un lit quitté.

## 7. API et HyLens

Aucune rupture de l'API :

- l'état d'un citoyen y est déjà une chaîne (`CitizenStateChanged`) : `"SLEEP"` passe tel quel, et HyLens l'affiche sans changement ;
- `homeBuilding` est déjà dans l'instantané du citoyen (`ApiSnapshots`) ;
- la téléportation de débogage passe par `CitizenAI.teleport`, qui réveille d'abord (§ 5.6).

On n'ajoute à l'API ni `asleep` ni les lits (YAGNI). `apiCheck` doit rester vert sans `apiDump`.

## 8. Persistance (schéma 6)

`ColonySerializer.SCHEMA_VERSION` passe de 5 à 6, par une étape de `MigrationChain`, avec la fixture d'une colonie au schéma 5 (skill `add-migration`). La lecture reste tolérante, et une clé absente prend sa valeur par défaut :

- `LivingModule` : `residents` (ids), `hiringMode` (défaut `DEFAULT`) ;
- `BedModule` : `beds` (positions) ;
- citoyen : `asleep` (défaut false), `bedPos` (défaut aucun) ;
- `ColonySettings` : `autoHousing` (défaut true).

La migration 5 → 6 remplit `residents` de chaque résidence à partir des `home` déjà sauvés des citoyens, dans l'ordre des citoyens. Le chargement (§ 1) les réattribue ensuite comme MC, et le rescan du plan (§ 2.2) retrouve les lits. L'emprise (§ 2.1) n'est pas sauvée : elle se recalcule.

## 9. Textes

Clés en en-US et fr-FR dans `hycolony.lang` (skill `add-lang-key`), reprises des libellés de MC (R § E.5) :

- onglet « Habitants » : titre, « Assignés {p0}/{p1} », Attribuer, Retirer, Rappeler les citoyens, les quatre modes, « Travaille à {p0} blocs d'ici », « Sans-abri », « Distance actuelle au travail : {p0} », « Sans emploi », l'infobulle du mode manuel, les avertissements d'amélioration ;
- embauche : « Habite ici », « Habite à {p0} blocs » ;
- hôtel de ville : « Citoyens {p0}/{p1} », « Needs Housing », « Reached Configured Limit » ;
- messages : `workerhuts.level0`, `workerhuts.recallfail`, « tous les citoyens dorment », « lit occupé » ;
- l'état `SLEEP` dans la fenêtre du citoyen.

## 10. Tests (TDD, cœur)

- **Attribution** : auto en `AUTO` et en `DEFAULT` avec `autoHousing` ; aucune en `MANUAL`, en `LOCKED` ou au niveau 0 ; refus d'une hutte pleine et d'un doublon ; déménagement (l'ancienne maison perd l'habitant, `bedPos` effacé) ; un citoyen logé n'est jamais pris par une autre hutte ; `onRemoved` ; un retrait ne réveille pas.
- **Capacité** : somme des résidences construites, `LOCKED`, `max(1, …)`, plafond de la config, couleurs et infobulles.
- **Actions** : permission `MANAGE_HUTS`, pas de vérification du mode côté serveur, cycle des modes, rappel (réveil, puis téléportation ; message d'échec), niveau 0.
- **Vues** : tri de MC (sans atelier sans-abri en tête, sans atelier logé en dernier), couleurs, « Sans emploi », Attribuer grisé quand la hutte est pleine ; la distance à la maison dans l'embauche.
- **Emprise** : coins du plan tournés, élargis d'un bloc ; hutte au niveau 0.
- **Lits** : enregistrement par le constructeur et la baguette, bloc de base seul, pas de doublon, rescan d'une résidence d'avant SP4, `removeBed`.
- **Horloge** : correspondance des phases et pause dans `FakeGameClock` (le calcul Hytale se vérifie en jeu, le plugin n'a pas de tests unitaires).
- **Décision** : départ juste à temps selon la distance (troncatures en `int`) ; pas de départ avant `NIGHT - 2000` ; départ au-delà de `NIGHT` ; heure en pause ; sans position de maison, pas de départ ; délai de 15 s en dormant ; réveil à l'aube ; le sommeil passe avant la pluie, le loisir et le travail.
- **Coucher** : lit par rang rechoisi à chaque tentative, repli sur la hutte (rang hors liste, lit pris), `removeBed` sans marcher puis rang recalculé, bloc au-dessus solide refusé, arrivée à 1,5 et 12 blocs, `bedTicks` remis à 0 en route, `MAX_BED_TICKS`, retour en `WALKING_HOME` à plus de 3 blocs en gardant son lit, recouché sur place près de son lit (lit quitté sans nous, réveil par téléportation), sans-abri debout près de l'hôtel de ville, objet tenu retiré, loisir remis à zéro, plaque de nom, particules, message « tous dorment » une seule fois puis réarmé à la nuit.
- **Réveil** : `onWakeUp` de l'atelier, du métier et de la maison ; réveil avant téléportation ; réapparition à l'aube d'un citoyen sans corps.
- **Persistance** : migration de la fixture schéma 5 (`residents` remplis depuis `home`), aller-retour des nouvelles clés, habitants en trop rendus sans-abri, citoyen endormi réveillé à l'apparition de son corps.

## 11. À vérifier en jeu (`docs/TESTING.md`)

- Une résidence prend des habitants seule ; en Manuel, on attribue et on retire depuis l'onglet ; le rappel ramène les habitants.
- L'hôtel de ville et `/hycolony info` montrent « Citoyens x/max ».
- Le soir, les citoyens rentrent, et le constructeur cesse de travailler.
- Un PNJ couché dans un lit Hytale et un lit HyVanilla : pose couchée, corps qui ne glisse pas hors du lit, pas de poussée, lit refusé à un joueur pendant ce temps, particules de sommeil.
- Lever à l'aube, y compris quand un joueur fait passer la nuit, et quand il se lève avant la fin du saut.
- Une résidence construite avant SP4 retrouve ses lits et ses habitants au chargement.
- Les citoyens en trop dorment debout dans la hutte.

## 12. Décisions prises à la réalisation

Le plan (`docs/superpowers/plans/2026-10-01-hycolony-sp4-home-sleep.md`) a été suivi, avec ces ajustements :

- **Paquets.** La maison et les lits sont dans `citizen/home`, le sommeil dans `citizen/sleep` (la matrice figée de `FeatureDependenciesTest` interdit à `citizen` de dépendre de `construction`). L'emprise d'une hutte est `colony/HutFootprint` (`building` ne voit pas les plans).
- **Plafond de citoyens.** *Écart avec MC* : MC plafonne aussi par la recherche `CITIZEN_CAP` (25 tant qu'elle n'est pas faite) ; sans recherche, seule `maxCitizenPerColony` plafonne.
- **Lit quitté.** `CitizenBodies.isInBed` voit un lit quitté sans nous (lit cassé, nuit passée par les joueurs, téléportation) ; le citoyen redevient éveillé sans les crochets de réveil (`SleepHandler.leftBed`) et retourne se coucher. *Écart avec MC* : rien ne sort un citoyen de son lit dans MC.
- **Drapeau « tous dorment ».** Il vit sur `CitizenManager` comme `areCitizensSleeping` de MC ; `SleepNotice` n'a que des méthodes statiques (limites PMD de couplage et de nombre de méthodes).
- **Plaque de nom.** Le « ! » d'une demande au joueur passe avant « zZz », comme MC dessine l'interaction avant le statut.
- **Confirmation d'amélioration.** Au lieu d'une fenêtre `WindowConfirm`, le premier clic sur « Améliorer » affiche l'avertissement dans le panneau des options et le bouton devient « Confirmer » ; le second lance l'ordre.
- **Bouton de mode.** L'onglet « Habitants » reprend les clés « Embauche : … » des huttes de travail.
- **Apparence.** L'onglet « Habitants » suit l'apparence actuelle des onglets de hutte ; il suivra l'apparence MineColonies (`Pages/HyColony/Mc/`) quand la fenêtre de hutte sera convertie (CLAUDE.md § 7).
- **Horloge.** `HytaleGameClock.realTicksUntil` travaille directement sur l'heure MC, chaque phase étant linéaire en temps réel ; le résultat est celui de la formule du § 4.
- **Plugin.** Le coucher passe par `BlockMountAPI.mountOnBlock` dans `Store.forEachChunk` (variante à prédicat, arrêtée au premier morceau) ; la particule est `Sleepy` (id-map `sleepParticle`). Le message « lit occupé » au joueur n'est pas envoyé : le refus natif de Hytale (`NO_MOUNT_POINT_FOUND`) est gardé, à confirmer en jeu.
- **Apparition d'un corps.** *Écart avec MC* : MC n'appelle `onWakeUp` à l'apparition que sans position de lit (`CitizenData.initEntityValues`) ; ici toujours (`SleepHandler.onWakeUp`), Hytale ne sauvant aucun PNJ couché. Comme MC, cela met fin au loisir (`setAsleep` le remet à zéro dans les deux sens).
- **Recoucher.** *Écart avec MC* : MC ne fait que réappliquer la pose près du lit ; ici un citoyen près de son lit mais pas couché refait un `trySleep` complet.
- **Position de réapparition.** Comme `nextRespawnPos` de MC, elle est effacée dès qu'un corps apparaît (`CitizenManager.spawnBody`) : un rappel raté ne fixe pas les réapparitions suivantes à la hutte.
