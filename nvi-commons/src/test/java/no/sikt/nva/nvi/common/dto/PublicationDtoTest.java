package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.model.InstanceType;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicationDtoTest {

  @ParameterizedTest
  @ValueSource(strings = {"AcademicChapter", "AcademicMonograph", "AcademicCommentary"})
  void shouldThrowValidationExceptionWhenPublicationIsAcademicChapterAndIsMissingIsbn(
      String publicationType) {
    var publication =
        createPublicationDto(Collections.emptyList(), InstanceType.parse(publicationType));

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for %s".formatted(publicationType),
        exception.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"AcademicChapter", "AcademicMonograph", "AcademicCommentary"})
  void
      shouldThrowValidationExceptionWhenPublicationIsAcademicChapterAndHasIsbnListWithNullValuesOnly(
          String publicationType) {
    var publication =
        createPublicationDto(Arrays.asList(null, null), InstanceType.parse(publicationType));

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for %s".formatted(publicationType),
        exception.getMessage());
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
