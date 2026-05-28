package no.sikt.nva.nvi.publication;

import static no.sikt.nva.nvi.common.examples.ExamplePublications.EMPTY_BODY;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_INVALID_DRAFT;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_NO_PUBLICATION_TYPE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_WITH_DUPLICATE_LABEL_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_DUPLICATE_DATE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_NO_TITLE;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_WITH_TWO_TITLES;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import no.sikt.nva.nvi.common.model.InstanceType;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PublicationLoaderServiceTest {

  private static final String BUCKET_NAME = "testBucket";
  private S3Driver s3Driver;
  private PublicationLoaderService dataLoader;
  private LogRecorder logRecorder;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new PublicationLoaderService(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
    logRecorder = LogRecorder.forClass(PublicationLoaderService.class);
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
    assertThrows(ParsingException.class, () -> parseExampleDocument(EXAMPLE_WITH_TWO_TITLES));
    assertThat(logRecorder.asString()).containsSequence("Publication title is repeated");
  }

  @Test
  void shouldLogValidationReportWhenInputHasNoTitle() {
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_WITH_NO_TITLE));
    assertThat(logRecorder.asString()).containsSequence("Publication title is missing");
  }

  @ParameterizedTest
  @ValueSource(strings = {EMPTY_BODY, EXAMPLE_NO_PUBLICATION_TYPE})
  void shouldLogPublicationTypeMissingFromBody(String body) {
    assertThrows(ParsingException.class, () -> parseExampleDocument(body));
    assertThat(logRecorder.asString()).containsSequence("Publication is missing or duplicated");
  }

  /**
   * This test is unfortunately necessary because the rule for Publication-type-checking may result
   * in false positives / false negatives.
   */
  @Test
  void shouldNotLogMissingPublicationWhenPublicationIsNotMissing() {
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_PUBLICATION_1_PATH));
    assertThat(logRecorder.asString()).doesNotContain("Publication is missing or duplicated");
  }

  @Test
  void shouldLogPublicationWithNoContributors() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_CONTRIBUTORS));
    assertThat(logRecorder.asString())
        .containsSequence(
            "Publication does not have at least one verified or unverified contributor");
  }

  @Test
  void shouldLogMissingPublicationIdentifier() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_IDENTIFIER));
    assertThat(logRecorder.asString()).containsSequence("Publication identifier is missing");
  }

  @Test
  void shouldLogRepeatedPublicationIdentifier() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_IDENTIFIER));
    assertThat(logRecorder.asString()).containsSequence("Publication identifier is repeated");
  }

  @Test
  void shouldLogMissingPublicationModifiedDate() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_MODIFIED_DATE));
    assertThat(logRecorder.asString()).containsSequence("Publication modified date is missing");
  }

  @Test
  void shouldLogRepeatedPublicationModifiedDate() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_MODIFIED_DATE));
    assertThat(logRecorder.asString()).containsSequence("Publication modified date is repeated");
  }

  @Test
  void shouldLogMissingPublicationAbstract() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.EXAMPLE_NO_ABSTRACT));
    assertThat(logRecorder.asString()).containsSequence("Publication abstract is missing");
  }

  @Test
  void shouldLogRepeatedPublicationAbstract() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.EXAMPLE_REPEATED_ABSTRACT));
    assertThat(logRecorder.asString()).containsSequence("Publication abstract is repeated");
  }

  @Test
  void shouldNotLogUnverifiedContributorAsError() {
    assertDoesNotThrow(() -> parseExampleDocument(EXAMPLE_PUBLICATION_2_PATH));

    assertThat(logRecorder.asString())
        .doesNotContain("Should contain at least one verified or unverified contributor");
  }

  @Test
  void shouldLogRepeatedLanguage() {
    assertThrows(
        ParsingException.class, () -> parseExampleDocument(ExamplePublications.MULTIPLE_LANGUAGES));
    assertThat(logRecorder.asString()).contains("Publication language is repeated");
  }

  @Test
  void shouldLogMissingPageCount() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PAGE_COUNT));
    assertThat(logRecorder.asString()).contains("Publication page count is missing");
  }

  @Test
  void shouldLogRepeatedPageCount() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PAGE_COUNT));
    assertThat(logRecorder.asString()).contains("Publication page count is repeated");
  }

  @Test
  void shouldLogMissingPublicationChannel() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_CHANNEL));
    assertThat(logRecorder.asString()).contains("Publication channel is missing");
  }

  @Test
  void shouldLogWhenMoreThanThreePublicationChannelsArePresent() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.THREE_REPEATED_PUBLICATION_CHANNELS));
    assertThat(logRecorder.asString()).contains("Publication channel is repeated");
  }

  @Test
  void shouldLogMissingPublicationDate() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_DATE));
    assertThat(logRecorder.asString()).contains("Publication date is missing");
  }

  @Test
  void shouldLogRepeatedPublicationDate() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_DATE));
    assertThat(logRecorder.asString()).contains("Publication date is repeated");
  }

  @Test
  void shouldLogRepeatedPublicationType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_TYPE));
    assertThat(logRecorder.asString()).contains("Publication type is repeated");
  }

  @Test
  void shouldLogWhenTopLevelOrganizationIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.MISSING_TOP_LEVEL_ORGANIZATION));
    assertThat(logRecorder.asString()).contains("Publication top-level organization is missing");
  }

  // This is an NVA data test
  @Test
  void shouldLogWhenPublicationDateYearIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.MISSING_PUBLICATION_DATE_YEAR));
    assertThat(logRecorder.asString()).contains("Publication date year is missing");
  }

  // This is an NVA data test
  @Test
  void shouldLogWhenPublicationDateYearIsRepeated() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.REPEATED_PUBLICATION_DATE_YEAR));
    assertThat(logRecorder.asString()).contains("Publication date year is repeated");
  }

  // This is an NVA data test
  @ParameterizedTest
  @ValueSource(strings = {"\"ABCD\"", "\"123\"", "2010", "\"\""})
  void shouldLogWhenPublicationDateYearIsNotCorrectType(String value) {
    var filename = ExamplePublications.PUBLICATION_DATE_YEAR_TEMPLATE;
    var document = stringFromResources(Path.of(filename)).formatted(value);
    if ("\"ABCD\"".equals(value) || "\"123\"".equals(value)) {
      assertDoesNotThrow(() -> parseExampleDocument(filename, document));
    } else {
      assertThrows(ParsingException.class, () -> parseExampleDocument(filename, document));
    }
    assertThat(logRecorder.asString())
        .contains("Publication date year is does not match expectations for type and structure");
  }

  @Test
  void shouldLogWhenContributorDoesNotHaveAtLeastOneAffiliation() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_NO_AFFILIATION));
    assertThat(logRecorder.asString()).contains("Contributor affiliation is missing");
  }

  // This slips through, polluting NVI
  @Test
  void shouldLogWhenContributorNameIsMissing() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_NO_NAME));
    assertThat(logRecorder.asString()).contains("Contributor name is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorNameIsRepeated() {
    logRecorder.clear();
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_REPEATED_NAME));
    assertThat(logRecorder.asString()).contains("Contributor name is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorRoleIsMissing() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_ROLE_MISSING));
    assertThat(logRecorder.asString()).contains("Contributor role is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorRoleIsRepeated() {
    logRecorder.clear();
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_ROLE_REPEATED));
    assertThat(logRecorder.asString()).contains("Contributor role is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorVerificationStatusIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_VERIFICATION_STATUS_MISSING));
    assertThat(logRecorder.asString()).contains("Contributor verification status is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenContributorVerificationStatusIsRepeated() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.CONTRIBUTOR_VERIFICATION_STATUS_REPEATED));
    assertThat(logRecorder.asString()).contains("Contributor verification status is repeated");
  }

  @Test
  void shouldLogWhenNoOrganizationHasNorwegianCountryCode() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_UNKNOWN_COUNTRY));
    assertThat(logRecorder.asString()).contains("No organization with country 'NO' found");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationHasRepeatedCountry() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_REPEATED_COUNTRY));
    assertThat(logRecorder.asString()).contains("Organization country is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationHasPartIsNotKnownType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_HAS_PART_NOT_URI));
    assertThat(logRecorder.asString()).contains("Organization hasPart is not IRI");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationPartOfIsNotKnownType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.ORGANIZATION_PART_OF_NOT_URI));
    assertThat(logRecorder.asString()).contains("Organization partOf is not IRI");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenOrganizationLabelIsPresentAndIsNotKnownLanguage() {
    assertDoesNotThrow(() -> parseExampleDocument(ExamplePublications.ORGANIZATION_LABEL_INVALID));
    assertThat(logRecorder.asString())
        .contains("Organization label is not a unique language literal from [en, nb, nn]");
  }

  // NVI test!
  @Test
  void shouldLogWhenPublicationChannelIsMissingInNviData() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_INVALID_TYPE));
    assertThat(logRecorder.asString()).contains("Publication channel is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelTypeHasMultipleValues() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_REPEATED));
    assertThat(logRecorder.asString()).contains("Publication channel type has multiple values");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_MISSING));
    assertThat(logRecorder.asString()).contains("Publication channel identifier is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsRepeated() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_REPEATED));
    assertThat(logRecorder.asString())
        .contains("Publication channel identifier has multiple values");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelIdentifierIsNotStringDataType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_IDENTIFIER_NOT_STRING));
    assertThat(logRecorder.asString()).contains("Publication channel identifier is not a string");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_MISSING));
    assertThat(logRecorder.asString()).contains("Publication channel name is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsRepeated() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_REPEATED));
    assertThat(logRecorder.asString()).contains("Publication channel name is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelNameIsNotStringDataType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_NAME_NOT_STRING));
    assertThat(logRecorder.asString()).contains("Publication channel name is not a string");
  }

  // In this case, it is an NVA test
  // This is a warning, not a violation since not everything is printed
  @Test
  void shouldLogWarningWhenPublicationChannelPrintIssnIsMissing() {
    assertDoesNotThrow(
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_MISSING));
    assertThat(logRecorder.asString()).contains("Publication channel print ISSN is missing");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelPrintIssnIsRepeated() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_REPEATED));
    assertThat(logRecorder.asString()).contains("Publication channel print ISSN is repeated");
  }

  // In this case, it is an NVA test
  @Test
  void shouldLogWhenPublicationChannelPrintIssnIsNotStringDataType() {
    assertThrows(
        ParsingException.class,
        () -> parseExampleDocument(ExamplePublications.PUBLICATION_CHANNEL_PISSN_NOT_STRING));
    assertThat(logRecorder.asString()).contains("Publication channel print ISSN is not a string");
  }

  @Test
  void shouldLoadNestedPublicationInstanceTypeAsParentPublicationType() {
    var publicationDto = parseExampleDocument(EXAMPLE_ACADEMIC_CHAPTER_PATH);
    assertEquals(InstanceType.NON_CANDIDATE, publicationDto.parentPublicationType());
  }

  @Test
  void shouldLoadNestedPublicationInstanceTypeWhenNviReportableType() {
    var publicationDto =
        parseExampleDocument("expandedPublications/nonCandidateAcademicChapter.json");
    assertEquals(InstanceType.ACADEMIC_MONOGRAPH, publicationDto.parentPublicationType());
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
        argumentSet(
            "Duplicate label example",
            EXAMPLE_PUBLICATION_WITH_DUPLICATE_LABEL_PATH,
            EXAMPLE_PUBLICATION_1),
        argumentSet("Academic chapter", EXAMPLE_ACADEMIC_CHAPTER_PATH, EXAMPLE_ACADEMIC_CHAPTER));
  }
}
