Feature: Approve Candidates
  Scenario: Create pending Approvals for new Candidate
    Given a publication that fulfills all criteria for NVI reporting
    And the publication is published in an open period
    When the publication is evaluated
    Then it should be evaluated as a Candidate

