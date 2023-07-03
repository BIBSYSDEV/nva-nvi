package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.ApplicationConstants.REGION;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateNviCandidateIndexDocument;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.aws.OpenSearchIndexClient;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.NviCandidateMessageBody;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.auth.CognitoCredentials;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv(
        "EXPANDED_RESOURCES_BUCKET");
    private static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    private static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    private final IndexClient<NviCandidateIndexDocument> indexClient;
    private final StorageReader<NviCandidateMessageBody> storageReader;

    @JacocoGenerated
    public IndexNviCandidateHandler() {
        this.storageReader = new S3StorageReader(EXPANDED_RESOURCES_BUCKET);
        var cognitoAuthenticator = new CognitoAuthenticator(HttpClient.newHttpClient(),
                                                            createCognitoCredentials(new SecretsReader()));
        var cachedJwtProvider = new CachedJwtProvider(cognitoAuthenticator, Clock.systemDefaultZone());
        this.indexClient = new OpenSearchIndexClient(SEARCH_INFRASTRUCTURE_API_HOST, cachedJwtProvider, REGION);
    }

    public IndexNviCandidateHandler(StorageReader<NviCandidateMessageBody> storageReader,
                                    IndexClient<NviCandidateIndexDocument> indexClient) {
        this.storageReader = storageReader;
        this.indexClient = indexClient;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::parseBody)
            .filter(Objects::nonNull)
            .map(this::validate)
            .filter(Objects::nonNull)
            .forEach(this::addNviCandidateToIndex);

        return null;
    }

    @JacocoGenerated
    private static CognitoCredentials createCognitoCredentials(SecretsReader secretsReader) {
        var credentials = secretsReader.fetchClassSecret(SEARCH_INFRASTRUCTURE_CREDENTIALS,
                                                         UsernamePasswordWrapper.class);
        return new CognitoCredentials(credentials::getUsername, credentials::getPassword,
                                      URI.create(SEARCH_INFRASTRUCTURE_AUTH_URI));
    }

    private void addNviCandidateToIndex(NviCandidateMessageBody candidate) {
        var indexedResource = storageReader.read(candidate);
        var indexDocument = generateNviCandidateIndexDocument(indexedResource, candidate);
        indexClient.addDocumentToIndex(indexDocument);
    }

    private NviCandidateMessageBody validate(NviCandidateMessageBody nviCandidate) {
        if (isNull(nviCandidate.publicationId())) {
            logInvalidMessageBody(nviCandidate.toJsonString());
            return null;
        }
        return nviCandidate;
    }

    private NviCandidateMessageBody parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, NviCandidateMessageBody.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error(ERROR_MESSAGE_BODY_INVALID, body);
    }
}
