"""Generates the food tooltips: a citizen's food value under each food's vanilla description (Hytalor patches).

Hytale has no hunger bar, so nothing shows a player what a food gives a citizen (docs/research/food-tooltips.md).
For each food of HyColony's food files (plugin/src/main/resources/Server/HyColony/Foods/<id>.json) this writes:

- Server/Patch/HyColony/Food/<id>.json: a Hytalor patch pointing the item's Description at our key;
- Server/Languages/<lang>/hycolony_food.lang: that key, the vanilla description (<msg key>) then HyColony's lines,
  each food headed by a comment the build checks against its food file (plugin's checkFoodTooltips).

Run it after changing the food table or the pinned Hytale version, then commit the outputs; the build never runs it.
Needs Python 3.10+.

    python tools/food/generate.py [path/to/Assets.zip]
"""

import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "plugin" / "src" / "main" / "resources"
FOODS = PACK / "Server" / "HyColony" / "Foods"
PATCHES = PACK / "Server" / "Patch" / "HyColony" / "Food"
LANG_FILE = "hycolony_food.lang"

# MC FoodUtils.getFoodValue: a prepared dish (MC IMinecoloniesFoodItem, here tier >= 1) counts twice; core FoodRules.
DISH_BONUS = 2
# MC CitizenWindowUtils.createSaturationBar: one drumstick per 6 of the 60 saturation (core SaturationBar).
SATURATION_PER_DRUMSTICK = 6
# MC Constants.MAX_BUILDING_LEVEL, for FoodUtils.getBuildingLevelForFood (core FoodRules.buildingLevelForFood).
MAX_BUILDING_LEVEL = 5
# MC ChatFormatting.GRAY and RED, the colours of MC's food tooltips (ClientEventHandler.onItemTooltipEvent).
GRAY = "#AAAAAA"
RED = "#FF5555"
# A rule between the vanilla description and ours, after ItemTooltip.ui's @Separator (colour #25262c): the tooltip
# text has no separator tag, so 18 horizontal bars (U+2015, 14 px wide in the description's 14 px Nunito Sans, ink
# edge to edge) span 252 px, under the 272 px of the narrowest tooltip's content with room for rounding at other UI
# scales: it should not wrap (docs/research/client-tooltip-markup.md § Séparateur).
SEPARATOR = '<color is="#25262C">' + "―" * 18 + "</color>"

# MC's texts where it has one (manual_en_us.json: core.gui.restaurant.foodquality and vanillafoodquality,
# core.item.food.tooltip.tier.<n>, coremod.item.tooltip.wrongfood); the value and poison lines are HyColony's.
TEXTS = {
    "en-US": {
        "value": "Feeds a citizen: {value} ({drumsticks} {unit})",
        "units": ("drumstick", "drumsticks"),
        # English: singular for exactly one (0.5 drumsticks).
        "singular": lambda count: count == 1,
        "level": "Eaten up to Residence level: {level}",
        "tier": {
            0: "Citizens prefer more sophisticated food!",
            1: "This food is somewhat acceptable for your citizens.",
            2: "This food will keep your citizens satisfied.",
            3: "This food will make your citizens happy for sure!",
        },
        "raw": "This food is too raw for your citizens to eat",
        "poisonous": "Poisonous: your citizens won't eat it",
        "decimal": ".",
    },
    "fr-FR": {
        "value": "Nourrit un citoyen : {value} ({drumsticks} {unit})",
        "units": ("gigot", "gigots"),
        # French: singular below two (1,5 gigot).
        "singular": lambda count: count < 2,
        "level": "Mangé jusqu'au niveau de résidence : {level}",
        "tier": {
            0: "Les citoyens préfèrent une cuisine plus raffinée !",
            1: "Cette nourriture convient à peu près à vos citoyens.",
            2: "Cette nourriture contentera vos citoyens.",
            3: "Cette nourriture rendra vos citoyens heureux, c'est sûr !",
        },
        "raw": "Cette nourriture est trop crue pour vos citoyens",
        "poisonous": "Empoisonné : vos citoyens n'en mangeront pas",
        "decimal": ",",
    },
}


def gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == name:
            return value.strip()
    raise KeyError(name)


def assets_zip():
    if len(sys.argv) > 1:
        return Path(sys.argv[1])
    return (Path.home() / ".gradle" / "caches" / "hytale-assets"
            / f"{gradle_property('patchline')}-{gradle_property('hytale_version')}-Assets.zip")


class Assets:
    """The items of the vanilla assets zip, their Parent chain merged, and the en-US server.lang keys."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.paths = {n.rsplit("/", 1)[1][:-5]: n for n in self.zip.namelist()
                      if n.startswith("Server/Item/Items/") and n.endswith(".json")}
        lang = self.zip.read("Server/Languages/en-US/server.lang").decode("utf-8")
        self.server_keys = {"server." + line.partition("=")[0].strip()
                            for line in lang.splitlines() if "=" in line and not line.lstrip().startswith("#")}
        self._merged = {}

    def own(self, item_id):
        return json.loads(self.zip.read(self.paths[item_id]).decode("utf-8"))

    def item(self, item_id):
        if item_id not in self._merged:
            data = self.own(item_id)
            parent = data.get("Parent")
            merged = dict(self.item(parent)) if parent in self.paths else {}
            merged.update(data)
            self._merged[item_id] = merged
        return self._merged[item_id]

    def cooking_inputs(self, foods):
        """Every input of a processing recipe whose primary output is a food (an item, or each item of a resource
        type), like the plugin's CookingBenches: what citizens will not eat raw (MC FoodUtils.EDIBLE). Recipes do not
        inherit; a recipe without PrimaryOutput makes its own item."""
        inputs = set()
        for item_id in self.paths:
            recipe = self.own(item_id).get("Recipe") or {}
            output = (recipe.get("PrimaryOutput") or {}).get("ItemId", item_id)
            benches = recipe.get("BenchRequirement") or []
            if output in foods and any(b.get("Type") == "Processing" for b in benches):
                for i in recipe.get("Input") or []:
                    if "ItemId" in i:
                        inputs.add(i["ItemId"])
                    elif "ResourceTypeId" in i:
                        inputs.update(self.of_resource_type(i["ResourceTypeId"]))
        return inputs

    def of_resource_type(self, type_id):
        return {i for i in self.paths
                if any(r.get("Id") == type_id for r in self.item(i).get("ResourceTypes") or [])}


def drumsticks(value, texts):
    count = round(value / SATURATION_PER_DRUMSTICK, 1)
    shown = (str(int(count)) if count == int(count) else str(count)).replace(".", texts["decimal"])
    return shown, texts["units"][0 if texts["singular"](count) else 1]


def lines(food, raw, texts):
    """HyColony's lines for one food: why citizens refuse it, or what it feeds and up to which home."""
    if raw:
        return RED, [texts["raw"]]
    if food["poisonous"]:
        return RED, [texts["poisonous"]]
    tier = food["tier"]
    value = food["nutrition"] * (DISH_BONUS if tier >= 1 else 1)
    count, unit = drumsticks(value, texts)
    level = max(2, min(food["nutrition"] - 1, MAX_BUILDING_LEVEL))
    return GRAY, [texts["value"].format(value=value, drumsticks=count, unit=unit),
                  texts["level"].format(level=level), texts["tier"][tier]]


def description(food, raw, vanilla_key, texts):
    colour, text = lines(food, raw, texts)
    ours = f'<color is="{colour}">' + "\\n".join(text) + "</color>"
    return f'<msg key="{vanilla_key}"/>\\n{SEPARATOR}\\n{ours}' if vanilla_key else ours


def header(item_id, food):
    """The comment the build compares with the food file (plugin build: checkFoodTooltips)."""
    return f"# {item_id} nutrition={food['nutrition']} tier={food['tier']} poisonous={str(food['poisonous']).lower()}"


def load_foods():
    """HyColony's food files, by item id, with the keys the tooltips read (Poisonous defaults to false)."""
    foods = {}
    for path in sorted(FOODS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        foods[path.stem] = {"nutrition": data["Nutrition"], "tier": data["Tier"],
                            "poisonous": data.get("Poisonous", False)}
    return foods


def main():
    assets = Assets(assets_zip())
    foods = load_foods()
    raw = assets.cooking_inputs(set(foods))
    missing = [f for f in foods if f not in assets.paths]
    if missing:
        sys.exit("unknown food items: " + ", ".join(missing))
    # The patch merges TranslationProperties into the item's own file: one that inherits its Name would lose it.
    nameless = [f for f in foods if not assets.own(f).get("TranslationProperties", {}).get("Name")]
    if nameless:
        sys.exit("foods without their own TranslationProperties.Name: " + ", ".join(nameless))
    for old in PATCHES.glob("*.json"):
        old.unlink()
    PATCHES.mkdir(parents=True, exist_ok=True)
    out = {lang: ["# Generated by tools/food/generate.py from Server/HyColony/Foods: do not edit by hand."]
           for lang in TEXTS}
    for item_id in sorted(foods):
        food = foods[item_id]
        key = assets.item(item_id).get("TranslationProperties", {}).get("Description")
        vanilla = key if key in assets.server_keys else None
        patch = {"_BaseAssetPath": assets.paths[item_id],
                 "TranslationProperties": {"Description": f"hycolony_food.{item_id}.description"}}
        (PATCHES / f"{item_id}.json").write_text(json.dumps(patch, indent=2) + "\n", encoding="utf-8",
                                                 newline="\n")
        for lang, texts in TEXTS.items():
            out[lang] += [header(item_id, food),
                          f"{item_id}.description = " + description(food, item_id in raw, vanilla, texts)]
    for lang, content in out.items():
        path = PACK / "Server" / "Languages" / lang / LANG_FILE
        path.write_text("\n".join(content) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(foods)} foods, {len(raw & set(foods))} raw")


if __name__ == "__main__":
    main()
