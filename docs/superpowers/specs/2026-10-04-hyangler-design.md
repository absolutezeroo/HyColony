# HyAngler : sixième mod, une vraie pêche pour Hytale et son API (P1)

Décisions de l'utilisateur, le 2026-10-04 (conversation de conception) :

- un **mod de pêche complet**, jouable sans HyColony, avec une vraie API que le pêcheur de MineColonies utilisera ensuite. Référence de l'ambition : Tide 2 (« j'aime beaucoup l'exemple de Tide 2 ») ;
- un **mod de plus dans ce dépôt** (approche A : « un mod en plus, c'est obligatoire »), nommé **HyAngler** (`dev.hyangler`) ;
- un **vrai système de biomes et d'heures** : un poisson ne se pêche que dans certains environnements et à certaines heures ;
- les données sont **extensibles comme la nourriture de HyColony** : des types d'assets ouverts aux autres mods (`2026-10-04-hycolony-nourriture-ouverte-design.md` § 5) ;
- heures et météo de chaque espèce : des valeurs réalistes proposées dans cette spec (§ 6.4), à valider à la relecture ;
- **un mod vraiment complet** : moulinet, ligne, hameçon, flotteur, appâts… (§ 13). En P1, les cannes sont livrées **déjà montées** ; l'établi et les pièces viennent en P3, mais l'API porte l'équipement (`Tackle`) dès P1 ;
- la pêche du joueur de P1 (§ 7), l'API (§ 8) et la robustesse (§ 10) ont été validées section par section.

Recherches (à lire avant le plan) :

- `docs/research/fishing-hytale.md` : ce que Hytale 0.7.0-pre.5 a et ce qu'un plugin peut faire ;
- `docs/research/fishing-benchmark.md` : Minecraft vanilla, Tide 2, Aquaculture 2, les autres jeux et les patrons d'API ;
- `docs/research/mc-fisherman.md` : le pêcheur de MineColonies, que P6 portera sur cette API.

Essai de modèles du 2026-10-04 (jetable, hors dépôt) : une perche et un brochet dessinés sur le rig des poissons de Hytale (os du Bluegill, `Common/NPC/Swimming_Wildlife/Bluegill/`), peints par blockpaint, avec un pinceau de nageoires en éventail copié sur ceux de Hytale. L'utilisateur les a vus et corrigés dans Blockbench (dents du brochet, nageoires du dessous, nageoires sombres à la base). Ils servent de base à la perche de P1 (§ 6.5) et au Bestiaire (§ 13).

## 1. Objectif

- **Une pêche qui suit le monde de Hytale** : ses 30 poissons, leurs environnements, ses paliers d'outils, ses bancs et ses raretés. Hytale pre.5 n'a pas de canne (aucun code de pêche dans le serveur, un modèle de canne inutilisé côté client, `fishing-hytale.md` § 2), seulement la nasse.
- **Un moteur pur et ouvert** : le tirage d'une prise ne dépend que d'un contexte immuable (Tide `FishingContext`, `fishing-benchmark.md` § 4.1). La canne d'un joueur et un PNJ s'en servent de la même façon.
- **Des données extensibles** : poissons, prises et cannes sont des fichiers d'assets qu'un autre mod ajoute ou remplace ; un mod peut aussi ajouter un type de condition en Java.
- **Critères de réussite :**
  - un joueur fabrique une canne, la lance, attend, ferre et attrape un poisson du bon environnement à la bonne heure, avec son état de rareté ;
  - `/hyangler test` montre, là où le joueur regarde, les prises possibles et leurs chances ; elles changent avec l'environnement, l'heure, la météo et la canne ;
  - un pack de test ajoute un poisson et un type de condition sans toucher à HyAngler ;
  - HyAngler ne voit aucun autre mod, et le cœur n'importe rien de Hytale **[build : `checkModApis`, ArchitectureTest]** ;
  - l'API publique est figée par `apiCheck` **[build]**.

## 2. Dépendances

```
HyBlockUI ← HyDomum ← HyColony → HyVanilla
    ↑                    ↑
    └────── HyLens ──────┘

HyAngler (aucune dépendance de mod)
```

- **HyAngler ne dépend d'aucun mod**, comme HyVanilla. HyColony ne le voit pas en P1 ; le lien (dépendance dure ou facultative du pêcheur) se décide dans la spec de P6.
- Ses projets déclarent `group = "dev.hyangler"`. `checkModApis` reçoit `"dev.hyangler." to listOf("dev.hyangler.api.", "dev.hyangler.plugin.api.")` (`build-logic/src/main/kotlin/hy.java-checks.gradle.kts:124-126`).
- Le plugin déclare `Hytale:Beam` et `Hytale:Weather` dans ses dépendances de manifeste, car il appelle ces plugins intégrés en Java (`jar:manifests.json:332-342, 375-385`, `fishing-hytale.md` § 5.3, § 5.6).

## 3. Modules

| Module | Contenu |
|---|---|
| `angler/api` (`:angler-api`, `dev.hyangler.api`) | L'API en Java pur : contexte, prises, équipement, conditions, événements. `hy.java-core`, sans dépendance de projet ; ArchUnit en liste blanche (`java..`, `org.jspecify..`, `dev.hyangler.api..`), comme `:api` de HyColony. Empreinte `angler/api/api.txt`. |
| `angler/core` (`:angler-core`, `dev.hyangler.core`) | Le moteur, testé en TDD : lecture tolérante des fichiers, conditions, tirage, rareté, temps de morsure, eau libre, machine d'état d'un lancer. Implémente l'API. `api(project(":angler-api"))`. Aucun import `com.hypixel` **[ArchitectureTest]**. |
| `angler/plugin` (`:angler-plugin`, `dev.hyangler.plugin`) | Les adaptateurs Hytale (`Hytale*`), les types d'assets, la canne, le bouchon, la ligne, les commandes, le selftest et le pack d'assets (`hyangler.lang`, modèles, textures, sons, `hyangler/id-map.json`). Le contrat `dev.hyangler.plugin.api` (holder, interfaces, records) ; l'implémentation dans `dev.hyangler.plugin.bridge`. Empreinte `angler/plugin/api.txt`. Aucune règle de jeu. |

- Paquets du cœur par fonctionnalité : `catalog` (fichiers lus, poissons, prises, cannes), `condition` (types et combinaisons), `roll` (tirage, rareté), `cast` (machine d'état d'un lancer, temps de morsure), `water` (eau libre, profondeur), `kernel/port`. 15 fichiers au plus par paquet **[build]**.
- Ports du cœur : `WaterProbe` (fluide et bloc d'une case, ciel visible), `WorldClock` (heure du jour, phase de lune), `WeatherProbe` (météo et pluie à une case), `EnvironmentProbe` (environnement et zone d'une case), `CatalogSource` (fichiers lus). Simulés par des `Fake*` dans les tests.

## 4. Hytale (pre.5, vérifié dans la recherche)

- **Poissons** : 30 `Fish_*_Item` (`zip:Server/Item/Items/Fish/`). Ce sont des objets vivants qui relâchent leur PNJ, avec une qualité de base et des états de rareté `*<Objet>_State_<Rareté>` (Uncommon à Legendary). 14 poissons « monstres », de qualité Legendary, n'ont pas d'état (`fishing-hytale.md` § 1.1). Le plan de cuisine les découpe en `Food_Fish_Raw`, de ×1 à ×16 selon la rareté (`sp4b-hytale-food.md` l. 171).
- **Où ils vivent** : les fichiers `zip:Server/NPC/Spawn/World/Zone*/Spawns_*_Fish_*.json` lient chaque espèce à des environnements `Env_ZoneN_*`. Les rivières et les côtes y ont toutes `DayTimeRange [6, 24]` ; les océans n'ont aucune tranche (`fishing-hytale.md` § 1.4, tableau).
- **Pondérations de référence** : les listes de la nasse (`zip:Server/Drops/Traps/Drops_Fishing_Trap_Crude*.json`). Sans appât : poissons, sel, déchets, trésors. Avec appât : communs 100, peu communs 50, rares 10, épiques 5, légendaires 1, monstres 0,1 (`fishing-hytale.md` § 1.3).
- **Outils** : paliers Crude, Copper, Iron, Thorium, Cobalt, puis Adamantite, Mithril et Onyxium. Durabilité des haches : 200, 300, 500, 700, 700, 1000, 400, 450. Recettes : Crude au `Fieldcraft` (catégorie `Tools`), les autres à l'établi (`Workbench_Tools`) (`zip:Server/Item/Items/Tool/Hatchet/Tool_Hatchet_*.json`).
- **Briques** :
  - lancer chargé `Charging`, avec une table « durée tenue → interaction » (`Ability_ChargedShot_Cast.json`) ;
  - projectile `ProjectileModule.spawnProjectile` ; la physique `Standard` calcule la poussée d'Archimède (densité 700 < eau 1000), mais aucune interaction ne signale l'entrée dans l'eau : il faut lire `isInFluid()` (`StandardPhysicsProvider.java:648`) ;
  - corde `BeamComponent` et `AttachedBeam.toEntity(…, "R-Attachment")`, comme le grappin ;
  - objet qui vole vers le joueur : `ItemUtils.interactivelyPickupItem` ;
  - lune : `WorldTimeResource.getMoonPhase()` ;
  - environnement d'une case : `EnvironmentSection` ; sa zone est son tag `ZoneN`, hérité de `Env_ZoneN` ;
  - durabilité : `ItemUtils.updateItemStackDurability` (`decreaseItemStackDurability` ne vaut que pour les armes et armures) ;
  - le monde tourne à 30 ticks/s (`TickingThread.java:17`), pas 20.
  
  Références : `fishing-hytale.md` § 3.1 à 3.9 et § 5 (API vérifiées pour le plan).
- **Types d'assets d'un plugin** : `getAssetRegistry()`, `HytaleAssetStore.builder`. Les fichiers sont lus dans chaque pack, et un pack chargé après un autre remplace un fichier de même nom (`2026-10-04-hycolony-nourriture-ouverte-design.md` § 4).
- **Pas d'enchantement, pas d'XP de joueur** : le palier de la canne tient le rôle des enchantements Appât et Chance de la mer (`fishing-hytale.md` § 3.9).

## 5. Minecraft vanilla, la référence du système

Source : `FishingHook` de vanilla lui-même (serveur officiel 26.3 décompilé, non obfusqué ; `tick`, `catchingFish`, `calculateOpenWater`, `getOpenWaterTypeForBlock`, `retrieve`), `data/minecraft/loot_table/gameplay/fishing.json` et `enchantment/lure.json`, et `fishing-benchmark.md` § 2 (wiki Minecraft). La copie de MineColonies (`NewBobberEntity`, `mc-fisherman.md`) n'est **pas** fidèle à vanilla (attente 1 060-1 300 avec un plancher de 5, pas d'eau libre) : elle ne sert qu'au pêcheur de P6.

- **Attente** : `U[100, 600] − 100 × appât` ticks, tirée au tick qui suit l'arrivée dans l'eau (et la fin d'une touche manquée). Vanilla retire au tick suivant une attente de 0 ou moins (40 % des tirages avec l'appât 3) ; HyAngler tire directement dans la partie positive, ce qui garde la même loi (écart, § 14). Un appât au-delà de 5 compte pour 5 : dès 6, aucune attente de vanilla n'est positive.
- **Le compte** baisse de 1 par tick, de 2 avec une chance de 25 % s'il pleut sur le bouchon, et ne baisse pas avec une chance de 50 % si le bouchon ne voit pas le ciel. Il ne tourne que si le bouchon est dans l'eau, comme chez vanilla (`catchingFish` n'est appelé qu'avec de l'eau sous le bouchon) : l'attente, l'approche et la fenêtre s'arrêtent si l'eau disparaît.
- **Approche** : `U[20, 80]` ticks, pendant lesquels le poisson nage vers le bouchon (bulles et sillage).
- **Fenêtre de ferrage** : `U[20, 40]` ticks.
- **Catégories** : poisson 85 (qualité −1), déchet 10 (−2), trésor 5 (+2). Poids effectif `max(⌊poids + qualité × chance⌋, 0)`. Les trésors exigent une **eau libre** : un cube 5 × 4 × 5 autour du bouchon fait seulement d'eau dessous et d'air dessus. L'eau ne compte que dans une case qu'aucun bloc solide n'occupe ; l'air, c'est aussi un nénuphar (`Blocks.LILY_PAD` chez vanilla, les `Plant_Flower_Water_*` de Hytale, liste `waterSurface` de l'id-map, car la plupart sont `Solid`). L'eau libre se teste à chaque tick de l'approche et de la touche, et ne tient que si elle a tenu à chacun : elle se perd quand le compteur `outOfWaterTime` (+1 par tick hors de l'eau, −1 par tick dans l'eau, de 0 à 10) atteint 10. Elle redevient vraie dès que l'attente reprend.
- **Usure** : 1 par prise, 2 si le bouchon est au sol quand on le ramène (même pendant une touche : la prise est donnée), 0 si rien n'a mordu. Un bouchon au sol 1 200 ticks de suite disparaît (`life`), sans usure ; le compte repart de zéro dès qu'il quitte le sol, et un bouchon tombé au sol qui roule dans l'eau pêche.

Ce sont des règles de système (des délais et des poids), pas des mesures du monde Minecraft : elles restent en ticks à 20 par seconde, comme dans tous nos cœurs.

## 6. Les données : trois types d'assets ouverts

Chaque type est un `AssetStore` de HyAngler, lu dans tous les packs, sous `Server/HyAngler/<Type>/`. Règles communes, reprises de `Foods` :

- un fichier par entrée, **nommé par l'id de l'objet Hytale** qu'il décrit ;
- un pack chargé après HyAngler remplace notre fichier en fournissant le même nom ;
- lecture tolérante : une clé absente prend sa valeur par défaut, une clé inconnue est ignorée (P2 ajoutera des clés de taille) ;
- un fichier invalide pour le cœur (objet inconnu, valeur hors bornes, condition inconnue ou mal formée) est écarté, journalisé une fois en WARNING, et listé par le selftest. Une **erreur de type** dans un champ typé (`"Weight": "trente"`) est, elle, une erreur de décodage de Hytale : dans un pack zip ou jar, elle arrête le serveur, comme pour tout asset de Hytale (`fishing-hytale.md` § 5.13). Les conditions et modificateurs sont lus en BSON brut, de toute forme (`RawBsonCodec` : une liste à la place d'un objet ne lève pas), et jugés par le cœur : seule une erreur de type sur `Weight`, `Quality`, `Rarities`, `Category`, `Count`, `Tier`, `Lure`, `Luck` ou `MaxLine` peut en arriver là. Nos fichiers sont vérifiés au build (`CheckPackAssets`) ;
- un objet sans fichier ne se pêche pas.

### 6.1 Poissons : `Server/HyAngler/Fish/<id d'objet>.json`

```json
{
  "Weight": 30,
  "Quality": 0,
  "Rarities": true,
  "Conditions": { "All": [
    { "Type": "Environment", "Ids": ["Env_Zone1_Forests", "Env_Zone1_Mountains", "Env_Zone1_Autumn"] },
    { "Type": "Water", "Kind": "Fresh" },
    { "Type": "Time", "From": 5, "To": 22 }
  ]},
  "Modifiers": [
    { "If": { "Type": "Time", "From": 5, "To": 9 }, "Multiplier": 1.5 },
    { "If": { "Type": "Weather", "Rain": true }, "Multiplier": 1.5 }
  ]
}
```

- `Weight` (entier ≥ 1) : son poids dans la catégorie poisson ; `Quality` (entier, défaut 0) : sa sensibilité à la chance (vanilla) ;
- `Rarities` (défaut `true`) : à la prise, tirer un état de rareté (§ 6.3). Il ne peut que couper le tirage : un objet sans états de rareté n'en tire jamais, même avec `true` ;
- `Conditions` : toutes doivent être vraies (§ 6.4), sinon le poisson est exclu ;
- `Modifiers` : multiplicateurs appliqués au poids quand leur condition est vraie (Tide `conditional`).

### 6.2 Prises : `Server/HyAngler/Catches/<id d'objet>.json`

Les déchets et les trésors, sur le même modèle : `Category` (`Junk` ou `Treasure`), `Weight`, `Quality`, `Count` (`[min, max]`, défaut `[1, 1]`), `Conditions`, `Modifiers`. Nos fichiers reprennent les objets des listes de la nasse : gravats, bâtons, lentilles d'eau, `Deco_Trash`, coquillages, sel pour les déchets ; `Deco_Treasure`, `Ore_Gold`, `Weapon_Spear_Fishbone` et `Deco_Treasure_Pile_Large` pour les trésors (`fishing-hytale.md` § 1.3). Tout trésor exige l'eau libre, que son fichier le dise ou non (§ 5).

### 6.3 Rareté

Un poisson à états tire son état après le choix de l'espèce. Les poids sont ceux de la nasse appâtée : Common 100, Uncommon 50, Rare 10, Epic 5, Legendary 1. La chance de la canne les ajuste avec des qualités 0, +1, +2, +3, +4 (même formule que § 5). L'objet donné est l'état `*<Objet>_State_<Rareté>`, ou l'objet de base pour Common. Un objet est « à états » s'il en a au moins un (Uncommon, Rare, Epic ou Legendary) : tous n'ont pas les quatre (`Fish_Tang_Blue_Item` commence à Rare, `Fish_Clownfish_Item` à Epic, `Fish_Jellyfish_Blue_Item` n'a que Legendary). Un rang tiré que l'objet n'a pas donne l'état le plus proche en dessous qu'il a, sinon l'objet de base (tâche 14, `withState` levant sur un état inconnu, `fishing-hytale.md` § 5.5). Les poissons « monstres » n'ont pas d'état : leur rareté vient de leur faible `Weight`. Défaut des assets à contourner : l'état Epic porte le ResourceType `Fish_Rare` (`fishing-hytale.md` § 1.1), ce qui ne change rien au tirage.

### 6.4 Conditions

| Type | Champs | Vrai quand |
|---|---|---|
| `All`, `Any`, `Not` | une liste de conditions (`Not` : une seule) | toutes, au moins une, aucune |
| `Environment` | `Ids` | l'environnement de la case du bouchon en fait partie |
| `Zone` | `Ids` (`Zone0`…`Zone4`) | la zone de la case en fait partie (le tag `ZoneN` de son environnement) |
| `Water` | `Kind` : `Fresh` ou `Salt` | eau salée = environnement de la liste `saltEnvironments` de `hyangler/id-map.json` (océans `Env_Zone0_*` et côtes `*_Shores`) ; sinon eau douce |
| `Depth` | `Min`, `Max` (blocs d'eau sous la surface) | la colonne d'eau sous le bouchon est dans les bornes (parcours borné) |
| `Time` | `From`, `To` (heures 0 à 24, `From > To` passe minuit, `From = To` refusé) | l'heure du monde est dans la plage |
| `Weather` | `Ids` et/ou `Rain` (booléen) | chaque clé donnée tient : la météo de la case est l'une des `Ids`, et il pleut sur le bouchon si `Rain` est vrai (ne pleut pas s'il est faux) |
| `Moon` | `Phases` (indices) | la phase de lune en fait partie |
| `OpenWater` | aucun | le cube 5 × 4 × 5 de vanilla autour du bouchon |
| `Sky` | `Visible` (booléen) | le bouchon voit le ciel |

- Un mod ajoute un type par l'API (`ConditionTypes.register`, § 8.1). Un type inconnu rend le fichier invalide, jamais le serveur.
- Les heures se lisent dans l'heure de Hytale (`WorldTimeResource`, jour = 60 % des 24 h, `plugin-b-api.md` § 31), pas en ticks de Minecraft : c'est le monde (CLAUDE.md § 6).

### 6.5 Nos fichiers : les espèces, leurs environnements et leurs heures

Les environnements sont repris des fichiers d'apparition de Hytale (`fishing-hytale.md` § 1.4). Les heures et la météo sont **à nous**, d'après la biologie : Hytale n'en distingue aucune. Les valeurs ci-dessous sont **à valider par l'utilisateur**.

| Espèce (objet) | Eau | Heures (prise) | Meilleur moment (×1,5) | Météo | Poids |
|---|---|---|---|---|---|
| Minnow (vairon) | douce + océan tempéré | 6-20 | — | — | 40 |
| Bluegill (crapet) | douce + océans | 7-19 | — | — | 40 |
| Trout_Rainbow (truite) | douce | 5-22 | 5-9, 18-22 | pluie ×1,5 | 25 |
| Catfish (poisson-chat) | douce | 19-6 | — | pluie ×1,3 ; `Depth` ≥ 2 | 25 |
| Pike (brochet) | douce | 6-21 | 6-9, 18-21 | — | 12 |
| Piranha | douce | 8-18 | — | — | 15 |
| Piranha_Black | douce | 8-18 | — | — | 5 |
| Salmon (saumon) | douce + océan froid | 4-22 | 4-8 | pluie ×1,3 | 20 |
| Frostgill | douce glaciaire + côtes et océan froids | 0-24 | — | neige ×1,5 | 15 |
| Snapjaw | côtes de zone 3 | 20-5 | — | — | 6 |
| Trilobite, Trilobite_Black | glaciaire, désert, oasis | 0-24 | — | `Depth` ≥ 2 | 8 |
| Clownfish, Tang ×4 (poissons-chirurgiens) | côtes | 7-19 | — | — | 20 |
| Pufferfish (poisson-globe) | côtes | 8-18 | — | — | 10 |
| Lobster (homard), Crab | côtes | 18-6 | — | `Depth` ≥ 2 | 10 |
| Eel_Moray (murène) | savane, océan chaud | 19-6 | — | — | 8 |
| Jellyfish ×6 (méduses) | côtes de zone 2 | 0-24 | 20-6 | — | 10 |
| Jellyfish_Man_Of_War | côtes de zone 2 | 0-24 | — | orage ×2 | 2 |
| Shark_Hammerhead (requin-marteau) | océan chaud, `OpenWater` | 18-6 | — | — | 1 |
| **Perch (perche, nouvelle)** | douce, zones 1 et 3 (plaines, forêts, marais, toundra) | 6-20 | 6-9, 17-20 | — | 30 |

- **Pas pêchables en P1** : `Whale_Humpback` (une baleine à la canne n'a pas de sens) et `Shellfish_Lava` (la pêche dans la lave viendra plus tard, § 13).
- **La perche** est le premier poisson ajouté **seulement par des données** : son objet `Fish_Perch_Item` à états de rareté (sur le gabarit `Template_Fish_Item`), son rôle de PNJ (variante de `Template_Swimming_Passive`) avec ses apparitions, et son fichier `Fish/`. Elle prouve qu'un autre mod peut ajouter une espèce. Son modèle repart de l'essai du 2026-10-04 (rig du Bluegill, orientations de repos du Bluegill), et l'utilisateur le valide dans Blockbench avant le commit.

## 7. La pêche du joueur (P1)

### 7.1 Les cannes

| Canne | Appât | Chance | Durabilité | Recette |
|---|---|---|---|---|
| `HyAngler_Rod_Crude` | 0 | 0 | 200 | `Fieldcraft` (`Tools`) et établi (`Workbench_Tools`) : bâtons, fibre |
| `HyAngler_Rod_Copper` | 1 | 0 | 300 | établi (`Workbench_Tools`) : lingot de cuivre, bois, fibre |
| `HyAngler_Rod_Iron` | 1 | 1 | 500 | établi : lingot de fer, cuir léger, tissu de lin |
| `HyAngler_Rod_Thorium` | 2 | 1 | 700 | établi de palier 2 (`RequiredTierLevel` 2) : lingot de thorium, cuir moyen, lin |
| `HyAngler_Rod_Cobalt` | 2 | 2 | 700 | établi de palier 2 : lingot de cobalt, cuir lourd, shadoweave |
| `HyAngler_Rod_Adamantite` | 3 | 2 | 1000 | établi de palier 3 : lingot d'adamantite, cuir lourd, cindercloth |
| `HyAngler_Rod_Mithril` | 3 | 3 | 400 | établi de palier 3 : lingot de mithril, cuir d'orage |
| `HyAngler_Rod_Onyxium` | 3 | 3 | 450 | établi de palier 3 : lingot d'onyxium, cuir d'orage (la hache d'onyxium de Hytale n'a pas de recette en pre.5 : écart, § 14) |

- **Huit cannes, une par palier d'outil de Hytale** (amendement du 2026-10-05 : l'utilisateur a demandé l'adamantite, le mithril et l'onyxium le 2026-10-04). Appât et chance ne dépassent pas 3, comme les enchantements Appât et Chance de la mer de vanilla ; mithril et onyxium ne diffèrent que par la durabilité tant que P3 n'ajoute pas les lignes et les moulinets.
- Les stats viennent de `Server/HyAngler/Rods/<id>.json` (`Tier`, `Lure`, `Luck`, `MaxLine`), ouvert comme les poissons ; la durabilité est celle de l'objet (`MaxDurability`), égale à celle de la hache du même palier (§ 4), comme sa `Quality` (Crude et Copper `Common`, Iron `Uncommon`, Thorium à Adamantite `Rare`, Mithril et Onyxium `Epic`). Une canne demande un peu moins de métal que la hache du même palier.
- **Les modèles** (approuvés par l'utilisateur dans Blockbench le 2026-10-04) : un dessin par palier, dans le langage que Hytale donne aux arcs de ce palier (`zip:Common/Items/Weapons/Bow/<Tier>`), sur un squelette commun. Le squelette, ce sont quatre sections articulées en chaîne (`Rod_Blank › Rod_Mid › Rod_Upper › Rod_Tip`, charnières à 16, 49, 83 et 100 unités), la manivelle (`Rod_Reel_Crank`) sur l'axe de son moulinet et l'anneau du scion (ses quatre barres `Rod_Tip_Ring_*`). Les animations et le suivi de la ligne en dépendent. Les modèles sont générés par `tools/angler/rods.py` (dessins : `rod_designs.py`, `rod_designs_rare.py`), qui refuse une pièce ou un groupe de pièces qui ne tient pas au reste par une face.

### 7.1 bis Les animations (essais du 2026-10-04, `fishing-hytale.md` § 7)

- Un jeu d'animations propre, `Server/Item/Animations/HyAngler_Rod.json` (`"Parent": "Item"`), fait dans Blockbench sur le joueur de Hytale avec la canne en attachement. Il compte treize entrées : `Idle`, `CastCharging`, `Cast`, `Bite`, `Hook`, `FightLight`, `FightHeavy`, `FightPump`, `ReelFight`, `Reel`, `Escape`, `Snap`, `Catch`. Une vraie canne ne plie pas à l'armé (le bras la ramène droite derrière l'épaule) ; seul le fouet avant fait traîner le scion, et elle plie sous un poisson. Chaque entrée a un fichier de 3e personne (corps et canne) et, sauf `Idle`, un `_FPS` (canne seule) : 25 fichiers. `Idle` garde en 1re personne celui de vanilla (`Main_Handed/Item/Idle_FPS.blockyanim`).
- Le `Charging` joue `CastCharging`, et les paliers de `Next` jouent `Cast` (`Effects.ItemAnimationId`). Le serveur joue les autres au bon moment par `AnimationUtils.playAnimation(…, Action, "HyAngler_Rod", …, true, …)`. En P1, ce sont `Bite` à la touche ; `Hook` puis `Catch` à la prise ; `Escape` pour un poisson manqué ; `Snap` à la casse. Le bouchon reste 40 ticks après la fin du lancer, le temps que l'animation de fin se joue avec sa ligne. `Reel` (manivelle à vide) sert au clic gauche de la canne. `FightLight`, `FightHeavy`, `FightPump` et `ReelFight` attendent le combat (P4).

### 7.2 Le lancer, le bouchon, la ligne

1. **Lancer** : clic droit maintenu, interaction `Charging` à trois paliers (0 ; 0,5 ; 1 s → force de lancer faible, moyenne, forte). Elle se termine par une interaction Java de HyAngler qui lance le bouchon (`spawnProjectile`), avec le son `SFX_Tool_Hookshot_Fire` en attendant un son propre.
2. **Ligne** (recette validée en jeu le 2026-10-04, `fishing-hytale.md` § 7.5, « Bilan ») :
   - un faisceau propre `HyAngler_Line`, un fil clair de 2 px (`Server/Entity/Beams/`, texture sous `Common/Beams/`) ;
   - une ligne qui pend : 18 entités porteuses sans modèle (19 segments) entre le bouchon et le scion, que le serveur pose à chaque tick sur une parabole sous la corde. La flèche vaut 12 % de la corde au repos et 1,5 % tendue (2,5 blocs au plus), et elle s'approche de sa cible de 25 % de l'écart par tick ;
   - le dernier segment s'accroche à l'os `R-Attachment` du pêcheur, décalé en blocs jusqu'au scion, dans le repère de l'os tel que l'animation le pose. Le serveur ajoute le pli des sections, mesuré par entrée dans Blockbench, change le faisceau sur place (`BeamComponent.set`), avance de 2 ticks et passe d'une pose à l'autre sur la `BlendingDuration` de l'entrée ;
   - les porteurs sont posés sur le scion vu du serveur : les mêmes pistes, depuis les pieds, tournées autour de l'épaule selon le regard (le jeu `Item` fait suivre le regard à l'épaule, de −30° à 60°) ;
   - une ligne cassée (`BROKEN`) est retirée d'un coup, porteurs compris ; toute fin du bouchon retire la ligne (`RefSystem` sur le bouchon).

   **Vue** : la pêche se joue en 3e personne (décision de l'utilisateur). En 1re personne, la canne dessinée devant la caméra n'est pas où la ligne s'accroche, et le serveur ne voit pas la vue du joueur. Au lancer, la vue passe en 3e personne, **sans être bloquée** (`SetServerCamera(ThirdPerson, false, null)`, décision de l'utilisateur du 2026-10-05) : le joueur peut revenir en 1re personne, et la pêche continue, car aucun paquet du client ne dit au serveur la vue choisie (vérifié : `ClientMovement`, `SetMovementStates`, `SyncPlayerPreferences`, `MouseInteraction`). À la fin du bouchon, quelle qu'elle soit, elle revient à la vue imposée par le mode de jeu s'il y en a une, sinon à celle du joueur (comme `SpectatorSystems.applyFreeCamera`), sauf si le joueur a déjà relancé ; un joueur parti dans un autre monde la retrouve par l'univers (`Universe.getPlayer`).
3. **Bouchon** : un projectile `Standard` avec un modèle propre. Un système de HyAngler lit `isInFluid()` des seuls bouchons : dès le contact, le bouchon est **figé à la surface** et oscille doucement (technique de Cozy Tales, idée seulement). Posé au sol, il reste au sol (§ 7.4). Sa durée de vie est passée à `spawnProjectile` (le défaut est de 5 min) : une heure, car le lancer n'a pas de durée maximale fixe (§ 7.4).
4. **Second clic droit** : ramène la ligne. Pendant la fenêtre de ferrage, c'est la prise ; sinon, rien.

### 7.3 L'attente et la prise

- La machine d'état est dans le cœur (`cast`), nourrie à 20 ticks/s par le plugin (dans l'eau, au sol, distance au joueur, pluie, ciel). Le monde de Hytale tourne à 30 ticks/s : le système du bouchon accumule le temps et avance le lancer d'un tick du cœur toutes les 0,05 s, comme `ColonyTickSystem` dans HyColony. Ses états :
  - `FLYING` ;
  - `FLOATING` : l'attente de § 5 ;
  - `APPROACH` : bulles `Water_Bubble_Stream` en sillage vers le bouchon ;
  - `BITING` : le bouchon plonge, éclaboussure `Water_Splash`, son `SFX_Water_MoveIn`, fenêtre de ferrage ;
  - puis `CAUGHT`, `ESCAPED`, `GROUNDED`, `BROKEN` ou `CANCELLED`.
- **À la prise**, le plugin construit le `FishingContext` à la case du bouchon (§ 8.1), avec l'eau libre que le lancer a gardée pendant l'approche et la touche (`CastSession.openWater`, § 5 ; le plugin lit `ContextFactory.openWater` à chaque tick de ces deux états seulement), le cœur tire la prise (§ 6) et applique les crochets de prise (§ 8.1). L'objet vole du bouchon vers le joueur (`interactivelyPickupItem` avec `origin` au bouchon) et la canne s'use (§ 5).

### 7.4 Les échecs (aucun état sans sortie, CLAUDE.md § 4)

| Cas | Effet |
|---|---|
| ferrer hors de la fenêtre, ou pendant le vol | rien ne vient, usure 0 ; la touche passée, retour à l'attente |
| bouchon au sol | ramené au clic, usure 2 ; disparu après `MAX_GROUNDED_TICKS` (1 200, vanilla) au sol de suite, lancer annulé |
| l'eau disparaît sous le bouchon | le compte s'arrête (vanilla) jusqu'à la borne du cycle |
| distance joueur-bouchon > `MaxLine` (32 par défaut) | la ligne casse, le bouchon disparaît |
| plus de canne en main (autre emplacement, canne jetée ou déplacée ; lu à chaque tick du bouchon), déconnexion, mort, changement de monde | lancer annulé, bouchon et ligne retirés au tick suivant, sans animation |
| chunk déchargé (Hytale retire le bouchon, sa `Ref` n'est plus valide) | lancer annulé |
| `FLYING` plus de 200 ticks au total sans eau (le compte gelé au sol) | lancer annulé |
| une attente, son approche et sa fenêtre durent plus de 4 fois leur durée tirée (`CYCLE_SLACK`) | lancer annulé (borne de sûreté, remise à zéro à chaque attente tirée : elle suit le multiplicateur de la config, et un ciel caché ne double qu'en moyenne la durée) |

Un seul lancer actif par joueur. L'état du lancer est gardé dans le cœur, indexé par joueur, jamais sauvegardé.

## 8. L'API

Règles reprises de l'API de HyColony (`2026-09-30-hycolony-api-hylens-design.md` § 4.1) :

- holder statique `HyAnglerApi.get()` ;
- instantanés immuables, `Optional` pour une absence, `ApiText` pour tout texte ;
- appel hors du fil du monde : `IllegalStateException`, sauf `get()` et la fermeture d'un abonnement ;
- semver propre (`ApiVersion`, `@since`), `@Experimental` pour ce qui peut changer ;
- empreintes vérifiées par `apiCheck`.

### 8.1 `dev.hyangler.api` (Java pur)

- **`FishingContext`** (record) : `environment`, `zone`, `water` (`FRESH`, `SALT`), `depth`, `openWater`, `skyVisible`, `hour` (double, 0 à 24), `weather`, `raining`, `moonPhase`, `tackle`. Aucun joueur ni bouchon : un PNJ le construit comme un joueur.
- **`Tackle`** (record) : les stats cumulées de l'équipement : `lure`, `luck`, `maxLine`, et dès P1 les champs de P3 à leur valeur neutre (`reelSpeed`, `lineStrength`, `hookSize`, `depthBias`, `bait` facultatif). Ajouter un champ plus tard serait une rupture (§ 8 règles) : ils y sont dès la v1.
- **`Catch`** (record) : `itemId`, `count`, `category` (`FISH`, `JUNK`, `TREASURE`), `rarity` (facultative), `source` (le fichier d'origine).
- **`Angler`** (scellé) : `Player(UUID)`, `Plugin(String name, String id)` (un PNJ d'un autre mod, le pêcheur de HyColony).
- **`Fishing`** (lu par `HyAnglerApi`) :
  - `roll(FishingContext, RandomGenerator) → Optional<Catch>`, pur ; vide quand rien ne peut mordre là ;
  - `chances(FishingContext) → List<CatchChance>` (prise, catégorie, probabilité), pour `/hyangler test`, le futur journal et une fenêtre de HyColony ;
  - `rod(String itemId) → Optional<RodStats>` (`tier`, `lure`, `luck`, `maxLine`) ;
  - `biteDelay(int lure, RandomGenerator) → BiteTimes` (attente, approche, fenêtre ; § 5), pour un client qui veut les délais de vanilla. Le pêcheur de HyColony garde ceux de MC (P6).
- **`ConditionTypes.register(String type, ConditionFactory)`** : un type de condition d'un autre mod, lu dans les fichiers à côté des nôtres. Il est appelé au `setup` de ce mod, avant le chargement des assets.
- **`CatchHooks.register(owner, CatchHook)`** : `(Angler, FishingContext, Catch) → Catch`, appelé après le tirage, dans l'ordre d'inscription. C'est ainsi qu'on annule ou modifie une prise (le « prise annulable et modifiable » de Gone Fishing), sans événement mutable.
- **Événements** (records publiés après les faits, sur le fil du monde, abonnement par `subscribe(owner, Class, Consumer)`) : `CastStarted(Angler, pos)`, `FishBiting(Angler, pos)`, `CatchLanded(Angler, Catch, pos)`, `CastEnded(Angler, Outcome)` (`Outcome` scellé : `Caught` (la prise donnée, vide si un crochet l'a annulée ou si rien ne pouvait mordre), `Escaped`, `Grounded`, `Broken`, `Cancelled`).
- Fils : `roll`, `chances`, `rod` et `biteTimes` sont purs et sûrs depuis n'importe quel fil (le catalogue est immuable une fois lu) ; inscrire un type, un crochet ou un abonné aussi (copie à l'écriture). Seuls les appels qui touchent le monde (§ 8.2) lèvent `IllegalStateException` hors de son fil.

### 8.2 `dev.hyangler.plugin.api` (types Hytale)

- `HyAnglerApi.contextAt(World, Vector3i, Tackle) → FishingContext` : lit l'environnement, la zone, la météo, l'heure, la lune, la colonne d'eau, l'eau libre et le ciel d'une case.
- `HyAnglerApi.tackleOf(ItemStack) → Optional<Tackle>` : l'équipement d'une canne (en P1, ses stats de fichier).
- `@Experimental` jusqu'à son premier client (P6) : `castVisual(Ref<EntityStore> entity, Vector3d target) → VisualCast`, qui pose un bouchon, une ligne et les effets pour une entité qui n'est pas un joueur. `VisualCast` a `bite()`, `reel()` et `close()`. Le pêcheur de HyColony y branche sa propre machine d'état (celle de MC).

### 8.3 Ce que HyColony en fera (P6, pour mémoire)

HyAngler y remplace les tables de pêche de Minecraft vers lesquelles MineColonies délègue (`DefaultFishermanLootProvider`, `fishing-benchmark.md` § 3). Le reste du métier reste du système MC dans le cœur de HyColony : étangs 5 × 5 × 2, recherche de rive, états, délais (1060 à 1300 ticks moins 100 × vitesse d'appât), table bonus par niveau de hutte, niveau de canne par niveau de hutte. Le contrat demandé par MC (`mc-fisherman.md` § 9) est couvert par `contextAt`, `roll`, `rod`, `castVisual` et la durabilité de Hytale. Le reste (forme d'un étang, rive, ligne de vue) passe par les ports de HyColony.

## 9. Commandes, textes, configuration

- **Commandes** :
  - `/hyangler test` : les prises possibles là où le joueur regarde (ou à sa position), avec leurs chances, son contexte et la canne en main ;
  - `/hyangler give <canne|poisson>` (opérateur) ;
  - `/hyangler selftest` : fichiers lus, pris et écartés, environnements de nos fichiers absents des assets, modèles et sons présents.
- **Textes** : `Server/Languages/{en-US,fr-FR}/hyangler.lang`, mêmes clés dans les deux **[build : `checkLangParity`]**. Les noms des cannes, de la perche et de ses états, les messages (ligne cassée, rien n'a mordu) et le texte des commandes.
- **id-map** (`hyangler/id-map.json`) : les ids d'assets Hytale qu'utilise le plugin (`saltEnvironments`, fluides d'eau, particules, sons, faisceau, gabarits), comme dans les autres mods (CLAUDE.md § 7).
- **Configuration** (`config.json` de HyAngler, section `HyAngler`) : `BiteTimeMultiplier` (1,0, de 0,1 à 10), `RodWear` (vrai), `MaxLineDefault` (32, de 8 à 64), `TestCommandOpOnly` (vrai). Le mini-jeu s'ajoutera en P4.

## 10. Robustesse et persistance

- Un port ne lève jamais d'exception : chunk déchargé, case hors monde ou environnement inconnu donnent un contexte neutre (eau douce, profondeur 0, environnement vide), et le premier échec est journalisé (CLAUDE.md § 4).
- Tout tourne sur le fil du monde. Les fichiers sont lus une fois, au premier usage après le chargement des assets ; un rechargement demande un redémarrage, comme pour `Foods`.
- Pas d'allocation par tick dans le suivi des bouchons : un système ECS parcourt seulement les entités qui ont le composant de bouchon de HyAngler. Les parcours d'eau (profondeur, eau libre) sont bornés (`SCAN_LIMIT`) et ne se font qu'au moment du tirage.
- **Rien n'est sauvegardé en P1** : le lancer est éphémère, et Hytale ne sauvegarde jamais un projectile (`NonSerialized`, `ProjectileModule.java:240`) ; au déchargement d'un chunk il retire le bouchon, et le lancer s'annule (§ 7.4).
- **Configuration malformée** : HyAngler ne voit pas HyBlockUI, donc pas son `ConfigQuarantine`. Il en garde une copie à lui, pour qu'un `config.json` malformé soit mis de côté au lieu d'arrêter le démarrage (`fishing-hytale.md` § 5.11).
- Les records et le journal de P2 seront en JSON versionné, avec migrations et fixtures (CLAUDE.md § 5).

## 11. Tests

- **Cœur (TDD)** :
  - chaque condition et leurs combinaisons ; heures qui passent minuit ;
  - poids effectifs avec la chance, exclusion à poids nul, modificateurs ;
  - catégories 85/10/5 et règle de l'eau libre pour les trésors ;
  - tirage de la rareté ;
  - attente, approche et fenêtre : bornes, pluie, ciel, appât (tirage positif, plafond à 5), plancher à 1 ;
  - machine d'état : chaque transition, chaque échec, le compte arrêté hors de l'eau, les bornes du vol, du sol et du cycle ;
  - lecture tolérante : clé absente, clé inconnue, valeur hors bornes, type de condition inconnu ;
  - registre de conditions et crochets de prise, avec un faux mod.
  
  Noms en phrases (`troutIsNotCaughtAtNoon`…).
- **API** : `apiCheck` sur les deux empreintes ; ArchUnit en liste blanche sur `:angler-api`.
- **Plugin** : `/hyangler selftest` et une section HyAngler de `docs/TESTING.md`, que l'utilisateur déroule en jeu.

## 12. Premier pas : l'essai en jeu, puis les garde-fous

1. **Essai en jeu, avant de bâtir dessus** (plugin minimal jetable, ou commande de test) :
   - un projectile `Standard` de densité 700 flotte-t-il, se fige-t-il à la surface, dérive-t-il ?
   - la corde `Rope`, ou notre faisceau, se lit-elle à 20 ou 30 blocs, et suit-elle un bouchon qui bouge ?
   - le lancer `Charging` fonctionne-t-il sur une canne, et quelle animation joue le client ?
   - l'objet de `interactivelyPickupItem` vole-t-il visiblement depuis le bouchon ?
   - les états `*Fish_*_Item_State_*` donnés par un autre pack ont-ils le bon nom et la bonne icône ?
   
   Si l'un échoue, la spec est amendée avant la suite (`fishing-hytale.md`, questions en jeu).

   Fait le 2026-10-04 (`fishing-hytale.md` § 6 et § 7). Réponses :
   - le bouchon ne flotte pas seul : il est figé, comme prévu ;
   - le lancer chargé marche ;
   - la ligne pend et suit la canne (§ 7.2) ;
   - les animations sont les nôtres (§ 7.1 bis) ;
   - la vue est imposée en 3e personne.

   Restent ouvertes, pour les tâches de la prise et de la perche :
   - la lisibilité de la ligne à 20 et 30 blocs ;
   - l'objet qui vole du bouchon vers le joueur ;
   - les états de rareté donnés par un autre pack.
2. **Garde-fous** (accord de l'utilisateur donné en choisissant « un mod en plus » ; session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`) :
   - CLAUDE.md § 1 : six mods, HyAngler, ses paquets `api`, ses modules ;
   - § 7 : `hyangler.lang`, `hyangler/id-map.json`, les types d'assets `Server/HyAngler/` ;
   - `AGENTS.md` ;
   - les agents (`hycolony-implementer`, `hycolony-reviewer`, `ui-lang-checker`) et la skill `add-lang-key` (liste des `.lang`) ;
   - `build-logic/src/main/kotlin/hy.java-checks.gradle.kts` : `checkModApis` (`modApis`), NullAway (`AnnotatedPackages`), `apiCheck` (`apiPackages` : `:angler-api` et `:angler-plugin`) ;
   - `.claude/hooks/guard.js` s'il nomme les mods.

## 13. Feuille de route : le mod complet

Chaque ligne aura sa spec. L'API de P1 porte déjà ce qu'il faut pour ne pas casser.

| Sous-projet | Contenu |
|---|---|
| **P1** (cette spec) | moteur, API, données ouvertes, 8 cannes montées et leurs animations, bouchon, ligne qui pend, attente de vanilla, perche, commandes |
| **P6** | le pêcheur de HyColony (MC `BuildingFisherman`, `JobFisherman`, `EntityAIWorkFisherman`) sur l'API |
| **P2** | taille et poids (loi log-normale, trophée ; Tide `FishSizeModel`), records personnels et du serveur, **journal** (silhouettes, habitats et heures découverts, nourri par `chances`) |
| **Bestiaire** | nouvelles espèces sur le rig de Hytale (le brochet d'Europe de l'essai, carpe, esturgeon, cabillaud…), là où `/hyangler test` montre des zones pauvres |
| **P3** | **établi de pêche** et pièces : canne (blank), **moulinet** (vitesse, frein, capacité), **ligne** (résistance, discrétion, longueur ; fibre, soie, tressée, câble d'acier contre les dents du brochet), **hameçon** (taille, fenêtre, prise double, ardillon), **flotteur et plomb** (surface, mi-eau, fond), **appâts** consommables et **leurres**, **boîte à pêche** |
| **P4** | **mini-jeu** facultatif et réglable (barre de timing ou de tension), qui tient compte de la force et du comportement du poisson, du frein et de la résistance de la ligne ; un PNJ ne le joue jamais |
| **P5** | pêche passive : la nasse de Hytale branchée sur le moteur, casiers, filets |
| Plus tard | **coins de pêche** (remous), **pêche sous la glace**, en grotte, **dans la lave** (`Shellfish_Lava`), poisson ramené vivant (seau, aquarium, étang), **trophées au mur**, **couteau à filets** (poids → filets), quêtes et tournois, sons propres |

## 14. Écarts et choix

HyAngler n'est pas un portage de MineColonies : la règle de fidélité de CLAUDE.md § 6 vaut pour P6, pas pour P1. Il suit le système de pêche vanilla de Minecraft (§ 5) dans le monde de Hytale. Les écarts à vanilla sont marqués `Deviation from vanilla: …` dans le code :

- **pas d'XP** à la prise (Hytale n'a pas d'expérience de joueur) ;
- **pas d'enchantements** : le palier de la canne porte l'appât et la chance (§ 7.1) ;
- **eau salée** déduite de l'environnement (Hytale n'a qu'une eau) ;
- **poissons et heures** : environnements de Hytale, heures et météo à nous (§ 6.5) ;
- **rareté** : les états de rareté de Hytale, avec les poids de la nasse (§ 6.3) ;
- **pas d'entité accrochée** au ferrage (vanilla tire une entité vers le joueur) : hors P1 ;
- **la pêche en 3e personne au lancer** (vanilla pêche dans les deux vues) : en 1re personne, Hytale dessine la canne ailleurs que là où la ligne s'accroche (§ 7.2) ; la vue reste libre ;
- **une attente toujours positive** : vanilla retire au tick suivant une attente de 0 ou moins ; HyAngler tire dans la partie positive, même loi, sans ces quelques ticks (en moyenne 0,7 avec l'appât 3) (§ 5) ;
- **un appât compté jusqu'à 5** : dès l'appât 6, toute attente de vanilla est négative et rien ne mord jamais (Paper corrige la même boucle : correctif « Fix Lure infinite loop », `paper-server/patches/sources/.../FishingHook.java.patch`) ; une canne d'un autre mod doit pêcher (§ 5) ;
- **des bornes de sûreté** que vanilla n'a pas : un vol de 200 ticks au total sans eau (le compte gelé au sol), un cycle d'attente 4 fois sa durée tirée (§ 7.4), la chance comptée jusqu'à 1 024 et les sommes de poids plafonnées, pour qu'un fichier ou un mod extrême ne fasse jamais déborder un calcul ;
- **pas de chance du joueur** (monde Hytale) : vanilla ajoute l'attribut Chance du joueur (`withLuck(luck + owner.getLuck())`) ; Hytale n'a pas d'attribut de chance, seule la canne compte ;
- **une catégorie vide cède sa part** (monde Hytale) : chez vanilla, les sous-tables donnent toujours quelque chose, et une sous-table vide garderait son poids pour ne rien donner ; ici les poissons dépendent de l'environnement et de l'heure, une catégorie est souvent vide, et sa part va aux autres ;
- **les qualités de rareté** (monde Hytale) : vanilla n'a pas de rareté, et la nasse de Hytale ignore la chance ; les qualités 0 à +4 sont à nous, sur la règle de qualité de vanilla (§ 6.3) ;
- **la canne d'onyxium a une recette**, alors que la hache d'onyxium de Hytale n'en a aucune en pre.5 (ni recette ni butin qui la nomme) : sinon, la canne serait introuvable.

## 15. À vérifier pendant le plan (sources décompilées)

- La lecture de la météo d'une case et de la pluie (`WeatherResource`, `getEffectiveWeatherIndex`) : les ids des météos de pluie, de neige et d'orage.
- La visibilité du ciel depuis une case (équivalent de `canSeeSky`), ou un parcours de colonne borné.
- `EnvironmentSection` depuis un système du plugin, et le nom de zone d'un environnement.
- `Charging` qui se termine par une interaction Java propre ; le passage du palier tenu à l'interaction.
- Un asset `Beam` propre (texture, échelle) et `BeamComponent` posé à la main sur un projectile.
- `spawnProjectile` avec une durée de vie, et un PNJ comme créateur (P6).
- Le gabarit `Template_Fish_Item` et le rôle `Template_Swimming_Passive` pour un poisson d'un autre pack (la perche).
- Les quantités et paliers d'établi des recettes de haches, pour aligner les cannes.
