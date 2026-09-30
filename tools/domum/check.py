"""Self-check of the Domum Ornamentum generator: python tools/domum/check.py (exits non-zero on failure). Tests on
real DO data call source.fetch(), which downloads DO's pinned commit once into build/domum-cache/ (network needed
on the first run only); tests on vanilla assets read the pinned Hytale version's assets zip (tags.open_assets)."""

import check_assemble
import check_connected
import check_convert
import check_cutter
import check_faces
import check_icons
import check_materials
import check_pack


def main():
    for module in (check_assemble, check_faces, check_materials, check_convert, check_pack, check_connected,
                   check_icons, check_cutter):
        module.run()
    print("tools/domum check: OK")


if __name__ == "__main__":
    main()
