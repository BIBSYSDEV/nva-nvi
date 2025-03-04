Feature: Example
  Scenario: Counting stuff
    Given that we start counting at 4
    And the publication is named "Counting stuff"
    When we add 1
    And we add 2
    Then we should have counted to 7
    And the publication should have the title "Counting stuff"
