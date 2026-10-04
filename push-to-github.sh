#!/usr/bin/env bash
# FleetPath - upload to GitHub and build the APK (macOS / Linux)
set -e
cd "$(dirname "$0")"
USER_NAME="jignesh-rapol"
REPO_NAME="FleetPath"
REPO_URL="https://github.com/$USER_NAME/$REPO_NAME.git"

command -v git >/dev/null || { echo "Install Git first: https://git-scm.com/downloads"; exit 1; }
[ -d .git ] || git init -b main >/dev/null
if [ -z "$(git config user.email)" ]; then
  read -rp "Email address of your GitHub account: " EMAIL
  git config user.email "$EMAIL"
fi
[ -n "$(git config user.name)" ] || git config user.name "Jignesh Rapol"
git add -A
git commit -m "FleetPath Android app" >/dev/null 2>&1 || true

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  gh repo create "$REPO_NAME" --private --source=. --remote=origin --push
else
  echo "Create an EMPTY repository named $REPO_NAME at https://github.com/new (no README), then press Enter."
  read -r
  git remote remove origin 2>/dev/null || true
  git remote add origin "$REPO_URL"
  git push -u origin main
fi
echo
echo "Done! The APK builds in about 5 minutes at: https://github.com/$USER_NAME/$REPO_NAME/actions"
