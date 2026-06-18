"""site.py -- HTML static site generator for the Idle Fantasy wiki."""
from __future__ import annotations

import re

import markdown as md_lib
from jinja2 import Environment, FileSystemLoader

from wiki.src import WIKI_ROOT
from wiki.src.page_hierarchy import PageHierarchy
from wiki.src.pages import get_pages, PAGE_DIRECTORY, PAGE_HIERARCHY

HTML_TEMPLATES = WIKI_ROOT / "html_templates"


# ---------------------------------------------------------------------------
# Markdown -> HTML helpers
# ---------------------------------------------------------------------------

# Page URL stems and titles -- used to distinguish page-level links from
# item cross-links when adding row fragments.
_PAGE_BY_URL: dict[str, str] = {
    pi.url.removesuffix(".md"): pi.title for pi in PAGE_DIRECTORY.values()
}


def _slugify(text: str) -> str:
    """Convert a display name to a row-id slug: 'Silver Ore' -> 'silver_ore'."""
    text = re.sub(r'<[^>]+>', '', text)      # strip HTML tags
    text = re.sub("['\\u2018\\u2019]", '', text)  # strip apostrophes (straight + curly)
    return re.sub(r'[^a-z0-9]+', '_', text.lower()).strip('_')


def _fix_page_links(html: str) -> str:
    """Add .html extension (and item row fragment) to relative wiki page hrefs.

    pages.py emits [text](PageStem) Markdown links and html_link() anchors.
    After the markdown library converts links to <a href="PageStem">text</a> we
    need to turn PageStem into PageStem.html (or PageStem.html#slug for item cross-links).
    """
    def fix(m: re.Match) -> str:
        href, text = m.group(1), m.group(2)
        if href not in _PAGE_BY_URL:
            return m.group(0)
        is_page_link = _PAGE_BY_URL[href] == text
        if is_page_link:
            return f'<a href="{href}.html">{text}</a>'
        slug = _slugify(text)
        return f'<a href="{href}.html#{slug}">{text}</a>'

    # Match <a href="Stem">display text</a> -- display may include emoji/spaces
    return re.sub(r'<a href="([^"#]+)">([^<]+)</a>', fix, html)


def _add_row_ids(html: str) -> str:
    """Add id="slug" to every <tbody> <tr> based on its first cell's text."""
    def process_tr(m: re.Match) -> str:
        tr_inner = m.group(1)
        first_td = re.search(r'<td>(.*?)</td>', tr_inner, re.DOTALL)
        if not first_td:
            return m.group(0)  # header row (<th>), skip
        slug = _slugify(first_td.group(1))
        if not slug:
            return m.group(0)
        return f'<tr id="{slug}">{tr_inner}</tr>'

    return re.sub(r'<tr>(.*?)</tr>', process_tr, html, flags=re.DOTALL)


def _md_to_html(text: str) -> str:
    # Todo: Improve fenced code blocks, maybe SuperFenced extension?
    html = md_lib.markdown(text, extensions=["tables", "toc", "fenced_code"])
    html = _fix_page_links(html)
    return _add_row_ids(html)


# ---------------------------------------------------------------------------
# Sidebar nav builder
# ---------------------------------------------------------------------------

def _build_nav(active_page_id: str | None, items: PageHierarchy | None = None) -> str:
    # Todo: Allow page hierarchies to be collapsible
    # Todo: Also make any existing HTML links active, not just md links, - eg. For the combat footer
    if items is None:
        items = PAGE_HIERARCHY
    lines = ["<ul>"]
    for item in items:
        if isinstance(item, str):
            page = PAGE_DIRECTORY[item]
            url = page.url.replace(".md", ".html")
            css = ' class="active"' if item == active_page_id else ""
            lines.append(f'<li><a href="{url}"{css}>{page.title}</a></li>')
        else:
            inner = _build_nav(active_page_id, item)
            lines.append(
                f'<li class="nav-section">'
                f'<span class="nav-label">{item.name}</span>'
                f'{inner}</li>'
            )
    lines.append("</ul>")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def get_html_pages() -> dict[str, str]:
    """Return {filename: html_string} for every wiki page plus CNAME."""
    env = Environment(loader=FileSystemLoader(str(HTML_TEMPLATES)), autoescape=False)
    base = env.get_template("base.html")

    md_pages = get_pages()
    html_pages: dict[str, str] = {}

    for md_filename, md_content in md_pages.items():
        if md_filename.startswith("_"):
            continue  # skip _Sidebar.md

        html_filename = md_filename.replace(".md", ".html")
        page_id = next(
            (pid for pid, pi in PAGE_DIRECTORY.items() if pi.url == md_filename),
            None,
        )
        page_title = PAGE_DIRECTORY[page_id].title if page_id else "Wiki"

        content_html = _md_to_html(md_content)
        nav_html = _build_nav(page_id)

        html_pages[html_filename] = base.render(
            page_title=page_title,
            content=content_html,
            nav=nav_html,
        )

    # index.html as alias for Home
    if "Home.html" in html_pages:
        html_pages["index.html"] = html_pages["Home.html"]

    # CNAME for GitHub Pages custom domain
    html_pages["CNAME"] = "idlefantasy.tristinbaker.xyz\n"

    return html_pages
