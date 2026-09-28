"""The "Domum Ornamentum" creative tab: one child per family, in DO's cutter group order (A.3), each with an icon
pair X.png / XActive.png under Icons/ItemCategories (a missing category icon stops the server)."""

from PIL import Image

import names
from blocks.common import LANGUAGES
from families import FAMILIES
from pack import write_json

TAB = "Server/Item/Category/CreativeLibrary/DomumOrnamentum.json"
ICONS = "Icons/ItemCategories/DomumOrnamentum"
TAB_ICON_SIZE = 88  # pixels, as vanilla tab icons (Natural.png)
CHILD_ICON_SIZE = 48  # pixels, as vanilla category icons (Blocks.png)


def generate(ctx):
    """Writes the tab, its icons and names; returns the tab JSON. Only families with a template appear."""
    used = [f for f in sorted(FAMILIES, key=lambda f: f.group) if any(
        i["Categories"] == ["DomumOrnamentum." + f.name] for i in ctx.items.values())]
    children = []
    for family in used:
        icon = f"{ICONS}_{family.name}.png"
        _icon_pair(ctx, icon, _first_icon(ctx, family), CHILD_ICON_SIZE)
        key = "category.do." + family.name.lower()
        children.append({"Id": family.name, "Name": "hycolony." + key, "Icon": icon})
        for language, name in zip(LANGUAGES, names.FAMILY_NAMES[family.name]):
            ctx.lang[language].append(f"{key} = {name}")
    tab = {"Icon": ICONS + ".png", "Order": 4, "Children": children}
    if used:
        _icon_pair(ctx, ICONS + ".png", _first_icon(ctx, used[0]), TAB_ICON_SIZE)
    write_json(ctx.pack / TAB, tab)
    return tab


def _first_icon(ctx, family):
    """The icon of the family's first template, as an image."""
    item = next(i for i in ctx.items.values() if i["Categories"] == ["DomumOrnamentum." + family.name])
    path = ctx.pack / "Common" / item["Icon"]
    return Image.open(path).convert("RGBA") if path.exists() else ctx.assets.image("Common/" + item["Icon"])


def _icon_pair(ctx, path, image, size):
    """Writes path and its ...Active.png twin (vanilla shows the second on the selected tab)."""
    resized = image.resize((size, size), Image.LANCZOS)
    for target in (path, path.replace(".png", "Active.png")):
        out = ctx.pack / "Common" / target
        out.parent.mkdir(parents=True, exist_ok=True)
        resized.save(out)

