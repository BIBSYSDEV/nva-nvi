Feature: Extract and transform publication from S3 URI of JSON document
  Rule: Example documents can be parsed without errors
    Scenario Template:
      Given a valid example document "<filename>" in S3
      When a PublicationDto is created from the document
      Then the ID of the transformed document ends with "<identifier>"
      Examples:
        | filename       | identifier                                        |
        | candidate.json | 01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d |

  Rule: Valid Publication has expected fields from example document
    Scenario: PublicationDto has expected properties
      Given a valid example document "candidate.json" in S3
      When a PublicationDto is created from the document
      Then the PublicationDto has the title "Demo nvi candidate"
      And the PublicationDto has identifier "01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d"
      And the PublicationDto has publication year "2023"
      And the PublicationDto has status "PUBLISHED"
      And the PublicationDto has modified date "2023-06-05T10:47:02.627823Z"
      And the PublicationDto is not an international collaboration

    Scenario: PublicationDto has expected contributors
      Given a valid example document "candidate.json" in S3
      When a PublicationDto is created from the document
      Then the PublicationDto has 1 contributor(s)
      And the Contributor with ID "https://api.dev.nva.aws.unit.no/cristin/person/997998" has the expected properties
