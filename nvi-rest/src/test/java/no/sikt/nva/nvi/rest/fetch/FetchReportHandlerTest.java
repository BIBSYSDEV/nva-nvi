package no.sikt.nva.nvi.rest.fetch;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_APPLICATION_TYPE;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATE;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.rest.model.ReportRow;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchReportHandlerTest extends LocalDynamoTest {

    private static final String INSTITUTION_ID = "institutionId";
    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
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
        periodRepository = periodRepositoryReturningOpenedPeriod(TestUtils.CURRENT_YEAR);
        handler = new FetchReportHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()),
                                                candidateRepository, periodRepository).orElseThrow();
        var year = Year.now().getValue();
        var institutionId = randomUri();
        var request = createRequest(candidate.getIdentifier(), institutionId, institutionId, year,
                                    "SomeAccessRight").build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnForbiddenWhenUserDoesNotBelongToSameInstitutionAsRequestedInstitution()
        throws IOException {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()),
                                                candidateRepository, periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), randomUri(), randomUri(), CURRENT_YEAR,
                                    MANAGE_NVI_CANDIDATE.name()).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnNotFoundIfInstitutionDoesntExist() {

    }

    @Test
    void shouldReturnEmptyXlsxFileWhenNotDataExists()
        throws IOException {
        var institutionId = randomUri();
        var request = createRequest(UUID.randomUUID(), institutionId, institutionId, CURRENT_YEAR,
                                    MANAGE_NVI_CANDIDATE.toString()).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);

        assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
        List<List<String>> data = readFromExcel(response.getBody());

        assertThat(response.getBody(), is(notNullValue()));
        assertThat(data.get(0).size(), is(ReportRow.class.getRecordComponents().length));
    }

    @Test
    void shouldReturnMediaTypeMicrosoftExcelWhenRequested() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(UUID.randomUUID(), institutionId, institutionId, CURRENT_YEAR,
                                    MANAGE_NVI_CANDIDATE.toString())
                          .withHeaders(Map.of(ACCEPT, MICROSOFT_EXCEL.toString()))
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(MICROSOFT_EXCEL.toString()));
    }

    @Test
    void shouldReturnMediaTypeMicrosoftExcelAsDefault() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(UUID.randomUUID(), institutionId, institutionId, CURRENT_YEAR,
                                    MANAGE_NVI_CANDIDATE.toString())
                          .withHeaders(Map.of(ACCEPT, ANY_APPLICATION_TYPE.toString()))
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(MICROSOFT_EXCEL.toString()));
    }

    private static HandlerRequestBuilder<InputStream> createRequest(UUID candidateIdentifier,
                                                                    URI userTopLevelCristinInstitution,
                                                                    URI institutionId, int year, String accessRight) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withAccessRights(customerId, accessRight)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidateIdentifier.toString(),
                                              INSTITUTION_ID,
                                              URLEncoder.encode(institutionId.toString(), StandardCharsets.UTF_8),
                                              YEAR, String.valueOf(year)));
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
