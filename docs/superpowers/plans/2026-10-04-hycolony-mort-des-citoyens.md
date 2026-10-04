# Mort des citoyens, vie et dégâts : plan d'implémentation

> **Pour les agents :** sous-skill requise : superpowers:executing-plans. Les étapes sont des cases à cocher (`- [ ]`). Chaque tâche : test rouge, code, test vert, mutation de contrôle, spotless fichier par fichier, `./gradlew build` vert, commit.

**But :** porter la mort des citoyens de MineColonies et le domaine 2 de l'audit (vie à l'échelle de Hytale, plafond par coup, filtre `HURT_CITIZEN`, mur, délai de soin, blocage, monstres hostiles, fuite).

**Architecture :** les règles vont dans le cœur (`core/`), testées avec `TestContexts`, `FakeBodies`, `FakeWorldBlocks`, `FakeNotifier`. Le plugin ne fait que transmettre (filtres de dégâts, système de mort, fournisseur d'attitude).

**Spec :** `docs/superpowers/specs/2026-10-04-hycolony-mort-des-citoyens-design.md`. **Recherche :** `docs/research/citizen-death.md` (§ 1.11 blessure, § 1.7 deuil, § 2 pre.5, § 3 découpage).

## Contraintes globales

- `Deviation from MC (Hytale world): <règle MC> → <équivalent Hytale, source>` pour l'échelle (100), le délai de soin (300 ticks) et la cause du blocage (`Crush`) ; `Deviation from MC (Hytale world): dropped experience → none` pour l'XP.
- Les textes passent par la skill `add-lang-key` (en-US et fr-FR, `hycolony.lang`).
- La sauvegarde monte de 10 à 11 (skill `add-migration`), avec la fixture v10.
- L'API monte à 1.4.0 (`ApiVersion.CURRENT`), `@since 1.4` sur `CitizenDied`, `./gradlew :api:apiDump :plugin:apiDump`.
- Le résumé du soir de MC (`computeNews`) n'est pas porté dans HyColony : l'entrée de journal suffit (corriger la spec § 3.8).

## Points d'attention pour la relecture

1. Un corps déchargé (chunk quitté) ne doit **jamais** passer par la mort : seule l'arrivée de `DeathComponent` la déclenche.
2. Un citoyen mort deux fois (deux événements) : la seconde arrivée ne fait rien (citoyen déjà retiré).
3. `WorkerModule.free` remet l'armure dans l'inventaire : la chute vient **après** la libération, pour tout lâcher.
4. Le corps mort ne doit pas être `despawn` (le cadavre disparaît seul après 1,5 s) : on le désuit seulement.
5. Un deuil au réveil d'un citoyen dont tous les co-résidents sont morts : l'état `MOURN` ne bloque pas le sommeil ni le repas, et se termine au réveil suivant.

---

### Tâche 1 : la vie à l'échelle de Hytale

**Fichiers :** `PR/Server/NPC/Roles/HyColony/HyColony_Citizen.json` (`MaxHealth` 100), `C/citizen/CitizenData.java` (`MC_MAX_HEALTH` → `MAX_HEALTH = 100`, Javadoc : l'échelle de Hytale), `C/citizen/food/HungerTicks.java` (`healAmount` × `maxHealth / 20`), `C/kernel/port/body/BodyHealth.java` (Javadoc), `CT/testing/FakeBodies.java` (`health = 100`, `maxHealth = 100`), tests `HungerTicksTest`, `CitizenInventoryViewTest`, `TownHallCitizensViewTest`.

- [ ] Test rouge `HungerTicksTest.healIsScaledToHytaleHealth` : un corps à 50/100, saturation pleine → soigné de 10 (2 × 100 / 20).
- [ ] `HungerTicks.updateHealing` : `health.heal(body, healAmount(sat) * health.maxHealth(body) / MC_HEALTH)` avec `MC_HEALTH = 20` (constante, Javadoc MC `CitizenConstants.BASE_MAX_HEALTH`) et l'écart (Hytale world).
- [ ] Vues : repli à 100/100 sans corps ; tests mis à jour (attendu : `Health.json` `Max: 100`).
- [ ] Rôle : `"MaxHealth": 100`.

### Tâche 2 : la blessure dans le cœur (filtre, plafond, mémoire)

**Fichiers :** créer `C/citizen/vitals/CitizenHurt.java` ; modifier `C/citizen/food/HungerTicks.java` (soin bloqué par le cœur), `C/kernel/port/body/BodyHealth.java` (retirer `recentlyHurt`), `P/npc/body/BodyVitals.java` (retirer `HURT_MEMORY_TICKS`, `hurt`, `recentlyHurt`), `P/npc/CitizenHurtSystem.java`, `CT/testing/FakeBodies.java`.

**Interfaces produites :**
- `public final class CitizenHurt` :
  - `static final int HURT_MEMORY_TICKS = 300;` (écart Hytale world, `Health.json` `NoDamageTaken` 15 s) ;
  - `static final double MAX_HIT_SHARE = 0.2;` (MC `EntityCitizen.handleDamagePerformed`) ;
  - `static final double HURT_CITIZEN_MAX_DAMAGE = 5;` (MC 1 point × 5) ;
  - `public static double allowed(double amount, double maxHealth, boolean playerWithoutHurtRight)` : le dégât après filtre et plafond (0 = annulé) ;
  - `public static void onHurtByAttacker(Colony c, CitizenData d, long tick)` : mémorise le coup (`CitizenData.lastHurtTick`, transitoire, non sauvé) ;
  - `public static boolean recentlyHurt(CitizenData d, long tick)`.
- [ ] Tests rouges `CitizenHurtTest` : `aHitTakesAtMostAFifthOfMaxHealth` (dégât 60 sur 100 → 20), `aPlayerWithoutHurtRightDealsAtMostFive`, `healWaitsThreeHundredTicksAfterAnAttackerHit` (via `HungerTicks`).
- [ ] Le plugin : `CitizenHurtSystem` passe dans le groupe filtre, avant `DamageSystems.ArmorDamageReduction` (`SystemDependency` `Order.BEFORE`, motif `BlockUseProtectionSystem`), applique `damage.setAmount(CitizenHurt.allowed(...))` et annule à 0 ; appelle `onHurtByAttacker` sur un `Damage.EntitySource`. Le droit `HURT_CITIZEN` : `Permissions.hasPermission(player, Action.HURT_CITIZEN)` si l'action existe, sinon l'ajouter (vérifier `Action`).

### Tâche 3 : fin de l'invulnérabilité, mur, blocage

**Fichiers :** rôle (`Invulnerable` retiré) ; créer `P/npc/CitizenVulnerabilitySystem.java` (retire le composant `Invulnerable` d'un corps chargé, `RefSystem` à l'ajout, motif `CitizenFireImmunitySystems.Grant`) ; créer `P/npc/CitizenWallSystem.java` (filtre : `Suffocation` annulée, `BodyTeleport.teleport(ref, position)`, MC `handleInWallDamage`) ; `C/kernel/port/body/BodyHealth.java` + `damage(BodyId, double amount)` ; `P/npc/body/HytaleBodyHealth.java` (dégât sous la cause `Crush`, résolue une fois par `DamageCause.getAssetMap().getIndex`) ; `C/citizen/vitals/CitizenWalkReports.java` (`stuck(..., TELEPORT)` → `health.damage(body, maxHealth × 0.2)`) ; `C/kernel/nav/StuckHandler.java` (Javadoc l. 25-31 : l'écart disparaît) ; `NpcSystems` (enregistrement).
- [ ] Test rouge `CitizenWalkReportsTest.aFullStuckCostsAFifthOfMaxHealth` (`FakeBodies.damaged`).
- [ ] Code ; Javadoc MC `PathingStuckHandler.completeStuckAction` et l'écart `STUCK_DAMAGE → Crush`.

### Tâche 4 : statistiques de colonie et sauvegarde v11

**Fichiers :** créer `C/colony/ColonyStatistics.java` (compteurs par jour : `increment(String id, int day)`, `count(String id, int day)`, `restore`), `Colony.statistics()` ; `ColonySerializer` (clé `statistics`) ; `MigrationV10ToV11` (ajoute `statistics: {}` et, par citoyen, `mourning: {"names": [], "active": false}`) ; `MigrationChain` ; fixture `colony-v10-*.json` ; `TownHallStats` + `TownHallView.Stats` (morts du jour) ; `TownHallStatsTab` (ligne) ; clé `ui.townhall.stats.deaths`.
- [ ] Tests rouges : `ColonyStatisticsTest`, `MigrationV10ToV11Test` (skill `add-migration`), sérialisation aller-retour.

### Tâche 5 : le deuil

**Fichiers :** créer `C/citizen/mourn/CitizenMourning.java` (record des noms + drapeau, MC `CitizenMournHandler`) dans `CitizenData` et `CitizenSerializer` ; `C/citizen/CitizenState.java` + `MOURN` ; `C/citizen/CitizenAI.java` (après le repas, avant la pluie : `MOURN` si en deuil) ; créer `C/citizen/mourn/MournAI.java` (MC `EntityAIMournCitizen` : toutes les 20 ticks, une chance sur deux de regarder un citoyen à 3 blocs, fin du regard une fois sur 200, sinon marcher vers la mairie, à défaut la maison) ; `SleepHandler.onWakeUp` (MC `CitizenManager.onWakeUp` : le deuil commence ou finit au réveil) ; clé `citizen.mourning`.
- [ ] Tests rouges `CitizenMourningTest` : `aCoResidentMournsTheDayAfterADeath`, `mourningEndsAtTheNextWakeUp`, `aMourningCitizenDoesNotWork`.

### Tâche 6 : la mort dans le cœur

**Fichiers :** créer `C/citizen/death/CitizenDeath.java`, `C/citizen/death/CitizenDied.java` (événement interne), `C/citizen/death/DeathMessage.java` (destinataires : propriétaire, membres `RECEIVE_MESSAGES`, gestionnaires ; direction : `FieldsTab.direction` déplacé dans `C/kernel/Directions.java`, public) ; `CitizenManager.onCitizenDied(int citizenId, Vec3 at, String causeId)` et `remove(int citizenId)` (citoyen, corps, IA) ; `ColonyManager.onCitizenDied(int colonyId, int citizenId, Vec3 at, String causeId)` ; `TownHallViews.MC_EVENTS` + `citizenDied` ; clés `citizen.died`, `citizen.deathCause.*`, `ui.townhall.event.citizenDied`.

**Ordre** (MC `EntityCitizen.die`, recherche § 1.1) : bonheur `DEATH` (3.0, 3 jours) sur tous les autres → deuil des co-résidents → statistique `death` du jour → libération (`WorkerModule.fire`, `CourierAssignmentModule.detach`, `LivingModule.remove`, `cancelAllFrom`) → chute de l'inventaire et de l'armure (`ports().blocks().drop`) → message → journal `citizenDied` (nom, cause) → retrait → `CitizenDied` posté → `markDirty`.
- [ ] Tests rouges `CitizenDeathTest` : `aDeadCitizenIsRemovedForGood`, `itsInventoryAndArmourFallAtItsPosition`, `itsJobHomeAndRequestsAreFreed`, `theColonyIsToldAndLogsIt`, `everyOtherCitizenIsSaddened`, `aSecondDeathEventDoesNothing`, `aNewCitizenStillArrivesAfterwards`.

### Tâche 7 : la mort côté plugin et l'API

**Fichiers :** créer `P/npc/CitizenDeathSystem.java` (`DeathSystems.OnDeathSystem`, requête `citizenTag()`, lit le tag, la position et la cause ; désuit le ref sans `despawn` ; `world.execute` → `manager.onCitizenDied`) ; `NpcSystems` ; `P/ui/citizen/CitizenInventoryWindows.java` (ferme les pages du mort sur `CitizenDied`) ; `api/.../event/CitizenDied.java` (`record CitizenDied(CitizenRef citizen)`, `@since 1.4`) ; `ApiVersion` 1.4.0 ; `ApiEvents.routeCitizensAndDays` ; `api/README.md` ; `apiDump`.
- [ ] Test rouge `CoreColonyWorldEventsTest.aDeathReachesTheApi`.

### Tâche 8 : monstres hostiles et fuite

**Fichiers :** `ColonyConfig.Gameplay` + `mobAttackCitizens` (5e composante, défaut `true`, MC `mobattackcitizens`), appelants listés (tests, `GameplaySection`) ; `P/config/GameplaySection.java` (`MobAttackCitizens`) ; créer `P/npc/CitizenAttitudeSystem.java` (`StoreSystem` après `BlackboardSystems.InitSystem`, `AttitudeView.registerProvider` priorité 150 : rôle du groupe `HyColony_Hostile` face à un corps de citoyen → `HOSTILE`, si la clé est vraie ; le test du groupe sort de `HostileSpawns` dans une petite classe partagée) ; créer `C/citizen/vitals/CitizenFlee.java` (MC `performMoveAway` : sans attaquant, 5 blocs ; attaquant et citoyen non garde, 15 blocs à l'opposé, sol par `WanderGround`) appelé par `onHurtByAttacker` ; `docs/research/config-inventory.md`.
- [ ] Tests rouges `CitizenFleeTest.aHitCitizenRunsFifteenBlocksAwayFromItsAttacker`, `ColonyConfigTest` (défaut `true`).

### Tâche 9 : documentation et vérifications en jeu

- [ ] `docs/TESTING.md` : nouveaux points (coups et plafond, soin 15 s, mur, mort : objets, message, journal, statistique, deuil, emploi libéré, nouvel arrivant ; monstres qui attaquent ; clé `MobAttackCitizens` ; corps sauvés vulnérables).
- [ ] Spec § 3.8 : le résumé du soir n'est pas porté.
- [ ] `plugin-b-api.md` : `OnDeathSystem`, `AttitudeView.registerProvider`, `Invulnerable` sauvé avec l'entité.

### Fin

- [ ] Relecture `hycolony-reviewer` et `mc-fidelity-checker` sur toute la branche ; corrections relues.
- [ ] Feu vert à l'utilisateur pour les tests en jeu.
