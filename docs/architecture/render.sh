#!/usr/bin/env bash
# Regenerates every diagram in diagrams/ from the D2 sources in d2/.
# Requires the D2 CLI (brew install d2). Layout engine and dark theme come from d2/styles.d2.
set -euo pipefail
cd "$(dirname "$0")"
for source in d2/boundary.d2 d2/evaluation.d2 d2/indexing.d2 d2/curation-api.d2 d2/reports.d2; do
  name="$(basename "$source" .d2)"
  d2 "$source" "diagrams/$name.svg"
done
