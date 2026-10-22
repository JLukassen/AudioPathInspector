#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="JLukassen/AudioPathInspector"
command -v git >/dev/null || { echo 'Install git: sudo dnf install git' >&2; exit 1; }
command -v gh >/dev/null || { echo 'Install GitHub CLI: sudo dnf install gh' >&2; exit 1; }
gh auth status >/dev/null || { echo 'Authenticate first: gh auth login' >&2; exit 1; }
# Deliberately never create, change visibility, force-push, or overwrite existing history.
gh repo view "$REPO" >/dev/null || { echo "GitHub repository $REPO is not accessible" >&2; exit 1; }
if [[ -n "$(git ls-remote --heads "https://github.com/$REPO.git")" ]]; then
  echo "Remote $REPO already has commits. Clone it and manually review/commit changes; refusing to overwrite." >&2
  exit 1
fi
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  [[ "$(git rev-parse --show-toplevel)" == "$PWD" ]] || { echo 'Nested Git checkout detected; aborting' >&2; exit 1; }
else
  git init -b main
fi
if git remote get-url origin >/dev/null 2>&1; then
  echo 'Existing origin detected; inspect it manually before pushing.' >&2
  exit 1
fi
git add .
if ! git diff --cached --quiet; then git commit -m 'Initial Audio Path Inspector 0.3.0 with Shizuku hardware diagnostics'; fi
git branch -M main
git remote add origin "https://github.com/$REPO.git"
git push -u origin main
echo "Published https://github.com/$REPO"
