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

# ---------------------------------------------------------------------------
# Commit pending changes
# ---------------------------------------------------------------------------

# Copy fastlane changelog into the app asset so the in-app "What's New" dialog shows it
FASTLANE_CHANGELOG="$REPO_DIR/fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
if [[ -f "$FASTLANE_CHANGELOG" ]]; then
    cp "$FASTLANE_CHANGELOG" "$CHANGELOG_ASSET"
    echo "==> Copied changelog ${VERSION_CODE}.txt to assets"
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

cd "$REPO_DIR"
git add docs/fdroid/repo/
git commit -m "Update F-Droid repo for $TAG"
git push
echo "==> Custom F-Droid repo updated and pushed"

# ---------------------------------------------------------------------------
# Regenerate wiki
# ---------------------------------------------------------------------------

echo "==> Regenerating wiki..."
cd "$REPO_DIR"
python3 scripts/generate_wiki.py
echo "==> Wiki updated"

echo ""
echo "======================================================"
echo "  Release $TAG complete"
echo "  APK:     $APK"
echo "  F-Droid: https://tristinbaker.github.io/IdleFantasy/fdroid/repo"
echo "  Upload:  https://github.com/tristinbaker/IdleFantasy/releases/tag/$TAG"
echo "  Wiki:    https://github.com/tristinbaker/IdleFantasy/wiki"
echo "======================================================"
