package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_APPLICATION_TYPE;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.index.xlsx.TwoDimensionalTable;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.problem.Problem;

public class FetchInstitutionReportHandlerTest {

    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchInstitutionReportHandler handler;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        handler = new FetchInstitutionReportHandler();
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(institutionId, CURRENT_YEAR, AccessRight.MANAGE_DOI).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnEmptyXlsxFile()
        throws IOException {
        handler.handleRequest(validRequest(MICROSOFT_EXCEL.toString()), output, CONTEXT);
        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actual = inputStreamToTwoDimensionalTable(new ByteArrayInputStream(decodedResponse));
        var expected = new TwoDimensionalTable(new ArrayList<>(), new ArrayList<>());
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    void shouldReturnMediaTypeMicrosoftExcelWhenRequested(String mediaType) throws IOException {
        var request = validRequest(mediaType);
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(mediaType));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    void shouldReturnBase64EncodedOutputStreamWhenContentTypeIsExcel(String mediaType) throws IOException {
        var request = validRequest(mediaType);
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getIsBase64Encoded());
    }

    @Test
    void shouldReturnMediaTypeMicrosoftExcelAsDefault() throws IOException {
        var request = validRequest(ANY_APPLICATION_TYPE.toString());
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(MICROSOFT_EXCEL.toString()));
    }

    private static List<List<String>> extractData(XSSFSheet sheet) {
        var data = new ArrayList<List<String>>();
        for (var rowCounter = 1; rowCounter <= sheet.getLastRowNum(); rowCounter++) {
            var row = sheet.getRow(rowCounter);
            data.add(extractRow(row));
        }
        return data;
    }

    private static List<String> extractHeaders(XSSFSheet sheet) {
        var headerRow = sheet.getRow(0);
        return extractRow(headerRow);
    }

    private static List<String> extractRow(XSSFRow headerRow) {
        var headers = new ArrayList<String>();
        for (var cellCounter = 0; cellCounter < headerRow.getLastCellNum(); cellCounter++) {
            headers.add(headerRow.getCell(cellCounter).getStringCellValue());
        }
        return headers;
    }

    private static InputStream validRequest(String mediaType) throws JsonProcessingException {
        var institutionId = randomUri();
        return createRequest(institutionId, CURRENT_YEAR, MANAGE_NVI_CANDIDATES)
                   .withHeaders(Map.of(ACCEPT, mediaType))
                   .build();
    }

    private static HandlerRequestBuilder<InputStream> createRequest(URI userTopLevelCristinInstitution,
                                                                    int year,
                                                                    AccessRight accessRight) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, accessRight)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(YEAR, String.valueOf(year)));
    }

    private TwoDimensionalTable inputStreamToTwoDimensionalTable(InputStream inputStream) {
        try (var workbook = new XSSFWorkbook(inputStream)) {
            var sheet = workbook.getSheetAt(0);
            var headers = extractHeaders(sheet);
            var data = extractData(sheet);
            return new TwoDimensionalTable(headers, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
