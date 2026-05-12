#!/usr/bin/env bash
set -euo pipefail

REPO_HOME="$HOME/.local/share/atlas"
REPO_URL="https://github.com/yjgbg-labs/atlas.git"

if ! command -v scala &>/dev/null && ! command -v scala-cli &>/dev/null; then
  echo "atlas: scala or scala-cli not found" >&2
  exit 1
fi
SCALA=$(command -v scala 2>/dev/null || command -v scala-cli)

# clone or update
if [ -d "$REPO_HOME/.git" ]; then
  git -C "$REPO_HOME" pull --ff-only --quiet
else
  rm -rf "$REPO_HOME"
  git clone --quiet "$REPO_URL" "$REPO_HOME"
fi

# compile & run
cd "$REPO_HOME"
"$SCALA" compile . --suppress-directives-in-multiple-files-warning 2>/dev/null
exec "$SCALA" run . --suppress-directives-in-multiple-files-warning "$@"
