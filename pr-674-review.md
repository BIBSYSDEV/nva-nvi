# PR #674 review checklist

[refactor: Use SPARQL parser to generate index documents](https://github.com/BIBSYSDEV/nva-nvi/pull/674)

## Behavioral changes to confirm

- [ ] **Label source shift.**
      `ApprovalMapper.extractLabels` reads from `candidate.publicationDetails().topLevelOrganizations()`,
      where the old code read from the expanded resource's `topLevelOrganizations` JSON.
      Confirm candidate-stored topLevelOrganizations always carry labels in production data,
      otherwise approvals will silently get empty labels.

- [ ] **Loose channel matching in `PublicationDetailsMapper.findMatchingPublicationChannelDto`.**
      If id is null, falls back to first channel of matching `channelType`.
      Could pick non-deterministically when a publication has multiple channels of the same type.
      Consider logging when the id-less fallback fires, or guarding so it only fires when there is exactly one channel of that type.

- [x] **`Organization.flattenPartOfChain()` only follows the first parent at each level.**
      `var parent = current.getFirst();` silently drops alternate parents.
      Add a one-line comment documenting the single-path assumption.
      *Resolved: added a Javadoc explaining the single-parent assumption on `Organization.flattenPartOfChain()`.*

- [ ] **NviContributor id semantics changed.**
      Old: `extractId(identity)` for both verified and unverified.
      New: only verified creators receive an id, unverified always get `null`.
      Verify against representative production documents that this is a cleanup rather than a regression.

- [x] **Test `shouldNotBuildIndexDocumentForNonApplicableCandidate` weakened.**
      Changed from `assertThrows(NoSuchKeyException.class, ...)` to `sqsClient.getSentMessages().isEmpty()`.
      Consider keeping both checks so we still verify the S3 bucket is untouched.
      *Resolved: re-added the `NoSuchKeyException` assertion alongside the message-queue check.*

## Minor cleanups

- [ ] `CandidateToIndexDocumentMapper` is `public` while sub-mappers are package-private.
      Only `IndexDocumentWithConsumptionAttributes.from(...)` instantiates it,
      so package-private would shrink the API surface.
      *Investigated: `IndexDocumentWithConsumptionAttributes` lives in `index.model.document`
      while the mapper lives in `index.utils`, so going package-private requires moving the
      entry point or introducing a package-private static factory in `utils`. Bigger move,
      keeping `public` for now.*

- [ ] `PublicationDetailsMapper.buildPublicationChannel` uses fully-qualified
      `no.sikt.nva.nvi.common.model.PublicationChannel` due to a name clash.
      Add an import alias or rename one type.

- [ ] `ContributorMapper.indexOrganizationWithId` uses fully-qualified
      `no.sikt.nva.nvi.index.model.document.Organization` for the same reason.
      Same fix.

- [ ] `buildNonNviContributor` and `extractRole` carry TODOs.
      File follow-up tickets and link them in the comments so they outlive the refactor.

- [x] `PublicationDetailsFixtures.buildContributorsFromCandidate` hardcodes
      `for (var i = 0; i < 10; i++)` adding 10 random non-NVI contributors.
      Extract a constant or accept it as a parameter.
      *Resolved: extracted `RANDOM_NON_NVI_CONTRIBUTORS = 10` constant.*

- [ ] Revisit `@SuppressWarnings("PMD.CouplingBetweenObjects")` on `IndexDocumentHandlerTest`.
      The mapper split likely makes this suppression unnecessary now.

## Test coverage gaps

- [x] Add explicit tests in `CandidateToIndexDocumentMapperTest` for
      `ApprovalMapper.extractSector`:
      `sector == UNKNOWN → null` and `sector == null → null`.
      The deleted `NviCandidateIndexDocumentGeneratorTest` had these and the replacement does not mirror them directly.
      *Resolved: added `shouldPopulateSectorOnApprovalWhenSectorIsKnown` (parameterized over the non-UNKNOWN values),
      `shouldNotPopulateSectorOnApprovalWhenSectorIsUnknown`, and `shouldNotPopulateSectorOnApprovalWhenSectorIsNull`.
      Extended `createCandidate` to take a `siktSector`.*
