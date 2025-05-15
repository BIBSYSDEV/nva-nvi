Feature: Evaluation of Publications as NVI Candidates
  Rule: An applicable Publication in an open Period is a Candidate
    Scenario: A new Publication is evaluated
      Given an applicable Publication
      And the reporting period for the Publication is "OPEN"
      When the Publication is evaluated
      Then the Publication is a Candidate

  Rule: An applicable Candidate re-evaluated in a closed Period is a Candidate
    Background:
      Given a Publication that has previously been evaluated as a Candidate

    Scenario: An applicable Publication remains a Candidate
      Given the reporting period for the Publication is "CLOSED"
      When the Publication is evaluated
      Then the Publication is a Candidate

    Scenario: A non-applicable Publication becomes a NonCandidate
      Given the reporting period for the Publication is "CLOSED"
      And the Publication type is changed so that the Publication is no longer applicable
      When the Publication is evaluated
      Then the Publication is a NonCandidate
