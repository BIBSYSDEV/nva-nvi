Feature: Transforming expanded publication to flat model
  Rule: Should be able to parse valid JSON without raising exceptions
    Scenario Template:
      Given a valid example document "<filename>"
      And an S3 URI to the document location
      When the document is extracted and transformed
      Then the ID of the transformed document ends with "<id>"
      Examples:
        | filename | id |
        | candidate.json | 01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d |
