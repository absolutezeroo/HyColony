# Mort des citoyens, vie et dégâts (domaine 2 du monde Hytale)

Date : 2026-10-04. Demandé par l'utilisateur : « l'étape 2 », puis « avec la mort », puis « c'est parti alors » (conception validée dans la conversation).

Recherche : `docs/research/citizen-death.md`, revérifiée sur 0.7.0-pre.5 et sur `sources/minecolonies` (`version/main`), et l'audit `docs/research/audit-monde-hytale.md`, entrées C-14, C-17 (« Hors du monde »), C-18, C-19, C-20, C-21, C-23. Règle : CLAUDE.md § 6, le système suit MC et le monde suit Hytale.

## 1. Objectif

Aujourd'hui, les citoyens sont invulnérables (rôle `HyColony_Citizen.json`, `Invulnerable: true`), et leur mort n'est pas portée. Les règles de vie et de dégâts du domaine 2 de l'audit sont donc latentes. Ce sous-projet porte la mort de MineColonies (`EntityCitizen.die`) et, avec elle, la vie, les dégâts et l'hostilité des monstres, sur l'échelle du monde de Hytale.

Succès :
1. un citoyen frappé perd de la vie, fuit, se soigne 15 s plus tard ;
2. un citoyen tué lâche ce qu'il porte, disparaît pour de bon, libère son emploi et son logement, et la colonie le sait (message, journal, statistique, bonheur, deuil) ;
3. un citoyen coincé dans un mur n'étouffe pas : il en est sorti, comme chez MC ;
4. les monstres attaquent les citoyens, sauf si la configuration le désactive ;
5. rien ne change dans les sauvegardes existantes sans migration.

## 2. Vie et dégâts (monde : Hytale)

- **Échelle** (C-18), `Deviation from MC (Hytale world): MC's 20 health points → Hytale's 100 (Server/Entity/Stats/Health.json)` :
  - le rôle passe à `MaxHealth: 100` ;
  - les soins de MC (`HungerTicks.healAmount` : 2, 1 ou `saturation / 20 / 2` points toutes les 100 ticks) et le seuil du filtre `HURT_CITIZEN` (1 point chez MC) sont multipliés par `maxHealth / 20`, soit 5 ;
  - les libellés « vie / max » des fenêtres (panneau d'inventaire du citoyen, onglet Citoyens de la mairie) affichent l'échelle de Hytale, et un citoyen sans corps compte 100/100 ; les cœurs ne dépendent pas de l'échelle.
- **Plafond par coup** (système, MC `EntityCitizen.handleDamagePerformed`) : un coup ne retire jamais plus de 20 % de la vie maximale, avant l'armure, comme MC.
- **Filtre `HURT_CITIZEN`** (système, MC `EntityCitizen.hurt`) : porté tel quel, à l'échelle ci-dessus.
- **Fin de l'invulnérabilité** : retirée du rôle ; le plugin enlève aussi le composant des corps déjà sauvés, que `RoleBuilderSystem` ne retire jamais (recherche § 2.4).
- **Mur** (C-19, système MC `EntityCitizen.handleInWallDamage`) : un dégât `Suffocation` est annulé et le citoyen est réveillé puis téléporté hors du bloc (MC `TeleportHelper.teleportCitizen`). `Deviation from MC (Hytale world): first IN_WALL hit at once → first Suffocation hit, once the Oxygen stat ran out (about 17 s, Oxygen.json)`. La noyade, les chutes et le vide restent ceux de Hytale.
- **Soin après un coup** (C-20), `Deviation from MC (Hytale world): Minecraft's 100-tick attacker memory → Health.json's NoDamageTaken delay (15 s)` : 300 ticks du cœur. La règle « seul un attaquant bloque le soin » de MC est gardée. La mémoire passe du plugin (`BodyVitals.HURT_MEMORY_TICKS`) au cœur.
- **Blocage complet** (C-23, système MC `PathingStuckHandler.completeStuckAction`) : 20 % de la vie maximale à chaque action complète, téléportation ou abandon loin du but (près du but, MC remet ses délais à zéro), `Deviation from MC (Hytale world): STUCK_DAMAGE → Crush` (cause la plus proche, réduite par l'armure comme chez MC).
- **Coup sur un dormeur** : il se réveille (Minecraft `LivingEntity.hurt`, `stopSleeping`).
- **Feu** (C-21) : l'immunité, ajout demandé, reste.

## 3. La mort (système : MC `EntityCitizen.die`)

Détection : un `DeathSystems.OnDeathSystem` du plugin, sur l'ajout de `DeathComponent` à un corps de citoyen, transmet au cœur l'id du citoyen (lu sur le `CitizenTag`), la position et la cause. Un déchargement n'y passe pas.

Le cœur (`CitizenDeath`), dans l'ordre de MC :
1. si le mort n'est pas garde (toujours vrai sans gardes), le modificateur de bonheur `DEATH` (3.0, 3 jours) sur tous les citoyens ;
2. le deuil des co-résidents (`updateCitizenMourn`) : état `MOURN` le jour suivant, sans travail (MC `EntityAIMournCitizen`) ;
3. la statistique `DEATH` du jour ;
4. l'inventaire et l'armure tombent au sol à la position du mort. `Deviation from MC (Hytale world): dropped experience → none, Hytale has no experience`. Pas de tombe (MC la rend optionnelle ; hors périmètre) ;
5. le message aux membres qui reçoivent les messages et aux gestionnaires : nom, cause, direction depuis le centre de la colonie (en-US et fr-FR). `Deviation from MC (Hytale world): Minecraft's death message → our text per Hytale damage cause or killer` (les textes de mort de Hytale s'adressent au joueur) ; le journal de la mairie fait de même ;
6. emploi, coursier et logement libérés (MC `removeCitizen` sur chaque module de chaque hutte) ; ses requêtes annulées dans chaque hutte de travail, qu'il y travaille ou non (MC `onRemoval`) ;
7. citoyen, corps et IA retirés ;
8. l'entrée « mort » du journal de la colonie (le résumé du soir de MC, `computeNews`, n'est pas porté dans HyColony) ;
9. l'événement interne, puis l'événement d'API `CitizenDied` (nouveau type public de l'API, version mineure).

Les nouveaux citoyens arrivent ensuite par le mécanisme existant.

## 4. Les monstres (C-14)

- Un fournisseur d'attitude de Hytale (`AttitudeView.registerProvider`, priorité 150) rend `HOSTILE` un rôle du groupe `HyColony_Hostile` face à un corps de citoyen.
- Clé `MobAttackCitizens` (défaut `true`, comme MC `mobattackcitizens`) dans la section `Combat` de `config.json`, comme la section `combat` de MC.
- Fuite (système, MC `EntityCitizen.performMoveAway` et `EntityAICitizenAvoidEntity`) : un citoyen non garde, frappé, fuit l'attaquant. Le cœur décide ; le plugin fournit « monstre hostile proche » (boîte du corps gonflée de 5 blocs, 3 en hauteur). Les courses passent par un marcheur et son anti-blocage, donc finissent toujours ; un repas, le coucher ou le deuil interrompent la fuite comme chez MC (`decideAiTask`). Écarts : pas d'appel à l'aide (pas de gardes), pas de son de fuite (pas encore de voix de citoyen), pas de ligne de vue, vitesse inchangée (MC 1,1 près d'un monstre, 0,8 plus loin : notre facteur de vitesse est celui du métier) ; `Deviation from MC (Hytale world): "whole" within 4 of MC's 20 points → the same share of 100`.
- Deuil : la marche vers la mairie passe par `walkToBuilding` (anti-blocage) ; regard à la hauteur des yeux du modèle Hytale (1,6, `Player.json`) ; pas de bulle « I'm still processing… » (interactions non portées).

## 5. Persistance et API

- Le deuil se sauvegarde dans le citoyen : nouveau champ, montée de `schemaVersion` par `MigrationChain` avec la fixture de l'ancienne version (skill `add-migration`).
- La statistique `DEATH` et l'entrée de journal suivent le format des statistiques et du journal existants.
- La vie n'est pas sauvée par HyColony (Hytale la garde avec le corps) : pas de migration.
- API : `CitizenDied` (`dev.hycolony.api`), `@since` de la nouvelle version, `./gradlew :api:apiDump :plugin:apiDump`.

## 6. Tests

- Cœur (TDD, `Fake*`) : mort d'un corps → citoyen retiré, objets et armure lâchés, emploi, logement et requêtes libérés, message, journal, statistique, `DEATH` sur les autres, deuil des co-résidents au réveil, pas de travail ce jour-là ; plafond de 20 % ; filtre `HURT_CITIZEN` ; soin bloqué 300 ticks après un coup d'attaquant ; dégâts de blocage ; fuite ; migration de l'ancienne sauvegarde.
- Plugin : vérifié en jeu (`docs/TESTING.md`, nouveaux points) : un monstre attaque et tue un citoyen, les objets tombent, le message arrive, le mur, l'invulnérabilité retirée des corps sauvés, la clé de config.

## 7. Incertain [in-game]

Repris de la recherche § 4 : retrait d'`Invulnerable` sur les corps sauvés ; attaque réelle d'un PNJ par les monstres (détection avec un joueur à moins de 50 blocs) ; ordre du fournisseur d'attitude après un rechargement ; corps sauvé à 20/20 qui se recharge à 20/100 ; animation de mort du modèle des citoyens ; objets lâchés sous le monde.

## 8. Hors périmètre

Les gardes (C-16), les raids (C-15), les tombes de MC, la permission `MAP_DEATHS` (carte), les succès.
