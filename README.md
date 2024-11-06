# nva-nvi
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/6f12579c9d494090bdd117ab5b737b6d)](https://app.codacy.com/gh/BIBSYSDEV/nva-nvi/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/6f12579c9d494090bdd117ab5b737b6d)](https://app.codacy.com/gh/BIBSYSDEV/nva-nvi/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

## OpenSearch Indexes

When building a new environment or the search indexes are deleted and needs to
be rebuilt, make sure to run the `InitHandler`.

## How to re-index

If the index already exists, and it is not necessary to update mappings, skip
to step 3.

1. Trigger `DeleteNviCandidateIndexHandler`. Verify that the index is deleted.
2. Trigger `InitHandler`. Verify that the index is created.
3. Trigger `NviBatchScanStartHandler` with input:

```json
{
  "types": [
    "CANDIDATE"
  ]
}
```

## How to requeue candidates in IndexDLQ

The `IndexDLQ` is a shared DLQ for all handlers related to indexing.
`NviRequeueDlqHandler` consumes messages from the `IndexDLQ`, and updates the
candidate in the DB with a new version. This will trigger the indexing flow
for the candidates.

Default `count` (number of messages consumed from DLQ) is 10. To specify
another `count`, provide it as input:

```json
{
  "count": 100
}
```

## DLQ Redrives

See template for which DLQs are available for redrive (configured with
`RedrivePolicy`). To start a DLQ redrive, locate the DLQ in the AWS console
(SQS) and press _Start DLQ Redrive_.
