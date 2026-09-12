"""
pages.py - Defines methods for generating IdleFantasy GitHub wiki pages from game data assets

Reads JSON assets from app/src/main/assets/data/ and generates appropriate Markdown content
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from logging import log
from pathlib import Path
from typing import Callable

import yaml

from wiki.src import ASSETS, SPRITES, TEMPLATES, RESOURCES, REPO_ROOT, GITHUB_REPO, DEFAULT_ICON, IMAGES_DIR, GUIDES
from wiki.src.game_data import STRINGS, load, title, item_name, house_item_name, skill_name, skill_desc, enemy_name, guild_name, \
    trade_route_name, thieving_npc_name, quest_name, agility_course_name, town_building_name, quest_desc, title_name, \
    pet_name, boss_name, boss_desc, trade_route_desc, pet_desc, item_desc, dungeon_name, dungeon_desc, expedition_name, \
    expedition_desc, seasonal_event_name, seasonal_reward_desc, prestige_effect_desc, tree_name, merc_name, race_name, \
    carnival_prize_name, carnival_prize_desc, slot_name, blessing_name
from wiki.src.page_hierarchy import PageHierarchy
from wiki.src.wiki_logs import LOGGER


# ---------------------------------------------------------------------------
# Page Listings
# ---------------------------------------------------------------------------

@dataclass
class PageInfo:
    title: str
    url: str
    _generator_func: Callable[[], str] | NotImplemented
    icon: Path | None = None
    _string_cache: str | None = field(init=False)

    def __post_init__(self):
        self._string_cache = None

    def generate(self) -> str:
        generated = self._string_cache
        if generated is None:
            generated = self._string_cache = self._generator_func()
        return generated


PAGE_DIRECTORY: dict[str, PageInfo] = {}
PAGE_HIERARCHY: PageHierarchy = PageHierarchy()

def add_static_pages():
    """Registers all static wiki pages."""
    # Add pages not in the hierarchy
    PAGE_DIRECTORY.update({
        "sidebar": PageInfo("Sidebar", "_Sidebar.md", gen_sidebar)
    })

    # Add pages in both the directory and hierarchy
    pages = [
        ("home", PageInfo("Home", "Home.md", gen_home)),
        ["Contributing", False, [
            ("getting_started_game", PageInfo("Getting Started - Game Contributions", "getting_started_game.md", gen_getting_started_game)),
            ("getting_started_wiki", PageInfo("Getting Started - Wiki Contributions", "getting_started_wiki.md", gen_getting_started_wiki)),
            ("wiki_page_types", PageInfo("Types of Wiki Pages", "wiki_page_types.md", gen_wiki_page_types)),
        ]],
        ["Skills", False, [
            ("skills", PageInfo("Skills", "Skills.md", gen_skills)),
            ("quest_icons", PageInfo("Quest Icons", "QuestIcons.md", gen_quest_icons)),
            ["Gathering", False, [
                ("mining", PageInfo(skill_name("mining"), "Mining.md", gen_mining, skill_icon_path("mining"))),
                ("fishing", PageInfo(skill_name("fishing"), "Fishing.md", gen_fishing, skill_icon_path("fishing"))),
                ("woodcutting", PageInfo(skill_name("woodcutting"), "Woodcutting.md", gen_woodcutting, skill_icon_path("woodcutting"))),
                ("farming", PageInfo(skill_name("farming"), "Farming.md", gen_farming, skill_icon_path("farming"))),
                ("thieving", PageInfo(skill_name("thieving"), "Thieving.md", gen_thieving, skill_icon_path("thieving")))
            ]],
            ["Crafting", False, [
                ("smithing", PageInfo(skill_name("smithing"), "Smithing.md", gen_smithing, skill_icon_path("smithing"))),
                ("cooking", PageInfo(skill_name("cooking"), "Cooking.md", gen_cooking, skill_icon_path("cooking"))),
                ("fletching", PageInfo(skill_name("fletching"), "Fletching.md", gen_fletching, skill_icon_path("fletching"))),
                ("crafting", PageInfo(skill_name("crafting"), "Crafting.md", gen_crafting, skill_icon_path("crafting"))),
                ("firemaking", PageInfo(skill_name("firemaking"), "Firemaking.md", gen_firemaking, skill_icon_path("firemaking"))),
                ("runecrafting", PageInfo(skill_name("runecrafting"), "Runecrafting.md", gen_runecrafting, skill_icon_path("runecrafting"))),
                ("herblore", PageInfo(skill_name("herblore"), "Herblore.md", gen_herblore, skill_icon_path("herblore"))),
                ("construction", PageInfo(skill_name("construction"), "Construction.md", gen_construction, skill_icon_path("construction")))
            ]],
            ["Support", False, [
                ("prayer", PageInfo(skill_name("prayer"), "Prayer.md", gen_prayer, skill_icon_path("prayer"))),
                ("mercantile", PageInfo(skill_name("mercantile"), "Mercantile.md", gen_mercantile, skill_icon_path("mercantile"))),
                ("agility", PageInfo(skill_name("agility"), "Agility.md", gen_agility, skill_icon_path("agility"))),
            ]],
            ["Combat", False, [
                ("slayer", PageInfo(skill_name("slayer"), "Slayer.md", gen_slayer, skill_icon_path("slayer"))),
            ]],
        ]],
        ["Inventory", False, [
            ("equipment", PageInfo("Equipment", "Equipment.md", gen_equipment)),
            ("heirlooms", PageInfo("Heirlooms", "Heirlooms.md", gen_heirlooms)),
        ]],
        ["Combat", False, [
            ("combat", PageInfo("Combat", "Combat.md", gen_combat_page)),
            ("bosses", PageInfo("Bosses", "Bosses.md", gen_bosses)),
            ("dungeons", PageInfo("Dungeons", "Dungeons.md", gen_dungeons)),
            ("enemies", PageInfo("Enemies", "Enemies.md", gen_enemies)),
            ("spells", PageInfo("Spells", "Spells.md", gen_spells)),
            ("mercenaries", PageInfo("Mercenary Camp", "Mercenaries.md", gen_mercenaries)),
        ]],
        ["Town", False, [
            ("shop", PageInfo("Shop", "Shop.md", gen_shop)),
            ("workers", PageInfo("Workers", "Workers.md", gen_workers)),
            ("guilds", PageInfo("Guilds", "Guilds.md", gen_guilds)),
            ("buildings", PageInfo("Buildings", "Buildings.md", gen_buildings)),
            ("carnival", PageInfo("Carnival", "Carnival.md", gen_carnival)),
            ("housing", PageInfo("Housing", "Housing.md", gen_housing)),
        ]],
        ["Miscellaneous", False, [
            ("expeditions", PageInfo("Expeditions", "Expeditions.md", gen_expeditions, skill_icon_path("expedition"))),
            ("pets", PageInfo("Pets", "Pets.md", gen_pets)),
            ("quests", PageInfo("Quests", "Quests.md", gen_quests)),
            ("titles", PageInfo("Titles", "Titles.md", gen_titles)),
            ("seasonal_events", PageInfo("Seasonal Events", "SeasonalEvents.md", gen_seasonal_events)),
        ]],
        ["Guides", False, [
            ("how_to_make_player_guides", PageInfo("How to add strategy guides to the wiki", "how_to_make_guides.md", gen_how_to_make_player_guides))
        ]]
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


def _gen_guide_content(guide_id: str, images: set[str], page_links: set[str]):
    if any(x.split(".", 1)[0] in page_links for x in images):
        LOGGER.warn_by_id(guide_id, f"Failed to generate guide `{guide_id}: Guide lists images and pages with the same ID (Check guides.yml)")
        return f"Content failed to generate (Check conflicting images and page IDs in guides.yml)"
    if any(x not in PAGE_DIRECTORY for x in page_links):
        LOGGER.warn_by_id(guide_id, f"Failed to generate guide `{guide_id}: Guide lists page IDs which don't exist")
        return f"Content failed to generate (Check listed pages in guides.yml)"
    content = get_template(f"guides/{guide_id}").format(
        **({
            **{v.split(".", 1)[0]: html_image(IMAGES_DIR / "guides" / v, classes="guide-img") for v in images},
            **{v: link(v) for v in page_links}
        }),
        table_of_contents="{table_of_contents}"
    )
    if "{table_of_contents}" in content:
        return content.format(table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(content)}")
    return content


def add_player_guides():
    # Load player guides
    with open(TEMPLATES / "guides" / "guides.yml", "r") as f:
        guide_list = yaml.safe_load(f)
    # No player guides to add
    if not guide_list:
        return
    # Retrieve and validate guides
    guides = {}
    for gid, guide in guide_list.items():
        g_title = guide.get("title")
        author = guide.get("author")
        last_updated = guide.get("last_updated")
        images = guide.get("images") or []
        page_links = guide.get("page_links") or []
        related_pages = guide.get("related_pages") or []
        # Validate data
        required_fields = {"title": g_title, "author": author, "last_updated": last_updated}
        custom_generator = guide.get("custom_generator")
        # Ensure all fields are present
        missing_fields = [label for label, value in required_fields.items() if value is None]
        if len(missing_fields) > 0:
            LOGGER.warn_by_id(f"guide:{gid}", f"Skipping guide `{gid}`. The following required fields were missing in `guilds.yml`: {",".join(missing_fields)}")
            continue
        # Ensure custom generator exists if present
        if custom_generator:
            if custom_generator not in PLAYER_GUIDE_GENERATORS:
                LOGGER.warn_by_id(f"guide:{gid}", f"Skipping guide `{gid}`: a custom generator was specified but couldn't be found in pages.py. Check the docs")
                continue
            if images or page_links:
                LOGGER.warn_by_id(f"guide:{gid}", f"Skipping guide `{gid}`: Must remove images, page_links, or table_of_contents config options when using a custom generator")
                continue
            custom_generator = PLAYER_GUIDE_GENERATORS[custom_generator]
        # Check the guide file actually exists
        if not (GUIDES / f"{gid}.md").is_file():
            LOGGER.warn_by_id(f"guide:{gid}", f"Skipping guide `{gid}`: `{(GUIDES / f"{gid}.md").relative_to(REPO_ROOT)}` was missing.`")
            continue
        guides[gid] = g_title, author, last_updated, images, page_links, related_pages, custom_generator
    # Create generators
    guide_to_generator: dict[str, tuple[str, Callable[[str], str]]] = {}
    for gid, (gtitle, author, last_updated, images, page_links, related_pages, custom_generator) in guides.items():
        if custom_generator:
            gen = lambda i=gid: custom_generator(get_template(f"guides/{i}"))
        else:
            gen = lambda guide_id=gid, i=frozenset(images), pl=frozenset(page_links): _gen_guide_content(guide_id, i, pl)

        def _gen_guide(i, t, a, ut, rel_pages, g):
            if any(x not in PAGE_DIRECTORY for x in rel_pages):
                LOGGER.warn_by_id(i, f"Failed to generate guide `{i}: Guide lists page IDs which don't exist")
                return f"Content failed to generate (Check listed pages in guides.yml)"
            return get_template("guides/guide_template").format(
                guide_title=t,
                author=a,
                update_time=ut,
                guide_content=g(),
                related_link_list=f"## Related pages\n\n{"\n".join(f"- {link(page)}" for page in sorted(rel_pages))}" if rel_pages else "",
                guide_howto=link("how_to_make_player_guides")
            )

        guide_to_generator[gid] = gtitle, lambda i=gid, t=gtitle, a=author, ut=last_updated, r=frozenset(related_pages), g=gen: _gen_guide(i, t, a, ut, r, g)
    # Add guides to page directory
    PAGE_DIRECTORY.update({f"guide_{k}": PageInfo(t, f"guide_{k}.md", f) for k, (t, f) in guide_to_generator.items()})
    # Add guides to page hierarchy
    PAGE_HIERARCHY.merge([["Guides", True, [f"guide_{x}" for x in guide_to_generator.keys()]]])



def add_boss_pages():
    bosses = load("raid_bosses.json")
    assert isinstance(bosses, dict)
    boss_pages = {
        boss_id: PageInfo(boss_name(boss_id), f"{boss_id}.md", lambda x=bosses[boss_id]: gen_boss(x), boss_sprite(boss_id))
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
            enemy_name(enemy_id),
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
            dungeon_name(dungeon["name"]),
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
            expedition_name(exp["name"]),
            f"{exp['name']}.md",
            lambda entry=exp: gen_expedition(entry),
        )
        for exp in expeditions
    }
    PAGE_DIRECTORY.update(expedition_pages)


def add_trade_route_pages():
    trade_routes = sorted(
        [load(f, False) for f in (ASSETS / "trade_routes").glob("*.json")],
        key=lambda tr: (tr["level_required"])
    )
    trade_route_pages = {
        tr["id"]: PageInfo(
            trade_route_name(tr["id"]),
            f"{tr['id']}.md",
            lambda entry=tr: gen_trade_route(entry)
        )
        for tr in trade_routes
    }
    PAGE_DIRECTORY.update(trade_route_pages)

# ---------------------------------------------------------------------------
# Main functions
# ---------------------------------------------------------------------------


def get_pages() -> dict[str, str]:
    def _convert_md_image_links(i_directory: dict[Path, str], m: re.Match) -> str:
        alt_text, href = m.group(1), m.group(2)
        return f"![{alt_text}]({i_directory[REPO_ROOT / href]})"

    def _convert_html_image_links(i_directory: dict[Path, str], m: re.Match) -> str:
        start, href, end = m.group(1), m.group(2), m.group(3)
        return f"<img{start}src='{i_directory[REPO_ROOT / href]}'{end}>"

    image_dir = get_image_directory()
    pages = {}
    for info in PAGE_DIRECTORY.values():
        content = info.generate()
        content = re.sub(r'!\[([^]]*)]\(([^)]*)\)', lambda x: _convert_md_image_links(image_dir, x), content)
        content = re.sub(r"<img([^>]*)src=['\"]([^'\"]*)['\"]([^>]*)>", lambda x: _convert_html_image_links(image_dir, x), content)
        pages[info.url] = content
    return pages


_IMAGE_DIR: dict[Path, str] | None = None


def get_image_directory() -> dict[Path, str]:
    global _IMAGE_DIR
    if _IMAGE_DIR is not None:
        return _IMAGE_DIR
    image_id = 0
    image_directory: dict[Path, str] = {DEFAULT_ICON: f"favicon.png"}
    # Add page favicons
    for icon in [v.icon for v in PAGE_DIRECTORY.values() if v.icon is not None]:
        if icon not in image_directory:
            image_directory[icon] = f"image_{image_id}{icon.suffix}"
            image_id += 1
    # Add images in pages
    def _add_image_paths(start_img_id: int, raw_image_paths: list[str]) -> int:
        for image_path in raw_image_paths:
            relative_path = Path(REPO_ROOT / image_path)
            if relative_path not in image_directory:
                image_directory[relative_path] = f"image_{start_img_id}{relative_path.suffix}"
                start_img_id += 1
        return start_img_id

    for page_content in [p.generate() for p in PAGE_DIRECTORY.values()]:
        image_paths = re.findall(r'!\[[^]]*]\(([^)]*)\)', page_content)
        image_paths += re.findall(r"<img[^>]*src=['\"]([^'\"]*)['\"][^>]*>", page_content)
        image_id = _add_image_paths(image_id, image_paths)
    # Construct _IMAGE_DIR for more effective generation in later calls
    _IMAGE_DIR = image_directory
    return image_directory


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


def fmt_materials(mats: dict) -> str:
    """Formats a dictionary of materials, generating item links for each of them"""
    return ", ".join(f"{fmt_amount(qty)}× {item_link(item)}" for item, qty in mats.items())


def fmt_pct(chance: float) -> str:
    """Formats a percentage into a string

    :param chance: The percentage chance (represented as a decimal. E.g. 0.1 → 10%)
    :return: The formatted string
    """
    pct = chance * 100
    return f"{pct:.1f}%" if pct < 1 else f"{pct:.0f}%"


def fmt_amount(amount: int) -> str:
    """Formats an integer amount into the appropriate string format"""
    return f"{amount:,}"

def table(headers: list[str], rows: list[list]) -> str:
    """Formats a markdown table from a set of rows

    :param headers: The list of headers for the table
    :param rows: A list of rows (of which each row is a list of cells). Each row should be the same size as the header
    :return: A string representing a markdown-formatted table
    """
    sep = " | "
    header_row  = sep.join(headers)
    divider_row = sep.join("---" for _ in headers)
    data_rows   = "\n".join(f"| {sep.join(str(c) for c in row)} |" for row in rows)
    return f"| {header_row} |\n| {divider_row} |\n{data_rows}"


def session_minutes(level: int, prestige: int = 0, chronos_mult: float = 1) -> int:
    """Mirrors SkillSimulator.sessionDurationMs() — 60→40 min linear across levels 1–99."""
    fraction = (level - 1) / 98.0
    max_reduction = 20 + prestige * 10 / 3
    return round((60 - max_reduction * fraction) * chronos_mult)


def github_issue_link(issue_num: int, display_name: str | None = None) -> str:
    """Links to the relevant GitHub issue (only works for the main IdleFantasy repo)"""
    return f"[{display_name if display_name else f"#{issue_num}"}]({GITHUB_REPO}/issues/{issue_num})"


def github_pull_request_link(pr_num: int, display_name: str | None = None) -> str:
    """Links to the relevant GitHub pull request (only works for the main IdleFantasy repo)"""
    return f"[{display_name if display_name else f"#{pr_num}"}]({GITHUB_REPO}/pull/{pr_num})"


def github_discussion_link(disc_num: int, display_name: str | None = None) -> str:
    """Links to the relevant GitHub discussion (only works for the main IdleFantasy repo)"""
    return f"[{display_name if display_name else f"#{disc_num}"}]({GITHUB_REPO}/discussions/{disc_num})"


def link(page_id: str, display_name: str | None = None, header: str | None = None):
    """Links to a page within the wiki

    :param page_id: The page ID of the page to link to. This is the key for the PAGE_DIRECTORY
    :param display_name: The display name to show for the link
    :param header: The specific header or id in the page that you are linking to
    :return: A markdown-formatted link to the specified page
    """
    page = PAGE_DIRECTORY[page_id]
    return f"[{page.title if display_name is None else display_name}]({page.url.removesuffix('.md')}{f"#{header}" if header else ""})"


def html_link(page_id: str, display_name: str | None = None) -> str:
    """HTML anchor for use inside raw HTML blocks where Markdown links are not parsed."""
    page = PAGE_DIRECTORY[page_id]
    name = page.title if display_name is None else display_name
    return f'<a href="{page.url.removesuffix(".md")}">{name}</a>'


def html_image(image_path: Path, alt_tag: str | None = None, classes: str | None = None, width: int | None = None) -> str:
    """Formats an HTML image for the image at the specified path. This image should be present somewhere in the repo

    :param image_path: The path to the image
    :param alt_tag: The alt tag describing what the image shows. Defaults to the name of the image if left blank
    :param classes: Any CSS classes that the image should use
    :param width: A specified width for the image - this is often overridden by the CSS class except in the GitHub wiki
    :return: A formatted HTML image tag pointing to the specified image
    """
    # width is an HTML attribute rather than CSS so sizing survives the GitHub
    # wiki's tag sanitizer, which strips class and style attributes.
    return (f"<img src='{image_path.relative_to(REPO_ROOT).as_posix()}' alt='{image_path.name if alt_tag is None else alt_tag}'"
            f"{f" class='{classes}'" if classes else ""}"
            f"{f" width='{width}'" if width else ""}>")


def image(image_path: Path, alt_tag: str | None = None) -> str:
    """Generates a standard markdown image from the specified path

    :param image_path: The path to the image. This should be somewhere in the repo
    :param alt_tag: The alt tag describing the image
    :return: A formatted markdown image tag pointing to the specified image
    """
    return f"![{image_path.name if alt_tag is None else alt_tag}]({image_path.relative_to(REPO_ROOT).as_posix()})"


def icon_path(icon_name: str) -> Path:
    """Gets the path to the specified game icon"""
    return RESOURCES / "drawable" / f"{icon_name}.png"


def skill_icon_path(skill: str) -> Path:
    """Gets the path to the specified skill icon"""
    return icon_path(f"skill_{skill.lower()}")


def boss_sprite(boss_id: str) -> Path | None:
    """Gets the path to the specified boss sprite"""
    boss_sprite_path = SPRITES / "bosses" / f"{boss_id}.png"
    return boss_sprite_path if boss_sprite_path.is_file() else None


def boss_icon(boss_id: str, fallback: str, width: int | None = None) -> str:
    """Boss art image, falling back to the emoji for bosses without a sprite."""
    sprite = boss_sprite(boss_id)
    if sprite:
        return html_image(sprite, boss_name(boss_id), width=width)
    return fallback


def _tool_table(slot: str, efficiency_key: str) -> str:
    """Gets the list of tools used in a number of pages"""
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    tools = sorted(
        [v for v in equipment.values() if v.get("slot") == slot and efficiency_key in v],
        key=lambda v: (list(v.get("requirements", {}).values() or [0])[0], v[efficiency_key])
    )
    rows = [[item_name(t["name"]), list(t.get("requirements", {}).values() or [1])[0], f"{t[efficiency_key]:.2f}×"] for t in tools]
    return table(["Tool", "Level Required", "Efficiency"], rows)


_PAGE_MAP: dict[str, str] | None = None


def build_page_map() -> dict[str, str]:
    """Builds the page map used for linking items in pages"""
    global _PAGE_MAP
    if _PAGE_MAP is not None:
        return _PAGE_MAP

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
    # Raw fish → fishing (before cooking so raw fish link here, not to cooking)
    _add(list(load("fish.json").keys()), "fishing")
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

    _PAGE_MAP = m
    return m


def _slugify(text: str) -> str:
    """Convert a display name to a row-id slug: 'Silver Ore' -> 'silver_ore'."""
    text = re.sub(r'<[^>]+>', '', text)      # strip HTML tags
    text = re.sub("['\\u2018\\u2019]", '', text)  # strip apostrophes (straight + curly)
    return re.sub(r'[^a-z0-9]+', '_', text.lower()).strip('_')


def item_link(key: str) -> str:
    """Returns a markdown link to the page where this item is documented, or plain title if unknown."""
    page_id = build_page_map().get(key)
    if page_id:
        return link(page_id, item_name(key), _slugify(item_name(key)))
    return item_name(key)


def gen_table_of_contents(page_content: str, max_level: int = 4, min_level: int = 2, exclude: list[str] | None = None) -> str:
    """Build a nested Markdown table of contents from ATX headings up to ``max_level``."""
    headings: list[tuple[int, str]] = []
    in_code_block = False
    excluded = set(exclude) if exclude else set()

    for line in page_content.splitlines():
        if line.strip().startswith("```"):
            in_code_block = not in_code_block
            continue
        if in_code_block:
            continue
        match = re.compile(r"^(#{1,6})\s+(.+)$").match(line)
        if not match or not min_level <= len(match.group(1)) < max_level:
            continue
        headings.append((len(match.group(1)), match.group(2).strip()))

    if not headings:
        return ""

    base_level = min(level for level, _ in headings)
    lines: list[str] = []
    for level, text in headings:
        label = re.sub(r"\[([^]]+)]\([^)]+\)", r"\1", text)
        label = re.sub(r"[*_`]", "", label).strip()
        anchor = re.sub(r"[^\w\s-]", "", label.lower())
        anchor = re.sub(r"\s+", "-", anchor).strip("-")
        if label not in excluded:
            lines.append(f"{'    ' * (level - base_level)}- [{label}](#{anchor})")

    return "\n".join(lines)


def make_latex_safe(content: str, escape_levels: int = 1, ignore_keys: list[str] | None = None) -> str:
    """Escape braces inside inline LaTeX (``$...$``) so ``str.format`` leaves them intact.

    Expressions such as ``$\\dfrac{a}{b}$`` contain braces that ``str.format`` would
    otherwise treat as replacement fields. Braces are doubled once per escape level
    (level 1 → ``{{`` / ``}}``, level 2 → ``{{{{`` / ``}}}}``, and so on).

    :param content: Text that may contain inline LaTeX maths.
    :param escape_levels: Number of ``.format`` passes the content will go through after this function is run.
    :param ignore_keys: List of keys to ignore - useful if the latex has template fields inside of it. Only fields within latex formulas need to be included (See gen_heirlooms for example).
    :return: Content with LaTeX braces escaped for the given format depth.
    """
    open_brace = "{" * (2 ** escape_levels)
    close_brace = "}" * (2 ** escape_levels)

    def _escape_math(match: re.Match[str]) -> str:
        body = match.group(1).replace("{", open_brace).replace("}", close_brace)
        return f"$`{body}`$"

    safe_latex = re.sub(r"\$`([^$]+)`\$", _escape_math, content)
    for key in ignore_keys or []:
        safe_latex = safe_latex.replace(open_brace + key + close_brace, "{" + key + "}")
    return safe_latex



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


def gen_getting_started_game() -> str:
    page = get_template("contributing/getting_started_game").format(
        table_of_contents="{table_of_contents}",
        wiki_contribution_link=link("getting_started_wiki", "Contributing to the wiki")
    )
    return page.format(table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(page)}")


def gen_getting_started_wiki() -> str:
    page = get_template("contributing/getting_started_wiki").format(
        page_types_link=link("wiki_page_types"),
        table_of_contents="{table_of_contents}",
        editing_a_page_link=github_pull_request_link(1353, "Guide: Fixing an out-of-date wiki page"),
        adding_quest_icons_page=github_pull_request_link(1669, "PR: Adding the quest icons page to the wiki"),
        game_contribution_link=link("getting_started_game", "how to contribute to the game")
    )
    return page.format(table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(page)}")


def gen_wiki_page_types() -> str:
    page = get_template("contributing/wiki_page_types")
    return page.format(table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(page)}")


def gen_how_to_make_player_guides() -> str:
    page = get_template("guides/how_to_make_player_guides").format(
        getting_started_wiki_link=link("getting_started_wiki"),
        table_of_contents="{table_of_contents}",
    )
    return page.format(
        table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(page)}"
    )


_PRESTIGE_RACES = ["human", "elf", "dwarf", "orc", "gnome", "halfling"]


def gen_prestige_race_tables() -> str:
    trees = load("prestige_paths.json")
    assert isinstance(trees, list)
    by_race: dict[str, list] = {r: [] for r in _PRESTIGE_RACES}
    for tree in trees:
        for path in tree["paths"]:
            for node in path["nodes"]:
                for race in node.get("races") or []:
                    shared = [race_name(r) for r in node["races"] if r != race]
                    by_race[race].append((tree["skill"], node, shared))
    parts = []
    for race in _PRESTIGE_RACES:
        rows = [
            [
                skill_name(skill),
                prestige_effect_desc(node["effect"], node.get("value", 0), node.get("unlock")) + (f" (shared with {', '.join(shared)})" if shared else ""),
                node["cost"],
            ]
            for skill, node, shared in by_race[race]
        ]
        parts.append(f"#### {race_name(race)}\n\n" + table(["Skill", "Upgrade", "Cost (points)"], rows))
    return "\n\n".join(parts)


def gen_skills() -> str:
    skill_list = [
        ("mining", "gathering"),
        ("fishing", "gathering"),
        ("woodcutting", "gathering"),
        ("farming", "gathering"),
        ("firemaking", "crafting"),
        ("agility", "support"),
        ("thieving", "gathering"),
        ("mercantile", "support"),
        ("smithing", "crafting"),
        ("cooking", "crafting"),
        ("fletching", "crafting"),
        ("crafting", "crafting"),
        ("runecrafting", "crafting"),
        ("herblore", "crafting"),
        ("construction", "crafting"),
        ("attack", "combat"),
        ("strength", "combat"),
        ("defense", "combat"),
        ("ranged", "combat"),
        ("magic", "combat"),
        ("hitpoints", "combat"),
        ("prayer", "support"),
        ("slayer", "combat"),
    ]
    rows = [
        [f"{html_image(skill_icon_path(skill), "", "text")} {link(skill) if skill in PAGE_DIRECTORY else skill_name(skill)}", cat.title(), skill_desc(skill)]
        for skill, cat in skill_list
    ]

    return get_template("skills/skills").format(
        skills_table=table(["Skill", "Category", "Description"], rows),
        quest_icon_link=link("quest_icons"),
        prestige_race_tables=gen_prestige_race_tables(),
    )


def gen_quest_icons() -> str:
    return get_template("skills/quest_icons").format(
        skills_link=link("skills"),
        guilds_link=link("guilds"),
        quests_link=link("quests"),
        seasonal_events_link=link("seasonal_events"),
    )


def gen_mining() -> str:
    # Create ore table
    ores = load("ores.json")
    assert isinstance(ores, dict)
    ore_rows = sorted(
        [[item_name(k), o["level_required"], o["xp_per_ore"], item_desc(k)]
         for k, o in ores.items()],
        key=lambda r: r[1]
    )
    # Create gem table
    gems = load("gems.json")
    assert isinstance(gems, dict)
    gem_rows = [
        [item_name(k), fmt_pct(g["drop_rate"]), item_desc(k)]
        for k, g in sorted(gems.items(), key=lambda x: x[1]["drop_rate"], reverse=True)
    ]
    # Return filled template
    return get_template("skills/gathering/mining").format(
        icon=html_image(skill_icon_path("mining"), "", "text"),
        ore_table=table(['Ore', 'Level Required', 'XP / Ore', "Description"], ore_rows),
        gem_table=table(["Gems", "Drop Rate", "Description"], gem_rows),
        pickaxe_table=_tool_table("pickaxe", "mining_efficiency"),
        tool_efficiency_section=_gathering_tool_eff_section(
            "mining", "pickaxe", "mining", "pickaxe", "Ores",
            {item_name(k): o["level_required"] for k, o in ores.items()}
        ),
    )


def gen_fishing() -> str:
    # Create fish rows
    fish_data = load("fish.json")
    assert isinstance(fish_data, dict)
    fish_rows = sorted(
        [[item_name(k), v["level_required"], v["xp_per_catch"]] for k, v in fish_data.items()],
        key=lambda r: r[1]
    )
    # Create fishing rare drops tables
    fishing_session_data = load("skills/fishing.json")
    assert isinstance(fishing_session_data, dict)
    rare_item_rows = []
    drop_tables = sorted(
        [(int(lvl), items) for lvl, items in fishing_session_data["drop_tables"].items()],
        key=lambda r: r[0]
    )
    for i, (min_level, drops) in enumerate(drop_tables):
        max_level = drop_tables[i + 1][0] - 1 if i < len(drop_tables) - 1 else None
        level_range = f"{min_level}-{max_level}" if max_level else f">{min_level}"
        drop_list = [(item_link(v["item"]), fmt_pct(v["chance"])) for v in sorted(drops, key=lambda x: x["chance"], reverse=True)]
        rare_item_rows.append([level_range, ", ".join(f"{c} {i}" for i, c in drop_list)])
    # Return filled template
    return get_template("skills/gathering/fishing").format(
        icon=html_image(skill_icon_path("fishing"), "", "text"),
        fish_table=table(['Fish', 'Level required','XP per catch'], fish_rows),
        rare_item_table=table(["Fishing level", "Drops"], rare_item_rows),
        rod_table=_tool_table("fishing_rod", "fishing_efficiency"),
        tool_efficiency_section=_gathering_tool_eff_section(
            "fishing", "fishing rod", "fishing", "fishing_rod", "Fish",
            {item_name(k): v["level_required"] for k, v in fish_data.items()}
        ),
    )


def gen_woodcutting() -> str:
    trees = load("trees.json")
    assert isinstance(trees, dict)
    rows = sorted(
        [[tree_name(k), t["level_required"], t["xp_per_log"], item_name(t["log_name"])]
         for k, t in trees.items()],
        key=lambda r: r[1]
    )
    tool_rows = _tool_table("axe", "woodcutting_efficiency")
    return get_template("skills/gathering/woodcutting").format(
        icon=html_image(skill_icon_path("woodcutting"), "", "text"),
        tree_table=table(['Tree','Level Required','XP / Log','Log'], rows),
        axe_table=tool_rows,
        tool_efficiency_section=_gathering_tool_eff_section(
            "chopping", "axe", "woodcutting", "axe", "Trees",
            {tree_name(k): v["level_required"] for k, v in trees.items()}
        ),
    )


def gen_farming() -> str:
    # Load crops
    crops = load("crops.json")
    assert isinstance(crops, dict)
    rows = sorted(
        [[
            f"{c.get('emoji', '')} {item_name(k)}",
            c["farming_level_required"],
            item_name(c["seed_name"]),
            c.get("seed_cost", "—"),
            f"{c['growth_time_hours']}h",
            c.get("planting_xp", "—"),
            c.get("harvest_xp", "—"),
            f"{c.get('yield_min', 1)}–{c.get('yield_max', 1)}",
        ] for k, c in crops.items() if k != "magic_bean"],
        key=lambda r: r[1]
    )
    # Hoes table
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    hoes = sorted(
        [v for v in equipment.values() if v.get("slot") == "hoe" and "farming_efficiency" in v],
        key=lambda v: list(v.get("requirements", {}).values() or [0])[0]
    )
    hoe_rows = [[item_name(h["name"]), list(h.get("requirements", {}).values() or [1])[0], f"+{int((h['farming_efficiency'] - 1) * 100)}%"] for h in hoes]

    # Ashes tables
    # Todo: Switch to avoid being hardcoded
    ash_rows = []
    ash_amounts = [("ashes", 1.1), ("oak_ashes", 1.2), ("willow_ashes", 1.35), ("maple_ashes", 1.5), ("yew_ashes", 1.75),
                   ("magic_ashes", 2), ("redwood_ashes", 2.5)]
    for ash, bonus in ash_amounts:
        ash_rows.append([item_name(ash), f"+{round((bonus - 1) * 100)}%"])

    magic_bean_note = (
        "Obtaining one requires patience. A lucky harvest may be all it takes. "
        "Plant it in any empty patch when you are ready. Do not expect a quick answer."
    )
    return get_template("skills/gathering/farming").format(
        icon=html_image(skill_icon_path("farming"), "", "text"),
        garden_link=link("buildings", "Garden", "garden"),
        thieving_link=link("thieving", "stealing"),
        seed_table=table(['Crop','Level','Seed','Seed Cost','Growth Time','Planting XP','Harvest XP','Yield'], rows),
        hoe_table=table(['Hoe','Level Required','Yield Bonus'], hoe_rows),
        ashes_table=table(["Ash", "Yield bonus"], ash_rows),
        magic_bean_section=magic_bean_note,
        tool_efficiency_section=_gathering_tool_eff_section(
            "harvesting", "hoe", "farming", "hoe", "crops",
            {item_name(k): v["farming_level_required"] for k, v in crops.items()}
        ),
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
            agility_course_name(c["name"]),
            c["level_required"],
            c["xp_per_success"],
            f"~{xp_per_min:,}",
            f"~{xp_per_session:,}",
        ])

    duration_rows = []
    for level in [1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99]:
        mins = session_minutes(level)
        duration_rows.append([level, f"{mins} min"])

    tool_rows = _tool_table("grappling_hook", "agility_efficiency")
    return get_template("skills/support/agility").format(
        icon=html_image(skill_icon_path("agility"), "", "text"),
        session_duration_table=table(["Agility Level", "Session Duration"], duration_rows),
        prestige_link=link("skills", header="prestige"),
        course_count=len(courses),
        course_table=table(['Course', 'Level Required', 'XP / Lap', 'XP / Min (est.)', 'XP / Session (est.)'], course_rows),
        grappling_hook_table=tool_rows,
    )


def _tool_eff_mult(tool_tier: int, item_tier: int) -> str:
    return "—" if tool_tier <= item_tier else f"×{1 + 0.25 * (tool_tier - item_tier)}"


def _mult_table(tier_levels: list[int], tier_name: str) -> str:
    return f"""<table class="small">
        <thead>
            <tr>
                <th colspan="2"></th>
                <th colspan="{len(tier_levels)}" style="text-align: center">Tool tier</th>
            </tr>
            <tr>
                <th colspan="2"></th>
                {"\n".join(f"<th>{tool_tier + 1}</th>" for tool_tier in range(len(tier_levels)))}
            </tr>
        </thead>
        <tbody>
            {"\n".join(f"""<tr>
                {"" if item_tier != 0 else f"""<th rowspan="{len(tier_levels)}" style="vertical-align: middle">
                        <span style="writing-mode: vertical-lr; transform: rotate(180deg)">{tier_name}</span>
                    </th>\n"""}<th>{item_tier + 1}</th>
                {"\n".join(f"<td>{_tool_eff_mult(tool_tier, item_tier)}</td>" for tool_tier in range(len(tier_levels)))}
            </tr>""" for item_tier in range(len(tier_levels)))}
        </tbody>
    </table>"""


_TOOL_TIER_LEVELS = [1, 15, 30, 55, 70, 85]


def _crafting_tool_eff_section(verb: str, skill: str, tool_slot: str) -> str:
    # Get tools associated with the designated tool slot
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    tools = {k: v for k, v in equipment.items() if v["slot"] == tool_slot}
    # Get heirloom items and exclude from tool list
    heirloom_items = {k: v for k, v in tools.items() if v.get("heirloom_skill") == skill}
    if len(heirloom_items) > 1:
        LOGGER.warn_by_id(f"multiple_heirlooms:{skill}", f"Only the first available heirloom for skill `{skill}` was selected as only one was expected — _tool_efficiency_section should be adjusted to handle multiple heirloom items")
    for item in heirloom_items.keys():
        tools.pop(item)
    heirloom_item = list(heirloom_items.items())[0][0]
    # Calculate tier rows
    tier_rows = []
    for i, min_level in enumerate(_TOOL_TIER_LEVELS):
        max_level = _TOOL_TIER_LEVELS[i + 1] if i + 1 < len(_TOOL_TIER_LEVELS) else None
        tools_in_tier = sorted([
            k for k, v in tools.items()
            if (i == 0 or v.get("requirements", {}).get(skill, 1) >= min_level)
               and (max_level is None or v.get("requirements", {}).get(skill, 0) < max_level)],
            key=lambda x: tools[x].get("requirements", {}).get(skill, 0)
        )
        tier_rows.append([
            i + 1,
            f"≥{min_level}" if max_level is None else f"{min_level}-{max_level - 1}",
            ", ".join(item_link(tool) for tool in tools_in_tier),
        ])

    return make_latex_safe(get_template("skills/crafting/crafting_tool_eff_section")).format(
        verb=verb,
        skill_name=skill,
        heirloom_tool=item_link(heirloom_item),
        heirloom_link=link("heirlooms", header="how-heirlooms-grow"),
        tier_table=table(["Tier", "Level Range", "Tools"], tier_rows),
        mult_table=_mult_table(tier_rows, "Item Tier"),
    )


def _gathering_tool_eff_section(verb: str, tool_name: str, skill: str, tool_slot: str, activity_name: str,
                                activity_lbl_to_level: dict[str, int]) -> str:
    # Get tools associated with the designated tool slot
    equipment = load("equipment.json")
    assert isinstance(equipment, dict)
    tools = {k: v for k, v in equipment.items() if v["slot"] == tool_slot}
    # Get heirloom items and exclude from tool list
    heirloom_items = {k: v for k, v in tools.items() if v.get("heirloom_skill") == skill}
    if len(heirloom_items) > 1:
        LOGGER.warn_by_id(f"multiple_heirlooms:{skill}", f"Only the first available heirloom for skill `{skill}` was selected as only one was expected — _tool_efficiency_section should be adjusted to handle multiple heirloom items")
    for item in heirloom_items.keys():
        tools.pop(item)
    heirloom_item = list(heirloom_items.items())[0][0]
    # Calculate tier rows
    tier_rows = []
    for i, min_level in enumerate(_TOOL_TIER_LEVELS):
        max_level = _TOOL_TIER_LEVELS[i + 1] if i + 1 < len(_TOOL_TIER_LEVELS) else None

        def _in_level(level: int) -> bool:
            return (i == 0 or level >= min_level) and (max_level is None or level < max_level)

        tools_in_tier = sorted([
            k for k, v in tools.items()
            if _in_level(v.get("requirements", {}).get(skill, 1))
        ], key=lambda x: tools[x].get("requirements", {}).get(skill, 0))
        activity_lbl_in_tier = sorted([
            k for k, v in activity_lbl_to_level.items()
            if _in_level(activity_lbl_to_level[k])
        ], key=lambda x: activity_lbl_to_level[x])
        tier_rows.append([
            i + 1,
            f"≥{min_level}" if max_level is None else f"{min_level}-{max_level - 1}",
            ", ".join(item_link(tool) for tool in tools_in_tier),
            ", ".join(k for k in activity_lbl_in_tier),
        ])

    return make_latex_safe(get_template("skills/gathering/gathering_tool_eff_section"), ignore_keys=["activity_name"]).format(
        tool_type=tool_name,
        verb=verb,
        activity_name=activity_name.lower(),
        skill_name=skill,
        heirloom_tool=item_link(heirloom_item),
        heirloom_link=link("heirlooms", header="how-heirlooms-grow"),
        tier_table=table(["Tier", "Level Range", "Tools", activity_name], tier_rows),
        mult_table=_mult_table(tier_rows, f"{activity_name} Tier"),
    )


def gen_smithing() -> str:
    recipes = load("recipes/smithing.json")
    assert isinstance(recipes, dict)
    equip = load("equipment.json")
    assert isinstance(equip, dict)
    groups = {"bar": [], "weapon": [], "armour": [], "tool": [], "component": [], "other": []}
    for key, r in recipes.items():
        t = r.get("type", "other")
        if t == "equipment":
            g = "weapon" if equip.get(key, {}).get("slot") == "weapon" else "armour"
        else:
            g = t if t in groups else "other"
        groups[g].append([item_name(key), r["level_required"], fmt_materials(r["materials"]), fmt_amount(r["xp_per_item"])])

    if len(groups["other"]) > 0:
        log(logging.WARNING, "Some smithing items were in the 'other' group which are not shown on the page")

    sections = []
    order = [("armour", "Armour"), ("bar", "Bars"), ("component", "Components"), ("tool", "Tools"), ("weapon", "Weapons")]
    for group_key, group_name in order:
        rows = sorted(groups[group_key], key=lambda x: x[1])
        if rows:
            sections.append(f"### {group_name}\n\n{table(['Item','Level','Materials','XP / Item'], rows)}")

    return get_template("skills/crafting/smithing").format(
        icon=html_image(skill_icon_path("smithing"), "", "text"),
        tool_efficiency_section=_crafting_tool_eff_section("smithing", "smithing", "hammer"),
        sections="\n\n".join(sections),
        hammer_table=_tool_table('hammer', 'smithing_efficiency'),
    )


def gen_cooking() -> str:
    recipes = load("recipes/cooking.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[item_name(i), r["level_required"], item_link(r["raw_item"]), fmt_amount(r["xp_per_item"]), r.get("healing_value", "—")]
         for i, r in recipes.items()],
        key=lambda r: r[1]
    )
    tool_rows = _tool_table("frying_pan", "cooking_efficiency")
    return get_template("skills/crafting/cooking").format(
        icon=html_image(skill_icon_path("cooking"), "", "text"),
        food_table=table(['Food','Level','Raw Ingredient','XP / Item','HP Healed'], rows),
        frying_pan_table=tool_rows,
        tool_efficiency_section=_crafting_tool_eff_section("cooking", "cooking", "frying_pan"),
    )


def gen_fletching() -> str:
    recipes = load("recipes/fletching.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[item_name(i), r["level_required"], fmt_materials(r["materials"]), fmt_amount(r["xp_per_item"])]
         for i, r in recipes.items()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/fletching").format(
        icon=html_image(skill_icon_path("fletching"), "", "text"),
        item_table=table(['Item','Level','Materials','XP / Item'], rows),
    )


def gen_crafting() -> str:
    recipes = load("recipes/crafting.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [[item_name(i), r["level_required"], fmt_materials(r["materials"]), fmt_amount(r["xp_per_item"])]
         for i, r in recipes.items()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/crafting").format(
        icon=html_image(skill_icon_path("crafting"), "", "text"),
        item_table=table(['Item','Level','Materials','XP / Item'], rows),
    )


def gen_firemaking() -> str:
    logs = load("logs.json")
    assert isinstance(logs, dict)
    rows = sorted(
        [[item_name(k), l["level_required"], fmt_amount(l["xp_per_log"])]
         for k, l in logs.items()],
        key=lambda r: r[1]
    )
    tool_rows = _tool_table("tinderbox", "firemaking_efficiency")
    return get_template("skills/crafting/firemaking").format(
        icon=html_image(skill_icon_path("firemaking"), "", "text"),
        farming_link=link("farming", "farming", "ashes"),
        herblore_link=link("herblore", "herblore"),
        runecrafting_link=link("runecrafting", "runecrafting", "bonus-crafted-runes"),
        item_table=table(['Log','Level Required','XP / Log Burned'], rows),
        tinderbox_table=tool_rows,
        tool_efficiency_section=_crafting_tool_eff_section("burning", "firemaking", "tinderbox"),
    )


def gen_runecrafting() -> str:
    runes = load("runes.json")
    assert isinstance(runes, dict)
    runes_rows = sorted(
        [[
            item_name(k),
            r["level_required"],
            r["essence_cost"],
            fmt_amount(r["xp_per_rune"]),
        ] for k, r in runes.items()],
        key=lambda r: r[1]
    )
    bonuses_rows = [["No ash", 1, 2, 3]]
    bonuses_rows += [[item_link(k), i + 2, i + 3, i + 4]
        for i, k in
        enumerate(["ashes", "oak_ashes", "willow_ashes", "maple_ashes", "yew_ashes", "magic_ashes", "redwood_ashes"])
    ]

    return get_template("skills/crafting/runecrafting").format(
        icon=html_image(skill_icon_path("runecrafting"), "", "text"),
        runes_table=table(['Rune','Level Required','Essence / Rune','XP / Rune'], runes_rows),
        bonuses_table=table(["Ash", "Level 1-49", "Level 50-74", "Level >75"], bonuses_rows)
    )


def gen_herblore() -> str:
    recipes = load("recipes/herblore.json")
    assert isinstance(recipes, dict)

    def fmt_effects(effects: dict, enhanced: bool = False) -> str:
        def val_for(v):
            return max(int(v * 2), v + 1) if enhanced else v
        return ", ".join(f"{stat.title()} +{val_for(val)}" for stat, val in effects.items())

    rows = sorted(
        [[
            item_name(k),
            r["level_required"],
            fmt_materials(r["materials"]),
            fmt_effects(r.get("effects", {})),
            fmt_effects(r.get("effects", {}), enhanced=True),
            r["xp_per_item"],
        ] for k, r in recipes.items()],
        key=lambda r: r[1]
    )
    return get_template("skills/crafting/herblore").format(
        icon=html_image(skill_icon_path("herblore"), "", "text"),
        potion_table=table(['Potion','Level','Ingredients','Effect','Enhanced Effect','XP'], rows),
    )


def gen_construction() -> str:
    recipes = load("recipes/construction.json")
    assert isinstance(recipes, dict)
    rows = sorted(
        [
            [item_name(k), r["level_required"], fmt_materials(r["materials"]), int(r["xp_per_item"])]
            for k, r in recipes.items()
        ],
        key=lambda r: r[1],
    )
    return get_template("skills/crafting/construction").format(
        icon=html_image(skill_icon_path("construction"), "", "text"),
        item_table=table(["Item", "Level", "Materials", "XP / Item"], rows),
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
            thieving_npc_name(npc["key"]),
            npc["level_required"],
            npc["base_xp"],
            f"{npc['coins_min']}-{npc['coins_max']}",
            ", ".join(loot_parts),
        ])
    return get_template("skills/gathering/thieving").format(
        icon=html_image(skill_icon_path("thieving"), "", "text"),
        npc_table=table(["NPC", "Level", "XP / Steal", "Coins", "Possible Loot"], rows),
        lockpick_table=_tool_table("lockpick", "thieving_efficiency"),
        tool_efficiency_section=_gathering_tool_eff_section(
            "pickpocketing", "lockpick", "thieving", "lockpick", "NPCs",
            {thieving_npc_name(npc["key"]): npc["level_required"] for npc in npcs}
        ),
    )


_BLESSING_BONE_COST = {1: 10, 10: 20, 20: 35, 30: 55, 40: 80, 50: 110,
                       60: 145, 70: 185, 80: 230, 90: 265, 99: 300}
# Todo: Move blessings from ChurchRepository.ALL_BLESSINGS into their own dedicated blessings.json file and reference in the game with gameData and here by loading the json file
# Keyed by blessing id (matches ChurchRepository.ALL_BLESSINGS); display names come from strings.xml via blessing_name().
_BLESSINGS = {
    "XP": [
        ("blessed_focus", 1, 1.05), ("blessed_focus_ii", 10, 1.10),
        ("blessed_focus_iii", 20, 1.15), ("tithe_blessing", 30, 1.18),
        ("tithe_blessing_ii", 40, 1.20), ("tithe_blessing_iii", 50, 1.25),
        ("divine_focus", 60, 1.28), ("divine_focus_ii", 70, 1.32),
        ("divine_grace", 80, 1.37), ("divine_grace_ii", 90, 1.43),
        ("sacred_grace", 99, 1.50),
    ],
    "DEFENSE": [
        ("stone_skin", 1, 2), ("stone_skin_ii", 10, 4), ("stone_skin_iii", 20, 6),
        ("stone_skin_iv", 30, 9), ("iron_ward", 40, 12), ("iron_ward_ii", 50, 15),
        ("diamond_skin", 60, 18), ("diamond_skin_ii", 70, 22),
        ("holy_shield", 80, 26), ("holy_shield_ii", 90, 30), ("aegis", 99, 35),
    ],
    "COINS": [
        ("fortune_i", 30, 0.08), ("fortune_ii", 40, 0.10), ("fortune_iii", 50, 0.13),
        ("fortune_iv", 60, 0.15), ("fortune_v", 70, 0.18), ("abundance", 80, 0.20),
        ("abundance_ii", 90, 0.23), ("abundance_iii", 99, 0.25),
    ],
}


def _blessing_table(kind: str, effect_fmt) -> str:
    rows = [[blessing_name(bid), level, _BLESSING_BONE_COST[level], effect_fmt(mag)]
            for bid, level, mag in _BLESSINGS[kind]]
    return table(["Name", "Prayer Level", "Cost (bones)", "Effect"], rows)


def gen_prayer() -> str:
    bones = load("bones.json")
    assert isinstance(bones, dict)
    rows = sorted(
        [[item_name(k), b["xp_per_bone"]]
         for k, b in bones.items()],
        key=lambda r: r[1]
    )
    return get_template("skills/support/prayer").format(
        icon=html_image(skill_icon_path("prayer"), "", "text"),
        church_link=link("buildings", "Church", "church"),
        prayer_table=table(['Bone / Ash','XP Each'], rows),
        blessing_xp_table=_blessing_table("XP", lambda m: f"+{round((m - 1) * 100)}% XP"),
        blessing_defense_table=_blessing_table("DEFENSE", lambda m: f"+{m} Defence"),
        blessing_coins_table=_blessing_table("COINS", lambda m: f"+{round(m * 100)}% coins"),
    )


def _trade_route_rows(ranges: list[tuple[int, dict]]):
    rows = []
    for i, (start_level, tr_range) in enumerate(ranges):
        mercantile_level = f"{start_level}-{ranges[i + 1][0]}" if i < len(ranges) - 1 else f">{start_level}"
        average_return = int((tr_range["max"] + tr_range["min"]) * 60 / 2)
        rows.append([mercantile_level, f"{tr_range["min"] * 60:,}-{tr_range["max"] * 60:,}", f"{average_return:,}"])
    return rows


def gen_trade_route(trade_route: dict) -> str:
    # Get XP and coin ranges
    xp_ranges = sorted(
        [(int(a), b) for a, b in trade_route["xp_ranges"].items()],
        key=lambda x: x[0]
    )
    coin_ranges = sorted(
        [(int(a), b) for a, b in trade_route["coin_ranges"].items()],
        key=lambda x: x[0]
    )

    return get_template("skills/support/trade_route").format(
        title=trade_route_name(trade_route["id"]),
        min_level=trade_route["level_required"],
        cost=f"{trade_route["coin_cost"]:,}",
        description=trade_route_desc(trade_route["id"]),
        xp_ranges_table=table(["Mercantile Level", "XP range", "Average xp"], _trade_route_rows(xp_ranges)),
        coin_ranges_table=table(["Mercantile Level", "Coin range", "Average coins"], _trade_route_rows(coin_ranges)),
    )


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
                link(r["id"]),
                r["level_required"],
                f"{r['coin_cost']:,}",
                f"{low_xp['min']}–{high_xp['max']}",
                f"{low_c['min']:,}–{high_c['max']:,}",
            ])
    route_rows.sort(key=lambda x: x[1])

    # Shop bonus table
    # Todo: Refactor game code to read from JSON files rather than be hardcoded here
    shop_bonus_data = [(0, 0), (20, 0.05), (40, 0.1), (60, 0.15), (80, 0.2), (99, 0.25)]
    shop_bonus_rows = []
    for i, (level, bonus) in enumerate(shop_bonus_data):
        mercantile_level = f"{level}-{shop_bonus_data[i + 1][0]}" if i < len(shop_bonus_data) - 1 else f"{level}"
        shop_bonus_rows.append([mercantile_level, f"-{round(bonus * 100)}%", f"+{round(bonus * 100)}%"])

    # Guild shop
    merchants_guild_items = load("marketplace.json")["merchants_guild"]["items"]
    guild_shop_rows = []
    for item_id, item in sorted(merchants_guild_items.items(), key=lambda x: x[1]["mercantile_level_required"]):
        description = item_desc(item_id)
        # Remove the description explaining the mercantile level
        description = description[:description.find(". Requires Mercantile")]
        guild_shop_rows.append([item_link(item_id), f"{item["price"]:,}", item["mercantile_level_required"], description])

    return get_template("skills/support/mercantile").format(
        icon=html_image(skill_icon_path("mercantile"), "", "text"),
        route_table=table(['Route', 'Level', 'Cost', 'XP / Min (range)', 'Coin Return (range)'], route_rows),
        shop_bonus_table=table(["Mercantile level", "Buy discount", "Sell bonus"], shop_bonus_rows),
        guild_shop_table=table(["Item", "Price", "Minimum Mercantile Level", "Description"], guild_shop_rows),
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
        name=expedition_name(entry["name"]),
        skill=link(entry["skill"]),
        level_required=entry["level_required"],
        requires_str=requires_str,
        unlock_str=unlock_str,
        description=expedition_desc(entry["name"]),
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
            skill_name(exp["skill"]),
            exp["level_required"],
            link(req) if req else "—",
            link(unlock) if unlock else "—",
        ])
    return get_template("miscellaneous/expeditions").format(
        icon=html_image(skill_icon_path("expedition"), "", "text"),
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
            [link(enemy), t["slayer_level"], f"{t['min_kills']}–{t['max_kills']}", t["xp_per_kill"]]
            for enemy, t in tasks.items()
        ],
        key=lambda r: r[1],
    )
    return get_template("skills/combat/slayer").format(
        icon=html_image(skill_icon_path("slayer"), "", "text"),
        task_table=table(['Enemy', 'Slayer Level', 'Kill Range', 'XP / Kill'], rows),
    )


def gen_equipment() -> str:
    equip = load("equipment.json")
    assert isinstance(equip, dict)
    slot_order = ["weapon", "head", "body", "legs", "boots", "cape", "ring", "necklace",
                  "shield", "pickaxe", "axe", "fishing_rod", "hoe",
                  "hammer", "tinderbox", "grappling_hook", "frying_pan", "lockpick"]
    # Todo: Categories are currently hardcoded in InventoryViewModel and should instead be handled in json files instead (eg. in equipment.json).
    # These should replace the current method using slot names
    by_slot: dict[str, list] = {s: [] for s in slot_order}
    for item in equip.values():
        slot = item.get("slot", "other")
        if slot in by_slot:
            reqs = ", ".join(f"{skill_name(sk)} {lv}" for sk, lv in item.get("requirements", {}).items()) or "—"
            by_slot[slot].append([
                item_name(item["name"]),
                item.get("attack_bonus", 0) or 0,
                item.get("strength_bonus", 0) or 0,
                item.get("defense_bonus", 0) or 0,
                item.get("mining_efficiency") or item.get("woodcutting_efficiency") or
                item.get("fishing_efficiency") or item.get("farming_efficiency") or
                item.get("smithing_efficiency") or item.get("firemaking_efficiency") or
                item.get("agility_efficiency") or item.get("cooking_efficiency") or
                item.get("thieving_efficiency") or "—",
                reqs,
            ])

    sections = []
    for slot in slot_order:
        rows = by_slot.get(slot, [])
        if not rows:
            continue
        rows.sort(key=lambda r: r[0])
        sections.append(f"## {slot_name(slot)}\n\n{table(['Item', 'Atk', 'Str', 'Def', 'Efficiency', 'Requirements'], rows)}")

    return get_template("inventory/equipment").format(
        equipment="\n\n".join(sections),
        heirlooms_link=link("heirlooms"),
    )


def gen_heirlooms() -> str:
    equip = load("equipment.json")
    bosses = load("raid_bosses.json")
    assert isinstance(equip, dict)
    assert isinstance(bosses, dict)
    heirlooms = {k: v for k, v in equip.items() if v.get("heirloom_skill")}

    # Which raid boss drops each heirloom, and at what chance
    dropped_by: dict[str, tuple[str, float]] = {}
    for boss_id, boss in bosses.items():
        for drop in boss.get("rare_drops", []):
            if drop["item"] in heirlooms:
                dropped_by[drop["item"]] = (boss_id, drop["chance"])

    def skill_link(skill: str) -> str:
        # Combat skills (attack, strength, ...) have no page of their own
        return link(skill) if skill in PAGE_DIRECTORY else skill_name(skill)

    drop_rows = []
    for key, item in heirlooms.items():
        boss_id, chance = dropped_by.get(key, (None, 0.0))
        drop_rows.append([
            item_name(key),
            skill_link(item["heirloom_skill"]),
            link(boss_id) if boss_id else "?",
            f"1 in {round(1 / chance):,}" if chance else "?",
            item_desc(key),
        ])

    combat_stats = [
        ("attack_bonus", "Atk"), ("strength_bonus", "Str"), ("defense_bonus", "Def"),
        ("ranged_attack_bonus", "Ranged Atk"), ("ranged_strength_bonus", "Ranged Str"),
        ("magic_attack_bonus", "Magic Atk"), ("magic_damage_bonus", "Magic Dmg"),
    ]

    tool_rows, combat_rows = [], []
    for key, item in heirlooms.items():
        base = item.get("heirloom_base", {})
        efficiency_key = next((k for k in item if k.endswith("_efficiency") and item[k]), None)
        if efficiency_key:
            tool_rows.append([
                item_name(key),
                slot_name(item["slot"]),
                skill_link(item["heirloom_skill"]),
                f"{base.get('efficiency', 1.0):.2f}×",
                f"{item[efficiency_key]:.2f}×",
            ])
        else:
            present = [(stat, label) for stat, label in combat_stats if item.get(stat)]
            combat_rows.append([
                item_name(key),
                title(item.get("combat_style") or ""),
                ", ".join(f"{base.get(stat, 0)} {label}" for stat, label in present),
                ", ".join(f"{item[stat]} {label}" for stat, label in present),
            ])

    return make_latex_safe(get_template("inventory/heirlooms"), ignore_keys=["gate_level"]).format(
        gate_level=85,  # mirrors HeirloomStats.GATE_LEVEL
        drop_table=table(["Heirloom", "Governing Skill", "Dropped By", "Drop Chance", "Description"], drop_rows),
        tool_table=table(["Heirloom", "Slot", "Governing Skill", "Efficiency at Item Lv 1", "Efficiency at Item Lv 99"], tool_rows),
        combat_table=table(["Heirloom", "Combat Style", "Stats at Item Lv 1", "Stats at Item Lv 99"], combat_rows),
        equipment_link=link("equipment"),
    )


def footer_link(text: str, icon: str | None = None) -> str:
    if icon:
        return f"<div class=\"footer-image-link\">{icon} {text}</div>"
    return text


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
        boss_links="\n".join(
            footer_link(html_link(boss_id), boss_icon(boss_id, boss.get("emoji", ""), 20))
            for boss_id, boss in sorted(bosses.items(), key=lambda x: bosses[x[0]].get("combat_level_required", 0))
        ),
        enemy_links=", ".join(
            html_link(enemy_id)
            for enemy_id, _ in sorted(enemies.items(), key=lambda x: x[1]["hp"])
        ),
        miscellaneous_links=", ".join([html_link("combat"), html_link("spells"), html_link("slayer")]),
    )


def gen_bosses() -> str:
    bosses = load("raid_bosses.json")
    assert isinstance(bosses, dict)

    def rows_for(raid: bool) -> list[list]:
        return [
            [
                boss_icon(boss_id, boss.get("emoji", ""), 48),
                link(boss_id),
                boss.get("combat_level_required", "—"),
                boss_desc(boss_id),
            ]
            for boss_id, boss in sorted(bosses.items(), key=lambda x: x[1].get("combat_level_required", 0))
            if bool(boss.get("raid", False)) == raid
        ]

    header = ["", "Boss", "Combat Level", "Description"]
    return get_template("combat/bosses").format(
        boss_table=table(header, rows_for(raid=False)),
        raid_table=table(header, rows_for(raid=True)),
        mercenaries_link=link("mercenaries"),
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
        name=dungeon_name(dungeon["name"]),
        recommended_level=dungeon.get("recommended_level", "—"),
        description=dungeon_desc(dungeon["name"]),
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
            dungeon_desc(dungeon["name"]),
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
            ", ".join(dungeon_name(dungeon["name"]) for dungeon, _ in _get_dungeons_by_enemy(enemy_id)) or "—",
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
        name=enemy_name(enemy["name"]),
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
        [item_name(k), s["magic_level_required"], item_link(s["rune_type"]), s["rune_cost"], s["max_hit"]]
        for k, s in spells.items()
    ], key=lambda r: r[1])
    return get_template("combat/spells").format(
        spell_table=table(["Spell", "Magic Level", "Rune", "Runes / Cast", "Max Hit"], rows),
        void_staff_link=item_link("void_staff"),
        combat_footer=gen_combat_footer(),
    )


def _shop_item_rows(category: dict) -> list[list]:
    rows = []
    for item_id, item in category.get("items", {}).items():
        stock = item.get("stock", "unlimited")
        lvl_req = item.get("mercantile_level_required")
        req_str = f"Mercantile {lvl_req}" if lvl_req else "—"
        rows.append([
            item_name(item_id),
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
        if pet.get("effect_type") == "coin_boost":
            return f"+{pet['boost_percent']}% Coins"
        if "boosted_skill" in pet:
            try:
                return f"+{pet['boost_percent']}% {link(pet["boosted_skill"])} XP"
            except KeyError: # Page doesn't exist for skill - assume skill does not have a string
                return f"+{pet['boost_percent']}% {title(pet["boosted_skill"])} XP"
        # If pet does not boost a skill
        return f"+{pet['boost_percent']}% {title(pet["effect_type"])} XP"
    # If pet does not provide a boost
    return pet.get("effect_type", "—")


def gen_pets() -> str:
    pets = load("pets.json")
    assert isinstance(pets, dict)
    rows = [
        [
            f"{pet.get('emoji', '')} {pet_name(pet['id'])}".strip(),
            pet.get("source", "—"),
            _pet_boost(pet),
            pet_desc(pet["id"]),
        ]
        for pet in sorted(pets.values(), key=lambda x: pet_name(x["id"]))
    ]
    return get_template("miscellaneous/pets").format(
        pet_table=table(["Pet", "Source", "Bonus", "Description"], rows),
    )


def gen_workers() -> str:
    # Worker tier stats (mirrored from WorkerTier enum)
    # Todo: Move workers into an appropriate JSON file
    tiers = [
        ("Long Laborer", 8,  0.5,  5_000,  4.0,  "Uncapped (2 min/item)"),
        ("Apprentice",   8,  1.0,  10_000, 8.0,  "480 items"),
        ("Journeyman",   6,  1.5,  20_000, 9.0,  "540 items (40 sec/item)"),
        ("Master",       4,  2.5,  50_000, 10.0,  "600 items (24 sec/item)"),
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
    gathering_skills = ["mining", "fishing", "woodcutting", "agility", "thieving"]
    crafting_skills  = ["smithing", "cooking", "fletching", "crafting", "firemaking", "runecrafting", "herblore", "construction"]
    skill_rows = (
        [["Gathering", skill_name(s)] for s in gathering_skills] +
        [["Crafting",  skill_name(s)] for s in crafting_skills] +
        [["Support",   skill_name("prayer")]]
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


def _localised_quest_desc(quest_type: str, target: str, amount: int, guild: str):
    match quest_type:
        case "gather" | "craft":
            display_target = target if guild != "firemaking" else target.replace("ashes", "log")
            verb = STRINGS.get_string(f"daily_verb_{guild}")
            return STRINGS.get_string("guild_quest_desc_gather", verb, amount, item_name(display_target), guild_name(guild))
        case "kill":
            combat_style = STRINGS.get_string(f"guild_combat_{guild}")
            return STRINGS.get_string("guild_quest_desc_kill", amount, combat_style)
        case "prayer":
            return STRINGS.get_string("guild_quest_desc_prayer", amount, guild_name(guild))
        case "sessions":
            return STRINGS.get_string("guild_quest_desc_sessions", amount, agility_course_name(target), guild_name(guild))
        case "trade":
            return STRINGS.get_string("guild_quest_desc_trade", amount, trade_route_name(target), guild_name(guild))
        case "earn_coins":
            return STRINGS.get_string("guild_quest_desc_earn_coins", fmt_amount(amount), guild_name(guild))
        case "pickpocket":
            return STRINGS.get_string("guild_quest_desc_pickpocket", amount, thieving_npc_name(target), guild_name(guild))
        case "slayer_task":
            return STRINGS.get_string("guild_quest_desc_slayer_task", amount, guild_name(guild))
        case "slayer_kill":
            return STRINGS.get_string("guild_quest_desc_slayer_kill", amount)
        case _:
            LOGGER.warn_by_id(f"guild_quest_type:{quest_type}", f"The guild quest `{quest_type}` was not appropriately managed when generating the guilds page")
            return f"{amount}× {title(target)}"



def gen_guilds() -> str:
    # Todo: Create individual guild pages with daily quests listed
    guild_quests = load("guild_quests.json")
    assert isinstance(guild_quests, dict)

    # Dailies required per tier (mirrored from GuildRepository.DAILIES_REQUIRED_PER_TIER)
    dailies_required = [2, 3, 4, 5, 7, 9, 12, 15, 20, 25]
    dailies_rows = [[lvl, count] for lvl, count in enumerate(dailies_required)]
    dailies_table = table(["Guild Level", "Dailies Required"], dailies_rows)

    # Guild Hall reduction table (tier 0-3)
    reduction_rows = [[0, "No reduction"]]
    tiers = load("buildings.json")["guild_hall"]["tiers"]
    for i, tier in enumerate(tiers):
        reduction_rows.append([i, STRINGS.get_string("town_guild_active_bonus", int(tier["bonuses"]["guild_quest_reduction"] * 100))])
    reduction_table = table(["Guild Hall Tier", "Quest Requirement"], reduction_rows)

    # One section per guild, ordered to match ALL_GUILDS
    guild_order = [
        "mining", "fishing", "woodcutting", "farming", "thieving",
        "smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore", "firemaking", "construction",
        "warriors", "archers", "mages", "slayer",
        "prayer", "mercantile", "agility"
    ]
    guild_section_tpl = get_template("town/guild_section")
    sections = []
    for guild in guild_order:
        quests = sorted(
            [(k, q) for k, q in guild_quests.items() if q["guild"] == guild],
            key=lambda x: x[1]["guild_level_required"],
        )
        rows = []
        for key, q in quests:
            r = q["rewards"]
            reward_parts = []
            if r.get("coins"):
                reward_parts.append(f"{fmt_amount(r['coins'])} coins")
            if r.get("xp"):
                reward_parts.append(f"{fmt_amount(r['xp'])} XP")
            reward_parts.append(fmt_materials(r.get("items", {})))
            rows.append([
                quest_name(key),
                q["guild_level_required"],
                _localised_quest_desc(q["type"], q["target"], q["amount"], q["guild"]),
                ", ".join(reward_parts),
            ])
        quest_table = table(["Quest", "Guild Level", "Goal", "Rewards"], rows)
        sections.append(guild_section_tpl.format(
            guild_name=guild_name(guild),
            quest_table=quest_table,
        ))

    return get_template("town/guilds").format(
        dailies_table=dailies_table,
        reduction_table=reduction_table,
        guild_sections="\n\n".join(sections),
    )


def gen_buildings() -> str:
    def bonus_string(bonus: str, amount: float) -> str:
        match bonus:
            case "worker_xp":
                return STRINGS.get_string("town_inn_active_bonus", round(amount * 100))
            case "guild_quest_reduction":
                return STRINGS.get_string("town_guild_active_bonus", round(amount * 100))
            case "extra_blessing_hrs":
                return f"Blessing: {round(amount + 24)}h"
            case "farm_plots":
                return STRINGS.get_plural("town_garden_bonus", int(amount), amount)
            case "queue_slots":
                return STRINGS.get_plural("town_queue_master_bonus", int(amount), amount)
            case "passive_cape_category":
                return STRINGS.get_string(f"town_cape_rack_t{amount}_bonus")
            case "secondary_material_save_chance":
                return STRINGS.get_string(f"town_artisans_workshop_active_bonus", round(amount * 100))
            case "player_session_speed_reduction":
                return STRINGS.get_string("town_chronos_spire_active_bonus", round(amount * 100))
            case _:
                LOGGER.warn_by_id(f"building_bonus:{bonus}", f"The bonus `{bonus}` was not specially formatted on the buildings page")
                return f"+{amount} {bonus.replace("_", " ").title()}"

    # Building tiers mirrored from TownBuildingDef / TownRepository
    def building_section(building: dict) -> str:
        rows = [[0, "—", "—", "—", "No bonus"]]
        for i, tier_data in enumerate(building["tiers"], start=1):
            if building["key"] == "fairgrounds":
                bonus_str = STRINGS.get_string(f"town_fairgrounds_t{i}_bonus")
            else:
                bonus_str = ", ".join([bonus_string(bonus, amount) for bonus, amount in tier_data["bonuses"].items()])
            rows.append([i, tier_data["construction_level_required"], f"{tier_data["coin_cost"]:,}",
                         fmt_materials(tier_data["materials"]), bonus_str])
        # Return filled section
        return get_template("town/building_section").format(
            title=town_building_name(building["key"]),
            description=building["description"],
            stat_table=table(["Tier", "Construction Level", "Coin Cost", "Materials", "Bonuses"], rows)
        )

    # Add more information such as wiki-specific names and descriptions to the buildings
    buildings = load("buildings.json")
    assert isinstance(buildings, dict)
    additional_info = {
        "inn": "Increases the XP gained by both workers each session.",
        "guild_hall": "Reduces the quantity required for all guild quest targets.",
        "church": f"Extends the duration of the Prayer blessing activated from the {link("prayer")} skill.",
        "fairgrounds": "Unlocks additional Carnival minigames, reduces minigame cooldowns, and increases idle ticket drop chance.",
        "garden": f"Grants extra {link("farming")} plots for growing crops.",
        "queue_master": "Grants extra queue slots allowing you to queue more items than the default 3 slots.",
        "cape_rack": "Enables effects from specific capes even when not equipped.",
        "artisans_workshop": "Allows you to preserve secondary crafting materials such as ashes during crafting.",
        "chronos_spire": "Provides further reductions to the session time (excludes Inn workers).",
    }
    # Add title/description to buildings dictionary
    for building_key, data in buildings.items():
        if building_key not in additional_info:
            LOGGER.warn_by_id(f"missing_building_description:{building_key}", f"The building `{building_key}` is missing a description in the buildings page")
        data["description"] = additional_info.get(building_key, "No description provided")

    sections = []
    for _, data in buildings.items():
        sections.append(building_section(data))

    return get_template("town/buildings").format(
        construction_link=link("construction"),
        buildings_tables="\n\n---\n\n".join(sections)
    )


def gen_carnival() -> str:
    prizes = load("carnival_prizes.json")
    assert isinstance(prizes, dict)
    prize_rows = []
    for key, prize in sorted(prizes.items(), key=lambda x: x[1]["ticket_cost"]):
        match prize["type"]:
            case "equipment":
                prize_rows.append([item_link(key), fmt_amount(prize["ticket_cost"]), item_desc(key)])
            case "pet":
                prize_rows.append([link("pets", pet_name(key)), fmt_amount(prize["ticket_cost"]), pet_desc(key)])
            case "xp_lamp":
                prize_rows.append([carnival_prize_name(key), fmt_amount(prize["ticket_cost"]), carnival_prize_desc(key)])
            case _:
                LOGGER.warn_by_id(f"missing_prize_type:{key}", f"The prize type `{key}` was not specially formatted on the carnival page")
                prize_rows.append([carnival_prize_name(key), fmt_amount(prize["ticket_cost"]), carnival_prize_desc(key)])

    # Idle chance formula and active-game rewards mirrored from CarnivalSimulator /
    # CarnivalViewModel; only 4 active minigames are available at Fairgrounds tier 0
    # (Pick-a-Cup and Higher or Lower unlock at tiers 1 and 2 — see the Buildings page).
    idle_rows = [
        ["Archery Range", "Ranged"],
        ["Strongman Competition", "Strength"],
        ["Wizard's Duel", "Magic"],
        ["Fishing Derby", "Fishing"],
    ]
    active_rows = [
        ["Ring Toss", "Time your throw into the target zone", "2", "7"],
        ["Hammer Strike", "Time your swing for a strong hit", "1–2", "6–8"],
        ["Potion Sequence", "Repeat a growing memory sequence of potion colors", "2", "7"],
        ["Item Appraisal", "Pick the more valuable item", "2", "7"],
        [f"Pick-a-Cup ({link("buildings", "Fairgrounds", "fairgrounds")} tier 1+)", "Track which cup hides the gem through a shuffle", "4", "7"],
        [f"Higher or Lower ({link("buildings", "Fairgrounds", "fairgrounds")} tier 2+)", "Guess higher or lower over several rounds — more correct in a row pays more", "up to 5", "up to 8"],
    ]

    return get_template("town/carnival").format(
        fairgrounds_link=link("buildings", "Fairgrounds", "fairgrounds"),
        idle_table=table(["Minigame", "Skill Trained"], idle_rows),
        active_table=table(["Minigame", "How to play", "Normal", "Hard"], active_rows),
        prize_table=table(["Prize", "Ticket Cost", "Effect"], prize_rows),
    )


def _fmt_event_date(ms: int, is_end: bool = False) -> str:
    # end_ms is the exclusive start of the day after the event ends, so step back
    # 1ms to display the actual last day (e.g. Sept 1 00:00 -> "August 31, 2026").
    dt = datetime.fromtimestamp((ms - 1 if is_end else ms) / 1000, tz=timezone.utc)
    return f"{dt.strftime("%B")} {dt.day}, {dt.year}"


def gen_seasonal_events() -> str:
    events = load("seasonal_events.json")
    assert isinstance(events, dict)
    sections = []
    for event_id, event in events.items():
        reward_rows = [
            [f"{tier['tokens']:,}", seasonal_reward_desc(event_id, tier["tokens"])]
            for tier in event["reward_tiers"]
        ]
        sections.append(get_template("miscellaneous/seasonal_event_section").format(
            banner=html_image(icon_path(event["banner_icon"]), "", "text"),
            display_name=seasonal_event_name(event_id),
            start_date=_fmt_event_date(event["start_ms"]),
            end_date=_fmt_event_date(event["end_ms"], is_end=True),
            reward_table=table(["Tokens Needed", "Reward"], reward_rows),
        ))

    return get_template("miscellaneous/seasonal_events").format(event_sections="\n\n---\n\n".join(sections))


def _quest_rewards(rewards: dict) -> str:
    parts = []
    if rewards.get("coins"):
        parts.append(f"{rewards['coins']:,} coins")
    if rewards.get("xp"):
        parts.append(f"{rewards['xp']:,} XP")
    for item, qty in rewards.get("items", {}).items():
        parts.append(f"{qty}× {item_link(item)}")
    return ", ".join(parts) or "—"


def gen_quests() -> str:
    quests = load("quests.json")
    assert isinstance(quests, dict)
    # Todo: Have quests be grouped as they are in the game
    by_skill: dict[str, list] = {}
    for k, quest in quests.items():
        by_skill.setdefault(quest["skill"], []).append((k, quest))

    sections = []
    for skill in sorted(by_skill.keys()):
        quest_rows = [
            [quest_name(k), quest_desc(k), _quest_rewards(q.get("rewards", {}))]
            for k, q in sorted(by_skill[skill], key=lambda x: (quest_name(x[0]), x[1].get("tier", 0)))
        ]
        quest_table = table(["Quest", "Objective", "Rewards"], quest_rows)
        sections.append(f"## {skill_name(skill) if skill != "combat" else "Combat"}\n\n{quest_table}")

    return get_template("miscellaneous/quests").format(quest_sections="\n\n".join(sections))


def gen_titles() -> str:
    # Skill quest-chain titles (mirrored from TitleRepository.SKILL_TITLES)
    # Todo: Move these titles such that they aren't hardcoded in the game nor here
    skill_titles = {
        "smithing": "master_smith",
        "cooking": "head_chef",
        "mining": "master_miner",
        "fishing": "master_angler",
        "woodcutting": "master_woodcutter",
        "fletching": "master_fletcher",
        "crafting": "master_artisan",
        "runecrafting": "runemaster",
        "herblore": "master_herbalist",
        "construction": "master_builder",
        "prayer": "devout",
        "thieving": "master_thief",
        "firemaking": "flamekeeper",
        "slayer": "slayer",
    }
    skill_rows = [[title_name(t_name), f"Complete all {link("quests", skill_name(skill))} quests"] for skill, t_name in skill_titles.items()]
    skill_table = table(["Title", "Requirement"], skill_rows)

    # Guild mastery titles (mirrored from TitleRepository.GUILD_TITLES) — for guilds with no quest chain of their own
    # Todo: Move these titles such that they aren't hardcoded in the game nor here
    guild_titles = {
        "warriors": "warlord",
        "archers": "marksman",
        "mages": "archmage",
        "mercantile": "merchant_prince",
        "agility": "pathfinder",
        "farming": "master_farmer",
    }
    guild_rows = [[title_name(t_name), f"Reach max level (10) in the {guild_name(guild)}"] for guild, t_name in guild_titles.items()]
    guild_table = table(["Title", "Requirement"], guild_rows)

    other_rows = [
        ["Godslayer", "Defeat every boss at least once"],
        ["Patron of the Realm", "Complete the Grand Monument"],
    ]
    other_table = table(["Title", "Requirement"], other_rows)

    return get_template("miscellaneous/titles").format(
        skill_table=skill_table,
        guild_table=guild_table,
        other_table=other_table,
    )


def gen_combat_page() -> str:
    page = make_latex_safe(get_template("combat/combat"), 2).format(
        bosses_link=link("bosses"),
        dungeons_link=link("dungeons"),
        table_of_contents="{table_of_contents}",
        wiki_contribution_link=link("getting_started_wiki", "Contributing to the wiki"),
        combat_footer=gen_combat_footer()
    )
    return page.format(table_of_contents=f"## Table of contents\n\n{gen_table_of_contents(page)}")


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
    assert pet is None or isinstance(pet, dict)
    if pet:
        name = f"{pet.get('emoji', '')} {pet_name(pet["id"])}".strip()
        rare_loot_rows.append([link("pets", name), fmt_pct(pet.get("chance", 0.005))])

    defensive_rows = [
        ["Attack", defensive_stats.get("attack_defense", "—")],
        ["Strength", defensive_stats.get("strength_defense", "—")],
        ["Ranged", defensive_stats.get("ranged_defense", "—")],
        ["Magic", defensive_stats.get("magic_defense", "—")],
    ]

    xp = boss.get("xp_rewards", {})

    return get_template("combat/boss").format(
        icon=boss_icon(boss["id"], boss.get("emoji", ""), 192),
        name=boss_name(boss["id"]),
        combat_level=boss.get("combat_level_required", "—"),
        hp=f"{hp:,}" if isinstance(hp, int) else hp,
        duration=boss.get("duration_minutes", "—"),
        description=boss_desc(boss["id"]),
        boss_attack=combat_stats.get("attack_level", 0) + combat_stats.get("attack_bonus", 0),
        defensive_table=table(["Style", "Defence"], defensive_rows),
        boss_link=link("bosses"),
        xp_rewards=", ".join(f"{skill_name(sk)} {v:,}" for sk, v in xp.items()) if xp else "—",
        loot_table=table(["Item", "Min", "Max"], common_loot_rows) if common_loot_rows else "_No loot defined._",
        rare_drops_table=table(["Item", "Chance"], rare_loot_rows) if rare_loot_rows else "_No rare drops._",
        combat_footer=gen_combat_footer(),
    )

def _merc_tier_label(tier: str) -> str:
    key = f"merc_tier_{tier}"
    return STRINGS.get_string(key) if key in STRINGS else title(tier)


_MERC_TIER_ORDER = {"cheap": 0, "seasoned": 1, "elite": 2}


def gen_mercenaries() -> str:
    mercs = load("mercenaries.json")
    assert isinstance(mercs, list)
    ordered = sorted(mercs, key=lambda m: (_MERC_TIER_ORDER.get(m["tier"], 99), merc_name(m["id"])))
    rows = [
        [
            f"{merc['emoji']} {merc_name(merc['id'])}",
            _merc_tier_label(merc["tier"]),
            merc["combat_style"].title(),
            f"{merc['attack_level']} (+{merc['attack_bonus']})",
            f"{merc['strength_level']} (+{merc['strength_bonus']})",
            merc["defense_level"],
            merc["hp"],
            fmt_amount(merc["hire_cost"]),
        ]
        for merc in ordered
    ]
    return get_template("combat/mercenaries").format(
        merc_table=table(["Mercenary", "Tier", "Style", "Attack", "Strength", "Defence", "HP", "Cost per Day"], rows),
        bosses_link=link("bosses"),
    )


def gen_housing() -> str:
    data = load("house_tiles.json")
    assert isinstance(data, dict)
    room_rows = [
        [f"Room {i + 2}", room["level"], fmt_amount(room["coins"]), fmt_materials(room["materials"]), fmt_amount(room["xp"])]
        for i, room in enumerate(data["rooms"])
    ]
    expansion_rows = [
        [f"Room {i + 1}", fmt_amount(tier["coins"]), fmt_materials(tier["materials"]), fmt_amount(tier["xp"])]
        for i, tier in enumerate(data["expansion"])
    ]
    catalogue = {item_id: item for item_id, item in data["items"].items() if not item.get("hidden")}
    furniture_rows = sorted(
        (
            [
                house_item_name(item.get("name_key") or item_id),
                item.get("category", "").replace("_", " ").title(),
                item["level_required"],
                fmt_amount(item["coin_cost"]),
                fmt_materials(item.get("materials", {})),
                fmt_amount(item["xp"]),
            ]
            for item_id, item in catalogue.items()
        ),
        key=lambda row: (row[2], row[0]),
    )
    return get_template("town/housing").format(
        room_table=table(["Room", "Construction Level", "Coins", "Materials", "XP"], room_rows),
        expansion_table=table(["Room Tier", "Coins per Cell", "Materials per Cell", "XP per Cell"], expansion_rows),
        furniture_count=len(catalogue),
        furniture_table=table(["Item", "Category", "Level", "Coins", "Materials", "XP"], furniture_rows),
        grounds_count=len(data["grounds"]),
    )

# ---------------------------------------------------------------------------
# Custom generators for player guides
# ---------------------------------------------------------------------------

# Note: Original html file for the tower odds simulator had all of the food and enemy data hardcoded.
# However, when introducing html in the md file, for some reason the generation would fail.
# I found, however, that by using a custom_generator that simply took in the md file
# and then just returned it immediately, the generation worked fine.
# I'm not sure what the issue was, but just leaving this here in case it's useful to someone.
#
# Additional note: the custom function must be put in above the PLAYER_GUIDE_GENERATORS dictionary
# in order for the code to execute.
#
# One more note: If adding another guide in the wiki, it may be necessary to place it in the guides.yml
# file above the entry for the tower odds simulator,
# as having the custom generator seems to mess with entries below it.
#
# This function was written with the help of Gemma-4-E4B-it
def gen_guide_tower_odds_sim(guide: str) -> str:
    cooking_data = load("recipes/cooking.json") # Bring in food entries
    enemies_data = load("enemies.json")         # Bring in enemy stats
    food_map_placeholder = "{FOODMAP}"          # Placeholder in md file
    enemies_placeholder  = "{ENEMIES}"          # Placeholder in md file

    # -----------------------------------------------------------------
    # OPTIMIZATION: Define and use the list of valid tower enemies,
    # reducing website bandwidth
    TOWER_ENEMIES = {
        "goblin", "skeleton", "zombie",
        "orc_warrior", "dark_wizard", "bandit",
        "cave_troll", "shadow_beast", "demon",
        "forge_demon", "shadow_assassin", "abyssal_leech",
        "void_stalker", "void_guardian", "abyssal_lord",
        "void_archon", "eternal_sentinel"
    }
    
    # Create the food map in the same format as the original JS
    food_map_data = {}
    for food_key, attributes in cooking_data.items():
        healing_value = attributes.get("healing_value")
        if healing_value is not None:
            food_map_data[food_key] = {"heal": healing_value}
    items = []
    for key, data in food_map_data.items():
        items.append(f'"{key}": {{ heal: {data["heal"]} }}')
    food_map = "{" + ", ".join(items) + "}"

    # Create the enemy data in the same format as the original JS 
    new_enemies_data = {}
    def to_camel_case(snake_str):
        components = snake_str.split('_')
        return components[0] + ''.join(x.title() for x in components[1:])
    for enemy_id, data in enemies_data.items():
        # --- FILTERING LOGIC APPLIED HERE ---
        if enemy_id not in TOWER_ENEMIES:
            continue
        combat_stats = data.get("combat_stats", {})
        defensive_stats = data.get("defensive_stats", {})
        transformed_entry = {
            "hp": data.get("hp"),
            "combat": {},
            "defensive": {}
        }
        for key, value in combat_stats.items():
            camel_key = to_camel_case(key)
            transformed_entry["combat"][camel_key] = value
        for key, value in defensive_stats.items():
            camel_key = to_camel_case(key)
            transformed_entry["defensive"][camel_key] = value
        new_enemies_data[enemy_id] = transformed_entry
    entry_strings = []
    for enemy_id, entry in new_enemies_data.items():
        combat_str_parts = [f'{k}: {v}' for k, v in entry['combat'].items()]
        combat_str = "{" + ", ".join(combat_str_parts) + "}"
        defensive_str_parts = [f'{k}: {v}' for k, v in entry['defensive'].items()]
        defensive_str = "{" + ", ".join(defensive_str_parts) + "}"
        enemy_entry_str = (
            f'"{enemy_id}": {{ hp: {entry["hp"]}, combat: {combat_str}, defensive: {defensive_str} }}'
        )
        entry_strings.append(enemy_entry_str)
    enemies_string = "{" + ", ".join(entry_strings) + "}"
            
    # Replace the food map placeholder with the food map
    parts = guide.split(food_map_placeholder, 1)
    before_placeholder  = parts[0]
    after_placeholder   = parts[1]
    guide = before_placeholder + food_map + after_placeholder
    
    # Replace the enemies data placeholder with the enemies data
    parts = guide.split(enemies_placeholder, 1)
    before_placeholder  = parts[0]
    after_placeholder   = parts[1]    
    guide = before_placeholder + enemies_string + after_placeholder

    # Can simply return the updated guide string, the rest of the formatting is handled by other functions        
    return guide

# Add generators here if you want to reference game data
PLAYER_GUIDE_GENERATORS: dict[str, Callable[[str], str]] = {
    "guide_tower_odds_sim": gen_guide_tower_odds_sim,
    # guide_the_infinite_tower: gen_guide_the_infinite_tower(),
}

# Example player guides
#
# In this example, the JSON file for the fish in the game is getting loaded in and then being used to create a table
#     which can then be referenced in the guide using the {fish_table} field
# This also shows how you can include tables of content in your pages. Adding the table_of_content="{table_of_content}"
#     is necessary to ensure it doesn't cause any errors in Python when formatting the string twice
#
# def gen_guide_the_infinite_tower(guide: str) -> str:
#     fish_data = load("fish.json")
#     assert isinstance(fish_data, dict)
#     fish_rows = sorted(
#         [[item_name(k), v["level_required"], v["xp_per_catch"]] for k, v in fish_data.items()],
#         key=lambda r: r[1]
#     )
#     content = guide.format(
#         fish_table=table(["Fish", "Level req.", "XP / Fish"], fish_rows),
#         table_of_contents="{table_of_contents}"
#     )
#     return content.format(table_of_contents=gen_table_of_contents(content))

# ---------------------------------------------------------------------------
# Adding pages to the directory/hierarchy
# ---------------------------------------------------------------------------

# Add all relevant pages to the hierarchy and page directory
add_static_pages()
add_player_guides()
add_boss_pages()
add_enemy_pages()
add_dungeon_pages()
add_expedition_pages()
add_trade_route_pages()
