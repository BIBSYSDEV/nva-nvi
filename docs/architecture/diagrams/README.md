# Rendered diagrams

Generated from the D2 sources in [`../d2/`](../d2/); do not edit by hand, run [`../render.sh`](../render.sh).

## System boundary

![System boundary of nva-nvi](boundary.svg)

Explained on the [architecture index page](../README.md#system-boundary).

## Evaluation flow

![Evaluation flow](evaluation.svg)

Explained on the [evaluation page](../evaluation.md#flow).

## Worklist search and approval

![Worklist search and approval](curation-api.svg)

Explained on the [curation API page](../curation-api.md#worklist-and-approval).

## Asynchronous report generation

![Asynchronous report generation](reports.svg)

Explained on the [reports page](../reports.md#asynchronous-report-generation).

## Indexing flow, all paths

![Indexing flow, all paths](indexing/index.svg)

Explained on the [indexing page](../indexing.md#flow); the four boards below come from the same D2 file and fade everything except one path.

## Indexing: candidate insert or applicable update

![Indexing path for a candidate insert or applicable update](indexing/candidate-upsert.svg)

Explained on the [indexing page](../indexing.md#one-write-at-a-time).

## Indexing: approval insert, update, or removal

![Indexing path for an approval insert, update, or removal](indexing/approval-change.svg)

Explained on the [indexing page](../indexing.md#one-write-at-a-time).

## Indexing: candidate becomes not applicable

![Indexing path when a candidate becomes not applicable](indexing/candidate-not-applicable.svg)

Explained on the [indexing page](../indexing.md#one-write-at-a-time).

## Indexing: candidate row removed

![Indexing path when a candidate row is removed](indexing/candidate-removed.svg)

Explained on the [indexing page](../indexing.md#one-write-at-a-time).
