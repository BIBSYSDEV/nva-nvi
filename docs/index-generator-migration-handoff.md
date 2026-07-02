# Index Document Generator Migration — Handoff

Working document to track progress, delete before opening the final PR.
Changes to this doc should be done in separate commits, not mixed with code changes.

Status as of 2026-07-02.
Branches: `np-51406-tests` (part 1, PR #781, awaiting review) and `np-51406-replace-doc-gen` (part 2, this work, stacked on part 1).
Ticket context: NP-51434 (regression tests), NP-51406 / NP-51402 / NP-51414 / NP-51432 (the underlying bugs), NP-51443 (batch failure reporting follow-up).

## Goal

Replace the legacy JSON-node index-document generator (which did live Cristin organization lookups) with a generator that builds the index document purely from the persisted `Candidate` plus a `PublicationDto`, and remove the old generator completely.
This is the foundation for fixing the reindex bugs that the regression tests in PR #781 document.

## Current state (what has been done)

Five commits sit on top of the PR #781 test commits (tip was `c69b67cd`):

| Commit     | Summary                                                                             |
| ---------- | ----------------------------------------------------------------------------------- |
| `96bc5fc2` | refactor: Introduce `IndexDocumentGenerator` interface (cherry-picked)              |
| `0ae8678a` | test: Port generator unit tests behind a handler-level swap point (cherry-picked)   |
| `abb52003` | feat: Add SPARQL/DTO-based index document mapper (cherry-picked)                    |
| `c5234d90` | refactor: Switch index document generation to the SPARQL/DTO mapper (cherry-picked) |
| `71bf2d83` | refactor: Remove legacy JsonNode index document generator (new work)                |

The first four are cherry-picks of these commits from `larsv/2026/05/11-new-index-doc-generator`:
`f0986f77`, `683733cc`, `bd19377e`, `b4553b9d`.
The branch's own fixture commit (`baa20c1a`, FakeUriRetriever + Cristin fixtures) was **not** cherry-picked because `np-51406-tests` already has equivalent fixtures (in different packages: our Cristin fixtures live in `nvi-commons/.../common/cristin`, the branch's were in `nvi-test/.../test/uriretriever`).

### New production architecture (all in `index-handlers/src/main/java/no/sikt/nva/nvi/index/`)

- `utils/IndexDocumentGenerator` — `@FunctionalInterface` with `NviCandidateIndexDocument generate()`.
- `utils/IndexDocumentGeneratorFactory` — `@FunctionalInterface` with `IndexDocumentGenerator forCandidate(Candidate)`.
- `utils/PublicationDtoIndexDocumentGeneratorFactory` — the only implementation. Loads a `PublicationDto` via `PublicationLoaderService.extractAndTransform(publicationBucketUri)` and hands it to `CandidateToIndexDocumentMapper`.
- `utils/CandidateToIndexDocumentMapper implements IndexDocumentGenerator` — top-level mapper; delegates to three sub-mappers and stitches the document together.
  - `utils/ContributorMapper` — maps `publicationDto.contributors()` to index contributors, matching each against the Candidate's NVI creators.
  - `utils/ApprovalMapper` — maps `candidate.approvals()` to `ApprovalView`s (labels, status, points, involvedOrganizations, sector, rboInstitution).
  - `utils/PublicationDetailsMapper` — maps title/abstract/date/channel/pages/language/handles.
- `IndexDocumentHandler` — default (production) constructor now wires `PublicationDtoIndexDocumentGeneratorFactory(new PublicationLoaderService(new S3StorageReader(EXPANDED_RESOURCES_BUCKET)), new Environment())`. Its constructor signature changed to take an `IndexDocumentGeneratorFactory` instead of an `S3StorageReader` + `UriRetriever`.
- `nvi-commons/.../common/client/model/Organization` gained `List<URI> flattenPartOfChain()` (walks `partOf`, nearest parent first, single-parent only).

The new path needs `implementation(project(':publication-service'))` on `index-handlers` (added by the cherry-pick) and `testImplementation(project(':publication-service'))` on `integration-tests` (added here, because `index-handlers` exposes `publication-service` only via `implementation`, which is not transitive).

### Deleted (legacy)

- `index-handlers/.../utils/NviCandidateIndexDocumentGenerator.java`
- `index-handlers/.../utils/JsonNodeIndexDocumentGeneratorFactory.java`
- `index-handlers/.../model/PersistedResource.java`
- `index-handlers/.../utils/NviCandidateIndexDocumentGeneratorTest.java` (legacy subclass of the shared base test)

### Test changes

- `index-handlers/.../IndexDocumentGeneratorTestBase` (abstract) + `CandidateToIndexDocumentMapperTest` (its only remaining subclass) own the document-**content** coverage for the new mapper. `IndexDocumentScenario` builds the `Candidate` + a mirrored `PublicationDto`.
- `IndexDocumentHandlerTest` was rewritten from ~935 lines to a focused **handler-mechanics** suite (persist to S3, emit SQS event, DLQ on failure, non-applicable skip, per-record batch isolation, reported candidate) wired through the new factory with a mocked `PublicationLoaderService`. Document-content assertions were dropped (now owned by the base/mapper tests).
- `getAnyNviCreatorIfPresent` (+ verified/unverified helpers) was relocated out of the deleted legacy generator into a new test helper `index-handlers/.../NviCreatorMatcher.java`, so `IndexDocumentTestUtils` (used by `IndexDocumentHandlerTest`, `UpdateIndexHandlerTest`, and several report/search tests) still compiles. Behaviour was preserved; it now uses `ExpandedResourceGenerator.extractId/extractName`.
- `ContributorMapper.buildNviContributor` display name: uses the Candidate's NVI-creator name when present, otherwise falls back to the Publication contributor name (NP-51414), so names stay populated for search.
- Cucumber: `integration-tests/.../cucumber/contexts/IndexingContext` was rewired to the new factory (reads the persisted publication from S3 via `PublicationLoaderService`); the obsolete `registerOrganization`/`FakeUriRetriever` stubbing and its 3 call sites in `IndexingSteps` were removed.

### Verification (all green as of this handoff)

```bash
./gradlew :index-handlers:check          # tests + 100% method/class JaCoCo + PMD + Spotless + Error Prone
./gradlew :integration-tests:clean :integration-tests:check
./gradlew :nvi-commons:test
```

Note: `:integration-tests:spotlessJava` can fail with `Could not read path '.../build/resources/test/features'` on a stale build dir; run `:integration-tests:clean` first.

### Currently-unused fixtures that are intentionally kept

After the cucumber rewire, these test doubles/fixtures have **no remaining references** (each is referenced only by its own file or by another of these fixtures):

- `nvi-test/.../test/uriretriever/FakeUriRetriever.java` and `FakeHttpResponse.java` — a reusable `UriRetriever` test double.
- `nvi-commons/.../common/cristin/CristinOrganizationFixtures.java` and `FakeCristinOrganization.java` — builders for fake Cristin `/cristin/organization` API responses (including `organizationWithNestedPartOf`).

They were introduced by PR #781 (commit `4726aece`) to fake the Cristin organization registry for the old, live-lookup generator. The new generator reads org data from the persisted publication, so nothing consumes them now.

**Do NOT delete them.** Keep them on purpose: faking `UriRetriever` responses in general, and Cristin organization API responses in particular, is a recurring need across the test suites, so these are expected to be reused by future tests. They are unused public test API, so they do not break the build. A likely future move is relocating them into `nva-commons` (shared test utilities) rather than keeping them here; do that rather than removing them.

## What was found

### The new generator is a partial fix, not a complete one

It sources NVI **points** from the Candidate (good), but the NVI **contributor list and their affiliations still come from the Publication** (`publicationDto.contributors()` in `ContributorMapper.mapContributors`). Consequences, confirmed by re-enabling the two NP-51406 cucumber scenarios (both fail, so they were left `@Disabled`):

- "Creator removed from the Publication is still indexed as an NVI creator" — the removed creator disappears from the index (index had 1 NVI creator, Candidate has 2).
- "Creator affiliation differs (moved A1→A2), index uses the Candidate" — the index reflects the moved affiliation from the Publication, not the Candidate.

### Review findings (adversarial, verified)

Draft-mapper limitations (deferred to the follow-up, several predate this work):

- `ContributorMapper.findMatchingUnverifiedCreator` matches purely by name. A homonymous, id-bearing, non-NVI contributor can be promoted to an NVI contributor. (Same behaviour as the old generator, so not a regression.)
- `PublicationDetailsMapper.findMatchingPublicationChannelDto` id-branch has no by-type fallback: if the Candidate's channel id has no match in the loaded publication (e.g. channel merged/superseded between evaluation and a reindex that did not re-evaluate), channel `name` and `printIssn` are silently dropped. This is a divergence from the legacy generator, which always resolved the name by channel type. This is the most likely real-world regression to watch.
- `Organization.flattenPartOfChain()` follows only the first parent at each level; an org with multiple `partOf` parents loses the alternate branches, which can under-populate an approval's `involvedOrganizations`.

Test/refactor observations (this work):

- The `IndexDocumentHandlerTest` happy-path assertions compare the persisted document to `CandidateToIndexDocumentMapper.generate()` run on the **same** stubbed `PublicationDto`, so the content comparison is tautological (it still verifies handler→generator wiring and the S3 gzip/JSON round-trip; mapper content is covered by the base/mapper tests).
- Dropping the legacy content tests and the multi-level org stubbing thins integration coverage of `flattenPartOfChain` / `involvedOrganizations`. The `@Disabled` NP-51406 scenarios will cover it once the mapper is completed.
- `IndexDocumentScenario` always sets each publication contributor's name equal to the NVI creator's name, so the name-mismatch/fallback branch is only exercised via cucumber, not a focused unit test.
- Four single-return-point convention violations (guard clauses) in the ported mapper (`ContributorMapper.findMatchingVerifiedCreator`/`findMatchingUnverifiedCreator`, `PublicationDetailsMapper.buildPages`/`findMatchingPublicationChannelDto`), and the handler's blank-message DLQ branch is untested. JaCoCo still passes because the gate is method/class-level, not branch-level.

## Agreed design: fallback chain for index data

Agreed with the developer on 2026-07-02, after the review above.
Every field in the index document is resolved through a strict priority chain:

1. **Candidate (DynamoDB)**: source of truth for everything NVI-specific.
   This covers creators (id, name, verification status, affiliation URIs), the full top-level organization trees (with nested `hasPart` and labels), points, approvals, and channel id/type/scientific value.
2. **Publication (S3 expanded document, parsed via SPARQL to `PublicationDto`)**: enrichment for anything the Candidate does not persist.
   Today that is non-NVI contributors, orcid, roles, and channel name/ISSN.
3. **Live URI lookup**: all ids (Organization, Publication, PublicationChannel, Contributor) are resolvable against our own proxy APIs.
   Use this only when a field expected from tiers 1-2 is missing, and log a warning every time this tier is used.
4. **Omit the field**: if the live lookup fails (e.g. 404 because the entity was deleted in the source), leave the field out of the index document and log a warning.

Design implications:

- A valid, truthful (if lean) index document must be producible from the Candidate alone.
  `PublicationDto` becomes optional enrichment, which folds NP-51432 (degraded/absent publication) into the core design instead of leaving it as a separate hardening step.
  The persisted `Candidate` model already supports this (verified 2026-07-02): `NviCreator` carries id/name/affiliations, and `PublicationDetails.topLevelOrganizations` carries the full org trees with labels, so affiliations, `partOf` chains, and approval labels can all be reconstructed without the Publication.
- `CandidateToIndexDocumentMapper` stays pure (no I/O).
  Model tier 3 as a small injected resolver interface (per-entity methods: channel name, organization labels, person name), wired in `PublicationDtoIndexDocumentGeneratorFactory`.
  Production gets an implementation backed by the authorized client; unit tests get a no-op.
- No client-side caching for tier 3: these lookups should be rare, and the proxy APIs handle their own caching.
- Historical organizations deleted in the sources (Publication and Cristin) are a known, accepted tier-4 case.
  Some fields will simply be empty, permanently.
- A tier-3 channel lookup returns the channel's *current* name, not its name at evaluation time.
  Accepted for search display.
- The kept-but-unused fixtures (`FakeUriRetriever`, the Cristin org fixtures, see above) are the intended test doubles for tier 3.

### Failure semantics

The dividing line is "would a retry help", not "is a field missing":

- **Permanent absence** (tier-3 404, entity deleted in the source): omit the field, log a warning, index the partial document.
  Do not dead-letter; a redrive can never heal it and the entry is pure noise.
- **Transient failure** (5xx, timeout, S3 read error, DynamoDB fetch failure): fail the record so it retries.
  Do not index a half-done document that a retry would have completed.
- Never do both (DLQ + index the partial doc): contradictory semantics, and redrive would just reproduce the same partial doc.
- Visibility into partial docs comes from a structured warning log (candidate identifier + omitted field), which supports a CloudWatch metric filter and alarm.
- The heal path for permanent omissions is backfill-then-reindex, not DLQ redrive.
- The handler currently swallows every per-record failure and manually sends to the DLQ immediately, so there is no automatic SQS retry today.
  Migrating `IndexDocumentHandler` to partial batch failure reporting (`ReportBatchItemFailures`, as already done in `ProcessBatchJobHandler`) is split out as [NP-51443](https://sikt.atlassian.net/browse/NP-51443).

### Creator names (NP-51414) plan

Evaluation does not persist creator names properly today (minor bug), so tier 1 is currently name-less for verified creators.
The plan is to fix the evaluation bug (in progress on a separate branch) **and** run a data migration to backfill missing names on historical candidates.
Until the backfill completes, the name fallback in `ContributorMapper.nviCreatorName` (Candidate first, then Publication) stays load-bearing in production.
Write tier-1-only tests against the fixed persistence, not the current buggy behavior.

## What remains to do

1. **Complete the NP-51406 fix (primary):** invert `ContributorMapper` so the NVI creator set and their affiliations are derived from the **Candidate** (`candidate.publicationDetails().verifiedCreators()` / `unverifiedCreators()`), using the `PublicationDto` only for enrichment (display name, orcid, role, non-NVI/searchable contributors).
   Reconstruct affiliation `partOf` chains and approval labels from the persisted top-level org trees when the Publication no longer has the creator or affiliation.
   Note that `ApprovalMapper.extractInvolvedOrganizations` derives from the mapped contributors, so it inherits the same fix; assert on `involvedOrganizations` too.
   Then re-enable and green the two `@Disabled` NP-51406 scenarios in `integration-tests/.../features/indexing/IndexingReportedCandidates.feature` (currently lines 39 and 46).
   Before relying on those scenarios as proof, strengthen the `IndexingSteps` affiliation assertion (line ~325) to compare per contributor (`Map<contributorId, Set<URI>>`) instead of one flattened `Set<URI>`, so contributor-to-affiliation cross-wiring is caught.
2. **Make `PublicationDto` optional (NP-51432, folded into the design):** the mapper should accept an absent/degraded publication and produce a Candidate-only document per the fallback chain, instead of throwing from `PublicationLoaderService` and dead-lettering.
   Apply the failure semantics above (transient S3 errors still fail the record).
   Then enable the four NP-51432 placeholders (feature lines 88, 90, 92, 94).
3. **Channel metadata (NP-51402):** resolve channel name/ISSN through the chain: Candidate (once persisted) => Publication by channel id => Publication by channel type => live lookup => omit with warning.
   The in-Publication by-type fallback is a one-line hardening that can land now: `findChannelDtoById(id).or(() -> findChannelDtoByType(channel.channelType()))` in `PublicationDetailsMapper` (guarded by a null check on `channelType`).
   Persisting name/ISSN on the Candidate at evaluation time is still worthwhile: it is truthful-at-evaluation, free at index time, and makes tier 3 a rarity.
   Then enable the NP-51402 scenarios (feature lines 53, 56), which are currently empty placeholders.
4. **Verified creator names (NP-51414):** fix the evaluation persistence bug plus the backfill migration (see the plan above); then the name-fallback in `ContributorMapper.nviCreatorName` can prefer the Candidate unconditionally.
   Enable the NP-51414 placeholder (feature line 36).
5. **Tier-3 resolver:** introduce the injected resolver interface for live lookups (see design above), with warning logs on use and on omission, wired in `PublicationDtoIndexDocumentGeneratorFactory` and stubbed with `FakeUriRetriever` in cucumber.
6. **Batch failure reporting ([NP-51443](https://sikt.atlassian.net/browse/NP-51443)):** migrate `IndexDocumentHandler` to `ReportBatchItemFailures` like `ProcessBatchJobHandler`; separate change.
7. **Optional hardening:** guard `findMatchingUnverifiedCreator` against id-bearing contributors; support multi-parent `flattenPartOfChain`; add a focused unit test for the channel-id-miss and name-mismatch branches; add back an integration assertion covering `involvedOrganizations`/partOf once a multi-level hierarchy is fed in.

## Key files

Production: `index-handlers/src/main/java/no/sikt/nva/nvi/index/IndexDocumentHandler.java`, `.../utils/CandidateToIndexDocumentMapper.java`, `.../utils/ContributorMapper.java`, `.../utils/ApprovalMapper.java`, `.../utils/PublicationDetailsMapper.java`, `.../utils/PublicationDtoIndexDocumentGeneratorFactory.java`, `nvi-commons/.../common/client/model/Organization.java`, `libs/publication-service/.../PublicationLoaderService.java`.

Tests: `index-handlers/.../IndexDocumentHandlerTest.java`, `.../IndexDocumentScenario.java`, `.../NviCreatorMatcher.java`, `.../utils/IndexDocumentGeneratorTestBase.java`, `.../utils/CandidateToIndexDocumentMapperTest.java`, `integration-tests/.../cucumber/contexts/IndexingContext.java`, `.../cucumber/steps/IndexingSteps.java`, `.../features/indexing/IndexingReportedCandidates.feature`.

## Suggested PR split

This work is a self-contained "swap the index generator + remove the legacy one" change (commits `96bc5fc2`..`71bf2d83`) that can be split into its own PR on top of the PR #781 test branch. It compiles and all tests pass; it does not yet close NP-51406, which is the follow-up.
