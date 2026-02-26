# Report Aggregation Queries — Design Notes

## Overview

Four query types mapping to the OpenAPI endpoints:

| Endpoint                                | Query class            | Returns                              |
| --------------------------------------- | ---------------------- | ------------------------------------ |
| `/reports`                              | `AllPeriodsQuery`      | `List<PeriodReport>`                 |
| `/reports/{period}`                     | `PeriodQuery`          | `PeriodReport`                       |
| `/reports/{period}/institutions`        | `AllInstitutionsQuery` | `List<InstitutionAggregationResult>` |
| `/reports/{period}/institutions/{inst}` | `InstitutionQuery`     | `InstitutionReport`                  |

## Query Structure

### Sealed interface

Expand the permits clause as queries are added:

```java
public sealed interface ReportAggregationQuery<T>
    permits AllPeriodsQuery, PeriodQuery, AllInstitutionsQuery, InstitutionQuery {
```

### Aggregation composition

All 4 queries share the same inner aggregation pattern at different depths:

```
globalStatus → localStatus → sum(points)
```

Each query wraps this differently:

- **AllInstitutions**: `nested(approvals) → terms(institutionId) → statusAggregation`
- **Period**: `nested(approvals) → statusAggregation`
- **AllPeriods**: `terms(year) → nested(approvals) → statusAggregation`
- **Institution**: `nested(approvals) → filter(institutionId) → terms(unit) → statusAggregation`

Extract the shared chain into a reusable building block (e.g. `StatusAggregation.byGlobalAndLocalStatus()`) to avoid duplicating it across 4 aggregation classes.

### Query filtering

| Query           | Filter                                                                            |
| --------------- | --------------------------------------------------------------------------------- |
| AllPeriods      | `match_all` (or filter by accessible periods)                                     |
| Period          | `term(reportingPeriod.year, period)`                                              |
| AllInstitutions | `term(reportingPeriod.year, period)`                                              |
| Institution     | `term(reportingPeriod.year, period)` + nested filter on `approvals.institutionId` |

The period filter is reused 3/4 times — extract as a shared static method.

### Response parsing

The bucket-walking logic (`parseGlobalStatusBuckets`, `parseLocalStatusBuckets`, `computeUndisputed`, `computeReportedTotals`) in `AllInstitutionsQuery` is identical for all 4 queries. Extract into a shared utility (e.g. `AggregationResponseParser`).

## Test Strategy

### Testing at the handler level

Tests assert on the actual HTTP response (status code + body), not on internal query models. This verifies the full pipeline: request → handler → query → OpenSearch → response parsing → DTO mapping → serialization.

Internal models (`CandidateTotal`, `LocalStatusSummary`, `InstitutionAggregationResult`) are implementation details covered implicitly. They may still deserve a few focused unit tests for edge cases (e.g. `CandidateTotal.add()`), but the main assertions live at the response level.

### Scenario-centric organization

Tests are organized by **scenario** (data setup), not by query type. One scenario sets up data once, then all relevant handlers/endpoints are tested against it. Since setup is shared and paid for once, we use **one assertion per test** — splitting into many focused `@Test` methods costs nothing.

```java
class ReportHandlerIntegrationTest {

    @Nested @TestInstance(PER_CLASS)
    @DisplayName("Two institutions with mixed statuses")
    class TwoInstitutionsMixedStatuses {

        @BeforeAll void setupAndIndex() { /* index data once */ }
        @AfterAll void cleanup() { /* delete index */ }

        // AllInstitutionsReport assertions
        @Test void allInstitutions_returnsOk() { ... }
        @Test void allInstitutions_hasExpectedInstitutionCount() { ... }
        @Test void allInstitutions_hasCorrectTypeField() { ... }
        @Test void allInstitutions_institutionA_hasExpectedValidPoints() { ... }
        @Test void allInstitutions_institutionA_hasExpectedDisputedCount() { ... }
        @Test void allInstitutions_institutionA_hasExpectedUndisputedProcessedCount() { ... }
        @Test void allInstitutions_institutionA_hasExpectedUndisputedTotalCount() { ... }
        @Test void allInstitutions_institutionB_hasExpectedValidPoints() { ... }

        // PeriodReport assertions
        @Test void periodReport_returnsOk() { ... }
        @Test void periodReport_hasExpectedValidPoints() { ... }
        @Test void periodReport_hasExpectedDisputedCount() { ... }
        @Test void periodReport_hasExpectedGlobalStatusCounts() { ... }

        // AllPeriodsReport assertions
        @Test void allPeriods_returnsOk() { ... }
        @Test void allPeriods_includesBothPeriods() { ... }

        // InstitutionReport assertions
        @Test void institutionReport_returnsOk() { ... }
        @Test void institutionReport_hasExpectedSector() { ... }
    }
}
```

The response can be parsed once in `@BeforeAll` alongside the data setup, then each `@Test` asserts a single property. This keeps tests readable and gives precise failure messages.

### Scenarios

#### Scenario 1: Single institution, all status combinations

- One institution with all 10 legal status combinations
- Verifies counting logic, dispute filtering, points summation
- Good for initial development; may be folded into Scenario 2 later

#### Scenario 2: Two institutions with different candidate distributions

- Institution A: all status combinations
- Institution B: only a subset (e.g. 3 PENDING + 1 APPROVED)
- Verifies institutions are correctly separated, different counts per institution
- Also includes irrelevant documents from other periods for cross-period filtering

#### Scenario 3: Collaboration (multi-institution candidate)

- One candidate with approvals from institution A and B
- Should count in both institutions' aggregations
- Points attributed per-institution (not double-counted globally)

#### Scenario 4: Empty results

- Query for a period with no candidates → empty list / zero counts

### What differs per endpoint

| Assertion                 | AllPeriods        | Period          | AllInstitutions | Institution     |
| ------------------------- | ----------------- | --------------- | --------------- | --------------- |
| Period filtering          | Shows all periods | Filters to 1    | Filters to 1    | Filters to 1    |
| Institution filtering     | All               | All             | All             | Filters to 1    |
| Returns institutions list | No (aggregated)   | No (aggregated) | Yes             | No (single)     |
| Returns unit breakdown    | No                | No              | No              | Yes             |
| validPoints               | Per period        | For period      | Per institution | For institution |
| Global status counts      | Per period        | For period      | Per institution | For institution |

### Response DTOs

The handler maps internal models to response DTOs matching the OpenAPI schema. These are the assertion targets:

- `AllPeriodsReport` — `{ type, id, periods: [PeriodReport] }`
- `PeriodReport` — `{ type, id, period, totals: PeriodTotals, byGlobalApprovalStatus }`
- `AllInstitutionsReport` — `{ type, id, period, institutions: [InstitutionReport] }`
- `InstitutionReport` — `{ type, id, period, sector, institution, institutionSummary, units? }`

Where `PeriodTotals` / `InstitutionTotals` / `UnitTotals` all share the `TotalsBase` shape:
`{ validPoints, disputedCount, undisputedProcessedCount, undisputedTotalCount }`

## Current TODOs / FIXMEs in code

- `AllInstitutionsQuery:23-24` — Filter handling for reported candidates (open/pending vs closed period)
- `AllInstitutionsQuery:95` — `computeReportedTotals` logic needs review for closed periods
- `InstitutionAggregationResult:37` — Replace `validPoints()` with switch when period state is implemented
- `AllInstitutionsQuery:65` — Sector is currently null in result

## Suggested implementation order

1. **AllInstitutionsQuery** — in progress, has tests
2. **PeriodQuery** — simplest next step, same aggregation without institution grouping
3. **AllPeriodsQuery** — wraps PeriodQuery aggregation with a terms bucket on year
4. **InstitutionQuery** — most complex, adds institution filter + unit breakdown
