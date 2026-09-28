"""The shape manifest the plugin reads at boot (hydomum/shapes.json, server-side only): one entry per
template with its slot tags, for core ShapeCatalog.parse."""

from pack import write_json

SCHEMA_VERSION = 1


def write(ctx):
    """Writes shapes.json under ctx.resources/hydomum."""
    write_json(ctx.resources / "hydomum" / "shapes.json",
               {"schemaVersion": SCHEMA_VERSION, "shapes": ctx.shapes})
