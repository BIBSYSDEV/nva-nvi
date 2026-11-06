#!/usr/bin/env bash
# Regenerates every diagram in diagrams/ from the D2 sources in d2/.
# Requires the D2 CLI (brew install d2). The TALA layout engine is selected in d2/styles.d2.

set -euo pipefail
cd "$(dirname "$0")"
for source in d2/boundary.d2 d2/evaluation.d2 d2/indexing.d2 d2/curation-api.d2 d2/reports.d2; do
  name="$(basename "$source" .d2)"
  d2 --scale 1 "$source" "diagrams/$name.svg"
done
