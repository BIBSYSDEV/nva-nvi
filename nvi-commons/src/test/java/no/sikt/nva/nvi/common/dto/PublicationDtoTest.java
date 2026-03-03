package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.model.PublicationDetailsFixtures.randomPublicationDtoBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
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
    var publication = createPublicationDto(emptyList(), InstanceType.parse(instanceType));

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
        "AcademicChapter is not valid nvi candidate when it is part of %s"
            .formatted(InstanceType.parse(parentInstanceType)),
        exception.getMessage());
  }

  @Test
  void shouldReturnHandlesWhenHandlesArePresent() {
    var expectedHandles = List.of(randomUri(), randomUri());
    var publication = randomPublicationDtoBuilder().withHandles(expectedHandles).build();

    assertThat(publication.handles()).containsExactlyInAnyOrderElementsOf(expectedHandles);
  }

  @ParameterizedTest
  @MethodSource("emptyAndNullCollectionProvider")
  void shouldReturnEmptyCollectionWhenHandlesIsNullOrEmpty(Collection<URI> handles) {
    var publication = randomPublicationDtoBuilder().withHandles(handles).build();

    assertThat(publication.handles()).isEmpty();
  }

  private static Stream<Collection<URI>> emptyAndNullCollectionProvider() {
    return Stream.of(null, Collections.emptySet());
  }

  @Test
  void shouldAllowAnyNonReservedParentPublicationTypeWhenEvaluatingAcademicChapter() {
    var publication = createPublicationDto(ACADEMIC_CHAPTER, randomString());

    assertDoesNotThrow(publication::validate);
  }

  private PublicationDto createPublicationDto(
      InstanceType instanceType, String parentInstanceType) {
    return randomPublicationDtoBuilder()
        .withPublicationType(instanceType)
        .withParentPublicationType(InstanceType.parse(parentInstanceType))
        .build();
  }

  private PublicationDto createPublicationDto(
      Collection<String> isbnList, InstanceType instanceType) {
    return randomPublicationDtoBuilder()
        .withPublicationType(instanceType)
        .withIsbnList(isbnList)
        .build();
  }
}
