Feature: Evaluation of Publications as NVI Candidates

  Rule: Publications can become Candidates if the Period is not closed
    Scenario Outline: A new applicable Publication becomes a Candidate
      Given an applicable Publication
      And the reporting period for the Publication is "<period_state>"
      When the Publication is evaluated
      Then it becomes a Candidate

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |

  Rule: Unreported Candidates can be updated regardless of period state
    Scenario Outline: An applicable Publication remains a Candidate
      Given an unreported Candidate
      And the reporting period for the Publication is "<period_state>"
      When the Publication is evaluated
      Then the Candidate is updated

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

    Scenario Outline: A non-applicable Publication becomes a NonCandidate
      Given an unreported Candidate
      And the reporting period for the Publication is "<period_state>"
      When the Publication is updated to be non-applicable
      Then it becomes a NonCandidate

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

