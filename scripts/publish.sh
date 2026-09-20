#!/usr/bin/env bash
# Finish a release that is already built locally: push the commit + tag and create the
# GitHub release. Use it when scripts/release.sh stopped after committing/tagging
# (for example when the network dropped during `git push`).
#
# Usage:
#   scripts/publish.sh 1.2.5                 # push + create the GitHub release
#   scripts/publish.sh 1.2.5 notes.md        # …with custom release notes
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-}"
NOTES_FILE="${2:-}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: scripts/publish.sh <major.minor.patch> [notes-file]" >&2
    exit 2
fi

TAG="v$VERSION"
APK="build/releases/MLingoFeed-$VERSION.apk"

[[ -f "$APK" ]] || { echo "Missing $APK — run scripts/release.sh $VERSION first." >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || { echo "Missing tag $TAG." >&2; exit 1; }
if ! gh auth status >/dev/null 2>&1; then
    echo "gh is not authenticated; run 'gh auth login' first." >&2
    exit 1
fi

retry() {
    local attempts="$1" delay=5
    shift
    for ((i = 1; i <= attempts; i++)); do
        if "$@"; then
            return 0
        fi
        echo "  (attempt $i/$attempts failed; retrying in ${delay}s)" >&2
        sleep "$delay"
        delay=$((delay * 2))
    done
    return 1
}

retry 5 git push origin HEAD
retry 5 git push origin "$TAG"

if gh release view "$TAG" >/dev/null 2>&1; then
    retry 5 gh release upload "$TAG" "$APK" --clobber
else
    NOTES=$(mktemp)
    if [[ -n "$NOTES_FILE" && -f "$NOTES_FILE" ]]; then
        cp "$NOTES_FILE" "$NOTES"
    else
        cat > "$NOTES" <<EOF
## MLingoFeed $VERSION

已签名的正式构建。本次版本的改动见仓库提交历史。
EOF
    fi
    if ! retry 5 gh release create "$TAG" "$APK" --title "MLingoFeed $VERSION" --notes-file "$NOTES"; then
        rm -f "$NOTES"
        echo "Failed to create the GitHub release for $TAG." >&2
        exit 1
    fi
    rm -f "$NOTES"
fi

echo "Published: $(gh release view "$TAG" --json url -q .url)"
