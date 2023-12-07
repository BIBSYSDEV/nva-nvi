package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    public static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    public static final String IDENTIFIER = "identifier";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexDocumentHandler.class);
    private final StorageReader<URI> storageReader;
    private final StorageWriter<NviCandidateIndexDocument> storageWriter;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final NviCandidateIndexDocumentGenerator documentGenerator;

    @JacocoGenerated
    public IndexDocumentHandler() {
        this(new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
             new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
             new CandidateRepository(defaultDynamoClient()),
             new PeriodRepository(defaultDynamoClient()),
             new NviCandidateIndexDocumentGenerator(defaultUriRetriever(new Environment())));
    }

    public IndexDocumentHandler(StorageReader<URI> storageReader,
                                StorageWriter<NviCandidateIndexDocument> storageWriter,
                                CandidateRepository candidateRepository,
                                PeriodRepository periodRepository,
                                NviCandidateIndexDocumentGenerator documentGenerator) {
        this.storageReader = storageReader;
        this.storageWriter = storageWriter;
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.documentGenerator = documentGenerator;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        LOGGER.info("Received event with records count: {}", input.getRecords().size());
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .map(this::fetchCandidate)
            .map(this::generateIndexDocument)
            .forEach(this::saveInCandidateBucket);
        return null;
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriever(Environment env) {
        return new AuthorizedBackendUriRetriever(env.readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 env.readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private static UUID extractIdentifierFromNewImage(DynamodbStreamRecord record) {
        return UUID.fromString(record.getDynamodb().getNewImage().get(IDENTIFIER).getS());
    }

    private void saveInCandidateBucket(NviCandidateIndexDocument nviCandidateIndexDocument) {
        var uri = attempt(() -> storageWriter.write(nviCandidateIndexDocument)).orElseThrow();
        LOGGER.info("Saved {} in bucket", uri);
    }

    private NviCandidateIndexDocument generateIndexDocument(Candidate candidate) {
        var expandedResource = getPublicationFromBucket(candidate);
        return documentGenerator.generateDocument(expandedResource, candidate);
    }

    private Candidate fetchCandidate(DynamodbStreamRecord record) {
        var candidateIdentifier = extractIdentifierFromNewImage(record);
        return fetchCandidate(candidateIdentifier);
    }

    private Candidate fetchCandidate(UUID candidateIdentifier) {
        return Candidate.fromRequest(() -> candidateIdentifier, candidateRepository, periodRepository);
    }

    private String getPublicationFromBucket(Candidate candidate) {
        var bucketUri = candidate.getPublicationDetails().publicationBucketUri();
        var result = storageReader.read(bucketUri);
        LOGGER.info("Fetched {} from bucket", bucketUri);
        return result;
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class)).orElseThrow();
    }
}
