package no.sikt.nva.nvi.index.apigateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class FetchReportHandler extends ApiGatewayHandler<Void, String> {

    private static final String INSTITUTION_ID = "institutionId";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public FetchReportHandler() {
        this(defaultDynamoClient());
    }

    public FetchReportHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository) {
        super(Void.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @JacocoGenerated
    public FetchReportHandler(DynamoDbClient dynamoDbClient) {
        this(new CandidateRepository(dynamoDbClient), new PeriodRepository(dynamoDbClient));
    }

    @Override
    protected List<MediaType> listSupportedMediaTypes() {
        return List.of(MediaType.MICROSOFT_EXCEL, MediaType.OOXML_SHEET);
    }

    @Override
    protected String processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        var requestedInstitution = decodeInstitutionIdentifierPath(requestInfo);
        validateRequest(requestedInstitution, requestInfo);
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, String output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static URI decodeInstitutionIdentifierPath(RequestInfo requestInfo) {
        return URI.create(URLDecoder.decode(requestInfo.getPathParameter(INSTITUTION_ID), UTF_8));
    }

    private static void validateRequest(URI requestedInstitution, RequestInfo requestInfo)
        throws UnauthorizedException, ForbiddenException {
        RequestUtil.hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATES);
        hasSameCustomer(requestInfo, requestedInstitution);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, URI requestedInstitution) throws ForbiddenException {
        if (!requestedInstitution.equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }
}
