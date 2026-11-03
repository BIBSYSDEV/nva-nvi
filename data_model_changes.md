# Current vs planned data model in NVI

Status: Draft

We need to change the data model in `nva-nvi` to handle new requirements and address gaps in the current implementation.
Production data is frozen to prevent data loss and we cannot open a new reporting period until these issues are resolved.
There are related issues and planned features that may be affected by changes in data model, which should be considered.

## Context and motivation

Motivations for changes:

- Users want to see Candidate metadata (approvals/notes) for a publication in all years, not just the current year
- Product owner wants audit trail / change history to see when changes were made and what was changed
- Business rules require Publications and Candidates to be "1:1 within a Period"
  - Current implementation is "1:1" regardless of period
  - 1:1 relationship between Candidate and Publication means we lose data when publication year changes and Candidate "moves" to a different period
- Team wants to duplicate data between environments for testing

### Domain models and terminology

| Name         | Definition                                                                                                            | Owner             | Identity                                       | Used for                                                                        |
| ------------ | --------------------------------------------------------------------------------------------------------------------- | ----------------- | ---------------------------------------------- | ------------------------------------------------------------------------------- |
| Publication  | A scholarly work registered in NVA.                                                                                   | `publication-api` | URI / identifier                               | Source of the data being evaluated for NVI reporting                            |
| Organization | A node in the Norwegian institutional hierarchy (faculty, institute, department, etc.).                               | Cristin           | URI / identifier (e.g. `194.1.2.3`)            | Identifies the unit a contributor is affiliated with                            |
| Institution  | Top-level Organization (root of an Organization tree). Every Organization either is an Institution or belongs to one. | Cristin           | URI / identifier (top-level, e.g. `194.0.0.0`) | The unit responsible for approving a Candidate                                  |
| Period       | A reporting period for NVI (currently one per year).                                                                  | NVI               | `year`                                         | Scopes Candidates and Approvals; controls open/closed state                     |
| Candidate    | A Publication evaluated for NVI reporting in a specific Period.                                                       | NVI               | `candidateIdentifier`                          | Core entity in NVI; represents a Publication in the NVI workflow for one Period |
| Approval     | An Institution's stance on a Candidate (`PENDING` / `APPROVED` / `REJECTED`).                                         | NVI               | (Candidate, Institution)                       | Per-Institution workflow state                                                  |
| Note         | A user-authored comment attached to a Candidate.                                                                      | NVI               | `noteIdentifier`                               | Cross-institution context and communication                                     |
| LogEntry     | An audit record of a write operation.                                                                                 | NVI               | `entryIdentifier`                              | Audit trail / change history                                                    |

Terminology:

- `id` => full URL with environment, e.g. `https://api.sandbox.nva.aws.unit.no/publication/0197c70b2124-64472eac-3873-4a5a-bec5-1480a2db1775` or `https://api.sandbox.nva.aws.unit.no/cristin/organization/194.0.0.0`
- `identifier` => identifying segment of `id`, e.g. `0197c70b2124-64472eac-3873-4a5a-bec5-1480a2db1775` or `194.0.0.0`
- "Candidate aggregate": Composite of Candidate, its Approvals, and its Notes. Scoped to a single Period.
  - Periods are a separate root aggregate, referenced by `year`.
  - Used inconsistently, so "Candidate" can refer to either the full aggregate or just the `Candidate` part of the data model
- `year` in the data model below refers to the reporting period matching the publication year at the time the Publication became a Candidate
  - Reporting periods map to years now, but this may change later (multiple periods per calendar year)

### Relevant resources

Related Jira tickets:

- [NVI candidates in closed periods should not be evaluated](https://sikt.atlassian.net/browse/NP-51099)
- [NVI - Avslutning](https://sikt.atlassian.net/browse/NP-47549)
  - [Refine NVI reporting](https://sikt.atlassian.net/browse/NP-48230)
  - [Implement reporting period lifecycle](https://sikt.atlassian.net/browse/NP-49541): Maybe unnecessary with these changes?
- [Add log entry when result are NVI-rapported](https://sikt.atlassian.net/browse/NP-50635)
- [Open for override approvals after closed NVI-period](https://sikt.atlassian.net/browse/NP-51004)
- [Handle deleted publications](https://sikt.atlassian.net/browse/NP-48622)

Resources:

- [Implementing version control in DynamoDB](https://aws.amazon.com/blogs/database/implementing-version-control-using-amazon-dynamodb/)

## Goals and non-goals

Overview of what this change tries to solve and what is explicitly out of scope.

### Business rules / invariants

- Uniqueness in Period: At most one Candidate per Publication per Period
- Candidates stay in their Period: Candidates never move between Periods
- Report only once: A Publication can only be Reported once
- Immutable once Reported: Candidate metadata for a Reported Publication cannot be altered
- Auditability: Must preserve change history
- Conflicting writes not allowed:
  - Concurrent updates to an Approval and the parent Candidate: First write succeeds, others fail
  - Concurrent updates to the same Approval: First write succeeds, others fail
  - Concurrent updates to different Approvals under the same Candidate: All succeed

### Goals

Existing features/requirements to keep:

- Concurrent Approvals: Users from different institutions can update their own Approvals on the same Candidate concurrently without blocking each other
- Reset Approvals: Approvals must reset to `PENDING` if the Candidate is changed and the changes affect NVI reporting (e.g. point calculation or publication year)
- Freeze Approvals: Users can only update their Approvals in an open Period
- Preserve past Candidates: Candidate for a Publications that is re-evaluated as a NonCandidate is updated to prevent it from being reported, but not deleted from the database

New or changed requirements:

- Audit trail: Preserve change history to support future audits
  - Keep all versions of records so we do not overwrite or modify business data
  - Create concise log of changes so we can show users "what / when / who" of changes
- Preserve status for non-reported Candidates in closed Period
- Link Candidates across Periods: Users want to see approvals and notes for the same Publication from all Periods where it has been a Candidate

### Non-goals / out of scope

Non-goals:

- Handle deleted Publications: Out of scope, separate ticket.
- Detect duplicate Publications with different IDs: Out of scope, this is the responsibility of `publication-api`.
- Avoid resetting Approvals on reverted changes: Out of scope and difficult to implement. Users want to avoid re-reviewing if changes are reverted (e.g. publication year changed `2025 -> 2026 -> 2025`).

### Future considerations

- Exceptions in grace period: Admins should be able to allow updates for specific Candidates in a closed Period
  - Re-evaluation of Candidate to update metadata
  - Allow users to update Approvals
  - Override user Approval directly with explanation logged
- Cross-environment duplication: Current implementation persists environment-specific URLs to DynamoDB, which prevents us from easily duplicating environments (e.g. to create a staging environment mirroring production). For the new data model, avoid using URLs in PK/SK, GSIs, or top-level attributes.
- Persist evaluations for all Publications: Persist result of evaluating a Publication even if it doesn't fulfill the requirements to become a Candidate. Relevant context if it becomes a valid Candidate in a future Period.
- Logging batch jobs: Could add log entries for batch jobs (migration/reindexing) to have a record of which jobs have run and when

### Open questions / wishlist

- Frontend/UX: Should users see Notes/Approvals from 2025 when looking at a 2026 Candidate, or should they be linked to a separate page for the 2025 Candidate?

## Current state

Current schema:

| Item                     | PK                                         | SK                                         | identifier              | periodYear |
| ------------------------ | ------------------------------------------ | ------------------------------------------ | ----------------------- | ---------- |
| Period                   | `PERIOD`                                   | `PERIOD#<year>`                            | `<year>`                | `null`     |
| CandidateUniquenessEntry | `CandidateUniquenessEntry#<publicationId>` | `CandidateUniquenessEntry#<publicationId>` | `null`                  | `null`     |
| Candidate                | `CANDIDATE#<candidateIdentifier>`          | `CANDIDATE#<candidateIdentifier>`          | `<candidateIdentifier>` | `<year>`   |
| Approval                 | `CANDIDATE#<candidateIdentifier>`          | `APPROVAL_STATUS#<organizationId>`         | `<candidateIdentifier>` | `null`     |
| Note                     | `CANDIDATE#<candidateIdentifier>`          | `NOTE#<noteIdentifier>`                    | `<candidateIdentifier>` | `null`     |

- Updates replace the existing document
- Deletes remove the existing document
- Creating a new Candidate is done in a transaction that also creates a `CandidateUniquenessEntry` + relevant set of `Approval`

## Proposed design

Constraints / notes:

- Must fetch Candidate aggregate with strongly consistent read (single query)
- Fetching Candidate aggregate with single query relies on shared partition key (cannot query by partial PK ("beginsWith"), only in SK)
- Data model should be monotonic (append-only)
- Can create GSIs with subset of data (identifiers/keys) to save space, by using `KEYS_ONLY` projection
- Audit trail: Persist log entry as part of every write transaction
- Cross-environment duplication: Do not use environment-specific URLs as identifiers

### New schema

Key points:

- Candidate aggregates all use the same PK: `PUBLICATION#<publicationIdentifier>`
- Candidate aggregates all use the same SK prefix: `PERIOD#<year>`
- All records have a version suffix
- Store PK/SK components as separate attributes on each record, not just embedded in the key
- Support fetching all NVI data for a publication: Use `publicationIdentifier` as partition key
- Include at least one `LogEntry` record with every single write transaction
- Uniqueness: Enforce max 1 Candidate per Publication per Period with sentinel record
- Immutability: Never delete data, store past versions
  - All writes in transactions with condition that PK+SK does not already exist
  - All inserts explicitly set `version=0` in SK
- Sortable log entries: Use `SortableIdentifier` for `LogEntry.entryIdentifier` to make them sorted chronologically
- TODO: Prevent race conditions by mutating counter on sentinel record?

| Item                     | PK                                    | SK                                                                  | Attributes                              | Notes                                                                |
| ------------------------ | ------------------------------------- | ------------------------------------------------------------------- | --------------------------------------- | -------------------------------------------------------------------- |
| Period                   | `PERIOD`                              | `PERIOD#<year>#<version>`                                           | `year, version`                         | Period state                                                         |
| CandidateUniquenessEntry | `PUBLICATION#<publicationIdentifier>` | `PERIOD#<year>#CANDIDATE_SENTINEL`                                  | `year, seq_no, candidateIdentifier`     | Sentinel record to prevent duplicates within period (static version) |
| Candidate                | `PUBLICATION#<publicationIdentifier>` | `PERIOD#<year>#CANDIDATE#VERSION#<version>`                         | `year, version, candidateIdentifier`    | Publication metadata "as reported"                                   |
| Approval                 | `PUBLICATION#<publicationIdentifier>` | `PERIOD#<year>#APPROVAL#<organizationIdentifier>#VERSION#<version>` | `year, version, organizationIdentifier` | Set of approvals from different institutions                         |
| Note                     | `PUBLICATION#<publicationIdentifier>` | `PERIOD#<year>#NOTE#<noteIdentifier>#VERSION#<version>`             | `year, version, noteIdentifier`         | Notes per Candidate                                                  |
| LogEntry (Period)        | `PERIOD`                              | `PERIOD#<year>#LOG#<entryIdentifier#VERSION#<version>`              | `year, version, entryIdentifier`        | Change events per Period                                             |
| LogEntry (Candidate)     | `PUBLICATION#<publicationIdentifier>` | `PERIOD#<year>#LOG#<entryIdentifier#VERSION#<version>`              | `year, version, entryIdentifier`        | Change events per Candidate (could include JSON-diff?)               |
| LogEntry (BatchJob)      | `BATCH_JOB#<jobIdentifier>`           | `LOG#<entryIdentifier>#VERSION#<version>`                           | `version, entryIdentifier`              | Log of batch jobs                                                    |

### Access patterns

Overview of current and planned access patterns.

Period:

- Create Period by `year`
  - Constraint: Fail if Period exists for this `year`
- Update Period by `year`
  - Constraint: Fail if Period has been updated concurrently
- Get Period by `year`
- Get all Periods

Candidate:

- Create Candidate aggregate by `publicationIdentifier` + `year`
  - Constraint: Fail if Candidate exists for this `publicationIdentifier` + `year`
- Update Candidate aggregate by `publicationIdentifier` + `year`
  - Constraint: Fail if Candidate has been updated concurrently
- Get Candidate identifiers by `year` (for batch processing / reindexing)
- Get Candidate aggregates by `publicationIdentifier`
- Get Candidate aggregate by `publicationIdentifier` + `year`
- Get Candidate aggregate by `candidateIdentifier` (could be deprecated, but used by frontend now)

Approval:

- Create/Update/Delete Approval by `publicationIdentifier` + `year` + `organizationIdentifier`
  - Constraint: Fail if Candidate has been updated concurrently
  - Constraint: Fail if Approval has been updated concurrently

Note:

- Create Note by `publicationIdentifier` + `year`
- Delete Note by `publicationIdentifier` + `year` + `noteIdentifier`

LogEntry (speculative):

- Get log entries for a Period by `year` (review changes to single Period)
- Get log entries for all Candidates by `year` (review recent changes to Candidates)
- Get log entries for all Candidates by `publicationIdentifier` (review changes in Candidate status for Publication)
- Get log entries for time range (review all recent changes)

### Reading current state of aggregate

Pseudo-code for how to read the current state of the Candidate aggregate:

```java
private QueryRequest aggregateQuery(String publicationIdentifier, String year) {
  var partitionKey = "PUBLICATION#" + publicationIdentifier;
  var sortKeyPrefix = "PERIOD#" + year + "#";
  return QueryRequest.builder()
      .tableName(NVI_TABLE_NAME)
      .keyConditionExpression("#pk = :pk AND begins_with(#sk, :skPrefix)")
      .expressionAttributeNames(Map.of("#pk", HASH_KEY, "#sk", SORT_KEY))
      .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(partitionKey),":skPrefix", AttributeValue.fromS(sortKeyPrefix)))
      .consistentRead(true)
      .build();
}

public Optional<CandidateAggregate> fetchCurrentAggregate(String publicationIdentifier, String year) {
  var rows = defaultClient.query(aggregateQuery(publicationIdentifier, year)).items();
  var liveRows = getNewestVersionsOnly(rows)
                      .filter(not(row -> isTombstone(row)))
                      .toList();
  return assembleAggregate(liveRows);
}
```

### Example write sequence

Write 1: First evaluation creates new Candidate:

- Create Sentinel 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` => `seq_no=0`
- Create Candidate 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE#VERSION#0` = Valid Candidate
- Create Approval 1: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#194.0.0.0#VERSION#0` = `PENDING`
- Create Approval 2: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#186.0.0.0#VERSION#0` = `PENDING`
- Create Log entry 1: `PUBLICATION#123` + `PERIOD#2025#LOG#<uuid>#VERSION#0` = `CREATE_CANDIDATE`
- Constraint: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` does not exist

Write 2: Updated Approval:

- Update Approval 1: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#194.0.0.0#VERSION#1` = `APPROVED`
- Create Log entry 2: `PUBLICATION#123` + `PERIOD#2025#LOG#<uuid>#VERSION#0` = `UPDATE_APPROVAL`
- Constraint: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` has `seq_no=0`

Write 3: Re-evaluation due to updated Publication title:

- Update Sentinel 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` => `seq_no=1`
- Update Candidate 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE#VERSION#1` = Valid Candidate
- Create Log entry 3: `PUBLICATION#123` + `PERIOD#2025#LOG#<uuid>#VERSION#0` = `UPDATE_METADATA_WITHOUT_RESETTING_APPROVALS`
- Constraint: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` has `seq_no=0`

Write 4: Re-evaluation due to updated Publication channel:

- Update Sentinel 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` => `seq_no=2`
- Update Candidate 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE#VERSION#2` = Valid Candidate
- Update Approval 1: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#194.0.0.0#VERSION#2` = `PENDING`
- Update Approval 2: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#186.0.0.0#VERSION#1` = `PENDING`
- Create Log entry 4: `PUBLICATION#123` + `PERIOD#2025#LOG#<uuid>#VERSION#0` = `UPDATE_METADATA_AND_RESET_APPROVALS`
- Constraint: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` has `seq_no=1`

Write 5: Re-evaluation due to updated Publication year:

- Update first Candidate aggregate
  - Update Sentinel 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` => `seq_no=3`
  - Update Candidate 1: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE#VERSION#3` = Invalid Candidate
  - Update Approval 1: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#194.0.0.0#VERSION#3` = `PENDING`
  - Update Approval 2: `PUBLICATION#123` + `PERIOD#2025#APPROVAL#186.0.0.0#VERSION#2` = `PENDING`
  - Create Log entry 5: `PUBLICATION#123` + `PERIOD#2025#LOG#<uuid>#VERSION#0` = `UPDATE_METADATA_AND_RESET_APPROVALS`
- Create new Candidate aggregate
  - Create Candidate 2: `PUBLICATION#123` + `PERIOD#2026#CANDIDATE#VERSION#0` = Valid Candidate
  - Create Approval 3: `PUBLICATION#123` + `PERIOD#2026#APPROVAL#194.0.0.0#VERSION#0` = `PENDING`
  - Create Approval 4: `PUBLICATION#123` + `PERIOD#2026#APPROVAL#186.0.0.0#VERSION#0` = `PENDING`
  - Create Log entry 6: `PUBLICATION#123` + `PERIOD#2026#LOG#<uuid>#VERSION#0` = `CREATE_CANDIDATE`
  - Create Sentinel 2: `PUBLICATION#123` + `PERIOD#2026#CANDIDATE_SENTINEL` => `seq_no=0`
- Constraint: `PUBLICATION#123` + `PERIOD#2025#CANDIDATE_SENTINEL` has `seq_no=2`
- Constraint: `PUBLICATION#123` + `PERIOD#2026#CANDIDATE_SENTINEL` does not exist

## Migration plan

TODO: This section left blank

## Alternatives considered

- Event sourcing: Not investigated properly, but seems overly complex and is a much larger change to the existing data model.

## Open questions / decisions needed

- TODO: Prevent excessive duplication of data
  - Duplication on reindexing:
    - Problem: Current reindexing workflow triggers write of Candidate aggregate with no change in content
    - Solution: Change reindexing to skip DB writes, just dump identifier to indexing queue directly
  - Duplication from event storm:
    - Problem: Known issue with excessive evaluations of the same Publication because of many change events from `publication-api` at the same time
    - Solution: ???

- TODO: What to use for version suffix? Incrementing integer, timestamp, `sortableIdentifier`?
- TODO: Decide on versioning strategy
  - Append version suffix, pick max on each read?
    - Problem: Must either pad ("00001") or convert to integer application side to sort?
  - Sentinel record pointing to most recent version?
  - Sentinel record duplicating most recent version?
- TODO: Decide how to prevent race conditions on concurrent writes with new model
  - Suggestion: Use revision counter on sentinel record, incremented on each write to aggregate
- FIXME: "Concurrent updates to the same Approval: First write succeeds, others fail" not handled
  - Drop this requirement and switch to last-write-wins?
  - Add sentinel records for Approval too?

## Risks / known unknowns

TODO: Not done yet

- Excessive writes and data duplication: If we duplicate data on every write, the size of the dataset will grow.
- Unexpected/unplanned access patterns may turn up and require new GSIs.
  - Get all log entries for a Period?
  - Get most recent log entries across Period?
- May be difficult to compare versions of same record to see semantic diff later (nested JSON with arrays)
- Changes in data model may make it difficult to read/restore old versions of records
- DynamoDB TransactWriteItems hard limit (100 items, 4 MB) may cause problems. Split large write transactions safely?
