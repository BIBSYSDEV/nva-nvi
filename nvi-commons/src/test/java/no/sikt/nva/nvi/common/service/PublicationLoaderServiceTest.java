package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.examples.ExamplePublications.EMPTY_BODY;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_INVALID_DRAFT;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_NO_PUBLICATION_TYPE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_DUPLICATE_DATE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_NO_TITLE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_TWO_TITLES;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.examples.ExamplePublications;
import no.sikt.nva.nvi.common.exceptions.ParsingException;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("PMD.GodClass")
class PublicationLoaderServiceTest {

  private static final String BUCKET_NAME = "testBucket";
  private S3Driver s3Driver;
  private PublicationLoaderService dataLoader;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new PublicationLoaderService(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentProvider")
  void shouldNotFailWhenParsingExampleDocument(String filename) {
    assertThatNoException().isThrownBy(() -> parseExampleDocument(filename));
  }

  @ParameterizedTest
  @MethodSource("applicableCandidateDocumentProvider")
  void shouldNotFailWhenValidatingExampleDocument(String filename) {
    var actual = parseExampleDocument(filename);
    assertThatNoException().isThrownBy(actual::validate);
  }

  @ParameterizedTest
  @MethodSource("applicableCandidateDocumentProvider")
  void shouldGetExpectedDataFromExampleDocuments(String filename, PublicationDto expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }

  @Test
  void shouldLogValidationReportWhenInputHasTwoTitles() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(ParsingException.class, () -> parseExampleDocument(EXAMPLE_WITH_TWO_TITLES));
    assertThat(logAppender.getMessages()).containsSequence("Publication title is repeated");
  }

  @Test
  void shouldLogValidationReportWhenInputHasNoTitle() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_WITH_NO_TITLE));
    assertThat(logAppender.getMessages()).containsSequence("Publication title is missing");
  }

  @ParameterizedTest
  @ValueSource(strings = {EMPTY_BODY, EXAMPLE_NO_PUBLICATION_TYPE})
  void shouldLogPublicationTypeMissingFromBody(String body) {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(ParsingException.class, () -> parseExampleDocument(body));
    assertThat(logAppender.getMessages()).containsSequence("Publication is missing or duplicated");
  }

  @Test
  void shouldLogUseOfNtriplesWhenNtriplesAreAvailable() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_WITH_NTRIPLES));
    assertThat(logAppender.getMessages()).containsSequence("Using N-Triples data");
  }

  /**
   * This test is unfortunately necessary because the rule for Publication-type-checking may result
   * in false positives / false negatives.
   */
  @Test
  void shouldNotLogMissingPublicationWhenPublicationIsNotMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_PUBLICATION_1_PATH));
    assertThat(logAppender.getMessages()).doesNotContain("Publication is missing or duplicated");
  }

  @Test
  void shouldLogPublicationWithNoContributors() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_CONTRIBUTORS));
    assertThat(logAppender.getMessages())
        .containsSequence(
            "Publication does not have at least one verified or unverified contributor");
  }

  @Test
  void shouldLogMissingPublicationIdentifier() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_IDENTIFIER));
    assertThat(logAppender.getMessages()).containsSequence("Publication identifier is missing");
  }

  @Test
  void shouldLogRepeatedPublicationIdentifier() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_IDENTIFIER));
    assertThat(logAppender.getMessages()).containsSequence("Publication identifier is repeated");
  }

  @Test
  void shouldLogMissingPublicationModifiedDate() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_MODIFIED_DATE));
    assertThat(logAppender.getMessages()).containsSequence("Publication modified date is missing");
  }

  @Test
  void shouldLogRepeatedPublicationModifiedDate() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_MODIFIED_DATE));
    assertThat(logAppender.getMessages()).containsSequence("Publication modified date is repeated");
  }

  @Test
  void shouldLogMissingPublicationAbstract() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_ABSTRACT));
    assertThat(logAppender.getMessages()).containsSequence("Publication abstract is missing");
  }

  @Test
  void shouldLogRepeatedPublicationAbstract() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_ABSTRACT));
    assertThat(logAppender.getMessages()).containsSequence("Publication abstract is repeated");
  }

  @Test
  void shouldNotLogUnverifiedContributorAsError() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_PUBLICATION_2_PATH));

    var messages = logAppender.getMessages();
    assertThat(messages)
        .doesNotContain("Should contain at least one verified or unverified contributor");
  }

  @Test
  void shouldLogRepeatedLanguage() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class, () -> parseExampleDocument(ExamplePublications.MULTIPLE_LANGUAGES));
    assertThat(logAppender.getMessages()).contains("Publication language is repeated");
  }

  @Test
  void shouldLogMissingPageCount() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PAGE_COUNT));
    assertThat(logAppender.getMessages()).contains("Publication page count is missing");
  }

  @Test
  void shouldLogRepeatedPageCount() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PAGE_COUNT));
    assertThat(logAppender.getMessages()).contains("Publication page count is repeated");
  }

  @Test
  void shouldLogMissingPublicationChannel() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_CHANNEL));
    assertThat(logAppender.getMessages()).contains("Publication channel is missing");
  }

  @Test
  void shouldLogWhenMoreThanThreePublicationChannelsArePresent() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.THREE_REPEATED_PUBLICATION_CHANNELS));
    assertThat(logAppender.getMessages()).contains("Publication channel is repeated");
  }

  @Test
  void shouldLogMissingPublicationDate() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_DATE));
    assertThat(logAppender.getMessages()).contains("Publication date is missing");
  }

  @Test
  void shouldLogRepeatedPublicationDate() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_DATE));
    assertThat(logAppender.getMessages()).contains("Publication date is repeated");
  }

  @Test
  void shouldLogRepeatedPublicationType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_TYPE));
    assertThat(logAppender.getMessages()).contains("Publication type is repeated");
  }

  @Test
  void shouldLogWhenTopLevelOrganizationIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.MISSING_TOP_LEVEL_ORGANIZATION));
    assertThat(logAppender.getMessages()).contains("Publication top-level organization is missing");
  }

  // This is an NVA data test
  @Test
  void shouldLogWhenPublicationDateYearIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_DATE_YEAR));
    assertThat(logAppender.getMessages()).contains("Publication date year is missing");
  }

  // This is an NVA data test
  @Test
  void shouldLogWhenPublicationDateYearIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_DATE_YEAR));
    assertThat(logAppender.getMessages()).contains("Publication date year is repeated");
  }

  // This is an NVA data test
  @ParameterizedTest
  @ValueSource(strings = {"\"ABCD\"", "\"123\"", "2010", "\"\""})
  void shouldLogWhenPublicationDateYearIsNotCorrectType(String value) {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    var filename = ExamplePublications.PUBLICATION_DATE_YEAR_TEMPLATE;
    var document = stringFromResources(Path.of(filename)).formatted(value);
    if ("\"ABCD\"".equals(value) || "\"123\"".equals(value)) {
      assertDoesNotThrow(() -> parseExampleDocument(filename, document));
    } else {
      assertThrows(ParsingException.class, () -> parseExampleDocument(filename, document));
    }
    assertThat(logAppender.getMessages())
        .contains("Publication date year is does not match expectations for type and structure");
  }

  @Test
  void shouldLogWhenContributorDoesNotHaveAtLeastOneAffiliation() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_NO_AFFILIATION));
    assertThat(logAppender.getMessages()).contains("Contributor affiliation is missing");
  }

  // This slips through, polluting NVI
  @Test
  void shouldLogWhenContributorNameIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_NO_NAME));
    assertThat(logAppender.getMessages()).contains("Contributor name is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorNameIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_REPEATED_NAME));
    assertThat(logAppender.getMessages()).contains("Contributor name is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorRoleIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_ROLE_MISSING));
    assertThat(logAppender.getMessages()).contains("Contributor role is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorRoleIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_ROLE_REPEATED));
    assertThat(logAppender.getMessages()).contains("Contributor role is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorVerificationStatusIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_VERIFICATION_STATUS_MISSING));
    assertThat(logAppender.getMessages()).contains("Contributor role is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorVerificationStatusIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_VERIFICATION_STATUS_REPEATED));
    assertThat(logAppender.getMessages()).contains("Contributor role is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationIsNotFromKnownCountry() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_UNKNOWN_COUNTRY));
    assertThat(logAppender.getMessages()).contains("Organization country is not equal to 'NO'");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationHasRepeatedCountry() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_REPEATED_COUNTRY));
    assertThat(logAppender.getMessages()).contains("Organization country is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationHasPartIsNotKnownType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_HAS_PART_NOT_URI));
    assertThat(logAppender.getMessages()).contains("Organization hasPart is not IRI");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationPartOfIsNotKnownType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_PART_OOF_NOT_URI));
    assertThat(logAppender.getMessages()).contains("Organization partOf is not IRI");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationLabelIsPresentAndIsNotKnownLanguage() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.ORGANIZATION_LABEL_INVALID));
    assertThat(logAppender.getMessages())
        .contains("Organization label is not a unique language literal from [en, nb, nn]");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationLabelIsPresentAndIsNotUniqueLanguage() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_LABEL_NOT_UNIQUE));
    assertThat(logAppender.getMessages())
        .contains("Organization label is not a unique language literal from [en, nb, nn]");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIsNotKnownType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_INVALID_TYPE));
    assertThat(logAppender.getMessages())
        .contains("Publication channel is not an IRI of type (Journal, Publisher, Series)");
  }

  // NVI test!
  @Test
  void shouldLogWhenPublicationChannelIsMissingInNviData() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_INVALID_TYPE));
    assertThat(logAppender.getMessages()).contains("Publication channel is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelTypeHasMultipleValues() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_REPEATED));
    assertThat(logAppender.getMessages()).contains("Publication channel type has multiple values");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_MISSING));
    assertThat(logAppender.getMessages()).contains("Publication channel identifier is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_REPEATED));
    assertThat(logAppender.getMessages())
        .contains("Publication channel identifier has multiple values");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsNotStringDataType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_NOT_STRING));
    assertThat(logAppender.getMessages())
        .contains("Publication channel identifier is not a string");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_MISSING));
    assertThat(logAppender.getMessages()).contains("Publication channel name is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_REPEATED));
    assertThat(logAppender.getMessages()).contains("Publication channel name is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsNotStringDataType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_NOT_STRING));
    assertThat(logAppender.getMessages()).contains("Publication channel name is not a string");
  }

  // In this case, it is an NVA test
  // This is a warning, not a violation since not everything is printed
  @Test
  void shouldLogWarningWhenPublicationChannelPrintIssnIsMissing() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_MISSING));
    assertThat(logAppender.getMessages()).contains("Publication channel print ISSN is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelPrintIssnIsRepeated() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_REPEATED));
    assertThat(logAppender.getMessages()).contains("Publication channel print ISSN is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelPrintIssnIsNotStringDataType() {
    var logAppender = LogUtils.getTestingAppender(PublicationLoaderService.class);
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_NOT_STRING));
    assertThat(logAppender.getMessages())
        .contains("Publication channel print ISSN is not a string");
  }

  private PublicationDto parseExampleDocument(String filename) {
    var document = stringFromResources(Path.of(filename));
    var publicationBucketUri = addToS3(filename, document);
    return dataLoader.extractAndTransform(publicationBucketUri);
  }

  private PublicationDto parseExampleDocument(String filename, String document) {
    var publicationBucketUri = addToS3(filename, document);
    return dataLoader.extractAndTransform(publicationBucketUri);
  }

  private URI addToS3(String identifier, String document) {
    try {
      return s3Driver.insertFile(UnixPath.of(identifier), document);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  private static Stream<Arguments> exampleDocumentProvider() {
    return Stream.of(
        argumentSet("Minimal example", EXAMPLE_PUBLICATION_1_PATH),
        argumentSet("Full example", EXAMPLE_PUBLICATION_2_PATH),
        argumentSet("Academic chapter", EXAMPLE_ACADEMIC_CHAPTER_PATH),
        argumentSet("Invalid draft", EXAMPLE_INVALID_DRAFT),
        argumentSet("NonCandidate with duplicate publication dates", EXAMPLE_WITH_DUPLICATE_DATE));
  }

  private static Stream<Arguments> applicableCandidateDocumentProvider() {
    return Stream.of(
        argumentSet("Minimal example", EXAMPLE_PUBLICATION_1_PATH, EXAMPLE_PUBLICATION_1),
        argumentSet("Full example", EXAMPLE_PUBLICATION_2_PATH, EXAMPLE_PUBLICATION_2),
        argumentSet("Academic chapter", EXAMPLE_ACADEMIC_CHAPTER_PATH, EXAMPLE_ACADEMIC_CHAPTER));
  }
}
