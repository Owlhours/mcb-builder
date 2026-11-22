#!/usr/bin/env bash
# The adapter rule: only code under adapter/ may use Minecraft's classes. Lists every Java file elsewhere that
# imports or names net.minecraft or com.mojang, and fails if there is one. (CI runs this on every push.)
set -euo pipefail
cd "$(dirname "$0")/.."
bad=$(git ls-files '*.java' | grep -v '^adapter/' | xargs -r grep -l -E 'net\.minecraft|com\.mojang' || true)
total=$(git ls-files '*.java' | grep -vc '^adapter/' || true)
if [ -n "$bad" ]; then
    echo "Minecraft used outside adapter/ in:"; echo "$bad" | sed 's/^/  /'; exit 1
fi
echo "adapter rule holds: 0 of $total Java files outside adapter/ use Minecraft's classes"
