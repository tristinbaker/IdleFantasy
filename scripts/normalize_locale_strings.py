"""Rewrite every locale strings file in the English file's order (issue #1233).

For each locale file: entries appear in exactly the order of the corresponding
English file, inheriting the English file's comments and section structure.
Existing translations are preserved byte-for-byte (moved to the canonical file
if they lived in the wrong one). Keys missing from a locale are inserted with
the English text plus an ``<!-- untranslated -->`` marker. Keys already marked
untranslated are rewritten to the current English text so stubs stay in sync
when base strings change. Keys no longer in the English file are dropped.
``translatable="false"`` entries stay base-only.

MARK_IDENTICAL=1 additionally marks pre-existing entries whose text is
byte-identical to English (one-time pass; translators delete false positives).
"""
from pathlib import Path
import os
import re

RES = Path(__file__).parents[1] / "app" / "src" / "main" / "res"
MARKER = "<!-- untranslated -->"

# strings.xml first, then alphabetical: scan and emission order decide which file wins
# when a key appears in several, so the order must be deterministic across machines
# (glob order is OS-dependent) with the canonical main file trumping the rest.
STRING_FILENAMES = sorted(
    (x.name for x in (RES / "values").glob("strings*.xml")),
    key=lambda name: (name != "strings.xml", name),
)

EN_ENTRY = re.compile(
    r'[ \t]*<(string(?:-array)?|plurals)\s[^>]*name="([^"]+)".*?</\1>', re.DOTALL)
LOC_ENTRY = re.compile(
    r'[ \t]*<(string(?:-array)?|plurals)\s[^>]*name="([^"]+)".*?</\1>'
    r'(?:[ \t]*<!--\s*untranslated\s*-->)?', re.DOTALL)


def inner(elem):
    """Element content between the first '>' and the final '</', for equality tests."""
    start = elem.find(">") + 1
    end = elem.rfind("</")
    return elem[start:end].strip()


def reindent(elem):
    lines = elem.split("\n")
    lines[0] = "    " + lines[0].lstrip()
    return "\n".join(lines)


def main():
    mark_identical = os.environ.get("MARK_IDENTICAL") == "1"

    for lang_dir in RES.glob("values-*"):
        if not lang_dir.is_dir():
            continue

        # Collect the locale's entries from every file (translations sometimes live
        # in a different file than the English key does; first occurrence wins, so
        # scan in FILES order for determinism — the canonical main file trumps
        # strays, and freshly-submitted values trump stale duplicates).
        loc = {}
        scan_order = [f for f in STRING_FILENAMES if (lang_dir / f).exists()] + sorted(
            f.name for f in lang_dir.iterdir()
            if f.name.startswith("strings") and f.name.endswith(".xml") and f.name not in STRING_FILENAMES
        )
        for filename in scan_order:
            content = open(lang_dir / filename, encoding="utf-8").read()
            for m in LOC_ENTRY.finditer(content):
                name = m.group(2)
                if name in loc:
                    continue
                elem = m.group(0)
                had_marker = "untranslated" in elem[elem.rfind("</"):]
                if had_marker:
                    elem = elem[: elem.rfind(">") + 1]  # strip trailing marker comment
                    elem = re.sub(r"[ \t]*<!--\s*untranslated\s*-->$", "", elem)
                loc[name] = (elem.strip("\n"), had_marker)

        emitted = set()
        added = kept = refreshed = marked = 0
        for filename in STRING_FILENAMES:
            en_path = RES / "values" / filename
            if not en_path.exists():
                continue
            en_content = open(en_path, encoding="utf-8").read()

            out = []
            pos = 0
            for m in EN_ENTRY.finditer(en_content):
                out.append(en_content[pos:m.start()])
                pos = m.end()
                en_elem = m.group(0)
                name = m.group(2)
                if 'translatable="false"' in en_elem or name in emitted:
                    continue
                emitted.add(name)
                if name in loc:
                    elem, had_marker = loc[name]
                    if had_marker:
                        # Stubs must mirror base exactly when English changes.
                        if inner(elem) != inner(en_elem):
                            refreshed += 1
                        out.append(reindent(en_elem) + " " + MARKER)
                        marked += 1
                    else:
                        mark = mark_identical and inner(elem) == inner(en_elem)
                        out.append(reindent(elem) + (" " + MARKER if mark else ""))
                        marked += mark
                    kept += 1
                else:
                    out.append(reindent(en_elem) + " " + MARKER)
                    added += 1
                    marked += 1
            out.append(en_content[pos:])
            with open(lang_dir / filename, "w", encoding="utf-8") as f:
                f.write("".join(out))

        stale = len(loc) - sum(1 for k in loc if k in emitted)
        print(f"{lang_dir.name}: kept {kept}, added {added}, refreshed {refreshed}, marked {marked}, dropped {stale} stale")


if __name__ == "__main__":
    main()
