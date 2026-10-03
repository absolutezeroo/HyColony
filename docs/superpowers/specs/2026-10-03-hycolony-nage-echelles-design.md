# HyColony : nager et grimper aux échelles comme MineColonies

Pistes choisies par l'utilisateur le 2026-10-03 : « nager comme MC » et « grimper comme MC ». Périmètre réduit le même jour, à sa demande : la recherche de chemin sera refaite (portage de l'A\* de MC, qui porte les échelles et les coûts d'eau). D'ici là, on fait **la nage entière** et **la mécanique d'escalade** ; la planification des échelles attend cette refonte (§ 5). Recherche : `docs/research/plugin-b-api.md` § 52 (ce que permet un PNJ), § 39 (montée de 3 blocs), § 40 (le chemin d'un PNJ) ; audit `docs/research/audit-monde-hytale.md` D-4 (échelles) et D-6 (eau).

## 1. Objectif

- Un citoyen qui entre dans une eau profonde **nage en surface**, avec l'animation de nage, et en ressort par la berge. Ses chemins peuvent traverser l'eau.
- Un corps de citoyen **sait monter et descendre** le long d'une colonne, avec l'animation d'escalade : c'est la mécanique que la future recherche de chemin utilisera pour les échelles. D'ici là, rien ne la déclenche hors du selftest : l'étage desservi par une seule échelle reste atteint par la téléportation de l'anti-blocage (audit D-4).

## 2. MineColonies

**Nage** (§ 52) :
- le navigateur des citoyens flotte et nage (`setCanFloat(true)`, `canSwim`, `MinecoloniesAdvancedPathNavigate.java:155-157`), deux fois plus vite dans l'eau (`CITIZEN_SWIM_BONUS` 2,0), sans être poussé par le courant ;
- l'A\* nage en surface : entrer dans l'eau coûte 24 (`swimCostEnter`), chaque nœud d'eau 4 (`swimCost`), 4 de plus la tête sous l'eau (`divingCost`) (`PathingOptions.java:43-73`, `AbstractPathJob.java:1037-1116`) ;
- `EntityAIFloat` : les yeux dans l'eau sans air au-dessus, le citoyen cherche à sortir (`PathJobEscapeWater`), navigation en pause 15 s ;
- pas de noyade dans le code de MC.

**Échelles** (§ 52, audit D-4) :
- `PathfindingUtils.isLadder` : les échelles (`LadderBlock`, tag `freeClimbBlocks`) sont toujours permises ; les autres blocs grimpables (lianes) seulement avec `canClimbAdvanced`, donné par la recherche `effects/vinesunlock` ;
- un nœud d'échelle explore vers le haut et le bas ; monter une échelle ne coûte pas de saut ; un grimpable qui n'est pas une échelle coûte 3 de plus ; on ne tombe jamais d'une échelle (`AbstractPathJob.java:684-688`, 1056, 1099-1101, 1611) ;
- mouvement : `handleLadders` centre le corps devant l'échelle, `doLadderMovement` le pousse vers le haut ou le fait descendre accroupi (`MinecoloniesAdvancedPathNavigate.java:741-875`).

## 3. Ce que Hytale permet (§ 52)

- Un rôle peut avoir plusieurs contrôleurs de mouvement ; rien ne bascule à l'entrée dans l'eau, mais `Role.setActiveMotionController` est public. `Dive` nage, ne se dirige que dans l'eau et n'en sort jamais seul ; `Walk` coule et marche au fond.
- Notre citoyen est `Invulnerable` : il ne se noie pas.
- Aucun code PNJ ne lit `BlockMovementSettings.isClimbable` : un PNJ ne grimpe pas. Un plugin peut déplacer le corps (téléportation exacte, vitesse) et poser l'état `climbing`, comme `CitizenMantleSystem` pose `mantling`.
- Le modèle du joueur, parent du nôtre, a les animations `Swim*` et `ClimbUp`/`ClimbDown`.
- L'A\* de Hytale coûte la seule distance, et n'explore que par la sonde du contrôleur actif : ni coût d'eau, ni passage par une échelle de plus de 3 blocs.

## 4. Nage

**Rôle** (`HyColony_Citizen.json`) :
- `MotionControllerList` : `Walk` (inchangé) et `Dive`, avec `InitialMotionController: "Walk"` (sans lui, le contrôleur de départ est tiré au hasard) ;
- `Dive` réglé pour nager **en surface**, la tête hors de l'eau : `MinDepthBelowSurface` 0, `MaxDiveDepth` faible, `SwimDepth` choisi pour garder les yeux dehors. Vitesses de nage : celles d'un joueur de Hytale (monde), réglées en jeu **[in-game]** ;
- `HyColonySeek` relâche `["Wade", "Breathe"]` : le chemin du `Walk` peut traverser l'eau (aujourd'hui, seul `Wade` est relâché, et le citoyen n'entre dans l'eau que tant que ses yeux restent dehors).

**Bascule** (plugin, nouveau `npc/CitizenSwimSystem`, après les états de mouvement, comme `CitizenMantleSystem`) :
- `Walk` → `Dive` quand le fluide atteint les yeux du corps (`Role.couldBreathe` faux aux yeux, ou la case des yeux est un fluide) ;
- `Dive` → `Walk` quand le corps a pied (un sol solide sous les pieds à moins d'un bloc), ou quand, à la surface, une case où se tenir l'attend juste devant lui, à `MaxClimbHeight` (3) au plus au-dessus de ses pieds (la berge) : `Walk` la monte comme une marche ;
- une bascule n'a pas lieu moins de 10 ticks après la précédente (pas de va-et-vient au bord) ;
- la cible de marche (`MoveTarget`) est gardée : `Seek` suit sa cible avec `Dive` comme avec `Walk`.
- Système sans état de jeu : il ne lit que le corps et le monde. Un corps rechargé reprend le contrôleur sauvé par Hytale (`RoleSystems.java:483-490`) ; s'il est dans l'eau, la bascule le corrige au tick suivant.

**Cœur** : rien de nouveau.
- `BlockApproach` garde la pénalité de MC pour une case de fin dans l'eau (`PathJobMoveCloseToXNearY`), et la promenade ne finit jamais au-dessus de l'eau (`WanderGround`, comme `PathJobRandomPos`).
- `EntityAIFloat` n'est pas porté : en surface, la tête est toujours hors de l'eau (écart ci-dessous).


## 5. Échelles : la mécanique seule

**Ce qui est fait maintenant** (plugin seulement) :
- `HytaleCitizenBodies.climb(BodyId body, Vec3 to)` et un nouveau `npc/CitizenClimbSystem` : le corps monte ou descend en ligne droite jusqu'à `to`, centré dans sa colonne, à la vitesse d'échelle d'un joueur de Hytale (monde), par petites téléportations exactes à chaque tick. L'état `climbing` est posé pendant ce temps, après les états de mouvement du PNJ comme `CitizenMantleSystem` (animations `ClimbUp`/`ClimbDown`) ;
- pendant la montée, `navStatus` vaut `MOVING`, puis `ARRIVED` ; un corps mort ou déchargé n'en fait rien, sans exception ;
- la montée ne peut pas durer toujours : au-delà de sa durée prévue plus une marge, le corps est posé à `to` ;
- `/hycolony selftest` ajoute une étape « climb » : le corps de test monte de 3 blocs sur place, puis redescend. C'est la seule utilisation d'ici la refonte, et elle sert à voir l'animation et le rendu en jeu.

**Ce qui attend la refonte de la recherche de chemin** (portage de l'A\* de MC) :
- le port `CitizenBodies.climb`, appelé par la recherche de chemin du cœur (pas avant : une méthode de port sans appelant dans le cœur serait du code mort) ;
- le fait de bloc « échelle » (`BlockCatalog`) : un bloc `MovementSettings.IsClimbable`, `Material: Solid` et `HitboxType: Ladder` (les `Furniture_*_Ladder`) est une échelle ; les autres blocs `IsClimbable` (lianes, cordes, chaînes, trappes) attendent `canClimbAdvanced` (recherche `vinesunlock`) ;
- le choix des échelles dans le chemin, avec les coûts de MC (pas de coût de saut, 3 de plus pour un grimpable qui n'est pas une échelle, jamais de chute depuis une échelle).

## 6. Écarts à MineColonies

Chacun porte un `Deviation from MC:` dans le code.

- (Hytale world) MC nage en surface avec la physique de Minecraft, deux fois plus vite (`CITIZEN_SWIM_BONUS`) → le contrôleur `Dive` de Hytale, aux vitesses de nage d'un joueur de Hytale.
- (contrainte Hytale) L'A\* de Hytale coûte la seule distance : ni le coût d'entrée dans l'eau (24), ni le coût de nage (4 par nœud) de MC ne pèsent sur le chemin. Seule la pénalité de case de fin dans l'eau reste (`BlockApproach`). À reprendre avec la refonte de la recherche de chemin.
- `EntityAIFloat` (sortir de l'eau quand la tête est dessous, pause de 15 s) n'est pas porté : `Dive` garde la tête hors de l'eau, et l'anti-blocage sort un citoyen coincé.
- (Hytale world) La montée suit la vitesse d'échelle d'un joueur de Hytale (`MovementConfig`, `ClimbSpeed`), pas la physique d'échelle de Minecraft.
- Les citoyens ne prennent pas encore les échelles (audit D-4) : la refonte de la recherche de chemin les fera passer par elles.

## 7. Tests

- Le cœur ne change pas : pas de nouveau test unitaire.
- Plugin (pas de tests unitaires, CLAUDE.md § 8) : l'étape « climb » de `/hycolony selftest`, et `docs/TESTING.md`.

## 8. À vérifier en jeu (`docs/TESTING.md`)

- Nage : un citoyen poussé dans un lac nage en surface, avec l'animation de nage, et sort par la berge ; un chemin qui traverse une rivière ; pas de va-et-vient au bord ; la descente dans un lac de plus de 3 blocs de profondeur.
- Escalade : l'étape « climb » du selftest monte puis redescend le corps de test, avec `ClimbUp` puis `ClimbDown`, sans saccade visible.
- Réglages à trouver en jeu : `SwimDepth` et les vitesses de `Dive`, la vitesse de montée.

## 9. Hors périmètre

- La refonte de la recherche de chemin (portage de l'A\* de MC), avec les échelles et les coûts d'eau : sa propre spec.
- Les portes (audit D-5), autre comportement de MC qui manque aux PNJ de Hytale : un changement à part.
- La recherche `vinesunlock` et `canUseRails` : avec le système de recherche.
