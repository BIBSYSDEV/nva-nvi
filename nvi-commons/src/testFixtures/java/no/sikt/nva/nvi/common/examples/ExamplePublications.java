package no.sikt.nva.nvi.common.examples;

import static no.sikt.nva.nvi.common.examples.ExampleContributors.ACADEMIC_CHAPTER_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_1_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_2;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_3;
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
  public static final String EXAMPLE_PUBLICATION_WITH_DUPLICATE_LABEL_PATH =
      "expandedPublications/validNviCandidateWithDuplicateLabel.json";
  public static final String EXAMPLE_ACADEMIC_CHAPTER_PATH =
      "expandedPublications/applicableAcademicChapter.json";
  public static final String EXAMPLE_INVALID_DRAFT = "expandedPublications/invalidDraft.json";
  public static final String EXAMPLE_WITH_DUPLICATE_DATE =
      "expandedPublications/nonCandidateWithDuplicateDate.json";
  public static final String EXAMPLE_WITH_TWO_TITLES =
      "expandedPublications/applicableAcademicChapterWithTwoTitles.json";
  public static final String EXAMPLE_WITH_NO_TITLE =
      "expandedPublications/validation/nonCandidateWithNoTitle.json";
  public static final String EXAMPLE_NO_PUBLICATION_TYPE =
      "expandedPublications/validation/no-publication-type.json";
  public static final String EMPTY_BODY = "expandedPublications/emptyBody.json";
  public static final String EXAMPLE_NO_CONTRIBUTORS =
      "expandedPublications/validation/nonCandidateWithNoContributors.json";
  public static final String EXAMPLE_NO_IDENTIFIER =
      "expandedPublications/validation/nonCandidateWithNoPublicationIdentifier.json";
  public static final String EXAMPLE_REPEATED_IDENTIFIER =
      "expandedPublications/validation/nonCandidateWithRepeatedPublicationIdentifier.json";
  public static final String EXAMPLE_NO_ABSTRACT =
      "expandedPublications/validation/candidateWithNoPublicationAbstract.json";
  public static final String EXAMPLE_REPEATED_ABSTRACT =
      "expandedPublications/validation/candidateWithRepeatedPublicationAbstract.json";
  public static final String EXAMPLE_NO_MODIFIED_DATE =
      "expandedPublications/validation/candidateWithNoModifiedDate.json";
  public static final String EXAMPLE_REPEATED_MODIFIED_DATE =
      "expandedPublications/validation/candidateWithRepeatedModifiedDate.json";
  public static final String MULTIPLE_LANGUAGES =
      "expandedPublications/validation/candidateWithMultipleLanguages.json";
  public static final String MISSING_PAGE_COUNT =
      "expandedPublications/validation/candidateWithMissingPageCount.json";
  public static final String REPEATED_PAGE_COUNT =
      "expandedPublications/validation/candidateWithRepeatedPageCount.json";
  public static final String MISSING_PUBLICATION_CHANNEL =
      "expandedPublications/validation/candidateWithMissingPublicationChannel.json";
  public static final String THREE_REPEATED_PUBLICATION_CHANNELS =
      "expandedPublications/validation/candidateWithThreePublicationChannels.json";
  public static final String MISSING_PUBLICATION_DATE =
      "expandedPublications/validation/candidateWithMissingPublicationDate.json";
  public static final String REPEATED_PUBLICATION_DATE =
      "expandedPublications/validation/candidateWithRepeatedPublicationDate.json";
  public static final String REPEATED_PUBLICATION_TYPE =
      "expandedPublications/validation/candidateWithRepeatedPublicationType.json";
  public static final String MISSING_TOP_LEVEL_ORGANIZATION =
      "expandedPublications/validation/candidateWithMissingTopLevelOrganization.json";
  public static final String MISSING_PUBLICATION_DATE_YEAR =
      "expandedPublications/validation/candidateWithMissingPublicationDateYear.json";
  public static final String REPEATED_PUBLICATION_DATE_YEAR =
      "expandedPublications/validation/candidateWithRepeatedPublicationDateYear.json";
  public static final String PUBLICATION_DATE_YEAR_TEMPLATE =
      "expandedPublications/validation/candidatePublicationDateYearTemplate.json_template";
  public static final String CONTRIBUTOR_NO_AFFILIATION =
      "expandedPublications/validation/contributorNoAffiliation.json";
  public static final String CONTRIBUTOR_NO_NAME =
      "expandedPublications/validation/contributorNoName.json";
  public static final String CONTRIBUTOR_REPEATED_NAME =
      "expandedPublications/validation/contributorRepeatedName.json";
  public static final String CONTRIBUTOR_ROLE_MISSING =
      "expandedPublications/validation/contributorRoleMissing.json";
  public static final String CONTRIBUTOR_ROLE_REPEATED =
      "expandedPublications/validation/contributorRoleRepeated.json";
  public static final String CONTRIBUTOR_VERIFICATION_STATUS_MISSING =
      "expandedPublications/validation/contributorVerificationStatusMissing.json";
  public static final String CONTRIBUTOR_VERIFICATION_STATUS_REPEATED =
      "expandedPublications/validation/contributorVerificationStatusRepeated.json";
  public static final String ORGANIZATION_UNKNOWN_COUNTRY =
      "expandedPublications/validation/organizationUnknownCountry.json";
  public static final String ORGANIZATION_REPEATED_COUNTRY =
      "expandedPublications/validation/organizationRepeatedCountry.json";
  public static final String ORGANIZATION_HAS_PART_NOT_URI =
      "expandedPublications/validation/organizationHasPartNotUri.json";
  public static final String ORGANIZATION_PART_OF_NOT_URI =
      "expandedPublications/validation/organizationPartOfNotUri.json";
  public static final String ORGANIZATION_LABEL_INVALID =
      "expandedPublications/validation/organizationLabelInvalid.json";
  public static final String PUBLICATION_CHANNEL_INVALID_TYPE =
      "expandedPublications/validation/publicationChannelInvalidType.json";
  public static final String PUBLICATION_CHANNEL_REPEATED =
      "expandedPublications/validation/publicationChannelTypeRepeated.json";
  public static final String PUBLICATION_CHANNEL_IDENTIFIER_MISSING =
      "expandedPublications/validation/publicationChannelIdentifierMissing.json";
  public static final String PUBLICATION_CHANNEL_IDENTIFIER_REPEATED =
      "expandedPublications/validation/publicationChannelIdentifierRepeated.json";
  public static final String PUBLICATION_CHANNEL_IDENTIFIER_NOT_STRING =
      "expandedPublications/validation/publicationChannelIdentifierNotString.json";
  public static final String PUBLICATION_CHANNEL_NAME_MISSING =
      "expandedPublications/validation/publicationChannelNameMissing.json";
  public static final String PUBLICATION_CHANNEL_NAME_REPEATED =
      "expandedPublications/validation/publicationChannelNameRepeated.json";
  public static final String PUBLICATION_CHANNEL_NAME_NOT_STRING =
      "expandedPublications/validation/publicationChannelNameNotString.json";
  public static final String PUBLICATION_CHANNEL_PISSN_MISSING =
      "expandedPublications/validation/publicationChannelPissnMissing.json";
  public static final String PUBLICATION_CHANNEL_PISSN_REPEATED =
      "expandedPublications/validation/publicationChannelPissnRepeated.json";
  public static final String PUBLICATION_CHANNEL_PISSN_NOT_STRING =
      "expandedPublications/validation/publicationChannelPissnNotString.json";
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
                  EXAMPLE_2_CONTRIBUTOR_5))
          .withTopLevelOrganizations(
              List.of(
                  TOP_LEVEL_ORGANIZATION_SIKT,
                  TOP_LEVEL_ORGANIZATION_NTNU,
                  EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .withIsbnList(List.of("isbn_ignored_for_validation"))
          .build();

  private static final String ISBN = "isbn_ignored_for_validation";
  private static final String ADDITIONAL_IDENTIFIER_ISBN =
      "additional_identifier_isbn_ignored_for_validation";
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
          .withIsbnList(List.of(ISBN, ADDITIONAL_IDENTIFIER_ISBN))
          .withParentPublicationType(InstanceType.NON_CANDIDATE)
          .build();
}
