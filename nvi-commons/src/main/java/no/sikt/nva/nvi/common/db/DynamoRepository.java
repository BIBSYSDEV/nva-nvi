package no.sikt.nva.nvi.common.db;

import java.util.Optional;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoRepository {
    private static final Logger logger = LoggerFactory.getLogger(DynamoRepository.class);
    final DynamoDbEnhancedClient client;

    protected DynamoRepository(DynamoDbClient client) {
        this.client = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    // PMD complains about the log error format but this call seems legit according to SLF4J
    // see http://slf4j.org/faq.html#exception_message
    @SuppressWarnings("PMD.InvalidLogMessageFormat")
    @JacocoGenerated
    protected static <T> RuntimeException handleError(Failure<T> fail) {
        logger.error("Error fetching user:", fail.getException());
        if (fail.getException() instanceof RuntimeException) {
            return (RuntimeException) fail.getException();
        } else {
            throw new RuntimeException(fail.getException());
        }
    }


}