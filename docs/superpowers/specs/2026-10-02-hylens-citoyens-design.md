# HyLens V2, lot 2 : faire apparaître un citoyen et régler sa saturation

Suite de `2026-10-02-hylens-livre-mc-design.md` (§ 2, lot 2) et de `2026-09-30-hycolony-api-hylens-design.md` (API v1).

Révisée après les relectures du 2026-10-02 (fidélité à MC) : mode créatif exigé, `=`, `+` et `-` distincts, valeur hors bornes refusée, genre tiré, journal au point d'apparition, refus « opérateur » avant la colonie.

## 1. Objectif

L'utilisateur veut agir sur les citoyens depuis HyLens (2026-10-02). Il a choisi les actions de MineColonies seules, sans ajout (ni compétences, ni métier). Les commandes de MC sur les citoyens sont `spawnNew`, `kill`, `modify … saturation` et `reload` (`sources/minecolonies/.../commands/citizencommands/`) :
- `reload` existe déjà dans HyLens (« Refaire le corps », `DebugAccess.respawnBody`) ;
- `kill` appelle `EntityCitizen.die`, donc toute la mort d'un citoyen, qui n'est pas portée (les citoyens sont invulnérables, spec SP4b § 4). « Tuer » viendra avec le sous-projet « mort des citoyens » (`docs/research/citizen-death.md`), décision de l'utilisateur du 2026-10-02 ;
- ce lot porte donc **`spawnNew`** et **`modify … saturation`**.

## 2. MineColonies

**`CommandCitizenSpawnNew`** (`IMCOPCommand` : opérateurs seuls, `COMMAND_REQUIRES_OP` vérifié avant la colonie) appelle `CitizenManager.spawnOrCreateCivilian(null, world, [], force = true)` (`core/colony/managers/CitizenManager.java` l. 231-310) :
- `force` passe outre le réglage « nouveaux citoyens » de la mairie (`MOVE_IN`) et l'avertissement du nombre maximum de citoyens ;
- le citoyen n'est créé qu'une fois son point d'apparition trouvé autour de l'hôtel de ville chargé (`createAndRegisterCivilianData` puis `initForNewCivilian`, qui tire son genre au hasard, `CitizenData.java:519`). Sans hôtel de ville, sans hôtel de ville chargé, ou sans place (l'avertissement `WARNING_COLONY_NO_ARRIVAL_SPACE` part alors), rien n'est créé et la commande plante sur un `null` (bug de MC) ;
- l'événement `CitizenSpawnedEvent` va au journal **au point d'apparition** (l. 293) ; la commande répond `COMMAND_CITIZEN_SPAWN_SUCCESS` avec le nom.

**`CommandCitizenModify`** (`IMCColonyOfficerCommand`) vérifie, dans l'ordre (l. 92-127) :
1. la valeur, entre 0 et `MAX_SATURATION` (`DoubleArgumentType.doubleArg(0, MAX)`, la commande est rejetée sinon) ;
2. opérateur, ou gestionnaire de la colonie ;
3. un non-opérateur n'y a droit que si `canPlayerUseModifyCitizensCommand` (défaut `false`, `ServerConfiguration` l. 157), sinon `COMMAND_DISABLED_IN_CONFIG` ;
4. un joueur, opérateur compris, doit être en **mode créatif** ; seule la console y échappe (`COMMAND_REQUIRES_CREATIVE`) ;
5. le citoyen.

Puis `= v` appelle `setSaturation`, `+ v` `increaseSaturation` (plafonnée au maximum), `- v` `decreaseSaturation` : `v` fois le `foodModifier` de la config, plancher 0, et `justAte = false` ; rien sur une colonie inactive (`CitizenData.java:1158-1171`). Les suggestions sont 0 et le maximum pour `=`, 1 pour `+` et `-`. La commande répond `COMMAND_CITIZEN_MODIFY_SUCCESS` avec la nouvelle valeur.

## 3. L'API (`DebugAccess`, version 1.2)

Deux méthodes et une énumération, `@Experimental` comme tout `dev.hycolony.api.debug`. L'API passe en **1.2.0** (`ApiVersion.CURRENT`), et HyLens est construit contre elle (`ApiCompatibility.BUILT_AGAINST`). `api/api.txt` ne change pas : il ne liste pas les parties `@Experimental`.

```java
ActionResult spawnCitizen(Actor actor, ColonyRef colony);
ActionResult modifySaturation(Actor actor, CitizenRef ref, SaturationChange change, double value);
enum SaturationChange { SET, INCREASE, DECREASE } // MC's =, +, -
```

**Droits et refus**, dans l'ordre de MC (`CoreDebugEdits`) :

| Action | Ordre des refus |
|---|---|
| `spawnCitizen` | joueur non opérateur (`hycolony.debug.refused.operator`, MC `COMMAND_REQUIRES_OP`), colonie (`refused.colony`) ; puis colonie inconnue `NotFound` ; puis `Unavailable` sans arrivée possible |
| `modifySaturation` | valeur hors de [0, 60] ou NaN (`hycolony.debug.refused.value`) ; opérateur hors créatif (`refused.creative` : chez MC, un opérateur passe le contrôle d'officier sans la colonie) ; colonie inconnue `NotFound` ; ni opérateur ni gestionnaire (`hycolony.permission.denied`) ; gestionnaire non opérateur sans la config (`refused.config`) ; joueur hors créatif (`refused.creative`, MC `COMMAND_REQUIRES_CREATIVE`) ; citoyen inconnu `NotFound` |

Un plugin passe partout, comme la console de MC ; la colonie, simple cause, est refusée.

## 4. Le cœur de HyColony (TDD)

- **`CitizenManager.spawnForced()`** : MC `spawnOrCreateCivilian(force = true)`. La création du citoyen est dans `Newcomers.register(colony, citizens, balanced)` : genre **équilibré** pour une arrivée initiale (MC `onColonyTick`), **tiré** pour l'arrivée forcée (MC `initForNewCivilian`). Le corps est essayé avant de garder le citoyen : sans hôtel de ville chargé, ou sans place (les joueurs prévenus, `CitizenArrival.tellNoSpace`), rien n'est créé. Le journal `citizenSpawned` est au point d'apparition du corps ; la colonie est marquée à réécrire ; l'événement `CitizenSpawned` part (et son pendant d'API).
- **`CoreDebugEdits`** porte les deux actions (`CoreDebugActions` aurait dépassé 150 lignes) ; `CoreColonyWorld.isOperator` sert au refus « opérateur » avant la colonie.
- **`modifySaturation`** : `SET` → `CitizenData.setSaturation`, `INCREASE` → `CitizenHunger.increase`, `DECREASE` → `CitizenHunger.decrease(value, foodModifier)` (déjà le port de MC `decreaseSaturation`, `justAte` compris), sans effet sur une colonie `INACTIVE` (MC `Colony.isActive`). La colonie est marquée à réécrire.
- **Config** : `ColonyConfig.Commands.canPlayerUseModifyCitizensCommand` (défaut `false`), clé `CanPlayerUseModifyCitizensCommand` de la section `Commands` de `config.json`.
- **Documentation** : `api/README.md` (§ 5), `docs/research/config-inventory.md`.
- **Textes** (`hycolony.lang`, en-US et fr-FR) : `debug.refused.config`, `debug.refused.operator`, `debug.refused.creative` (textes de MC), et `debug.refused.value` (« entre {p0} et {p1} », le nôtre : chez MC, c'est Brigadier qui rejette la valeur avec son propre message).

## 5. HyLens

- **Onglet Colonies** : un bouton **« Nouveau citoyen »** sous « Contrôler maintenant », sur la colonie choisie ; le résultat sous la page de droite (`ActionReport.spawned` : « Fait. », le refus, « colonie inconnue » ou « hôtel de ville non chargé ou sans place »).
- **Onglet Citoyens** : une ligne **« Saturation : 42.0/60 »** et quatre boutons **0**, **−**, **+**, **Max**. `SaturationStep` (cœur de HyLens) donne l'opérateur de MC et la valeur suggérée : `SET 0`, `DECREASE 1`, `INCREASE 1`, `SET max` ; HyColony applique les règles de MC. Sans saturation lue, la ligne est cachée. Loisir, Téléporter et Refaire le corps passent en deux colonnes.
- **Textes** (`hylens.lang`) : « Nouveau citoyen », « Saturation : {p0}/{p1} », une clé par bouton, et les trois messages de `ActionReport.spawned` et `action.noColony`.

## 6. Tests

- Cœur de HyColony :
  - arrivée forcée : au-delà des arrivées coupées et du nombre initial ; sauvée, journalisée au point d'apparition, annoncée ; genre tiré même quand l'équilibre dirait l'inverse ; sans hôtel de ville, hôtel de ville déchargé ou sans place : rien de créé, avertissement une fois (sauf déchargé) ;
  - `spawnCitizen` : opérateur, plugin, gestionnaire refusé « opérateur » (même pour une colonie inconnue), colonie refusée, `Unavailable`, `NotFound` ;
  - `modifySaturation` : `SET` (0 compris) et sauvegarde ; `INCREASE` plafonné ; `DECREASE` fois le modificateur de nourriture, `justAte` remis à faux, plancher 0, rien sur une colonie inactive ; valeurs hors bornes, NaN et infinis refusés sans rien changer ; gestionnaire hors créatif refusé par la config (avant le créatif et le citoyen), accepté avec elle ; opérateur hors créatif refusé, même pour une colonie inconnue ; citoyen inconnu `NotFound` ;
  - la config : défaut `false`.
- Cœur de HyLens : `SaturationStep` (opérateur et valeur de chaque bouton), la saturation dans `MenuView`, `ActionReport.spawned`, `ApiCompatibilityTest` (HyColony 1.1 refusée).
- En jeu (`docs/TESTING.md` 335-338).

## 7. Écarts à MineColonies

Chacun porte un `Deviation from MC:` dans le code.

- Sans arrivée possible, `spawnCitizen` répond `Unavailable` au lieu du plantage de MC (`CoreDebugEdits.spawnCitizen`).
- Les actions répondent un `ActionResult` sans le texte de succès de MC (nom du nouveau citoyen, nouvelle saturation) : HyLens les relit (`CoreDebugEdits`). En ajouter une charge serait une rupture de l'API.
- L'événement d'API `CitizenSpawned` n'a pas la source `COMMANDS` de MC `CitizenAddedModEvent` (en ajouter une serait une rupture du record).
- `modifySaturation` marque la colonie à réécrire, que MC ne marque pas : HyColony ne réécrit que les colonies marquées.
