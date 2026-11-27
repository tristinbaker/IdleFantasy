#!/usr/bin/env python3
"""Regenerate the main story strings from the writer's story doc (.docx).

Run from the repo root:
    python3 scripts/extract_story_strings.py "path/to/Idle Fantasy Story Doc.docx"

Writes app/src/main/res/values/strings_story.xml (English only), one string per
pop-up page, then run scripts/normalize_locale_strings.py to refresh the locale stubs.

Tracked changes in the doc are read as accepted (insertions kept, deletions dropped).
Each page is found by its opening words rather than its paragraph position, so the
writer can add notes or design text anywhere without shifting the mapping. If an
opening is edited, the script stops and names the page; update its anchor below.
Page and note ids must stay in step with data/model/MainStory.kt.
"""
import html
import re
import sys
import zipfile

OUT = "app/src/main/res/values/strings_story.xml"

# (note id, opening words of the page). Pages of one note are listed in order.
PAGES = [
    ("intro", "A tall Human man stands before"),
    ("intro", "“Hail, contractor, and welcome to the"),
    ("intro", "Your briefing after the fact is,"),
    ("landing_welcome", "“Welcome to the Island!” Thaddius’ voice"),
    ("landing_gather_start", "“Ah, I see you’ve chosen to"),
    ("landing_gather_done", "“There’s nothing like hard work to"),
    ("landing_craft_start", "“Supplies are fantastic to have but"),
    ("landing_cook_start", "“Nothing can be done well on"),
    ("landing_farm_start", "“Now that you’ve made landfall I"),
    ("landing_farm_done", "The farmhand, a tall Orc woman,"),
    ("landing_farm_done", "“Hah! I’m glad to know our"),
    ("imp_prompt", "As your outpost is unfurling and"),
    ("imp_start", "“Hail, Duster! Good work in tracking"),
    ("imp_done", "As you strike down the last"),
    ("goblin_prompt", "Seemingly agitated and stirred by the"),
    ("goblin_done", "The battle was harder than you"),
    ("spider_start", "As your people recover from the"),
    ("spider_done", "With the Broodmother felled, for the"),
    ("spider_done", "“Duster,” Thaddius calls to you with"),
    ("bandit_done", "Thaddius was correct in asserting the"),
    ("crypt_start", "As you return to your outpost to"),
    ("crypt_done", "Exhausted, lightly wounded, and covered in"),
    ("crypt_done", "As you take the time to"),
    ("fortress_done", "As you and your force muster"),
    ("fortress_done", "With the Fortress Ruins cleared, at"),
    ("fortress_done", "Thaddius’ voice over the sending stone"),
    ("orc_start", "You’ve gathered everything of importance that"),
    ("orc_done", "To say that the fight was"),
    ("orc_return", "As you return to your outpost your"),
    ("orc_return", "The four battlemages wore heavy adamantite"),
    ("orc_return", "“The molt of a great and"),
    ("volcanic_start", "To plunge into a volcano in"),
    ("volcanic_done", "Demons are not easy foes to"),
    ("volcanic_done", "As you and your party exit"),
    ("dragon_start", "Almost every household back home tells"),
    ("dragon_done", "Many heroic legends back home end"),
    ("dragon_done", "The trek back to your outpost"),
    ("infernal_start", "The great pyre burns bright and"),
    ("infernal_start", "As you cautiously lower yourself into"),
    ("infernal_done", "With each Chaos-warped demon that fell,"),
    ("infernal_done", "After much-needed recovery from traveling beyond"),
    ("temple_start", "You and your forces muster outside"),
    ("temple_start", "As you hold the key and"),
    ("temple_start", "Much like the gateway to the"),
    ("temple_start", "The necromancer turns to peer at"),
    ("temple_done", "The slithering creatures that burst up"),
    # The duel and ending play when the Kur'zul boss falls.
    ("kurzul_done", "Kur’zul grunts and growls with frustration"),
    ("kurzul_done", "As Kur’zul slumps and then collapses"),
    ("kurzul_done", "“A vessel was promised and a"),
    ("kurzul_done", "Then, as swiftly as the form"),
]

# Scene labels the writer puts in front of a page ("Upon completion of ...: ").
LABEL = re.compile(r"^(Upon|Additional|Another|Second|Quest Prompt|Undead Crypt|Fortress Ruins)[^:.“”]{0,60}:\s*")


def accepted_paragraphs(docx):
    xml = zipfile.ZipFile(docx).read("word/document.xml").decode("utf-8")
    out = []
    for p in re.findall(r"<w:p[ >].*?</w:p>", xml, re.S):
        p = re.sub(r"<w:del\s[^>]*/>", "", p)
        p = re.sub(r"<w:del\s[^>]*>.*?</w:del>", "", p, flags=re.S)
        text = "".join(html.unescape(t) for t in re.findall(r"<w:t(?:\s[^>]*)?>(.*?)</w:t>", p, re.S))
        out.append(re.sub(r"\s{2,}", " ", LABEL.sub("", text.strip(), count=1)))
    return out


def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    return "\\" + s if s[:1] in "@?" else s


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    paras = accepted_paragraphs(sys.argv[1])
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Main story quest text (v2.0.0). Generated by scripts/extract_story_strings.py from the story doc: one string per pop-up page. -->",
             "<resources>"]
    page_no = {}
    errors = []
    for note, anchor in PAGES:
        hits = [p for p in paras if p.lstrip("“").startswith(anchor.lstrip("“"))]
        if len(hits) != 1:
            errors.append(f"{note}: {len(hits)} paragraphs start with {anchor!r}")
            continue
        text = hits[0].strip("“”") if note == "imp_prompt" else hits[0]
        if "%" in text:
            errors.append(f"{note}: contains %, which Android would read as a format argument")
        page_no[note] = page_no.get(note, 0) + 1
        lines.append(f'    <string name="story_{note}_{page_no[note]}">{esc(text)}</string>')
    if errors:
        sys.exit("Nothing written:\n  " + "\n  ".join(errors))
    lines.append("</resources>")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{len(PAGES)} pages written to {OUT}")


main()
