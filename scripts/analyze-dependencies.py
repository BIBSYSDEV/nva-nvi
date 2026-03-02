#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10"
# ///
"""
Analyze .aws-sam/build handler dependencies to find large and duplicated JARs.

Usage:
    ./scripts/analyze-dependencies.py            # Top 25 JARs by size (default)
    ./scripts/analyze-dependencies.py -n 50      # Top 50
    ./scripts/analyze-dependencies.py --count 0  # All JARs
"""

import argparse
import sys
from collections import defaultdict
from pathlib import Path

SAM_BUILD_DIR = Path(".aws-sam/build")


def main():
    parser = argparse.ArgumentParser(description="Analyze SAM build handler dependencies.")
    parser.add_argument(
        "--count", "-n", type=int, default=25,
        help="Number of JARs to list, sorted by size (default: 25, 0 for all)",
    )
    args = parser.parse_args()

    if not SAM_BUILD_DIR.is_dir():
        print(f"Error: {SAM_BUILD_DIR} not found. Run './scripts/sam-build.sh' first.")
        sys.exit(1)

    jar_sizes: dict[str, int] = {}
    jar_handlers: dict[str, set[str]] = defaultdict(set)

    for jar_path in SAM_BUILD_DIR.rglob("lib/*.jar"):
        handler_name = jar_path.relative_to(SAM_BUILD_DIR).parts[0]
        jar_name = jar_path.name
        size = jar_path.stat().st_size

        jar_sizes[jar_name] = max(jar_sizes.get(jar_name, 0), size)
        jar_handlers[jar_name].add(handler_name)

    handler_count = len({h for handlers in jar_handlers.values() for h in handlers})
    print(f"=== Dependency analysis for {handler_count} handlers ===\n")

    sorted_jars = sorted(jar_sizes.items(), key=lambda x: x[1], reverse=True)
    displayed_jars = sorted_jars if args.count == 0 else sorted_jars[:args.count]

    shown = len(displayed_jars)
    total = len(sorted_jars)
    label = f"Top {shown} of {total}" if shown < total else f"All {total}"
    print(f"{label} JARs by size:\n")

    header = f"{'JAR File':<60} {'Size (MB)':>10} {'Handlers':>10} {'Total (MB)':>12}"
    separator = f"{'--------':<60} {'---------':>10} {'--------':>10} {'----------':>12}"
    print(header)
    print(separator)

    for jar_name, size in displayed_jars:
        count = len(jar_handlers[jar_name])
        jar_mb = size / 1_048_576
        total_mb = jar_mb * count
        print(f"{jar_name:<60} {jar_mb:>10.2f} {count:>10} {total_mb:>12.2f}")

    # Totals always cover all JARs, not just displayed ones
    all_grand_total = sum(s * len(jar_handlers[n]) for n, s in sorted_jars)
    all_unique_total = sum(s for _, s in sorted_jars)

    print()
    print(f"Grand total (all JARs across all handlers): {all_grand_total / 1_048_576:>10.2f} MB")
    print(f"Unique JARs total:                          {all_unique_total / 1_048_576:>10.2f} MB")
    print(f"Duplication overhead:                       {(all_grand_total - all_unique_total) / 1_048_576:>10.2f} MB")


if __name__ == "__main__":
    main()
