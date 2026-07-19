from pathlib import Path

WIKI_ROOT  = Path(__file__).parents[1]
REPO_ROOT  = WIKI_ROOT.parent
ASSETS     = REPO_ROOT / "app" / "src" / "main" / "assets" / "data"
RESOURCES  = REPO_ROOT / "app" / "src" / "main" / "res"
TEMPLATES  = WIKI_ROOT / "templates"
