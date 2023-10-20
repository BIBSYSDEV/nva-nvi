package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.rest.create.NviNoteRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchReportHandlerTest extends LocalDynamoTest {

    private static final Context CONTEXT = mock(Context.class);
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private ByteArrayOutputStream output;
    private FetchReportHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        handler = new FetchReportHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnUnauthenticatedWhenNotLoggedInWithValidUser() throws IOException {
        var identifier = randomUri();
        var customerId = randomUri();
        var year = Year.now().getValue();
        var input = createInputWithoutAccessRights(customerId, identifier, year).build();
        handler.handleRequest(input, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnNotFoundIfInstitutionDoesntExist() {

    }

    @Test
    void shouldReturnEmptyXlsxFileWhenNotDataExists()
        throws IOException {
        var input = createInput(randomUri(), randomUri(), Year.now().getValue())
                        .build();
        handler.handleRequest(input, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);

        assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
        List<List<String>> data = readFromExcel(response.getBody());

        assertThat(response.getBody(), is(notNullValue()));
        assertThat(data.get(0).size(), is(ReportRow.class.getRecordComponents().length));
    }

    @Test
    void shouldContentNegotiate() throws IOException {
        var input = createInput(randomUri(), randomUri(), Year.now().getValue())
                        .withHeaders(Map.of(HttpHeaders.ACCEPT, MediaType.MICROSOFT_EXCEL.toString()))
                        .build();
        handler.handleRequest(input, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE), is(MediaType.MICROSOFT_EXCEL.toString()));
    }

    @Test
    void shouldContentNegotiateMore() throws IOException {
        var input = createInput(randomUri(), randomUri(), Year.now().getValue())
                        .withHeaders(Map.of(HttpHeaders.ACCEPT, MediaType.ANY_APPLICATION_TYPE.toString()))
                        .build();
        handler.handleRequest(input, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE), is(MediaType.MICROSOFT_EXCEL.toString()));
    }

    private static HandlerRequestBuilder<NviNoteRequest> createInput(URI customerId, URI identifier, int year) {
        return createInputWithoutAccessRights(customerId, identifier, year)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name());
    }

    private static HandlerRequestBuilder<NviNoteRequest> createInputWithoutAccessRights(URI customerId,
                                                                                        URI identifier,
                                                                                        int year) {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("institutionIdentifier", identifier.toString(),
                                              "year", String.valueOf(year)))
                   .withUserName(randomString());
    }

    private static ArrayList<List<String>> getLists(XSSFSheet sheet) {
        var data = new ArrayList<List<String>>();
        for (Row row : sheet) {
            var currentRow = new ArrayList<String>();
            for (Cell cell : row) {
                currentRow.add(getCellValue(cell));
            }
            data.add(currentRow);
        }
        return data;
    }

    private static String getCellValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf(cell.getNumericCellValue());
        } else {
            return cell.getStringCellValue();
        }
    }

    private List<List<String>> readFromExcel(String content) throws IOException {
        byte[] decode = Base64.getDecoder().decode(content);
        Path tmp = Files.createTempFile("tmp", ".xlsx");
        Files.write(tmp, decode);
        try (var workbook = new XSSFWorkbook(tmp.toFile())) {
            return getLists(workbook.getSheetAt(0));
        } catch (InvalidFormatException e) {
            throw new RuntimeException(e);
        }
    }
}
