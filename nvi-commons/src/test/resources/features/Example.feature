Feature: Example

  Scenario: Counting stuff
    Given that we start counting at 4
    And the publication is named "Counting stuff"
    When we add 1
    And we add 2
    Then we should have counted to 7
    And the publication should have the title "Counting stuff"

  Scenario: Converting NviCreatorDto to DbCreatorType
    Given a contributor named "John Doe"
    And the contributor is affiliated with "NTNU"
    And the contributor is affiliated with "Sikt"
    And we have a DTO for this contributor
    When the DTO is converted to a DB entity
    Then the DB entity should have the name "John Doe"
    And the DB entity should have the same affiliations as the DTO
    And all the other stuff

  Scenario: Mixing contexts
    Given a contributor named "John Doe"
    And that we start counting at 2
    And the contributor is affiliated with "NTNU"
    And the contributor is affiliated with "Sikt"
    And we have a DTO for this contributor
    When the DTO is converted to a DB entity
    And we add 2
    Then the DB entity should have the name "John Doe"
    And all the other stuff
    And we should have counted to 4
