package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PublicationDtoTest {

  @Test
  void shouldThrowValidationExceptionWhenPublicationIsAcademicChapterAndIsMissingIsbn() {
    var publication = createPublicationDto(Collections.emptyList());

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for AcademicChapter", exception.getMessage());
  }

  @Test
  void
      shouldThrowValidationExceptionWhenPublicationIsAcademicChapterAndHasIsbnListWithNullValuesOnly() {
    var publication = createPublicationDto(Arrays.asList(null, null));

    Executable executable = publication::validate;
    var exception = assertThrows(ValidationException.class, executable);

    assertEquals(
        "Required field 'isbnList' must not be empty for AcademicChapter", exception.getMessage());
  }

  private PublicationDto createPublicationDto(List<String> isbnList) {
    return PublicationDto.builder()
        .withId(randomUri())
        .withStatus(randomString())
        .withPublicationDate(new PublicationDateDto(randomYear(), null, null))
        .withPublicationType(ACADEMIC_CHAPTER)
        .withPublicationChannels(Collections.emptyList())
        .withTopLevelOrganizations(Collections.emptyList())
        .withContributors(Collections.emptyList())
        .withIsbnList(isbnList)
        .build();
  }
}
