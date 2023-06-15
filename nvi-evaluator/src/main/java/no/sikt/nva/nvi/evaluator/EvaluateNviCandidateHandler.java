package no.sikt.nva.nvi.evaluator;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import no.sikt.nva.nvi.evaluator.model.CandidateResponse;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class EvaluateNviCandidateHandler
    implements RequestHandler<S3Event, Void> {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String AWS_REGION_ENV_VARIABLE = "AWS_REGION";
    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;
    private static final String AFFILIATION_SPARQL =
        IoUtils.stringFromResources(Path.of("sparql/affiliation.sparql"));
    //TODO to be configured somehow
    private static final String NVI_YEAR = "2023";
    private static final String ID_JSON_PATH = "/body/id";
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_CANDIDATE =
        IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
            .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);
    private final S3Client s3Client;
    private final SqsClient sqsClient;

    @JacocoGenerated
    protected EvaluateNviCandidateHandler() {
        this(defaultS3Client(), defaultSqsClient());
    }

    public EvaluateNviCandidateHandler(S3Client s3Client, SqsClient sqsClient) {
        super();
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
    }

    @JacocoGenerated
    public static S3Client defaultS3Client() {
        var awsRegion = ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                            .orElse(Region.EU_WEST_1.toString());
        return S3Client.builder()
                   .region(Region.of(awsRegion))
                   .httpClient(UrlConnectionHttpClient.builder().build())
                   .build();
    }

    @Override
    public Void handleRequest(S3Event input, Context context) {
        var expectingSinglRecord = input.getRecords().get(0);
        var name = expectingSinglRecord.getS3().getBucket().getName();
        var key = expectingSinglRecord.getS3().getObject().getKey();
        var s3Driver = new S3Driver(s3Client, name);

        var s3bucketPath = UriWrapper.fromUri(URI.create(String.format("s3://%s/%s", name, key))).toS3bucketPath();
        var content = s3Driver.getFile(s3bucketPath);
        var json = attempt(() -> dtoObjectMapper.readTree(content)).orElseThrow();
        var theBody = json.at("/body").toString();

        var model = ModelFactory.createDefaultModel();
        loadDataIntoModel(model, stringToStream(theBody));
        var affiliationUris = new ArrayList<String>();
        try (var exec = QueryExecutionFactory.create(AFFILIATION_SPARQL, model)) {
            var resultSet = exec.execSelect();
            while (resultSet.hasNext()) {
                affiliationUris.add(
                    resultSet.next().getResource("affiliation").getURI());
            }
        }
        if (affiliationUris.isEmpty()) {
            return null;
        }
        //TODO ADD Check of Affiliations NVI affinity
        var nviAffiliationsForApproval = new ArrayList<>(affiliationUris);

        //TODO NviMonaCalc
        var nviCandidate =
            attempt(() -> QueryExecutionFactory.create(NVI_CANDIDATE, model))
                .map(QueryExecution::execAsk)
                .orElseThrow();

        if (!nviCandidate) {
            return null;
        }

        var resourceUri = URI.create(json.at(ID_JSON_PATH).asText());
        var response = CandidateResponse.builder()
                           .resourceUri(resourceUri)
                           .approvalAffiliations(
                               nviAffiliationsForApproval.stream().map(URI::create).toList())
                           .build();
        attempt(() ->
                    dtoObjectMapper.writeValueAsString(response))
            .map(this::createCandidate)
            .map(sqsClient::sendMessage).orElseThrow();
        return null;
    }

    @JacocoGenerated
    private static SdkHttpClient httpClientForConcurrentQueries() {
        return ApacheHttpClient.builder()
                   .useIdleConnectionReaper(true)
                   .maxConnections(MAX_CONNECTIONS)
                   .connectionMaxIdleTime(Duration.ofMinutes(IDLE_TIME))
                   .connectionTimeout(Duration.ofMinutes(TIMEOUT_TIME))
                   .build();
    }

    @JacocoGenerated
    private static SqsClient defaultSqsClient() {

        var region = ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                         .map(Region::of)
                         .orElse(Region.EU_WEST_1);
        return SqsClient.builder()
                   .region(region)
                   .httpClient(httpClientForConcurrentQueries())
                   .build();
    }

    @JacocoGenerated
    private static void logInvalidJsonLdInput(Exception exception) {
        LOGGER.warn("Invalid JSON LD input encountered: ", exception);
    }

    private SendMessageRequest createCandidate(String body) {
        return SendMessageRequest.builder()
                   .messageBody(body)
                   .build();
    }

    private void loadDataIntoModel(Model model, InputStream inputStream) {
        if (isNull(inputStream)) {
            return;
        }
        try {
            RDFDataMgr.read(model, inputStream, Lang.JSONLD);
        } catch (RiotException e) {
            logInvalidJsonLdInput(e);
        }
    }
}
