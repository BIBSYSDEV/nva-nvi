Feature: Indexing of reported NVI Candidates
  # Terminology
  #
  # NVI data: Reportable data that affects NVI points (creators that qualify, publication channel
  #   and level used for points, points per institution, approval status). Creator name is also NVI
  #   data and is meant to be frozen on the Candidate (see name note below).
  # Period: NVI reporting period, currently one calendar year, with a state (pending/open/closed).
  # Publication: External input. The (possibly updated) expanded Publication we read from S3.
  # Candidate: Persisted domain model in DynamoDB, derived from a Publication. Immutable once
  #   reported or once the Period closes.
  # Index document: Ephemeral JSON in S3/OpenSearch for search and reporting. Derived from the
  #   Candidate, enriched with data from the Publication.

  Background:
    Given an institution with departments A and B, and sections A1 and A2 under department A
    And a second institution
    And a Publication co-authored by a creator in section A1 and a creator in the second institution
    And a reported Candidate for the Publication

  Rule: The persisted Candidate is the source of truth for NVI data in the index document

    Scenario: Indexing produces a document with the same NVI data as the Candidate
      When the Candidate is indexed
      Then the index document has the same NVI points as the Candidate
      And the index document has the same channel level as the Candidate
      And the index document has the same channel ID as the Candidate
      And the index document has the same NVI affiliations as the Candidate
      And the index document has the same NVI creators as the Candidate
      And the index document has the same approval statuses as the Candidate
      And the index document has the same reporting status as the Candidate

    Scenario: Creator added to the Publication is not indexed as an NVI creator
      Given a creator is added to the Publication
      When the Candidate is indexed
      Then the index document has the same NVI data as the Candidate
      And the added creator is not indexed as an NVI creator

    @Disabled # FIXME: NP-51414 - Verified NVI creator names are not persisted
    Scenario: Creator name differs between Candidate and Publication, index uses the Candidate

    Scenario: Creator removed from the Publication is still indexed as an NVI creator
      Given the creator in section A1 is removed from the Publication
      When the Candidate is indexed
      Then the index document has the same NVI data as the Candidate
      And the creator in section A1 is still indexed as an NVI creator

    Scenario: Creator affiliation differs between Candidate and Publication, index uses the Candidate
      Given the creator in section A1 is moved to section A2 in the Publication
      When the Candidate is indexed
      Then the index document has the same NVI data as the Candidate
      And the creator is indexed as affiliated with section A1, not section A2

    @Disabled # FIXME: NP-51402 - Channel metadata isn't persisted
    Scenario: Channel name differs between Candidate and Publication, index uses the Candidate

    @Disabled # FIXME: NP-51402 - Channel metadata isn't persisted
    Scenario: Channel ISSN differs between Candidate and Publication, index uses the Candidate

    Scenario: Channel level differs between Candidate and Publication, index uses the Candidate
      Given the channel level in the Publication is changed from level 1 to level 2
      When the Candidate is indexed
      Then the index document has the same NVI data as the Candidate
      And the indexed channel is level 1, not level 2

  Rule: Index documents can be enriched with updated data if absent from the Candidate

    Scenario: All contributors are indexed as searchable, not only NVI creators
      Given a creator is added to the Publication
      When the Candidate is indexed
      Then all contributors are indexed as searchable, including non-NVI ones

    Scenario: Creator added to Publication is included as searchable field
      Given a creator is added to the Publication
      When the Candidate is indexed
      Then the added creator is indexed as a searchable contributor

    # FIXME NP-51402: channel name isn't persisted on the Candidate, so the index falls back to the Publication
    Scenario: Channel name is indexed for search
      When the Candidate is indexed
      Then the indexed channel has a name

    # FIXME NP-51414: verified creator names aren't persisted, so the index falls back to the Publication
    Scenario: Creator names are indexed for search
      When the Candidate is indexed
      Then the indexed creators have names

  Rule: Candidates can be indexed when the Publication is degraded or absent

    @Disabled # FIXME: NP-51432 - Indexing fails when the live Publication or org data is degraded or absent
    Scenario: Publication has no valid publication channel
    @Disabled # FIXME: NP-51432 - Indexing fails when the live Publication or org data is degraded or absent
    Scenario: Publication has no valid NVI organizations
    @Disabled # FIXME: NP-51432 - Indexing fails when the live Publication or org data is degraded or absent
    Scenario: Publication cannot be fetched from S3
    @Disabled # FIXME: NP-51432 - Indexing fails when the live Publication or org data is degraded or absent
    Scenario: Publication has been deleted
