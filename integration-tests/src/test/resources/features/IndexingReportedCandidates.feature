Feature: Indexing of reported NVI Candidates
  # NVI data: Reportable data that affects NVI points, e.g. creators that qualify for points, publication channel used for points, points given per institution etc
  # Period: NVI reporting period, currently one calendar year, with a state (pending/open/closed)
  # Publication: External data source. The (possibly updated) expanded Publication we use as input data and read from S3
  # Candidate: Persisted data. Our domain model, persisted to DynamoDB and derived from a Publication
  # Index document: Ephemeral data. JSON representation of the Candidate persisted to S3/OpenSearch for searching and reporting
  # Publication maps to Candidate, Candidate is immutable once reported or once the Period closes
  # Candidate maps to index document, enriched with updated data as needed
  # TODO: Clarify separation between immutable and mutable data. Split in separate features?
  # Complication: All contributors must be included in the index document to make them searchable, but DB only has those who received NVI points. Means these must come from the updated publication (for now at least).

  Background:
    Given an institution with departments A and B
    And sections A1 and A2 belonging to department A
    And a Publication whose creator is affiliated with section A1
    And a reported Candidate for the Publication

  Rule: NVI data in the index document matches the persisted Candidate

    Scenario: Index document matches reported Candidate
      When the Candidate is indexed
      Then the indexed NVI points match the Candidate
      And the indexed NVI affiliations match the Candidate
      And the indexed NVI creators match the Candidate

    Scenario: Creator removed from Publication is included as NVI creator
    Scenario: Creator added to Publication is not included as NVI creator
    Scenario: Creator added to Publication is included as searchable field

    @Disabled # FIXME: NP-51406 - Reported NVI numbers can silently change when a candidate is reindexed
    Scenario: Creator affiliation changes, but index document shows original affiliation

    Scenario: Channel name changes, but the index document shows the original channel

  # TODO: Rephrase, but how?
  Rule: Non-NVI data missing from the persisted Candidate can be read from the Publication
  Rule: Index documents can be enriched with non-NVI data that isn't in the database

    @Disabled # FIXME: NP-51414 - Verified NVI creator names are not persisted on the Candidate
    Scenario: Publication and Candidate has different names for creator, index uses name from Candidate

    @Disabled # FIXME: NP-51414 - Verified NVI creator names are not persisted on the Candidate
    Scenario: Candidate is missing creator name, index uses name from Publication

    Scenario: Candidate is missing channel name, index uses name from Publication



    Rule: Reindexing a reported Candidate in a closed period does not change the indexed data
    @Disabled # FIXME: NP-51406 - Reported NVI numbers can silently change when a candidate is reindexed
    Scenario: Reindexing keeps the NVI affiliations after the source affiliation changes
      Given the Candidate has been indexed
      When the creator is moved to the second department in the source publication
      And the Candidate is indexed
      Then the indexed NVI affiliations are unchanged
      And the indexed NVI affiliations match the Candidate

    Scenario: Reindexing keeps the NVI points after the source affiliation changes
      Given the Candidate has been indexed
      When the creator is moved to the second department in the source publication
      And the Candidate is indexed
      Then the indexed NVI points are unchanged
      And the indexed NVI points match the Candidate

    Scenario: Reindexing keeps the NVI creators after the source affiliation changes
      Given the Candidate has been indexed
      When the creator is moved to the second department in the source publication
      And the Candidate is indexed
      Then the indexed NVI creators are unchanged
      And the indexed NVI creators match the Candidate


  @Disabled # FIXME: Add Jira ticket, this is a known bug/gap
  Rule: Candidates can be indexed without any data from the updated Publication
      Scenario: Publication and Candidate have different publication channels, index uses channel from Candidate

    Scenario: Updated Publication has no valid publication channel

    Scenario: Updated Publication has no valid NVI organizations

    Scenario: Publication is deleted