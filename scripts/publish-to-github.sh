#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

VISIBILITY="${1:---private}"
case "$VISIBILITY" in
  --private|--public) ;;
  *) echo 'Usage: bash scripts/publish-to-github.sh [--private|--public]' >&2; exit 2 ;;
esac

command -v git >/dev/null || { echo 'Install git first: sudo dnf install git' >&2; exit 1; }
command -v gh >/dev/null || { echo 'Install GitHub CLI first: sudo dnf install gh' >&2; exit 1; }
gh auth status >/dev/null || { echo 'Authenticate first: gh auth login' >&2; exit 1; }

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  TOP=$(git rev-parse --show-toplevel)
  if [[ "$TOP" != "$PWD" ]]; then
    echo "This directory is nested in a different Git repository: $TOP" >&2
    exit 1
  fi
else
  git init -b main
fi

if git remote get-url origin >/dev/null 2>&1; then
  echo 'An origin remote is already configured. Aborting to prevent accidental overwrites.' >&2
  exit 1
fi

git add .
if ! git diff --cached --quiet; then
  git commit -m "Initial Audio Path Inspector 0.2.0 with launcher icon"
fi

gh repo create JLukassen/AudioPathInspector "$VISIBILITY" \
  --description "Android audio path diagnostics: device inventory, Shizuku, ALSA and audio HAL metadata" \
  --source=. --remote=origin --push

echo 'Published to GitHub; inspect the printed repository URL.'
