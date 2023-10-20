package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class FetchReportHandler extends ApiGatewayHandler<Void, String> {

    public static final Encoder ENCODER = Base64.getEncoder();
    private static final String INSTITUTION_IDENTIFIER = "institutionIdentifier";
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
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        var data = List.of(ReportRow.builder().build());
        return attempt(() -> createReport(data))
                   .map(this::toBase64EncodedString)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, String output) {
        return HttpURLConnection.HTTP_OK;
    }

    private String toBase64EncodedString(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private byte[] createReport(List<ReportRow> data) throws Exception {
        try (
            var byteArrayOutputStream = new ByteArrayOutputStream();
            var excel = Excel.fromRecord(data);
        ) {
            excel.write(byteArrayOutputStream);
            setIsBase64Encoded(true);

            return byteArrayOutputStream.toByteArray();
        }
    }
}
