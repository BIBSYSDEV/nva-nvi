package no.sikt.nva.nvi.index.xlsx;

import static no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil.xlsxGeneratorFromInputStream;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.getOrderedValues;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import nva.commons.core.StringUtils;
import org.junit.jupiter.api.Test;

class FastExcelXlsxGeneratorTest {

  private static final List<String> HEADERS = getOrderedValues();

  @Test
  void shouldRoundTripHeadersAndStringData() {
    var data = List.of(randomRow(), randomRow(), randomRow());

    var actual = generateAndParseBack(data);

    assertThat(actual).isEqualTo(new FastExcelXlsxGenerator(HEADERS, data));
  }

  private static XlsxGenerator generateAndParseBack(List<List<String>> data) {
    var bytes =
        Base64.getDecoder()
            .decode(new FastExcelXlsxGenerator(HEADERS, data).toBase64EncodedString());
    return xlsxGeneratorFromInputStream(new ByteArrayInputStream(bytes));
  }

  private static List<String> randomRow() {
    var row = new ArrayList<>(Collections.nCopies(HEADERS.size(), StringUtils.EMPTY_STRING));
    row.set(InstitutionReportHeader.REPORTING_YEAR.getOrder(), randomString());
    row.set(InstitutionReportHeader.PUBLICATION_IDENTIFIER.getOrder(), randomString());
    row.set(
        InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL_POINTS.getOrder(),
        randomBigDecimal().toString());
    row.set(
        InstitutionReportHeader.INTERNATIONAL_COLLABORATION_FACTOR.getOrder(),
        randomBigDecimal().toString());
    row.set(InstitutionReportHeader.CREATOR_SHARE_COUNT.getOrder(), randomBigDecimal().toString());
    row.set(
        InstitutionReportHeader.POINTS_FOR_AFFILIATION.getOrder(), randomBigDecimal().toString());
    return row;
  }
}
