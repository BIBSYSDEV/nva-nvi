package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.setupReportedCandidate;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.fetch.ReportStatusDto.StatusDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FetchReportStatusByPublicationIdHandlerTest extends LocalDynamoTest {

    private static final String CLOSED_YEAR = String.valueOf(CURRENT_YEAR - 1);
    private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";
    private Context context;
    private ByteArrayOutputStream output;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private FetchReportStatusByPublicationIdHandler handler;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        context = new FakeContext();
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    }

    @Test
    void shouldReturnReportedYearWhenPublicationIsReportedInClosedPeriod() throws IOException {
        var dao = setupReportedCandidate(candidateRepository, CLOSED_YEAR);
        var reportedCandidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);

        periodRepository = periodRepositoryReturningOpenedPeriod(Integer.parseInt(CLOSED_YEAR));
        handler = new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

        handler.handleRequest(createRequest(reportedCandidate.getPublicationId()), output, context);
        var actualResponseBody = GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
                                     .getBodyObject(ReportStatusDto.class);
        var expected = new ReportStatusDto(reportedCandidate.getPublicationId(), StatusDto.REPORTED,
                                           CLOSED_YEAR);
        assertEquals(expected, actualResponseBody);
    }

    private static InputStream createRequest(URI publicationId)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(PATH_PARAM_PUBLICATION_ID, publicationId.toString()))
                   .build();
    }
}
