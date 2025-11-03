Feature: Evaluation of Publications as NVI Candidates

  Rule: Publications can become Candidates if the Period is not closed
    Scenario Outline: A new applicable Publication becomes a Candidate
      Given an applicable Publication
      And the reporting period for the Publication is "<period_state>"
      When the Publication is evaluated
      Then the Publication is persisted as a Candidate

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |

  Rule: Unreported Candidates can be updated regardless of period state
    Scenario Outline: An applicable Publication remains a Candidate
      Given a Publication that has previously been evaluated as a Candidate
      And the Candidate is not reported
      And the reporting period for the Publication is "<period_state>"
      When the Publication is evaluated
      Then the Publication is persisted as a Candidate
      And the persisted data is updated

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

    Scenario Outline: A non-applicable Publication becomes a NonCandidate
      Given a Publication that has previously been evaluated as a Candidate
      And the Candidate is not reported
      And the reporting period for the Publication is "<period_state>"
      And the Publication type is changed so that the Publication is no longer applicable
      When the Publication is evaluated
      Then the Publication is persisted as a NonCandidate
      And the persisted data is updated

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

