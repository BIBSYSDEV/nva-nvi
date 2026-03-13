# Migration plan: replace JsonNode-based index document generation with Candidate + PublicationDto

## Context

The `NviCandidateIndexDocumentGenerator` builds `NviCandidateIndexDocument` by mixing data from the `Candidate` domain model and a raw `JsonNode` (expanded publication JSON fetched from S3). The JsonNode parsing is fragile, hard to maintain, and uses 30+ JSON pointers. Meanwhile:

- The **Candidate** domain model already contains most metadata (title, abstract, language, dates, pages, org labels, etc.)
- The **PublicationDto** (via `PublicationLoaderService`) provides the remaining data (all contributors with roles/ORCID, publication channel names/ISSNs, full organization hierarchy) through a clean, strongly-typed interface

Goal: incrementally replace the JsonNode-based generator with one that uses only `Candidate` + `PublicationDto`, using a strangler fig approach with small, independently deployable PRs.

## Data source mapping

| Index document field       | Current source                 | New source                                                          |
| -------------------------- | ------------------------------ | ------------------------------------------------------------------- |
| title, abstract, language  | JsonNode                       | Candidate.publicationDetails()                                      |
| instanceType               | JsonNode                       | Candidate.pointCalculation().instanceType()                         |
| publicationDate            | JsonNode                       | Candidate.publicationDetails().publicationDate()                    |
| pages (begin/end/count)    | JsonNode                       | Candidate.publicationDetails().pageCount()                          |
| org labels (for approvals) | JsonNode topLevelOrganizations | Candidate.publicationDetails().topLevelOrganizations().labels()     |
| channel name, printIssn    | JsonNode                       | PublicationDto.publicationChannels()                                |
| contributors (all roles)   | JsonNode contributor array     | PublicationDto.contributors()                                       |
| contributor ORCID          | JsonNode                       | PublicationDto.contributors() (needs ORCID added)                   |
| org hierarchy (partOf)     | UriRetriever HTTP + RDF parse  | PublicationDto contributor affiliations (Organization.partOf chain) |

## Prerequisites

### PR 1: Add ORCID to ContributorDto and SPARQL query

**Scope**: `nvi-commons` only

- Add `OPTIONAL { ?personId :orcid ?orcid }` to `publication_query.sparql` CONSTRUCT clause
- Add `String orcid` field to `ContributorDto`
- Update `publication_frame.json` if needed to include orcid mapping
- Add tests

**Files**:

- `nvi-commons/src/main/resources/publication_query.sparql`
- `nvi-commons/src/main/resources/publication_frame.json`
- `nvi-commons/src/main/java/no/sikt/nva/nvi/common/dto/ContributorDto.java`

**Risk**: Very low. Additive only. Existing consumers unaffected (new field is null if absent).

### PR 2: Include all contributor roles in SPARQL query (not just Creator)

**Scope**: `nvi-commons` only

- Remove or relax `FILTER (?roleType = :Creator)` in `publication_query.sparql`
- Ensure `ContributorRole` handles non-Creator roles
- Verify downstream consumers (EvaluatorService, PointService) correctly filter via `ContributorDto.isCreator()`
- Add tests for non-Creator contributors appearing in PublicationDto

**Files**:

- `nvi-commons/src/main/resources/publication_query.sparql`
- `nvi-commons/src/main/java/no/sikt/nva/nvi/common/dto/ContributorRole.java`

**Risk**: Low-medium. Must verify EvaluatorService and PointService filter correctly. These already use `ContributorDto.isCreator()`.

### PR 3: Add utility to flatten Organization.partOf chain to List\<URI\>

**Scope**: `nvi-commons` or `index-handlers`

- Static method that walks nested `Organization.partOf` chain, collecting URIs into a flat list
- This replaces the current HTTP-fetch + RDF-parse approach for building `NviOrganization.partOf`

```java
public static List<URI> flattenPartOfChain(Organization organization) {
    var result = new ArrayList<URI>();
    var current = organization.partOf();
    while (nonNull(current) && !current.isEmpty()) {
        var parent = current.getFirst();
        result.add(parent.id());
        current = parent.partOf();
    }
    return result;
}
```

**Risk**: Very low. Pure utility, no behavior change.

## Phase 1: Replace simple metadata using Candidate (no new deps)

### PR 4: Migrate publication metadata fields from JsonNode to Candidate

**Scope**: `index-handlers`, `NviCandidateIndexDocumentGenerator` only

Replace in `expandPublicationDetails()`:

- `extractMainTitle()` -> `candidate.publicationDetails().title()`
- `extractAbstract()` -> `candidate.publicationDetails().abstractText()`
- `extractLanguage()` -> `candidate.publicationDetails().language()`
- `extractPublicationDate()` from JsonNode -> `candidate.publicationDetails().publicationDate()` (already a `PublicationDate` with year/month/day)
- `extractInstanceType()` -> `candidate.pointCalculation().instanceType().getValue()`
- `extractPages()` -> map from `candidate.publicationDetails().pageCount()`

Remove the now-unused private methods: `extractMainTitle()`, `extractAbstract()`, `extractLanguage()`, `extractPublicationDate()`, `formatPublicationDate()`, `extractInstanceType()`, `extractPages()`, `extractPagesBeginIfPresent()`, `extractPagesEndIfPresent()`, `extractNumberOfPagesIfPresent()`.

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`

**Risk**: Low. Field-for-field replacements. Verify output equivalence with tests.

### PR 5: Migrate organization labels from JsonNode to Candidate

**Scope**: `index-handlers`, `NviCandidateIndexDocumentGenerator` only

Replace `extractLabels(Approval)` which parses `topLevelOrganizations` from expanded JSON:

- Find matching Organization in `candidate.publicationDetails().topLevelOrganizations()` by `id == approval.institutionId()`
- Return its `labels()` map

Remove: `extractLabels()`, `extractLabelsFromExpandedResource()`, `readAsStringMap()`, `isOrgWithInstitutionId()`.

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`

**Risk**: Low. Same data, different access path.

## Phase 2: Introduce PublicationDto for remaining fields

### PR 6: Wire PublicationLoaderService into IndexDocumentHandler

**Scope**: `index-handlers`

- Add `PublicationLoaderService` as constructor dependency of `IndexDocumentHandler`
- In `generateIndexDocumentWithConsumptionAttributes(Candidate)`, load `PublicationDto` via `publicationLoaderService.extractAndTransform(candidate.publicationDetails().publicationBucketUri())`
- Thread `PublicationDto` through to generator (add as constructor parameter alongside existing `JsonNode`)
- Generator accepts but doesn't use `PublicationDto` yet

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/IndexDocumentHandler.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/model/document/IndexDocumentWithConsumptionAttributes.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/model/document/NviCandidateIndexDocument.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`

**Risk**: Medium. Introduces SPARQL processing in the indexing path. Performance should be measured, but this is the same processing already proven in `EvaluatorService`.

### PR 7: Migrate publication channel name/ISSN to PublicationDto

**Scope**: `index-handlers`, `NviCandidateIndexDocumentGenerator`

In `buildPublicationChannel()`:

- Match `PublicationChannelDto` from `publicationDto.publicationChannels()` by channel id (from `candidate.getPublicationChannel().id()`)
- Use matched channel's `name()` and `printIssn()` instead of extracting from JsonNode

Remove: `extractName()`, `extractJournalName()`, `extractPublisherName()`, `extractSeriesName()`, `extractPrintIssn()`, `extractJournalPrintIssn()`, `extractSeriesPrintIssn()`.

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`

**Risk**: Low.

### PR 8: Migrate contributor extraction to PublicationDto + Candidate

**Scope**: `index-handlers`, `NviCandidateIndexDocumentGenerator`. **Largest PR.**

Replace `expandContributors()`:

1. Iterate `publicationDto.contributors()` (all contributors, thanks to PR 2)
2. For each `ContributorDto`, match against `candidate.publicationDetails().nviCreators()`:
   - Verified: match by `contributorDto.id()` == `nviCreator.id()`
   - Unverified: match by `contributorDto.name()` == `nviCreator.name()`
3. If NVI creator -> build `NviContributor`:
   - id, name from ContributorDto
   - orcid from ContributorDto (added in PR 1)
   - role from ContributorDto.role()
   - For NVI affiliations (those in `nviCreator.nviAffiliations()`): find matching `Organization` in `contributorDto.affiliations()`, flatten partOf chain (PR 3 utility) -> `NviOrganization`
   - For non-NVI affiliations: `Organization` with just id
4. If not NVI creator -> build `Contributor` with same basic fields

Remove: `expandContributors()`, `createContributor()`, `generateNviContributor()`, `generateContributor()`, `expandAffiliations()`, `expandAffiliation()`, `isNviAffiliation()`, `generateAffiliationWithPartOf()`, `listPropertyPartOfObjects()`, `getRawContentFromUriCached()`, `getRawContentFromUri()`, `extractId()`, `extractName()`, `extractRoleType()`, `getVerifiedNviCreatorIfPresent()`, `getUnverifiedNviCreatorIfPresent()`, `getAnyNviCreatorIfPresent()`.

Also removes: `UriRetriever`/`OrganizationRetriever` dependency, `temporaryCache`, Jena RDF model usage.

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`

**Risk**: Medium-high. Most complex mapping. Key risk: partOf chain from PublicationDto's Organization affiliations vs. HTTP-fetched RDF may differ. Write comparison tests.

## Phase 3: Cleanup

### PR 9: Remove JsonNode dependency entirely

- Remove `JsonNode expandedResource` from generator constructor
- Remove `PersistedResource` usage from `IndexDocumentHandler`
- Remove `StorageReader<URI>` dependency if only used for expanded resource (check if `PublicationLoaderService` has its own reader)
- Update `IndexDocumentWithConsumptionAttributes.from()` signature
- Update `NviCandidateIndexDocument.from()` signature
- Clean up unused imports (JsonPointers, GraphUtils, Jena, etc.)
- Remove `@SuppressWarnings("PMD.GodClass")` if class is now small
- Update/remove NP-48093 tech debt references

**Files**:

- `index-handlers/src/main/java/no/sikt/nva/nvi/index/IndexDocumentHandler.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/model/document/IndexDocumentWithConsumptionAttributes.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/model/document/NviCandidateIndexDocument.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/utils/NviCandidateIndexDocumentGenerator.java`
- `index-handlers/src/main/java/no/sikt/nva/nvi/index/model/PersistedResource.java` (delete if unused elsewhere)

**Risk**: Low. All field migrations already verified.

## Verification strategy

For PRs 4-8, each should include:

1. **Unit tests** comparing old vs new output for the same input data
2. **Deploy to dev** and run a batch re-index (`REFRESH_CANDIDATES`) to verify index documents match
3. For PR 8 specifically, consider a shadow-mode approach: generate with both old and new paths, log differences, return old result until verified

## Test approach

```bash
# Run tests for affected modules
./gradlew :index-handlers:test
./gradlew :nvi-commons:test

# After deploying each PR to dev:
# 1. Trigger batch re-index via StartBatchJobHandler with payload:
# { "jobType": "REFRESH_CANDIDATES", "filter": { "reportingYears": ["2024", "2025"] } }
# 2. Compare sample index documents before/after
```

## Key technical details

### Current call chain

```
IndexDocumentHandler
  -> CandidateService.getCandidateByIdentifier() [DynamoDB]
  -> StorageReader.read(publicationBucketUri) [S3 -> JsonNode]
  -> IndexDocumentWithConsumptionAttributes.from(candidate, persistedResource, uriRetriever)
    -> NviCandidateIndexDocumentGenerator(uriRetriever, expandedResource, candidate)
      -> generateDocument() [mixes Candidate + JsonNode + HTTP calls]
```

### Target call chain

```
IndexDocumentHandler
  -> CandidateService.getCandidateByIdentifier() [DynamoDB]
  -> PublicationLoaderService.extractAndTransform(publicationBucketUri) [S3 -> SPARQL -> PublicationDto]
  -> IndexDocumentWithConsumptionAttributes.from(candidate, publicationDto)
    -> NviCandidateIndexDocumentGenerator(candidate, publicationDto)
      -> generateDocument() [uses only Candidate + PublicationDto]
```

### Organization hierarchy: current vs new approach

**Current**: For each NVI creator affiliation URI, fetch organization via HTTP (`UriRetriever`), parse RDF model (`Jena`), walk `partOf` property chain to collect parent URIs into `NviOrganization.partOf: List<URI>`.

**New**: `PublicationDto.contributors().affiliations()` contains `Organization` objects with nested `partOf: List<Organization>` chain already populated. Flatten using utility method to produce the same `List<URI>`. No HTTP calls needed.

### ORCID gap

`ContributorDto` currently has no ORCID field. The SPARQL query (`publication_query.sparql`) does not extract ORCID. PR 1 adds both. The expanded JSON does contain ORCID at `/contributor/identity/orcid`.

### Non-NVI contributor gap

The SPARQL query currently filters to `Creator` role only (`FILTER (?roleType = :Creator)`). The index document includes ALL contributor roles. PR 2 removes this filter so `PublicationDto` includes all contributors.
