Feature: Reporting period status
  Rule: A period is open or closed based on the reporting date
    Scenario: A period is closed if the reporting date is in the past
      Given an open period for year "2020"
      When the period for year "2020" is updated with a reporting date in the past
      Then the period for "2020" should be closed

    Scenario: A period is open if the reporting date is in the future
      Given a closed period for year "2020"
      When the period for year "2020" is updated with a reporting date in the future
      Then the period for "2020" should not be closed

