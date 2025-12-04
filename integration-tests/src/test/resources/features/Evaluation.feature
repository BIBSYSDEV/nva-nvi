Feature: Evaluation of Publications as NVI Candidates

  Rule: Reported Candidates are never updated

    Background:
      Given an applicable Publication published "this" year
      And a reported Candidate for the Publication exists

    Scenario Outline: Reported Candidate is not updated
      Given the reporting period for "this" year is "<period_state>"
      When the Publication title is changed
      Then the Candidate is not updated

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

    Scenario Outline: Reported Candidate is not demoted
      Given the reporting period for "this" year is "<period_state>"
      When the Publication is updated to be non-applicable
      Then the Candidate is applicable

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

  Rule: Publications can become Candidates in pending or open periods

    Background:
      Given an applicable Publication published "this" year

    Scenario Outline: A new Publication becomes a Candidate
      Given the reporting period for "this" year is "<period_state>"
      When the Publication is evaluated
      Then it becomes a Candidate

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |

    Scenario: A new Publication does not become a Candidate in a closed period
      Given the reporting period for "this" year is "CLOSED"
      When the Publication is evaluated
      Then it does not become a Candidate

  Rule: Candidates can be updated in pending or open periods

    Background:
      Given an applicable Publication published "this" year
      And an unreported Candidate for the Publication exists

    Scenario Outline: Candidate is updated when Publication changes
      Given the reporting period for "this" year is "<period_state>"
      When the Publication title is changed
      Then the Candidate is updated

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |

    Scenario: Candidate in closed period is not updated
      Given the reporting period for "this" year is "CLOSED"
      When the Publication title is changed
      Then the Candidate is not updated

  Rule: Unreported Candidates can be demoted in any period

    Background:
      Given an applicable Publication published "this" year
      And an unreported Candidate for the Publication exists

    Scenario Outline: Candidate becomes NonCandidate when Publication is no longer applicable
      Given the reporting period for "this" year is "<period_state>"
      When the Publication is updated to be non-applicable
      Then it becomes a NonCandidate

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

  Rule: Year transitions follow the target period's rules

    Background:
      Given an applicable Publication published "this" year
      And an unreported Candidate for the Publication exists

    Scenario Outline: Candidate is moved to pending period
      Given the reporting period for "this" year is "<period_state>"
      And the reporting period for "next" year is "PENDING"
      When the Publication date is changed to "next" year
      Then the Candidate is updated
      And the reporting period for the Candidate is "next" year

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

    Scenario Outline: Candidate is moved to open period
      Given the reporting period for "this" year is "<period_state>"
      And the reporting period for "next" year is "OPEN"
      When the Publication date is changed to "next" year
      Then the Candidate is updated
      And the reporting period for the Candidate is "next" year

      Examples:
        | period_state |
        | OPEN         |
        | PENDING      |
        | CLOSED       |

    Scenario: Candidate is demoted when moved to closed period
      Given the reporting period for "this" year is "OPEN"
      And the reporting period for "last" year is "CLOSED"
      When the Publication date is changed to "last" year
      Then it becomes a NonCandidate
