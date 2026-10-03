# Plan : nager, et la mécanique d'escalade

Spec : `docs/superpowers/specs/2026-10-03-hycolony-nage-echelles-design.md`. Tout se passe dans le plugin (`plugin/`) : le cœur ne change pas. Chaque API Hytale utilisée est revérifiée dans `build/vineflower/hytale-server` avant d'écrire le code (§ 52 de `plugin-b-api.md` en donne les fichiers et les lignes).

## Tâche 1 : le rôle nage

- `HyColony_Citizen.json` :
  - `MotionControllerList` : le `Walk` actuel, plus un `Dive` ;
  - `InitialMotionController: "Walk"` ;
  - réglages de `Dive` pour nager en surface : `MinDepthBelowSurface` 0, `MaxDiveDepth` faible, `SwimDepth` qui garde les yeux dehors, vitesses de nage d'un joueur de Hytale (`Server/Entity/MovementConfig/Default.json`) ;
  - `HyColonySeek` : `RelaxedConstraints: ["Wade", "Breathe"]`.
- Vérifier : les noms des clés dans `BuilderMotionControllerDive` et `BuilderRole` (`InitialMotionController`) ; le format de `RelaxedConstraints` dans `BuilderBodyMotionFindBase`.
- Mettre à jour le `$Comment` du rôle (écarts : vitesses de nage de Hytale).

## Tâche 2 : la bascule marche / nage

- Nouveau `plugin/.../npc/CitizenSwimSystem` (système qui tourne après `MovementStatesSystem`, requête : citoyens avec `NPCEntity`, `TransformComponent`) :
  - `Walk` → `Dive` quand la case des yeux est un fluide (lecture du fluide comme `MotionControllerWalk.java:733-757`) ;
  - `Dive` → `Walk` quand le corps a pied (sol solide à moins d'un bloc sous les pieds) ou qu'une case où se tenir l'attend devant lui, à 3 blocs au plus au-dessus des pieds ;
  - 10 ticks au moins entre deux bascules (compteur par corps, dans un composant ou une table du système, sans allocation par tick) ;
  - bascule par `Role.setActiveMotionController(ref, npc, name, accessor)` ;
  - attrape `RuntimeException`, journalise la première en WARNING, puis en FINE (CLAUDE.md § 4).
- Enregistrer le système avec `CitizenMantleSystem`.

## Tâche 3 : la mécanique d'escalade

- Nouveau composant `ClimbTarget` (cible, sens, ticks restants) et `npc/CitizenClimbSystem` :
  - chaque tick, monte ou descend le corps vers la cible d'un pas (vitesse d'échelle d'un joueur de Hytale, constante en blocs par seconde à régler en jeu), centré dans la colonne, par `Teleport.createExact` ;
  - pose `movementStates.climbing` après `MovementStatesSystem` ;
  - au but ou à l'échéance (durée prévue plus une marge), pose le corps à la cible et retire le composant.
- `HytaleCitizenBodies.climb(BodyId, Vec3)` : ajoute le composant (méthode du plugin, pas du port, spec § 5) ; `navStatus` rend `MOVING` tant que le composant est là.
- Vérifier : l'état `climbing` de `MovementStates`, `Teleport.createExact`, l'ordre des systèmes.

## Tâche 4 : le selftest

- `BodySelfTest` : après « move », une étape « climb » qui monte le corps de 3 blocs sur place, puis le redescend, et rapporte `ARRIVED` deux fois (ou le statut obtenu).

## Tâche 5 : documentation

- `docs/TESTING.md` : points de la spec § 8.
- `docs/research/audit-monde-hytale.md` : D-6 réglé (nage) ; D-4 : la mécanique existe, les échelles attendent la refonte de la recherche de chemin.
- `docs/research/plugin-b-api.md` § 52 : ce qui a été vérifié en écrivant le code.

## Fin

- `./gradlew build` vert ; relecture `hycolony-reviewer` (et `mc-fidelity-checker` pour les écarts) ; commits par tâche ; l'utilisateur teste en jeu (le serveur n'est jamais lancé par nous).
