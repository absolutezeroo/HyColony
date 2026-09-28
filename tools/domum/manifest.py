"""The shape manifest the plugin reads at boot (hycolony/ornament/shapes.json, server-side only): one entry per
template with its slot tags, for core ShapeCatalog.parse."""

from pack import write_json

SCHEMA_VERSION = 1


def write(ctx):
    """Writes shapes.json under ctx.resources/hycolony/ornament."""
    write_json(ctx.resources / "hycolony" / "ornament" / "shapes.json",
               {"schemaVersion": SCHEMA_VERSION, "shapes": ctx.shapes})
