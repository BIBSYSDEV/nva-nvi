package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomIsbn10;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.model.InstanceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationDtoTest {

  public static Stream<Arguments> isbnRequiringTypeProvider() {
    return Stream.of(Arguments.of("AcademicChapter", "AcademicMonograph", "AcademicCommentary"));
  }

  public static Stream<Arguments> invalidNestedInstanceTypeProvider() {
    return Stream.of(Arguments.of("AcademicMonograph", "AcademicCommentary"));
  }

  @ParameterizedTest
  @MethodSource("isbnRequiringTypeProvider")
  void shouldThrowValidationExceptionWhenPublicationHasInstanceTypeAndIsMissingIsbn(
      String instanceType) {
    var publication =
        createPublicationDto(Collections.emptyList(), InstanceType.parse(instanceType));

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for %s".formatted(instanceType),
        exception.getMessage());
  }

  @ParameterizedTest
  @MethodSource("isbnRequiringTypeProvider")
  void
      shouldThrowValidationExceptionWhenPublicationHasInstanceTypeTypeAndHasIsbnListWithNullValuesOnly(
          String instanceType) {
    var publication =
        createPublicationDto(Arrays.asList(null, null), InstanceType.parse(instanceType));

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for %s".formatted(instanceType),
        exception.getMessage());
  }

  @ParameterizedTest
  @MethodSource("invalidNestedInstanceTypeProvider")
  void shouldThrowValidationExceptionWhenAcademicChapterHasNestedInstanceType(
      String parentInstanceType) {
    var publication = createPublicationDto(ACADEMIC_CHAPTER, parentInstanceType);

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "AcademicChapter is not valid nvi candidate when it is published in %s"
            .formatted(parentInstanceType),
        exception.getMessage());
  }

  @Test
  void shouldAllowAnyNonReservedParentPublicationTypeWhenEvaluatingAcademicChapter() {
    var publication = createPublicationDto(ACADEMIC_CHAPTER, randomString());

    assertDoesNotThrow(publication::validate);
  }

  private PublicationDto createPublicationDto(
      InstanceType instanceType, String parentInstanceType) {
    return PublicationDto.builder()
        .withId(randomUri())
        .withStatus(randomString())
        .withPublicationDate(new PublicationDateDto(randomYear(), null, null))
        .withPublicationType(instanceType)
        .withParentPublicationType(parentInstanceType)
        .withPublicationChannels(Collections.emptyList())
        .withTopLevelOrganizations(Collections.emptyList())
        .withContributors(Collections.emptyList())
        .withIsbnList(List.of(randomIsbn10()))
        .build();
  }

  private PublicationDto createPublicationDto(List<String> isbnList, InstanceType instanceType) {
    return PublicationDto.builder()
        .withId(randomUri())
        .withStatus(randomString())
        .withPublicationDate(new PublicationDateDto(randomYear(), null, null))
        .withPublicationType(instanceType)
        .withPublicationChannels(Collections.emptyList())
        .withTopLevelOrganizations(Collections.emptyList())
        .withContributors(Collections.emptyList())
        .withIsbnList(isbnList)
        .build();
  }
}
