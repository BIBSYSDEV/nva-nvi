# Test plan

Tests and relevant scenarios we need to cover:

## Tests

- Has correct count for each local status with disputes filtered out
- Has correct count for each global status
- Has correct reported totals
  - Period state doesn't matter for the test, just set up some reported candidates
- Has correct "valid points":
  - Pending period: undisputed total points
  - Open period: globally approved points
- Has institution ID and sector
- Has `NviPeriod`
- Has correct undisputedProcessedCount
- Has correct undisputedTotalCount

## Scenarios

Typical scenario with a mix of candidates:

- Two institutions
- Each institution has at least one candidate for each local approval status
- The main institution has candidates in unrelated periods

Scenario with closed period for reported candidates:

- Same as above?
- Need at least one approved unreported and one approved reported (to show that we filter out unreported)
