#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

info()  { echo -e "${GREEN}==>${NC} $*"; }
error() { echo -e "${RED}==>${NC} $*"; exit 1; }

# check scala
if command -v scala &>/dev/null; then
  SCALA=$(command -v scala)
elif command -v scala-cli &>/dev/null; then
  SCALA=$(command -v scala-cli)
else
  error "scala or scala-cli not found. Install: https://scala-lang.org/download/"
fi
info "using $SCALA"

# install atlas
ATLAS_DIR="$HOME/.atlas"
REPO_URL="https://github.com/yjgbg-labs/atlas.git"

if [ -d "$ATLAS_DIR" ]; then
  info "updating atlas..."
  git -C "$ATLAS_DIR" pull --ff-only
else
  info "cloning atlas..."
  git clone "$REPO_URL" "$ATLAS_DIR"
fi

info "atlas installed at $ATLAS_DIR"
info "starting atlas MCP server..."
exec "$SCALA" run "$ATLAS_DIR" "$@"
