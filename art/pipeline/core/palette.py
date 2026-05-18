"""
palette.py — Mirror of the Kotlin tier palette from
`app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt`.

CLAUDE.md ground rule: do not fork the palette. This file mirrors the seven
tier colors so the Python pipeline can produce sprites that match the
in-app tier styling. If `Color.kt` changes, update this file to match.

The `verify_in_sync()` function below parses `Color.kt` at run time and
fails loudly if any tier color has drifted, so the mirror cannot silently
desync.
"""

from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT  = Path(__file__).resolve().parents[3]
COLOR_KT   = REPO_ROOT / "app/src/main/kotlin/com/fantasyidler/ui/theme/Color.kt"


# Tier colors, mirrored from Color.kt lines 46-52. Format: (R, G, B) 0-255.
TIER_BRONZE  = (0xCD, 0x7F, 0x32)
TIER_IRON    = (0x80, 0x80, 0x80)
TIER_STEEL   = (0x71, 0xA6, 0xD2)
TIER_MITHRIL = (0x6A, 0x0D, 0xAD)
TIER_ADAMANT = (0x2D, 0x6A, 0x4F)
TIER_RUNE    = (0x00, 0xB4, 0xD8)
TIER_DRAGON  = (0xD6, 0x28, 0x28)

TIERS: dict[str, tuple[int, int, int]] = {
    "bronze":  TIER_BRONZE,
    "iron":    TIER_IRON,
    "steel":   TIER_STEEL,
    "mithril": TIER_MITHRIL,
    "adamant": TIER_ADAMANT,
    "rune":    TIER_RUNE,
    "dragon":  TIER_DRAGON,
}


def _parse_color_kt() -> dict[str, tuple[int, int, int]]:
    """Parse Tier<Name> = Color(0xFFRRGGBB) declarations from Color.kt."""
    text = COLOR_KT.read_text()
    pat = re.compile(r"val\s+Tier(\w+)\s*=\s*Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\)")
    out: dict[str, tuple[int, int, int]] = {}
    for name, hex_rgb in pat.findall(text):
        rgb = tuple(int(hex_rgb[i:i+2], 16) for i in (0, 2, 4))
        out[name.lower()] = rgb  # type: ignore[assignment]
    return out


def verify_in_sync() -> None:
    """Raise AssertionError if Color.kt and TIERS disagree."""
    actual = _parse_color_kt()
    for tier, expected in TIERS.items():
        got = actual.get(tier)
        assert got == expected, (
            f"palette mismatch for Tier{tier.capitalize()}: "
            f"Color.kt={got}, palette.py={expected}. Update palette.py to match."
        )


if __name__ == "__main__":
    verify_in_sync()
    print("palette.py is in sync with Color.kt.")
