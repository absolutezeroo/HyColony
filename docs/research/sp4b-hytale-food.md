# SP4b : la nourriture côté Hytale (objets, cuisson, PNJ, sièges)

Recherche du 2026-10-01, pour porter la faim, la nourriture et le bonheur des citoyens de MineColonies.

Sources :
- Hytale 0.7.0-pre.4 : sources décompilées `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `HY/`).
- Assets : `~/AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip` (abrégé `zip:`). Il a la même taille (3 808 952 006 octets) que `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.4-Assets.zip`. Le `server/Assets.zip` du dépôt n'a **pas** été lu.
- MineColonies : `sources/minecolonies/src/main/java/com/minecolonies/` (abrégé `MC/`).

Les tableaux viennent d'un parcours de tous les `zip:Server/Item/Items/**/*.json`, avec la chaîne `Parent` résolue selon les règles de `Item` (§ 1.1).

## Réponse courte

1. **Pas de faim dans Hytale.** Les `EntityStatType` sont `Ammo`, `DeployablePreview`, `GlidingActive`, `Health`, `Immunity`, `MagicCharges`, `Mana`, `Oxygen`, `SignatureCharges`, `SignatureEnergy`, `Stamina` et `StaminaRegenDelay` (`zip:Server/Entity/Stats/*.json`). Aucune ne s'appelle `Hunger`, `Saturation` ou `Satiety`, et aucune source décompilée n'en parle (grep `hunger|saturation|satiety` : seul `FluidFX.ColorsSaturation` sort). Manger ne fait que **soigner** et donner des **bonus** temporaires.
2. **Manger** = interaction `Secondary` (clic droit) `Root_Secondary_Consume_Food_T1/T2/T3`. Elle charge 2,5 s, retire 1 objet, puis lance la variable `Effect` de l'objet. Le champ `"Consumable"` n'est qu'un drapeau envoyé au client (`HY/server/core/asset/type/item/config/Item.java:378-384`, `786`) : le serveur ne s'en sert pas.
3. **Rien ne ressemble à la nutrition de MC.** Une nourriture Hytale a un **palier de soin** : soin instantané de 5 % (T1), 10 % (pain et T2) ou 15 % (T3) de la vie max. Les plats cuisinés ajoutent une **régénération** de 360 s (`HealthRegen_Buff_T1/T2/T3`) et un buff de vie max (viandes) ou d'endurance max (fruits et légumes). Le mapping vers `nutrition`/`saturation` de MC doit donc être une **table HyColony** (id-map), dérivée de ces paliers.
4. **Cuisson** : la seule cuisson « à la MC » (temps + combustible) est le **feu de camp** (`Bench_Campfire`, banc `Processing` d'id `Campfire`). Il a 3 recettes : `res:Meats` → `Food_Wildmeat_Cooked`, `res:Vegetables` → `Food_Vegetable_Cooked`, `Food_Fish_Raw` → `Food_Fish_Grilled`, 2 s chacune. Le four (`Furnace`) ne cuit aucune nourriture. Le plan de cuisine (`Cookingbench`) est un banc de **fabrication** (`Crafting`) : pain, tartes, brochettes, salades, farine, pâte, sel, épices, fromage.
5. **Nourriture nocive** : seuls les 6 champignons lumineux (`Plant_Crop_Mushroom_Glowing_*`) empoisonnent, avec `Poison` 2 s. Rien ne pourrit. La viande crue n'a **aucun** effet négatif, seulement l'animation `ConsumeDisgust`.
6. **PNJ** : l'animation `Consume` du jeu d'animations d'objet `Item` se joue comme le `PLANT` actuel (`AnimationUtils.playAnimation(ref, Action, "Item", "Consume", store)`). Pour soigner : `EntityStatMap.addStatValue(DefaultEntityStatTypes.getHealth(), n)`. Pour voir les dégâts : un `DamageEventSystem` dans le groupe *inspect*, mais le rôle citoyen est `Invulnerable`. Pour ralentir : un effet avec `ApplicationEffects.HorizontalSpeedMultiplier` < 1 (`Slow` = 0,5), ce qui marche déjà avec `CitizenSpeed`. Particules : `Food_Eat`, et `Hungry`, `Hearts`, `Angry` dans `Server/Particles/NPC/Emotions/`.
7. **Sièges** : 32 meubles ont des `Seats` (1 place, 2 pour les bancs). `BlockMountAPI.mountOnBlock` les traite comme les lits (`BlockMountType.Seat`). Le drapeau `MovementStates.sitting` existe.
8. **Nourrir à la main** : `CitizenUseSystem` reçoit `UseEntityEvent.Pre` (touche *Use*). L'événement porte l'`InteractionContext`, qui donne l'objet tenu et son conteneur (`getHeldItem`, `getHeldItemContainer`, `getHeldItemSlot`). On peut donc lire l'objet et en retirer un. **[in-game]**

## 1. Inventaire des objets comestibles

### 1.1 Règles de résolution (héritage `Parent`)

- `BuilderCodec.decodeAndInherit` copie d'abord **tous** les champs hérités du parent, puis décode ceux de l'enfant (`HY/codec/builder/BuilderCodec.java:452-463`).
- `Interactions` et `InteractionVars` **fusionnent** clé par clé avec le parent : `MapUtil.combineUnmodifiable(parent, enfant)` (`Item.java:443-451`, `478-485` ; `HY/common/util/MapUtil.java:11-16`). Un `"Interactions": {}` dans l'enfant **garde donc** l'interaction du parent.
- `Quality` n'est **pas** héritée (`append`, pas `appendInherited`, `Item.java:159`). Sans `Quality`, l'index vaut 0, la qualité `Default` (valeur −1, `HY/.../item/config/ItemQuality.java:138-151`, `Item.java:1312-1318`).
- `MaxStack` absent partout donne 100, ou 1 pour un outil, une arme ou une armure (`Item.java:1277-1283`).
- Valeurs de qualité (`zip:Server/Item/Qualities/*.json`, `QualityValue`) : Junk 0, Common 1, Uncommon 2, Rare 3, Epic 4, Legendary 5 (Technical 8, Tool 9, Template, Debug et Developer 10).
- API d'exécution : `Item.getInteractions()` (`Map<InteractionType,String>`, l. 1194), `getInteractionVars()` (`Map<String,String>`, l. 1206 ; valeurs = ids de `RootInteraction`), `getResourceTypes()` (l. 1182), `getCategories()` (l. 1158), `getQualityIndex()` (l. 1109), `getMaxStack()` (l. 1105), `getData().getRawTags()` (`HY/assetstore/AssetExtraInfo.java:160`).

### 1.2 Chaîne de consommation (joueur)

- `Root_Secondary_Consume_Food_T1/T2/T3` (`zip:Server/Item/RootInteractions/Consumables/`) : `RequireNewClick`, puis `Condition_Consume_Food_Tn`. Celle-ci exige le mode `Adventure` et pas accroupi, puis va à `Consume_Charge_Food_Tn` ; sinon `Block_Secondary` (`zip:Server/Item/Interactions/Consumables/Condition_Consume_Food_T1.json`).
- `Consume_Charge_Food_Tn` remplace les variables `ConsumeSFX` puis `Consume_Charge`, qui vaut par défaut `Consume_Charge_Food_Tn_Inner`.
- `Consume_Charge_Food_T1_Inner`, `T2_Inner` et `T3_Inner` sont **identiques** (`.../Consume_Charge_Food/*.json`) :
  - `Charging` qui ne casse pas sur un coup (`FailOnDamage: false`), animation d'objet `Consume`, vitesse horizontale × 0,75 ;
  - à **2,5 s** : `ModifyInventory AdjustHeldItemQuantity -1`, puis les variables `ConsumedSFX`, `Effect`, et une pause de 0,2 s.
- Les paliers T1/T2/T3 du `Root` ne changent donc **rien** au comportement : seule la variable `Effect` de l'objet compte.
- Les objets remplacent souvent `Consume_Charge` par `{ "Parent": "Consume_Charge_Food_T1_Inner", "Effects": { "ItemAnimationId": …, "Particles": [{ "SystemId": "Food_Eat", "Color": …, "TargetNodeName": "Mouth" }] } }`. Les animations sont `Consume` (défaut), `ConsumeSide`, `ConsumeDisgust` et `ConsumeDisgustSide`.
- Variante sans palier : `Consume_Charge` (`zip:.../Interactions/Consumables/Consume_Charge.json`), même chose mais à **4,0 s**. Elle sert aux champignons lumineux.
- Boissons : `Root_Secondary_Consume_Drink` → `Condition_Consume_Drink` → `Food_EffectCondition_Drink` (ne boit pas si `Food_Health_Restore_Tiny` est actif) → `Consume_Charge_Durability`. Celle-ci charge **3,0 s**, retire 1 de durabilité (variable `DurabilityModify`), puis lance `Effect`.

### 1.3 Effets de nourriture (`zip:Server/Entity/Effects/Food/**`)

Comment ils s'appliquent (`HY/server/core/entity/effect/ActiveEntityEffect.java:182-230`) :
- les `StatModifiers` (`EntityEffect.java:149-157`, champ `getEntityStats()` l. 530) sont ajoutés tous les `DamageCalculatorCooldown` secondes pendant `Duration`, ou **une seule fois** si ce délai vaut 0 ;
- `ValueType: Percent` = montant × (max − min) / 100 (`HY/server/core/modules/entitystats/EntityStatMap.java:998-1004`) ;
- les `RawStatModifiers` sont des modificateurs de stat (ici de `Max`) tant que l'effet dure.

| Effet | Contenu | Sens |
|---|---|---|
| `Food_Instant_Heal_T1` | Health 5, Percent, 0,1 s, délai 0 | +5 % de la vie max, une fois |
| `Food_Instant_Heal_Bread` | Health 10, Percent | +10 % |
| `Food_Instant_Heal_T2` | Health 10, Percent | +10 % |
| `Food_Instant_Heal_T3` | Health 15, Percent | +15 % |
| `HealthRegen_Buff_Raw` | Health 1 %, toutes les 2 s, 60 s | régénération de 30 % au total |
| `HealthRegen_Buff_T1` | 1 %, toutes les 2 s, 360 s | 180 % |
| `HealthRegen_Buff_T2` | 1,5 %, toutes les 2 s, 360 s | 270 % |
| `HealthRegen_Buff_T3` | 2 %, toutes les 2 s, 360 s | 360 % |
| `Meat_Buff_T1/T2/T3` | vie max × 1,05 / 1,10 / 1,15 pendant 360 s ; T3 : +5 % de résistance Physical et Projectile | buff de viande |
| `FruitVeggie_Buff_T1/T2/T3` | endurance max × 1,1 / 1,2 / 1,3, 360 s ; T2 : +0,025 d'endurance par 0,1 s, T3 : +0,05 | buff de fruits et légumes |
| `Food_Health_Regen_Small` | Health +1 (absolu) toutes les 2 s, 120 s | défaut de `Template_Food` |
| `Food_Health_Restore_Small` | Health +10 (absolu), une fois | eau (chope, chope à bière) |

Les interactions `HealthRegen_TierCheck_*`, `Meat_TierCheck_*` et `FruitVeggie_TierCheck_*` (`zip:Server/Item/Interactions/Consumables/Food/`) n'appliquent un buff que si aucun buff de palier supérieur n'est actif. Elles retirent ceux de palier inférieur. Le cru (`Raw`) ne passe que si aucun T1, T2 ou T3 n'est actif.

Autres familles présentes, mais utilisées par **aucun** objet comestible : `Food_Health_Boost_*`, `Food_Stamina_*` et `_Deprecated/Food_Buff_*`.

### 1.4 Ingrédients crus

« Effet » = contenu de la variable `Effect` après résolution. Catégorie `Items.Foods` partout. `Consumable: true` sauf mention.

| Id | Fichier `zip:Server/Item/Items/…` | Quality | MaxStack | ResourceTypes | Tags | Effet | Anim. | Devient (feu de camp) |
|---|---|---|---|---|---|---|---|---|
| `Food_Beef_Raw` | `Food/Food_Beef_Raw.json` | Common | 25 | Meats | Type=Food | `Food_Instant_Heal_T1` | ConsumeDisgust | `Food_Wildmeat_Cooked` |
| `Food_Pork_Raw` | `Food/Food_Pork_Raw.json` | Common | 25 | Meats | Type=Food | idem | ConsumeDisgust | `Food_Wildmeat_Cooked` |
| `Food_Chicken_Raw` | `Food/Food_Chicken_Raw.json` | Common | 25 | Meats | Type=Food | idem | ConsumeDisgustSide | `Food_Wildmeat_Cooked` |
| `Food_Wildmeat_Raw` | `Food/Food_Wildmeat_Raw.json` | Common | 25 | Meats | Type=Food | idem | ConsumeDisgustSide | `Food_Wildmeat_Cooked` |
| `Food_Fish_Raw` | `Food/Food_Fish_Raw.json` | Common | 25 | Foods | Type=Food | idem | ConsumeDisgustSide | `Food_Fish_Grilled` |
| `Food_Egg` | `Food/Food_Egg.json` | Common | 25 | Foods | Type=Food | idem | ConsumeDisgust | (aucune) |
| `Plant_Crop_{Aubergine,Carrot,Cauliflower,Chilli,Corn,Lettuce,Onion,Potato,Pumpkin,Rice,Tomato,Turnip}_Item` (12) | `Plant/Crop/<X>/…_Item.json`, parent `Template_Crop_Item` | Common | 100 (défaut) | Vegetables | Type=Plant, Family=Crop | `Food_Instant_Heal_T1` + `HealthRegen_TierCheck_Raw` | Consume (maïs : ConsumeSide + `Food_Eat`) | `Food_Vegetable_Cooked` |
| `Plant_Fruit_{Apple,Azure,Berries_Red,Coconut,Mango,Pinkberry,Poison,Spiral,Windwillow}` (9) | `Plant/Fruit/…`, parent `Template_Fruit` | Common | 100 (défaut) | Fruits | Type=Plant, Family=Fruit | `Food_Instant_Heal_T1` + `HealthRegen_TierCheck_Raw` | Consume + `Food_Eat` | (aucune) |
| `Plant_Crop_Mushroom_Glowing_{Blue,Green,Orange,Purple,Red,Violet}` (6) | `Plant/Crop/…`, sans parent | (aucune → Default) | 100 | Vegetables, Mushrooms | — | **`Poison`** (durée 2 s, `Extend`), interaction en ligne → `Consume_Charge` (4 s) | Consume | `Food_Vegetable_Cooked` |

Remarques :
- **`Plant_Fruit_Poison`** (« Poison Tree Fruit ») n'empoisonne **pas** : il hérite de l'effet T1 de `Template_Fruit`. Sa description en-US le confirme : « Instantly restores 5% health… Health Regen I, 1:00 » (`zip:Server/Languages/en-US/server.lang`).
- Ne se mangent **pas** (pas d'interaction de consommation) : les autres champignons (`Plant_Crop_Mushroom_Cap_*`, `Common_*`, `Flatcap_*`, `Shelve_*`, `Block_*`), qui sont pourtant des `Vegetables` et/ou `Mushrooms`, donc cuisables ou utilisables en recette. Ne se mangent pas non plus le blé (`Plant_Crop_Wheat_Item`), le coton et les graines.
- Les poissons vivants `Fish_*_Item` (30) ne se mangent pas. Ce sont des objets « SpawnNPC » (`Fish/Template_Fish_Item.json`), avec les ResourceTypes `Fish` et `Fish_Uncommon/Rare/Epic/Legendary`. Le plan de cuisine les découpe en `Food_Fish_Raw` : ×1, ×2, ×4, ×8 ou ×16 selon la rareté.
- `Food_Fish_Raw_Uncommon/Rare/Epic/Legendary` sont des objets porteurs de recette (sortie `Food_Fish_Raw` ou eux-mêmes ×2). Ils n'ont ni `Quality` ni nom en-US.

### 1.5 Plats préparés et cuits

| Id | Quality | ItemLevel | Root | Effet | Anim. | Fabrication |
|---|---|---|---|---|---|---|
| `Food_Wildmeat_Cooked` | Common | 10 | T1 | Heal_T1 + `HealthRegen_TierCheck_T1` + `Meat_TierCheck_T1` | ConsumeSide | Campfire : `res:Meats`×1, 2 s |
| `Food_Fish_Grilled` | Common | 10 | T1 | Heal_T1 + Regen T1 + Meat T1 | ConsumeSide | Campfire : `Food_Fish_Raw`×1, 2 s |
| `Food_Vegetable_Cooked` | Common | 10 | T1 | Heal_T1 + Regen T1 + `FruitVeggie_TierCheck_T1` | ConsumeSide | Campfire : `res:Vegetables`×1, 2 s |
| `Food_Bread` | Uncommon | 7 | T2 | `Food_Instant_Heal_Bread` (10 %) | Consume | Cookingbench[Baked] : `Ingredient_Dough` + `res:Fuel`×3, 5 s |
| `Food_Cheese` | Uncommon | 8 | T3 | Heal_T2 | Consume | Cookingbench[Ingredients] : `res:Milk_Bucket`×1 → fromage + **`Container_Bucket`**, 5 s |
| `Food_Popcorn` | Uncommon | 10 | T3 | Heal_T2 | Consume | Cookingbench[Baked] : maïs ×2 + sel + `res:Fuel`×3, 5 s |
| `Food_Kebab_Fruit` | Uncommon | 2 | T2 | Heal_T2 + Regen T2 + FruitVeggie T2 | ConsumeSide | Cookingbench[Prepared] : `Ingredient_Stick` + `res:Fruits`×4, 1 s |
| `Food_Kebab_Meat` | Uncommon | 3 | T2 | Heal_T2 + Regen T2 + Meat T2 | ConsumeSide | Prepared : bâton + `res:Meats`×4, 1 s |
| `Food_Kebab_Mushroom` | Uncommon | 1 | T2 | Heal_T2 + Regen T2 + FruitVeggie T2 | ConsumeSide | Prepared : bâton + `res:Mushrooms`×3, 1 s |
| `Food_Kebab_Vegetable` | Uncommon | 4 | T2 | Heal_T2 + Regen T2 + FruitVeggie T2 | ConsumeSide | Prepared : bâton + `res:Vegetables`×4, 1 s |
| `Food_Salad_Berry` | Uncommon | 6 | T2 | Heal_T2 + Regen T2 + FruitVeggie T2 | Consume | Prepared : laitue + `Plant_Fruit_Berries_Red`×5, 1 s |
| `Food_Salad_Mushroom` | Uncommon | 5 | T2 | Heal_T2 + Regen T2 + FruitVeggie T2 | Consume | Prepared : laitue + `res:Mushrooms`×3, 1 s |
| `Food_Salad_Caesar` | Rare | 9 | T3 | Heal_T3 + Regen T3 + FruitVeggie T3 | Consume | Prepared : laitue + fromage + `res:Meats` + sel + épices, 1 s |
| `Food_Pie_Apple` | Rare | 11 | T3 | Heal_T3 + Regen T3 + FruitVeggie T3 | Consume | Baked : pâte + pomme ×3 + épices + `res:Fuel`×3, 5 s |
| `Food_Pie_Meat` | Rare | 12 | T3 | Heal_T3 + Regen T3 + **Meat** T3 | Consume | Baked : pâte + `res:Meats` + épices + sel + `res:Fuel`×3, 5 s |
| `Food_Pie_Pumpkin` | Rare | 13 | T3 | Heal_T3 + Regen T3 + FruitVeggie T3 | Consume | Baked : pâte + citrouille + épices + `res:Fuel`×3, 5 s |
| `Food_Candy_Cane` | (aucune) | 10 | T1 | effet en ligne Health +10 (absolu), `Extend` | ConsumeSide | (aucune ; objet saisonnier) |

Tous ces plats sont dans `zip:Server/Item/Items/Food/<Id>.json` : parent `Template_Food`, ou `Food_Pie_Apple` pour les salades et `Food_Pie_Meat`. Ils ont tous MaxStack 25, `ResourceTypes` Foods et Tags Type=Food. Les `Halloween_Basket_*` du même dossier ne se mangent pas.

Palier « qualité » MC (`IMinecoloniesFoodItem.getTier`, `MC/api/items/IMinecoloniesFoodItem.java:12`) : le signal Hytale le plus fiable est la présence de `HealthRegen_TierCheck_T1/T2/T3` dans `Effect`. Le `Root` T1/T2/T3 n'est pas fiable : le fromage et le pop-corn ont un `Root` T3 mais un soin T2 et pas de régénération. La `Quality` suit à peu près : Common = T1, Uncommon = T2, Rare = T3.

### 1.6 Comestibles hors nourriture

- **Ingrédients du plan de cuisine** : `Ingredient_Dough`, `Ingredient_Flour`, `Ingredient_Salt`, `Ingredient_Spices` et `Ingredient_Fishbone` (`zip:Server/Item/Items/Ingredient/`). Ils ont `Parent: Template_Food`, `Consumable: false` et `"Interactions": {}`. Comme l'héritage fusionne les cartes (§ 1.1), ils **gardent** `Secondary = Root_Secondary_Consume_Food_T1` et l'effet `Food_Health_Regen_Small` du modèle. Selon les sources, ils se mangent donc. **[in-game]** À vérifier (le client voit `consumable=false`). Pour MC, ce ne sont pas de la nourriture.
- **Boissons** (états d'objet, ids `*<Objet>_State_<État>`, comme `*Deco_Tankard_State_Filled_Water` dans la recette de `Ingredient_Dough`) :

| Objet / état | Effet | Rend (`BrokenItem`) | MaxStack |
|---|---|---|---|
| `Deco_Mug` / `Filled_Water` | `Food_Health_Restore_Small` (+10 PV) | `Deco_Mug` | 1 |
| `Deco_Tankard` / `Filled_Water` | `Food_Health_Restore_Small` | `Deco_Tankard` | 1 |
| `Container_Bucket` / `Filled_Milk` | `Antidote` (120 s) | `Container_Bucket` | 1 |
| `Container_Bucket` / `Filled_Mosshorn_Milk` | `Potion_Morph_Mosshorn` | `Container_Bucket` | 1 |
| `Deco_Bucket` / `Filled_Milk`, `Filled_Mosshorn_Milk` | idem | `Deco_Bucket` | 1 |

  Sources : `zip:Server/Item/Items/Deco/Deco_Mug.json:39-129` (état `Filled_Water`, `BrokenItem` l. 126), `Container/Container_Bucket.json:169-281` (lait, ResourceType `Milk_Bucket`, `BrokenItem` l. 231) et l. 282-344 (lait de Mosshorn). C'est le **seul** retour de contenant à la consommation. Il passe par la durabilité (`MaxDurability 1` + `BrokenItem`), pas par un champ « reste ». Les nourritures solides ne rendent rien : il n'y a ni bol ni bouteille. Les potions ne rendent pas `Potion_Empty` (grep sans résultat dans `Interactions/` et `RootInteractions/`).
- **Potions** (`zip:Server/Item/Items/Potion/`, 25 avec `Root_Secondary_Consume_Potion*`) : santé, signature, endurance, transformation, antidote. Ce n'est pas de la nourriture pour MC.
- **`Bandage_Crude`** : `ChangeStat Health +25`. Ce n'est pas de la nourriture.

### 1.7 ResourceTypes utiles (`zip:Server/Item/ResourceTypes/`)

- `Meats` (4) : `Food_Beef_Raw`, `Food_Chicken_Raw`, `Food_Pork_Raw`, `Food_Wildmeat_Raw`.
- `Vegetables` (50) : les 12 `Plant_Crop_*_Item` et 38 champignons.
- `Mushrooms` (45).
- `Fruits` (9).
- `Foods` (28) : tous les `Food_*` sauf les viandes crues, plus les 5 ingrédients.
- `Fish*`, `Milk_Bucket`, `Milk_Mosshorn_Bucket`, `Fuel`.

## 2. Faim, stats

- Pas de faim (voir Réponse courte, point 1). `DefaultEntityStatTypes` n'expose que `Health`, `Oxygen`, `Stamina`, `Mana`, `SignatureEnergy` et `Ammo` (`HY/server/core/modules/entitystats/asset/DefaultEntityStatTypes.java:13-47`).
- `Health.json` : de 0 à 100, `ResetType MaxValue`. **Régénération des PNJ** : +5 % toutes les 0,5 s, si le PNJ est vivant, pas un joueur, sans dégât depuis 15 s, et que la condition `RegenHealth` est vraie (`zip:Server/Entity/Stats/Health.json`). Cette condition lit `HealthRegenState.isRegenEnabled()` et vaut vrai sans ce composant (`HY/server/core/modules/entity/condition/RegenHealthCondition.java:66-68`). Pour une régénération pilotée par la nourriture comme MC, on peut la couper avec `HealthRegenState.setRegenEnabled(false)` (`HY/server/core/modules/entity/component/HealthRegenState.java:9-20`, composant de `EntityModule`, l. 720). **[in-game]**
- La vie max d'un PNJ = `MaxHealth` du rôle. `BalancingInitialisationSystem` pose le modificateur `NPC_Max` et remplit la vie au spawn (`HY/server/npc/systems/BalancingInitialisationSystem.java:78-92`). Notre rôle a `MaxHealth 20` (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`). Un soin T1 (5 %) rend donc 1 PV à un citoyen.

## 3. Cuisson

### 3.1 Bancs

| Bloc | Bench | Type | Détails |
|---|---|---|---|
| `Bench_Campfire` | `Campfire` | `Processing` | 2 entrées (`FilterValidIngredients`), 1 combustible (`res:Fuel`), 4 sorties, `AllowNoInputProcessing`, sortie bonus `Ingredient_Charcoal` tous les 2 combustibles. `Use` = `Open_Processing_Bench` (`zip:Server/Item/Items/Bench/Bench_Campfire.json`) |
| `Bench_Furnace` | `Furnace` | `Processing` | même forme, tiers (le tier 2 réduit le temps de 30 % et ajoute 1 entrée). Ne cuit **aucune** nourriture : minerais, pierres, `Potion_Empty` |
| `Bench_Cooking` | `Cookingbench` | `Crafting` | catégories `Prepared`, `Baked`, `Ingredients`. Fabrication par un joueur, pas de combustible en continu (les recettes `Baked` consomment `res:Fuel`×3 comme ingrédient) |

### 3.2 Toutes les recettes de nourriture

Recettes portées par les objets (`Recipe`). Aucune autre dans `zip:Server/Item/Recipes/` (qui ne contient que `Salvage/`) :

| Banc | Entrée | Sortie | Temps |
|---|---|---|---|
| Processing:Campfire | `res:Meats`×1 | `Food_Wildmeat_Cooked` | 2 s |
| Processing:Campfire | `res:Vegetables`×1 | `Food_Vegetable_Cooked` | 2 s |
| Processing:Campfire | `Food_Fish_Raw`×1 | `Food_Fish_Grilled` | 2 s |
| Cookingbench[Ingredients] | `res:Fish`/`Fish_Uncommon`/`Rare`/`Epic`/`Legendary`×1 | `Food_Fish_Raw` ×1/×2/×4/×8/×16 | 1 s |
| Cookingbench[Ingredients] | `Plant_Crop_Wheat_Item`×10 | `Ingredient_Flour` | 1 s |
| Cookingbench[Ingredients] | farine + œuf ×2 + `*Deco_Tankard_State_Filled_Water` | `Ingredient_Dough` + `Deco_Tankard` | 1 s |
| Cookingbench[Ingredients] | `Rock_Salt` | `Ingredient_Salt`×5 | 1 s |
| Cookingbench[Ingredients] | `res:Vegetables` + `res:Flowers`×5 | `Ingredient_Spices` | 1 s |
| Cookingbench[Ingredients] | `res:Milk_Bucket` | `Food_Cheese` + `Container_Bucket` | 5 s |
| Cookingbench[Baked] / [Prepared] | voir § 1.5 | | |

Correspondance avec MC `ISCOOKABLE` (`MC/api/util/ItemStackUtils.java:162` : le résultat de four de l'objet est de la nourriture) : sont « cuisables » en Hytale les **entrées du feu de camp** dont la sortie est de la nourriture. Ce sont les 4 viandes crues, `Food_Fish_Raw` et tout `Vegetables` (y compris les champignons non comestibles et lumineux).

### 3.3 API d'un banc de transformation

- **Composant** `ProcessingBenchBlock implements Component<ChunkStore>` (`HY/builtin/crafting/component/ProcessingBenchBlock.java:70`). Il est sauvegardé (`InputContainer`, `FuelContainer`, `OutputContainer`, `Progress`, `FuelTime`, `Active`, `RecipeId`, `LastTickGameTime`, l. 75-94) et s'obtient par `getComponentType()` (l. 125).
- **Lecture** : `getInputContainer()`, `getFuelContainer()`, `getOutputContainer()` (`ItemContainer`, l. 146-156), `getItemContainer()` (`CombinedItemContainer` fuel+input+output, l. 354), `getFuelTime()` (l. 158), `isActive()` (l. 308), `getRecipe()` (l. 359), `getInputProgress()` (l. 363) et `getRecipeTimeSeconds(int tierLevel)` (l. 399-407, réduit par le tier).
- **Allumer** : `setActive(boolean, BenchBlock, @Nullable BlockModule.BlockStateInfo)` (l. 312-337). Il refuse d'allumer si le combustible est vide (l. 314-316) et prévient les fenêtres ouvertes.
- **Trouver le composant** à partir d'une position, comme `OpenProcessingBenchInteraction` (`HY/builtin/crafting/interaction/OpenProcessingBenchInteraction.java:58-76`) :
  1. `world.getChunkStore().getChunkSectionReferenceAtBlock(x,y,z)` ;
  2. `BlockModule.getBlockEntity(store, sectionRef, x, y, z)` (`HY/server/core/modules/block/BlockModule.java:287` ; variante `getBlockEntity(World, x, y, z)`, l. 280) ;
  3. `store.getComponent(blockRef, ProcessingBenchBlock.getComponentType())`, et de même `BenchBlock` (tier, fenêtres) et `BlockModule.BlockStateInfo`.
- **Mettre et retirer des objets** : `ItemContainer.addItemStack(ItemStack)` (`HY/server/core/inventory/container/ItemContainer.java:837`), `addItemStackToSlot(short, ItemStack)` (l. 177), `getItemStack(short)` (l. 201), `removeItemStackFromSlot(short, int)` (l. 277) et `getCapacity()` (l. 66).
  - Les entrées filtrent les ingrédients valides des recettes du banc (l. 227-259) et le combustible le ResourceType `Fuel` (l. 262-274).
  - La sortie a le filtre global `ALLOW_OUTPUT_ONLY` (l. 280) : on y retire, on n'y ajoute pas.
  - Changer l'entrée ou la sortie relance `updateRecipe` (l. 290-291).
- **Ce qui fait avancer** : le système `BenchSystems.ProcessingBenchTick` (`EntityTickingSystem<ChunkStore>`, `HY/builtin/crafting/system/BenchSystems.java:245-474`).
  - Il tourne pour tout banc chargé, et le temps vient du temps de jeu (`WorldTimeResource`, l. 296-308).
  - Il éteint le banc si la sortie est pleine (l. 319-335), s'il n'y a plus d'ingrédient (l. 340-358) ou plus de combustible (l. 428-440).
  - Il passe l'état du bloc à `Processing` ou `ProcessCompleted` (l. 445-453). `advanceProcessing` est à l. 509.
  - Rattrapage au chargement du chunk, plafonné à 86 400 s (`setupSlots`, l. 296-305).
  - Chaque combustible donne `quantité × Item.getFuelQuality()` secondes (l. 625-643).
- **Énumérer les recettes** :
  - `CraftingPlugin.getBenchRecipes(BenchType, String benchId)` et `getBenchRecipes(BenchType, String benchId, @Nullable String category)` (`HY/builtin/crafting/CraftingPlugin.java:235-240`), ou `getBenchRecipes(Bench)` (l. 230) ;
  - toutes : `CraftingRecipe.getAssetMap()` (`HY/server/core/asset/type/item/config/CraftingRecipe.java:171`) ;
  - lecture : `getInput()` (`MaterialQuantity[]`, l. 268), `getOutputs()` (l. 272), `getPrimaryOutput()` (l. 293), `getBenchRequirement()` (l. 276) et `getTimeSeconds()` (l. 280) ;
  - aides : `CraftingManager.getInputMaterials(recipe)` (`HY/builtin/crafting/component/CraftingManager.java:644`), `getOutputItemStacks(recipe)` (l. 603) et `matches(MaterialQuantity, ItemStack)` (l. 670, gère `ResourceTypeId`).
- Tout passe sur le thread du monde (stores `ChunkStore`/`EntityStore`). **[in-game]** : comportement d'un banc rempli et allumé par un plugin sans fenêtre ouverte (rien dans le code ne l'exige).

## 4. Nourriture nocive

- **Poison** : seuls les 6 `Plant_Crop_Mushroom_Glowing_*` (§ 1.4) appliquent `{ "Parent": "Poison", "Duration": 2, "OverlapBehavior": "Extend" }`. `Poison` fait 10 dégâts de type `Poison` toutes les 5 s, c'est un debuff de 16 s par défaut, ici ramené à 2 s (`zip:Server/Entity/Effects/Status/Poison.json`). L'antidote et le lait retirent `Poison_T1/T2/T3` (`Potion_Antidote.json`).
- **Pourri ou avarié** : aucun objet ni mécanique (grep `rotten|spoil` sans résultat pertinent).
- **Dégoût** : les viandes crues, le poisson cru et l'œuf jouent `ConsumeDisgust*` (visage dégoûté, `zip:Server/Item/Animations/Item.json:253-266`), sans effet négatif.
- Rappel MC : le tag `poisonous_food` = `POISONOUS_POTATO`, `CHICKEN`, `SPIDER_EYE` et `ROTTEN_FLESH` (`MC/core/generation/defaults/DefaultItemTagsProvider.java:178-182`). Le poisson-globe n'y est **pas**. `FoodUtils` refuse ce tag (`MC/api/util/FoodUtils.java:45`). Un équivalent Hytale fidèle serait `Food_Chicken_Raw` (choix de portage) et les champignons lumineux.

## 5. Côté PNJ (corps des citoyens)

### 5.a Animation de repas

- Le modèle `Player` (parent de notre `PlayerTestModel_V`) n'a **pas** d'animation de repas dans ses `AnimationSets` (`zip:Server/Models/Human/Player.json`). Il a `Sit`, `Sit2`, `SitGround`, `Sleep`, `Sleep2`, `Hurt`, etc.
- Le joueur mange avec les animations d'**objet** du jeu `Item` (`PlayerAnimationsId: "Item"` de `Template_Food`), qu'on retrouve dans `zip:Server/Item/Animations/Item.json:241-266` :
  - `Consume` : `Characters/Animations/Items/Main_Handed/Item/Attacks/Eat/Eat.blockyanim`, sans boucle ;
  - `ConsumeSide` : `Eat_Side` ;
  - `ConsumeDisgust` et `ConsumeDisgustSide` : avec `ThirdPersonFace` `Eat_Disgust_Face.blockyanim` ;
  - boissons : `Consume` redéfini en `Drink.blockyanim`, en boucle (`Deco_Mug.json`, `PlayerAnimationsId`).
- Côté PNJ : `AnimationUtils.playAnimation(Ref, AnimationSlot, String itemAnimationsId, String animationId, ComponentAccessor)` (`HY/server/core/entity/AnimationUtils.java:85-93`), avec `(ref, AnimationSlot.Action, "Item", "Consume", store)`. C'est le schéma déjà employé pour `PLANT` (`"Item", "Interact"`) dans `HytaleCitizenBodies.playAnimation` (`plugin/.../adapter/HytaleCitizenBodies.java:294-307`). Il faut tenir l'aliment en main (`setHeldItem`, slot 0). **[in-game]**

### 5.b Soigner, lire la vie

- `EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType())`, `int h = DefaultEntityStatTypes.getHealth()`.
- Soigner de N : `stats.addStatValue(h, n)` (`HY/server/core/modules/entitystats/EntityStatMap.java:445`, variante `Predictable` l. 449).
- Lire : `stats.get(h)` → `EntityStatValue.get()`, `getMax()`, `getMin()`, `asPercentage()` (`EntityStatValue.java:58-70`). Il y a aussi `setStatValue` (l. 401) et `maximizeStatValue` (l. 651).
- Déjà utilisé pour la lecture dans `HytaleCitizenBodies.healthPercent` (l. 159-166).
- Autre voie : appliquer un effet de nourriture vanilla (§ 5.d).

### 5.c Voir qu'un PNJ prend des dégâts

- Les dégâts sont un événement ECS `Damage` invoqué sur la cible (`commandBuffer.invoke(ref, damage)`, par exemple `ActiveEntityEffect.java:247`). On l'écoute avec `DamageEventSystem extends EntityEventSystem<EntityStore, Damage>` (`HY/server/core/modules/entity/damage/DamageEventSystem.java:14`), comme `CitizenFireImmunitySystems.Guard` (requête `HyColonyComponents.citizenTag()`).
- Groupes :
  - *gather* ;
  - *filter* (`DamageModule.get().getFilterDamageGroup()`) ;
  - application (`DamageSystems.ApplyDamage`, `DamageSystems.java:237-281`, qui retire la vie et pose `DeathComponent`) ;
  - *inspect* (`getInspectDamageGroup()`, après l'application).
- Un système *inspect* ne voit que les dégâts **non annulés** : `EventSystem.shouldProcessEvent` saute les événements annulés (`HY/component/system/EventSystem.java:13-15`). Lecture : `Damage.getAmount()` (l. 197), `getCause()` (l. 148), `getSource()` (l. 165).
- **Limite actuelle** : le rôle citoyen est `"Invulnerable": true`, donc `DamageSystems.FilterUnkillable` (l. 1232-1270) annule tout. Il faut retirer l'invulnérabilité avec le filtre `HURT_CITIZEN` décrit dans `docs/research/citizen-death.md` § 2.4. Sinon, seul un système du groupe *filter* voit la tentative, et son ordre par rapport à `FilterUnkillable` n'est pas garanti.

### 5.d Ralentir un PNJ

- `NPCEntity.getCurrentHorizontalSpeedMultiplier` multiplie le `HorizontalSpeedMultiplier` de **tous** les effets actifs (`HY/server/npc/entities/NPCEntity.java:551-585`). Il est lu par `MotionControllerBase` (l. 521) et les mouvements d'errance.
- Vanilla : `Slow` (`zip:Server/Entity/Effects/Status/Slow.json`) = × 0,5 pendant 10 s, mais avec teintes, `ModelVFXId Intangible_Dark` et effet d'écran.
- HyColony : `CitizenSpeed` garde **un** effet `HyColony_Speed_*` infini (id-map `speedEffects`, de 1,05 à 1,45) et choisit le plus proche du facteur (`plugin/.../npc/CitizenSpeed.java`). Ajouter des paliers < 1 à `speedEffects` (`HyColony_Speed_95`… avec seulement `HorizontalSpeedMultiplier`) suffit pour `setMovementSpeed(body, 0.8)`. `nearest` gère déjà les facteurs < 1.
- Pour un effet limité dans le temps : `EffectControllerComponent.addEffect(ref, effect, duration, OverlapBehavior, accessor)` (`HY/server/core/entity/effect/EffectControllerComponent.java:183`) ou `addEffect(ref, effect, accessor)` (l. 151, durée de l'asset).

### 5.e Particules

- **Repas** : `Food_Eat` (`zip:Server/Particles/Item/Food/Food_Eat.particlesystem`, émetteurs `Food_Eat_Chunks` et `Food_Eat_Drops`). La nourriture l'attache à l'os `Mouth`, avec une couleur par aliment (§ 1.4).
  - Depuis un plugin : `ParticleUtil.spawnParticleEffect(String, Vector3dc, ComponentAccessor)` (`HY/server/core/universe/world/ParticleUtil.java:50`).
  - Avec couleur : `spawnParticleEffect(String, Vector3dc, yaw, pitch, roll, scale, com.hypixel.hytale.protocol.Color, List<Ref<EntityStore>> playerRefs, accessor)` (l. 127-138).
  - **[in-game]** : rendu à la position de la tête (pas d'attache à `Mouth` par cette voie).
- **Émotions** (`zip:Server/Particles/NPC/Emotions/`) : `Hungry`, `Hearts`, `Hearts_Subtle`, `Angry`, `Sleepy` (déjà utilisé), `Question`, `Alerted`, `Stunned`, et `Want_Food_<Apple|Aubergine|Carrot|Cauliflower|Chilli|Corn|Cotton|Lettuce|Onion|Potato|Pumpkin|Tomato|Turnip>`. `Hungry` correspond à « citoyen affamé » et `Hearts` à « content ».

## 6. Sièges

- Un bloc est un siège si `BlockType.getSeats() != null`. Il passe **avant** le lit dans `BlockMountAPI.mountOnBlock` (`HY/builtin/mounts/BlockMountAPI.java:73-83`, `BlockMountType.Seat`). Signature et effets : voir `plugin-b-api.md` § 41 et § 44, et `CitizenBeds`. `findAvailableSeat` choisit le point libre le plus proche de `interactPos`. `NO_MOUNT_POINT_FOUND` si tout est pris.
- Le joueur s'assoit par `SeatingInteraction` (`Use` = `Block_Seat`), qui appelle la même méthode avec le centre du bloc visé (`HY/builtin/mounts/interactions/SeatingInteraction.java`). Rien n'y est propre au joueur, sauf le son et le message d'échec.
- Pose : `MovementStates.sitting` existe (`HY/protocol/MovementStates.java:37`), comme `sleeping` pour `CitizenBeds.setSleeping`. Le modèle a `Sit`, `Sit2` et `SitGround`. **[in-game]** : savoir si le client assoit un PNJ monté sur un siège par le seul `MountedUpdate`, ou s'il faut `sitting = true` ou l'animation `Sit` sur le créneau `Status`.
- **Meubles vanilla avec `Seats`** (32, `zip:Server/Item/Items/**`). Les bancs ont **2** places, les autres 1 :
  - chaises : `Deco_Chair_Scrap`, `Furniture_Ancient_Chair`, `Furniture_Desert_Chair`, `Furniture_Frozen_Castle_Chair`, `Furniture_Human_Ruins_Chair`, `Furniture_Jungle_Chair`, `Furniture_Lumberjack_Chair`, `Furniture_Royal_Magic_Chair`, `Furniture_Tavern_Chair`, `Furniture_Village_Chair` ;
  - tabourets : `Furniture_Crude_Stool`, `Furniture_Faun_Stool`, `Furniture_Feran_Stool`, `Furniture_Goblin_Stool`, `Furniture_Human_Ruins_Stool`, `Furniture_Kweebec_Stool`, `Furniture_Tavern_Stool`, `Furniture_Temple_{Dark,Emerald,Light,Wind}_Stool`, `Furniture_Village_Stool` ;
  - bancs (2 places) : `Furniture_Ancient_Bench`, `Furniture_Castle_Bench`, `Furniture_Feran_Bench`, `Furniture_Frozen_Castle_Bench`, `Furniture_Human_Ruins_Bench`, `Furniture_Jungle_Bench`, `Furniture_Tavern_Bench`, `Furniture_Temple_Light_Bench`, `Furniture_Village_Bench` ;
  - canapé : `Furniture_Royal_Magic_Couch` (1 place).
  - Exemple de point : `Furniture_Village_Chair`, `Seats: [{ "Offset": {0, 0, 0.2}, "Yaw": 0 }]`, `Use: Block_Seat`.

## 7. Nourrir un citoyen à la main (MC `EntityCitizen.eatFoodInteraction`)

- Aujourd'hui, la touche *Use* sur un citoyen arrive dans `CitizenUseSystem` (`plugin/src/main/java/dev/hycolony/plugin/npc/CitizenUseSystem.java:24-64`).
  - Tout rôle de PNJ a `Interactions.Use = "*UseNPC"`, et `UseEntityInteraction` invoque `UseEntityEvent.Pre` sur le joueur avant de lancer l'interaction (`HY/server/core/modules/interaction/interaction/config/client/UseEntityInteraction.java:78-84`).
  - Le système annule l'événement et ouvre la fenêtre du citoyen.
- Objet tenu : `UseEntityEvent.getContext()` (`HY/server/core/event/events/ecs/UseEntityEvent.java:57`) donne l'`InteractionContext`, avec `getHeldItem()` (`ItemStack`, `HY/server/core/entity/InteractionContext.java:422`), `getHeldItemContainer()` (l. 413) et `getHeldItemSlot()` (l. 417).
- Retirer un objet : `ctx.getHeldItemContainer().removeItemStackFromSlot(ctx.getHeldItemSlot(), 1)` (`ItemContainer.java:277`), c'est-à-dire l'équivalent serveur de `ModifyInventory AdjustHeldItemQuantity -1`. Autre voie : `InventoryComponent.getItemInHand(accessor, playerRef)` (`HY/server/core/inventory/InventoryComponent.java:392`), qui lit l'objet actif de la barre (ou de l'outil).
- Le clic droit (`Secondary`) avec de la nourriture en main fait **manger le joueur** : il ne vise pas le PNJ. Le « nourrir » de MC (clic droit sur le citoyen) correspond donc à la touche *Use* avec l'aliment en main, puis on décide dans `CitizenUseSystem` : nourrir si l'objet tenu est un aliment, sinon ouvrir la fenêtre. **[in-game]** : que le contexte d'une interaction *Use* sur une entité porte bien l'objet tenu.

## 8. Proposition de correspondance MC → Hytale (à valider)

Les identifiants d'assets vivent dans l'id-map (CLAUDE.md § 7). Il faut donc une table `foods` dans `hycolony/id-map.json` : id → `nutrition`, `saturation`, `tier` (0 à 3), `poisonous`, `container`, `cookedInto`. Le cœur n'y voit que des clés d'objet. Faits Hytale disponibles pour la remplir :

| Notion MC | Source Hytale |
|---|---|
| `ISFOOD` (`FoodProperties != null`, nutrition > 0 et saturation > 0, `MC/api/util/ItemStackUtils.java:146-152`) | `Secondary` résolu = `Root_Secondary_Consume_Food*` ou interaction en ligne vers `Consume_Charge`. Exclure les ingrédients (§ 1.6) et les champignons lumineux (poison) |
| nutrition | aucune ; à dériver du palier de soin (5 / 10 / 15 %) et de la régénération (Raw, T1, T2, T3) |
| `ISCOOKABLE` | entrée d'une recette `Processing:Campfire` dont la sortie est une nourriture (§ 3.2) |
| `poisonous_food` | champignons lumineux ; `Food_Chicken_Raw` si l'on suit le tag MC (choix de portage) |
| contenant rendu | boissons seulement (`BrokenItem`) ; fromage → seau (recette) |
| palier `IMinecoloniesFoodItem` | `HealthRegen_TierCheck_Tn` dans `Effect` (T1 : 3 plats cuits ; T2 : brochettes et salades baies/champignons ; T3 : tartes et César) |

## Incertain / [in-game]

- Ingrédients (`Ingredient_*`) réellement mangeables malgré `Consumable: false` (§ 1.6).
- Banc de feu de camp rempli et allumé par un plugin, sans fenêtre (§ 3.3).
- Animation `Item`/`Consume` sur un citoyen, et position des particules `Food_Eat` sans attache `Mouth` (§ 5.a, 5.e).
- Pose assise d'un PNJ monté sur un siège (§ 6).
- Objet tenu présent dans le contexte de `UseEntityEvent.Pre` (§ 7).
- Coupure de la régénération vanilla des PNJ par `HealthRegenState` (§ 2).
