# Batch jobs

The batch job system provides a queue-based approach for processing NVI candidates and periods with optimistic locking. It consists of two handlers:

- **StartBatchJobHandler**: Scans DynamoDB and adds work items to `BatchJobWorkQueue`
- **ProcessBatchJobHandler**: Processes individual work items using transactional service methods

A "work item" in this context is a request to do a specific operation on an individual `Candidate` or `NviPeriod`.

### Architecture diagrams

![Batch job architecture](./batchjobs/architecture.svg)
![Batch job data flow](./batchjobs/data_flow.svg)


To regenerate diagrams:
```bash
d2 architecture.d2 -l tala
d2 data_flow.d2 -l tala
```

## Job types

### Refresh candidates

Reads candidates from DB and writes them back, triggering "on read" migrations (if any) and OpenSearch reindexing.

```json
{
  "jobType": "REFRESH_CANDIDATES",
  "filter": { "reportingYears": ["2024", "2025"] }
}
```

### Migrate candidates

Reads candidates from DB, fetches original publication from S3, enriches with missing data, and writes back.

```json
{
  "jobType": "MIGRATE_CANDIDATES",
  "filter": { "reportingYears": ["2024"] }
}
```

### Refresh periods

Reads periods from DB and writes them back.

```json
{
  "jobType": "REFRESH_PERIODS"
}
```

## Filters and other optional parameters

The only filter implemented for now is `ReportingYearFilter`, which limits the scan to a list of years.
Using this filter routes the scan through the GSI for reporting years, which does not support parallel scans.
This effectively means that `ReportingYearFilter` and `maxParallelSegments` are mutually exclusive.

Other filters may be implemented later as needed.


| Parameter | Default | Description                                                                           |
|-----------|---------|---------------------------------------------------------------------------------------|
| `filter.reportingYears` | All years | List of years to process.                                                             |
| `maxParallelSegments` | 10 | Number of parallel DynamoDB scan segments (only used when no year filter is present). |
| `maxItems` | No limit | Maximum number of work items to add to work queue. Useful for testing.                |

**Example: Test with limited items**
```json
{
  "jobType": "REFRESH_CANDIDATES",
  "filter": { "reportingYears": ["2024"] },
  "maxItems": 10
}
```

**Example: Full table scan with custom parallelism**
```json
{
  "jobType": "REFRESH_CANDIDATES",
  "maxParallelSegments": 5
}
```

## How to trigger

1. Open AWS Console → Lambda → `StartBatchJobHandler`
2. Go to the Test tab
3. Paste one of the payloads above
4. Click Test

## Adjusting throughput

Edit the SQS trigger on `ProcessBatchJobHandler`:

1. Lambda → `ProcessBatchJobHandler` → Configuration → Triggers
2. Edit SQS trigger:
   - **BatchSize**: 1-10 items per Lambda invocation
3. For concurrency control, adjust Lambda reserved concurrency

## Emergency stop

**To stop loading new items:**
- Set `PROCESSING_ENABLED=false` environment variable on `StartBatchJobHandler`

**To stop processing:**
- Disable SQS trigger on `ProcessBatchJobHandler`
- Optionally purge `BatchJobWorkQueue` if items should be discarded

## Error recovery

Failed items go to `BatchJobDLQ` after 3 attempts. To reprocess:

1. AWS Console → SQS → `BatchJobDLQ`
2. Click "Start DLQ redrive"
3. Select "Redrive to source queue"
4. Monitor `ProcessBatchJobHandler` logs for processing
