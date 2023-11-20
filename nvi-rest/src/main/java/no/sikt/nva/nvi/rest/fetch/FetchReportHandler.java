package no.sikt.nva.nvi.rest.fetch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.utils.RequestUtil.hasAccessRight;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATE;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.rest.model.Excel;
import no.sikt.nva.nvi.rest.model.ReportRow;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class FetchReportHandler extends ApiGatewayHandler<Void, String> {

    public static final Encoder ENCODER = Base64.getEncoder();
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
        return List.of(MediaType.MICROSOFT_EXCEL);
    }

    @Override
    protected String processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        var requestedInstitution = decodeInstitutionIdentifierPath(requestInfo);
        validateRequest(requestedInstitution, requestInfo);
        var data = List.of(ReportRow.builder().build());
        return attempt(() -> createReport(data))
                   .map(this::toBase64EncodedString)
                   .orElseThrow(ExceptionMapper::map);
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
        hasAccessRight(requestInfo, MANAGE_NVI_CANDIDATE);
        hasSameCustomer(requestInfo, requestedInstitution);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, URI requestedInstitution) throws ForbiddenException {
        if (!requestedInstitution.equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }

    private String toBase64EncodedString(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private byte[] createReport(List<ReportRow> data) throws Exception {
        try (
            var byteArrayOutputStream = new ByteArrayOutputStream();
            var excel = Excel.fromRecord(data)
        ) {
            excel.write(byteArrayOutputStream);
            setIsBase64Encoded(true);

            return byteArrayOutputStream.toByteArray();
        }
    }
}
