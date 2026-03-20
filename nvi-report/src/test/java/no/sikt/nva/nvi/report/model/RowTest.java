package no.sikt.nva.nvi.report.model;

import static no.sikt.nva.nvi.report.model.RowFixtures.completeReportRow;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import no.sikt.nva.nvi.report.model.institutionreport.ReportHeader;
import no.sikt.nva.nvi.report.model.institutionreport.ReportRowBuilder;
import org.junit.jupiter.api.Test;

class RowTest {

  @Test
  void shouldThrowIllegalArgumentExceptionWhenBuildingRowWithDuplicatedHeader() {
    var executable =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ReportRowBuilder()
                    .withYear("2024")
                    .withYear("2024")
                    .withFacultyNumber("1")
                    .withFacultyNumber("2")
                    .build());
    assertEquals("Duplicate headers found in a row: ARSTALL, AVDNR", executable.getMessage());
  }

  @Test
  void shouldContainCellForEachReportHeader() {
    var row = completeReportRow(randomString());
    var headers = row.headers();
    var allHeaders = Arrays.stream(ReportHeader.values()).map(Enum::name).toList();

    assertThat(headers).containsExactlyInAnyOrderElementsOf(allHeaders);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenReportRowIsMissingHeaders() {
    var executable =
        assertThrows(
            IllegalStateException.class, () -> new ReportRowBuilder().withYear("2024").build());
    assertThat(executable.getMessage()).startsWith("Missing headers: ");
  }
}
