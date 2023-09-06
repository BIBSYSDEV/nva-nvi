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
import java.util.Map;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
import no.sikt.nva.nvi.common.model.business.DbCreator;
import no.sikt.nva.nvi.common.model.business.DbLevel;
import no.sikt.nva.nvi.common.model.business.DbPublicationDate;
import no.sikt.nva.nvi.common.model.business.DbStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.NviApprovalStatus;
import no.sikt.nva.nvi.rest.NviStatusRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class UpdateNviCandidateStatusHandlerDbTest extends LocalDynamoTest {

    private UpdateNviCandidateStatusHandler handler;
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
        handler = new UpdateNviCandidateStatusHandler(nviService);
    }

    @ParameterizedTest
    @EnumSource(NviApprovalStatus.class)
    void shouldUpdateApprovalStatus(NviApprovalStatus status) throws IOException {
        var institutionId = randomUri();
        var candidate = createCandidate(institutionId);
        var candidateWithIdentifier = nviCandidateRepository.create(candidate,
                                                                    List.of(DbApprovalStatus.builder()
                                                                                .institutionId(institutionId)
                                                                                .status(DbStatus.PENDING)
                                                                                .build()));

        var req = new NviStatusRequest(candidateWithIdentifier.identifier(), institutionId, status);
        var request = createRequest(req);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance.approvalStatuses().get(0).status().getValue(), is(equalTo(status.getValue())));
    }

    private static DbCandidate createCandidate(URI institutionId) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .level(DbLevel.LEVEL_ONE)
                   .internationalCollaboration(false)
                   .publicationBucketUri(randomUri())
                   .publicationDate(
                       new DbPublicationDate("2023", "01", "01"))
                   .creators(List.of(new DbCreator(randomUri(), List.of(institutionId))))
                   .creatorCount(1)
                   .instanceType("AcademicArticle")
                   .applicable(true)
                   .points(List.of())
                   .build();
    }

    private InputStream createRequest(NviStatusRequest body) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(Map.of("candidateIdentifier", body.candidateId().toString()))
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   //TODO CHANGE TO CORRECT ACCESS RIGHT
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }
}
