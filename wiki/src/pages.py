"""
pages.py - Defines methods for generating IdleFantasy GitHub wiki pages from game data assets

Reads JSON assets from app/src/main/assets/data/ and generates appropriate Markdown content
"""

from __future__ import annotations

import json
import logging
import traceback
from dataclasses import dataclass
from logging import log
from pathlib import Path
from typing import Callable

from wiki.src import ASSETS, TEMPLATES
from wiki.src.page_hierarchy import PageHierarchy


# ---------------------------------------------------------------------------
# Page Listings
# ---------------------------------------------------------------------------

@dataclass
class PageInfo:
    title: str
    url: str
    generate: Callable[[], str] | NotImplemented


PAGE_DIRECTORY: dict[str, PageInfo] = {}
PAGE_HIERARCHY: PageHierarchy = PageHierarchy()


def add_static_pages():
    """Registers all static wiki pages."""
    # Todo: Add combat page with strategy information explaining what attack, etc, does - link in boss and enemy pages

    # Add pages not in the hierarchy
    PAGE_DIRECTORY.update({
        "sidebar": PageInfo("Sidebar", "_Sidebar.md", gen_sidebar)
    })

    # Add pages in both the directory and hierarchy
    pages = [
        ("home", PageInfo("Home", "Home.md", gen_home)),
        ["Skills", False, [
            ("skills", PageInfo("Skills", "Skills.md", gen_skills)),
            ["Gathering", False, [
                ("mining", PageInfo("Mining", "Mining.md", gen_mining)),
                ("fishing", PageInfo("Fishing", "Fishing.md", gen_fishing)),
                ("woodcutting", PageInfo("Woodcutting", "Woodcutting.md", gen_woodcutting)),
                ("farming", PageInfo("Farming", "Farming.md", gen_farming)),
                ("agility", PageInfo("Agility", "Agility.md", gen_agility)),
                ("thieving", PageInfo("Thieving", "Thieving.md", gen_thieving))
            ]],
            ["Crafting", False, [
                ("smithing", PageInfo("Smithing", "Smithing.md", gen_smithing)),
                ("cooking", PageInfo("Cooking", "Cooking.md", gen_cooking)),
                ("fletching", PageInfo("Fletching", "Fletching.md", gen_fletching)),
                ("crafting", PageInfo("Crafting", "Crafting.md", gen_crafting)),
                ("firemaking", PageInfo("Firemaking", "Firemaking.md", gen_firemaking)),
                ("runecrafting", PageInfo("Runecrafting", "Runecrafting.md", gen_runecrafting)),
                ("herblore", PageInfo("Herblore", "Herblore.md", gen_herblore)),
                ("construction", PageInfo("Construction", "Construction.md", gen_construction))
            ]],
            ["Support", False, [
                ("prayer", PageInfo("Prayer", "Prayer.md", gen_prayer)),
                ("mercantile", PageInfo("Mercantile", "Mercantile.md", gen_mercantile)),
            ]],
            ["Combat", False, [
                ("slayer", PageInfo("Slayer", "Slayer.md", gen_slayer)),
            ]],
        ]],
        ["Inventory", False, [
            ("equipment", PageInfo("Equipment", "Equipment.md", gen_equipment)),
        ]],
        ["Combat", False, [
            ("bosses", PageInfo("Bosses", "Bosses.md", gen_bosses)),
            ("dungeons", PageInfo("Dungeons", "Dungeons.md", gen_dungeons)),
            ("enemies", PageInfo("Enemies", "Enemies.md", gen_enemies)),
            ("spells", PageInfo("Spells", "Spells.md", gen_spells)),
        ]],
        ["Town", False, [
            ("shop", PageInfo("Shop", "Shop.md", gen_shop)),
            ("workers", PageInfo("Workers", "Workers.md", gen_workers)),
            ("guilds", PageInfo("Guilds", "Guilds.md", gen_guilds)),
            ("buildings", PageInfo("Buildings", "Buildings.md", gen_buildings)),
            ("game_corner", PageInfo("Game Corner", "Game-Corner.md", gen_game_corner)),
        ]],
        ["Miscellaneous", False, [
            ("expeditions", PageInfo("Expeditions", "Expeditions.md", gen_expeditions)),
            ("pets", PageInfo("Pets", "Pets.md", gen_pets)),
            ("quests", PageInfo("Quests", "Quests.md", gen_quests)),
        ]],
    ]

    # Convert into form suitable for hierarchy merge function
    def _make_hierarchical(page_list: list[tuple[str, PageInfo] | list]):
        items = []
        for x in page_list:
            if isinstance(x, list): # pagify the contents
                items.append([x[0], x[1], _make_hierarchical(x[2])])
            else: # Append only the name
                items.append(x[0])
        return items

    # Add pages to hierarchy
    PAGE_HIERARCHY.merge(_make_hierarchical(pages))

    # Add pages to directory, ignoring the hierarchical structure
    # Note: The `pages` variable is no longer in a usable state after running this so it should be done last
    while len(pages) > 0:
        item = pages.pop(0)
        if isinstance(item, list): # Add all subpages
            pages += item[2]
        else: # Add page
            PAGE_DIRECTORY.update({item[0]: item[1]})


def add_boss_pages():
    bosses = load("raid_bosses.json")
    assert isinstance(bosses, dict)
    boss_pages = {
        boss_id: PageInfo(bosses[boss_id]["display_name"], f"{boss_id}.md", lambda x=bosses[boss_id]: gen_boss(x))
        for boss_id in bosses.keys()
    }
    PAGE_DIRECTORY.update(boss_pages)
    # Todo: Remove once Page Hierarchies are collapsible
    # PAGE_HIERARCHY.merge([
    #     ["Combat", True, [
    #         ["Bosses", [boss_id for boss_id in boss_pages.keys()]],
    #     ]]
    # ])


def add_enemy_pages():
    enemies = load("enemies.json")
    assert isinstance(enemies, dict)
    enemy_pages = {
        enemy_id: PageInfo(
            enemies[enemy_id]["display_name"],
            f"{enemy_id}.md",
            lambda entry=enemies[enemy_id]: gen_enemy(entry),
        )
        for enemy_id in enemies.keys()
    }
    PAGE_DIRECTORY.update(enemy_pages)
    # Todo: Remove once Page Hierarchies are collapsible
    # PAGE_HIERARCHY.merge([
    #     ["Combat", True, [
    #         ["Enemies", [enemy_id for enemy_id in enemy_pages.keys()]],
    #     ]]
    # ])


def add_dungeon_pages():
    dungeons = sorted(
        (load(f, False) for f in (ASSETS / "dungeons").glob("*.json")),
        key=lambda d: d.get("recommended_level", 0),
    )
    dungeon_pages = {
        dungeon["name"]: PageInfo(
            dungeon["display_name"],
            f"{dungeon['name']}.md",
            lambda entry=dungeon: gen_dungeon(entry),
        )
        for dungeon in dungeons
    }
    PAGE_DIRECTORY.update(dungeon_pages)
    # Todo: Remove once Page Hierarchies are collapsible
    # PAGE_HIERARCHY.merge([
    #     ["Combat", True, [
    #         ["Dungeons", [dungeon["name"] for dungeon in dungeons]],
    #     ]]
    # ])


def add_expedition_pages():
    expeditions = sorted(
        (load(f, False) for f in (ASSETS / "skilling_dungeons").glob("*.json")),
        key=lambda d: (d["skill"], d["level_required"]),
    )
    expedition_pages = {
        exp["name"]: PageInfo(
            exp["display_name"],
            f"{exp['name']}.md",
            lambda entry=exp: gen_expedition(entry),
        )
        for exp in expeditions
    }
    PAGE_DIRECTORY.update(expedition_pages)

# ---------------------------------------------------------------------------
# Main functions
# ---------------------------------------------------------------------------

def get_pages() -> dict[str, str]:
    return {info.url: info.generate() for info in PAGE_DIRECTORY.values()}


def check_wiki_validity():
    print("Starting wiki validation")

    # Check hierarchy and directory links
    # Get all pages in the hierarchy
    pages_in_hierarchy = set()
    listing_items = [PAGE_HIERARCHY]
    while len(listing_items) > 0:
        item = listing_items.pop(0)
        if isinstance(item, str):
            pages_in_hierarchy.add(item)
        else:
            listing_items += [x for x in item]
    # Confirm page listing has all pages
    print("Checking page directory...")
    for page in pages_in_hierarchy:
        if page not in PAGE_DIRECTORY:
            print(f"Critical: Page '{page}' is listed in the hierarchy but not in the directory")
    # Confirm all directory items are in the hierarchy excluding special pages (e.g. Sidebar/Footer)
    print("Checking hierarchy...")
    for page_id, page_info in PAGE_DIRECTORY.items():
        if page_id not in pages_in_hierarchy and not page_info.url.startswith("_"):
            print(f"Warning: Page '{page_id}' is listed in the directory but not present in the hierarchy")

    # Ensure all pages can generate content
    print("Checking page content...")
    for page_id, page_info in PAGE_DIRECTORY.items():
        if page_info.generate is NotImplemented:
            print(f"Critical: Page '{page_id}' does not contain a method to create content")
        else:
            try:
                page_info.generate()
            except:
                print(f"Critical: Creating content for page '{page_id}' failed due to the below error")
                print(f"\033[91m{traceback.format_exc()}\033[00m")

    print("Validation complete")

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def get_template(name: str) -> str:
    """Gets a template file by name"""
    try:
        with open(TEMPLATES / f"{name}.md", encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError as e:
        print(f"Error: The requested template '{name}' does not exist")
        raise e


def load(rel_path: str | Path, prefix_assets: bool = True) -> dict | list:
    path = (ASSETS / rel_path) if prefix_assets else Path(rel_path)
    return json.loads(path.read_text(encoding="utf-8"))


def title(key: str) -> str:
    return key.replace("_", " ").title()


def fmt_materials(mats: dict) -> str:
    return ", ".join(f"{qty}× {item_link(item)}" for item, qty in mats.items())


def fmt_pct(chance: float) -> str:
    pct = chance * 100
    return f"{pct:.1f}%" if pct < 1 else f"{pct:.0f}%"


def table(headers: list[str], rows: list[list]) -> str:
    sep = " | "
    header_row  = sep.join(headers)
    divider_row = sep.join("---" for _ in headers)
    data_rows   = "\n".join(f"| {sep.join(str(c) for c in row)} |" for row in rows)
    return f"| {header_row} |\n| {divider_row} |\n{data_rows}"


def session_minutes(level: int) -> int:
    """Mirrors SkillSimulator.sessionDurationMs() — 60→40 min linear across levels 1–99."""
    fraction = (level - 1) / 98.0
    return round(60 - 20 * fraction)


def link(page_id: str, display_name: str | None = None):
    page = PAGE_DIRECTORY[page_id]
    return f"[{page.title if display_name is None else display_name}]({page.url.removesuffix('.md')})"


def html_link(page_id: str, display_name: str | None = None) -> str:
    """HTML anchor for use inside raw HTML blocks where Markdown links are not parsed."""
    page = PAGE_DIRECTORY[page_id]
    name = page.title if display_name is None else display_name
    return f'<a href="{page.url.removesuffix(".md")}">{name}</a>'


def _tool_table(slot: str, efficiency_key: str) -> str:
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    tools = sorted(
        [v for v in equipment.values() if v.get("slot") == slot and efficiency_key in v],
        key=lambda v: (list(v.get("requirements", {}).values() or [0])[0], v[efficiency_key])
    )
    rows = [[t["display_name"], list(t.get("requirements", {}).values() or [1])[0], f"{t[efficiency_key]:.2f}×"] for t in tools]
    return table(["Tool", "Level Required", "Efficiency"], rows)

_ITEM_PAGE_MAP: dict[str, str] | None = None


def build_item_page_map() -> dict[str, str]:
    global _ITEM_PAGE_MAP
    if _ITEM_PAGE_MAP is not None:
        return _ITEM_PAGE_MAP

    m: dict[str, str] = {}

    def _add(keys: list[str], page_id: str):
        for k in keys:
            if k and k not in m:
                m[k] = page_id

    # Equipment first — specific named items, highest priority
    _add(list(load("equipment.json").keys()), "equipment")
    # Bones and ashes → prayer
    _add(list(load("bones.json").keys()), "prayer")
    # Ores (including coal, rune_essence) → mining
    _add(list(load("ores.json").keys()), "mining")
    # Logs → woodcutting (keys from logs.json + log_name fields from trees.json)
    tree_log_names = [t["log_name"] for t in load("trees.json").values()]
    _add(list(load("logs.json").keys()) + tree_log_names, "woodcutting")
    # Runes → runecrafting
    _add(list(load("runes.json").keys()), "runecrafting")
    # Smithing outputs → smithing
    _add(list(load("recipes/smithing.json").keys()), "smithing")
    # Fish and raw fishing drops → fishing (before cooking so raw fish link here, not to cooking)
    fishing_data = load("skills/fishing.json")
    fish_items: list[str] = []
    for dt in fishing_data.get("drop_tables", {}).values():
        entries = dt if isinstance(dt, list) else dt.get("items", [])
        for drop in entries:
            if isinstance(drop, dict) and "item" in drop:
                fish_items.append(drop["item"])
    _add(fish_items, "fishing")
    # Cooked food outputs → cooking (raw ingredients intentionally excluded so raw fish link to fishing)
    _add(list(load("recipes/cooking.json").keys()), "cooking")
    # Fletching outputs → fletching
    _add(list(load("recipes/fletching.json").keys()), "fletching")
    # Crafting outputs → crafting
    _add(list(load("recipes/crafting.json").keys()), "crafting")
    # Herblore outputs → herblore
    _add(list(load("recipes/herblore.json").keys()), "herblore")
    # Crops and seeds → farming
    crops = load("crops.json")
    seed_keys = [c["seed_name"] for c in crops.values() if "seed_name" in c]
    _add(list(crops.keys()) + seed_keys, "farming")

    _ITEM_PAGE_MAP = m
    return m


def item_link(key: str) -> str:
    """Returns a markdown link to the page where this item is documented, or plain title if unknown."""
    page_id = build_item_page_map().get(key)
    if page_id:
        page = PAGE_DIRECTORY[page_id]
        return f"[{title(key)}]({page.url.removesuffix('.md')})"
    return title(key)


# ---------------------------------------------------------------------------
# Page Creation
# ---------------------------------------------------------------------------

def _gen_page_listing(pages: PageHierarchy, level: int = 2) -> str:
    content = ""
    for value in pages:
        if isinstance(value, str): # Add link
            content += f"- {link(value)}\n"
        else: # Add subsection
            content += f"\n{"#" * level} {value.name}\n"
            content += f"{_gen_page_listing(value, level + 1)}\n"
    # Return content without trailing newline/etc
    return content.strip()


def gen_home() -> str:
    links = _gen_page_listing(PAGE_HIERARCHY, 3)
    return get_template("home").format(links=links)


def gen_sidebar() -> str:
    return _gen_page_listing(PAGE_HIERARCHY)


def gen_skills() -> str:
    skill_list = [
        ("Mining", "gathering", "Extract ores and gems from the earth."),
        ("Fishing", "gathering", "Catch fish and aquatic creatures."),
        ("Woodcutting", "gathering", "Chop trees for logs."),
        ("Farming", "gathering", "Plant seeds and harvest crops."),
        ("Firemaking", "gathering", "Burn logs for XP. Produces ashes for Prayer."),
        ("Agility", "gathering", "Reduces session time across all skills (60→40 min at level 99)."),
        ("Thieving", "gathering", "Pickpocket NPCs in the Town for coins and loot."),
        ("Mercantile", "gathering",
         "Send trade caravans and explore skilling expeditions for lore and dungeon unlocks."),
        ("Smithing", "crafting", "Smelt ores into bars and forge equipment."),
        ("Cooking", "crafting", "Cook raw food to restore HP in combat."),
        ("Fletching", "crafting", "Craft bows and arrows."),
        ("Crafting", "crafting", "Make jewellery and other items."),
        ("Runecrafting", "crafting", "Craft runes from rune essence."),
        ("Herblore", "crafting", "Brew potions for combat stat boosts."),
        ("Construction", "crafting", "Build furniture used to upgrade town buildings (Inn, Guild Hall, Church)."),
        ("Attack", "combat", "Increases melee accuracy."),
        ("Strength", "combat", "Increases max melee damage."),
        ("Defense", "combat", "Reduces damage taken."),
        ("Ranged", "combat", "Attack from a distance with a bow."),
        ("Magic", "combat", "Cast spells using runes."),
        ("Hitpoints", "combat", "Total health. Increases with combat."),
        ("Prayer", "combat", "Bury bones to unlock combat prayers."),
        ("Slayer", "combat", "Receive tasks from the Slayer Master to kill specific enemies for bonus XP and points."),
    ]
    rows = [[link(skill.lower()) if skill.lower() in PAGE_DIRECTORY else skill, cat, desc] for skill, cat, desc
            in skill_list]

    prestige_rows = [
        ["Attack",    "+5 Attack per prestige level (up to +15 at prestige 3)"],
        ["Strength",  "+5 Strength per prestige level (up to +15 at prestige 3)"],
        ["Defense",   "+5 Defense per prestige level (up to +15 at prestige 3)"],
        ["Ranged",    "+5 Ranged per prestige level (up to +15 at prestige 3)"],
        ["Magic",     "+5 Magic per prestige level (up to +15 at prestige 3)"],
        ["Hitpoints", "+5 Hitpoints per prestige level (+50 max HP per level, up to +150 at prestige 3)"],
        ["All other skills", "XP bonus only"],
    ]

    return get_template("skills/skills").format(
        skills_table=table(["Skill", "Category", "Description"], rows),
        prestige_table=table(["Skill", "Bonus (in addition to +10% XP)"], prestige_rows),
    )


def gen_mining() -> str:
    ores = load("ores.json")
    assert isinstance(ores, dict)
    # Todo: Add information about how ore amounts change depending on pickaxe, etc
    rows = sorted(
        [[o["display_name"], o["level_required"], o["xp_per_ore"]]
         for o in ores.values()],
        key=lambda r: r[1]
    )
    tool_rows = _tool_table("pickaxe", "mining_efficiency")
    return get_template("skills/gathering/mining").format(
        ore_table=table(['Ore','Level Required','XP / Ore'], rows),
        pickaxe_table=tool_rows
    )


def gen_fishing() -> str:
    fish_data = load("skills/fishing.json")
    assert isinstance(fish_data, dict)
    # Todo: Adjust based upon new fishing mechanics
    xp_ranges = fish_data.get("xp_ranges", {})
    rows = sorted(
        [[f"Level {k}+", v["min"], v["max"], f"{round((v['min']+v['max'])/2*60):,}"]
         for k, v in xp_ranges.items()],
        key=lambda r: int(r[0].split()[1].rstrip("+"))
    )
    tool_rows = _tool_table("fishing_rod", "fishing_efficiency")
    return get_template("skills/gathering/fishing").format(
        fish_table=table(['Level Tier','Min XP / Min','Max XP / Min','Avg XP / Session'], rows),
        rod_table=tool_rows
    )


def gen_woodcutting() -> str:
    trees = load("trees.json")
    assert isinstance(trees, dict)
    rows = sorted(
        [[t["display_name"], t["level_required"], t["xp_per_log"], t["log_display_name"]]
         for t in trees.values()],
        key=lambda r: r[1]
    )
    tool_rows = _tool_table("axe", "woodcutting_efficiency")
    return get_template("skills/gathering/woodcutting").format(
        tree_table=table(['Tree','Level Required','XP / Log','Log'], rows),
        axe_table=tool_rows
    )


def gen_farming() -> str:
    # Todo: Add detail about using ashes to improve yield
    crops = load("crops.json")
    assert isinstance(crops, dict)
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
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    hoes = sorted(
        [v for v in equipment.values() if v.get("slot") == "hoe" and "farming_efficiency" in v],
        key=lambda v: list(v.get("requirements", {}).values() or [0])[0]
    )
    hoe_rows = [[h["display_name"], list(h.get("requirements", {}).values() or [1])[0], f"+{int(h['farming_efficiency']*100)}%"] for h in hoes]
    return get_template("skills/gathering/farming").format(
        seed_table=table(['Crop','Level','Seed','Seed Cost','Growth Time','Planting XP','Harvest XP','Yield'], rows),
        hoe_table=table(['Hoe','Level Required','Yield Bonus'], hoe_rows)
    )


def gen_agility() -> str:
    courses = load("agility_courses.json")
    assert isinstance(courses, dict)
    sorted_courses = sorted(courses.values(), key=lambda x: x["level_required"])

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

    return get_template("skills/gathering/agility").format(
        session_duration_table=table(["Agility Level", "Session Duration"], duration_rows),
        course_count=len(courses),
        course_table=table(['Course', 'Level Required', 'XP / Lap', 'XP / Min (est.)', 'XP / Session (est.)'], course_rows)
    )


def gen_smithing() -> str:
    recipes = load("recipes/smithing.json")
    assert isinstance(recipes, dict)
    groups = {"bar": [], "weapon": [], "armour": [], "tool": [], "component": [], "other": []}
    for key, r in recipes.items():
        t = r.get("type", "other")
        g = t if t in groups else "other"
        groups[g].append([r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]])

    if len(groups["other"]) > 0:
        log(logging.WARNING, "Some smithing items were in the 'other' group which are not shown on the page")

    sections = []
    # Todo: Fix missing armour and weapons sections
    order = [("armour", "Armour"), ("bar", "Bars"), ("component", "Components"), ("tool", "Tools"), ("weapon", "Weapons")]
    for group_key, group_name in order:
        rows = sorted(groups[group_key], key=lambda x: x[1])
        if rows:
            sections.append(f"## {group_name}\n\n{table(['Item','Level','Materials','XP / Item'], rows)}")

    return get_template("skills/crafting/smithing").format(sections="\n\n".join(sections))


def gen_cooking() -> str:
    recipes = load("recipes/cooking.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[r["display_name"], r["level_required"], item_link(r["raw_item"]), r["xp_per_item"], r.get("healing_value", "—")]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/cooking").format(
        food_table=table(['Food','Level','Raw Ingredient','XP / Item','HP Healed'], rows)
    )


def gen_fletching() -> str:
    recipes = load("recipes/fletching.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/fletching").format(item_table=table(['Item','Level','Materials','XP / Item'], rows))


def gen_crafting() -> str:
    recipes = load("recipes/crafting.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[r["display_name"], r["level_required"], fmt_materials(r["materials"]), r["xp_per_item"]]
         for r in recipes.values()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/crafting").format(item_table=table(['Item','Level','Materials','XP / Item'], rows))


def gen_firemaking() -> str:
    # Todo: Add details about using ashes for Rune Crafting, etc
    logs = load("logs.json")
    assert isinstance(logs, dict)
    rows = sorted(
        [[l["display_name"], l["level_required"], l["xp_per_log"]]
         for l in logs.values()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/firemaking").format(item_table=table(['Log','Level Required','XP / Log Burned'], rows))


def gen_runecrafting() -> str:
    # Todo: Add details for using ashes
    runes = load("runes.json")
    assert isinstance(runes, dict)
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
    return get_template("skills/crafting/runecrafting").format(
        runes_table=table(['Rune','Level Required','Essence / Rune','XP / Rune','Output Multiplier'], rows)
    )


def gen_herblore() -> str:
    recipes = load("recipes/herblore.json")
    assert isinstance(recipes, dict)
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
    return get_template("skills/crafting/herblore").format(potion_table=table(['Potion','Level','Ingredients','Effect','XP'], rows))


def gen_construction() -> str:
    recipes = load("recipes/construction.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [
            [r["display_name"], r["level_required"], fmt_materials(r["materials"]), int(r["xp_per_item"])]
            for r in recipes.values()
        ],
        key=lambda r: r[1],
    )
    return get_template("skills/crafting/construction").format(
        item_table=table(["Item", "Level", "Materials", "XP / Item"], rows)
    )


def gen_thieving() -> str:
    npcs = load("thieving_npcs.json")
    assert isinstance(npcs, list)
    rows = []
    for npc in npcs:
        loot_parts = []
        for entry in npc.get("loot_table", []):
            qty_str = ""
            if entry.get("min_qty") and entry.get("max_qty"):
                qty_str = f" ({entry['min_qty']}-{entry['max_qty']})"
            loot_parts.append(f"{fmt_pct(entry['chance'])} {item_link(entry['item'])}{qty_str}")
        rows.append([
            npc["display_name"],
            npc["level_required"],
            npc["base_xp"],
            f"{npc['coins_min']}-{npc['coins_max']}",
            ", ".join(loot_parts),
        ])
    return get_template("skills/gathering/thieving").format(
        npc_table=table(["NPC", "Level", "XP / Steal", "Coins", "Possible Loot"], rows)
    )


def gen_prayer() -> str:
    # Todo: Add info about bone altar
    bones = load("bones.json")
    assert isinstance(bones, dict)
    rows = sorted(
        [[b["display_name"], b["xp_per_bone"]]
         for b in bones.values()],
        key=lambda r: r[1]
    )
    return get_template("skills/support/prayer").format(prayer_table=table(['Bone / Ash','XP Each'], rows))


def gen_mercantile() -> str:
    # Trade routes
    route_rows = []
    for f in sorted((ASSETS / "trade_routes").glob("*.json")):
        routes = load(f, False)
        if isinstance(routes, dict):
            routes = [routes]
        for r in routes:
            low_xp  = list(r["xp_ranges"].values())[0]
            high_xp = list(r["xp_ranges"].values())[-1]
            low_c   = list(r["coin_ranges"].values())[0]
            high_c  = list(r["coin_ranges"].values())[-1]
            route_rows.append([
                r["display_name"],
                r["level_required"],
                f"{r['coin_cost']:,}",
                f"{low_xp['min']}–{high_xp['max']}",
                f"{low_c['min']:,}–{high_c['max']:,}",
            ])
    route_rows.sort(key=lambda x: x[1])

    # Skilling expeditions
    exp_rows = []
    for f in sorted((ASSETS / "skilling_dungeons").glob("*.json")):
        d = load(f, False)
        assert isinstance(d, dict)
        xp_vals = list(d["xp_ranges"].values())
        xp_str  = f"{xp_vals[0]['min']}–{xp_vals[-1]['max']}"
        exp_rows.append([
            d["display_name"],
            title(d["skill"]),
            d["level_required"],
            xp_str,
            title(d.get("unlock_dungeon", "—")),
        ])
    exp_rows.sort(key=lambda x: (x[1], x[2]))

    return get_template("skills/support/mercantile").format(
        route_table=table(['Route', 'Level', 'Cost', 'XP / Min (range)', 'Coin Return (range)'], route_rows),
        expedition_table=table(['Expedition', 'Skill', 'Level Required', 'XP / Min (range)', 'Unlocks Dungeon'], exp_rows)
    )


def gen_expedition(entry: dict) -> str:
    xp_rows = [
        [f"Level {lv}+", f"{vals['min']}–{vals['max']}"]
        for lv, vals in entry["xp_ranges"].items()
    ]
    drop_rows = [
        [f"Level {lv}+", ", ".join(f"{fmt_pct(d['chance'])} {item_link(d['item'])}" for d in drops)]
        for lv, drops in entry["drop_tables"].items()
    ]
    notes = "\n".join(
        f"{i + 1}. _{note}_"
        for i, note in enumerate(entry.get("note_texts", []))
    )
    req = entry.get("requires_previous_unlock")
    requires_str = f"\n**Requires:** {link(req)} unlocked first" if req else ""
    unlock = entry.get("unlock_dungeon")
    unlock_str = f"\n**Unlocks:** {link(unlock)}" if unlock else ""
    return get_template("miscellaneous/expedition").format(
        name=entry["display_name"],
        skill=title(entry["skill"]),
        level_required=entry["level_required"],
        requires_str=requires_str,
        unlock_str=unlock_str,
        description=entry.get("description", ""),
        xp_table=table(["Level", "XP / Minute"], xp_rows),
        drop_table=table(["Level", "Possible Drops"], drop_rows),
        notes_list=notes,
        note_threshold=entry.get("note_threshold", 5),
    )


def gen_expeditions() -> str:
    expeditions = sorted(
        (load(f, False) for f in (ASSETS / "skilling_dungeons").glob("*.json")),
        key=lambda d: (d["skill"], d["level_required"]),
    )
    rows = []
    for exp in expeditions:
        req = exp.get("requires_previous_unlock")
        unlock = exp.get("unlock_dungeon")
        rows.append([
            link(exp["name"]),
            title(exp["skill"]),
            exp["level_required"],
            link(req) if req else "—",
            link(unlock) if unlock else "—",
        ])
    return get_template("miscellaneous/expeditions").format(
        expedition_table=table(
            ["Expedition", "Skill", "Level Required", "Requires", "Unlocks"],
            rows,
        )
    )


def gen_slayer() -> str:
    tasks = load("slayer_tasks.json")
    assert isinstance(tasks, dict)
    rows = sorted(
        [
            [f"[{title(enemy)}](Enemies)", t["slayer_level"], f"{t['min_kills']}–{t['max_kills']}", t["xp_per_kill"]]
            for enemy, t in tasks.items()
        ],
        key=lambda r: r[1],
    )
    return get_template("skills/combat/slayer").format(
        task_table=table(['Enemy', 'Slayer Level', 'Kill Range', 'XP / Kill'], rows)
    )


def gen_equipment() -> str:
    equip = load("equipment.json")
    assert isinstance(equip, dict)
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

    return get_template("inventory/equipment").format(equipment="\n\n".join(sections))


def gen_combat_footer() -> str:
    dungeons = sorted(
        (load(f, False) for f in (ASSETS / "dungeons").glob("*.json")),
        key=lambda d: d.get("recommended_level", 0),
    )
    bosses = load("raid_bosses.json")
    enemies = load("enemies.json")
    assert isinstance(bosses, dict)
    assert isinstance(enemies, dict)
    return get_template("combat/combat_footer").format(
        dungeon_heading=html_link("dungeons"),
        boss_heading=html_link("bosses"),
        enemy_heading=html_link("enemies"),
        dungeon_links=", ".join(html_link(dungeon["name"]) for dungeon in dungeons),
        boss_links=", ".join(
            html_link(boss_id)
            for boss_id in sorted(bosses.keys(), key=lambda x: bosses[x].get("combat_level_required", 0))
        ),
        enemy_links=", ".join(
            html_link(enemy_id)
            for enemy_id, _ in sorted(enemies.items(), key=lambda x: x[1]["hp"])
        ),
        miscellaneous_links=", ".join([html_link("spells"), html_link("slayer")]),
    )


def gen_bosses() -> str:
    bosses = load("raid_bosses.json")
    assert isinstance(bosses, dict)
    rows = [
        [
            link(boss_id),
            boss.get("combat_level_required", "—"),
            boss.get("description", ""),
        ]
        for boss_id, boss in sorted(bosses.items(), key=lambda x: x[1].get("combat_level_required", 0))
    ]
    return get_template("combat/bosses").format(
        boss_table=table(["Boss", "Combat Level", "Description"], rows),
        combat_footer=gen_combat_footer(),
    )


def _dungeon_loot_rows(dungeon: dict, enemies: dict) -> list[list]:
    loot_enemies: dict[str, list[str]] = {}
    for spawn in dungeon.get("enemy_spawns", []):
        enemy_id = spawn.get("enemy")
        if not enemy_id:
            continue
        enemy = enemies.get(enemy_id, {})
        drops = enemy.get("always_drops", []) + enemy.get("drop_table", [])
        for drop in drops:
            item = drop["item"]
            enemy_ids = loot_enemies.setdefault(item, [])
            if enemy_id not in enemy_ids:
                enemy_ids.append(enemy_id)
    return [
        [item_link(item), ", ".join(link(enemy_id) for enemy_id in enemy_ids)]
        for item, enemy_ids in sorted(loot_enemies.items())
    ]


def gen_dungeon(dungeon: dict) -> str:
    enemies = load("enemies.json")
    assert isinstance(enemies, dict)
    # Create spawn rows
    spawns = dungeon.get("enemy_spawns", [])
    total_w = sum(s.get("weight", 1) for s in spawns)
    spawn_rows = [
        [link(s["enemy"]), s.get("weight", 1), f"{s.get('weight', 1) / total_w * 100:.0f}%"]
        for s in spawns
    ]
    # Create loot rows
    loot_rows = _dungeon_loot_rows(dungeon, enemies)
    return get_template("combat/dungeon").format(
        name=dungeon["display_name"],
        recommended_level=dungeon.get("recommended_level", "—"),
        description=dungeon.get("description", ""),
        spawn_table=table(["Enemy", "Weight", "Spawn Chance"], spawn_rows) if spawn_rows else "",
        loot_table=table(["Loot", "Dropped By"], loot_rows) if loot_rows else "_No loot._",
        combat_footer=gen_combat_footer(),
    )


def gen_dungeons() -> str:
    dungeons = sorted(
        (load(f, False) for f in (ASSETS / "dungeons").glob("*.json")),
        key=lambda d: d.get("recommended_level", 0),
    )
    rows = [
        [
            link(dungeon["name"]),
            dungeon.get("recommended_level", "—"),
            dungeon.get("description", ""),
        ]
        for dungeon in dungeons
    ]
    return get_template("combat/dungeons").format(
        dungeon_table=table(["Dungeon", "Recommended Level", "Description"], rows),
        combat_footer=gen_combat_footer(),
    )


def _get_dungeons_by_enemy(enemy_id: str) -> list[tuple[dict, dict]]:
    dungeons = sorted(
        (load(f, False) for f in (ASSETS / "dungeons").glob("*.json")),
        key=lambda d: d.get("recommended_level", 0),
    )
    results = []
    for dungeon in dungeons:
        for spawn in dungeon.get("enemy_spawns", []):
            if spawn.get("enemy") == enemy_id:
                results.append((dungeon, spawn))
                break
    return results


def gen_enemies() -> str:
    enemies = load("enemies.json")
    assert isinstance(enemies, dict)
    rows = [
        [
            link(enemy_id),
            enemy["hp"],
            enemy.get("xp_drops", {}).get("combat", "—"),
            ", ".join(dungeon["display_name"] for dungeon, _ in _get_dungeons_by_enemy(enemy_id)) or "—",
        ]
        for enemy_id, enemy in sorted(enemies.items(), key=lambda x: x[1]["hp"])
    ]
    return get_template("combat/enemies").format(
        boss_link=link("bosses"),
        enemy_table=table(["Enemy", "HP", "XP on kill", "Found in"], rows),
        combat_footer=gen_combat_footer(),
    )


def _enemy_drop_rows(enemy: dict) -> list[list]:
    drop_rows = []
    for drop in enemy.get("always_drops", []):
        qty = drop.get("quantity", drop.get("quantity_min", 1))
        drop_rows.append([item_link(drop["item"]), "100%", qty])
    for drop in enemy.get("drop_table", []):
        qty_min = drop.get("quantity_min", 1)
        qty_max = drop.get("quantity_max", qty_min)
        qty_str = str(qty_min) if qty_min == qty_max else f"{qty_min}–{qty_max}"
        drop_rows.append([item_link(drop["item"]), fmt_pct(drop["chance"]), qty_str])
    return drop_rows


def _enemy_dungeon_rows(enemy_id: str) -> list[list]:
    rows = []
    for dungeon, spawn in _get_dungeons_by_enemy(enemy_id):
        spawns = dungeon.get("enemy_spawns", [])
        total_w = sum(s.get("weight", 1) for s in spawns)
        rows.append([
            link(dungeon["name"]),
            dungeon.get("recommended_level", "—"),
            f"{spawn.get('weight', 1) / total_w * 100:.0f}%",
        ])
    return rows


def gen_enemy(enemy: dict) -> str:
    combat_stats = enemy.get("combat_stats", {})
    defensive_stats = enemy.get("defensive_stats", {})
    hp = enemy.get("hp", "—")
    xp = enemy.get("xp_drops", {}).get("combat", "—")
    drop_rows = _enemy_drop_rows(enemy)
    dungeon_rows = _enemy_dungeon_rows(enemy["name"])

    return get_template("combat/enemy").format(
        name=enemy["display_name"],
        hp=f"{hp:,}" if isinstance(hp, int) else hp,
        xp=f"{xp:,}" if isinstance(xp, int) else xp,
        attack=combat_stats.get("attack_level", 0) + combat_stats.get("attack_bonus", 0),
        attack_defence=defensive_stats.get("attack_defense", "—"),
        strength_defence=defensive_stats.get("strength_defense", "—"),
        ranged_defence=defensive_stats.get("ranged_defense", "—"),
        magic_defence=defensive_stats.get("magic_defense", "—"),
        loot_table=table(["Item", "Chance", "Qty"], drop_rows) if drop_rows else "_No drops._",
        dungeon_table=table(["Dungeon", "Combat Level", "Spawn Chance"], dungeon_rows) if dungeon_rows else "_Not found in any dungeon._",
        combat_footer=gen_combat_footer(),
    )


def gen_spells() -> str:
    spells = load("spells.json")
    assert isinstance(spells, dict)
    rows = sorted([
        [s["display_name"], s["magic_level_required"], title(s["rune_type"]), s["rune_cost"], s["max_hit"]]
        for s in spells.values()
    ], key=lambda r: r[1])
    return get_template("combat/spells").format(
        spell_table=table(["Spell", "Magic Level", "Rune", "Runes / Cast", "Max Hit"], rows),
        combat_footer=gen_combat_footer(),
    )


def _shop_item_rows(category: dict) -> list[list]:
    rows = []
    for item in category.get("items", {}).values():
        stock = item.get("stock", "unlimited")
        lvl_req = item.get("mercantile_level_required")
        req_str = f"Mercantile {lvl_req}" if lvl_req else "—"
        rows.append([
            item["display_name"],
            f"{item['price']:,}",
            stock.title() if isinstance(stock, str) else str(stock),
            req_str,
        ])
    return rows


def gen_shop() -> str:
    marketplace = load("marketplace.json")
    assert isinstance(marketplace, dict)
    section_template = get_template("town/shop_section")
    sections = []
    for category in marketplace.values():
        sections.append(section_template.format(
            category_name=category["category_name"],
            description=category.get("description", ""),
            item_table=table(["Item", "Price", "Stock", "Requirement"], _shop_item_rows(category)),
        ))

    return get_template("town/shop").format(shop_sections="\n\n".join(sections))


def _pet_boost(pet: dict) -> str:
    if pet.get("boost_percent"):
        skill = title(pet.get("boosted_skill", pet.get("effect_type", "")))
        return f"+{pet['boost_percent']}% {skill} XP"
    return pet.get("effect_type", "—")


def gen_pets() -> str:
    pets = load("pets.json")
    assert isinstance(pets, dict)
    rows = [
        [
            f"{pet.get('emoji', '')} {pet['display_name']}".strip(),
            pet.get("source", "—"),
            _pet_boost(pet),
            pet.get("description", "—"),
        ]
        for pet in sorted(pets.values(), key=lambda x: x["display_name"])
    ]
    return get_template("miscellaneous/pets").format(
        pet_table=table(["Pet", "Source", "Bonus", "Description"], rows),
    )


def gen_workers() -> str:
    # Worker tier stats (mirrored from WorkerTier enum)
    tiers = [
        ("Long Laborer", 8,  0.5,  5_000,  4.0,  "Uncapped (2 min/item)"),
        ("Apprentice",   8,  1.0,  10_000, 8.0,  "480 items"),
        ("Journeyman",   6,  1.25, 20_000, 7.5,  "360 items"),
        ("Master",       4,  2.0,  50_000, 8.0,  "240 items"),
    ]
    tier_rows = [
        [name, f"{dur}h", f"{eff:.2f}×", f"{cost:,}", f"{gather:.1f}×", craft]
        for name, dur, eff, cost, gather, craft in tiers
    ]
    tier_table = table(
        ["Tier", "Session Duration", "Efficiency", "Hire Cost", "Gathering Output", "Crafting Output"],
        tier_rows,
    )

    # Allowed skills (mirrors WorkerSkillsScreen: GATHERING minus FARMING, all CRAFTING_SKILLS, Prayer)
    gathering_skills = ["Mining", "Fishing", "Woodcutting", "Agility", "Thieving"]
    crafting_skills  = ["Smithing", "Cooking", "Fletching", "Crafting", "Firemaking", "Runecrafting", "Herblore", "Construction"]
    skill_rows = (
        [["Gathering", s] for s in gathering_skills] +
        [["Crafting",  s] for s in crafting_skills] +
        [["Support",   "Prayer"]]
    )
    skill_table = table(["Category", "Skill"], skill_rows)

    # Inn upgrade XP bonuses (tier 0–3: +0%, +10%, +20%, +30%)
    inn_rows = [[tier, f"×{1.0 + tier * 0.10:.2f}"] for tier in range(4)]
    inn_bonus_table = table(["Inn Tier", "Worker XP Multiplier"], inn_rows)

    return get_template("town/workers").format(
        tier_table=tier_table,
        skill_table=skill_table,
        inn_bonus_table=inn_bonus_table,
    )


def gen_guilds() -> str:
    guild_quests = load("guild_quests.json")
    assert isinstance(guild_quests, dict)

    # Reputation thresholds (mirrored from GuildRepository.REP_THRESHOLDS)
    rep_thresholds = [500, 1_500, 4_000, 9_000, 20_000, 40_000, 75_000, 140_000, 250_000, 450_000]
    rep_rows = [[lvl, f"{rep_thresholds[lvl - 1]:,}"] for lvl in range(1, 11)]
    rep_table = table(["Guild Level", "Reputation Required"], rep_rows)

    # Guild Hall reduction table (tier 0-3)
    reduction_rows = [
        [0, "No reduction (100%)"],
        [1, "10% fewer required (90%)"],
        [2, "20% fewer required (80%)"],
        [3, "30% fewer required (70%)"],
    ]
    reduction_table = table(["Guild Hall Tier", "Quest Requirement"], reduction_rows)

    # One section per guild, ordered to match ALL_GUILDS
    guild_order = [
        "mining", "fishing", "woodcutting", "farming", "firemaking", "agility",
        "smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore",
        "warriors", "archers", "mages", "prayer", "mercantile",
    ]
    guild_section_tpl = get_template("town/guild_section")
    sections = []
    for guild in guild_order:
        quests = sorted(
            [q for q in guild_quests.values() if q["guild"] == guild],
            key=lambda q: q["guild_level_required"],
        )
        rows = []
        for q in quests:
            r = q["rewards"]
            reward_parts = []
            if r.get("coins"):
                reward_parts.append(f"{r['coins']:,} coins")
            if r.get("xp"):
                reward_parts.append(f"{r['xp']:,} XP")
            if r.get("reputation"):
                reward_parts.append(f"{r['reputation']:,} rep")
            for item, qty in r.get("items", {}).items():
                reward_parts.append(f"{qty}x {title(item)}")
            rows.append([
                q["name"],
                q["guild_level_required"],
                title(q.get("target", "")),
                f"{q['amount']:,}",
                ", ".join(reward_parts),
            ])
        quest_table = table(["Quest", "Guild Level", "Target", "Amount", "Rewards"], rows)
        sections.append(guild_section_tpl.format(
            guild_name=title(guild),
            quest_table=quest_table,
        ))

    return get_template("town/guilds").format(
        rep_table=rep_table,
        reduction_table=reduction_table,
        guild_sections="\n\n".join(sections),
    )


def gen_buildings() -> str:
    # Building tiers mirrored from TownBuildingDef / TownRepository
    def building_table(tiers: list, bonus_col: str, bonuses: list[str]) -> str:
        rows = []
        rows.append([0, "—", "—", "—", "No bonus"])
        for i, (con_lvl, coins, mats, bonus) in enumerate(tiers, start=1):
            mat_str = ", ".join(f"{qty:,}x {title(item)}" for item, qty in mats.items())
            rows.append([i, con_lvl, f"{coins:,}", mat_str, bonus])
        return table(["Tier", "Construction Level", "Coin Cost", "Materials", bonus_col], rows)

    inn_tiers = [
        (20,   50_000,  {"plank": 200, "oak_plank": 100, "iron_nail": 500},        "Worker XP x1.10"),
        (45,  250_000,  {"oak_plank": 500, "willow_plank": 200, "steel_nail": 1500}, "Worker XP x1.20"),
        (70, 1_000_000, {"willow_plank": 1000, "maple_plank": 1000, "mithril_nail": 3000}, "Worker XP x1.30"),
    ]
    guild_hall_tiers = [
        (25,    75_000, {"oak_plank": 300, "iron_nail": 600},                          "Quest req. -10%"),
        (50,   350_000, {"willow_plank": 600, "steel_nail": 1500},                     "Quest req. -20%"),
        (75, 1_500_000, {"maple_plank": 1500, "yew_plank": 500, "mithril_nail": 3000}, "Quest req. -30%"),
    ]
    church_tiers = [
        (30,   100_000, {"oak_plank": 200, "carved_stone": 400, "steel_nail": 500},        "Blessing 30h"),
        (55,   500_000, {"willow_plank": 500, "stone_block": 600, "steel_nail": 1500},     "Blessing 36h"),
        (80, 2_000_000, {"yew_plank": 800, "stone_block": 1000, "mithril_nail": 3000},     "Blessing 48h"),
    ]

    return get_template("town/buildings").format(
        inn_table=building_table(inn_tiers, "Bonus", []),
        guild_hall_table=building_table(guild_hall_tiers, "Bonus", []),
        church_table=building_table(church_tiers, "Bonus", []),
    )


def gen_game_corner() -> str:
    exclusive_rows = [
        ["Ring of Fortune",    "Ring",     "Atk +20, Str +24, Def +16", "None", "1-in-1,000 on any win"],
        ["Amulet of Fortune",  "Necklace", "Atk +25, Str +32, Def +20", "None", "1-in-1,000 on any win"],
    ]
    slots_rows = [
        ["🍒 Cherry",   "8",  "2×"],
        ["🍋 Lemon",    "6",  "3×"],
        ["🍊 Orange",   "5",  "4×"],
        ["🔔 Bell",     "4",  "8×"],
        ["BAR",         "3", "10×"],
        ["7️⃣ Seven",    "2", "20×"],
        ["💎 Diamond",  "1", "50×"],
    ]
    lottery_rows = [
        ["75%", "No win"],
        ["15%", "1,500 coins"],
        ["8%",  "5,000 coins"],
        ["2%",  "20,000 coins"],
    ]
    blackjack_rows = [
        ["Blackjack (A + 10-value on deal)", "2.5×"],
        ["Win",                              "2×"],
        ["Push",                             "Bet returned"],
        ["Lose / Bust",                      "Bet lost"],
    ]
    roulette_rows = [
        ["Red / Black",    "~48.6%", "2×"],
        ["Odd / Even",     "~48.6%", "2×"],
        ["Dozen (1-12 / 13-24 / 25-36)", "~32.4%", "3×"],
        ["Single Number",  "2.7%",   "36×"],
    ]
    scratch_rows = [
        ["🍒 Cherry",  "30", "2×  price"],
        ["💰 Coin Bag","25", "5×  price"],
        ["⭐ Star",    "20", "10× price"],
        ["🔔 Bell",    "15", "20× price"],
        ["💎 Diamond", "10", "50× price"],
    ]
    poker_rows = [
        ["Royal Flush",      "800×"],
        ["Straight Flush",    "50×"],
        ["Four of a Kind",    "25×"],
        ["Full House",         "9×"],
        ["Flush",              "6×"],
        ["Straight",           "4×"],
        ["Three of a Kind",    "3×"],
        ["Two Pair",           "2×"],
        ["Jacks or Better",    "1×"],
    ]
    return get_template("town/game_corner").format(
        exclusive_table=table(["Item", "Slot", "Bonuses", "Requirements", "Source"], exclusive_rows),
        slots_table=table(["Symbol", "Reel Weight", "3-Match Payout"], slots_rows),
        lottery_table=table(["Chance", "Prize"], lottery_rows),
        blackjack_table=table(["Outcome", "Payout"], blackjack_rows),
        roulette_table=table(["Bet Type", "Win Chance", "Payout"], roulette_rows),
        scratch_table=table(["Symbol", "Cell Weight", "Row Payout"], scratch_rows),
        poker_table=table(["Hand", "Payout"], poker_rows),
    )


def _quest_rewards(rewards: dict) -> str:
    parts = []
    if rewards.get("coins"):
        parts.append(f"{rewards['coins']:,} coins")
    if rewards.get("xp"):
        parts.append(f"{rewards['xp']:,} XP")
    for item, qty in rewards.get("items", {}).items():
        parts.append(f"{qty}× {title(item)}")
    return ", ".join(parts) or "—"


def gen_quests() -> str:
    quests = load("quests.json")
    assert isinstance(quests, dict)
    by_skill: dict[str, list] = {}
    for quest in quests.values():
        by_skill.setdefault(quest["skill"], []).append(quest)

    sections = []
    for skill in sorted(by_skill.keys()):
        quest_rows = [
            [q["name"], q.get("description", "—"), _quest_rewards(q.get("rewards", {}))]
            for q in sorted(by_skill[skill], key=lambda q: (q["name"], q.get("tier", 0)))
        ]
        quest_table = table(["Quest", "Objective", "Rewards"], quest_rows)
        sections.append(f"## {title(skill)}\n\n{quest_table}")

    return get_template("miscellaneous/quests").format(quest_sections="\n\n".join(sections))


def gen_boss(boss: dict) -> str:
    combat_stats = boss.get("combat_stats", {})
    defensive_stats = boss.get("defensive_stats", {})
    hp = boss.get("hp", "—")

    # Add guaranteed loot
    common_loot_rows = []
    loot = boss.get("common_loot", {})
    coins_min = loot.get("coins_min")
    coins_max = loot.get("coins_max")
    if coins_min is not None and coins_max is not None:
        common_loot_rows.append(["Coins", coins_min, coins_max])
    for item, info in loot.get("items", {}).items():
        if isinstance(info, dict):
            min_loot = info.get('min', 1)
            max_loot = info.get('max', 1)
        else:
            min_loot = max_loot = str(info)
        common_loot_rows.append([item_link(item), min_loot, max_loot])

    # Add rare drops
    rare_loot_rows = []
    for drop in boss.get("rare_drops", []):
        rare_loot_rows.append([
            item_link(drop.get("item", "?")),
            fmt_pct(drop.get("chance", 0.005)),
        ])
    # Add pet chance
    pet = boss.get("pet")
    assert isinstance(pet, dict)
    if pet:
        pet_name = f"{pet.get('emoji', '')} {pet.get('display_name', 'Pet')}".strip()
        rare_loot_rows.append([f"[{pet_name}](Pets)", fmt_pct(pet.get("chance", 0.005))])

    defensive_rows = [
        ["Attack", defensive_stats.get("attack_defense", "—")],
        ["Strength", defensive_stats.get("strength_defense", "—")],
        ["Ranged", defensive_stats.get("ranged_defense", "—")],
        ["Magic", defensive_stats.get("magic_defense", "—")],
    ]

    xp = boss.get("xp_rewards", {})

    return get_template("combat/boss").format(
        icon=boss.get("emoji", ""),
        name=boss["display_name"],
        combat_level=boss.get("combat_level_required", "—"),
        hp=f"{hp:,}" if isinstance(hp, int) else hp,
        duration=boss.get("duration_minutes", "—"),
        description=boss.get("description", ""),
        boss_attack=combat_stats.get("attack_level", 0) + combat_stats.get("attack_bonus", 0),
        defensive_table=table(["Style", "Defence"], defensive_rows),
        boss_link=link("bosses"),
        xp_rewards=", ".join(f"{title(sk)} {v:,}" for sk, v in xp.items()) if xp else "—",
        loot_table=table(["Item", "Min", "Max"], common_loot_rows) if common_loot_rows else "_No loot defined._",
        rare_drops_table=table(["Item", "Chance"], rare_loot_rows) if rare_loot_rows else "_No rare drops._",
        combat_footer=gen_combat_footer(),
    )

# ---------------------------------------------------------------------------
# Adding pages to the directory/hierarchy
# ---------------------------------------------------------------------------

# Add all relevant pages to the hierarchy and page directory
add_static_pages()
add_boss_pages()
add_enemy_pages()
add_dungeon_pages()
add_expedition_pages()
