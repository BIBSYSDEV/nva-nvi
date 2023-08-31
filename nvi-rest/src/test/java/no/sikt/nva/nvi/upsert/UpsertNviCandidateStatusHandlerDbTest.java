package no.sikt.nva.nvi.upsert;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Candidate.Builder;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.NviApprovalStatus;
import no.sikt.nva.nvi.rest.NviStatusRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class UpsertNviCandidateStatusHandlerDbTest extends LocalDynamoTest {

    private UpsertNviCandidateStatusHandler handler;
    private NviService nviService;
    private NviCandidateRepository nviCandidateRepository;
    private DynamoDbClient localDynamo;
    private Context context;
    private ByteArrayOutputStream output;
    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        nviService = new NviService(localDynamo);
        handler = new UpsertNviCandidateStatusHandler(nviService);
    }

    @Test
    void shouldUpdateApprovalStatus() throws IOException {
        URI institutionId = randomUri();
        var candidateWithIdentifier = nviCandidateRepository.save(createCandidate(institutionId));

        NviStatusRequest req = new NviStatusRequest(candidateWithIdentifier.identifier(), institutionId,
                                                                 NviApprovalStatus.APPROVED);
        InputStream request = createRequest(req);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        CandidateResponse bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance.approvalStatuses().get(0).status(), is(equalTo(Status.APPROVED)));
    }
    private InputStream createRequest(NviStatusRequest body) throws JsonProcessingException {
        URI customerId = randomUri();
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   //TODO CHANGE TO CORRECT ACCESS RIGHT
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private static Candidate createCandidate(URI institutionId) {
        return Candidate.builder()
                   .withPublicationId(randomUri())
                   .withApprovalStatuses(List.of(
                       new ApprovalStatus(institutionId,
                                          Status.PENDING,
                                          null, null)))
                   .withLevel(Level.LEVEL_ONE)
                   .withIsInternationalCollaboration(false)
                   .withPublicationBucketUri(randomUri())
                   .withPublicationDate(
                       new PublicationDate("2023", "01", "01"))
                   .withCreators(List.of(new Creator(randomUri(), List.of(institutionId))))
                   .withCreatorCount(1)
                   .withInstanceType("AcademicArticle")
                   .withIsApplicable(true)
                   .withNotes(List.of())
                   .build();
    }
}
