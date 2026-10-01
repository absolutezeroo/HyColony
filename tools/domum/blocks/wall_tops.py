"""The states of a HyDomum wall's top (MC WallBlock.updateShape): for each template shape, each set of its arms made
tall by the block above, and the raised post a straight run or a cross may take. Named as domum/core WallState
names them: the shape, "_Tall" and the tall sides at yaw 0 in N, E, S, W order, "_Up" for the optional post; the
plugin's rule set finds each by that name in TemplateShapeBlockPatterns.
"""

from itertools import combinations

from blocks import common

# The shapes whose post depends on the block above; the others always raise it (an arm lacks its opposite).
OPTIONAL_POST = ("Straight", "Cross_Junction")
LETTERS = (("north", "N"), ("east", "E"), ("south", "S"), ("west", "W"))


def name(shape, tall, post):
    """The state name of shape (its arms at yaw 0) with its tall sides and post (domum/core WallState.name)."""
    result = shape
    if tall:
        result += "_Tall" + "".join(letter for side, letter in LETTERS if side in tall)
    if post and shape in OPTIONAL_POST:
        result += "_Up"
    return result


def looks(shapes):
    """(name, arms, tall sides, post) of every wall top but the plain shapes themselves (shapes: shape -> arms at yaw
    0, the lone post included with no arm): a post where an arm lacks its opposite (MC shouldRaisePost), an optional
    one on a straight run or a cross, even over two tall opposite arms (a raised wall post above raises it first)."""
    result = []
    for shape, arms in shapes.items():
        for count in range(len(arms) + 1):
            for tall in map(set, combinations(sorted(arms), count)):
                posts = (False, True) if shape in OPTIONAL_POST else (True,)
                for post in posts:
                    state = name(shape, tall, post)
                    if state != shape:
                        result.append((state, arms, tall, post))
    return result


def props(arms, tall, post):
    """DO's wall multipart props of such a top."""
    sides = {side: ("tall" if side in tall else "low") if side in arms else "none" for side in common.SIDES}
    return {"up": "true" if post else "false", **sides}
