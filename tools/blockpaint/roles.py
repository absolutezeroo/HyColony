"""What each part of a model is, and what happens to it (spec 2026-10-03 blockpaint surfaces, roles): a role says
what a part is (a grip, a sole, a roof), the usage what it goes through (touched, struck); together they give each
part its declared maps, which the effects read beside the baked ones."""

from collections import namedtuple

# The declared maps of a part, each from 0 to 1: touched by hands, struck, rubbed, near the ground, the eye's focus.
Declared = namedtuple("Declared", "contact impact abrasion ground focus", defaults=(0.0,) * 5)
# A role: the declared maps it gives its part, and the weights it puts on effects ({effect name: weight}, 1 when
# missing: a roof gathers moss and runs with water, a face stays clean).
Role = namedtuple("Role", "maps weights", defaults=({},))

ROLES = {
    "blade": Role(Declared(impact=0.6, abrasion=0.5)),
    "handle": Role(Declared(contact=0.8)),
    "grip": Role(Declared(contact=1.0)),
    "guard": Role(Declared(contact=0.3, impact=0.5)),
    "boot": Role(Declared(abrasion=0.5, ground=0.6), {"mud": 1.5, "dirt": 1.3}),
    "sole": Role(Declared(abrasion=1.0, ground=1.0), {"mud": 2.0, "dirt": 1.5}),
    "face": Role(Declared(focus=1.0), {"dirt": 0.2, "grime": 0.2, "dust": 0.2}),
    "hair": Role(Declared(focus=0.5), {"dirt": 0.3, "dust": 0.3}),
    "armor_plate": Role(Declared(impact=0.6)),
    "cloth_panel": Role(Declared()),
    "strap": Role(Declared(contact=0.5, abrasion=0.3)),
    "shield_rim": Role(Declared(impact=1.0, abrasion=0.4)),
    "shield_face": Role(Declared(impact=0.5, focus=0.5)),
    "roof": Role(Declared(), {"moss": 1.5, "water_stain": 1.5, "dust": 0.5}),
    "wall": Role(Declared()),
    "floor": Role(Declared(abrasion=0.8, ground=1.0), {"dirt": 1.5}),
    "step": Role(Declared(abrasion=1.0, ground=0.8), {"dirt": 1.5}),
    "wheel": Role(Declared(abrasion=0.8, ground=0.8), {"mud": 1.5}),
    "axle": Role(Declared(contact=0.3), {"grease": 2.0}),
    "door": Role(Declared(contact=0.3)),
    "hinge": Role(Declared(contact=0.2), {"rust": 1.5, "grease": 1.5}),
    "lid": Role(Declared(contact=0.5)),
    "leg": Role(Declared(impact=0.3, ground=0.6), {"dirt": 1.3}),
    "rope": Role(Declared(contact=0.4, abrasion=0.3)),
    "pot": Role(Declared(), {"water_stain": 1.3}),
    "rim": Role(Declared(contact=0.5, impact=0.4)),
}


def contact(amount):
    """Usage: the part is touched by hands this much."""
    return {"contact": amount}


def impact(amount):
    """Usage: the part is struck this much."""
    return {"impact": amount}


def abrasion(amount):
    """Usage: the part is rubbed this much."""
    return {"abrasion": amount}


def role_of(name, roles):
    """The Role of the part name ({name: role name}), None without one. Fails on an unknown role, listing the known
    ones."""
    role = roles.get(name)
    if role is not None and role not in ROLES:
        raise SystemExit(f"{name}: unknown role {role} (roles: {', '.join(sorted(ROLES))})")
    return ROLES[role] if role else None


def declared(name, roles, usage, focus):
    """The declared maps of the part name: its role's ({name: role}), corrected by its usage ({name: {map: amount}},
    usage.contact and kin), with focus 1 when focus names it. Fails on an unknown role (role_of)."""
    role = role_of(name, roles)
    maps = (role.maps if role else Declared())._replace(**usage.get(name, {}))
    return maps._replace(focus=1.0) if name in focus else maps


def weight(name, roles, effect):
    """The weight the role of the part name puts on effect (1 without a role or a weight). Fails on an unknown role
    (role_of)."""
    role = role_of(name, roles)
    return role.weights.get(effect, 1.0) if role else 1.0
