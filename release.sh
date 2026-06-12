#!/usr/bin/env bash
# release.sh — full release pipeline for IdleFantasy
#
# Usage: ./release.sh "Commit message describing the release"
#
# What it does:
#   1. Reads versionName + versionCode from app/build.gradle.kts
#   2. Commits all pending changes
#   3. Tags the commit as vX.X.X
#   4. Pushes main + tag to origin
#   5. Updates metadata/com.tristinbaker.idlefantasy.yml (F-Droid build entry)
#   6. Commits + pushes the metadata update
#   7. Does a fresh clone from GitHub, checks out the tag, and builds the release APK
#   8. Copies APK into docs/fdroid/repo/, runs fdroid update, commits + pushes
#   9. Prints the APK path and GitHub release URL

set -euo pipefail

# Ensure user-local binaries (e.g. fdroid at ~/.local/bin) are always on PATH
export PATH="$HOME/.local/bin:$PATH"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$REPO_DIR/app/build.gradle.kts"
METADATA_FILE="$REPO_DIR/metadata/com.tristinbaker.idlefantasy.yml"
FDROID_DIR="$REPO_DIR/docs/fdroid"
CHANGELOG_ASSET="$REPO_DIR/app/src/main/assets/changelog.txt"
CLONE_DIR="/tmp/FantasyIdler-release"

# ---------------------------------------------------------------------------
# Argument
# ---------------------------------------------------------------------------

COMMIT_MSG="${1:-}"
if [[ -z "$COMMIT_MSG" ]]; then
    echo "Usage: $0 \"Commit message\""
    exit 1
fi

# ---------------------------------------------------------------------------
# Read version from build.gradle.kts
# ---------------------------------------------------------------------------

VERSION_NAME=$(grep '^\s*versionName\s*=' "$GRADLE_FILE" | sed 's/.*"\(.*\)".*/\1/')
VERSION_CODE=$(grep '^\s*versionCode\s*=' "$GRADLE_FILE" | grep -oP '\d+')
TAG="v$VERSION_NAME"

echo "==> Release: $TAG (versionCode $VERSION_CODE)"

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------

cd "$REPO_DIR"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
    echo "ERROR: not on main (current branch: $BRANCH)"
    exit 1
fi

if git rev-parse "$TAG" &>/dev/null; then
    echo "ERROR: tag $TAG already exists"
    exit 1
fi

if [[ -z "${DEFIDE_STORE_PASSWORD:-}" || -z "${DEFIDE_KEY_PASSWORD:-}" ]]; then
    echo "ERROR: DEFIDE_STORE_PASSWORD and DEFIDE_KEY_PASSWORD must be set in the environment"
    exit 1
fi

if ! command -v fdroid &>/dev/null; then
    echo "ERROR: fdroid not found on PATH (looked in $PATH)"
    exit 1
fi

# ---------------------------------------------------------------------------
# Unit tests
# ---------------------------------------------------------------------------

echo "==> Running unit tests..."
cd "$REPO_DIR"
./gradlew testDebugUnitTest
echo "==> Unit tests passed"

# ---------------------------------------------------------------------------
# Sync missing English placeholder strings into all language resource files
# ---------------------------------------------------------------------------

echo "==> Syncing translation stubs..."
python3 - "$REPO_DIR/app/src/main/res" <<'PYEOF'
import re, os, sys

RES_DIR = sys.argv[1]
LANGS = ['values-de', 'values-es', 'values-fr', 'values-tr', 'value-es-rES']
FILES = [
    'strings.xml', 'strings_game.xml', 'strings_items.xml',
    'strings_skills.xml', 'strings_enemies.xml',
    'strings_notifications.xml', 'strings_quests.xml',
]

entry_re = re.compile(r'( {4}<(string(?:-array)?) [^>]*name="([^"]+)".*?</\2>)', re.DOTALL)
name_re  = re.compile(r'<(?:string|string-array)\s[^>]*name="([^"]+)"')

for filename in FILES:
    en_path = os.path.join(RES_DIR, 'values', filename)
    if not os.path.exists(en_path):
        continue
    en_content = open(en_path).read()

    en_entries, en_seen = [], set()
    for m in entry_re.finditer(en_content):
        full, name = m.group(1), m.group(3)
        if name not in en_seen and 'translatable="false"' not in full:
            en_entries.append((name, full))
            en_seen.add(name)

    for lang in LANGS:
        lang_path = os.path.join(RES_DIR, lang, filename)
        if not os.path.exists(lang_path):
            continue
        lang_content = open(lang_path).read()
        lang_names   = set(name_re.findall(lang_content))

        stubs = [entry for name, entry in en_entries if name not in lang_names]
        if not stubs:
            continue

        new_content = lang_content.replace('</resources>', '\n'.join(stubs) + '\n</resources>')
        open(lang_path, 'w').write(new_content)
        print(f'  {lang}/{filename}: +{len(stubs)} keys')
PYEOF
echo "==> Translation stubs synced"

# ---------------------------------------------------------------------------
# Commit pending changes
# ---------------------------------------------------------------------------

# Copy fastlane changelog into the app asset so the in-app "What's New" dialog shows it
FASTLANE_CHANGELOG="$REPO_DIR/fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
if [[ -f "$FASTLANE_CHANGELOG" ]]; then
    { echo "v$VERSION_NAME"; cat "$FASTLANE_CHANGELOG"; echo; cat "$CHANGELOG_ASSET" 2>/dev/null; } > /tmp/changelog_merged.txt
    mv /tmp/changelog_merged.txt "$CHANGELOG_ASSET"
    echo "==> Prepended changelog ${VERSION_CODE}.txt to assets/changelog.txt"
else
    echo "WARNING: No fastlane changelog found at $FASTLANE_CHANGELOG"
fi

git add -A
if ! git diff --cached --quiet; then
    git commit -m "$COMMIT_MSG"
    echo "==> Committed: $COMMIT_MSG"
else
    echo "==> Nothing to commit"
fi

# ---------------------------------------------------------------------------
# Tag + push
# ---------------------------------------------------------------------------

git tag "$TAG"
echo "==> Tagged $TAG"

git push origin main
git push origin "$TAG"
echo "==> Pushed main + $TAG"

COMMIT_HASH=$(git rev-parse "$TAG")
echo "==> Commit hash: $COMMIT_HASH"

# ---------------------------------------------------------------------------
# Update F-Droid metadata
# ---------------------------------------------------------------------------

python3 -c "
import sys, re
path     = sys.argv[1]
ver      = sys.argv[2]
code     = sys.argv[3]
commit   = sys.argv[4]

content = open(path).read()

entry = (
    f\"  - versionName: '{ver}'\n\"
    f\"    versionCode: {code}\n\"
    f\"    commit: {commit}\n\"
    f\"    subdir: app\n\"
    f\"    gradle:\n\"
    f\"      - yes\n\"
    f\"\n\"
)

content = content.replace('AllowedAPKSigningKeys:', entry + 'AllowedAPKSigningKeys:')
content = re.sub(r'^CurrentVersion:.*', f'CurrentVersion: {ver}', content, flags=re.M)
content = re.sub(r'^CurrentVersionCode:.*', f'CurrentVersionCode: {code}', content, flags=re.M)

open(path, 'w').write(content)
print('Metadata updated.')
" "$METADATA_FILE" "$VERSION_NAME" "$VERSION_CODE" "$COMMIT_HASH"

git add "$METADATA_FILE"
git commit -m "Update F-Droid metadata commit hash for $TAG"
git push
echo "==> F-Droid metadata committed and pushed"

# ---------------------------------------------------------------------------
# Clean clone + release build
# ---------------------------------------------------------------------------

echo "==> Building from clean clone..."
rm -rf "$CLONE_DIR"
git clone https://github.com/tristinbaker/IdleFantasy.git "$CLONE_DIR"
cd "$CLONE_DIR"
git checkout "$TAG"
./gradlew assembleRelease

APK="$CLONE_DIR/app/build/outputs/apk/release/app-release.apk"

# ---------------------------------------------------------------------------
# Update custom F-Droid repo (docs/fdroid)
# ---------------------------------------------------------------------------

echo "==> Updating custom F-Droid repo..."
cd "$REPO_DIR"

# Copy APK named by versionCode so old versions remain available
cp "$APK" "$FDROID_DIR/repo/com.tristinbaker.idlefantasy_${VERSION_CODE}.apk"

# Regenerate signed index
cd "$FDROID_DIR"
fdroid update

# Deploy generated index + APKs to gh-pages so GitHub Pages (custom domain) serves them
GH_PAGES_WORK="/tmp/gh-pages-fdroid-deploy"
rm -rf "$GH_PAGES_WORK"
cd "$REPO_DIR"
git worktree add "$GH_PAGES_WORK" gh-pages
git -C "$GH_PAGES_WORK" pull --rebase origin gh-pages
mkdir -p "$GH_PAGES_WORK/fdroid/repo" "$GH_PAGES_WORK/fdroid/archive"
rsync -a "$FDROID_DIR/repo/" "$GH_PAGES_WORK/fdroid/repo/"
rsync -a "$FDROID_DIR/archive/" "$GH_PAGES_WORK/fdroid/archive/" 2>/dev/null || true
cd "$GH_PAGES_WORK"
git add fdroid/
git diff --cached --quiet || git commit -m "Update F-Droid repo for $TAG"
git push origin gh-pages
cd "$REPO_DIR"
git worktree remove "$GH_PAGES_WORK"
echo "==> Custom F-Droid repo updated and pushed (gh-pages)"

# ---------------------------------------------------------------------------
# Create GitHub release
# ---------------------------------------------------------------------------

echo "==> Creating GitHub release..."
cd "$REPO_DIR"

PREV_TAG=$(git tag --sort=-version:refname | grep -A1 "^${TAG}$" | tail -1)

RELEASE_FLAGS=(
    --title "$TAG"
    --notes-file "$FASTLANE_CHANGELOG"
    --latest
    --verify-tag
)
[[ -n "$PREV_TAG" ]] && RELEASE_FLAGS+=(--notes-start-tag "$PREV_TAG")

gh release create "$TAG" "$APK#app-release.apk" "${RELEASE_FLAGS[@]}"
echo "==> GitHub release created (previous: ${PREV_TAG:-none})"

# ---------------------------------------------------------------------------
# Regenerate wiki
# ---------------------------------------------------------------------------

echo "==> Regenerating wiki..."
cd "$REPO_DIR"
python3 -m wiki.src update
echo "==> Wiki updated"

echo ""
echo "======================================================"
echo "  Release $TAG complete"
echo "  APK:     $APK"
echo "  F-Droid: https://tristinbaker.github.io/IdleFantasy/fdroid/repo"
echo "  GitHub:  https://github.com/tristinbaker/IdleFantasy/releases/tag/$TAG"
echo "  Wiki:    https://github.com/tristinbaker/IdleFantasy/wiki"
echo "======================================================"
