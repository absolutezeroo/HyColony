# Pêche : le mini-jeu du combat (« rester dans le vert ») (2026-10-05)

Demande de l'utilisateur : « il manque une UI où faut rester dans le vert pour récupérer le poisson ». La spec HyAngler (`docs/superpowers/specs/2026-10-04-hyangler-design.md` l. 355) range ce mini-jeu en P4 : facultatif et réglable, il tient compte de la force et du comportement du poisson, du frein et de la ligne, et un PNJ ne le joue jamais. Aujourd'hui, un clic droit pendant la touche prend le poisson tout de suite (`CastSession.reel`, `BITING → CAUGHT`).

Sources : **HY** = `build/vineflower/hytale-server/com/hypixel/hytale/` (serveur décompilé, `hytale_version = 0.7.0-pre.5` dans `gradle.properties` l. 19 ; CLAUDE.md dit encore pre.4) ; **zip** = `%USERPROFILE%/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip`. Rien n'a été lancé en jeu : tout ce qui dépend du client est marqué **[in-game]**. Complète `fishing-benchmark.md` (§ 4-5, systèmes de pêche) et `fishing-hytale.md` (§ 5.4, `Charging`).

## 1. Banc d'essai des mini-jeux

### 1.1 Stardew Valley : la barre verte sous gravité

Code de `BobberBar` lu dans une décompilation de la 1.5 (https://raw.githubusercontent.com/veywrn/StardewValley/master/StardewValley/Menus/BobberBar.cs) ; règles de jeu : https://stardewvalleywiki.com/Fishing. La 1.6 peut différer. Stardew met à jour 60 fois par seconde : les valeurs « par image » ci-dessous sont aussi données par seconde et par tick de 20/s (calcul de notre part).

- **La colonne** fait 568 px. La barre verte du joueur mesure `96 + 8 × niveau de pêche` px (96 à 176), plus 24 avec le flotteur en liège (objet 695) ; avec la canne de débutant sous le niveau 5, `+ 40 − 8 × niveau` (constructeur).
- **La barre du joueur** : `gravity = buttonPressed ? −0.25 : 0.25` px/image², multipliée par 0,6 quand le poisson est dans la barre (0,3 avec l'hameçon barbelé, 691). Soit 900 px/s² (540 dans la barre), ou 2,25 px/tick² à 20 ticks/s. `bobberBarSpeed += gravity ; bobberBarPos += bobberBarSpeed`, bornée entre 0 et `568 − hauteur` ; au bas, elle rebondit : `speed = −speed × 2/3` (× 0,1 en plus avec le flotteur de plomb, 692).
- **Le poisson** (0 à 532 px) : chaque image, avec la probabilité `difficulté × (20 si smooth, sinon 1) / 4000`, il choisit une cible `pos + rand(−au-dessus, en-dessous) × min(99, difficulté + rand(10, 45)) / 100`. Il accélère vers elle de `(cible − pos) / (rand(10, 30) + 100 − min(100, difficulté))`. Les « floater » et « sinker » ajoutent une dérive qui croît de 0,01 par image jusqu'à ±1,5. Mouvements : `mixed` (0), `dart` (1), `smooth` (2), `sinker` (3), `floater` (4). Exemple : à la difficulté 50, le poisson change de cible 0,75 fois par seconde en moyenne. Les difficultés vont de 5 à 110 (wiki).
- **Dans la barre** : `bobberPosition + 12 ≤ bobberBarPos − 32 + hauteur && bobberPosition − 16 ≥ bobberBarPos − 32`.
- **La jauge de prise** (`distanceFromCatching`) part de 0,3 (0,1 si le joueur n'a encore rien pêché, d'après l'extrait). Elle gagne +0,002 par image dans la barre (0,12/s) et perd −0,003 hors de la barre (0,18/s ; −0,002 avec le flotteur piège 694 ou la canne de débutant). On gagne à 1 et on perd à 0. **Durée** : 5,8 s au minimum ((1 − 0,3) / 0,12), et l'échec arrive en 1,7 s si le joueur ne fait rien (0,3 / 0,18).
- **Parfait** : `perfect = false` dès que le poisson tremble, c'est-à-dire dès qu'il sort de la barre. Une prise parfaite donne qualité +1 et XP ×2,4 (wiki, `fishing-benchmark.md` § 5).
- **Trésor** : il apparaît après 1 000 à 3 000 ms (`treasureAppearTimer`), 15 % des prises (wiki). Il se remplit de +0,0135 par image tant qu'il est dans la barre (environ 1,2 s) et retombe de 0,01 sinon. Avec le chasseur de trésor (693), la jauge de prise ne baisse pas tant que le trésor est dans la barre.
- **Échec** : le poisson s'échappe et l'appât est consommé (wiki).
- **Accessibilité** : rien dans le jeu. Des mods existent, comme *Full Fishing Bar*, qui étire la barre sur toute la colonne et peut ferrer seul (https://www.curseforge.com/stardewvalley/mods/full-fishing-bar, 78 513 téléchargements).

### 1.2 Tide (Minecraft) : un seul clic chronométré

Code : https://github.com/Lightning-64/Tide-2 (branche `main`), `data/minigame/FishCatchMinigame.java`, `client/gui/overlays/CatchMinigameOverlay.java`, `data/fishing/MinigameBehavior.java`.

- **Le jeu** : un curseur va et vient sur une barre dont la zone verte est centrée. Le joueur clique une fois.
- **Zone et vitesse** : `area = clamp(1 − strength, 0.05, 1)` et `speed = max(fishSpeed / 20 × minigameDifficultyMultiplier, 0.05)` (`FishCatchMinigame` l. 69-70). Sans données de poisson, `strength` vaut 0,2 (« miss area percentage ») et `speed` 0,5 (« movements per second », l. 61-62).
- **Les lignes** jouent sur ces valeurs : cuivre, vitesse × 0,9 ; fer, force × 0,86 ; or, vitesse × 0,95 ; diamant, force × 0,75 (l. 64-67).
- **Le curseur** vaut `behavior(animProgress × speed)`, dans [−1, 1], avec `animProgress` en ticks (`CatchMinigameOverlay` l. 155). Les comportements sont des fonctions de `t` : `SINE`, `PLATEAU`, `JITTER`, `DARTS`, `LINEAR`, `LINEAR_WRAP` (`MinigameBehavior` l. 12-17).
- **Jugement** : `|pos| < area` est une prise, et `|pos| < 0,1` une prise parfaite (l. 69-71). Sinon le poisson est manqué : `invalidateCatch`, `retrieve` et un son de cisaille (`FishCatchMinigame.onFail`, l. 104-113). Un délai de 200 ms précède le premier clic accepté (`INIT_DELAY_MILLIS`). Au bout de 80 ticks (4 s), le mini-jeu se ferme par `onTimeout`, qui n'invalide pas la prise (l. 99-102) ; la suite n'a pas été lue.
- **Architecture** : le mini-jeu tourne **sur le client**, qui envoie son verdict (`MinigameServerMsg`, codes 0 à 3, l. 135-143). Un mod Hytale n'a pas de code client : ce modèle ne se transpose pas tel quel.
- Le wiki donne d'anciennes notes, de Perfect (10 % central, 100 % de prise) à Trash (0 %), avec la mention « outdated content » (https://lightning-64.github.io/tide-wiki/mechanics/fishing-minigame/). Le mini-jeu peut être désactivé dans la configuration (`fishing-benchmark.md` § 4.1).

### 1.3 Aquaculture 2

Aquaculture n'a pas de mini-jeu : il ajoute poissons, cannes modulaires et boîte à pêche (https://www.curseforge.com/minecraft/mc-mods/aquaculture, `fishing-benchmark.md` § 4.2). Le mod *Fishing +* lui ajoute un mini-jeu : appuyer sur Espace quand les boules bleues sont dans l'indicateur rouge (https://www.curseforge.com/minecraft/mc-mods/fishing-plus).

### 1.4 Fisch (Roblox) : la barre de Stardew, à l'horizontale

https://fischipedia.org/wiki/Fishing

- **Commandes** : clic, Espace ou toucher pousse la barre blanche vers la droite, et le relâcher la ramène vers la gauche (accélération dans les deux sens).
- **Progression** : ±12 % par seconde, dans la barre ou hors d'elle (c'est le 0,12/s de Stardew). Le mini-jeu est verrouillé pendant 1,2 s ou jusqu'à 20 %. Il faut au moins 6,8 s pour remonter un poisson, 8 s depuis le début de l'animation. On perd à 0.
- **Statistiques de la canne** : Control élargit la barre (30 % sans Control), Resilience calme les mouvements du poisson ; Lure Speed et Luck ne servent qu'avant le combat. Par poisson, un modificateur de vitesse de progression de −99 % à +400 %.
- **Parfait** (aucune perte de progression) : 10 C$ et +50 % d'XP. Avant le combat, un mini-jeu de « secousse » : chaque clic réduit l'attente de 0,5 à 1,5 s.

### 1.5 Palia : une zone dans le monde, déplacée à la souris

https://palia.wiki.gg/wiki/Fishing

- **Écran** : une barre de santé de la canne en bas, et deux crochets « zone sûre » au-dessus de l'eau, que le joueur déplace à gauche et à droite **avec la souris** (stick droit sur Switch). Il mouline en tenant l'action principale.
- **États** : vert, le flotteur est dans la zone et le poisson ne saute pas, mouliner ne coûte rien et la santé remonte doucement ; jaune, le poisson saute, mouliner abîme la canne ; rouge, le flotteur est hors de la zone, la santé fond vite en moulinant et lentement sinon.
- **Difficulté** : les poissons plus rares bougent plus durement ; un appât de palier supérieur donne des poissons plus vifs ; les améliorations de canne accélèrent la zone.
- **Fin** : à santé 0, le poisson s'échappe. Chaque essai consomme un appât. Une prise santé pleine est parfaite : +20 % d'XP. Aucune option d'accessibilité documentée.

### 1.6 Sea of Thieves : tirer à l'opposé, laisser fatiguer

https://seaofthieves.wiki.gg/wiki/Fishing

- Le poisson fuit : la canne se pointe à l'opposé de sa nage, sinon la caméra tremble et la ligne casse ; un bruit de bois qui craque prévient.
- On regagne un quart de tour de moulinet à chaque changement de direction du poisson. Une fois fatigué, il se remonte sans risque.
- Commandes : l'orientation de la canne se fait au stick analogique, et l'action principale lance et mouline.

### 1.7 Valheim : tenir pour mouliner, payer en endurance

Le wiki (fandom et wiki.gg) refuse la lecture directe (HTTP 402 et 401) ; les valeurs viennent de l'extrait de recherche d'un miroir du wiki (https://breezewiki.discard.no/valheim/wiki/Fishing) et du guide https://xgamingserver.com/blog/valheim-fishing-guide/.

- Il faut ferrer dans les 0,5 s après la touche, puis tenir le clic droit pour mouliner. Mouliner coûte de l'endurance, et le poisson s'échappe quand elle est épuisée.
- Le poisson se débat par à-coups (marche/arrêt), ce qui change la vitesse de remontée et le coût en endurance.
- La vitesse de base vaut 2 m/s au niveau 0, +2 % par niveau, jusqu'à 6 m/s au niveau 100. Le coût en endurance dépend du poisson et de son niveau, multiplié par un facteur qui baisse de 0,8 % par niveau (20 % au niveau 100).

### 1.8 Terraria, Dave the Diver, Dredge

- **Terraria** : pas de mini-jeu. On lance, puis on reclique quand le flotteur bouge (https://www.pcgamer.com/uk/terraria-fishing-rod-bait-pole-quest). Un mod tModLoader y ajoute celui de Stardew.
- **Dave the Diver** : on harponne d'abord ; une fois le poisson assez blessé, il faut marteler une touche pour remplir une jauge. D'autres pointes de harpon changent la mécanique (https://caniplaythat.com/2023/09/07/dave-the-diver-accessibility-review/).
- **Dredge** : six mini-jeux selon le poisson. Le mini-jeu est **facultatif** : sans appuyer, la prise vient juste plus lentement (`fishing-benchmark.md` § 5).

### 1.9 Ce qui se dégage

- **Ce qui rend la prise difficile** : la vitesse et l'imprévisibilité du poisson (Stardew : difficulté et mouvement ; Tide : `speed` et `behavior` ; Fisch : progress speed ; Palia : rareté et appât), la taille de la zone (Stardew : niveau et liège ; Fisch : Control ; Tide : `1 − strength`) et le rythme de la jauge (Stardew : piège ; Fisch : Resilience). L'équipement réduit l'un ou l'autre ; c'est le « frein et la ligne » de la spec (Tide, ses lignes).
- **L'échec** : le poisson s'échappe, et parfois l'appât est perdu (Stardew, Palia). Chez Sea of Thieves, c'est la ligne qui casse.
- **Durée** : de 4 s pour un clic de Tide à environ 6 à 8 s au minimum pour Stardew et Fisch ; plus longue pour un poisson vif.
- **Parfait** : récompensé partout (qualité ou XP). Hytale n'a pas d'XP de joueur (spec l. 363) : la récompense serait à choisir (rareté, taille).
- **Accessibilité** : désactivable (Tide), facultatif (Dredge) ou facilité par un mod (Stardew). C'est la spec : « facultatif et réglable ».

## 2. Ce que Hytale permet

### 2.1 Afficher : HUD personnalisé, pages, barres natives

- **HUD personnalisé.** `HudManager.addCustomHud(playerRef, hud)` affiche, `removeCustomHud(playerRef, key)` retire (HY `server/core/entity/entities/player/hud/HudManager.java:116-136`). `CustomUIHud.update(clear, builder)` écrit un paquet `CustomHud(key, zOrder, clear, commandes)` par `writeNoCache` (`hud/CustomUIHud.java:31-34`). Ce paquet est compressé (`protocol/packets/interface_/CustomHud.java:18`), sur le canal `Default` (l. 37-39). Hors file d'attente, `PacketHandler.writePacket` fait un `writeAndFlush` immédiat (`server/core/io/PacketHandler.java:256-276`).
  - Rien dans le serveur ne limite la cadence. Un HUD ne prend ni la souris ni le clavier : le joueur garde caméra et clics. Le seul HUD vanilla (`SpectatingHud`) est statique, et HyLens rafraîchit le sien toutes les 0,5 s (`plugin-b-api.md` § 37). Ce que coûte au client un rafraîchissement à 20 ou 30 Hz, et s'il le lisse, reste **[in-game]**.
  - Coût réseau d'une mise à jour de 3 ou 4 `set` (sélecteur et valeur BSON, compressés) : de l'ordre de 100 octets, soit environ 2 Ko/s par pêcheur à 20 Hz (estimation de notre part, non mesurée).
- **Ce qu'on peut changer à chaud.** `UICommandBuilder.set` accepte chaînes, `Message`, booléens, nombres et `Value` (`server/core/ui/builder/UICommandBuilder.java:89-131`). `setObject` accepte `Area`, `ItemGridSlot`, `ItemStack`, `LocalizableString`, `PatchStyle`, `DropdownEntryInfo` et **`Anchor`** (l. 133-141, table l. 183-189). `Anchor` a `Left`, `Right`, `Top`, `Bottom`, `Height`, `Width`… (`server/core/ui/Anchor.java:8-18`). Le jeu déplace ainsi des marqueurs par le serveur : `MemoriesPage` pose `anchor.setLeft(Value.of(left))` puis `setObject(selector + ".Anchor", anchor)` (`builtin/adventure/memories/page/MemoriesPage.java:216-220`). Déplacer la zone verte et le poisson revient donc à écrire `#Zone.Anchor` et `#Fish.Anchor` ; une jauge se remplit par `#Meter.Value`.
- **Éléments natifs réutilisables** (zip `Common/UI/Custom/Common.ui`) :
  - `@ProgressBar` (l. 1025-1035, `Value`, textures `Common/ProgressBar*.png`, 284 × 6 par défaut), utilisé par `Pages/PrefabEditorSaveSettings.ui:305` et `Pages/Memories/MemoriesCategoryPanel.ui:105` ;
  - `@CircularProgressBar` (l. 1007-1016, `Value`, `MaskTexturePath`) ;
  - `Sprite` animé **par le client** (`@DefaultSpinner`, l. 611-617 : `Frame (…, Count: 72)`, `FramesPerSecond: 30`) ;
  - `TimerLabel` (compte à rebours côté client, `Hud/TimeLeft.ui:16-19`).
  - Aucun `.ui` vanilla ne montre une barre verticale ni une zone de timing : la colonne de Stardew se ferait en `Group` dont on pose `Anchor.Top`. Que `ProgressBar` sache se remplir à la verticale reste **[in-game]**.
- **Barre de charge de `Charging`.** `DisplayProgress` (vrai par défaut, `ChargingInteraction.java:159`, envoyé au client l. 402) : le client dessine sa propre barre de charge, sans zone ni repère. Le serveur ne la pilote pas.
- **Barres natives de stats.** `HudComponent` a `Stamina`, `Health`, `Mana`, `Oxygen` et `BossBar` (`protocol/packets/interface_/HudComponent.java:24-31`). `EntityStatMap.subtractStatValue(index, montant)` (`server/core/modules/entitystats/EntityStatMap.java:253`) et `DefaultEntityStatTypes.getStamina()` (`entitystats/asset/DefaultEntityStatTypes.java:21`) permettent de faire payer le moulinet en endurance, comme Valheim, et la barre d'endurance s'affiche sans UI à nous.
  - L'endurance vanilla (zip `Server/Entity/Stats/Stamina.json`) : 10 au maximum, minimum −4, régénération +0,3 toutes les 0,1 s (3/s), sauf pendant `StaminaRegenDelay`, une garde (`Wielding`), un sprint ou un vol plané. Notre `Charging` n'est pas un `Wielding` : la régénération continue pendant qu'on mouline, sauf si on pose `StaminaRegenDelay` (**[in-game]**).
  - `UpdateBossBar(entityNetworkId, nom, hide, hardMode)` (`protocol/packets/interface_/UpdateBossBar.java:15-27`, utilisé par `builtin/encountermanager/EncounterBossBarState.java:70`) affiche la barre de vie d'une entité. Ce que le client lit pour la remplir est **[in-game]**.
- **Pages.** Le paquet `CustomPage` n'a que `key`, `isInitial`, `clear`, `lifetime`, `commands`, `eventBindings` (`protocol/packets/interface_/CustomPage.java:24-33`) : aucun champ pour garder la souris au jeu. Une page prend donc le curseur, comme toutes nos fenêtres ; **[in-game]** pour s'en assurer.
  - Ce qu'une page apporte : une liaison `ValueChanged` sur un `Slider` ou un `FloatSlider` envoie la valeur pendant qu'on le glisse, sans verrouiller l'interface (`locksInterface = false`, `protocol/packets/interface_/CustomUIEventBinding.java:24`). C'est ce que fait `EntitySpawnPage`, avec en plus `MouseButtonReleased` au lâcher (`server/npc/pages/EntitySpawnPage.java:120-140`).
  - La poignée d'un curseur a une taille réglable (`SliderStyle(… HandleWidth: 16, HandleHeight: 16)`, zip `Common.ui:959-967`) : une poignée large peut faire la zone verte. Un type `KeyDown` existe aussi (`CustomUIEventBindingType.java:17`), sans usage vanilla.
  - La cadence des `ValueChanged` pendant un glissement est **[in-game]**.
  - HyBlockUI ne fournit que des pages (`InteractiveCustomUIPage`, `blockui/src/main/java/dev/hyblockui/api/`), pas de HUD. Le seul HUD du dépôt est `hylens/plugin/src/main/java/dev/hylens/plugin/watch/WatchHud.java`.
- **UI Noesis (XAML) côté serveur.** `HudManager.dev_registerWidget` lève tant que la propriété système `hytale.serverside_ui_preview` n'est pas posée (`server/core/modules/ui/UIModule.java:20, 28-34` : « still in development »). Inutilisable aujourd'hui.

### 2.2 Savoir si le joueur tient le bouton

- **`Charging` + `AllowIndefiniteHold`, le meilleur signal.** Tant que le client tient le bouton, il synchronise `chargeValue = −1` (`CHARGING_HELD`, `ChargingInteraction.java:199`) ; le serveur garde l'interaction `NotFinished` (l. 267-269). Au lâcher arrive la durée tenue, mesurée par le client, puis le saut vers `Next` (l. 271-284). `−2` signale une annulation (l. 270, `CHARGING_CANCELED`), par exemple un autre clic avec `CancelOnOtherClick`, vrai par défaut (l. 93). L'interaction attend les données du client (`WaitForDataFrom.Client`, l. 222).
  - Le serveur vérifie la durée annoncée contre l'horloge, avec 0,25 s de tolérance, mais ne fait qu'en journaliser l'excès (`validateChargeValue`, l. 460-484).
  - Le motif vanilla pour lire « il tient » à chaque tick : `ChargingCondition` passe par `InteractionManager.forEachInteraction` et teste `interaction instanceof ChargingInteraction` (`server/core/modules/entity/condition/ChargingCondition.java:43-55`). `forEachInteraction` parcourt l'opération courante de chaque chaîne et de ses fourches (`server/core/entity/InteractionManager.java:1530-1549`). La garde au bouclier (`WieldingInteraction extends ChargingInteraction`, `config/client/WieldingInteraction.java:46`) repose sur ce mécanisme.
  - **Latence** : `SyncInteractionChains` est mis en file (1 000 au plus) puis traité au tick du monde (`server/core/io/handlers/game/GamePacketHandler.java:919-940`). L'appui et le lâcher arrivent donc avec RTT/2, plus au plus un tick du monde (33 ms à 30 ticks/s, `plugin-b-api.md` § 41).
  - **Rappui** : chaque appui est une nouvelle chaîne. Sans `Cooldown`, une racine `Secondary` attend 0,35 s (`InteractionTypeUtils.DEFAULT_COOLDOWN`, `config/InteractionTypeUtils.java:60-66`, appliqué par `InteractionManager.isOnCooldown`, l. 1324-1383). Une racine accepte une clé `Cooldown` (`config/RootInteraction.java:43-92, 116`) : `{"Cooldown": 0}` sur la racine `Secondary` de la canne lèverait ce délai (**[in-game]** : un relâcher-rappui rapide).
  - Notre chaîne actuelle (`angler/plugin/src/main/resources/Server/Item/Items/HyAngler/HyAngler_Rod_Crude.json`) commence par `HyAngler_Cast` avec `Reel: true`, qui échoue (pas de charge) quand un lancer est en cours (`CastInteraction.java:24-25, 67`). Pendant un combat, il laisserait passer la chaîne vers `Charging` : tenir le clic droit, c'est mouliner. La barre de charge native et l'animation `CastCharging` s'afficheraient alors aussi. On les évite par un **état d'objet** « combat », qui a ses propres `Interactions` : `Charging` sans `DisplayProgress`, avec `ItemAnimationId: ReelFight`. Les états d'objet sont la clé `State` (`server/core/asset/type/item/config/Item.java:1316`, mode `INJECT_PARENT`), posés par `ItemStack.withState(state)` (`server/core/inventory/ItemStack.java:219-226`), comme `/item state` (`command/commands/player/inventory/ItemStateCommand.java:45`). Changer l'objet tenu peut annuler la chaîne en cours (`OnItemChangeBehavior`) : on le change au ferrage, avant tout appui (**[in-game]**).
- **`MouseInteraction` / `PlayerMouseButtonEvent`.** Le paquet 111 porte bouton, `Pressed`/`Released` et position d'écran (`protocol/packets/player/MouseInteraction.java`, `protocol/MouseButtonEvent.java`, `MouseButtonState.java:6-7`). Le serveur le traduit en `PlayerMouseButtonEvent` (`server/core/modules/interaction/InteractionModule.java:409-497`). Le seul usage vanilla, `CameraDemo`, l'écoute derrière une caméra serveur à curseur (`server/core/command/commands/player/camera/CameraDemo.java:47, 67, 160`). Quand le client l'envoie en jeu normal n'est pas connu (déjà noté dans `build-goggles-and-wand.md` l. 220) : **[in-game]**, et à ne pas choisir sans essai.
- **Mouvements, regard, touches de déplacement.** `ClientMovement` (`protocol/packets/player/ClientMovement.java:29-46`) porte `movementStates` (dont `crouching`, `jumping`, `sprinting`, `protocol/MovementStates.java:17-39`), `lookOrientation` et `wishMovement` (les touches de déplacement voulues). Le serveur les met en file sur `PlayerInput` (`SetMovementStates`, `SetHead`, `WishMovement` ; `GamePacketHandler.java:300-370`), sur le fil du monde. La cadence d'envoi est celle du client (**[in-game]**).
  - Ce sont des signaux continus : tenir Maj (accroupi), viser avec la caméra (Palia), tirer à gauche ou à droite avec Q/D (Sea of Thieves). Mais ils déplacent aussi le joueur.
- **Ping.** `PacketHandler.getPingInfo(PongType.Tick).getPingMetricSet().getAverage(0)` donne le RTT moyen, en microsecondes (`PacketHandler.java:316-323`, `TIME_UNIT` l. 605), mesuré par un ping chaque seconde (l. 325-331). Il permet de compenser la latence d'un jugement au temps près.

### 2.3 Ce qui sert déjà au combat

- **Animations** dans notre pack (`angler/plugin/src/main/resources/Server/Item/Animations/HyAngler_Rod.json`) : `FightLight`, `FightHeavy`, `FightPump`, `ReelFight` (en boucle, 3e et 1re personne), plus `Hook`, `Catch`, `Escape`, `Snap`. Le serveur les joue par `AnimationUtils.playAnimation` (spec l. 202, `fishing-hytale.md` § 7.3).
- **Sons** (zip `Server/Audio/SoundEvents/`) :
  - moulinet : `SFX/Tools/Hookshot/SFX_Tool_Hookshot_Reel` et `_Local` ;
  - tension, bois ou corde qui craque : `SFX/Weapons/Bow/SFX_Bow_T1_Draw`, `SFX_Bow_T2_Draw` (et `_Local`), `Environments/Emitters/Tree/SFX_Emit_Tree_Creak` ;
  - casse : `BlockSounds/Rope/SFX_Rope_Break` ;
  - poisson qui fuit : `SFX/NPC/Ocean/Fish/SFX_Fish_Flee` ;
  - éclaboussures : `BlockSounds/Water/SFX_Water_MoveIn`, `SFX_Water_MoveOut` (`fishing-hytale.md` § 1.6, § 5.12).
  - Aucun son d'UI de réussite ou d'échec propre à un mini-jeu. Les plus proches sont `SFX/Crafting/SFX_Generic_Crafting_Failed` et `SFX/Player/Sleep/SFX_Sleep_Success`. `SoundUtil.playSoundEvent2dToPlayer` joue un son pour le seul joueur (`fishing-hytale.md` § 5.12).
- **Sensibilité de la souris** : `Charging.MouseSensitivityAdjustmentTarget` et `Duration` (`ChargingInteraction.java:174`) ralentissent la caméra pendant qu'on tient le bouton. C'est utile si l'on vise avec la caméra.

### 2.4 La contrainte qui décide de tout : pas de code client

Tide, Stardew, Fisch et Palia simulent le mini-jeu chez le client. Chez nous, la seule animation libre côté client est un `Sprite` à `FramesPerSecond` ou un `TimerLabel`. Le reste suit une boucle serveur : l'entrée arrive après RTT/2, le serveur calcule et redessine, et le dessin arrive après encore RTT/2. Entre l'appui et la barre qui bouge, il s'écoule **un RTT plus un tick** : à peu près rien en solo (serveur local), environ 150 à 200 ms à 100 ms de ping (estimation). Une barre de Stardew réglée à l'identique, à 900 px/s², devient alors molle et en retard. Les designs ci-dessous sont classés selon leur tolérance à cette latence.

## 3. Trois designs pour Hytale (classés)

Tous ajoutent au cœur un état **`FIGHTING`** entre la touche et la prise. `CastSession.reel()` pendant `BITING` passe en `FIGHTING` quand le mini-jeu est actif, et prend le poisson tout de suite sinon (option « désactivé » de Tide, et toujours pour un PNJ). L'état tient un `FightSession` pur (dans `dev.hyangler.core.fight`), nourri à chaque tick du cœur par un nouveau champ `CastInputs.reeling` (« le bouton est tenu », lu par le motif `ChargingCondition`).

Ses sorties, conformément à CLAUDE.md § 4 :
- jauge pleine : `CAUGHT` ;
- jauge vide : `ESCAPED` ;
- tension au maximum : `BROKEN` (déjà dans `CastEnd`, usure 0) ;
- ligne trop longue : `BROKEN`, comme aujourd'hui ;
- borne de durée : `ESCAPED` ;
- `cancel()`.

Le tirage de la prise doit avoir lieu **au ferrage** et non plus à la fin, pour que la difficulté et le comportement du poisson (données de `FishAsset`, à ajouter, sur le modèle de Tide `strength`, `speed` et `behavior`) règlent le combat. Les réglages vont dans `config.json` de HyAngler : `Minigame` (activé ou non) et un multiplicateur de difficulté, comme Tide.

### 3.1 Recommandé : tension et endurance du poisson (Valheim, Sea of Thieves, Palia)

- **Le jeu** :
  - le poisson alterne des phases « tire » et « repos » (Valheim, à-coups), dont la longueur et la force viennent de sa difficulté et de son comportement ;
  - tenir le clic droit mouline : la distance baisse, mais si le poisson tire, la **tension** monte, et la ligne casse au maximum (Sea of Thieves) ;
  - lâcher fait baisser la tension et laisse filer un peu de ligne ;
  - la « zone verte » est celle d'une barre de tension, où il faut rester entre « trop mou » (le poisson se décroche lentement) et « trop tendu ».
  - Une prise parfaite (jamais dans le rouge) peut donner un bonus de rareté ou de taille, à décider.
- **Ce qu'il faut** :
  - un HUD (`CustomUIHud`) avec deux `ProgressBar`, tension et distance, et une teinte (`PatchStyle`) verte, jaune ou rouge, mis à jour à 10 Hz ;
  - l'état de canne « combat » et `{"Cooldown": 0}` (§ 2.2) ;
  - les animations `FightLight` et `FightHeavy` selon la tension, `ReelFight` quand on mouline, `FightPump` au repos ; les sons du moulinet, du craquement et de la casse (§ 2.3) ; le bouchon secoué dans l'eau par le serveur ;
  - au choix, le coût du moulinet en endurance (barre native, `subtractStatValue`).
- **Pourquoi il tolère la latence** : les décisions se prennent sur des phases de 0,5 à 2 s, et la tension se règle par une hystérésis, non par une poursuite au pixel près. Une marge de quelques ticks absorbe le RTT.
- **Risques** :
  - lisibilité sans retour immédiat : animations et sons doivent annoncer « il tire » avant que la tension ne monte ;
  - cadence du HUD et coût client **[in-game]** ;
  - annulation de la chaîne au changement d'état de l'objet **[in-game]**.
- **Dans `CastSession`** : `FIGHTING` délègue à `FightSession.tick(reeling)`, qui tient la phase du poisson, la tension, la distance et un compte de ticks borné. Tout est en ticks de 20/s ; c'est entièrement testable en TDD.

### 3.2 Fidèle à Stardew : la barre verte sous gravité, en HUD

- **Le jeu** : la colonne de Stardew (§ 1.1), avec la zone verte du joueur, le poisson et la jauge de prise. Tenir le clic droit fait monter la zone, la lâcher la fait tomber. Mêmes formules, converties en ticks : gravité 2,25 px/tick² (×0,6 sur le poisson), jauge +0,006 et −0,009 par tick. Les poissons suivent les comportements `mixed`, `dart`, `smooth`, `sinker`, `floater` et une difficulté de 5 à 110.
- **Ce qu'il faut** :
  - un HUD à 20 Hz, qui écrit `#Zone.Anchor` (`Top`), `#Fish.Anchor` (`Top`) et `#Meter.Value` (§ 2.1) ;
  - le même signal de bouton que le design 3.1, l'état de canne « combat » et `Cooldown 0` ;
  - un réglage qui ralentit la physique quand le ping est élevé.
- **Risques** : c'est le plus exposé à la latence (§ 2.4). Pas d'interpolation connue des `Anchor` par le client : à 20 Hz, la barre avance par sauts (**[in-game]**). Il faut garder la jauge et la difficulté réglables, et une option « désactivé ».
- **Dans `CastSession`** : le même `FIGHTING` ; `FightSession` porte la physique de `BobberBar`, avec des positions en px virtuels de la colonne de 568.

### 3.3 Le plus simple : un clic chronométré (Tide)

- **Le jeu** : une aiguille va et vient sur une barre avec une zone verte (`area = 1 − force`, vitesse et comportement du poisson). Un clic dans la zone prend le poisson, à moins de 10 % du centre la prise est parfaite, sinon le poisson s'échappe. Au bout de 80 ticks, le mini-jeu expire.
- **Ce qu'il faut** : un HUD dont le serveur déplace l'aiguille (`Anchor.Left`), et le clic, qui est l'appui de la chaîne. Le serveur juge la position de l'aiguille **RTT plus tôt** (ping du § 2.2) pour annuler le décalage entre ce que le joueur a vu et quand son clic arrive. Variante : une aiguille animée par le client (`Sprite` à `FramesPerSecond`, une feuille d'images du balayage), que le serveur juge à partir de l'heure d'envoi du HUD et du RTT. Elle a besoin d'essais : quand le sprite démarre-t-il, et boucle-t-il ? (**[in-game]**).
- **Risques** : peu d'entrées, mais un jugement au temps près sur un réseau qui varie. C'est un mini-jeu de réflexe, que la spec range parmi les choix possibles (« barre de timing ») mais qui est loin du « rester dans le vert » demandé.
- **Dans `CastSession`** : `FIGHTING` attend un seul `reel()`, jugé par `FightSession.judge(ticks depuis le début)`.

### 3.4 Écartés pour l'instant

- **Page avec curseur** (`FloatSlider` dont la poignée large fait la zone verte, `ValueChanged` sans verrou, § 2.1). Le contrôle du joueur s'affiche sans latence, mais la page prend la souris (caméra figée, **[in-game]**), Échap la ferme, et la cadence des événements est inconnue.
- **Visée à la Palia** : garder le bouchon, que le serveur déplace, au centre de la vue (`lookOrientation`). La caméra en 3e personne et le regard de la tête ne coïncident pas forcément, et le déplacement à la main d'un projectile figé reste **[in-game]** (`fishing-hytale.md` § 5.2).
- **`MouseInteraction`** : envoi par le client inconnu hors caméra à curseur.

## 4. Incertitudes (toutes **[in-game]**)

1. Cadence acceptable et coût client d'un `CustomHud` rafraîchi à 10, 20 ou 30 Hz ; lissage éventuel d'un `Anchor` ou d'une `Value` entre deux mises à jour.
2. Délai réel entre l'appui ou le lâcher d'un `Charging` et sa vue par le serveur, en solo puis à distance.
3. Rappui rapide après un lâcher, avec ou sans `{"Cooldown": 0}` sur la racine `Secondary`.
4. Effet de `withState` sur l'objet tenu pendant une chaîne (`OnItemChangeBehavior`), et masquage de la barre de charge native par `DisplayProgress: false`.
5. Une page prend-elle forcément le curseur ? Cadence des `ValueChanged` d'un curseur glissé.
6. Une `ProgressBar` verticale ; un `Sprite` qui joue une seule fois, et depuis quand.
7. Ce que remplit la barre de boss (`UpdateBossBar`) : la vie de l'entité, ou autre chose.
