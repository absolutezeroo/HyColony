"""The "Domum Ornamentum" creative tab: every template in one list, as DO's single creative tab in Minecraft, with an
icon pair X.png / XActive.png under Icons/ItemCategories (a missing category icon stops the server). Hytale's
creative library shows a tab's children, so the one list is the tab's only child."""

from PIL import Image

from blocks.common import DO_TAB_LIST, LANGUAGES
from pack import write_json

TAB = "Server/Item/Category/CreativeLibrary/HyDomum.json"
ICONS = "Icons/ItemCategories/HyDomum"
TAB_ICON_SIZE = 88  # pixels, as vanilla tab icons (Natural.png)
CHILD_ICON_SIZE = 48  # pixels, as vanilla category icons (Blocks.png)
ALL = DO_TAB_LIST
TAB_NAME = "Domum Ornamentum"  # the mod's name, the same in every language


def generate(ctx):
    """Writes the tab, its icons and name; returns the tab JSON. Without any template the tab has no child."""
    children = []
    if ctx.items:
        first = _first_icon(ctx)
        icon = f"{ICONS}_{ALL}.png"
        _icon_pair(ctx, icon, first, CHILD_ICON_SIZE)
        _icon_pair(ctx, ICONS + ".png", first, TAB_ICON_SIZE)
        key = "category.do." + ALL.lower()
        children.append({"Id": ALL, "Name": "hydomum_blocks." + key, "Icon": icon})
        for language in LANGUAGES:
            ctx.lang[language].append(f"{key} = {TAB_NAME}")
    tab = {"Icon": ICONS + ".png", "Order": 4, "Children": children}
    write_json(ctx.pack / TAB, tab)
    return tab


def _first_icon(ctx):
    """The icon of the first template generated, as an image."""
    item = next(iter(ctx.items.values()))
    path = ctx.pack / "Common" / item["Icon"]
    return Image.open(path).convert("RGBA") if path.exists() else ctx.assets.image("Common/" + item["Icon"])


def _icon_pair(ctx, path, image, size):
    """Writes path and its ...Active.png twin (vanilla shows the second on the selected tab)."""
    resized = image.resize((size, size), Image.LANCZOS)
    for target in (path, path.replace(".png", "Active.png")):
        out = ctx.pack / "Common" / target
        out.parent.mkdir(parents=True, exist_ok=True)
        resized.save(out)
