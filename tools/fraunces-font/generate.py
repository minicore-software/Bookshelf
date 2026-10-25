#!/usr/bin/env python3
"""Produces the static Fraunces SemiBold instance bundled at
app/src/main/res/font/fraunces_semibold.ttf from the upstream Fraunces
variable font.

Usage:
    python generate.py generate   # writes a fresh instance to out/fraunces_semibold.ttf
    python generate.py check      # verifies the pinned source hash and the committed
                                   # font's invariants (no fvar/STAT, correct family name)

See docs/plans/10-blank-landing-screen.md section 3 and
app/src/main/java/com/minicore/bookshelf/core/designsystem/Type.kt for why this instance
exists and how it is used. Requires fonttools==4.67.0 (pip install -r requirements.txt).

Do not hand-edit app/src/main/res/font/fraunces_semibold.ttf. Regenerate it with this
script's "generate" mode instead, so the next change starts from the same reproducible
steps instead of another one-off hand edit.
"""

import sys
from hashlib import sha256
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPT_DIR.parent.parent
SOURCE_PATH = SCRIPT_DIR / "source" / "Fraunces[SOFT,WONK,opsz,wght].ttf"
OUT_DIR = SCRIPT_DIR / "out"
COMMITTED_PATH = ROOT_DIR / "app" / "src" / "main" / "res" / "font" / "fraunces_semibold.ttf"

# Source: the Fraunces family (Undercase Type, SIL OFL 1.1) variable font, as published
# at google/fonts commit ac502d8eff76ef4d9477cdcc8ef7d0c84fde5372 (the last commit to
# touch this exact file for the v1.0 release; https://github.com/google/fonts/commits/
# ac502d8eff76ef4d9477cdcc8ef7d0c84fde5372/ofl/fraunces/Fraunces%5BSOFT,WONK,opsz,wght%5D.ttf).
# Pinned to this commit, not `main` (which moves), so re-downloading reproduces the same
# bytes. Re-download with:
#   curl -L -o "source/Fraunces[SOFT,WONK,opsz,wght].ttf" \
#     "https://raw.githubusercontent.com/google/fonts/ac502d8eff76ef4d9477cdcc8ef7d0c84fde5372/ofl/fraunces/Fraunces%5BSOFT,WONK,opsz,wght%5D.ttf"
SOURCE_SHA256 = "177ff6c0f14e5550a3c624247cd1189611d4eb65d000b14944c63d967958abbb"

# The axis pins this ticket needs (docs/plans/10-blank-landing-screen.md, L6): 24pt optical
# size, SemiBold weight, no soft/wonky variants.
AXIS_LIMITS = {"opsz": 24, "wght": 600, "SOFT": 0, "WONK": 0}

# fontTools.varLib.instancer can rewrite the name table for us (updateFontNames=True), but
# that requires a STAT Axis Value entry for every pinned axis value. Fraunces's STAT table
# only declares named Axis Values for opsz 9/72/144 (its three static-adjacent optical
# sizes), not 24, so that call fails with "Cannot find Axis Values {'opsz': 24.0}". Verified
# against fonttools 4.67.0. We therefore instantiate with updateFontNames=False (which keeps
# the *default-instance* names, "Fraunces"/"Regular" family and "Fraunces 9pt Black"
# typographic name, and only prunes the STAT-only name records that reference axis values
# the instance no longer varies over) and then overwrite the family/subfamily name records
# ourselves, following Google Fonts' own naming convention for Fraunces's static optical-size
# cuts ("Fraunces <N>pt" family, weight subfamily) since no 24pt static cut is published.
NAME_OVERRIDES = {
    1: "Fraunces",
    2: "SemiBold",
    3: "1.000;UCT;Fraunces24pt-SemiBold",
    4: "Fraunces 24pt SemiBold",
    5: "Version 1.000",
    6: "Fraunces24pt-SemiBold",
    16: "Fraunces 24pt",
    17: "SemiBold",
}

# name IDs above 255 are STAT axis-value names (e.g. "Optical Size", "Softness", "Wonky")
# that no longer apply once the font is a single static instance; name ID 25 is the
# STAT-only "variations PostScript name prefix". Both become stale once fvar/STAT are gone.
STALE_NAME_IDS = {25} | set(range(256, 32768))


def load_source() -> TTFont:
    data = SOURCE_PATH.read_bytes()
    digest = sha256(data).hexdigest()
    if digest != SOURCE_SHA256:
        raise SystemExit(
            f"{SOURCE_PATH}: SHA-256 is {digest}, expected {SOURCE_SHA256}. The source font "
            "changed or was re-downloaded from an un-pinned ref: verify its provenance "
            "before regenerating."
        )
    return TTFont(str(SOURCE_PATH))


def build_instance() -> TTFont:
    font = load_source()
    instancer.instantiateVariableFont(
        font,
        AXIS_LIMITS,
        inplace=True,
        updateFontNames=False,
    )
    if "STAT" in font:
        del font["STAT"]

    name = font["name"]
    for name_id in STALE_NAME_IDS:
        name.removeNames(nameID=name_id)
    for name_id, value in NAME_OVERRIDES.items():
        name.setName(value, name_id, 1, 0, 0)  # Macintosh
        name.setName(value, name_id, 3, 1, 0x409)  # Windows, en-US
    return font


def assert_static_instance(font: TTFont, label: str) -> list[str]:
    errors = []
    if "fvar" in font:
        errors.append(f"{label}: still has an fvar table (not a static instance).")
    if "STAT" in font:
        errors.append(f"{label}: still has a STAT table (not a static instance).")
    name = font["name"]
    family = name.getDebugName(1)
    subfamily = name.getDebugName(2)
    if family != "Fraunces":
        errors.append(f"{label}: name ID 1 (family) is {family!r}, expected 'Fraunces'.")
    if subfamily != "SemiBold":
        errors.append(f"{label}: name ID 2 (subfamily) is {subfamily!r}, expected 'SemiBold'.")
    return errors


def generate() -> None:
    font = build_instance()
    errors = assert_static_instance(font, "freshly generated instance")
    if errors:
        print("FAILED:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        raise SystemExit(1)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = OUT_DIR / "fraunces_semibold.ttf"
    font.save(str(out_path))
    print(f"Wrote {out_path}.")
    print(
        "Review it (family/weight, glyph coverage) before copying it over "
        f"{COMMITTED_PATH} and re-running 'check'."
    )


def check() -> None:
    errors = []

    try:
        font = build_instance()
        errors.extend(assert_static_instance(font, "freshly generated instance"))
    except SystemExit as error:
        errors.append(str(error))
        font = None

    if not COMMITTED_PATH.exists():
        errors.append(f"{COMMITTED_PATH}: missing committed font file.")
    else:
        committed = TTFont(str(COMMITTED_PATH))
        errors.extend(assert_static_instance(committed, "committed font"))

        if font is not None:
            committed_cmap = committed.getBestCmap()
            fresh_cmap = font.getBestCmap()
            if set(committed_cmap) != set(fresh_cmap):
                errors.append(
                    "committed font: cmap character coverage differs from a freshly "
                    "generated instance from the same pinned source — the committed file "
                    "may predate the current source/axis pins."
                )

    if errors:
        print("FAILED:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        raise SystemExit(1)

    print("All checks passed.")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else None
    if mode == "generate":
        generate()
    elif mode == "check":
        check()
    else:
        print("Usage: python generate.py <generate|check>", file=sys.stderr)
        raise SystemExit(1)
