@Disabled
Feature: Indexing of reported NVI Candidates (PROPOSAL - for side-by-side review)
  # This whole file is @Disabled on purpose: it is a restructuring proposal to compare against
  # IndexingReportedCandidates.feature. Drop the feature-level @Disabled (and implement steps)
  # scenario by scenario once the titles are agreed.
  #
  # Glossary
  # NVI data: Reportable data that affects NVI points (creators that qualify, publication channel
  #   and level used for points, points per institution, approval status). Creator name is also NVI
  #   data and is meant to be frozen on the Candidate (see name note below).
  # Period: NVI reporting period, currently one calendar year, with a state (pending/open/closed).
  # Publication: External input. The (possibly updated) expanded Publication we read from S3.
  # Candidate: Persisted domain model in DynamoDB, derived from a Publication. Immutable once
  #   reported or once the Period closes.
  # Index document: Ephemeral JSON in S3/OpenSearch for search and reporting. Derived from the
  #   Candidate, enriched with data from the Publication.
  #
  # Two invariants for a reported Candidate's index document:
  #   I1  NVI data always equals the Candidate, regardless of the live Publication        -> Rule A
  #   I2  Searchable / non-NVI data is enriched from the Publication, best-effort          -> Rule B
  # Robustness when the Publication is degraded or absent                                 -> Rule C
  #
  # Frozen-but-not-yet-persisted NVI data: a creator's name (NP-51414) and a publication channel's
  # name/ISSN/ISBN (NP-51402) are NVI data meant to be frozen on the Candidate, but are not currently
  # persisted there (only the channel id and level are persisted). For now the index enriches them
  # from the Publication; once persisted, the index should prefer the Candidate when the two differ.
  # So each has a desired "Candidate wins" scenario in Rule A (disabled until its bug is fixed) and a
  # temporary "fall back to the Publication" scenario in Rule B reflecting current behaviour.

  Rule: NVI data in the index document equals the Candidate, regardless of the live Publication

    # Corollary of this rule: if NVI data always equals the Candidate, a second pass cannot change it.
    # Kept as one end-to-end guard rather than one scenario per facet.
    # TODO: Is this necessary as a separate scenario?
    @Disabled # FIXME: NP-51406
    Scenario: Reindexing after a Publication change does not alter the indexed NVI data
      Given the Candidate has been indexed
      And the creator in section A1 is moved to section A2 in the Publication
      When the Candidate is indexed
      Then the indexed NVI affiliations are unchanged
      And the indexed NVI points are unchanged
      And the indexed NVI creators are unchanged


