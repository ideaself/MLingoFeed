#!/usr/bin/env bash
# MLingoFeed release helper.
#
# Bumps the version, builds a signed release APK, commits + tags it and publishes
# a GitHub release with the APK attached.
#
# Usage:
#   scripts/release.sh 1.2.0              # full release (commit, tag, push, gh release)
#   scripts/release.sh 1.2.0 --no-push    # build + commit + tag locally
#   scripts/release.sh 1.2.0 --dry-run    # validate and print the plan only
#
# Requires: app/release.jks + MLINGOFEED_* entries in local.properties (both gitignored).
# On this machine the toolchain defaults to $HOME/tools/jdk17 and $HOME/tools/android-sdk
# and falls back to a cached Gradle distribution when ./gradlew cannot download one.
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-}"
shift || true
NO_PUSH=0
DRY_RUN=0
for arg in "$@"; do
    case "$arg" in
        --no-push) NO_PUSH=1 ;;
        --dry-run) DRY_RUN=1 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: scripts/release.sh <major.minor.patch> [--no-push|--dry-run]" >&2
    exit 2
fi

BUILD_FILE="app/build.gradle.kts"
KEYSTORE="app/release.jks"
LOCAL_PROPS="local.properties"
APK_SRC="app/build/outputs/apk/release/MLingoFeed-release.apk"
APK_DIR="build/releases"
APK_OUT="$APK_DIR/MLingoFeed-$VERSION.apk"

# --- preconditions -----------------------------------------------------------
[[ -f "$BUILD_FILE" ]] || { echo "Run from the repo root." >&2; exit 1; }
if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean; commit or stash first." >&2
    exit 1
fi
if [[ ! -f "$KEYSTORE" ]]; then
    echo "Missing $KEYSTORE — restore it from your backup (it is gitignored)." >&2
    exit 1
fi
if ! grep -q "MLINGOFEED_STORE_PASSWORD" "$LOCAL_PROPS" 2>/dev/null; then
    echo "Missing MLINGOFEED_* signing entries in $LOCAL_PROPS." >&2
    exit 1
fi
if [[ "$NO_PUSH" == 0 ]] && ! gh auth status >/dev/null 2>&1; then
    echo "gh is not authenticated; run 'gh auth login' or use --no-push." >&2
    exit 1
fi

CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$BUILD_FILE" | head -1)
CURRENT_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -1)
NEXT_CODE=$((CURRENT_CODE + 1))
TAG="v$VERSION"

echo "Release plan:"
echo "  versionName : $CURRENT_NAME -> $VERSION"
echo "  versionCode : $CURRENT_CODE -> $NEXT_CODE"
echo "  tag         : $TAG"
echo "  apk         : $APK_OUT"
echo "  push        : $([[ "$NO_PUSH" == 1 ]] && echo no || echo yes)"
if [[ "$DRY_RUN" == 1 ]]; then
    exit 0
fi

# --- toolchain ---------------------------------------------------------------
export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/tools/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
GRADLE="./gradlew"
# The wrapper blocks for minutes when it cannot reach services.gradle.org, so give it a
# short window and then fall back to an already downloaded distribution.
if ! timeout 60 ./gradlew --version >/dev/null 2>&1; then
    for candidate in "$HOME"/.gradle/wrapper/dists/gradle-*-bin/*/gradle-*/bin/gradle; do
        if [[ -x "$candidate" ]]; then
            GRADLE="$candidate"
            break
        fi
    done
fi
echo "Using Gradle: $GRADLE"

# --- version bump ------------------------------------------------------------
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEXT_CODE/" "$BUILD_FILE"
sed -i "s/versionName = \"$CURRENT_NAME\"/versionName = \"$VERSION\"/" "$BUILD_FILE"

# --- build & verify ----------------------------------------------------------
"$GRADLE" assembleRelease --console=plain
APKSIGNER="$(ls "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | tail -1)"
if [[ -n "$APKSIGNER" && -x "$APKSIGNER" ]]; then
    "$APKSIGNER" verify --print-certs "$APK_SRC" | head -3
fi
mkdir -p "$APK_DIR"
cp "$APK_SRC" "$APK_OUT"

# --- commit, tag, publish ----------------------------------------------------
git add "$BUILD_FILE"
git commit -m "Release $VERSION"
git tag -a "$TAG" -m "MLingoFeed $VERSION"

if [[ "$NO_PUSH" == 1 ]]; then
    echo "Built $APK_OUT, committed and tagged $TAG (not pushed)."
    exit 0
fi

git push origin HEAD
git push origin "$TAG"
NOTES=$(mktemp)
cat > "$NOTES" <<EOF
## MLingoFeed $VERSION

已签名的正式构建 (versionCode $NEXT_CODE)。本次版本的改动见仓库提交历史。
EOF
gh release create "$TAG" "$APK_OUT" --title "MLingoFeed $VERSION" --notes-file "$NOTES"
rm -f "$NOTES"
echo "Published: $(gh release view "$TAG" --json url -q .url)"
