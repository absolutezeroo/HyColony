# HyLens V2, lot 2 : faire apparaître un citoyen et régler sa saturation

Suite de `2026-10-02-hylens-livre-mc-design.md` (§ 2, lot 2) et de `2026-09-30-hycolony-api-hylens-design.md` (API v1).

## 1. Objectif

L'utilisateur veut agir sur les citoyens depuis HyLens (2026-10-02). Il a choisi les actions de MineColonies seules, sans ajout (ni compétences, ni métier). Les commandes de MC sur les citoyens sont `spawnNew`, `kill`, `modify … saturation` et `reload` (`sources/minecolonies/.../commands/citizencommands/`) :
- `reload` existe déjà dans HyLens (« Refaire le corps », `DebugAccess.respawnBody`) ;
- `kill` appelle `EntityCitizen.die`, donc toute la mort d'un citoyen, qui n'est pas portée (les citoyens sont invulnérables, spec SP4b § 4). « Tuer » viendra avec le sous-projet « mort des citoyens » (`docs/research/citizen-death.md`), décision de l'utilisateur du 2026-10-02 ;
- ce lot porte donc **`spawnNew`** et **`modify … saturation`**.

## 2. MineColonies

**`CommandCitizenSpawnNew`** (`IMCOPCommand` : opérateurs seuls) appelle `CitizenManager.spawnOrCreateCivilian(null, world, [], force = true)` (`core/colony/managers/CitizenManager.java` l. 231-310) :
- `force` passe outre le réglage « nouveaux citoyens » de la mairie (`MOVE_IN`) et l'avertissement du nombre maximum de citoyens ;
- le citoyen apparaît au point d'apparition calculé autour de l'hôtel de ville, seulement s'il est chargé. Sans hôtel de ville chargé, rien n'est créé : la commande plante alors sur un `null` (bug de MC, non porté) ;
- l'événement `CitizenSpawnedEvent` va au journal de la colonie ; la commande répond `COMMAND_CITIZEN_SPAWN_SUCCESS` avec le nom.

**`CommandCitizenModify`** (`IMCColonyOfficerCommand` : opérateur, ou gestionnaire de la colonie) :
- un joueur non opérateur n'y a droit que si `canPlayerUseModifyCitizensCommand` (défaut `false`, `ServerConfiguration` l. 157) ; sinon `COMMAND_DISABLED_IN_CONFIG` ;
- `saturation = v`, `+ v`, `- v`, avec `v` entre 0 et `MAX_SATURATION` ; les suggestions sont 0 et le maximum pour `=`, 1 pour `+` et `-` ;
- `increaseSaturation` et `decreaseSaturation` bornent le résultat entre 0 et le maximum.

## 3. L'API (`DebugAccess`, version 1.2)

Deux méthodes, `@Experimental` comme le reste de `DebugAccess`. L'API passe en **1.2.0** (`ApiVersion.CURRENT`), et HyLens est construit contre elle (`ApiCompatibility.BUILT_AGAINST`). `api/api.txt` est régénéré (`./gradlew :api:apiDump`).

```java
/**
 * A new citizen arrives at the colony's town hall (MC {@code /mc citizens spawnNew}), even with "new citizens" off and
 * beyond the colony's room. Operators only, and plugins; {@link ActionResult.Unavailable} without a loaded town hall,
 * nothing created then.
 *
 * @since 1.2
 */
ActionResult spawnCitizen(Actor actor, ColonyRef colony);

/**
 * Sets the citizen {@code ref}'s saturation to {@code value}, kept between 0 and its maximum (MC
 * {@code /mc citizens modify saturation}). A colony manager who is not an operator needs the server's
 * Commands.CanPlayerUseModifyCitizensCommand.
 *
 * @since 1.2
 */
ActionResult setSaturation(Actor actor, CitizenRef ref, double value);
```

`=`, `+` et `-` de MC se font tous par `setSaturation` : HyLens lit la saturation actuelle (`ColonyWorld.wellbeing`) et envoie la nouvelle valeur. Une seule méthode suffit ; la borne est appliquée par HyColony.

**Droits** (`CoreDebugActions.refusal`, qui sert déjà aux 4 actions) :

| Action | Opérateur | Gestionnaire non opérateur | Plugin | Colonie |
|---|---|---|---|---|
| `spawnCitizen` | oui | non (`hycolony.permission.denied`) | oui | non |
| `setSaturation` | oui | si `CanPlayerUseModifyCitizensCommand`, sinon refus `hycolony.debug.refused.config` | oui | non |

## 4. Le cœur de HyColony (TDD)

- **`CitizenManager.spawnForced(BlockPos townHall)`** : le citoyen MC `spawnOrCreateCivilian(force = true)`. Il réutilise le chemin de `spawnInitialCitizen` (genre équilibré, nom, compétences, journal `citizenSpawned`, événement `CitizenSpawned`) sans regarder `moveIn` ni `initialCitizenAmount`. Il renvoie `false` sans rien créer si l'hôtel de ville n'est pas chargé (`worldQuery().isLoaded`). L'arrivée normale (`onColonyTick`) ne change pas.
- **`CoreDebugActions`** gagne `spawnCitizen` et `setSaturation`. S'il dépasse 150 lignes, ces deux actions vont dans un collaborateur (`CoreDebugColonyActions`). Le refus « opérateur seul » et le refus par la config s'ajoutent à `refusal`.
- **Config** : `ColonyConfig.Commands` gagne `canPlayerUseModifyCitizensCommand` (défaut `false`, MC) ; `plugin/config/CommandsSection` lit et écrit la clé `CanPlayerUseModifyCitizensCommand` de la section `Commands` de `config.json`. Une ancienne config sans la clé prend le défaut.
- **Texte** : `hycolony.debug.refused.config` (« Cette commande est désactivée dans la configuration du serveur. », MC `COMMAND_DISABLED_IN_CONFIG`), en-US et fr-FR.

## 5. HyLens

- **Onglet Colonies** : sous « Contrôler maintenant », un bouton **« Nouveau citoyen »**, sur la colonie choisie. Le résultat s'affiche sous la page de droite (« Fait. », ou le refus) ; le nouveau venu entre dans la liste au redessin suivant.
- **Onglet Citoyens** : une ligne **« Saturation : 42/60 »** et quatre petits boutons **0**, **−**, **+**, **Max** (les suggestions de MC : 0 et le maximum pour `=`, 1 pour `+` et `-`). La valeur vient de `ColonyWorld.wellbeing` ; sans elle (citoyen inconnu), la ligne et les boutons sont cachés.
- **Place** : Loisir, Téléporter et Refaire le corps passent en deux colonnes de petits boutons pour libérer la ligne.
- **Cœur de HyLens** (TDD) : `MenuView.CitizenRow` gagne la saturation et son maximum (`Optional`, vides sans `wellbeing`). Le calcul de la valeur envoyée (0, actuelle − 1, actuelle + 1, maximum) est dans le cœur de HyLens (`SaturationStep`), testé.
- **Textes** (`hylens.lang`, en-US et fr-FR) : « Nouveau citoyen », « Saturation : {p0}/{p1} », les quatre boutons (une clé chacun).

## 6. Tests

- Cœur de HyColony :
  - `spawnCitizen` d'un opérateur ajoute un citoyen même avec « nouveaux citoyens » coupé et au-delà de `initialCitizenAmount` ; un gestionnaire non opérateur est refusé ; sans hôtel de ville chargé, `Unavailable` et aucun citoyen ;
  - `setSaturation` : la valeur posée, bornée à 0 et 60 ; un gestionnaire refusé par défaut, accepté avec la config ; colonie ou citoyen inconnu, `NotFound` ;
  - la config : la clé lue, son défaut sans elle.
- Cœur de HyLens : `SaturationStep` (0, −1, +1, max, bornes), la saturation dans `MenuView`.
- En jeu (`docs/TESTING.md`) : « Nouveau citoyen » avec les arrivées coupées, puis sans hôtel de ville chargé ; les quatre boutons de saturation, le HUD qui suit ; un gestionnaire non opérateur refusé, puis accepté avec la clé.

## 7. Écarts à MineColonies

- `setSaturation` remplace les trois opérateurs `=`, `+`, `-` de la commande : HyLens calcule la valeur, HyColony la borne. Même effet.
- Sans hôtel de ville chargé, `Unavailable` au lieu du plantage de MC.
- La réponse de `spawnCitizen` ne porte pas le nom du nouveau citoyen (`ActionResult` n'a pas de charge, en ajouter une serait une rupture) : il apparaît dans la liste.
