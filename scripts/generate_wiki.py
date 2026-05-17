#!/usr/bin/env python3
"""
generate_wiki.py — Auto-generate IdleFantasy GitHub Wiki pages from game data assets.

Usage:
    python3 scripts/generate_wiki.py

Reads JSON assets from app/src/main/assets/data/, generates markdown pages,
clones the GitHub wiki repo, overwrites the auto-generated pages, and pushes.
"""

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT  = Path(__file__).parent.parent
ASSETS     = REPO_ROOT / "app/src/main/assets/data"
WIKI_REPO  = "git@github.com:tristinbaker/IdleFantasy.wiki.git"
WIKI_DIR   = Path("/tmp/IdleFantasy-wiki")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def load(rel_path: str) -> dict | list:
    return json.loads((ASSETS / rel_path).read_text())

def title(key: str) -> str:
    return key.replace("_", " ").title()

def fmt_materials(mats: dict) -> str:
    return ", ".join(f"{qty}× {title(item)}" for item, qty in mats.items())

def fmt_pct(chance: float) -> str:
    pct = chance * 100
    return f"{pct:.1f}%" if pct < 1 else f"{pct:.0f}%"

def table(headers: list[str], rows: list[list]) -> str:
    sep = " | "
    header_row  = sep.join(headers)
    divider_row = sep.join("---" for _ in headers)
    data_rows   = "\n".join(sep.join(str(c) for c in row) for row in rows)
    return f"| {header_row} |\n| {divider_row} |\n" + "\n".join(f"| {sep.join(str(c) for c in row)} |" for row in rows)

def session_minutes(level: int) -> int:
    """Mirrors SkillSimulator.sessionDurationMs() — 60→40 min linear across levels 1–99."""
    fraction = (level - 1) / 98.0
    return round(60 - 20 * fraction)


# ---------------------------------------------------------------------------
# Page generators
# ---------------------------------------------------------------------------

def gen_home(pages: list[str]) -> str:
    links = "\n".join(f"- [[{p.removesuffix('.md')}]]" for p in sorted(pages) if p != "Home")
    return f"""# Idle Fantasy Wiki

Welcome to the community wiki for **Idle Fantasy** — an idle RPG with skills, combat, and crafting.

All data on these pages is auto-generated from the game's data files and is always up to date with the latest release.

## Pages

{links}

---
*This wiki is auto-generated. To report errors, open an [issue](https://github.com/tristinbaker/IdleFantasy/issues).*
"""


def gen_skills() -> str:
    skill_list = [
        ("Mining",       "gathering", "Extract ores and gems from the earth."),
        ("Fishing",      "gathering", "Catch fish and aquatic creatures."),
        ("Woodcutting",  "gathering", "Chop trees for logs."),
        ("Farming",      "gathering", "Plant seeds and harvest crops."),
        ("Firemaking",   "gathering", "Burn logs for XP. Produces ashes for Prayer."),
        ("Agility",      "gathering", "Reduces session time across all skills (60→40 min at level 99)."),
        ("Smithing",     "crafting",  "Smelt ores into bars and forge equipment."),
        ("Cooking",      "crafting",  "Cook raw food to restore HP in combat."),
        ("Fletching",    "crafting",  "Craft bows and arrows."),
        ("Crafting",     "crafting",  "Make jewellery and other items."),
        ("Runecrafting", "crafting",  "Craft runes from rune essence."),
        ("Herblore",     "crafting",  "Brew potions for combat stat boosts."),
        ("Attack",       "combat",    "Increases melee accuracy."),
        ("Strength",     "combat",    "Increases max melee damage."),
        ("Defense",      "combat",    "Reduces damage taken."),
        ("Ranged",       "combat",    "Attack from a distance with a bow."),
        ("Magic",        "combat",    "Cast spells using runes."),
        ("Hitpoints",    "combat",    "Total health. Increases with combat."),
        ("Prayer",       "combat",    "Bury bones to unlock combat prayers."),
    ]
    rows = [[f"[[{s}]]" if s in {"Mining","Fishing","Woodcutting","Farming","Firemaking","Agility",
                                   "Smithing","Cooking","Fletching","Crafting","Runecrafting","Herblore",
                                   "Enemies","Prayer"} else s,
             c.title(), d] for s, c, d in skill_list]
    return f"# Skills\n\nIdle Fantasy has 19 skills split across three categories.\n\n{table(['Skill','Category','Description'], rows)}\n\nAll skills cap at **level 99**.\n"


def gen_agility() -> str:
    courses = load("agility_courses.json")
    sorted_courses = sorted(courses.values(), key=lambda c: c["level_required"])

    course_rows = []
    for c in sorted_courses:
        laps_per_min = 2
        success_rate = 0.90  # approximate mid-point
        xp_per_min   = round(laps_per_min * c["xp_per_success"] * success_rate)
        xp_per_session = xp_per_min * 60
        course_rows.append([
            c["display_name"],
            c["level_required"],
            c["xp_per_success"],
            f"~{xp_per_min:,}",
            f"~{xp_per_session:,}",
        ])

    duration_rows = []
    for level in [1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99]:
        mins = session_minutes(level)
        duration_rows.append([level, f"{mins} min"])

    return f"""# Agility

Agility reduces session time across **all skills**. Higher levels mean faster sessions everywhere.

## Session Duration by Level

{table(['Agility Level', 'Session Duration'], duration_rows)}

Duration scales linearly from 60 minutes at level 1 to 40 minutes at level 99.

## Courses

There are {len(courses)} courses. Success rate scales with how far your level exceeds the requirement (capped at 95%). Each minute you attempt **2 laps**.

{table(['Course', 'Level Required', 'XP / Lap', 'XP / Min (est.)', 'XP / Session (est.)'], course_rows)}

> XP estimates assume ~90% success rate. At the exact level requirement success rate is 60%; it reaches 95% when your level is 17+ above the requirement.
"""


def gen_mining() -> str:
    ores = load("ores.json")
    rows = sorted(
        [[o["display_name"], o["level_required"], o["xp_per_ore"]]
         for o in ores.values()],
        key=lambda r: r[1]
    )
    return f"# Mining\n\nMine ores from the earth. Gems have a small chance to drop alongside any ore.\n\n{table(['Ore','Level Required','XP / Ore'], rows)}\n\nTools: equip a **Pickaxe** (bronze through runite) to increase ore yield.\n"


def gen_woodcutting() -> str:
    trees = load("trees.json")
    rows = sorted(
        [[t["display_name"], t["level_required"], t["xp_per_log"], t["log_display_name"]]
         for t in trees.values()],
        key=lambda r: r[1]
    )
    return f"# Woodcutting\n\nChop trees to gather logs used in Fletching and Firemaking.\n\n{table(['Tree','Level Required','XP / Log','Log'], rows)}\n\nTools: equip an **Axe** (bronze through runite) to increase log yield.\n"


def gen_firemaking() -> str:
    logs = load("logs.json")
    rows = sorted(
        [[l["display_name"], l["level_required"], l["xp_per_log"]]
         for l in logs.values()],
        key=lambda r: r[1]
    )
    return f"# Firemaking\n\nBurn logs from your inventory to gain Firemaking XP. Each log burned produces **ashes** which can be scattered for bonus Prayer XP.\n\n{table(['Log','Level Required','XP / Log Burned'], rows)}\n"


def gen_fishing() -> str:
    fish_data = load("skills/fishing.json")
    xp_ranges = fish_data.get("xp_ranges", {})
    rows = sorted(
        [[f"Level {k}+", v["min"], v["max"], f"{round((v['min']+v['max'])/2*60):,}"]
         for k, v in xp_ranges.items()],
        key=lambda r: int(r[0].split()[1].rstrip("+"))
    )
    return f"# Fishing\n\nFish from the waters. XP and drops scale with your Fishing level.\n\n{table(['Level Tier','Min XP / Min','Max XP / Min','Avg XP / Session'], rows)}\n\nTools: equip a **Fishing Rod** (bronze through runite) to increase catch rate.\n"


def gen_farming() -> str:
    crops = load("crops.json")
    rows = sorted(
        [[
            f"{c.get('emoji','')} {c['display_name']}",
            c["farming_level_required"],
            title(c["seed_name"]),
            c.get("seed_cost", "—"),
            f"{c['growth_time_hours']}h",
            c.get("planting_xp", "—"),
            c.get("harvest_xp", "—"),
            f"{c.get('yield_min',1)}–{c.get('yield_max',1)}",
        ] for c in crops.values()],
        key=lambda r: r[1]
    )
    return f"""# Farming

Plant seeds in up to **5 patches** and return after the growth time to harvest.

{table(['Crop','Level','Seed','Seed Cost','Growth Time','Planting XP','Harvest XP','Yield'], rows)}

Seeds can be purchased from the **Shop** under Seeds & Farming. Equip a **Hoe** to increase harvest yield.
"""


def gen_runecrafting() -> str:
    runes = load("runes.json")
    rows = sorted(
        [[
            r["display_name"],
            r["level_required"],
            r["essence_cost"],
            r["xp_per_rune"],
            "×2 at 50 / ×3 at 75",
        ] for r in runes.values()],
        key=lambda r: r[1]
    )
    return f"""# Runecrafting

Craft runes from Rune Essence. Higher Runecrafting levels unlock stronger runes. At level 50+ you produce 2 runes per essence; at level 75+ you produce 3.

{table(['Rune','Level Required','Essence / Rune','XP / Rune','Output Multiplier'], rows)}

Rune Essence can be purchased from the **Shop**.
"""


def gen_prayer() -> str:
    bones = load("bones.json")
    rows = sorted(
        [[b["display_name"], b["xp_per_bone"]]
         for b in bones.values()],
        key=lambda r: r[1]
    )
    return f"""# Prayer

Bury bones to earn Prayer XP. Higher-tier bones give more XP. Ashes from Firemaking can also be scattered.

{table(['Bone / Ash','XP Each'], rows)}

> Tip: Dragon and superior bones give the most XP per session. Farm higher-level dungeons for better bone drops.
"""


def gen_smithing() -> str:
    recipes = load("recipes/smithing.json")
    groups = {"bar": [], "weapon": [], "armour": [], "tool": [], "other": []}
    for key, r in recipes.items():
        t = r.get("type", "other")
        g = t if t in groups else "other"
        groups[g].append([r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]])

    sections = []
    order = [("bar", "Bars"), ("weapon", "Weapons"), ("armour", "Armour"), ("tool", "Tools")]
    for group_key, group_name in order:
        rows = sorted(groups[group_key], key=lambda r: r[1])
        if rows:
            sections.append(f"## {group_name}\n\n{table(['Item','Level','Materials','XP / Item'], rows)}")

    return "# Smithing\n\nSmelt ores into bars, then forge equipment. All smithing is done in sessions.\n\n" + "\n\n".join(sections) + "\n"


def gen_cooking() -> str:
    recipes = load("recipes/cooking.json")
    rows = sorted(
        [[r["display_name"], r["level_required"], title(r["raw_item"]), r["xp_per_item"], r.get("healing_value", "—")]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return f"# Cooking\n\nCook raw ingredients into food that restores HP in combat.\n\n{table(['Food','Level','Raw Ingredient','XP / Item','HP Healed'], rows)}\n\nCooked food is automatically used in dungeons via the **Equipped Food** system.\n"


def gen_fletching() -> str:
    recipes = load("recipes/fletching.json")
    rows = sorted(
        [[r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return f"# Fletching\n\nCraft bows and arrows from logs and metal components.\n\n{table(['Item','Level','Materials','XP / Item'], rows)}\n"


def gen_crafting() -> str:
    recipes = load("recipes/crafting.json")
    rows = sorted(
        [[r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return f"# Crafting\n\nCreate jewellery and other items from precious materials.\n\n{table(['Item','Level','Materials','XP / Item'], rows)}\n"


def gen_herblore() -> str:
    recipes = load("recipes/herblore.json")
    rows = sorted(
        [[
            r["display_name"],
            r["level_required"],
            fmt_materials(r["materials"]),
            ", ".join(f"{stat.title()} +{val}" for stat, val in r.get("effects", {}).items()),
            r["xp_per_item"],
        ] for r in recipes.values()],
        key=lambda r: r[1]
    )
    return f"""# Herblore

Brew potions from farming crops and dungeon reagents. Potions give flat combat stat boosts and can be selected before entering a dungeon or boss fight.

{table(['Potion','Level','Ingredients','Effect','XP'], rows)}

> Herblore secondary ingredients (imp hide, rotten flesh, etc.) drop from dungeon enemies and are protected from Sell Junk.
"""


def gen_equipment() -> str:
    equip = load("equipment.json")
    slot_order = ["weapon", "head", "body", "legs", "boots", "cape", "ring", "necklace",
                  "shield", "pickaxe", "axe", "fishing_rod", "hoe"]
    slot_names = {
        "weapon": "Weapons", "head": "Helmets", "body": "Chestplates", "legs": "Legs",
        "boots": "Boots", "cape": "Capes", "ring": "Rings", "necklace": "Necklaces",
        "shield": "Shields", "pickaxe": "Pickaxes", "axe": "Axes",
        "fishing_rod": "Fishing Rods", "hoe": "Hoes",
    }
    by_slot: dict[str, list] = {s: [] for s in slot_order}
    for item in equip.values():
        slot = item.get("slot", "other")
        if slot in by_slot:
            reqs = ", ".join(f"{title(sk)} {lv}" for sk, lv in item.get("requirements", {}).items()) or "—"
            by_slot[slot].append([
                item["display_name"],
                item.get("attack_bonus", 0) or 0,
                item.get("strength_bonus", 0) or 0,
                item.get("defense_bonus", 0) or 0,
                item.get("mining_efficiency") or item.get("woodcutting_efficiency") or
                item.get("fishing_efficiency") or item.get("farming_efficiency") or "—",
                reqs,
            ])

    sections = []
    for slot in slot_order:
        rows = by_slot.get(slot, [])
        if not rows:
            continue
        rows.sort(key=lambda r: r[0])
        sections.append(f"## {slot_names.get(slot, title(slot))}\n\n{table(['Item','Atk','Str','Def','Efficiency','Requirements'], rows)}")

    return "# Equipment\n\nAll wearable gear in Idle Fantasy, grouped by slot.\n\n" + "\n\n".join(sections) + "\n"


def gen_enemies() -> str:
    enemies = load("enemies.json")
    sections = []
    for e in sorted(enemies.values(), key=lambda x: x["hp"]):
        name = e["display_name"]
        hp   = e["hp"]
        cs   = e.get("combat_stats", {})
        atk  = cs.get("attack", "—")
        st   = cs.get("strength", "—")
        df   = cs.get("defense", "—")
        xp   = e.get("xp_drops", {})
        xp_str = ", ".join(f"{title(sk)} {v}" for sk, v in xp.items()) if xp else "—"

        drops = e.get("drop_table", [])
        always = e.get("always_drops", [])
        drop_rows = []
        for d in always:
            qty = d.get("quantity", d.get("quantity_min", 1))
            drop_rows.append([title(d["item"]), "100%", qty])
        for d in drops:
            qty_min = d.get("quantity_min", 1)
            qty_max = d.get("quantity_max", qty_min)
            qty_str = str(qty_min) if qty_min == qty_max else f"{qty_min}–{qty_max}"
            drop_rows.append([title(d["item"]), fmt_pct(d["chance"]), qty_str])

        drop_table = table(["Item", "Chance", "Qty"], drop_rows) if drop_rows else "_No drops._"
        sections.append(f"### {name}\n\n**HP:** {hp} | **Atk:** {atk} | **Str:** {st} | **Def:** {df} | **XP:** {xp_str}\n\n{drop_table}")

    return "# Enemies\n\nAll enemies found in dungeons, ordered by HP.\n\n" + "\n\n".join(sections) + "\n"


def gen_dungeons() -> str:
    dungeon_files = sorted(ASSETS.glob("dungeons/*.json"))
    sections = []
    for f in dungeon_files:
        d = json.loads(f.read_text())
        name    = d["display_name"]
        rec_lv  = d.get("recommended_level", "—")
        desc    = d.get("description", "")
        spawns  = d.get("enemy_spawns", [])
        total_w = sum(s.get("weight", 1) for s in spawns)
        rows = [[title(s["enemy"]), s.get("weight", 1), f"{s.get('weight',1)/total_w*100:.0f}%"]
                for s in spawns]
        spawn_table = table(["Enemy", "Weight", "Spawn Chance"], rows) if rows else ""
        sections.append(f"## {name}\n\n**Recommended Level:** {rec_lv}\n\n{desc}\n\n{spawn_table}")

    return "# Dungeons\n\nEach dungeon session lasts the standard session duration (reduced by Agility). Enemies spawn randomly according to the weights below.\n\n" + "\n\n".join(sections) + "\n"


def gen_bosses() -> str:
    bosses = load("raid_bosses.json")
    sections = []
    for b in sorted(bosses.values(), key=lambda x: x.get("combat_level_required", 0)):
        name     = f"{b.get('emoji','')} {b['display_name']}"
        req_lv   = b.get("combat_level_required", "—")
        hp       = b.get("hp", "—")
        duration = b.get("duration_minutes", "—")
        xp       = b.get("xp_rewards", {})
        xp_str   = ", ".join(f"{title(sk)} {v}" for sk, v in xp.items()) if xp else "—"

        loot = b.get("common_loot", {})
        loot_rows = []
        coins = loot.get("coins")
        if coins:
            loot_rows.append(["Coins", "100%", f"{coins.get('min',0):,}–{coins.get('max',0):,}"])
        for item, info in loot.items():
            if item == "coins":
                continue
            if isinstance(info, dict):
                qty = f"{info.get('min',1)}–{info.get('max',1)}" if "min" in info else str(info.get("quantity", 1))
                loot_rows.append([title(item), "100%", qty])

        rare = b.get("rare_drops", [])
        for r in rare:
            chance = fmt_pct(r.get("chance", 0.005))
            loot_rows.append([title(r.get("item","?")), chance, r.get("quantity", 1)])

        pet = b.get("pet")
        if pet:
            loot_rows.append([f"{pet.get('name','Pet')} (pet)", fmt_pct(pet.get("chance", 0.005)), 1])

        loot_table = table(["Item", "Chance", "Qty"], loot_rows) if loot_rows else "_No loot defined._"
        desc = b.get("description", "")
        sections.append(f"## {name}\n\n**Combat Level Required:** {req_lv} | **HP:** {hp} | **Duration:** {duration} min\n\n{desc}\n\n**XP Rewards:** {xp_str}\n\n{loot_table}")

    return "# Bosses\n\nRaid bosses are high-difficulty encounters with unique loot.\n\n" + "\n\n".join(sections) + "\n"


# ---------------------------------------------------------------------------
# Wiki repo management
# ---------------------------------------------------------------------------

def clone_wiki():
    if WIKI_DIR.exists():
        shutil.rmtree(WIKI_DIR)
    subprocess.run(["git", "clone", WIKI_REPO, str(WIKI_DIR)], check=True)

def write_pages(pages: dict[str, str]):
    for filename, content in pages.items():
        (WIKI_DIR / filename).write_text(content)

def commit_and_push():
    subprocess.run(["git", "-C", str(WIKI_DIR), "add", "-A"], check=True)
    result = subprocess.run(
        ["git", "-C", str(WIKI_DIR), "diff", "--cached", "--quiet"]
    )
    if result.returncode == 0:
        print("Wiki is already up to date — nothing to push.")
        return
    subprocess.run(
        ["git", "-C", str(WIKI_DIR), "commit", "-m", "Auto-update wiki from game data"],
        check=True,
    )
    subprocess.run(["git", "-C", str(WIKI_DIR), "push"], check=True)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    pages: dict[str, str] = {
        "Skills.md":      gen_skills(),
        "Agility.md":     gen_agility(),
        "Mining.md":      gen_mining(),
        "Woodcutting.md": gen_woodcutting(),
        "Firemaking.md":  gen_firemaking(),
        "Fishing.md":     gen_fishing(),
        "Farming.md":     gen_farming(),
        "Runecrafting.md":gen_runecrafting(),
        "Prayer.md":      gen_prayer(),
        "Smithing.md":    gen_smithing(),
        "Cooking.md":     gen_cooking(),
        "Fletching.md":   gen_fletching(),
        "Crafting.md":    gen_crafting(),
        "Herblore.md":    gen_herblore(),
        "Equipment.md":   gen_equipment(),
        "Enemies.md":     gen_enemies(),
        "Dungeons.md":    gen_dungeons(),
        "Bosses.md":      gen_bosses(),
    }
    pages["Home.md"] = gen_home(list(pages.keys()))

    print(f"Generated {len(pages)} pages.")

    clone_wiki()
    write_pages(pages)
    commit_and_push()
    print("Done.")


if __name__ == "__main__":
    main()
