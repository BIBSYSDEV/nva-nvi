package no.sikt.nva.nvi.common.examples;

import static no.sikt.nva.nvi.common.examples.ExampleContributors.ACADEMIC_CHAPTER_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_1_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_2;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_3;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_4;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_5;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.EXAMPLE_TOP_LEVEL_ORGANIZATION_3;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_NTNU;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_SIKT;
import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.JOURNAL_OF_TESTING;
import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.PUBLISHER_OF_TESTING;
import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.SERIES_OF_ANTHOLOGY;
import static no.sikt.nva.nvi.common.examples.ExamplePublicationChannels.SERIES_OF_TESTING;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.InstanceType;

/**
 * Example models for testing purposes, corresponding to the data in
 * /resources/expandedPublications/
 */
public class ExamplePublications {
  public static final String EXAMPLE_PUBLICATION_1_PATH =
      "expandedPublications/validNviCandidate1.json";
  public static final String EXAMPLE_PUBLICATION_2_PATH =
      "expandedPublications/validNviCandidate2.json";
  public static final String EXAMPLE_ACADEMIC_CHAPTER_PATH =
      "expandedPublications/applicableAcademicChapter.json";
  public static final String EXAMPLE_INVALID_DRAFT = "expandedPublications/invalidDraft.json";
  public static final String EXAMPLE_WITH_DUPLICATE_DATE =
      "expandedPublications/nonCandidateWithDuplicateDate.json";

  public static final PublicationDto EXAMPLE_PUBLICATION_1 =
      PublicationDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication/0195c6f1a431-6290c69b-5488-44ea-b20f-cef3464fb1b5"))
          .withIdentifier("0195c6f1a431-6290c69b-5488-44ea-b20f-cef3464fb1b5")
          .withTitle("Example NVI candidate #1")
          .withAbstract("Lorem ipsum")
          .withPageCount(new PageCountDto(null, null, null))
          .withPublicationDate(new PublicationDateDto("2025", null, null))
          .withStatus("PUBLISHED")
          .withPublicationType(InstanceType.ACADEMIC_ARTICLE)
          .withModifiedDate(Instant.parse("2025-03-24T06:59:56.170369925Z"))
          .withLanguage("http://lexvo.org/id/iso639-3/nob")
          .withPublicationChannels(List.of(JOURNAL_OF_TESTING))
          .withContributors(List.of(EXAMPLE_1_CONTRIBUTOR))
          .withIsApplicable(true)
          .withIsInternationalCollaboration(false)
          .withTopLevelOrganizations(
              List.of(TOP_LEVEL_ORGANIZATION_NTNU, TOP_LEVEL_ORGANIZATION_SIKT))
          .build();

  public static final PublicationDto EXAMPLE_PUBLICATION_2 =
      PublicationDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication/0195c6f37392-68057afa-9b9f-4e7a-8c9a-f5aef6b657be"))
          .withIdentifier("0195c6f37392-68057afa-9b9f-4e7a-8c9a-f5aef6b657be")
          .withTitle("Example NVI candidate #2")
          .withAbstract("Lorem ipsum dolor sit amet")
          .withPageCount(new PageCountDto(null, null, "42"))
          .withPublicationDate(new PublicationDateDto("2025", "3", "24"))
          .withStatus("PUBLISHED")
          .withPublicationType(InstanceType.ACADEMIC_MONOGRAPH)
          .withModifiedDate(Instant.parse("2025-03-24T08:23:24.859620342Z"))
          .withLanguage("http://lexvo.org/id/iso639-3/eng")
          .withPublicationChannels(List.of(PUBLISHER_OF_TESTING, SERIES_OF_TESTING))
          .withIsApplicable(true)
          .withIsInternationalCollaboration(true)
          .withContributors(
              List.of(
                  EXAMPLE_2_CONTRIBUTOR_1,
                  EXAMPLE_2_CONTRIBUTOR_2,
                  EXAMPLE_2_CONTRIBUTOR_3,
                  EXAMPLE_2_CONTRIBUTOR_4,
                  EXAMPLE_2_CONTRIBUTOR_5))
          .withTopLevelOrganizations(
              List.of(
                  TOP_LEVEL_ORGANIZATION_SIKT,
                  TOP_LEVEL_ORGANIZATION_NTNU,
                  EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .build();

  public static final PublicationDto EXAMPLE_ACADEMIC_CHAPTER =
      PublicationDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication/01961fbe4a94-6eb30f7c-836d-49ef-9acf-dc5d62fe9092"))
          .withIdentifier("01961fbe4a94-6eb30f7c-836d-49ef-9acf-dc5d62fe9092")
          .withTitle("2025-04-10 - Test av NVI")
          .withPageCount(new PageCountDto("474", "482", null))
          .withPublicationDate(new PublicationDateDto("2025", null, null))
          .withStatus("PUBLISHED")
          .withPublicationType(InstanceType.ACADEMIC_CHAPTER)
          .withModifiedDate(Instant.parse("2025-04-10T12:50:05.920945837Z"))
          .withPublicationChannels(List.of(SERIES_OF_ANTHOLOGY))
          .withIsApplicable(true)
          .withIsInternationalCollaboration(false)
          .withContributors(List.of(ACADEMIC_CHAPTER_CONTRIBUTOR_1))
          .withTopLevelOrganizations(List.of(TOP_LEVEL_ORGANIZATION_SIKT))
          .build();
}
