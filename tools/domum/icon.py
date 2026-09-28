"""A shape's icon painted from its icon map (iconmap.py) and a texture layout: the same reading as the plugin's
runtime, used for the templates' icons and to check the maps."""

from PIL import Image


def from_map(icon_map, layout):
    """The RGBA icon: each covered map pixel takes the layout texel it codes, darkened by its shade; the texel's
    own transparency is kept."""
    width, height = layout.size
    source = layout.convert("RGBA").load()
    icon = Image.new("RGBA", icon_map.size, (0, 0, 0, 0))
    out = icon.load()
    for y in range(icon_map.size[1]):
        for x in range(icon_map.size[0]):
            r, g, b, a = icon_map.getpixel((x, y))
            if not a:
                continue
            tr, tg, tb, ta = source[r * width // 256, g * height // 256]
            out[x, y] = (tr * b // 255, tg * b // 255, tb * b // 255, ta)
    return icon
